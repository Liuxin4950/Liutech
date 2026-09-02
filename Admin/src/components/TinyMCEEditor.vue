<template>
  <div class="tinymce-editor">
    <div v-if="!editorLoaded" class="editor-loading">正在加载编辑器...</div>
    <Editor
      ref="editorComponent"
      v-model="content"
      :init="editorConfig"
      :disabled="disabled"
      license-key="gpl"
      @init="onEditorInit"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Editor from '@tinymce/tinymce-vue'
import type { Editor as TinyEditor } from 'tinymce'
import theme from '@/utils/theme'
import { ImageUploadService } from '@/services/upload'
import richContentCss from '@/assets/styles/rich-content.css?inline'

import 'tinymce/tinymce'
import 'tinymce/themes/silver'
import 'tinymce/icons/default'
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
import 'tinymce/plugins/codesample'
import 'tinymce/plugins/quickbars'
import 'tinymce/plugins/emoticons/js/emojis'

interface Props {
  modelValue?: string
  disabled?: boolean
  height?: number
  placeholder?: string
}

interface Emits {
  (event: 'update:modelValue', value: string): void
  (event: 'change', value: string): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  disabled: false,
  height: 800,
  placeholder: ''
})

const emit = defineEmits<Emits>()
const editorLoaded = ref(false)
const editorComponent = ref<{ rerender: (init: Record<string, unknown>) => void } | null>(null)
const editorTheme = computed(() => theme.current.value === 'dark' ? 'dark' : 'light')

const content = computed({
  get: () => props.modelValue,
  set: (value: string) => {
    emit('update:modelValue', value)
    emit('change', value)
  }
})

const SYSTEM_FONT_STACK = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Helvetica Neue', sans-serif"
const EDITOR_FONT_FORMATS = `系统默认=${SYSTEM_FONT_STACK};微软雅黑=Microsoft YaHei,Helvetica Neue,PingFang SC,sans-serif;苹果苹方=PingFang SC,Microsoft YaHei,sans-serif;宋体=simsun,serif;仿宋体=FangSong,serif;黑体=SimHei,sans-serif;Arial=arial,helvetica,sans-serif;Consolas=consolas,monaco,monospace;`

const editorConfig = computed(() => ({
  height: props.height,
  min_height: 360,
  menubar: 'edit view insert format tools table help',
  language_url: '/tinymce/langs/zh_CN.js',
  language: 'zh_CN',
  content_language: 'zh-CN',
  base_url: '/tinymce',
  suffix: '.min',
  skin: editorTheme.value === 'dark' ? 'oxide-dark' : 'oxide',
  content_css: false,
  content_style: richContentCss,
  body_class: `rich-content liutech-editor-content liutech-editor-${editorTheme.value}`,
  plugins: [
    'advlist', 'autolink', 'lists', 'link', 'image', 'charmap', 'preview',
    'anchor', 'searchreplace', 'visualblocks', 'code', 'fullscreen',
    'insertdatetime', 'media', 'table', 'help', 'wordcount', 'emoticons',
    'codesample', 'quickbars'
  ],
  toolbar: [
    'undo redo | blocks fontfamily fontsize | bold italic underline strikethrough',
    'forecolor backcolor | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent',
    'blockquote link image media table codesample | removeformat preview fullscreen'
  ].join(' | '),
  toolbar_mode: 'sliding',
  quickbars_selection_toolbar: 'bold italic underline | forecolor backcolor | quicklink h2 h3 blockquote',
  quickbars_insert_toolbar: 'quickimage quicktable',
  contextmenu: 'link image table',
  font_family_formats: EDITOR_FONT_FORMATS,
  font_size_formats: '12px 14px 16px 18px 20px 24px 28px 32px 40px 48px',
  line_height_formats: '1 1.2 1.4 1.6 1.8 2 2.5',
  codesample_languages: [
    { text: 'HTML/XML', value: 'markup' },
    { text: 'JavaScript', value: 'javascript' },
    { text: 'TypeScript', value: 'typescript' },
    { text: 'Vue', value: 'vue' },
    { text: 'React JSX', value: 'jsx' },
    { text: 'CSS', value: 'css' },
    { text: 'Java', value: 'java' },
    { text: 'Python', value: 'python' },
    { text: 'Go', value: 'go' },
    { text: 'Rust', value: 'rust' },
    { text: 'C', value: 'c' },
    { text: 'C++', value: 'cpp' },
    { text: 'C#', value: 'csharp' },
    { text: 'PHP', value: 'php' },
    { text: 'Ruby', value: 'ruby' },
    { text: 'SQL', value: 'sql' },
    { text: 'JSON', value: 'json' },
    { text: 'Markdown', value: 'markdown' },
    { text: 'Shell', value: 'bash' }
  ],
  color_map: [
    '#202124', '标题灰', '#3C4043', '正文灰', '#5F6368', '次要灰', '#FFFFFF', '白色',
    '#4A69D1', '主题靛蓝', '#8AB4F8', '浅蓝', '#F0B8C0', '淡雅粉', '#34A853', '成功绿',
    '#FBBC04', '警告黄', '#EA4335', '错误红', '#4285F4', '信息蓝', '#000000', '黑色'
  ],
  color_cols: 6,
  custom_colors: true,
  images_upload_handler: ImageUploadService.uploadTinyMCEImage,
  automatic_uploads: true,
  images_upload_credentials: false,
  images_reuse_filename: true,
  images_file_types: 'jpeg,jpg,jpe,jfi,jif,jfif,png,gif,bmp,webp',
  paste_data_images: true,
  paste_as_text: false,
  convert_urls: false,
  relative_urls: false,
  remove_script_host: false,
  link_context_toolbar: true,
  link_default_target: '_blank',
  link_title: false,
  image_caption: true,
  image_advtab: true,
  image_class_list: [
    { title: '无', value: '' },
    { title: '响应式', value: 'img-responsive' },
    { title: '圆角', value: 'img-rounded' },
    { title: '圆形', value: 'img-circle' }
  ],
  browser_spellcheck: true,
  placeholder: props.placeholder || undefined,
  resize: true,
  statusbar: true,
  elementpath: false,
  branding: false,
  promotion: false
}))

const onEditorInit = (_event: unknown, editor: TinyEditor) => {
  const doc = editor.getDoc()
  doc.documentElement.classList.add(`liutech-editor-${editorTheme.value}`)
  editorLoaded.value = true
}

// TinyMCE 的 skin 不能运行时热切换。Vue 集成组件的 rerender 会先保存正文、
// 安全销毁实例再重建，从而切换官方皮肤且不维护脆弱的 .tox 深层覆盖。
watch(editorTheme, () => {
  editorLoaded.value = false
  editorComponent.value?.rerender(editorConfig.value)
})
</script>

<style scoped>
.tinymce-editor {
  width: 100%;
}

.editor-loading {
  margin-bottom: 10px;
  padding: 20px;
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: 8px;
  background: var(--lt-color-bg-spotlight);
  color: var(--lt-color-text-tertiary);
  text-align: center;
}
</style>
