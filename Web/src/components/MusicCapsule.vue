<template>
  <div class="music-capsule" v-if="musicList.length > 0">
    <!-- 封面旋转区域 -->
    <div class="cover-wrapper" :class="{ rotating: isPlaying }" @click="togglePlay">
      <img
        v-if="currentMusic?.coverUrl"
        :src="currentMusic.coverUrl"
        alt="封面"
        class="cover-image"
      />
      <div v-else class="cover-placeholder">
        <span class="music-icon">♪</span>
      </div>
    </div>

    <!-- 歌曲信息 -->
    <div class="music-info" v-if="currentMusic">
      <div class="music-title">{{ currentMusic.title }}</div>
      <div class="music-artist">{{ currentMusic.artist || '未知艺术家' }}</div>
    </div>

    <!-- 控制按钮 -->
    <div class="controls">
      <button class="control-btn" @click.stop="playPrev" title="上一首">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/>
        </svg>
      </button>

      <button class="control-btn play-btn" @click.stop="togglePlay" :title="isPlaying ? '暂停' : '播放'">
        <svg v-if="!isPlaying" viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
          <path d="M8 5v14l11-7z"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
          <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
        </svg>
      </button>

      <button class="control-btn" @click.stop="playNext" title="下一首">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getMusicList, type MusicItem } from '../services/musicApi'

// 播放状态
const musicList = ref<MusicItem[]>([])
const currentIndex = ref(0)
const currentMusic = ref<MusicItem | null>(null)
const isPlaying = ref(false)

// 音频对象
let fullAudio: HTMLAudioElement | null = null   // 伴奏
let vocalAudio: HTMLAudioElement | null = null  // 人声（播放+嘴型同步）

// 获取音乐列表
const fetchMusicList = async () => {
  try {
    const list = await getMusicList()
    musicList.value = list.sort((a, b) => a.sortOrder - b.sortOrder)
    if (list.length > 0) {
      currentIndex.value = 0
      currentMusic.value = list[0]
    }
  } catch (error) {
    console.error('获取音乐列表失败:', error)
  }
}

// 播放音乐
const playMusic = () => {
  if (!currentMusic.value) return

  const { fullAudioUrl, vocalUrl } = currentMusic.value

  // 停止之前的播放
  stopMusic()

  // 播放伴奏（用户听到）
  fullAudio = new Audio(fullAudioUrl)
  fullAudio.volume = 1
  fullAudio.crossOrigin = 'anonymous'

  // 播放人声（用户听到 + 模型驱动嘴型）
  if (vocalUrl) {
    vocalAudio = new Audio(vocalUrl)
    vocalAudio.volume = 1
    vocalAudio.crossOrigin = 'anonymous'

    // 同步播放进度
    fullAudio.ontimeupdate = () => {
      if (fullAudio && vocalAudio && Math.abs(vocalAudio.currentTime - fullAudio.currentTime) > 0.5) {
        vocalAudio.currentTime = fullAudio.currentTime
      }
    }
  }

  // 监听播放完成
  fullAudio.onended = () => {
    playNext()
  }

  // 开始播放
  fullAudio.play().catch(console.error)
  vocalAudio?.play().catch(console.error)

  isPlaying.value = true

  // 触发Live2D模型对口型（使用人声）
  emit('play', vocalUrl || fullAudioUrl)
}

// 停止音乐
const stopMusic = () => {
  if (fullAudio) {
    fullAudio.pause()
    fullAudio.currentTime = 0
    fullAudio = null
  }
  if (vocalAudio) {
    vocalAudio.pause()
    vocalAudio.currentTime = 0
    vocalAudio = null
  }
  isPlaying.value = false
}

// 暂停音乐
const pauseMusic = () => {
  fullAudio?.pause()
  vocalAudio?.pause()
  isPlaying.value = false
  emit('pause')
}

// 切换播放/暂停
const togglePlay = () => {
  if (isPlaying.value) {
    pauseMusic()
  } else {
    playMusic()
  }
}

// 上一首
const playPrev = () => {
  if (musicList.value.length <= 1) return
  currentIndex.value = (currentIndex.value - 1 + musicList.value.length) % musicList.value.length
  currentMusic.value = musicList.value[currentIndex.value]
  if (isPlaying.value) {
    playMusic()
  }
}

// 下一首
const playNext = () => {
  if (musicList.value.length <= 1) return
  currentIndex.value = (currentIndex.value + 1) % musicList.value.length
  currentMusic.value = musicList.value[currentIndex.value]
  if (isPlaying.value) {
    playMusic()
  }
}

// 组件暴露的方法
defineExpose({
  playMusic,
  pauseMusic,
  stopMusic,
  togglePlay,
  playNext,
  playPrev
})

// 事件定义
const emit = defineEmits<{
  (e: 'play', audioUrl: string): void
  (e: 'pause'): void
}>()

onMounted(() => {
  fetchMusicList()
})

onBeforeUnmount(() => {
  stopMusic()
})
</script>

<style lang="scss" scoped>
@use "@/assets/styles/tokens" as *;
.music-capsule {
  position: absolute;
  top: -120px;
  right: 20px;
  width: 180px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.85));
  border-radius: 20px;
  padding: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  backdrop-filter: blur(10px);
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;

  @include respond(md) {
    width: 160px;
    top: -100px;
    right: 10px;
    padding: 12px;
  }
}

.cover-wrapper {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  flex-shrink: 0;
  transition: transform 0.3s ease;

  @include respond(md) {
    width: 60px;
    height: 60px;
  }

  &:hover {
    transform: scale(1.05);
  }

  &.rotating {
    animation: rotate 10s linear infinite;
  }
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.music-icon {
  font-size: 32px;
  color: white;

  @include respond(md) {
    font-size: 24px;
  }
}

.music-info {
  text-align: center;
  width: 100%;
  overflow: hidden;
}

.music-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;

  @include respond(md) {
    font-size: 12px;
  }
}

.music-artist {
  font-size: 12px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  @include respond(md) {
    font-size: 10px;
  }
}

.controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
}

.control-btn {
  background: none;
  border: none;
  color: #666;
  cursor: pointer;
  padding: 8px;
  border-radius: 50%;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    color: #667eea;
    background: rgba(102, 126, 234, 0.1);
  }

  &:active {
    transform: scale(0.95);
  }
}

.play-btn {
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  border-radius: 50%;

  @include respond(md) {
    width: 36px;
    height: 36px;
  }

  &:hover {
    background: linear-gradient(135deg, #5a6fd6, #6a4190);
    color: white;
  }
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
