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
import { ImageUploadService } from '@/services/utils'
// 导入TinyMCE核心
import 'tinymce/tinymce'
// 导入TinyMCE主题
import 'tinymce/themes/silver'
// 导入TinyMCE图标
import 'tinymce/icons/default'
// 导入TinyMCE插件
import 'tinymce/plugins/advlist' // 高级列表
import 'tinymce/plugins/autolink' // 自动链接
import 'tinymce/plugins/lists' // 列表插件
import 'tinymce/plugins/link' // 链接插件
import 'tinymce/plugins/image' // 图片插件
import 'tinymce/plugins/charmap' // 特殊字符
import 'tinymce/plugins/preview' // 预览
import 'tinymce/plugins/anchor' // 锚点
import 'tinymce/plugins/searchreplace' // 查找替换
import 'tinymce/plugins/visualblocks' // 可视化块
import 'tinymce/plugins/code' // 代码
import 'tinymce/plugins/fullscreen' // 全屏
import 'tinymce/plugins/insertdatetime' // 插入日期时间
import 'tinymce/plugins/media' // 媒体
import 'tinymce/plugins/table' // 表格
import 'tinymce/plugins/help' // 帮助
import 'tinymce/plugins/wordcount' // 字数统计
import 'tinymce/plugins/emoticons' // 表情符号
import 'tinymce/plugins/codesample' // 代码示例
import 'tinymce/plugins/nonbreaking' // 不间断空格
import 'tinymce/plugins/visualchars' // 可视化字符
import 'tinymce/plugins/directionality' // 文字方向
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
  language_url: '/tinymce/langs/zh_CN.js', // 中文语言包路径
  language: 'zh_CN', // 设置语言为中文
  base_url: '/node_modules/tinymce', // 设置TinyMCE资源基础路径
  suffix: '.min', // 使用压缩版本
  plugins: [
    'advlist', 'autolink', 'lists', 'link', 'image', 'charmap', 'preview',
    'anchor', 'searchreplace', 'visualblocks', 'code', 'fullscreen',
    'insertdatetime', 'media', 'table', 'help', 'wordcount', 'emoticons',
    'codesample', 'nonbreaking', 'visualchars', 'directionality'
  ],
  toolbar: [
    'undo redo | formatselect fontselect fontsizeselect | bold italic underline strikethrough',
    'alignleft aligncenter alignright alignjustify | bullist numlist outdent indent',
    'link image media table emoticons | codesample code | searchreplace | preview fullscreen help'
  ].join(' | '),
  // 字体选项
  font_formats: '微软雅黑=Microsoft YaHei,Helvetica Neue,PingFang SC,sans-serif;苹果苹方=PingFang SC,Microsoft YaHei,sans-serif;宋体=simsun,serif;仿宋体=FangSong,serif;黑体=SimHei,sans-serif;Arial=arial,helvetica,sans-serif;Arial Black=arial black,avant garde;Book Antiqua=book antiqua,palatino;',
  // 字号选项
  fontsize_formats: '12px 14px 16px 18px 20px 22px 24px 26px 28px 30px 32px 34px 36px 38px 40px 42px 44px 46px 48px 50px 52px 54px 56px 58px 60px 62px 64px 66px 68px 70px 72px',
  // 行高选项
  lineheight_formats: '1 1.1 1.2 1.3 1.4 1.5 1.6 1.8 2.0 2.5 3.0',
  // 代码示例语言
  codesample_languages: [
    { text: 'HTML/XML', value: 'markup' },
    { text: 'JavaScript', value: 'javascript' },
    { text: 'CSS', value: 'css' },
    { text: 'PHP', value: 'php' },
    { text: 'Ruby', value: 'ruby' },
    { text: 'Python', value: 'python' },
    { text: 'Java', value: 'java' },
    { text: 'C', value: 'c' },
    { text: 'C#', value: 'csharp' },
    { text: 'C++', value: 'cpp' },
    { text: 'TypeScript', value: 'typescript' },
    { text: 'Vue', value: 'vue' },
    { text: 'React JSX', value: 'jsx' },
    { text: 'SQL', value: 'sql' },
    { text: 'JSON', value: 'json' },
    { text: 'Markdown', value: 'markdown' },
    { text: 'Shell', value: 'bash' },
    { text: 'Go', value: 'go' },
    { text: 'Rust', value: 'rust' }
  ],
  // 图片上传配置 - 使用统一的图片上传服务
  images_upload_handler: ImageUploadService.uploadTinyMCEImage,
  // 图片上传相关配置
  automatic_uploads: true, // 启用自动上传到服务器
  images_upload_credentials: false, // 是否发送凭据
  images_reuse_filename: true, // 重用文件名
  images_file_types: 'jpeg,jpg,jpe,jfi,jif,jfif,png,gif,bmp,webp', // 支持的图片格式
  
  // 启用URL转换，处理服务器返回的URL
  convert_urls: true,
  relative_urls: false,
  
  // 粘贴配置
  paste_data_images: true, // 允许粘贴图片
  paste_as_text: false, // 不强制粘贴为纯文本
  paste_remove_styles_if_webkit: false, // 保留样式
  // 其他配置
  branding: false, // 隐藏TinyMCE品牌信息
  elementpath: false, // 隐藏底部元素路径
  resize: 'both', // 允许调整大小
  statusbar: true, // 显示状态栏
  remove_script_host: false, // 保留脚本主机
  content_style: getContentStyle(theme.current.value === 'dark'),
  placeholder: props.placeholder,
  promotion: false, // 隐藏升级提示
  skin: theme.current.value === 'dark' ? 'oxide-dark' : 'oxide',  
  content_css: theme.current.value === 'dark' ? 'dark' : 'default',
  directionality: 'ltr',
  element_format: 'html',
  entities: '160,nbsp,38,amp,60,lt,62,gt',
  indent: false,
  keep_styles: false,
  paste_webkit_styles: 'none',
  paste_retain_style_properties: 'color font-size',
  // 初始化回调，用于调试
  init_instance_callback: (editor: any) => {
    console.log('TinyMCE编辑器初始化完成:', editor.id)
    console.log('编辑器模式:', editor.readonly ? '只读' : '可编辑')
    console.log('当前主题:', theme.current.value)
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