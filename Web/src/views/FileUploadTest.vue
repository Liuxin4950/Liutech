<template>
  <div class="file-upload-test">
    <h2>文件上传测试</h2>
    
    <!-- 图片上传测试 -->
    <div class="upload-section">
      <h3>图片上传测试</h3>
      <input 
        type="file" 
        accept="image/*" 
        @change="handleImageUpload" 
        ref="imageInput"
      />
      <button @click="uploadImage" :disabled="!selectedImage || uploading">上传图片</button>
      <div v-if="imageResult" class="result">
        <p>上传成功！</p>
        <p>文件名: {{ imageResult.fileName }}</p>
        <p>文件大小: {{ imageResult.fileSize }} bytes</p>
        <p>访问地址: <a :href="imageResult.fileUrl" target="_blank">{{ imageResult.fileUrl }}</a></p>
        <img :src="imageResult.fileUrl" alt="上传的图片" style="max-width: 200px; margin-top: 10px;" />
      </div>
    </div>

    <!-- 文档上传测试 -->
    <div class="upload-section">
      <h3>文档上传测试</h3>
      <input 
        type="file" 
        accept=".txt,.pdf,.doc,.docx" 
        @change="handleDocumentUpload" 
        ref="documentInput"
      />
      <input 
        type="text" 
        v-model="documentDescription" 
        placeholder="文件描述（可选）"
        style="margin-left: 10px;"
      />
      <button @click="uploadDocument" :disabled="!selectedDocument || uploading">上传文档</button>
      <div v-if="documentResult" class="result">
        <p>上传成功！</p>
        <p>文件名: {{ documentResult.fileName }}</p>
        <p>文件大小: {{ documentResult.fileSize }} bytes</p>
        <p>访问地址: <a :href="documentResult.fileUrl" target="_blank">{{ documentResult.fileUrl }}</a></p>
      </div>
    </div>

    <!-- TinyMCE图片上传测试 -->
    <div class="upload-section">
      <h3>TinyMCE图片上传测试</h3>
      <input 
        type="file" 
        accept="image/*" 
        @change="handleTinyMCEImageUpload" 
        ref="tinyMCEImageInput"
      />
      <button @click="uploadTinyMCEImage" :disabled="!selectedTinyMCEImage || uploading">上传TinyMCE图片</button>
      <div v-if="tinyMCEResult" class="result">
        <p>上传成功！</p>
        <p>TinyMCE返回格式: {{ JSON.stringify(tinyMCEResult) }}</p>
        <img :src="tinyMCEResult.location" alt="TinyMCE上传的图片" style="max-width: 200px; margin-top: 10px;" />
      </div>
    </div>

    <!-- 错误信息显示 -->
    <div v-if="error" class="error">
      <p>错误: {{ error }}</p>
    </div>

    <!-- 加载状态 -->
    <div v-if="uploading" class="loading">
      <p>上传中...</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { post } from '@/services/api'

// 响应式数据
const uploading = ref(false)
const error = ref('')

// 图片上传相关
const selectedImage = ref<File | null>(null)
const imageResult = ref<any>(null)
const imageInput = ref<HTMLInputElement>()

// 文档上传相关
const selectedDocument = ref<File | null>(null)
const documentDescription = ref('')
const documentResult = ref<any>(null)
const documentInput = ref<HTMLInputElement>()

// TinyMCE图片上传相关
const selectedTinyMCEImage = ref<File | null>(null)
const tinyMCEResult = ref<any>(null)
const tinyMCEImageInput = ref<HTMLInputElement>()

// 处理图片选择
const handleImageUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files[0]) {
    selectedImage.value = target.files[0]
    imageResult.value = null
    error.value = ''
  }
}

// 处理文档选择
const handleDocumentUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files[0]) {
    selectedDocument.value = target.files[0]
    documentResult.value = null
    error.value = ''
  }
}

// 处理TinyMCE图片选择
const handleTinyMCEImageUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files[0]) {
    selectedTinyMCEImage.value = target.files[0]
    tinyMCEResult.value = null
    error.value = ''
  }
}

// 上传图片
const uploadImage = async () => {
  if (!selectedImage.value) return
  
  uploading.value = true
  error.value = ''
  
  try {
    const formData = new FormData()
    formData.append('file', selectedImage.value)
    
    const response = await post('/upload/image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    
    imageResult.value = response.data.data
    selectedImage.value = null
    if (imageInput.value) {
      imageInput.value.value = ''
    }
  } catch (err: any) {
    error.value = err.message || '上传失败'
  } finally {
    uploading.value = false
  }
}

// 上传文档
const uploadDocument = async () => {
  if (!selectedDocument.value) return
  
  uploading.value = true
  error.value = ''
  
  try {
    const formData = new FormData()
    formData.append('file', selectedDocument.value)
    if (documentDescription.value) {
      formData.append('description', documentDescription.value)
    }
    
    const response = await post('/upload/document', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    
    documentResult.value = response.data.data
    selectedDocument.value = null
    documentDescription.value = ''
    if (documentInput.value) {
      documentInput.value.value = ''
    }
  } catch (err: any) {
    error.value = err.message || '上传失败'
  } finally {
    uploading.value = false
  }
}

// 上传TinyMCE图片
const uploadTinyMCEImage = async () => {
  if (!selectedTinyMCEImage.value) return
  
  uploading.value = true
  error.value = ''
  
  try {
    const formData = new FormData()
    formData.append('file', selectedTinyMCEImage.value)
    
    const response = await post('/upload/tinymce/image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    
    // TinyMCE接口返回的是直接的对象，不是包装在data中
    tinyMCEResult.value = response.data
    selectedTinyMCEImage.value = null
    if (tinyMCEImageInput.value) {
      tinyMCEImageInput.value.value = ''
    }
  } catch (err: any) {
    error.value = err.message || '上传失败'
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.file-upload-test {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.upload-section {
  margin-bottom: 30px;
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 8px;
}

.upload-section h3 {
  margin-top: 0;
  color: #333;
}

.upload-section input[type="file"],
.upload-section input[type="text"] {
  margin-right: 10px;
  margin-bottom: 10px;
}

.upload-section button {
  padding: 8px 16px;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.upload-section button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.upload-section button:hover:not(:disabled) {
  background-color: #0056b3;
}

.result {
  margin-top: 15px;
  padding: 10px;
  background-color: #d4edda;
  border: 1px solid #c3e6cb;
  border-radius: 4px;
  color: #155724;
}

.result p {
  margin: 5px 0;
}

.result a {
  color: #007bff;
  text-decoration: none;
}

.result a:hover {
  text-decoration: underline;
}

.error {
  margin-top: 15px;
  padding: 10px;
  background-color: #f8d7da;
  border: 1px solid #f5c6cb;
  border-radius: 4px;
  color: #721c24;
}

.loading {
  margin-top: 15px;
  padding: 10px;
  background-color: #d1ecf1;
  border: 1px solid #bee5eb;
  border-radius: 4px;
  color: #0c5460;
  text-align: center;
}
</style>