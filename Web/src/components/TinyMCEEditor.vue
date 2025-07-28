<template>
  <div class="tinymce-editor">
    <div v-if="!editorLoaded" class="editor-loading">
      正在加载编辑器...
    </div>
    <Editor
      v-model="content"
      :init="editorConfig"
      :disabled="disabled"
      @change="handleChange"
      @init="onEditorInit"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import Editor from '@tinymce/tinymce-vue'
import theme from '@/utils/theme'

// 导入TinyMCE核心
import 'tinymce/tinymce'
// 导入TinyMCE主题
import 'tinymce/themes/silver'
// 导入TinyMCE图标
import 'tinymce/icons/default'
// 导入TinyMCE插件
import 'tinymce/plugins/advlist'
import 'tinymce/plugins/autolink'
import 'tinymce/plugins/lists'
import 'tinymce/plugins/link'
import 'tinymce/plugins/image'
import 'tinymce/plugins/charmap'
import 'tinymce/plugins/preview'
import 'tinymce/plugins/anchor'
import 'tinymce/plugins/searchreplace'
import 'tinymce/plugins/visualblocks'
import 'tinymce/plugins/code'
import 'tinymce/plugins/fullscreen'
import 'tinymce/plugins/insertdatetime'
import 'tinymce/plugins/media'
import 'tinymce/plugins/table'
import 'tinymce/plugins/help'
import 'tinymce/plugins/wordcount'
import 'tinymce/plugins/emoticons'
// 导入表情符号数据库
import 'tinymce/plugins/emoticons/js/emojis'

console.log('TinyMCEEditor组件正在加载...')

// 定义组件属性
interface Props {
  modelValue?: string
  disabled?: boolean
  height?: number
  placeholder?: string
}

// 定义事件
interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  disabled: false,
  height: 400,
  placeholder: '请输入文章内容...'
})

const emit = defineEmits<Emits>()

// 编辑器内容
const content = ref(props.modelValue)
// 编辑器加载状态
const editorLoaded = ref(false)

// 动态主题样式
const getContentStyle = (isDark: boolean) => {
  const lightStyle = `
    body { 
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif; 
      font-size: 14px;
      line-height: 1.6;
      color: #333;
      background-color: #ffffff;
    }
    p { margin: 8px 0; }
    h1, h2, h3, h4, h5, h6 { margin: 16px 0 8px 0; font-weight: 600; color: #333; }
    blockquote { 
      border-left: 4px solid #ddd; 
      margin: 16px 0; 
      padding: 8px 16px; 
      background: #f9f9f9;
      color: #333;
    }
    code { 
      background: #f1f2f6; 
      padding: 2px 6px; 
      border-radius: 4px; 
      font-family: 'Courier New', monospace;
      color: #e74c3c;
    }
    pre { 
      background: #f8f9fa; 
      border: 1px solid #e9ecef; 
      border-radius: 4px; 
      padding: 12px; 
      overflow-x: auto;
      color: #333;
    }
    img { max-width: 100%; height: auto; }
    table { border-collapse: collapse; width: 100%; }
    table td, table th { border: 1px solid #ddd; padding: 8px; color: #333; }
    table th { background-color: #f2f2f2; font-weight: bold; }
  `
  
  const darkStyle = `
    body { 
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif; 
      font-size: 14px;
      line-height: 1.6;
      color: #ffffff;
      background-color: #1a1a1a;
    }
    p { margin: 8px 0; }
    h1, h2, h3, h4, h5, h6 { margin: 16px 0 8px 0; font-weight: 600; color: #ffffff; }
    blockquote { 
      border-left: 4px solid #4c4d4f; 
      margin: 16px 0; 
      padding: 8px 16px; 
      background: #2d2d2d;
      color: #ffffff;
    }
    code { 
      background: #2d2d2d; 
      padding: 2px 6px; 
      border-radius: 4px; 
      font-family: 'Courier New', monospace;
      color: #66b1ff;
    }
    pre { 
      background: #2d2d2d; 
      border: 1px solid #4c4d4f; 
      border-radius: 4px; 
      padding: 12px; 
      overflow-x: auto;
      color: #ffffff;
    }
    img { max-width: 100%; height: auto; }
    table { border-collapse: collapse; width: 100%; }
    table td, table th { border: 1px solid #4c4d4f; padding: 8px; color: #ffffff; }
    table th { background-color: #2d2d2d; font-weight: bold; }
  `
  
  return isDark ? darkStyle : lightStyle
}

// TinyMCE配置（响应式主题）
const editorConfig = computed(() => ({
  height: props.height,
  menubar: false,
  readonly: false, // 确保编辑器不是只读模式
  base_url: '/node_modules/tinymce', // 设置TinyMCE资源基础路径
  suffix: '.min', // 使用压缩版本
  plugins: [
    'advlist', 'autolink', 'lists', 'link', 'image', 'charmap', 'preview',
    'anchor', 'searchreplace', 'visualblocks', 'code', 'fullscreen',
    'insertdatetime', 'media', 'table', 'help', 'wordcount', 'emoticons'
  ],
  toolbar: [
    'undo redo | blocks | bold italic underline strikethrough | alignleft aligncenter alignright alignjustify',
    'bullist numlist outdent indent | removeformat | link image media table | code preview fullscreen | help'
  ].join(' | '),
  content_style: getContentStyle(theme.current.value === 'dark'),
  placeholder: props.placeholder,
  branding: false,
  promotion: false,
  skin: theme.current.value === 'dark' ? 'oxide-dark' : 'oxide',  
  content_css: theme.current.value === 'dark' ? 'dark' : 'default',
  directionality: 'ltr',
  element_format: 'html',
  entities: '160,nbsp,38,amp,60,lt,62,gt',
  indent: false,
  keep_styles: false,
  paste_data_images: true,
  paste_as_text: false,
  paste_webkit_styles: 'none',
  paste_retain_style_properties: 'color font-size',
  resize: 'both',
  statusbar: true,
  convert_urls: false,
  relative_urls: false,
  remove_script_host: false,
  // 初始化回调，用于调试
  init_instance_callback: (editor: any) => {
    console.log('TinyMCE编辑器初始化完成:', editor.id)
    console.log('编辑器模式:', editor.readonly ? '只读' : '可编辑')
    console.log('当前主题:', theme.current.value)
  },
  // 图片上传配置（暂时禁用，后续可以添加图片上传功能）
  images_upload_handler: (blobInfo: any, success: Function, _failure: Function) => {
    // 这里可以实现图片上传逻辑
    // 暂时返回base64
    const reader = new FileReader()
    reader.onload = () => {
      success(reader.result)
    }
    reader.readAsDataURL(blobInfo.blob())
  }
}))

// 监听外部值变化
watch(() => props.modelValue, (newValue) => {
  if (newValue !== content.value) {
    content.value = newValue
  }
})

// 处理内容变化
const handleChange = () => {
  emit('update:modelValue', content.value)
  emit('change', content.value)
}

// 监听内容变化
watch(content, (newValue) => {
  emit('update:modelValue', newValue)
  emit('change', newValue)
})

// 编辑器实例引用
const editorInstance = ref<any>(null)

// 编辑器初始化完成
const onEditorInit = (editor: any) => {
  console.log('TinyMCE编辑器初始化完成')
  editorInstance.value = editor
  editorLoaded.value = true
}

// 监听主题变化，重新初始化编辑器
watch(() => theme.current.value, () => {
  if (editorInstance.value && typeof editorInstance.value.remove === 'function') {
    console.log('主题已切换，重新初始化编辑器')
    // 保存当前内容
    const currentContent = content.value
    // 使用正确的方法销毁编辑器实例
    try {
      editorInstance.value.remove()
    } catch (error) {
      console.warn('编辑器销毁时出现警告:', error)
    }
    // 重置状态
    editorInstance.value = null
    editorLoaded.value = false
    // 延迟重新初始化，确保DOM更新
    setTimeout(() => {
      content.value = currentContent
    }, 100)
  }
})
</script>

<style scoped>
.tinymce-editor {
  width: 100%;
}

.editor-loading {
  padding: 20px;
  text-align: center;
  color: #666;
  background: #f9f9f9;
  border: 1px solid #e1e5e9;
  border-radius: 8px;
  margin-bottom: 10px;
}

/* TinyMCE样式覆盖 - 浅色主题 */
:deep(.tox) {
  border-radius: 8px;
  border: 1px solid var(--border-color, #e1e5e9);
  transition: all 0.3s ease;
}

:deep(.tox-toolbar) {
  background: var(--bg-color, #ffffff);
  border-bottom: 1px solid var(--border-color, #e1e5e9);
  transition: all 0.3s ease;
}

:deep(.tox-edit-area) {
  background: var(--bg-color, #ffffff);
  transition: all 0.3s ease;
}

:deep(.tox-statusbar) {
  background: var(--bg-color, #ffffff);
  border-top: 1px solid var(--border-color, #e1e5e9);
  color: var(--text-color, #333333);
  transition: all 0.3s ease;
}

:deep(.tox-toolbar__group) {
  border-color: var(--border-color, #e1e5e9);
}

:deep(.tox-tbtn) {
  color: var(--text-color, #333333);
}

:deep(.tox-tbtn:hover) {
  background: var(--hover-color, #ecf5ff);
}

/* 暗色主题适配 */
.dark :deep(.tox) {
  border-color: var(--border-color, #4c4d4f);
  background: var(--bg-color, #1a1a1a);
}

.dark :deep(.tox-toolbar) {
  background: var(--bg-color, #1a1a1a);
  border-bottom-color: var(--border-color, #4c4d4f);
}

.dark :deep(.tox-edit-area) {
  background: var(--bg-color, #1a1a1a);
}

.dark :deep(.tox-statusbar) {
  background: var(--bg-color, #1a1a1a);
  border-top-color: var(--border-color, #4c4d4f);
  color: var(--text-color, #ffffff);
}

.dark :deep(.tox-toolbar__group) {
  border-color: var(--border-color, #4c4d4f);
}

.dark :deep(.tox-tbtn) {
  color: var(--text-color, #ffffff);
}

.dark :deep(.tox-tbtn:hover) {
  background: var(--hover-color, #18222c);
}

.dark :deep(.tox-tbtn--enabled) {
  background: var(--primary-color, #66b1ff);
  color: #ffffff;
}

.dark :deep(.tox-menubar) {
  background: var(--bg-color, #1a1a1a);
  border-bottom-color: var(--border-color, #4c4d4f);
}

.dark :deep(.tox-collection__item) {
  background: var(--bg-color, #1a1a1a);
  color: var(--text-color, #ffffff);
}

.dark :deep(.tox-collection__item:hover) {
  background: var(--hover-color, #18222c);
}
</style>