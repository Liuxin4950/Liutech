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
import { ImageUploadService } from '@/services/upload'
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
import 'tinymce/plugins/quickbars' // 快速工具栏
// 导入表情符号数据库
import 'tinymce/plugins/emoticons/js/emojis'

console.log('TinyMCE编辑器组件正在加载...')

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

// TinyMCE图片上传处理函数
const handleImageUpload = ImageUploadService.uploadTinyMCEImage

// TinyMCE配置
const editorConfig = computed(() => ({
  height: props.height,
  menubar: false,
  readonly: false,
  language_url: '/tinymce/langs/zh_CN.js', // 中文语言包路径
  language: 'zh_CN',
  base_url: '/tinymce',
  suffix: '.min',
  plugins: [
    'advlist', 'autolink', 'lists', 'link', 'image', 'charmap', 'preview',
    'anchor', 'searchreplace', 'visualblocks', 'code', 'fullscreen',
    'insertdatetime', 'media', 'table', 'help', 'wordcount', 'emoticons',
    'codesample', 'nonbreaking', 'visualchars', 'directionality',
    'quickbars'
  ],
  toolbar: [
    'undo redo | formatselect fontselect fontsizeselect | bold italic underline strikethrough',
    'alignleft aligncenter alignright alignjustify | bullist numlist outdent indent',
    'forecolor backcolor | link image media table emoticons | codesample code | searchreplace',
    'preview fullscreen | help'
  ].join(' | '),
  quickbars_selection_toolbar: 'bold italic underline | forecolor backcolor | quicklink h2 h3 blockquote',
  quickbars_insert_toolbar: 'quickimage quicktable',
  // 字体选项
  font_formats: '微软雅黑=Microsoft YaHei,Helvetica Neue,PingFang SC,sans-serif;苹果苹方=PingFang SC,Microsoft YaHei,sans-serif;宋体=simsun,serif;仿宋体=FangSong,serif;黑体=SimHei,sans-serif;Arial=arial,helvetica,sans-serif;Arial Black=arial black,avant garde;Book Antiqua=book antiqua,palatino;',
  // 字号选项
  fontsize_formats: '12px 14px 16px 18px 20px 22px 24px 26px 28px 30px 32px 34px 36px 38px 40px 42px 44px 46px 48px 50px',
  // 代码示例语言
  codesample_languages: [
    { text: 'HTML/XML', value: 'markup' },
    { text: 'JavaScript', value: 'javascript' },
    { text: 'CSS', value: 'css' },
    { text: 'PHP', value: 'php' },
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
  // 自定义颜色调色板
  color_map: [
    '#0ea5e9', 'Primary Blue',
    '#0284c7', 'Primary Dark',
    '#38bdf8', 'Primary Light',
    '#22c55e', 'Success Green',
    '#f43f5e', 'Error Red',
    '#facc15', 'Warning Yellow',
    '#06b6d4', 'Info Cyan',
    '#6366f1', 'Purple',
    '#111827', 'Text Primary',
    '#374151', 'Text Secondary',
    '#6b7280', 'Text Tertiary',
    '#ffffff', 'White',
    '#f9fafb', 'Light Gray',
    '#f3f4f6', 'Gray 100',
    '#e5e7eb', 'Gray 200',
    '#d1d5db', 'Gray 300'
  ],
  color_cols: 8,
  custom_colors: true,
  color_default_foreground: '#111827',
  color_default_background: '#ffffff',
  // 文字格式预设
  style_formats: [
    {
      title: '标题',
      items: [
        { title: '标题 1', format: 'h1' },
        { title: '标题 2', format: 'h2' },
        { title: '标题 3', format: 'h3' },
        { title: '标题 4', format: 'h4' },
        { title: '标题 5', format: 'h5' },
        { title: '标题 6', format: 'h6' }
      ]
    },
    {
      title: '内联',
      items: [
        { title: '加粗', format: 'bold' },
        { title: '斜体', format: 'italic' },
        { title: '下划线', format: 'underline' },
        { title: '删除线', format: 'strikethrough' },
        { title: '上标', format: 'superscript' },
        { title: '下标', format: 'subscript' },
        { title: '代码', format: 'code' }
      ]
    },
    {
      title: '块级',
      items: [
        { title: '段落', format: 'p' },
        { title: '块引用', format: 'blockquote' },
        { title: '代码块', format: 'pre' },
        { title: '水平线', format: 'hr' }
      ]
    }
  ],
  // 图片上传配置
  images_upload_handler: handleImageUpload,
  automatic_uploads: true,
  images_upload_credentials: false,
  images_reuse_filename: true,
  images_file_types: 'jpeg,jpg,jpe,jfi,jif,jfif,png,gif,bmp,webp',
  // 启用URL转换
  convert_urls: true,
  relative_urls: false,
  // 粘贴配置
  paste_data_images: true,
  paste_as_text: false,
  paste_remove_styles_if_webkit: false,
  // 其他配置
  branding: false,
  elementpath: false,
  resize: 'both',
  statusbar: true,
  remove_script_host: false,
  content_style: `
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
  `,
  placeholder: props.placeholder,
  promotion: false,
  skin: 'oxide',
  content_css: 'default',
  directionality: 'ltr',
  element_format: 'html',
  entities: '160,nbsp,38,amp,60,lt,62,gt',
  indent: false,
  keep_styles: false,
  paste_webkit_styles: 'none',
  paste_retain_style_properties: 'color font-size',
  // 编辑器尺寸和布局
  min_height: 200,
  max_height: 600,
  autoresize_bottom_margin: 50,
  autoresize_overflow_padding: 16,
  paste_word_valid_elements: 'b,strong,i,em,h1,h2,h3,h4,h5,h6,p,div,ul,ol,li,table,tr,td,th,blockquote,code',
  // 链接配置
  link_context_toolbar: true,
  link_default_target: '_blank',
  link_title: false,
  // 图片配置
  image_caption: true,
  image_advtab: true,
  image_class_list: [
    { title: '无', value: '' },
    { title: '响应式', value: 'img-responsive' },
    { title: '圆角', value: 'img-rounded' },
    { title: '圆形', value: 'img-circle' }
  ],
  // 表格配置
  table_advtab: true,
  table_class_list: [
    { title: '无', value: '' },
    { title: '基础表格', value: 'table table-bordered' },
    { title: '条纹表格', value: 'table table-striped' }
  ],
  // 初始化回调
  init_instance_callback: (editor: any) => {
    console.log('TinyMCE编辑器初始化完成:', editor.id)
    // Ctrl+S 保存快捷键
    editor.shortcuts.add('ctrl+s', '保存内容', function () {
      console.log('保存内容...')
    })
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

/* TinyMCE样式覆盖 */
:deep(.tox) {
  border-radius: 8px;
  border: 1px solid #e1e5e9;
  transition: all 0.3s ease;
}

:deep(.tox-toolbar) {
  background: #ffffff;
  border-bottom: 1px solid #e1e5e9;
  transition: all 0.3s ease;
}

:deep(.tox-edit-area) {
  background: #ffffff;
  transition: all 0.3s ease;
}

:deep(.tox-statusbar) {
  background: #ffffff;
  border-top: 1px solid #e1e5e9;
  color: #333333;
  transition: all 0.3s ease;
}

:deep(.tox-toolbar__group) {
  border-color: #e1e5e9;
}

:deep(.tox-tbtn) {
  color: #333333;
}

:deep(.tox-tbtn:hover) {
  background: #ecf5ff;
}
</style>