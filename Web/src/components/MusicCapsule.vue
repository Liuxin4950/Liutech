<template>
  <div v-if="musicList.length > 0" class="music-shell" @click.stop>
    <div class="music-capsule" :class="{ collapsed: isCollapsed }">
      <div class="cover-wrapper" :class="{ rotating: isPlaying }" @click="toggleCollapse" :title="isCollapsed ? '展开音乐胶囊' : '折叠音乐胶囊'">
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

      <div v-if="!isCollapsed && currentMusic" class="music-info">
        <div class="music-title">{{ currentMusic.title }}</div>
        <div class="music-artist">{{ currentMusic.artist || '未知艺术家' }}</div>
      </div>

      <div v-if="!isCollapsed" class="controls">
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

        <button class="control-btn list-btn" @click.stop="togglePlaylist" :title="showPlaylist ? '收起歌单' : '查看歌单'">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M8 6h12"></path>
            <path d="M8 12h12"></path>
            <path d="M8 18h12"></path>
            <circle cx="4" cy="6" r="1"></circle>
            <circle cx="4" cy="12" r="1"></circle>
            <circle cx="4" cy="18" r="1"></circle>
          </svg>
        </button>
      </div>
    </div>

    <div v-if="showPlaylist && !isCollapsed" class="playlist-panel">
      <button
        v-for="(item, index) in musicList"
        :key="item.id"
        class="playlist-item"
        :class="{ active: index === currentIndex }"
        @click.stop="selectTrack(index)"
        :title="item.title"
      >
        <span class="playlist-index">{{ index + 1 }}</span>
        <span class="playlist-text">
          <span class="playlist-title">{{ item.title }}</span>
          <span class="playlist-artist">{{ item.artist || '未知艺术家' }}</span>
        </span>
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
const isPaused = ref(false)
const showPlaylist = ref(false)
const isCollapsed = ref(false)

// 音频对象
let fullAudio: HTMLAudioElement | null = null   // 伴奏
let vocalAudio: HTMLAudioElement | null = null  // 人声（播放+嘴型同步）
let lastFullUrl: string | null = null
let lastVocalUrl: string | null = null
let isSyncing = false
let unbindSync: (() => void) | null = null

// 获取音乐列表
const fetchMusicList = async () => {
  try {
    const list = await getMusicList()
    musicList.value = list.sort((a, b) => a.sortOrder - b.sortOrder)
    if (list.length > 0) {
      currentIndex.value = 0
      currentMusic.value = list[0]
    }
  } catch {
    // 获取音乐列表失败时静默处理
  }
}

const createAudio = (url: string) => {
  const audio = new Audio(url)
  audio.preload = 'auto'
  audio.crossOrigin = 'anonymous'
  audio.volume = 1
  return audio
}

const waitForCanPlay = (audio: HTMLAudioElement, timeoutMs = 6000) => {
  return new Promise<void>((resolve) => {
    if (audio.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA) {
      resolve()
      return
    }

    let settled = false
    const cleanup = () => {
      audio.removeEventListener('canplay', onCanPlay)
      audio.removeEventListener('error', onError)
    }
    const done = () => {
      if (settled) return
      settled = true
      cleanup()
      resolve()
    }
    const onCanPlay = () => done()
    const onError = () => done()

    audio.addEventListener('canplay', onCanPlay)
    audio.addEventListener('error', onError)

    window.setTimeout(done, timeoutMs)
  })
}

const syncCurrentTime = (master: HTMLAudioElement, slave: HTMLAudioElement) => {
  const drift = Math.abs(slave.currentTime - master.currentTime)
  if (drift > 0.15 && Number.isFinite(master.currentTime)) {
    slave.currentTime = master.currentTime
  }
  if (slave.playbackRate !== master.playbackRate) {
    slave.playbackRate = master.playbackRate
  }
}

const bindSync = (master: HTMLAudioElement, slave: HTMLAudioElement) => {
  if (isSyncing) return
  isSyncing = true

  const onTimeUpdate = () => {
    if (!fullAudio || !vocalAudio) return
    syncCurrentTime(master, slave)
  }
  const onSeeking = () => {
    if (!fullAudio || !vocalAudio) return
    if (Number.isFinite(master.currentTime)) slave.currentTime = master.currentTime
  }
  const onPlay = async () => {
    if (!fullAudio || !vocalAudio) return
    if (!slave.paused) return
    try {
      await slave.play()
    } catch {
    }
  }
  const onPause = () => {
    if (!fullAudio || !vocalAudio) return
    if (!slave.paused) slave.pause()
  }

  master.addEventListener('timeupdate', onTimeUpdate)
  master.addEventListener('seeking', onSeeking)
  master.addEventListener('seeked', onSeeking)
  master.addEventListener('play', onPlay)
  master.addEventListener('pause', onPause)

  const unbind = () => {
    master.removeEventListener('timeupdate', onTimeUpdate)
    master.removeEventListener('seeking', onSeeking)
    master.removeEventListener('seeked', onSeeking)
    master.removeEventListener('play', onPlay)
    master.removeEventListener('pause', onPause)
    isSyncing = false
  }

  unbindSync = unbind
  fullAudio?.addEventListener('ended', unbind, { once: true })
}

const ensureTrackLoaded = () => {
  if (!currentMusic.value) return

  const { fullAudioUrl, vocalUrl } = currentMusic.value

  const fullChanged = lastFullUrl !== fullAudioUrl
  const vocalChanged = lastVocalUrl !== (vocalUrl || null)
  if (fullChanged || vocalChanged) {
    stopMusic()
  }

  if (!fullAudio) {
    fullAudio = createAudio(fullAudioUrl)
    lastFullUrl = fullAudioUrl
    fullAudio.onended = () => {
      playNext()
    }
  }

  if (vocalUrl) {
    if (!vocalAudio) {
      vocalAudio = createAudio(vocalUrl)
      lastVocalUrl = vocalUrl
    }
  } else {
    vocalAudio = null
    lastVocalUrl = null
  }

  const master = vocalAudio || fullAudio
  const slave = vocalAudio ? fullAudio : null
  if (slave) bindSync(master, slave)
}

const startPlayback = async (mode: 'fromStart' | 'resume') => {
  if (!currentMusic.value) return
  ensureTrackLoaded()
  if (!fullAudio) return

  const master = vocalAudio || fullAudio
  const slave = vocalAudio ? fullAudio : null

  if (mode === 'fromStart') {
    fullAudio.currentTime = 0
    if (vocalAudio) vocalAudio.currentTime = 0
  } else if (mode === 'resume') {
    if (slave) syncCurrentTime(master, slave)
  }

  const readiness = [waitForCanPlay(fullAudio)]
  if (vocalAudio) readiness.push(waitForCanPlay(vocalAudio))
  await Promise.all(readiness)

  const playPromises: Promise<any>[] = []
  playPromises.push(fullAudio.play())
  if (vocalAudio) playPromises.push(vocalAudio.play())
  const results = await Promise.allSettled(playPromises)
  const ok = results.some(r => r.status === 'fulfilled')
  isPlaying.value = ok
  isPaused.value = !ok ? isPaused.value : false
  if (ok) emit('play', master)
}

// 播放音乐（从头开始）
const playMusic = async () => {
  isPaused.value = false
  await startPlayback('fromStart')
}

// 停止音乐
const stopMusic = () => {
  if (unbindSync) {
    unbindSync()
    unbindSync = null
  }
  if (fullAudio) {
    fullAudio.onended = null
    fullAudio.ontimeupdate = null
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
  isPaused.value = false
}

// 暂停音乐
const pauseMusic = () => {
  fullAudio?.pause()
  vocalAudio?.pause()
  isPlaying.value = false
  isPaused.value = true
  emit('pause')
}

// 继续播放
const resumeMusic = async () => {
  if (!currentMusic.value) return
  await startPlayback('resume')
}

// 切换播放/暂停
const togglePlay = () => {
  if (isPlaying.value) {
    pauseMusic()
  } else {
    if (isPaused.value) {
      resumeMusic()
      return
    }
    playMusic()
  }
}

// 上一首
const playPrev = () => {
  if (musicList.value.length <= 1) return
  const shouldAutoPlay = isPlaying.value
  currentIndex.value = (currentIndex.value - 1 + musicList.value.length) % musicList.value.length
  currentMusic.value = musicList.value[currentIndex.value]
  if (shouldAutoPlay) {
    playMusic()
  }
}

// 下一首
const playNext = () => {
  if (musicList.value.length <= 1) return
  const shouldAutoPlay = isPlaying.value
  currentIndex.value = (currentIndex.value + 1) % musicList.value.length
  currentMusic.value = musicList.value[currentIndex.value]
  if (shouldAutoPlay) {
    playMusic()
  }
}

const selectTrack = (index: number) => {
  if (index < 0 || index >= musicList.value.length) return
  const shouldAutoPlay = isPlaying.value || isPaused.value
  currentIndex.value = index
  currentMusic.value = musicList.value[index]
  showPlaylist.value = false
  if (shouldAutoPlay) {
    playMusic()
  }
}

const togglePlaylist = () => {
  showPlaylist.value = !showPlaylist.value
}

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  if (isCollapsed.value) {
    showPlaylist.value = false
  }
}

// 组件暴露的方法
defineExpose({
  playMusic,
  pauseMusic,
  resumeMusic,
  stopMusic,
  togglePlay,
  playNext,
  playPrev,
  selectTrack,
  togglePlaylist,
  isPlaying: () => isPlaying.value,
  isPaused: () => isPaused.value
})

// 事件定义
const emit = defineEmits<{
  (e: 'play', audio: HTMLAudioElement): void
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
.music-shell {
  position: absolute;
  top: -70px;
  right: 20px;
  z-index: 100;

  @include respond(md) {
    top: -60px;
    right: 10px;
  }
}

.music-capsule {
  width: 380px;
  height: 60px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: 30px;
  padding: 8px 16px 8px 8px;
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(10px);
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: width 0.28s ease, padding 0.28s ease, border-radius 0.28s ease, box-shadow 0.2s ease;

  @include respond(md) {
    width: 320px;
    height: 50px;
    padding: 6px 12px 6px 6px;
  }

  &.collapsed {
    width: 60px;
    padding: 8px;
    border-radius: 999px;
    gap: 0;

    @include respond(md) {
      width: 50px;
      padding: 6px;
    }
  }
}

.list-btn {
  flex-shrink: 0;
}

.cover-wrapper {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  flex-shrink: 0;
  transition: transform 0.3s ease;

  @include respond(md) {
    width: 38px;
    height: 38px;
  }

  &:hover {
    transform: scale(1.08);
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
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.music-icon {
  font-size: 20px;
  color: white;

  @include respond(md) {
    font-size: 16px;
  }
}

.music-info {
  flex: 1;
  min-width: 0;
  text-align: left;
  overflow: hidden;
}

.music-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-title);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;

  @include respond(md) {
    font-size: 12px;
  }
}

.music-artist {
  font-size: 11px;
  color: var(--text-muted);
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
  gap: 4px;
  flex-shrink: 0;
}

.control-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: 50%;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    color: var(--color-primary);
    background: var(--bg-soft);
  }

  &:active {
    transform: scale(0.92);
  }

  svg {
    @include respond(md) {
      width: 16px;
      height: 16px;
    }
  }
}

.playlist-panel {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 320px;
  max-height: 280px;
  overflow: auto;
  padding: 8px;
  border-radius: 18px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-lg);
  z-index: 101;

  @include respond(md) {
    width: 260px;
    right: 10px;
    max-height: 220px;
  }
}

.playlist-item {
  width: 100%;
  border: none;
  background: transparent;
  border-radius: 12px;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-main);
  cursor: pointer;
  text-align: left;
  transition: background-color 0.2s ease, color 0.2s ease;

  &:hover {
    background: var(--bg-hover);
  }

  &.active {
    background: var(--bg-active);
    color: var(--text-title);
  }
}

.playlist-index {
  width: 20px;
  font-size: 12px;
  color: var(--text-subtle);
  flex-shrink: 0;
}

.playlist-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.playlist-title,
.playlist-artist {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.playlist-title {
  font-size: 13px;
  font-weight: 600;
}

.playlist-artist {
  font-size: 11px;
  color: var(--text-subtle);
}

.play-btn {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  color: white;
  border-radius: 50%;

  @include respond(md) {
    width: 28px;
    height: 28px;
  }

  &:hover {
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
    color: white;
    opacity: 0.9;
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
