import type { Comment } from './comment'

/** 展示时将根评论的全部回复平铺，保留原对象和真实父评论关系。 */
export function flattenCommentReplies(root: Comment): { replies: Comment[]; byId: ReadonlyMap<number, Comment> } {
  const byId = new Map<number, Comment>([[root.id, root]])
  const replies: Comment[] = []
  const pending = [...(root.children || [])]
  while (pending.length) {
    const comment = pending.pop()!
    if (byId.has(comment.id)) continue
    byId.set(comment.id, comment)
    replies.push(comment)
    for (const child of comment.children || []) pending.push(child)
  }
  const timestamp = (comment: Comment): number => {
    const value = Date.parse(comment.createdAt)
    return Number.isFinite(value) ? value : 0
  }
  replies.sort((a, b) => timestamp(a) - timestamp(b) || a.id - b.id)
  return { replies, byId }
}

/** 保留同 ID 的对象，避免轮询时重建评论组件、关闭回复框或改变展开状态。 */
export function mergeCommentTree(current: Comment[], incoming: Comment[], preserveIds: ReadonlySet<number> = new Set()): Comment[] {
  const existing = new Map(current.map(comment => [comment.id, comment]))
  const ordered = incoming.map(comment => {
    const old = existing.get(comment.id)
    if (!old) return comment
    const children = mergeCommentTree(old.children || [], comment.children || [], preserveIds)
    const { children: _children, ...fields } = comment
    Object.assign(old, fields)
    old.children = children
    return old
  })
  const containsPreserved = (comment: Comment): boolean => preserveIds.has(comment.id) || (comment.children || []).some(containsPreserved)
  const received = new Set(incoming.map(comment => comment.id))
  // 请求开始之后用户发布的新评论尚未包含在本次快照中，保留到下一次刷新。
  for (const old of current) if (!received.has(old.id) && containsPreserved(old)) ordered.push(old)
  current.splice(0, current.length, ...ordered)
  return current
}

export function addCommentToTree(comments: Comment[], comment: Comment): boolean {
  if (!comment.parentId) {
    if (!comments.some(item => item.id === comment.id)) comments.unshift(comment)
    return true
  }
  return addReplyToChildren(comments, comment)
}
function addReplyToChildren(comments: Comment[], comment: Comment): boolean {
  const pending = [...comments]
  const visited = new Set<number>()
  while (pending.length) {
    const parent = pending.pop()!
    if (visited.has(parent.id)) continue
    visited.add(parent.id)
    if (parent.id === comment.parentId) {
      parent.children ||= []
      if (!parent.children.some(item => item.id === comment.id)) parent.children.push(comment)
      return true
    }
    for (const child of parent.children || []) pending.push(child)
  }
  return false
}

export function commentAuthorName(comment: Comment): string {
  return comment.authorType === 'BOT' ? comment.bot?.name || 'AI 角色' : comment.user?.username || '匿名用户'
}
export function commentAuthorAvatar(comment: Comment): string | undefined {
  return comment.authorType === 'BOT' ? comment.bot?.avatarUrl : comment.user?.avatarUrl
}
