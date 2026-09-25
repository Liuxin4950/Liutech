import { post } from './api'

/**
 * 图片上传响应接口
 * @author 刘鑫
 * @date 2025-01-17
 */
export interface ImageUploadResponse {
  fileName: string
  fileSize: number
  fileUrl: string
}

/**
 * TinyMCE图片上传响应接口
 */
export interface TinyMCEImageResponse {
  location: string
}

/**
 * 图片上传服务类
 * @author 刘鑫
 * @date 2025-01-17
 */
export class ImageUploadService {
  /**
   * 通用图片上传方法
   * @param file 图片文件
   * @returns 上传结果
   */
  static async uploadImage(file: File): Promise<ImageUploadResponse> {
    // 验证文件类型
    if (!file.type.startsWith('image/')) {
      throw new Error('请选择图片文件')
    }

    // 验证文件大小（5MB）
    if (file.size > 5 * 1024 * 1024) {
      throw new Error('图片大小不能超过5MB')
    }

    try {
      const formData = new FormData()
      formData.append('file', file)

      const apiResponse = await post<ImageUploadResponse>('/upload/image', formData, {
        headers: {
          'Content-Type': undefined // 设为 undefined 让浏览器自动设置正确的 multipart/form-data
        }
      })

      return apiResponse.data
    } catch (error: any) {
      console.error('图片上传失败:', error)
      // 提取后端返回的错误信息
      const errorMsg = error.response?.data?.message || error.message || '图片上传失败，请重试'
      throw new Error(errorMsg)
    }
  }

  /**
   * TinyMCE编辑器图片上传方法
   *
   * 走统一 axios 实例（`post`），自动注入 token、复用超时与错误处理；
   * 不再用原生 fetch 直连 —— 历史实现为了绕过「响应拦截器按 code !== 200 判错」
   * 才自己解析 URL 与读取 token，导致 Admin 与 Web 两套实现分叉。
   * 拦截器现已对齐 Web（未知结构包装为标准格式），本方法可以直接复用实例。
   *
   * @param blobInfo TinyMCE的blob信息
   * @param _progress 进度回调函数
   * @returns Promise<string> 返回图片URL
   */
  static async uploadTinyMCEImage(
    blobInfo: { blob(): Blob; filename(): string },
    _progress: (percent: number) => void
  ): Promise<string> {
    const formData = new FormData()
    formData.append('file', blobInfo.blob(), blobInfo.filename())

    const response = await post<any>('/upload/tinymce/image', formData, {
      // 显式声明 multipart：与 api.ts 实例默认不预设 Content-Type 的约定一致
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    // 后端返回 TinyMCE 期望的 { location } 或 { error }；拦截器会包装为 { code, message, data }，
    // 因此 location 可能位于 data 层或顶层，两种都兼容。
    const wrapped = response as any
    const location = wrapped?.data?.location ?? wrapped?.location
    if (location) {
      return location
    }

    const errorMsg = wrapped?.data?.error ?? wrapped?.error
    if (errorMsg) {
      throw new Error('上传失败：' + errorMsg)
    }
    throw new Error('上传失败：服务器未返回图片地址')
  }

}

/**
 * 从 ant-design-vue Upload 的 @change 事件参数里取出「本次选中」的原始 File。
 *
 * 为什么要统一走这里：Upload 在 `:before-upload` 返回 `false`（我们用它拦下 antd
 * 自带的上传请求、自己走上传流程）时是非受控的，内部 fileList 会跨多次选择不断
 * 累积。若直接取 `info.fileList[0]`，在同一个弹窗里第二次换图时会拿到上一次的
 * 旧文件——上传的是旧图。这里取列表的最后一项（最新），并兜底 `info.file`
 * 本身（before-upload=false 时 antd 会把 info.file 设成重建出的原始 File）。
 * 搭配 `:max-count="1"` 使用时 fileList 恒为最新一项，最稳妥。
 */
export function pickUploadFile(info: any): File | undefined {
  const list = info?.fileList
  if (Array.isArray(list) && list.length > 0) {
    const last = list[list.length - 1]
    const fromList = last?.originFileObj ?? (last instanceof File ? last : undefined)
    if (fromList) return fromList as File
  }
  const current = info?.file
  return current?.originFileObj ?? (current instanceof File ? current : undefined)
}

export default ImageUploadService
