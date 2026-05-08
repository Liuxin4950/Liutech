import { post } from './api'

/**
 * TinyMCE图片上传响应接口
 */
export interface TinyMCEImageResponse {
  location: string
}

/**
 * 图片上传服务类
 */
export class ImageUploadService {
  /**
   * TinyMCE编辑器图片上传方法
   * @param blobInfo TinyMCE的blob信息
   * @param progress 进度回调函数
   * @returns Promise<string> 返回图片URL
   */
  static uploadTinyMCEImage(
    blobInfo: any,
    _progress: (percent: number) => void
  ): Promise<string> {
    return new Promise((resolve, reject) => {
      try {
        const formData = new FormData()
        formData.append('file', blobInfo.blob(), blobInfo.filename())

        // 获取token
        const token = localStorage.getItem('token')

        // 使用与api.ts相同的baseURL逻辑
        const getBackendURL = (): string => {
          const envUrl = import.meta.env.VITE_API_BASE_URL as string | undefined
          if (envUrl && envUrl.trim().length > 0) {
            return envUrl
          }
          if (import.meta.env.DEV) {
            return 'http://127.0.0.1:8080'
          }
          return '/api'
        }

        fetch(`${getBackendURL()}/upload/tinymce/image`, {
          method: 'POST',
          headers: {
            'Authorization': token ? `Bearer ${token}` : ''
          },
          body: formData
        })
        .then(response => {
          if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`)
          }
          return response.json()
        })
        .then(data => {
          // 后端返回格式: { "location": "http://localhost:8080/uploads/images/xxx.jpg" }
          if (data.location) {
            resolve(data.location)
          } else if (data.error) {
            reject('上传失败：' + data.error)
          } else {
            reject('上传失败：服务器未返回图片地址')
          }
        })
        .catch(error => {
          console.error('TinyMCE图片上传失败:', error)
          reject('上传失败：' + error.message)
        })
      } catch (error) {
        reject('图片处理失败：' + error)
      }
    })
  }
}

export default ImageUploadService
