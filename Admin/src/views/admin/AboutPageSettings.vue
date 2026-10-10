<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  UploadOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import AboutPageService from '@/services/about'
import type { AboutPageInfo, AboutSocialLink } from '@/services/about'
import { ImageUploadService } from '@/services/upload'
import defaultAuthorAvatar from '@/assets/image/author-avatar.png'

const loading = ref(false)
const saving = ref(false)
const loadFailed = ref(false)
const uploading = ref<'avatar' | 'honors' | null>(null)
const form = ref<AboutPageInfo | null>(null)
const formRef = ref<FormInstance>()
const activeProjectKey = ref<string | string[]>('0')

// 上传图片使用 Admin 已有的 /uploads 代理；默认头像来自前台静态资源。
const resolvePreviewUrl = (url?: string | null): string | undefined => {
  const path = url?.trim()
  if (!path) return undefined
  return path === '/洛天依.png' ? defaultAuthorAvatar : path
}

const socialColumns = [
  { title: '标签', key: 'label', width: 180 },
  { title: '展示文字', key: 'value', width: 240 },
  { title: '链接', key: 'href' },
  { title: '操作', key: 'actions', width: 140, align: 'right' as const },
]

const requiredTextRules = (messageText: string, max: number): Rule[] => [
  { required: true, whitespace: true, message: messageText, trigger: 'blur' },
  { max, message: `不能超过 ${max} 个字符`, trigger: 'blur' },
]

const requiredListRules = (messageText: string, max: number): Rule[] => [
  { required: true, type: 'array', min: 1, message: messageText, trigger: 'change' },
  { type: 'array', max, message: `最多添加 ${max} 项`, trigger: 'change' },
]

const optionalListRules = (max: number): Rule[] => [
  { type: 'array', max, message: `最多添加 ${max} 项`, trigger: 'change' },
]

const linkRules = (required: boolean, allowMailto: boolean): Rule[] => [
  ...(required ? [{ required: true, whitespace: true, message: '请输入链接', trigger: 'blur' } as Rule] : []),
  { max: 500, message: '链接不能超过 500 个字符', trigger: 'blur' },
  {
    validator: async (_rule, value?: string | null) => {
      const link = value?.trim()
      if (!link) return
      const isInternalPath = link.startsWith('/') && !link.startsWith('//')
      if (isInternalPath || /^https?:\/\//i.test(link) || (allowMailto && /^mailto:/i.test(link))) return
      throw new Error(allowMailto ? '仅支持站内路径、HTTP(S) 或 mailto 链接' : '仅支持站内路径或 HTTP(S) 链接')
    },
    trigger: 'blur',
  },
]

const clone = (value: AboutPageInfo): AboutPageInfo => JSON.parse(JSON.stringify(value))

const uniqueTrimmed = (items: string[]): string[] =>
  [...new Set(items.map(item => item.trim()).filter(Boolean))]

const normalizeContent = (value: AboutPageInfo) => {
  value.author.name = value.author.name.trim()
  value.author.title = value.author.title.trim()
  value.author.avatar = value.author.avatar.trim()
  value.author.bio = value.author.bio.trim()
  value.motto = value.motto.trim()
  value.introParagraphs = value.introParagraphs.map(item => item.trim())
  value.skillGroups.forEach(group => {
    group.category = group.category.trim()
    group.skills = uniqueTrimmed(group.skills)
  })
  value.projects.forEach(project => {
    project.name = project.name.trim()
    project.description = project.description.trim()
    project.technologies = uniqueTrimmed(project.technologies)
    project.link = project.link?.trim() || null
  })
  value.socialLinks.forEach(link => {
    link.label = link.label.trim()
    link.value = link.value.trim()
    link.href = link.href.trim()
  })
  value.honors.summary = value.honors.summary.trim()
  value.honors.imageUrl = value.honors.imageUrl?.trim() || null
  value.contactText = value.contactText.trim()
  value.bannerDescription = value.bannerDescription.trim()
  value.metaDescription = value.metaDescription.trim()
}

const loadContent = async () => {
  if (loading.value) return
  loading.value = true
  loadFailed.value = false
  try {
    const response = await AboutPageService.get()
    form.value = clone(response.data)
    activeProjectKey.value = response.data.projects.length ? '0' : ''
    await nextTick()
    formRef.value?.clearValidate()
  } catch (error: any) {
    loadFailed.value = true
    if (!error?.isBusiness) message.error('关于页内容加载失败')
  } finally {
    loading.value = false
  }
}

const saveContent = async () => {
  if (!form.value || saving.value || uploading.value) return
  normalizeContent(form.value)
  try {
    await formRef.value?.validate()
  } catch (error: any) {
    if (!error?.isBusiness) message.warning('请先修正表单中标记的问题')
    return
  }

  saving.value = true
  try {
    const response = await AboutPageService.update(form.value)
    form.value = clone(response.data)
    message.success('关于页已更新')
  } catch (error: any) {
    if (!error?.isBusiness) message.error('关于页保存失败')
  } finally {
    saving.value = false
  }
}

const uploadImage = async (file: File, target: 'avatar' | 'honors') => {
  if (!form.value || uploading.value || saving.value) return
  uploading.value = target
  try {
    const result = await ImageUploadService.uploadImage(file)
    if (target === 'avatar') form.value.author.avatar = result.fileUrl
    else form.value.honors.imageUrl = result.fileUrl
    message.success('图片上传成功，保存页面后生效')
  } catch (error: any) {
    if (!error?.isBusiness) message.error(error?.message || '图片上传失败')
  } finally {
    uploading.value = null
  }
}

const beforeImageUpload = (file: File, target: 'avatar' | 'honors'): boolean => {
  void uploadImage(file, target)
  return false
}

const moveItem = <T>(items: T[], index: number, offset: -1 | 1) => {
  const target = index + offset
  if (target < 0 || target >= items.length) return
  const [item] = items.splice(index, 1)
  items.splice(target, 0, item)
}

const moveProject = (index: number, offset: -1 | 1) => {
  if (!form.value) return
  const target = index + offset
  moveItem(form.value.projects, index, offset)
  if (target >= 0 && target < form.value.projects.length) activeProjectKey.value = String(target)
}

const confirmRemove = (title: string, onConfirm: () => void) => {
  Modal.confirm({ title, content: '删除后需点击页面顶部的保存才会生效。', onOk: onConfirm })
}

const addIntro = () => form.value?.introParagraphs.push('')
const addSocialLink = () => form.value?.socialLinks.push({ label: '', value: '', href: '' })
const addSkillGroup = () => form.value?.skillGroups.push({ category: '', skills: [] })
const addProject = () => {
  if (!form.value) return
  form.value.projects.push({ name: '', description: '', technologies: [], link: null })
  activeProjectKey.value = String(form.value.projects.length - 1)
}
const removeProject = (index: number) => {
  if (!form.value) return
  form.value.projects.splice(index, 1)
  activeProjectKey.value = String(Math.max(0, Math.min(index, form.value.projects.length - 1)))
}
const socialRowKey = (_record: AboutSocialLink, index?: number) => String(index ?? 0)

onMounted(loadContent)
</script>

<template>
  <div class="about-settings">
    <a-card :bordered="false" class="page-card">
      <div class="page-header">
        <div>
          <h2>关于页管理</h2>
          <p>统一管理作者资料、技术栈、项目经历与联系信息。</p>
        </div>
        <a-space class="page-actions" wrap>
          <a-button :loading="loading" :disabled="saving || !!uploading" @click="loadContent"><ReloadOutlined />重新加载</a-button>
          <a-button type="primary" :loading="saving" :disabled="!form || loading || !!uploading" @click="saveContent"><SaveOutlined />保存并发布</a-button>
        </a-space>
      </div>
    </a-card>

    <a-spin :spinning="loading">
      <a-result v-if="loadFailed && !form" status="error" title="关于页内容加载失败">
        <template #extra><a-button type="primary" @click="loadContent">重试</a-button></template>
      </a-result>

      <a-form v-else-if="form" ref="formRef" :model="form" layout="vertical" class="about-form">
        <a-tabs type="card">
          <a-tab-pane key="basic" tab="基础信息" force-render>
            <div class="basic-editor-grid">
              <a-card :bordered="false" class="form-card author-settings-card">
                <div class="section-heading">
                  <h3>作者资料</h3>
                  <p>用于首页作者卡片与关于页的个人信息展示。</p>
                </div>

                <div class="avatar-editor">
                  <div class="avatar-preview">
                    <a-avatar :size="88" :src="resolvePreviewUrl(form.author.avatar)" :alt="form.author.name">
                      <template #icon><UserOutlined /></template>
                    </a-avatar>
                    <span>当前头像</span>
                  </div>
                  <div class="avatar-fields">
                    <div class="upload-action">
                      <a-upload :show-upload-list="false" :max-count="1" :disabled="!!uploading || saving" accept="image/*" :before-upload="(file: File) => beforeImageUpload(file, 'avatar')">
                        <a-button :loading="uploading === 'avatar'" :disabled="(!!uploading && uploading !== 'avatar') || saving"><UploadOutlined />更换头像</a-button>
                      </a-upload>
                      <p class="field-hint">建议使用正方形图片，大小不超过 5MB。</p>
                    </div>
                    <a-form-item label="头像地址" :name="['author', 'avatar']" :rules="linkRules(true, false)" class="avatar-url-item">
                      <a-input v-model:value="form.author.avatar" :maxlength="500" placeholder="/uploads/... 或 https://..." />
                    </a-form-item>
                  </div>
                </div>

                <div class="identity-fields">
                  <a-form-item label="作者姓名" :name="['author', 'name']" :rules="requiredTextRules('请输入作者姓名', 50)">
                    <a-input v-model:value="form.author.name" :maxlength="50" placeholder="前台展示的姓名" />
                  </a-form-item>
                  <a-form-item label="作者头衔" :name="['author', 'title']" :rules="requiredTextRules('请输入作者头衔', 80)">
                    <a-input v-model:value="form.author.title" :maxlength="80" placeholder="例如：开发者 · 技术分享" />
                  </a-form-item>
                </div>
                <a-form-item label="首页作者简介" :name="['author', 'bio']" :rules="requiredTextRules('请输入首页作者简介', 500)" extra="简要介绍你的关注领域，显示在首页侧栏。">
                  <a-textarea v-model:value="form.author.bio" :auto-size="{ minRows: 3, maxRows: 6 }" :maxlength="500" show-count />
                </a-form-item>
                <a-form-item label="座右铭" name="motto" :rules="requiredTextRules('请输入座右铭', 120)" class="last-form-item">
                  <a-input v-model:value="form.motto" :maxlength="120" placeholder="一句你想分享的话" />
                </a-form-item>
              </a-card>

              <a-card :bordered="false" class="form-card display-settings-card">
                <div class="section-heading">
                  <h3>页面描述</h3>
                  <p>设置关于页顶部文案与搜索摘要。</p>
                </div>
                <a-form-item label="顶部横幅描述" name="bannerDescription" :rules="requiredTextRules('请输入 Banner 描述', 200)" extra="显示在关于页顶部横幅中。">
                  <a-textarea v-model:value="form.bannerDescription" :auto-size="{ minRows: 2, maxRows: 4 }" :maxlength="200" show-count />
                </a-form-item>
                <a-form-item label="搜索摘要（SEO）" name="metaDescription" :rules="requiredTextRules('请输入 SEO 描述', 300)" extra="简要概括页面内容，作为搜索引擎的页面描述。" class="last-form-item">
                  <a-textarea v-model:value="form.metaDescription" :auto-size="{ minRows: 4, maxRows: 7 }" :maxlength="300" show-count />
                </a-form-item>
              </a-card>
            </div>

            <a-card :bordered="false" class="form-card intro-settings-card">
              <div class="section-heading">
                <h3>个人介绍</h3>
                <p>在关于页按以下顺序展示，最多添加 6 个段落。</p>
              </div>
              <div v-for="(_paragraph, index) in form.introParagraphs" :key="index" class="intro-row">
                <a-form-item :label="`段落 ${index + 1}`" :name="['introParagraphs', index]" :rules="requiredTextRules('介绍段落不能为空', 800)" class="grow-form-item">
                  <a-textarea v-model:value="form.introParagraphs[index]" :rows="3" :maxlength="800" show-count />
                </a-form-item>
                <a-space class="intro-actions">
                  <a-button type="text" aria-label="上移段落" title="上移段落" :disabled="index === 0" @click="moveItem(form.introParagraphs, index, -1)"><ArrowUpOutlined /></a-button>
                  <a-button type="text" aria-label="下移段落" title="下移段落" :disabled="index === form.introParagraphs.length - 1" @click="moveItem(form.introParagraphs, index, 1)"><ArrowDownOutlined /></a-button>
                  <a-button type="text" danger aria-label="删除段落" title="删除段落" :disabled="form.introParagraphs.length === 1" @click="confirmRemove('删除该介绍段落？', () => form!.introParagraphs.splice(index, 1))"><DeleteOutlined /></a-button>
                </a-space>
              </div>
              <a-button type="dashed" block :disabled="form.introParagraphs.length >= 6" @click="addIntro"><PlusOutlined />添加介绍段落</a-button>
            </a-card>
          </a-tab-pane>

          <a-tab-pane key="skills" tab="技术栈" force-render>
            <a-alert
              class="section-tip"
              type="info"
              show-icon
              message="这里只维护展示文字；Web 会统一以 #关键词 呈现，不再配置或匹配技术图标。"
            />
            <a-card v-for="(group, groupIndex) in form.skillGroups" :key="groupIndex" size="small" class="form-card skill-card">
              <template #title>技术栈分组 {{ groupIndex + 1 }}</template>
              <template #extra>
                <a-space>
                  <a-button type="text" :disabled="groupIndex === 0" @click="moveItem(form.skillGroups, groupIndex, -1)"><ArrowUpOutlined /></a-button>
                  <a-button type="text" :disabled="groupIndex === form.skillGroups.length - 1" @click="moveItem(form.skillGroups, groupIndex, 1)"><ArrowDownOutlined /></a-button>
                  <a-button type="text" danger :disabled="form.skillGroups.length === 1" @click="confirmRemove('删除该技术栈分组？', () => form!.skillGroups.splice(groupIndex, 1))"><DeleteOutlined /></a-button>
                </a-space>
              </template>
              <div class="skill-fields">
                <a-form-item label="分组名称" :name="['skillGroups', groupIndex, 'category']" :rules="requiredTextRules('请输入分组名称', 40)">
                  <a-input v-model:value="group.category" :maxlength="40" placeholder="例如：前端开发" />
                </a-form-item>
                <a-form-item
                  label="技能"
                  :name="['skillGroups', groupIndex, 'skills']"
                  :rules="requiredListRules('请至少添加一项技能', 20)"
                  extra="输入后按回车添加；顺序即前台展示顺序。"
                >
                  <a-select
                    v-model:value="group.skills"
                    mode="tags"
                    :token-separators="[',', '，']"
                    max-tag-count="responsive"
                    placeholder="输入技能名称后按回车"
                  />
                </a-form-item>
              </div>
            </a-card>
            <a-button type="dashed" block :disabled="form.skillGroups.length >= 8" @click="addSkillGroup"><PlusOutlined />添加技术栈分组</a-button>
          </a-tab-pane>

          <a-tab-pane key="projects" tab="项目经历" force-render>
            <a-alert
              class="section-tip"
              type="info"
              show-icon
              message="技术关键词仅用于关于页展示，不关联文章标签、文章搜索或内容聚合。"
            />
            <a-collapse v-model:active-key="activeProjectKey" accordion class="project-collapse">
              <a-collapse-panel
                v-for="(project, index) in form.projects"
                :key="String(index)"
                :header="project.name.trim() || `项目 ${index + 1}`"
                force-render
              >
                <template #extra>
                  <a-space @click.stop>
                    <a-button type="text" :disabled="index === 0" @click="moveProject(index, -1)"><ArrowUpOutlined /></a-button>
                    <a-button type="text" :disabled="index === form.projects.length - 1" @click="moveProject(index, 1)"><ArrowDownOutlined /></a-button>
                    <a-button type="text" danger :disabled="form.projects.length === 1" @click="confirmRemove('删除该项目？', () => removeProject(index))"><DeleteOutlined /></a-button>
                  </a-space>
                </template>
                <a-form-item label="项目名称" :name="['projects', index, 'name']" :rules="requiredTextRules('请输入项目名称', 100)">
                  <a-input v-model:value="project.name" :maxlength="100" />
                </a-form-item>
                <a-form-item label="项目描述" :name="['projects', index, 'description']" :rules="requiredTextRules('请输入项目描述', 2000)">
                  <a-textarea v-model:value="project.description" :rows="4" :maxlength="2000" show-count />
                </a-form-item>
                <a-form-item label="技术关键词" :name="['projects', index, 'technologies']" :rules="optionalListRules(12)" extra="纯展示字段，与博客文章标签系统完全独立。">
                  <a-select
                    v-model:value="project.technologies"
                    mode="tags"
                    :token-separators="[',', '，']"
                    max-tag-count="responsive"
                    placeholder="输入关键词后按回车"
                  />
                </a-form-item>
                <a-form-item label="项目链接（可选）" :name="['projects', index, 'link']" :rules="linkRules(false, false)">
                  <a-input v-model:value="project.link" placeholder="/ 或 https://..." />
                </a-form-item>
              </a-collapse-panel>
            </a-collapse>
            <a-button class="add-button" type="dashed" block :disabled="form.projects.length >= 20" @click="addProject"><PlusOutlined />添加项目</a-button>
          </a-tab-pane>

          <a-tab-pane key="contact" tab="荣誉与联系" force-render>
            <a-card :bordered="false" class="form-card">
              <a-form-item label="荣誉摘要" :name="['honors', 'summary']" :rules="requiredTextRules('请输入荣誉摘要', 500)">
                <a-textarea v-model:value="form.honors.summary" :rows="3" :maxlength="500" show-count />
              </a-form-item>
              <a-form-item label="荣誉区图片" :name="['honors', 'imageUrl']" :rules="linkRules(false, false)">
                <div class="image-field">
                  <a-image v-if="form.honors.imageUrl" class="honors-preview" :width="140" :src="resolvePreviewUrl(form.honors.imageUrl)" />
                  <div class="image-controls">
                    <a-input v-model:value="form.honors.imageUrl" :maxlength="500" placeholder="留空则使用前台默认图片" />
                    <div class="upload-action">
                      <a-upload :show-upload-list="false" :max-count="1" :disabled="!!uploading || saving" accept="image/*" :before-upload="(file: File) => beforeImageUpload(file, 'honors')">
                        <a-button :loading="uploading === 'honors'" :disabled="!!uploading || saving" :disabled="(!!uploading && uploading !== 'honors') || saving"><UploadOutlined />上传图片</a-button>
                      </a-upload>
                      <p class="field-hint">大小不超过 5MB，保存页面后生效。</p>
                    </div>
                  </div>
                </div>
              </a-form-item>
              <a-form-item label="联系区说明" name="contactText" :rules="requiredTextRules('请输入联系区说明', 300)">
                <a-textarea v-model:value="form.contactText" :rows="2" :maxlength="300" show-count />
              </a-form-item>

              <a-divider orientation="left">社交链接</a-divider>
              <a-table
                class="social-table"
                :columns="socialColumns"
                :data-source="form.socialLinks"
                :pagination="false"
                :row-key="socialRowKey"
                :scroll="{ x: 980 }"
                size="small"
              >
                <template #bodyCell="{ column, record, index }">
                  <template v-if="column.key === 'label'">
                    <a-form-item :name="['socialLinks', index, 'label']" :rules="requiredTextRules('请输入标签', 40)" class="table-form-item">
                      <a-input v-model:value="record.label" :maxlength="40" placeholder="例如：GitHub" />
                    </a-form-item>
                  </template>
                  <template v-else-if="column.key === 'value'">
                    <a-form-item :name="['socialLinks', index, 'value']" :rules="requiredTextRules('请输入展示文字', 100)" class="table-form-item">
                      <a-input v-model:value="record.value" :maxlength="100" />
                    </a-form-item>
                  </template>
                  <template v-else-if="column.key === 'href'">
                    <a-form-item :name="['socialLinks', index, 'href']" :rules="linkRules(true, true)" class="table-form-item">
                      <a-input v-model:value="record.href" placeholder="https://... 或 mailto:..." />
                    </a-form-item>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-space>
                      <a-button type="text" size="small" :disabled="index === 0" @click="moveItem(form.socialLinks, index, -1)"><ArrowUpOutlined /></a-button>
                      <a-button type="text" size="small" :disabled="index === form.socialLinks.length - 1" @click="moveItem(form.socialLinks, index, 1)"><ArrowDownOutlined /></a-button>
                      <a-popconfirm title="删除该社交链接？" @confirm="form.socialLinks.splice(index, 1)">
                        <a-button type="text" size="small"><DeleteOutlined /></a-button>
                      </a-popconfirm>
                    </a-space>
                  </template>
                </template>
              </a-table>
              <a-button class="add-button" type="dashed" block :disabled="form.socialLinks.length >= 8" @click="addSocialLink"><PlusOutlined />添加社交链接</a-button>
            </a-card>
          </a-tab-pane>
        </a-tabs>
      </a-form>
    </a-spin>
  </div>
</template>

<style scoped>
.about-settings {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
}

.page-card,
.form-card {
  border-radius: var(--lt-radius-xl);
  box-shadow: var(--lt-shadow-xs);
}

.page-card,
.form-card,
.section-tip {
  margin-bottom: var(--lt-space-lg);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-lg);
}

.page-header h2 {
  margin: 0;
  color: var(--lt-color-text);
}

.page-header p {
  margin: var(--lt-space-xs) 0 0;
  color: var(--lt-color-text-tertiary);
}

.page-actions {
  flex-shrink: 0;
}

.about-form :deep(.ant-form-item) {
  margin-bottom: var(--lt-space-lg);
}

.about-form :deep(.ant-input),
.about-form :deep(.ant-input-textarea) {
  min-width: 0;
}

.form-card :deep(.ant-card-body) {
  padding: var(--lt-space-xl);
}

.basic-editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(280px, 1fr);
  align-items: start;
  gap: var(--lt-space-lg);
}

.basic-editor-grid > .form-card {
  min-width: 0;
}

.section-heading {
  margin-bottom: var(--lt-space-xl);
}

.section-heading h3 {
  margin: 0;
  font-size: var(--lt-font-size-md);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-text);
}

.section-heading p {
  margin: var(--lt-space-xs) 0 0;
  color: var(--lt-color-text-secondary);
  font-size: var(--lt-font-size-sm);
  line-height: var(--lt-line-height-relaxed);
}

.avatar-editor {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  align-items: center;
  gap: var(--lt-space-lg);
  margin-bottom: var(--lt-space-xl);
  padding: var(--lt-space-lg);
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-color-bg-spotlight);
}

.avatar-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lt-space-sm);
}

.avatar-preview :deep(.ant-avatar) {
  flex-shrink: 0;
  background: var(--lt-color-primary-bg);
  color: var(--lt-color-primary);
  border: 1px solid var(--lt-color-primary-border);
}

.avatar-preview > span {
  color: var(--lt-color-text-secondary);
  font-size: var(--lt-font-size-xs);
}

.avatar-fields {
  min-width: 0;
}

.upload-action {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-sm) var(--lt-space-md);
}

.upload-action :deep(.ant-upload-wrapper) {
  flex-shrink: 0;
  width: auto;
}

.field-hint {
  margin: 0;
  color: var(--lt-color-text-secondary);
  font-size: var(--lt-font-size-xs);
  line-height: var(--lt-line-height-relaxed);
}

.avatar-fields .upload-action {
  margin-bottom: var(--lt-space-md);
}

.about-form :deep(.avatar-url-item),
.about-form :deep(.last-form-item) {
  margin-bottom: 0;
}

.identity-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-lg);
}

.intro-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--lt-space-sm);
  margin-bottom: var(--lt-space-md);
  padding: var(--lt-space-lg);
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: var(--lt-radius-lg);
}

.intro-actions {
  align-self: start;
}

.grow-form-item,
.table-form-item {
  margin-bottom: 0 !important;
}

.skill-card {
  border: 1px solid var(--lt-color-border-secondary);
}

.skill-fields {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  gap: var(--lt-space-lg);
}

.image-field {
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
}

.honors-preview {
  flex-shrink: 0;
  overflow: hidden;
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: var(--lt-radius-lg);
}

.image-controls {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-md);
}

.project-collapse {
  overflow: hidden;
  border-radius: var(--lt-radius-xl);
  background: var(--lt-color-bg-container);
}

.project-collapse :deep(.ant-collapse-content-box) {
  padding: var(--lt-space-xl);
}

.social-table {
  overflow: hidden;
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: var(--lt-radius-lg);
}

.social-table :deep(.ant-table-cell) {
  vertical-align: top;
}

.add-button {
  margin-top: var(--lt-space-lg);
}

@media (max-width: 1100px) {
  .basic-editor-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

@media (max-width: 768px) {
  .page-header,
  .image-field {
    align-items: stretch;
    flex-direction: column;
  }

  .skill-fields,
  .intro-row {
    grid-template-columns: 1fr;
  }

  .intro-actions {
    justify-self: end;
  }

  .form-card :deep(.ant-card-body),
  .project-collapse :deep(.ant-collapse-content-box) {
    padding: var(--lt-space-lg);
  }
}

@media (max-width: 480px) {
  .avatar-editor {
    grid-template-columns: 1fr;
    padding: var(--lt-space-md);
  }

  .identity-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .upload-action {
    align-items: flex-start;
  }
}
</style>
