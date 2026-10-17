<script setup lang="ts">
import { ref, watch, onUnmounted, onDeactivated, computed } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  DeleteOutlined,
  CloudOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  SortAscendingOutlined,
  SearchOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue'
import { useTablePage, useCrudActions, useModalForm } from '@/composables'
import { useTableColumnPrefs } from '@/composables/useTableColumnPrefs'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useTableExport } from '@/composables/useTableExport'
import TableExportButton from '@/components/TableExportButton.vue'
import LtFilterBar from '@/components/LtFilterBar.vue'
import LtTableCard from '@/components/LtTableCard.vue'
import type { Music } from '../../services/music'
import musicService from '../../services/music'
import { formatDateTime } from '../../utils/utils'
import { ImageUploadService, pickUploadFile } from '../../services/upload'

// 扩展 Music 类型以支持前端状态
interface MusicItem extends Music {
  statusLoading?: boolean
}

// 编辑表单类型
interface MusicEditForm {
  title: string
  artist: string
  sortOrder: number
  status: number
  coverUrl: string
}

// 状态选项
const statusOptions = [
  { label: '全部', value: undefined },
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

// ============== 表格列 ==============
const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '封面', dataIndex: 'coverUrl', key: 'cover', width: 80 },
  { title: '歌曲名', dataIndex: 'title', key: 'title', width: 200 },
  { title: '艺术家', dataIndex: 'artist', key: 'artist', width: 150 },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const }
]

const columnPrefsCtrl = useTableColumnPrefs('music', columns, { alwaysVisible: ["action"] })
const prefColumns = columnPrefsCtrl.prefColumns

// ============== 表格页面 ==============
const {
  loading, dataSource, selectedRowKeys, searchParams, pagination,
  load, handleSearch, handleReset, handleTableChange, onSelectChange, clearSelection
} = useTablePage<MusicItem, { status: number | undefined; keyword: string }>({
  // 后端音乐列表返回全量数组（不分页）：单页装下全量，避免分页器显示虚假多页、
  // 翻页后每页重复全量数据；排序协议也要求 dataSource 保持完整歌单
  loadFn: async (params) => {
    const res = await musicService.getMusicList(params)
    if (res.code === 200) {
      const list = (Array.isArray(res.data) ? res.data : []) as unknown as MusicItem[]
      return { code: 200, message: res.message, data: { records: list, total: list.length } }
    }
    return { code: res.code || 500, message: res.message || '加载失败', data: { records: [] as MusicItem[], total: 0 } }
  },
  defaultPageSize: 500,
  defaultSearchParams: { status: undefined, keyword: '' },
  loadErrorMessage: '加载音乐列表失败'
})
pagination.showSizeChanger = false
pagination.showQuickJumper = false

const exportCtrl = useTableExport({
  columns: prefColumns,
  rows: dataSource,
  filename: 'music',
})

// ============== CRUD 操作 ==============
const {
  handleDelete, handleBatchDelete
} = useCrudActions({
  deleteFn: (id) => musicService.deleteMusic(id),
  batchDeleteFn: (ids) => musicService.batchDelete(ids),
  onRefresh: load,
  clearSelection,
  entityName: '音乐',
  mode: 'hard'
})

// ============== 编辑弹窗 ==============
const editRules = {
  title: [{ required: true, whitespace: true, message: '请输入歌曲名', trigger: 'blur' }]
}

const {
  modalVisible: editModalVisible,
  confirmLoading: editLoading,
  formRef: editFormRef,
  formModel: editForm,
  isEdit,
  editingId,
  handleOk: submitEditForm,
  handleCancel: handleEditCancel
} = useModalForm<MusicEditForm>({
  updateFn: (id, data) => musicService.updateMusic(id, {
    title: data.title,
    artist: data.artist,
    sortOrder: data.sortOrder,
    status: data.status,
    cover: data.coverUrl,
    fullAudio: editFullAudio.value || undefined,
    vocalAudio: editVocalAudio.value || undefined
  }),
  defaultForm: () => ({ title: '', artist: '', sortOrder: 0, status: 1, coverUrl: '' }),
  onUpdateSuccess: () => { stopPreview(); load() },
  entityName: '音乐',
})

const editFullAudio = ref<File | null>(null)
const editVocalAudio = ref<File | null>(null)
const editExistingAudio = ref({ full: '', vocal: '' })
const editCoverPreview = ref('')
const editCoverUploading = ref(false)
const editFullPlayer = ref<HTMLAudioElement | null>(null)
const editVocalPlayer = ref<HTMLAudioElement | null>(null)
let editSession = 0
const resetEditFiles = () => {
  editFullPlayer.value?.pause()
  editVocalPlayer.value?.pause()
  editSession++
  editFullAudio.value = editVocalAudio.value = null
  if (editCoverPreview.value.startsWith('blob:')) URL.revokeObjectURL(editCoverPreview.value)
  editCoverPreview.value = ''
  editCoverUploading.value = false
}
watch(editModalVisible, (open) => { if (!open) resetEditFiles() })

const handleEditSubmit = () => {
  if (editLoading.value || editCoverUploading.value) return
  return submitEditForm()
}
const handleEditCoverChange = async (info: any) => {
  if (editCoverUploading.value || editLoading.value) return
  const file = pickUploadFile(info)
  if (!file) return
  if (!file.type.startsWith('image/')) { message.error('请选择图片文件'); return }
  const session = editSession
  editCoverUploading.value = true
  try {
    const result = await ImageUploadService.uploadImage(file)
    if (session !== editSession || !editModalVisible.value) return
    if (editCoverPreview.value.startsWith('blob:')) URL.revokeObjectURL(editCoverPreview.value)
    editCoverPreview.value = URL.createObjectURL(file)
    editForm.value.coverUrl = result.fileUrl
  } catch (error: any) {
    if (session === editSession && !error?.isBusiness) message.error('封面上传失败，原封面已保留')
  } finally {
    if (session === editSession) editCoverUploading.value = false
  }
}
const handleEditAudioChange = (info: any, track: 'full' | 'vocal') => {
  const file = pickUploadFile(info)
  if (!file || editLoading.value) return
  if (!/\.(mp3|wav|ogg|flac|m4a|aac|webm|opus)$/i.test(file.name)) {
    message.error('请选择支持的音频文件'); return
  }
  if (track === 'full') editFullAudio.value = file
  else editVocalAudio.value = file
}

// 覆盖 openEdit：精确映射字段
const openEdit = (record: MusicItem) => {
  stopPreview()
  resetEditFiles()
  isEdit.value = true
  editingId.value = record.id
  editForm.value = {
    title: record.title,
    artist: record.artist || '',
    sortOrder: record.sortOrder,
    status: record.status,
    coverUrl: record.coverUrl || ''
  }
  editCoverPreview.value = record.coverUrl || ''
  editExistingAudio.value = { full: record.fullAudioUrl, vocal: record.vocalUrl }
  editModalVisible.value = true
}

// ============== 播放控制 ==============
const audioPlayer = new Audio()
const currentPlayingId = ref<number | null>(null)
const musicPage = ref<HTMLElement | null>(null)
const isPlaying = ref(false)
const previewLoadingId = ref<number | null>(null)
const previewTrack = ref<'full' | 'vocal'>('full')
let previewVersion = 0

const stopPreview = () => {
  previewVersion++
  audioPlayer.pause()
  currentPlayingId.value = null
  previewLoadingId.value = null
  isPlaying.value = false
}
audioPlayer.onended = stopPreview
audioPlayer.onplaying = () => { isPlaying.value = true; previewLoadingId.value = null }
audioPlayer.onpause = () => { isPlaying.value = false }
audioPlayer.onerror = () => {
  if (currentPlayingId.value !== null) message.error('试听失败，请检查音频文件或网络')
  stopPreview()
}
watch(previewTrack, stopPreview)
onDeactivated(() => {
  stopPreview()
  editFullPlayer.value?.pause()
  editVocalPlayer.value?.pause()
  musicPage.value?.querySelectorAll('audio').forEach(audio => audio.pause())
})

const playPreview = async (record: Music) => {
  if (currentPlayingId.value === record.id && (isPlaying.value || previewLoadingId.value === record.id)) {
    previewVersion++
    audioPlayer.pause()
    previewLoadingId.value = null
    return
  }
  const token = ++previewVersion
  if (currentPlayingId.value !== record.id) {
    audioPlayer.pause()
    audioPlayer.src = previewTrack.value === 'full' ? record.fullAudioUrl : record.vocalUrl
    currentPlayingId.value = record.id
  }
  previewLoadingId.value = record.id
  try {
    await audioPlayer.play()
  } catch {
    if (token !== previewVersion) return
    stopPreview()
    message.error('试听失败，请点击重试或检查音频文件')
  } finally {
    if (token === previewVersion) previewLoadingId.value = null
  }
}

onUnmounted(() => {
  stopPreview()
  resetEditFiles()
  audioPlayer.src = ''
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
})

// ============== 上传弹窗 ==============
const uploadModalVisible = ref(false)
const uploadLoading = ref(false)
const uploadForm = ref({
  title: '',
  artist: '',
  cover: null as File | null,
  coverUrl: '' as string,
  fullAudio: null as File | null,
  vocalAudio: null as File | null
})
const coverPreview = ref<string>('')
const coverUploading = ref(false)

const handleCoverChange = async (info: any) => {
  if (coverUploading.value || uploadLoading.value) return
  const file = pickUploadFile(info)
  if (!file) return
  if (!file.type.startsWith('image/')) { message.error('请选择图片文件'); return }
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
  coverPreview.value = URL.createObjectURL(file)
  uploadForm.value.coverUrl = ''
  try {
    coverUploading.value = true
    const result = await ImageUploadService.uploadImage(file)
    uploadForm.value.coverUrl = result.fileUrl
  } catch (e: any) {
    if (!e?.isBusiness) message.error('封面上传失败')
    URL.revokeObjectURL(coverPreview.value)
    coverPreview.value = ''
  } finally {
    coverUploading.value = false
  }
}

const handleFullAudioChange = (info: any) => {
  const file = pickUploadFile(info)
  if (file) uploadForm.value.fullAudio = file
}

const handleVocalAudioChange = (info: any) => {
  const file = pickUploadFile(info)
  if (file) uploadForm.value.vocalAudio = file
}

const resetUploadForm = () => {
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
  uploadForm.value = {
    title: '',
    artist: '',
    cover: null,
    coverUrl: '',
    fullAudio: null,
    vocalAudio: null
  }
  coverPreview.value = ''
}

const handleUpload = async () => {
  if (uploadLoading.value || coverUploading.value) return
  const { title, artist, coverUrl, fullAudio, vocalAudio } = uploadForm.value
  if (!title.trim()) { message.error('请输入歌曲名'); return }
  if (!coverUrl) { message.error('请上传封面图'); return }
  if (!fullAudio) { message.error('请选择完整音频'); return }
  if (!vocalAudio) { message.error('请选择人声音频'); return }
  try {
    uploadLoading.value = true
    await musicService.uploadMusic({
      title: title.trim(),
      artist: artist.trim() || undefined,
      cover: coverUrl || undefined,
      fullAudio,
      vocalAudio
    })
    message.success('上传成功')
    uploadModalVisible.value = false
    resetUploadForm()
    load()
  } catch (e: any) {
    if (!e?.isBusiness) message.error('上传失败')
  } finally {
    uploadLoading.value = false
  }
}

// ============== 排序与状态 ==============
// 后端 PUT /admin/music/sort 是按「传入 id 列表的顺序」把 sortOrder 重写成数组下标
// （MusicService.updateSortOrder: music.setSortOrder(i)），并不读取任何 sortOrder 字段。
// 所以必须真正移动数组元素再发送；只交换两条记录的 sortOrder 字段、按原顺序发 id 是无效操作。
const handleSortChange = async (id: number, direction: 'up' | 'down') => {
  if (sortLoading.value || sortFiltered.value || loading.value) return
  const index = dataSource.value.findIndex((item) => item.id === id)
  if (index === -1) return
  const newIndex = direction === 'up' ? index - 1 : index + 1
  if (newIndex < 0 || newIndex >= dataSource.value.length) return
  const list = [...dataSource.value]
  const [moved] = list.splice(index, 1)
  list.splice(newIndex, 0, moved)
  dataSource.value = list
  sortLoading.value = true
  try {
    await musicService.updateSortOrder(list.map((item) => item.id))
    await load()
  } catch (e: any) {
    if (!e?.isBusiness) message.error('排序更新失败')
    await load()
  } finally {
    sortLoading.value = false
  }
}
const sortLoading = ref(false)
const sortFiltered = computed(() => searchParams.value.status !== undefined || !!searchParams.value.keyword?.trim())

const handleStatusChange = async (id: number, status: number) => {
  const record = dataSource.value.find(item => item.id === id)
  if (record?.statusLoading) return
  if (record) record.statusLoading = true
  try {
    await musicService.updateMusic(id, { status })
    message.success('状态更新成功')
    if (record) record.status = status
    if (status === 0 && currentPlayingId.value === id) stopPreview()
    await load()
  } catch (e: any) {
    if (!e?.isBusiness) message.error('状态更新失败')
  } finally {
    if (record) record.statusLoading = false
  }
}
</script>

<template>
  <div ref="musicPage" class="p-24">
    <a-alert class="mb-16" type="info" show-icon message="前台播放完整音频，纯人声仅用于看板娘嘴型，不重复发声。两份音频请保持相同起点与时长；前台支持列表循环、单曲循环与顺序播放。" />
    <!-- 搜索区域 -->
    <LtFilterBar>
      <a-form-item label="状态" class="lt-filter-bar__field">
        <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear @change="handleSearch">
          <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="关键词" class="lt-filter-bar__field">
        <a-input v-model:value="searchParams.keyword" placeholder="搜索歌曲名/艺术家" allow-clear @press-enter="handleSearch" />
      </a-form-item>
      <template #actions>
        <a-button type="primary" @click="handleSearch">
          <template #icon><SearchOutlined /></template>
          搜索
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </template>
    </LtFilterBar>

    <!-- 表格区域 -->
    <LtTableCard>
      <template #title>
        <span><CloudOutlined /> AI音乐管理</span>
      </template>
      <template #actions>
        <a-select v-model:value="previewTrack" aria-label="试听音轨" style="width: 130px">
          <a-select-option value="full">试听完整音频</a-select-option>
          <a-select-option value="vocal">试听纯人声</a-select-option>
        </a-select>
        <TableExportButton :ctrl="exportCtrl" />
        <TableColumnSettings :ctrl="columnPrefsCtrl" />
        <a-button type="primary" @click="uploadModalVisible = true">
          <PlusOutlined /> 上传音乐
        </a-button>
        <a-popconfirm
          title="确定要批量彻底删除选中的音乐吗？此操作不可恢复！"
          @confirm="handleBatchDelete(selectedRowKeys)"
        >
          <a-button danger :disabled="selectedRowKeys.length === 0">
            <DeleteOutlined /> 批量删除
          </a-button>
        </a-popconfirm>
      </template>

      <a-table
        :columns="prefColumns"
        :data-source="dataSource"
        :loading="loading"
        :scroll="{ x: 1000 }"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        row-key="id"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'cover'">
            <a-avatar v-if="record.coverUrl" :src="record.coverUrl" shape="square" :size="50" />
            <a-avatar v-else shape="square" :size="50" style="background: var(--color-primary)">
              <CloudOutlined />
            </a-avatar>
          </template>

          <template v-else-if="column.key === 'title'">
            <a-tooltip :title="record.title">
              <span style="font-weight: var(--lt-font-weight-medium)">{{ record.title }}</span>
            </a-tooltip>
          </template>

          <template v-else-if="column.key === 'artist'">
            {{ record.artist || '-' }}
          </template>

          <template v-else-if="column.key === 'sortOrder'">
            <a-space :title="sortFiltered ? '请先重置筛选，再调整完整歌单顺序' : undefined">
              <a-button type="link" size="small" :disabled="sortLoading || sortFiltered || loading || dataSource[0]?.id === record.id" @click="handleSortChange(record.id, 'up')">
                <SortAscendingOutlined style="transform: rotate(180deg)" />
              </a-button>
              <span>{{ record.sortOrder }}</span>
              <a-button type="link" size="small" :disabled="sortLoading || sortFiltered || loading || dataSource[dataSource.length - 1]?.id === record.id" @click="handleSortChange(record.id, 'down')">
                <SortAscendingOutlined />
              </a-button>
            </a-space>
          </template>

          <template v-else-if="column.key === 'status'">
            <a-switch
              :checked="record.status === 1"
              checked-children="启用"
              un-checked-children="禁用"
              :loading="record.statusLoading"
              @change="(checked: boolean) => handleStatusChange(record.id, checked ? 1 : 0)"
            />
          </template>

          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" :loading="previewLoadingId === record.id" @click="playPreview(record)">
                <template #icon>
                  <PauseCircleOutlined v-if="currentPlayingId === record.id && isPlaying" />
                  <PlayCircleOutlined v-else />
                </template>
                {{ currentPlayingId === record.id && isPlaying ? '暂停' : '试听' }}
              </a-button>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确定要彻底删除这首音乐吗？此操作不可恢复！" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </LtTableCard>

    <!-- 上传弹窗 -->
    <a-modal
      v-model:open="uploadModalVisible"
      title="上传AI音乐"
      :confirm-loading="uploadLoading"
      :ok-button-props="{ disabled: coverUploading }"
      :cancel-button-props="{ disabled: uploadLoading || coverUploading }"
      :closable="!uploadLoading && !coverUploading"
      :mask-closable="!uploadLoading && !coverUploading"
      :width="500"
      destroy-on-close
      @ok="handleUpload"
      @cancel="() => { uploadModalVisible = false; resetUploadForm() }"
    >
      <a-form layout="vertical">
        <a-form-item label="歌曲名" required>
          <a-input v-model:value="uploadForm.title" placeholder="请输入歌曲名" />
        </a-form-item>
        <a-form-item label="艺术家">
          <a-input v-model:value="uploadForm.artist" placeholder="请输入艺术家名称" />
        </a-form-item>
        <a-form-item label="封面图" required>
          <a-upload-dragger :show-upload-list="false" accept="image/*" :max-count="1" :disabled="coverUploading || uploadLoading" :before-upload="() => false" @change="handleCoverChange">
            <div v-if="coverPreview" class="cover-preview">
              <img :src="coverPreview" alt="封面预览" />
            </div>
            <p class="ant-upload-text">{{ coverUploading ? '正在上传封面…' : '拖入封面图片，或点击选择' }}</p>
          </a-upload-dragger>
        </a-form-item>
        <a-form-item label="完整音频（伴奏 + 人声）" required help="前台播放此完整成品。请勿只上传伴奏，否则播放时听不到人声。">
          <a-upload :show-upload-list="false" accept="audio/*" :max-count="1" :disabled="uploadLoading" :before-upload="() => false" @change="handleFullAudioChange">
            <a-button :type="uploadForm.fullAudio ? 'primary' : 'default'">
              {{ uploadForm.fullAudio ? '已选择: ' + uploadForm.fullAudio.name : '选择完整音频' }}
            </a-button>
          </a-upload>
        </a-form-item>
        <a-form-item label="纯人声音频（口型分析）" required help="此轨静音播放，只驱动 Live2D 嘴型。两条音轨须保持相同起点和时间轴，请勿单独裁剪。">
          <a-upload :show-upload-list="false" accept="audio/*" :max-count="1" :disabled="uploadLoading" :before-upload="() => false" @change="handleVocalAudioChange">
            <a-button :type="uploadForm.vocalAudio ? 'primary' : 'default'">
              {{ uploadForm.vocalAudio ? '已选择: ' + uploadForm.vocalAudio.name : '选择人声音频' }}
            </a-button>
          </a-upload>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:open="editModalVisible"
      title="编辑音乐"
      :confirm-loading="editLoading"
      :width="600"
      :closable="!editLoading && !editCoverUploading"
      :mask-closable="!editLoading && !editCoverUploading"
      :keyboard="!editLoading && !editCoverUploading"
      :cancel-button-props="{ disabled: editLoading || editCoverUploading }"
      destroy-on-close
      @ok="handleEditSubmit"
      @cancel="handleEditCancel"
    >
      <a-form ref="editFormRef" :model="editForm" :rules="editRules" layout="vertical">
        <a-form-item name="title" label="歌曲名" required>
          <a-input v-model:value="editForm.title" placeholder="请输入歌曲名" maxlength="100" />
        </a-form-item>
        <a-form-item label="艺术家">
          <a-input v-model:value="editForm.artist" placeholder="请输入艺术家名称" />
        </a-form-item>
        <a-form-item label="封面">
          <div class="edit-cover">
            <img v-if="editCoverPreview" :src="editCoverPreview" alt="音乐封面" />
            <a-upload :show-upload-list="false" accept="image/*" :before-upload="() => false" :disabled="editCoverUploading || editLoading" @change="handleEditCoverChange">
              <a-button :loading="editCoverUploading">替换封面</a-button>
            </a-upload>
          </div>
        </a-form-item>
        <a-alert class="mb-16" type="info" show-icon message="未选择新文件时保留原音轨；换歌时请一起替换完整音频和人声，保持时间轴一致。" />
        <a-form-item label="完整音频（伴奏 + 人声）" help="前台实际播放此轨。单独的背景伴奏不会自动与人声混音。">
          <audio v-if="editExistingAudio.full" ref="editFullPlayer" :src="editExistingAudio.full" controls preload="none" class="edit-audio" />
          <a-space wrap>
            <a-upload :show-upload-list="false" accept="audio/*,.webm,.m4a" :before-upload="() => false" :disabled="editLoading" @change="(info: any) => handleEditAudioChange(info, 'full')">
              <a-button>{{ editFullAudio ? '重选完整音频' : '替换完整音频' }}</a-button>
            </a-upload>
            <a-button v-if="editFullAudio" type="link" @click="editFullAudio = null">保留原文件</a-button>
          </a-space>
          <p v-if="editFullAudio" class="selected-audio">{{ editFullAudio.name }}</p>
        </a-form-item>
        <a-form-item label="纯人声音频（口型分析）" help="此轨仅驱动 Live2D 嘴型，前台静音播放；此处试听可检查人声。">
          <audio v-if="editExistingAudio.vocal" ref="editVocalPlayer" :src="editExistingAudio.vocal" controls preload="none" class="edit-audio" />
          <a-space wrap>
            <a-upload :show-upload-list="false" accept="audio/*,.webm,.m4a" :before-upload="() => false" :disabled="editLoading" @change="(info: any) => handleEditAudioChange(info, 'vocal')">
              <a-button>{{ editVocalAudio ? '重选人声音频' : '替换人声音频' }}</a-button>
            </a-upload>
            <a-button v-if="editVocalAudio" type="link" @click="editVocalAudio = null">保留原文件</a-button>
          </a-space>
          <p v-if="editVocalAudio" class="selected-audio">{{ editVocalAudio.name }}</p>
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="editForm.sortOrder" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="editForm.status">
            <a-select-option v-for="opt in statusOptions.filter(o => o.value !== undefined)" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.edit-cover { display: flex; align-items: center; gap: 16px; }
.edit-cover img { width: 72px; height: 72px; object-fit: cover; border-radius: var(--lt-radius-md); }
.edit-audio { display: block; width: 100%; height: 36px; margin-bottom: 10px; }
.selected-audio { margin: 8px 0 0; color: var(--lt-color-text-secondary); overflow-wrap: anywhere; }
.cover-preview {
  width: 100px;
  height: 100px;
  border-radius: var(--lt-radius-lg);
  overflow: hidden;
  border: 1px dashed var(--lt-color-border);
  margin: 0 auto 8px;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>





