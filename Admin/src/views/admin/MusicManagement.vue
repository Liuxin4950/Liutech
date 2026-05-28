<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
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
import type { Music } from '../../services/music'
import musicService from '../../services/music'
import { formatDateTime } from '../../utils/utils'
import { ImageUploadService } from '../../services/upload'

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
}

// 状态选项
const statusOptions = [
  { label: '全部', value: undefined },
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

// ============== 表格页面 ==============
const {
  loading, dataSource, selectedRowKeys, searchParams, pagination,
  load, handleSearch, handleReset, handleTableChange, onSelectChange, clearSelection
} = useTablePage<MusicItem, { status: number | undefined; keyword: string }>({
  loadFn: async (params) => {
    const res = await musicService.getMusicList(params)
    if (res.code === 200) {
      const list = (Array.isArray(res.data) ? res.data : []) as unknown as MusicItem[]
      return { code: 200, message: res.message, data: { records: list, total: list.length } }
    }
    return { code: res.code || 500, message: res.message || '加载失败', data: { records: [] as MusicItem[], total: 0 } }
  },
  defaultSearchParams: { status: undefined, keyword: '' },
  loadErrorMessage: '加载音乐列表失败'
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
const {
  modalVisible: editModalVisible,
  confirmLoading: editLoading,
  formRef: editFormRef,
  formModel: editForm,
  isEdit,
  editingId,
  handleOk: handleEditSubmit,
  handleCancel: handleEditCancel
} = useModalForm<MusicEditForm>({
  updateFn: (id, data) => musicService.updateMusic(id, {
    title: data.title,
    artist: data.artist,
    sortOrder: data.sortOrder,
    status: data.status
  }),
  defaultForm: () => ({ title: '', artist: '', sortOrder: 0, status: 1 }),
  onUpdateSuccess: load,
  entityName: '音乐',
})

// 覆盖 openEdit：精确映射字段
const openEdit = (record: MusicItem) => {
  isEdit.value = true
  editingId.value = record.id
  editForm.value = {
    title: record.title,
    artist: record.artist || '',
    sortOrder: record.sortOrder,
    status: record.status
  }
  editModalVisible.value = true
}

// ============== 播放控制 ==============
const audioPlayer = new Audio()
const currentPlayingId = ref<number | null>(null)
const isPlaying = ref(false)

audioPlayer.onended = () => {
  currentPlayingId.value = null
  isPlaying.value = false
}

const playPreview = (record: Music) => {
  if (currentPlayingId.value === record.id) {
    if (isPlaying.value) {
      audioPlayer.pause()
      isPlaying.value = false
    } else {
      audioPlayer.play()
      isPlaying.value = true
    }
    return
  }
  audioPlayer.src = record.fullAudioUrl
  audioPlayer.play()
  currentPlayingId.value = record.id
  isPlaying.value = true
}

onUnmounted(() => {
  audioPlayer.pause()
  audioPlayer.src = ''
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
  const file = info.fileList?.[0]?.originFileObj || info.file?.originFileObj
  if (!file) return
  coverPreview.value = URL.createObjectURL(file)
  try {
    coverUploading.value = true
    const result = await ImageUploadService.uploadImage(file)
    uploadForm.value.coverUrl = result.fileUrl
  } catch (e: any) {
    message.error(e.message || '封面上传失败')
    coverPreview.value = ''
  } finally {
    coverUploading.value = false
  }
}

const handleFullAudioChange = (info: any) => {
  const file = info.fileList?.[0]?.originFileObj || info.file?.originFileObj
  if (file) uploadForm.value.fullAudio = file
}

const handleVocalAudioChange = (info: any) => {
  const file = info.fileList?.[0]?.originFileObj || info.file?.originFileObj
  if (file) uploadForm.value.vocalAudio = file
}

const resetUploadForm = () => {
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
    message.error(e.message || '上传失败')
  } finally {
    uploadLoading.value = false
  }
}

// ============== 排序与状态 ==============
const handleSortChange = async (id: number, direction: 'up' | 'down') => {
  const index = dataSource.value.findIndex((item) => item.id === id)
  if (index === -1) return
  const newIndex = direction === 'up' ? index - 1 : index + 1
  if (newIndex < 0 || newIndex >= dataSource.value.length) return
  const temp = dataSource.value[index].sortOrder
  dataSource.value[index].sortOrder = dataSource.value[newIndex].sortOrder
  dataSource.value[newIndex].sortOrder = temp
  try {
    await musicService.updateSortOrder(dataSource.value.map((item) => item.id))
    load()
  } catch (e: any) {
    message.error(e.message || '排序更新失败')
    load()
  }
}

const handleStatusChange = async (id: number, status: number) => {
  const record = dataSource.value.find(item => item.id === id)
  if (record) record.statusLoading = true
  try {
    await musicService.updateMusic(id, { status })
    message.success('状态更新成功')
    if (record) record.status = status
  } catch (e: any) {
    message.error(e.message || '状态更新失败')
  } finally {
    if (record) record.statusLoading = false
  }
}
</script>

<template>
  <div class="p-24">
    <!-- 搜索区域 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="6">
            <a-form-item label="状态" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear @change="handleSearch">
                <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="关键词" class="mb-0">
              <a-input v-model:value="searchParams.keyword" placeholder="搜索歌曲名/艺术家" allow-clear @press-enter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :span="12" class="text-right">
            <a-space>
              <a-button type="primary" @click="handleSearch">
                <template #icon><SearchOutlined /></template>
                搜索
              </a-button>
              <a-button @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
            </a-space>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 表格区域 -->
    <a-card :bordered="false">
      <template #title>
        <span><CloudOutlined /> AI音乐管理</span>
      </template>
      <template #extra>
        <a-space>
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
        </a-space>
      </template>

      <a-table
        :columns="[
          { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
          { title: '封面', dataIndex: 'coverUrl', key: 'cover', width: 80 },
          { title: '歌曲名', dataIndex: 'title', key: 'title', width: 200 },
          { title: '艺术家', dataIndex: 'artist', key: 'artist', width: 150 },
          { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 120 },
          { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
          { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
          { title: '操作', key: 'action', width: 200, fixed: 'right' }
        ]"
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
              <span style="font-weight: 500">{{ record.title }}</span>
            </a-tooltip>
          </template>

          <template v-else-if="column.key === 'artist'">
            {{ record.artist || '-' }}
          </template>

          <template v-else-if="column.key === 'sortOrder'">
            <a-space>
              <a-button type="link" size="small" :disabled="record.sortOrder === 0" @click="handleSortChange(record.id, 'up')">
                <SortAscendingOutlined style="transform: rotate(180deg)" />
              </a-button>
              <span>{{ record.sortOrder }}</span>
              <a-button type="link" size="small" @click="handleSortChange(record.id, 'down')">
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
              <a-button type="link" size="small" @click="playPreview(record)">
                <template #icon>
                  <PauseCircleOutlined v-if="currentPlayingId === record.id && isPlaying" />
                  <PlayCircleOutlined v-else />
                </template>
                {{ currentPlayingId === record.id && isPlaying ? '暂停' : '播放' }}
              </a-button>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确定要彻底删除这首音乐吗？此操作不可恢复！" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 上传弹窗 -->
    <a-modal
      v-model:open="uploadModalVisible"
      title="上传AI音乐"
      :confirm-loading="uploadLoading"
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
        <a-form-item label="封面图">
          <a-upload :show-upload-list="false" accept="image/*" :before-upload="() => false" @change="handleCoverChange">
            <div v-if="coverPreview" class="cover-preview">
              <img :src="coverPreview" alt="封面预览" />
            </div>
            <a-button v-else>
              <CloudOutlined /> 选择封面图
            </a-button>
          </a-upload>
        </a-form-item>
        <a-form-item label="完整音频" required help="背景音乐（伴奏+人声混合）">
          <a-upload :show-upload-list="false" accept="audio/*" :before-upload="() => false" @change="handleFullAudioChange">
            <a-button :type="uploadForm.fullAudio ? 'primary' : 'default'">
              {{ uploadForm.fullAudio ? '已选择: ' + uploadForm.fullAudio.name : '选择完整音频' }}
            </a-button>
          </a-upload>
        </a-form-item>
        <a-form-item label="人声音频" required help="纯人声，用于Live2D模型对口型">
          <a-upload :show-upload-list="false" accept="audio/*" :before-upload="() => false" @change="handleVocalAudioChange">
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
      :width="400"
      destroy-on-close
      @ok="handleEditSubmit"
      @cancel="handleEditCancel"
    >
      <a-form ref="editFormRef" :model="editForm" layout="vertical">
        <a-form-item label="歌曲名">
          <a-input v-model:value="editForm.title" placeholder="请输入歌曲名" />
        </a-form-item>
        <a-form-item label="艺术家">
          <a-input v-model:value="editForm.artist" placeholder="请输入艺术家名称" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="editForm.sortOrder" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="editForm.status">
            <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.cover-preview {
  width: 100px;
  height: 100px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px dashed var(--border-base);
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>





