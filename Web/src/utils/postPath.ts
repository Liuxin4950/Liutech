/**
 * 文章 URL 路径工具
 *
 * URL 形如 /post/{id}-{slug}，slug 只保留标题中的 ASCII 片段（英文/数字），
 * 中文部分跳过以避免百分号编码乱码；纯中文标题 slug 为空，退化为 /post/{id}。
 * 路由仍按 {id} 匹配，PostDetail 用 parsePostId 从参数中提取数字 id。
 */

/** 将标题转为 URL 友好的 slug（提取 ASCII 字母数字片段，丢弃中文/标点） */
export const slugify = (title: string): string => {
  const matches = title.toLowerCase().match(/[a-z0-9]+/g)
  return matches ? matches.join('-') : ''
}

/** 构造文章路径 /post/{id} 或 /post/{id}-{slug} */
export const buildPostPath = (id: number | string, title?: string): string => {
  const slug = title ? slugify(title) : ''
  return slug ? `/post/${id}-${slug}` : `/post/${id}`
}

/** 从路由参数解析数字 id，兼容 /post/6 与 /post/6-vue3 */
export const parsePostId = (raw: unknown): number => {
  const match = String(raw ?? '').match(/^\d+/)
  return match ? parseInt(match[0], 10) : NaN
}
