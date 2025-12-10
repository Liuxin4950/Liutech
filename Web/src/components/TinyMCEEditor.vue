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
import 'tinymce/plugins/quickbars' // 快速工具栏
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
  height: 800,
  placeholder: '请输入文章内容...'
})

const emit = defineEmits<Emits>()

// 编辑器内容
const content = ref(props.modelValue)
// 编辑器加载状态
const editorLoaded = ref(false)

// 动态主题样式 - 完全匹配主题系统
const getContentStyle = (isDark: boolean) => {
  const lightStyle = `
    body { 
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif; 
      font-size: 16px;
      line-height: 1.8;
      color: #3C4043;
      background-color: #FFFFFF;
      padding: 20px;
      word-wrap: break-word;
    }
    
    /* 标题样式 */
    h1, h2, h3, h4, h5, h6 { 
      margin: 24px 0 16px 0; 
      font-weight: 600; 
      color: #202124;
      line-height: 1.4;
    }
    h1 { font-size: 2em; }
    h2 { font-size: 1.7em; }
    h3 { font-size: 1.4em; }
    h4 { font-size: 1.2em; }
    h5 { font-size: 1.1em; }
    h6 { font-size: 1em; }
    
    /* 段落样式 */
    p { 
      margin: 16px 0; 
      color: #3C4043;
      line-height: 1.8;
    }
    
    /* 链接样式 */
    a {
      color: #4A69D1;
      text-decoration: none;
      transition: color 0.2s ease;
    }
    a:hover {
      color: #3A4F9A;
      text-decoration: underline;
    }
    
    /* 列表样式 */
    ul, ol {
      margin: 16px 0;
      padding-left: 24px;
      color: #3C4043;
    }
    li {
      margin: 8px 0;
      line-height: 1.6;
    }
    
    /* 引用块样式 */
    blockquote { 
      border-left: 4px solid #4A69D1; 
      margin: 20px 0; 
      padding: 16px 20px; 
      background: #F8F9FA;
      color: #5F6368;
      font-style: italic;
      border-radius: 0 8px 8px 0;
    }
    blockquote p {
      margin: 0;
    }
    
    /* 代码样式 */
    code { 
      background: #F7F9FC; 
      padding: 2px 6px; 
      border-radius: 4px; 
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      color: #EA4335;
      font-size: 0.9em;
      border: 1px solid #F1F3F4;
    }
    
    pre { 
      background: #F8F9FA; 
      border: 1px solid #E8EAED; 
      border-radius: 8px; 
      padding: 16px; 
      overflow-x: auto;
      color: #3C4043;
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      font-size: 0.9em;
      line-height: 1.5;
    }
    pre code {
      background: none;
      border: none;
      padding: 0;
      color: inherit;
      font-size: inherit;
    }
    
    /* 表格样式 */
    table { 
      border-collapse: collapse; 
      width: 100%; 
      margin: 20px 0;
      background-color: #FFFFFF;
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 1px 2px rgba(32, 33, 36, 0.1);
    }
    table td, table th { 
      border: 1px solid #E8EAED; 
      padding: 12px 16px; 
      color: #3C4043;
      text-align: left;
    }
    table th { 
      background-color: #F8F9FA; 
      font-weight: 600; 
      color: #202124;
      border-bottom: 2px solid #4A69D1;
    }
    table tr:last-child td {
      border-bottom: none;
    }
    table tr:hover {
      background-color: #F1F3F4;
    }
    
    /* 图片样式 */
    img { 
      max-width: 100%; 
      height: auto; 
      border-radius: 8px;
      box-shadow: 0 2px 6px rgba(32, 33, 36, 0.12), 0 1px 3px rgba(32, 33, 36, 0.08);
      margin: 16px 0;
      display: block;
      margin-left: auto;
      margin-right: auto;
    }
    
    /* 分隔线样式 */
    hr {
      border: none;
      height: 2px;
      background: linear-gradient(to right, transparent, #E8EAED, transparent);
      margin: 32px 0;
    }
    
    /* 强调文本 */
    strong, b {
      color: #202124;
      font-weight: 600;
    }
    
    em, i {
      color: #5F6368;
      font-style: italic;
    }
    
    del, s {
      color: #9AA0A6;
      text-decoration: line-through;
    }
    
    u {
      text-decoration: underline;
      color: #F0B8C0;
    }
    
    mark {
      background-color: #FEF7E0;
      color: #3C4043;
      padding: 2px 4px;
      border-radius: 3px;
    }
  `
  
  const darkStyle = `
    body { 
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif; 
      font-size: 16px;
      line-height: 1.8;
      color: #E8EAED;
      background-color: #202124;
      padding: 20px;
      word-wrap: break-word;
    }
    
    /* 标题样式 */
    h1, h2, h3, h4, h5, h6 { 
      margin: 24px 0 16px 0; 
      font-weight: 600; 
      color: #FFFFFF;
      line-height: 1.4;
    }
    h1 { font-size: 2em; }
    h2 { font-size: 1.7em; }
    h3 { font-size: 1.4em; }
    h4 { font-size: 1.2em; }
    h5 { font-size: 1.1em; }
    h6 { font-size: 1em; }
    
    /* 段落样式 */
    p { 
      margin: 16px 0; 
      color: #E8EAED;
      line-height: 1.8;
    }
    
    /* 链接样式 */
    a {
      color: #8AB4F8;
      text-decoration: none;
      transition: color 0.2s ease;
    }
    a:hover {
      color: #66B1FF;
      text-decoration: underline;
    }
    
    /* 列表样式 */
    ul, ol {
      margin: 16px 0;
      padding-left: 24px;
      color: #E8EAED;
    }
    li {
      margin: 8px 0;
      line-height: 1.6;
    }
    
    /* 引用块样式 */
    blockquote { 
      border-left: 4px solid #8AB4F8; 
      margin: 20px 0; 
      padding: 16px 20px; 
      background: #2D2F30;
      color: #9AA0A6;
      font-style: italic;
      border-radius: 0 8px 8px 0;
    }
    blockquote p {
      margin: 0;
    }
    
    /* 代码样式 */
    code { 
      background: #3C4043; 
      padding: 2px 6px; 
      border-radius: 4px; 
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      color: #4285F4;
      font-size: 0.9em;
      border: 1px solid #5F6368;
    }
    
    pre { 
      background: #2D2F30; 
      border: 1px solid #5F6368; 
      border-radius: 8px; 
      padding: 16px; 
      overflow-x: auto;
      color: #E8EAED;
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      font-size: 0.9em;
      line-height: 1.5;
    }
    pre code {
      background: none;
      border: none;
      padding: 0;
      color: inherit;
      font-size: inherit;
    }
    
    /* 表格样式 */
    table { 
      border-collapse: collapse; 
      width: 100%; 
      margin: 20px 0;
      background-color: #2D2F30;
      border-radius: 8px;
      overflow: hidden;
    }
    table td, table th { 
      border: 1px solid #5F6368; 
      padding: 12px 16px; 
      color: #E8EAED;
      text-align: left;
    }
    table th { 
      background-color: #2D2F30; 
      font-weight: 600; 
      color: #FFFFFF;
      border-bottom: 2px solid #8AB4F8;
    }
    table tr:last-child td {
      border-bottom: none;
    }
    table tr:hover {
      background-color: #3C4043;
    }
    
    /* 图片样式 */
    img { 
      max-width: 100%; 
      height: auto; 
      border-radius: 8px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
      margin: 16px 0;
      display: block;
      margin-left: auto;
      margin-right: auto;
    }
    
    /* 分隔线样式 */
    hr {
      border: none;
      height: 2px;
      background: linear-gradient(to right, transparent, #5F6368, transparent);
      margin: 32px 0;
    }
    
    /* 强调文本 */
    strong, b {
      color: #FFFFFF;
      font-weight: 600;
    }
    
    em, i {
      color: #9AA0A6;
      font-style: italic;
    }
    
    del, s {
      color: #80868B;
      text-decoration: line-through;
    }
    
    u {
      text-decoration: underline;
      color: #F8B4B4;
    }
    
    mark {
      background-color: #856404;
      color: #E8EAED;
      padding: 2px 4px;
      border-radius: 3px;
    }
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
  base_url: '/tinymce', // 设置TinyMCE资源基础路径
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
  // 自定义颜色调色板 - 完全匹配主题色系
  color_map: [
    // 主色系
    '#4A69D1', '主色-现代靛蓝',
    '#3A4F9A', '主色-深靛蓝',
    '#8AB4F8', '主色-亮蓝（深色模式）',
    '#F7F9FC', '主色-极淡灰蓝',
    
    // 点缀色系
    '#F0B8C0', '淡雅粉',
    '#E89AA8', '淡雅粉-hover',
    '#F8B4B4', '深色模式-温暖琥珀',
    
    // 状态色系
    '#34A853', '成功-绿',
    '#FBBC04', '警告-黄',
    '#EA4335', '错误-红',
    '#4285F4', '信息-蓝',
    
    // 文本色系
    '#202124', '标题-深灰',
    '#3C4043', '正文-中灰',
    '#5F6368', '次要-浅灰',
    '#9AA0A6', '弱化-更淡',
    '#FFFFFF', '白色',
    
    // 背景色系
    '#F8F9FA', '柔和背景',
    '#F7F9FC', '元素背景',
    '#F1F3F4', '悬停背景',
    '#E8F0FE', '标签背景',
    
    // 边框色系
    '#E8EAED', '常规边框',
    '#BDC1C6', '深边框',
    '#F1F3F4', '极浅边框',
    
    // 深色模式专用色
    '#202124', '深色-主背景',
    '#2D2F30', '深色-卡片背景',
    '#3C4043', '深色-元素背景',
    '#5F6368', '深色-边框'
  ],
  // 自定义颜色选择器
  color_cols: 8,
  custom_colors: true,
  color_default_foreground: '#3C4043',
  color_default_background: '#FFFFFF',
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
      title: '主题颜色',
      items: [
        { title: '标题文字', inline: 'span', styles: { color: '#202124' } },
        { title: '正文文字', inline: 'span', styles: { color: '#3C4043' } },
        { title: '次要文字', inline: 'span', styles: { color: '#5F6368' } },
        { title: '主色靛蓝', inline: 'span', styles: { color: '#4A69D1' } },
        { title: '深主色', inline: 'span', styles: { color: '#3A4F9A' } },
        { title: '淡雅粉', inline: 'span', styles: { color: '#F0B8C0' } },
        { title: '成功绿色', inline: 'span', styles: { color: '#34A853' } },
        { title: '警告黄色', inline: 'span', styles: { color: '#FBBC04' } },
        { title: '错误红色', inline: 'span', styles: { color: '#EA4335' } },
        { title: '信息蓝色', inline: 'span', styles: { color: '#4285F4' } }
      ]
    }
  ],
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