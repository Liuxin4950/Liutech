/**
 * 通用分页结果接口
 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}


/** 主后端统一响应结构 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}
