# TinyMCE 图片上传功能配置说明

> 作者：刘鑫  
> 更新时间：2025年1月11日

## 概述

本文档详细说明了博客系统中 TinyMCE 编辑器的图片上传功能配置，包括前端配置、后端接口和使用方法。

## 当前实现方式

### 服务器上传方式（推荐使用）

系统采用服务器上传方式，图片文件上传到后端服务器，返回访问URL。

#### 前端配置

```javascript
// TinyMCE 图片上传配置
images_upload_handler: (blobInfo, progress) => {
  return new Promise((resolve, reject) => {
    const formData = new FormData()
    formData.append('file', blobInfo.blob(), blobInfo.filename())
    
    // 获取用户认证token
    const token = localStorage.getItem('token')
    
    fetch('http://localhost:8080/upload/tinymce/image', {
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
  })
}

// 相关配置项
automatic_uploads: true,              // 启用自动上传
images_upload_credentials: false,     // 不发送凭据（使用Authorization头）
images_reuse_filename: true,          // 重用文件名
images_file_types: 'jpeg,jpg,jpe,jfi,jif,jfif,png,gif,bmp,webp',
convert_urls: true,                   // 启用URL转换
relative_urls: false,                 // 使用绝对URL
paste_data_images: true               // 允许粘贴图片
```

## 后端接口说明

### 接口地址
```
POST /upload/tinymce/image
```

### 请求参数
- **file**: MultipartFile - 图片文件
- **Authorization**: Header - Bearer token（用户认证）

### 响应格式

#### 成功响应
```json
{
  "location": "http://localhost:8080/uploads/images/20250111/abc123.jpg"
}
```

#### 错误响应
```json
{
  "error": "文件格式不支持"
}
```

### 后端实现（Spring Boot）

```java
@PostMapping("/tinymce/image")
public Object uploadImageForTinyMCE(
        @RequestParam("file") MultipartFile file,
        HttpServletRequest request) {
    
    Long userId = getCurrentUserId(request);
    log.info("接收到TinyMCE图片上传请求 - 用户ID: {}, 文件名: {}", userId, file.getOriginalFilename());
    
    try {
        FileUploadResl result = fileUploadService.uploadImage(file, userId);
        
        // 返回TinyMCE期望的格式
        return new TinyMCEResponse(result.getFileUrl());
        
    } catch (Exception e) {
        log.error("TinyMCE图片上传失败", e);
        // TinyMCE错误格式
        return new TinyMCEErrorResponse(e.getMessage());
    }
}
```

## 文件存储配置

### 存储路径
- **基础路径**: `{项目根目录}/uploads/`
- **图片路径**: `uploads/images/`
- **访问URL**: `http://localhost:8080/uploads/images/文件名`

### 文件限制
- **支持格式**: jpg, jpeg, png, gif, bmp, webp
- **最大大小**: 10MB
- **命名规则**: 时间戳 + 随机字符串 + 原扩展名

## 安全配置

### 认证要求
- 图片上传需要用户登录
- 使用JWT Token认证
- Token通过Authorization头传递

### 文件验证
- 文件类型验证
- 文件大小限制
- 文件名安全处理

## 使用方法

### 1. 直接上传
- 点击工具栏的图片按钮
- 选择本地图片文件
- 系统自动上传并插入

### 2. 拖拽上传
- 直接将图片拖拽到编辑器
- 系统自动处理上传

### 3. 粘贴上传
- 复制图片到剪贴板
- 在编辑器中粘贴（Ctrl+V）
- 系统自动上传处理

## 优势特点

### ✅ 优点
- **高效存储**: 图片存储在服务器，减少数据库压力
- **便于管理**: 统一的文件管理和备份
- **支持大图**: 不受HTML内容大小限制
- **CDN友好**: 可配置CDN加速访问
- **安全可控**: 完整的权限和安全验证

### ⚠️ 注意事项
- 需要后端服务正常运行
- 需要用户登录认证
- 需要配置静态资源访问

## 故障排除

### 常见问题

1. **上传失败 - 401错误**
   - 检查用户是否已登录
   - 确认token是否有效

2. **上传失败 - 500错误**
   - 检查后端服务是否运行
   - 查看后端日志错误信息

3. **图片显示不了**
   - 检查静态资源配置
   - 确认文件路径是否正确

4. **文件格式不支持**
   - 确认图片格式在允许列表中
   - 检查文件是否损坏

### 调试方法

1. **查看浏览器控制台**
   ```javascript
   // 在浏览器控制台查看上传请求
   console.log('TinyMCE图片上传失败:', error)
   ```

2. **查看后端日志**
   ```
   接收到TinyMCE图片上传请求 - 用户ID: 1, 文件名: image.jpg
   图片上传成功 - 用户ID: 1, 访问URL: http://localhost:8080/uploads/images/xxx.jpg
   ```

3. **测试接口**
   ```bash
   # 使用curl测试上传接口
   curl -X POST \
     -H "Authorization: Bearer your-token" \
     -F "file=@test.jpg" \
     http://localhost:8080/upload/tinymce/image
   ```

## 更新日志

- **2025-01-11**: 从base64方案切换到服务器上传方案
- **2025-01-11**: 添加JWT认证支持
- **2025-01-11**: 完善错误处理和日志记录
- **2025-01-11**: 修复TinyMCE图片尺寸获取问题，后端返回完整URL而非相对路径