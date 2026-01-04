<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  DeleteOutlined,
  CloudOutlined,
  DownOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  SortAscendingOutlined,
  RedoOutlined
} from '@ant-design/icons-vue'
import type { Music, UploadMusicParams, UpdateMusicParams } from '../../services/music'
import musicService from '../../services/music'
import { formatDateTime } from '../../utils/uitls'
import { ImageUploadService } from '../../services/upload'

// 响应式数据
const loading = ref(false)
const dataSource = ref<Music[]>([])
const selectedRowKeys = ref<number[]>([])

// 搜索参数
const searchParams = ref({
  status: undefined as number | undefined,
  keyword: ''
})

// 显示已删除
const showDeleted = ref(false)

// 状态选项
const statusOptions = [
  { label: '全部', value: undefined },
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

// 播放状态控制
const currentAudio = ref<HTMLAudioElement | null>(null)
const currentPlayingId = ref<number | null>(null)
const isPlaying = ref(false)

// ============== 上传弹窗 ==============
const uploadModalVisible = ref(false)
const uploadLoading = ref(false)
const uploadForm = ref({
  title: '',
  artist: '',
  cover: null as File | null,
  coverUrl: '' as string,  // 上传后的封面URL
  fullAudio: null as File | null,
  vocalAudio: null as File | null
})
const coverPreview = ref<string>('')
const coverUploading = ref(false)

// 封面上传
const handleCoverChange = async (info: any) => {
  const file = info.file?.originFileObj
  if (!file) return

  // 先显示本地预览
  coverPreview.value = URL.createObjectURL(file)

  // 上传到服务器
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

// 完整音频
const handleFullAudioChange = (info: any) => {
  const file = info.file?.originFileObj
  if (file) {
    uploadForm.value.fullAudio = file
  }
}

// 人声音频
const handleVocalAudioChange = (info: any) => {
  const file = info.file?.originFileObj
  if (file) {
    uploadForm.value.vocalAudio = file
  }
}

// 重置上传表单
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

// 提交上传
const handleUpload = async () => {
  const { title, artist, coverUrl, fullAudio, vocalAudio } = uploadForm.value

  if (!title.trim()) {
    message.error('请输入歌曲名')
    return
  }
  if (!fullAudio) {
    message.error('请选择完整音频')
    return
  }
  if (!vocalAudio) {
    message.error('请选择人声音频')
    return
  }

  try {
    uploadLoading.value = true
    await musicService.uploadMusic({
      title: title.trim(),
      artist: artist.trim() || undefined,
      cover: coverUrl || undefined,  // 传递封面URL
      fullAudio,
      vocalAudio
    })
    message.success('上传成功')
    uploadModalVisible.value = false
    resetUploadForm()
    loadMusicList()
  } catch (e: any) {
    message.error(e.message || '上传失败')
  } finally {
    uploadLoading.value = false
  }
}

// ============== 编辑弹窗 ==============
const editModalVisible = ref(false)
const editLoading = ref(false)
const editingId = ref<number | null>(null)
const editForm = ref({
  title: '',
  artist: '',
  sortOrder: 0,
  status: 1
})

const openEdit = (record: Music) => {
  editingId.value = record.id
  editForm.value = {
    title: record.title,
    artist: record.artist || '',
    sortOrder: record.sortOrder,
    status: record.status
  }
  editModalVisible.value = true
}

const handleEditSubmit = async () => {
  if (!editingId.value) return

  try {
    editLoading.value = true
    await musicService.updateMusic(editingId.value, {
      title: editForm.value.title.trim() || undefined,
      artist: editForm.value.artist.trim() || undefined,
      sortOrder: editForm.value.sortOrder,
      status: editForm.value.status
    })
    message.success('更新成功')
    editModalVisible.value = false
    loadMusicList()
  } catch (e: any) {
    message.error(e.message || '更新失败')
  } finally {
    editLoading.value = false
  }
}

// ============== 列表操作 ==============
const loadMusicList = async () => {
  try {
    loading.value = true
    const res = await musicService.getMusicList()
    let list = (res.data || []).sort((a, b) => a.sortOrder - b.sortOrder)
    // 根据显示已删除筛选
    if (!showDeleted.value) {
      list = list.filter((item) => !item.deletedAt)
    }
    dataSource.value = list
  } catch (e: any) {
    message.error(e.message || '加载失败')
    dataSource.value = []
  } finally {
    loading.value = false
  }
}

const onSelectChange = (keys: number[]) => {
  selectedRowKeys.value = keys
}

const handleDelete = async (id: number) => {
  try {
    await musicService.deleteMusic(id)
    message.success('删除成功')
    loadMusicList()
  } catch (e: any) {
    message.error(e.message || '删除失败')
  }
}

const handleRestore = async (id: number) => {
  try {
    await musicService.restoreMusic(id)
    message.success('恢复成功')
    loadMusicList()
  } catch (e: any) {
    message.error(e.message || '恢复失败')
  }
}

const handleBatchRestore = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要恢复的音乐')
    return
  }
  try {
    await musicService.batchRestoreMusic(selectedRowKeys.value)
    message.success('批量恢复成功')
    selectedRowKeys.value = []
    loadMusicList()
  } catch (e: any) {
    message.error(e.message || '批量恢复失败')
  }
}

const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要删除的音乐')
    return
  }
  try {
    for (const id of selectedRowKeys.value) {
      await musicService.deleteMusic(id)
    }
    message.success('批量删除成功')
    selectedRowKeys.value = []
    loadMusicList()
  } catch (e: any) {
    message.error(e.message || '批量删除失败')
  }
}

const handleStatusChange = async (id: number, status: number) => {
  try {
    await musicService.updateMusic(id, { status })
    message.success('状态更新成功')
    loadMusicList()
  } catch (e: any) {
    message.error(e.message || '状态更新失败')
  }
}

const handleSortChange = async (id: number, direction: 'up' | 'down') => {
  const index = dataSource.value.findIndex((item) => item.id === id)
  if (index === -1) return

  const newIndex = direction === 'up' ? index - 1 : index + 1
  if (newIndex < 0 || newIndex >= dataSource.value.length) return

  // 交换排序值
  const temp = dataSource.value[index].sortOrder
  dataSource.value[index].sortOrder = dataSource.value[newIndex].sortOrder
  dataSource.value[newIndex].sortOrder = temp

  // 保存到后端
  try {
    await musicService.updateSortOrder(dataSource.value.map((item) => item.id))
    loadMusicList()
  } catch (e: any) {
    message.error(e.message || '排序更新失败')
    loadMusicList()
  }
}

// 播放预览 - 防止重复创建播放器
const playPreview = (record: Music) => {
  // 如果正在播放同一首，停止
  if (currentPlayingId.value === record.id) {
    currentAudio.value?.pause()
    currentAudio.value = null
    currentPlayingId.value = null
    isPlaying.value = false
    return
  }

  // 停止上一首
  if (currentAudio.value) {
    currentAudio.value.pause()
    currentAudio.value = null
  }

  // 创建新的播放器
  const audio = new Audio(record.fullAudioUrl)
  audio.onended = () => {
    currentAudio.value = null
    currentPlayingId.value = null
    isPlaying.value = false
  }

  audio.play()
  currentAudio.value = audio
  currentPlayingId.value = record.id
  isPlaying.value = true
}

// 组件卸载时清理
onUnmounted(() => {
  currentAudio.value?.pause()
  currentAudio.value = null
})

// 搜索
const handleSearch = () => {
  loadMusicList()
}

// 重置
const handleReset = () => {
  searchParams.value = { status: undefined, keyword: '' }
  showDeleted.value = false
  loadMusicList()
}

onMounted(() => {
  loadMusicList()
})
</script>

<template>
  <div class="music-management">
    <div class="page-header">
      <h2><CloudOutlined /> AI音乐管理</h2>
    </div>

    <!-- 搜索区域 -->
    <a-card class="search-card" :bordered="false">
      <a-form layout="inline" :model="searchParams">
        <a-form-item label="状态">
          <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear style="width: 120px" @change="handleSearch">
            <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">搜索</a-button>
            <a-button @click="handleReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 操作区域 -->
    <a-card class="action-card" :bordered="false">
      <a-space>
        <a-button type="primary" @click="uploadModalVisible = true">
          <PlusOutlined /> 上传音乐
        </a-button>
        <a-button danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete">
          <DeleteOutlined /> 批量删除
        </a-button>
        <a-button :disabled="selectedRowKeys.length === 0" @click="handleBatchRestore" v-if="showDeleted">
          <RedoOutlined /> 批量恢复
        </a-button>
        <a-switch
          v-model:checked="showDeleted"
          checked-children="显示已删除"
          un-checked-children="正常状态"
          @change="loadMusicList"
        />
      </a-space>
    </a-card>

    <!-- 表格区域 -->
    <a-card :bordered="false">
      <a-table
        :columns="[
          { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
          { title: '封面', dataIndex: 'coverUrl', key: 'cover', width: 80 },
          { title: '歌曲名', dataIndex: 'title', key: 'title', width: 200 },
          { title: '艺术家', dataIndex: 'artist', key: 'artist', width: 150 },
          { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80 },
          { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
          { title: '删除时间', dataIndex: 'deletedAt', key: 'deletedAt', width: 180 },
          { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
          { title: '操作', key: 'action', width: 280, fixed: 'right' }
        ]"
        :data-source="dataSource"
        :loading="loading"
        :scroll="{ x: 1200 }"
        :row-selection="{
          selectedRowKeys,
          onChange: onSelectChange
        }"
        :pagination="false"
      >
        <!-- 封面列 -->
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'cover'">
            <a-avatar
              v-if="record.coverUrl"
              :src="record.coverUrl"
              shape="square"
              :size="50"
            />
            <a-avatar v-else shape="square" :size="50" style="background: #667eea">
              <CloudOutlined />
            </a-avatar>
          </template>

          <!-- 歌曲名列 -->
          <template v-else-if="column.key === 'title'">
            <a-tooltip :title="record.title">
              <span style="font-weight: 500">{{ record.title }}</span>
            </a-tooltip>
          </template>

          <!-- 艺术家列 -->
          <template v-else-if="column.key === 'artist'">
            {{ record.artist || '-' }}
          </template>

          <!-- 排序列 -->
          <template v-else-if="column.key === 'sortOrder'">
            <a-space>
              <a-button
                type="link"
                size="small"
                :disabled="record.sortOrder === 0"
                @click="handleSortChange(record.id, 'up')"
              >
                <SortAscendingOutlined style="transform: rotate(180deg)" />
              </a-button>
              <span>{{ record.sortOrder }}</span>
              <a-button
                type="link"
                size="small"
                @click="handleSortChange(record.id, 'down')"
              >
                <SortAscendingOutlined />
              </a-button>
            </a-space>
          </template>

          <!-- 状态列 -->
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'red'">
              {{ record.status === 1 ? '启用' : '禁用' }}
            </a-tag>
          </template>

          <!-- 删除时间列 -->
          <template v-else-if="column.key === 'deletedAt'">
            <span v-if="record.deletedAt" style="color: #ff4d4f">{{ formatDateTime(record.deletedAt) }}</span>
            <span v-else>-</span>
          </template>

          <!-- 创建时间列 -->
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.key === 'action'">
            <a-space>
              <!-- 未删除的音乐显示正常操作 -->
              <template v-if="!record.deletedAt">
                <a-button type="link" size="small" @click="playPreview(record)">
                  <PlayCircleOutlined /> 播放
                </a-button>
                <a-button type="link" size="small" @click="openEdit(record)">
                  编辑
                </a-button>

                <!-- 状态切换 -->
                <a-dropdown>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item
                        key="enable"
                        @click="handleStatusChange(record.id, 1)"
                        :disabled="record.status === 1"
                      >
                        启用
                      </a-menu-item>
                      <a-menu-item
                        key="disable"
                        @click="handleStatusChange(record.id, 0)"
                        :disabled="record.status === 0"
                      >
                        禁用
                      </a-menu-item>
                    </a-menu>
                  </template>
                  <a-button type="link" size="small">
                    {{ record.status === 1 ? '禁用' : '启用' }} <DownOutlined />
                  </a-button>
                </a-dropdown>

                <a-popconfirm
                  title="确定要删除这首音乐吗？"
                  @confirm="handleDelete(record.id)"
                >
                  <a-button type="link" size="small" danger>
                    删除
                  </a-button>
                </a-popconfirm>
              </template>

              <!-- 已删除的音乐显示恢复按钮 -->
              <template v-else>
                <a-button type="link" size="small" @click="handleRestore(record.id)">
                  <RedoOutlined /> 恢复
                </a-button>
              </template>
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
          <a-upload
            :show-upload-list="false"
            accept="image/*"
            :before-upload="() => false"
            @change="handleCoverChange"
          >
            <div v-if="coverPreview" class="cover-preview">
              <img :src="coverPreview" alt="封面预览" />
            </div>
            <a-button v-else>
              <CloudOutlined /> 选择封面图
            </a-button>
          </a-upload>
        </a-form-item>

        <a-form-item label="完整音频" required help="背景音乐（伴奏+人声混合）">
          <a-upload
            :show-upload-list="false"
            accept="audio/*"
            :before-upload="() => false"
            @change="handleFullAudioChange"
          >
            <a-button :type="uploadForm.fullAudio ? 'primary' : 'default'">
              {{ uploadForm.fullAudio ? '已选择: ' + uploadForm.fullAudio.name : '选择完整音频' }}
            </a-button>
          </a-upload>
        </a-form-item>

        <a-form-item label="人声音频" required help="纯人声，用于Live2D模型对口型">
          <a-upload
            :show-upload-list="false"
            accept="audio/*"
            :before-upload="() => false"
            @change="handleVocalAudioChange"
          >
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
      @ok="handleEditSubmit"
    >
      <a-form layout="vertical">
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
.music-management {
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.cover-preview {
  width: 100px;
  height: 100px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px dashed #d9d9d9;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
