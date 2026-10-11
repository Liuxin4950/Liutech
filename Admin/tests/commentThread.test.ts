import { describe, expect, it, vi } from 'vitest'
import type { Comment, CommentThreadPage } from '@/services/comments'
import { commentAuthorName, flattenCommentRelations, indexCommentRelations, loadCompleteCommentThread } from '@/utils/commentThread'

const comment = (id: number, parentId?: number, extra: Partial<Comment> = {}): Comment => ({ id, parentId, postId: 7, content: `评论 ${id}`, ...extra })
const response = (records: Comment[], total: number, current = 1, size = 500, snapshotMaxId = total): CommentThreadPage => ({
  commentId: total, postId: 7, postTitle: '测试文章', snapshotMaxId,
  comments: { records, total, current, size, pages: Math.ceil(total / size) },
})

describe('管理端完整评论关系', () => {
  it('取尽超过一页的关系并固定首批快照边界，不裁剪父节点、兄弟或后代', async () => {
    const first = Array.from({ length: 500 }, (_, index) => comment(index + 1, index === 0 ? undefined : 1))
    const load = vi.fn(async (page: number) => page === 1 ? response(first, 501) : response([comment(501, 500)], 501, 2))
    const result = await loadCompleteCommentThread(load)
    expect(result.comments).toHaveLength(501)
    expect(load.mock.calls).toEqual([[1], [2, 501]])
    expect(result.comments.at(-1)?.parentId).toBe(500)
  })

  it('软删除父节点与真人、AI 兄弟保持在同一关系中', () => {
    const source = [comment(1, undefined, { deletedAt: '2026-10-11' }), comment(2, 1), comment(3, 1, { botId: 8 }), comment(4, 2), comment(5)]
    const index = indexCommentRelations(source)
    const rows = flattenCommentRelations(index, new Set([1, 2, 3, 4, 5]))
    expect(rows.map(row => row.comment.id)).toEqual([1, 2, 4, 3, 5])
    expect(index.children.get(1)).toEqual([2, 3])
    expect(rows[0]?.comment.deletedAt).toBe('2026-10-11')
    expect(rows[2]?.depth).toBe(2)
  })

  it('循环、孤儿和跨文章父节点保留内容并标出异常，无无限递归', () => {
    const source = [comment(1, 2), comment(2, 1), comment(3, 99), comment(4, 5), comment(5, undefined, { postId: 8 })]
    const index = indexCommentRelations(source)
    const rows = flattenCommentRelations(index, new Set(source.map(item => item.id!)))
    expect(rows).toHaveLength(5)
    expect(new Set(rows.map(row => row.comment.id)).size).toBe(5)
    expect(index.issues.get(1)).toContain('循环')
    expect(index.issues.get(2)).toContain('循环')
    expect(index.issues.get(3)).toContain('不可用')
    expect(index.issues.get(4)).toContain('其他文章')
  })

  it('一万两千层评论可安全索引和展开，折叠保留后代数据', () => {
    const source = Array.from({ length: 12000 }, (_, index) => comment(index + 1, index === 0 ? undefined : index))
    const relations = indexCommentRelations(source)
    const expanded = new Set(source.map(item => item.id!))
    expect(flattenCommentRelations(relations, expanded).at(-1)?.depth).toBe(11999)
    expect(flattenCommentRelations(relations, new Set())).toHaveLength(1)
    expect(relations.byId.size).toBe(12000)
  })

  it.each(['total', 'empty', 'duplicate', 'snapshot', 'foreign'] as const)('拒绝加载中的 %s 异常，不冒充完整树', async kind => {
    const first = response([comment(1), comment(2, 1)], 3, 1, 2)
    const second = response([comment(3, 2)], 3, 2, 2)
    if (kind === 'total') second.comments.total = 4
    if (kind === 'empty') second.comments.records = []
    if (kind === 'duplicate') second.comments.records = [comment(2)]
    if (kind === 'snapshot') second.snapshotMaxId = 4
    if (kind === 'foreign') second.comments.records = [comment(3, undefined, { postId: 9 })]
    await expect(loadCompleteCommentThread(async page => page === 1 ? first : second)).rejects.toThrow(/变化|重复|不一致/)
  })

  it('缺失当前评论时明确失败，并依据真实身份显示作者', async () => {
    const page = response([comment(1), comment(2)], 2)
    page.commentId = 3
    page.snapshotMaxId = 3
    await expect(loadCompleteCommentThread(async () => page)).rejects.toThrow('当前评论')
    expect(commentAuthorName(comment(1, undefined, { user: { id: 2, username: 'liuxin' } }))).toBe('liuxin')
    expect(commentAuthorName(comment(2, 1, { botId: 8, bot: { id: 8, name: '黑塔' } }))).toBe('黑塔')
    expect(commentAuthorName(comment(3, undefined, { userId: 21 }))).toBe('用户 #21')
  })
})
