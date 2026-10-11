import type { Comment, CommentThreadPage } from '@/services/comments'

export interface CommentRelations {
  byId: Map<number, Comment>
  parents: Map<number, number | undefined>
  children: Map<number, number[]>
  roots: number[]
  issues: Map<number, string>
}

export interface CommentThreadRow {
  comment: Comment
  depth: number
  childCount: number
  issue?: string
}

/** 取尽稳定分页并校验完整性。遇到变化明确失败，不展示残缺数据为完整树。 */
export async function loadCompleteCommentThread(loadPage: (page: number, snapshotMaxId?: number) => Promise<CommentThreadPage>) {
  const first = await loadPage(1)
  const { total, size, pages } = first.comments
  if (!Number.isSafeInteger(total) || total < 1 || !Number.isSafeInteger(size) || size < 1
    || !Number.isSafeInteger(first.snapshotMaxId) || first.snapshotMaxId < first.commentId
    || pages !== Math.ceil(total / size)) throw new Error('评论关系分页数据不完整，请刷新重试')
  const comments: Comment[] = []
  const ids = new Set<number>()
  const append = (data: CommentThreadPage, page: number) => {
    const result = data.comments
    const expected = Math.min(size, total - (page - 1) * size)
    if (data.postId !== first.postId || data.commentId !== first.commentId || data.snapshotMaxId !== first.snapshotMaxId || result.total !== total
      || result.size !== size || result.current !== page || result.pages !== pages || result.records.length !== expected) {
      throw new Error('评论数据在加载期间发生变化，请刷新重试')
    }
    for (const comment of result.records) {
      if (!comment.id || comment.id > first.snapshotMaxId || ids.has(comment.id) || comment.postId !== first.postId) {
        throw new Error('评论关系数据重复或文章不一致，请刷新重试')
      }
      ids.add(comment.id)
      comments.push(comment)
    }
  }
  append(first, 1)
  for (let page = 2; page <= pages; page++) append(await loadPage(page, first.snapshotMaxId), page)
  if (comments.length !== total || !ids.has(first.commentId)) throw new Error('当前评论已发生变化，请刷新重试')
  return { commentId: first.commentId, postId: first.postId, postTitle: first.postTitle, comments }
}

/** 管理端关系索引：平铺数据保留全部节点，仅对异常连接作可解释的显示处理。 */
export function indexCommentRelations(comments: Comment[]): CommentRelations {
  const byId = new Map<number, Comment>()
  for (const comment of comments) if (comment.id) byId.set(comment.id, comment)
  const parents = new Map<number, number | undefined>()
  const children = new Map<number, number[]>()
  const issues = new Map<number, string>()
  for (const [id, comment] of byId) {
    children.set(id, [])
    const parent = comment.parentId ? byId.get(comment.parentId) : undefined
    if (comment.parentId && (!parent || parent.postId !== comment.postId)) {
      issues.set(id, parent && parent.postId !== comment.postId || comment.parent?.postId && comment.parent.postId !== comment.postId
        ? '父评论属于其他文章，已独立展示' : '父评论不可用，已独立展示')
    }
    parents.set(id, parent?.postId === comment.postId ? parent.id : undefined)
  }
  const processed = new Set<number>()
  for (const start of byId.keys()) {
    if (processed.has(start)) continue
    const path: number[] = []
    const positions = new Map<number, number>()
    let current: number | undefined = start
    while (current !== undefined && !processed.has(current)) {
      const cycleStart = positions.get(current)
      if (cycleStart !== undefined) {
        for (let index = cycleStart; index < path.length; index++) {
          issues.set(path[index]!, '评论关系存在循环，已安全展开')
        }
        parents.set(current, undefined)
        break
      }
      positions.set(current, path.length)
      path.push(current)
      current = parents.get(current)
    }
    for (const id of path) processed.add(id)
  }
  const roots: number[] = []
  for (const id of byId.keys()) {
    const parent = parents.get(id)
    if (parent === undefined) roots.push(id)
    else children.get(parent)!.push(id)
  }
  return { byId, parents, children, roots, issues }
}

/** 迭代展开，深层评论不占用 JavaScript 调用栈。 */
export function flattenCommentRelations(relations: CommentRelations, expanded: ReadonlySet<number>): CommentThreadRow[] {
  const rows: CommentThreadRow[] = []
  const stack = relations.roots.map(id => ({ id, depth: 0 })).reverse()
  while (stack.length) {
    const { id, depth } = stack.pop()!
    const children = relations.children.get(id) || []
    rows.push({ comment: relations.byId.get(id)!, depth, childCount: children.length, issue: relations.issues.get(id) })
    if (expanded.has(id)) {
      for (let index = children.length - 1; index >= 0; index--) stack.push({ id: children[index]!, depth: depth + 1 })
    }
  }
  return rows
}

export function commentAuthorName(comment: Comment): string {
  return comment.authorType === 'BOT' || comment.botId
    ? comment.bot?.name || `AI 角色 #${comment.botId ?? '-'}`
    : comment.user?.username || `用户 #${comment.userId ?? '-'}`
}
