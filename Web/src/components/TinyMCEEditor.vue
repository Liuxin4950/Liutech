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
// 颜色相关功能已集成到TinyMCE 7.x核心中，无需单独导入
import 'tinymce/plugins/quickbars' // 快速工具栏
// 这些插件在当前版本中不可用，暂时注释掉
// import 'tinymce/plugins/hr' // 水平线
// import 'tinymce/plugins/pagebreak' // 分页符
// import 'tinymce/plugins/template' // 模板
// import 'tinymce/plugins/save' // 保存
// import 'tinymce/plugins/autosave' // 自动保存
// import 'tinymce/plugins/print' // 打印
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
  // 自定义颜色调色板 - 匹配主题色
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
  // 自定义颜色选择器
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
    },
    {
      title: '对齐',
      items: [
        { title: '左对齐', format: 'alignleft' },
        { title: '居中', format: 'aligncenter' },
        { title: '右对齐', format: 'alignright' },
        { title: '两端对齐', format: 'alignjustify' }
      ]
    },
    {
      title: '颜色',
      items: [
        { title: '主要文字', inline: 'span', styles: { color: '#111827' } },
        { title: '次要文字', inline: 'span', styles: { color: '#374151' } },
        { title: '主要蓝色', inline: 'span', styles: { color: '#0ea5e9' } },
        { title: '成功绿色', inline: 'span', styles: { color: '#22c55e' } },
        { title: '错误红色', inline: 'span', styles: { color: '#f43f5e' } },
        { title: '警告黄色', inline: 'span', styles: { color: '#facc15' } }
      ]
    }
  ],
  // 模板功能暂时禁用，因为template插件不可用
  // templates: [
  //   {
  //     title: '文章模板',
  //     description: '标准文章格式',
  //     content: '<h2>文章标题</h2><p>在这里开始你的文章内容...</p><h3>第一部分</h3><p>详细内容...</p><h3>第二部分</h3><p>详细内容...</p><h3>总结</h3><p>总结内容...</p>'
  //   },
  //   {
  //     title: '技术文档',
  //     description: '技术文档格式',
  //     content: '<h1>技术文档标题</h1><h2>概述</h2><p>简要描述...</p><h2>前置条件</h2><ul><li>条件1</li><li>条件2</li></ul><h2>步骤</h2><ol><li>第一步</li><li>第二步</li></ol><h2>注意事项</h2><blockquote><p>重要提示...</p></blockquote>'
  //   },
  //   {
  //     title: '代码示例',
  //     description: '包含代码块的模板',
  //     content: '<h2>代码示例</h2><p>下面是一个代码示例：</p><pre><code>// 你的代码在这里</code></pre><h3>说明</h3><p>代码说明...</p>'
  //   }
  // ],
  // 格式选项
  formats: {
    alignleft: { selector: 'p,h1,h2,h3,h4,h5,h6,td,th,div,ul,ol,li,table,img', classes: 'text-left' },
    aligncenter: { selector: 'p,h1,h2,h3,h4,h5,h6,td,th,div,ul,ol,li,table,img', classes: 'text-center' },
    alignright: { selector: 'p,h1,h2,h3,h4,h5,h6,td,th,div,ul,ol,li,table,img', classes: 'text-right' },
    alignjustify: { selector: 'p,h1,h2,h3,h4,h5,h6,td,th,div,ul,ol,li,table,img', classes: 'text-justify' },
    bold: { inline: 'strong' },
    italic: { inline: 'em' },
    underline: { inline: 'span', styles: { 'text-decoration': 'underline' } },
    strikethrough: { inline: 'span', styles: { 'text-decoration': 'line-through' } }
  },
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
  // 高级功能配置 - 保存相关功能暂时禁用
  // autosave_interval: '30s',
  // autosave_prefix: 'tinymce-autosave-{path}{query}-{id}-',
  // autosave_restore_when_empty: true,
  // autosave_retention: '1440m',
  // save_enablewhendirty: true,
  // save_onsavecallback: function () {
  //   console.log('内容已保存')
  // },
  // 编辑器尺寸和布局
  min_height: 300,
  max_height: 800,
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
  // 初始化回调，用于调试
  init_instance_callback: (editor: any) => {
    console.log('TinyMCE编辑器初始化完成:', editor.id)
    console.log('编辑器模式:', editor.readonly ? '只读' : '可编辑')
    console.log('当前主题:', theme.current.value)
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