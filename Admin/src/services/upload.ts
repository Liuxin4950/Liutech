import { post } from './api'

// 动态获取后端URL（优先使用环境变量）
const getBackendURL = (): string => {
  const envUrl = import.meta.env.VITE_API_BASE_URL as string | undefined
  if (envUrl && envUrl.trim().length > 0) {
    return envUrl
  }
  // 开发环境兜底
  if (import.meta.env.DEV) {
    return 'http://127.0.0.1:8080'
  }
  // 生产环境兜底
  const hostname = window.location.hostname
  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    return '/api'
  }
  return 'https://liutech.com'
}

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

      const response = await post('/upload/image', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })

      return response.data
    } catch (error) {
      console.error('图片上传失败:', error)
      throw new Error('图片上传失败，请重试')
    }
  }

  /**
   * TinyMCE编辑器图片上传方法
   * @param blobInfo TinyMCE的blob信息
   * @param _progress 进度回调函数
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

  /**
   * 文档上传方法
   * @param file 文档文件
   * @param description 文件描述（可选）
   * @returns 上传结果
   */
  static async uploadDocument(file: File, description?: string): Promise<any> {
    try {
      const formData = new FormData()
      formData.append('file', file)
      if (description) {
        formData.append('description', description)
      }

      const response = await post('/upload/document', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })

      return response.data
    } catch (error) {
      console.error('文档上传失败:', error)
      throw new Error('文档上传失败，请重试')
    }
  }
}

export default ImageUploadService