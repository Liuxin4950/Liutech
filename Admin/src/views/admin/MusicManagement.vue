<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
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
import type { Music } from '../../services/music'
import musicService from '../../services/music'
import { formatDateTime } from '../../utils/uitls'
import { ImageUploadService } from '../../services/upload'

// 扩展 Music 类型以支持前端状态
interface MusicItem extends Music {
  statusLoading?: boolean
}

// 响应式数据
const loading = ref(false)
const dataSource = ref<MusicItem[]>([])
const selectedRowKeys = ref<number[]>([])

// 搜索参数
const searchParams = ref({
  status: undefined as number | undefined,
  keyword: ''
})

// 状态选项
const statusOptions = [
  { label: '全部', value: undefined },
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

// 播放状态控制
const audioPlayer = new Audio()
const currentPlayingId = ref<number | null>(null)
const isPlaying = ref(false)

// 监听播放结束
audioPlayer.onended = () => {
  currentPlayingId.value = null
  isPlaying.value = false
}

// 播放预览
const playPreview = (record: Music) => {
  // 如果点击的是当前正在播放的音乐
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

  // 播放新的音乐
  audioPlayer.src = record.fullAudioUrl
  audioPlayer.play()
  currentPlayingId.value = record.id
  isPlaying.value = true
}

// 组件卸载时清理
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
  coverUrl: '' as string,  // 上传后的封面URL
  fullAudio: null as File | null,
  vocalAudio: null as File | null
})
const coverPreview = ref<string>('')
const coverUploading = ref(false)

// 封面上传
const handleCoverChange = async (info: any) => {
  // 当 before-upload 返回 false 时，使用 fileList 获取文件
  const file = info.fileList?.[0]?.originFileObj || info.file?.originFileObj
  if (!file) {
    console.log('封面文件为空', info)
    return
  }

  console.log('准备上传封面', file.name, file.size, file.type)

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
  // 当 before-upload 返回 false 时，使用 fileList 获取文件
  const file = info.fileList?.[0]?.originFileObj || info.file?.originFileObj
  if (file) {
    uploadForm.value.fullAudio = file
  }
}

// 人声音频
const handleVocalAudioChange = (info: any) => {
  // 当 before-upload 返回 false 时，使用 fileList 获取文件
  const file = info.fileList?.[0]?.originFileObj || info.file?.originFileObj
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
    // 构建查询参数
    const params: any = {}

    // 只添加有值的参数
    if (searchParams.value.status !== undefined) {
      params.status = searchParams.value.status
    }
    if (searchParams.value.keyword && searchParams.value.keyword.trim()) {
      params.keyword = searchParams.value.keyword.trim()
    }

    const res = await musicService.getMusicList(params)
    // 后端已按 sortOrder 排序，直接使用
    dataSource.value = res.data || []
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

const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要删除的音乐')
    return
  }
  try {
    await musicService.batchDelete(selectedRowKeys.value)
    message.success('批量删除成功')
    selectedRowKeys.value = []
    loadMusicList()
  } catch (e: any) {
    message.error(e.message || '批量删除失败')
  }
}

const handleStatusChange = async (id: number, status: number) => {
  // 找到对应的记录以显示加载状态
  const record = dataSource.value.find(item => item.id === id)
  if (record) {
    // 临时添加statusLoading属性
    record.statusLoading = true
  }

  try {
    await musicService.updateMusic(id, { status })
    message.success('状态更新成功')
    
    // 更新本地数据状态，避免整个列表刷新导致闪烁
    if (record) {
      record.status = status
    }
  } catch (e: any) {
    message.error(e.message || '状态更新失败')
    // 恢复状态（如果失败）
    if (record) {
      loadMusicList()
    }
  } finally {
    if (record) {
      record.statusLoading = false
    }
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

// 搜索
const handleSearch = () => {
  loadMusicList()
}

// 重置
const handleReset = () => {
  searchParams.value = { status: undefined, keyword: '' }
  loadMusicList()
}

onMounted(() => {
  loadMusicList()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2><CloudOutlined /> AI音乐管理</h2>
    </div>

    <!-- 搜索区域 -->
    <div class="modern-search-card">
      <a-form layout="inline" :model="searchParams">
        <a-form-item label="状态">
          <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear style="width: 120px" @change="handleSearch">
            <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
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
        </a-form-item>
      </a-form>
    </div>

    <!-- 表格区域 -->
    <div class="modern-card">
      <div class="table-toolbar">
        <div class="left">
          <a-button type="primary" @click="uploadModalVisible = true">
            <PlusOutlined /> 上传音乐
          </a-button>
          <a-popconfirm
            title="确定要批量彻底删除选中的音乐吗？此操作不可恢复！"
            @confirm="handleBatchDelete"
          >
            <a-button danger :disabled="selectedRowKeys.length === 0">
              <DeleteOutlined /> 批量删除
            </a-button>
          </a-popconfirm>
        </div>
      </div>

      <a-table
        :columns="[
          { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
          { title: '封面', dataIndex: 'coverUrl', key: 'cover', width: 80 },
          { title: '歌曲名', dataIndex: 'title', key: 'title', width: 200 },
          { title: '艺术家', dataIndex: 'artist', key: 'artist', width: 150 },
          { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80 },
          { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
          { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
          { title: '操作', key: 'action', width: 200, fixed: 'right' }
        ]"
        :data-source="dataSource"
        :loading="loading"
        :scroll="{ x: 1000 }"
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
            <a-switch
              :checked="record.status === 1"
              checked-children="启用"
              un-checked-children="禁用"
              :loading="record.statusLoading"
              @change="(checked: boolean) => handleStatusChange(record.id, checked ? 1 : 0)"
            />
          </template>

          <!-- 创建时间列 -->
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="playPreview(record)">
                <template #icon>
                  <PauseCircleOutlined v-if="currentPlayingId === record.id && isPlaying" />
                  <PlayCircleOutlined v-else />
                </template>
                {{ currentPlayingId === record.id && isPlaying ? '暂停' : '播放' }}
              </a-button>
              <a-button type="link" size="small" @click="openEdit(record)">
                编辑
              </a-button>

              <a-popconfirm
                title="确定要彻底删除这首音乐吗？此操作不可恢复！"
                @confirm="handleDelete(record.id)"
              >
                <a-button type="link" size="small" danger>
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

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
