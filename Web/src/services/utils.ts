import { post, getAxiosInstance } from './api'

/**
 * 图片上传响应接口
 */
interface ImageUploadResponse {
  fileName: string
  fileSize: number
  fileUrl: string
}

/**
 * 图片上传服务类
 * @author 刘鑫
 * @date 2025-01-11
 */
export class ImageUploadService {
  /**
   * 通用图片上传方法（仅管理员可用，用于文章封面/TinyMCE 等）
   * @param file 图片文件
   * @returns 上传结果
   */
  static async uploadImage(file: File): Promise<ImageUploadResponse> {
    return ImageUploadService.uploadTo('/upload/image', file)
  }

  /**
   * 头像上传方法（普通登录用户可用）
   * @param file 图片文件
   * @returns 上传结果
   */
  static async uploadAvatar(file: File): Promise<ImageUploadResponse> {
    return ImageUploadService.uploadTo('/upload/avatar', file)
  }

  /**
   * 图片上传内部实现：校验 + FormData 提交到指定端点
   * @param endpoint 上传端点
   * @param file 图片文件
   */
  private static async uploadTo(endpoint: string, file: File): Promise<ImageUploadResponse> {
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

      const response = await post(endpoint, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })

      return response.data
    } catch (error: any) {
      console.error('图片上传失败:', error)
      throw new Error(error?.message || '图片上传失败，请重试')
    }
  }

  /**
   * TinyMCE编辑器图片上传方法
   * @param blobInfo TinyMCE的blob信息
   * @param progress 进度回调函数
   * @returns Promise<string> 返回图片URL
   */
  static async uploadTinyMCEImage(
    blobInfo: { blob(): Blob; filename(): string },
    _progress: (percent: number) => void
  ): Promise<string> {
    const formData = new FormData()
    formData.append('file', blobInfo.blob(), blobInfo.filename())

    const { data } = await getAxiosInstance().post('/upload/tinymce/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    // 响应拦截器会把非标准响应包装为 {code, message, data}，location 可能位于 data 或 data.data
    const wrapped = data as any
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