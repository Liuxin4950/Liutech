/**
 * 全量分页加载（Admin 端）
 *
 * 后端 PageQuery 对每页 size 有上限（超出直接 400），所以「需要一次拿全」的下拉选项、
 * 排序列表不能靠单次大 size 请求，必须按后端允许的页大小逐页读满。
 *
 * helper 只负责翻页与拼接，请求仍走调用方已有 service 方法，错误照常抛给调用方
 * （拦截器已统一提示后端 message，调用方按 isBusiness 决定是否补兜底文案）。
 *
 * @author 刘鑫
 */
import type { ApiResponse } from './types'
import type { PageResult } from './types'

/** 默认每页条数：低于后端 size 上限，留出安全余量 */
export const DEFAULT_PAGE_SIZE = 200

/**
 * 单页请求函数
 *
 * @param page 页码，从 1 开始
 * @param size 每页条数
 */
export type PageFetcher<T> = (page: number, size: number) => Promise<ApiResponse<PageResult<T>>>

/**
 * 逐页读取全量记录
 *
 * @param fetchPage 单页请求（传已有 service 方法，不新增接口）
 * @param pageSize 每页条数，默认 {@link DEFAULT_PAGE_SIZE}
 * @returns 所有页 records 的拼接结果，顺序与后端分页顺序一致
 */
export async function loadAllPages<T>(
  fetchPage: PageFetcher<T>,
  pageSize: number = DEFAULT_PAGE_SIZE
): Promise<T[]> {
  const records: T[] = []
  let pages = 1
  for (let page = 1; page <= pages; page++) {
    const response = await fetchPage(page, pageSize)
    if (response.code !== 200 || !response.data) throw new Error(response.message || '分页内容加载失败')
    const data = response.data
    if (!Array.isArray(data.records) || !Number.isInteger(data.pages) || data.pages < 0) {
      throw new Error('分页结果格式不正确')
    }
    if (page === 1) pages = data.pages
    if (pages > 0 && data.records.length === 0) throw new Error('分页内容发生变化，请重新加载')
    records.push(...data.records)
  }
  return records
}
