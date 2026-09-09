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

export default ImageUploadService
