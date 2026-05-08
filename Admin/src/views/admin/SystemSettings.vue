<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SaveOutlined, SettingOutlined } from '@ant-design/icons-vue'
import SettingsService from '@/services/settings'
import type { SystemSetting, GroupedSettings, SettingUpdateItem } from '@/services/settings'

const loading = ref(false)
const savingGroup = ref<string | null>(null)
const grouped = ref<GroupedSettings>({})

// 每个分组的表单数据（key -> value）
const formData = ref<Record<string, string>>({})

// 分组中文名称映射
const groupLabels: Record<string, string> = {
  site: '站点基本设置',
  filing: '备案与统计',
  comment: '评论设置',
  upload: '上传设置',
  tts: '语音设置',
  other: '其他'
}

// 设置项的中文标签（从 description 中提取，或手动映射）
const fieldLabels: Record<string, string> = {
  'site.name': '站点名称',
  'site.description': '站点描述',
  'site.keywords': 'SEO 关键词',
  'site.logo_url': '站点 Logo URL',
  'site.favicon_url': 'Favicon URL',
  'site.footer_text': '页脚文本',
  'site.icp_number': 'ICP 备案号',
  'site.analytics_code': '统计代码',
  'comment.need_review': '评论需要审核',
  'upload.max_size_mb': '最大上传大小 (MB)'
}

// 布尔类型的设置项
const booleanKeys = new Set(['comment.need_review'])

const loadSettings = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const res = await SettingsService.getGrouped()
    grouped.value = res.data

    // 初始化表单数据
    const form: Record<string, string> = {}
    for (const settings of Object.values(res.data)) {
      for (const s of settings) {
        form[s.settingKey] = s.settingValue ?? ''
      }
    }
    formData.value = form
  } catch (error: any) {
    message.error(error?.message || '加载系统设置失败')
  } finally {
    loading.value = false
  }
}

const saveGroup = async (group: string) => {
  if (savingGroup.value) return
  savingGroup.value = group
  try {
    const settings: SettingUpdateItem[] = (grouped.value[group] || []).map(s => ({
      key: s.settingKey,
      value: formData.value[s.settingKey] ?? '',
      description: s.description
    }))
    await SettingsService.batchUpdate(settings)
    message.success(`${groupLabels[group] || group} 已保存`)
    await loadSettings()
  } catch (error: any) {
    message.error(error?.message || '保存失败')
  } finally {
    savingGroup.value = null
  }
}

const getLabel = (key: string, description?: string): string => {
  return fieldLabels[key] || description || key
}

onMounted(() => {
  loadSettings()
})
</script>

<template>
  <div class="page-container">
    <a-card :bordered="false" class="page-header-card">
      <div class="page-header">
        <div class="page-header-left">
          <SettingOutlined class="page-header-icon" />
          <div>
            <h3 class="page-title">系统设置</h3>
            <p class="page-desc">管理站点名称、SEO、评论、上传等全局配置</p>
          </div>
        </div>
        <a-button @click="loadSettings" :loading="loading">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </a-card>

    <a-spin :spinning="loading">
      <template v-for="(settings, group) in grouped" :key="group">
        <a-card :bordered="false" class="settings-group-card">
          <template #title>{{ groupLabels[group] || group }}</template>
          <template #extra>
            <a-button
              type="primary"
              size="small"
              :loading="savingGroup === group"
              @click="saveGroup(group)"
            >
              <template #icon><SaveOutlined /></template>
              保存
            </a-button>
          </template>

          <a-form layout="vertical">
            <a-row :gutter="16">
              <a-col
                v-for="setting in settings"
                :key="setting.id"
                :xs="24"
                :sm="12"
                :lg="8"
              >
                <!-- 布尔类型用 Switch -->
                <a-form-item
                  v-if="booleanKeys.has(setting.settingKey)"
                  :label="getLabel(setting.settingKey, setting.description)"
                >
                  <a-switch
                    :checked="formData[setting.settingKey] === 'true'"
                    @change="(val: boolean) => formData[setting.settingKey] = val ? 'true' : 'false'"
                    checked-children="是"
                    un-checked-children="否"
                  />
                  <div class="field-desc" v-if="setting.description">{{ setting.description }}</div>
                </a-form-item>

                <!-- 其他用 Input -->
                <a-form-item
                  v-else
                  :label="getLabel(setting.settingKey, setting.description)"
                >
                  <a-input
                    v-model:value="formData[setting.settingKey]"
                    :placeholder="setting.description || setting.settingKey"
                    allow-clear
                  />
                  <div class="field-desc" v-if="setting.description">{{ setting.description }}</div>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-card>
      </template>

      <a-empty v-if="!loading && Object.keys(grouped).length === 0" description="暂无设置项" />
    </a-spin>
  </div>
</template>

<style scoped>
.page-container {
  padding: 24px;
  background: var(--bg-main);
  min-height: 100vh;
}

.page-header-card {
  margin-bottom: 16px;
  border-radius: 12px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-header-icon {
  font-size: 28px;
  color: var(--color-primary);
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.page-desc {
  margin: 2px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.settings-group-card {
  margin-bottom: 16px;
  border-radius: 12px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
}

.field-desc {
  color: #9ca3af;
  font-size: 12px;
  margin-top: 4px;
}
</style>
