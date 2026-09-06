<template>
  <div v-if="musicList.length > 0" class="music-fab-wrapper" @click.stop>
    <!-- 折叠时的圆形封面按钮（match .fab 样式），始终显示 -->
    <button
      class="music-fab"
      :class="{ 'is-playing': isPlaying, 'is-expanded': !isCollapsed }"
      @click.stop="handleFabClick"
      :title="fabTitle"
      :aria-label="fabTitle"
    >
      <img
        v-if="currentMusic?.coverUrl"
        :src="currentMusic.coverUrl"
        alt="封面"
        class="cover-image"
        :class="{ rotating: isPlaying }"
        @error="handleImageError"
      />
      <div v-else class="cover-placeholder" :class="{ rotating: isPlaying }">
        <span class="music-icon">♪</span>
      </div>
    </button>

    <!-- 展开时向左延伸的信息+控件胶囊 -->
    <transition name="panel">
      <div v-if="!isCollapsed" class="music-panel">
        <div v-if="currentMusic" class="music-info">
          <div class="music-title">{{ currentMusic.title }}</div>
          <div class="music-artist">{{ loading ? '正在加载...' : playbackError || currentMusic.artist || '未知艺术家' }}</div>
        </div>

        <div class="controls">
          <button class="control-btn" @click.stop="playPrev" title="上一首" aria-label="上一首">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/>
            </svg>
          </button>

          <button class="control-btn play-btn" @click.stop="togglePlay" :title="loading ? '取消加载' : isPlaying ? '暂停' : '播放'" :aria-label="loading ? '取消加载' : isPlaying ? '暂停' : '播放'">
            <svg v-if="!isPlaying" viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
              <path d="M8 5v14l11-7z"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
              <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
            </svg>
          </button>

          <button class="control-btn" @click.stop="playNext" title="下一首" aria-label="下一首">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/>
            </svg>
          </button>

          <button class="control-btn list-btn" @click.stop="togglePlaylist" :title="showPlaylist ? '收起歌单' : '查看歌单'" :aria-label="showPlaylist ? '收起歌单' : '查看歌单'">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
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
    </transition>

    <!-- 播放列表向上弹出 -->
    <transition name="playlist">
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
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { getMusicList, type MusicItem } from '@/services/musicApi'
import { handleImageError } from '@/composables/useImageFallback'
import { resumeAudioContext } from '@/composables/useAudioLipSync'

const emit = defineEmits<{ play: [audio: HTMLAudioElement]; pause: [] }>()
const musicList = ref<MusicItem[]>([])
const currentIndex = ref(0)
const currentMusic = ref<MusicItem | null>(null)
const isPlaying = ref(false)
const isPaused = ref(false)
const loading = ref(false)
const playbackError = ref('')
const showPlaylist = ref(false)
const isCollapsed = ref(true)
let fullAudio: HTMLAudioElement | null = null
let vocalAudio: HTMLAudioElement | null = null
let loadedId: number | null = null
let generation = 0
let actionVersion = 0
let disposed = false
let cleanupTracks = () => {}
const pending = new Set<() => void>()
const playOwners = new WeakMap<HTMLAudioElement, number>()
const fabTitle = computed(() => !isCollapsed.value ? '折叠音乐胶囊' : isPlaying.value ? '展开播放器' : '播放音乐并展开')
const getCurrentAudio = () => [vocalAudio, fullAudio].find(audio => audio && !audio.paused && !audio.ended) || null
const publishState = () => {
  const source = getCurrentAudio()
  isPlaying.value = !!source
  if (source) emit('play', source)
  else emit('pause')
}
const pauseMusic = (userInitiated = true) => {
  if (userInitiated) actionVersion++
  generation++
  pending.forEach(cancel => cancel())
  pending.clear()
  loading.value = false
  fullAudio?.pause()
  vocalAudio?.pause()
  isPaused.value = true
  publishState()
}
const stopMusic = () => {
  pauseMusic(false)
  cleanupTracks()
  cleanupTracks = () => {}
  for (const audio of [fullAudio, vocalAudio]) {
    if (audio) { audio.removeAttribute('src'); audio.load() }
  }
  fullAudio = vocalAudio = null
  loadedId = null
  isPaused.value = false
}
const createAudio = (url: string) => {
  const audio = new Audio()
  audio.crossOrigin = 'anonymous'
  audio.preload = 'auto'
  audio.src = url
  return audio
}
const ensureTrack = () => {
  const item = currentMusic.value
  if (!item || loadedId === item.id) return
  stopMusic()
  loadedId = item.id
  fullAudio = createAudio(item.fullAudioUrl)
  vocalAudio = item.vocalUrl ? createAudio(item.vocalUrl) : null
  const full = fullAudio
  const vocal = vocalAudio
  const synchronize = () => {
    if (vocal && !vocal.paused && !full.paused && Math.abs(vocal.currentTime - full.currentTime) > 0.15) full.currentTime = vocal.currentTime
  }
  const ended = () => {
    if (getCurrentAudio()) return
    isPlaying.value = false
    if (musicList.value.length > 1) {
      currentIndex.value = (currentIndex.value + 1) % musicList.value.length
      currentMusic.value = musicList.value[currentIndex.value]
      void startPlayback(false, false)
    } else { isPaused.value = false; publishState() }
  }
  const stateChanged = () => { if (!loading.value) publishState() }
  const failed = () => { playbackError.value = '部分音轨无法播放，可切歌或重试'; stateChanged() }
  for (const audio of [full, vocal]) {
    audio?.addEventListener('pause', stateChanged)
    audio?.addEventListener('playing', stateChanged)
    audio?.addEventListener('error', failed)
    audio?.addEventListener('ended', ended)
  }
  vocal?.addEventListener('timeupdate', synchronize)
  cleanupTracks = () => {
    for (const audio of [full, vocal]) {
      audio?.removeEventListener('pause', stateChanged)
      audio?.removeEventListener('playing', stateChanged)
      audio?.removeEventListener('error', failed)
      audio?.removeEventListener('ended', ended)
    }
    vocal?.removeEventListener('timeupdate', synchronize)
  }
}
const playTrack = (audio: HTMLAudioElement, token: number) => new Promise<boolean>(resolve => {
  playOwners.set(audio, token)
  let finished = false
  let timer: ReturnType<typeof setTimeout>
  const finish = (ok: boolean) => {
    if (finished) return
    finished = true
    clearTimeout(timer)
    pending.delete(cancel)
    audio.removeEventListener('error', failed)
    if (!ok && playOwners.get(audio) === token) audio.pause()
    resolve(ok)
  }
  const cancel = () => finish(false)
  const failed = () => finish(false)
  pending.add(cancel)
  audio.addEventListener('error', failed, { once: true })
  timer = setTimeout(cancel, 8000)
  audio.play().then(() => {
    if (finished) { if (playOwners.get(audio) === token) audio.pause() }
    else finish(true)
  }, failed)
})
const startPlayback = async (resume: boolean, userInitiated = true) => {
  if (!currentMusic.value || disposed) return
  if (userInitiated) actionVersion++
  ensureTrack()
  const token = ++generation
  const tracks = [fullAudio, vocalAudio].filter((audio): audio is HTMLAudioElement => !!audio)
  playbackError.value = ''
  loading.value = true
  // 在用户点击调用栈内启动音轨和解锁 context，避免等待网络后丢失浏览器播放许可。
  void resumeAudioContext().catch(() => {})
  if (!resume) tracks.forEach(audio => { audio.currentTime = 0 })
  if (!tracks.length) {
    loading.value = false
    playbackError.value = '播放失败，请检查音频地址'
    publishState()
    return
  }
  const results = await Promise.all(tracks.map(audio => playTrack(audio, token).then(ok => {
    if (token === generation && !disposed) {
      if (ok) {
        loading.value = false
        isPaused.value = false
        publishState()
      } else if (tracks.some(track => !track.paused && !track.ended)) {
        loading.value = false
        playbackError.value = '部分音轨不可用，正在播放可用音轨'
        publishState()
      }
    }
    return ok
  })))
  if (token !== generation || disposed) return
  loading.value = false
  isPaused.value = !results.some(Boolean)
  if (!results.every(Boolean)) playbackError.value = results.some(Boolean) ? '部分音轨不可用，正在播放可用音轨' : '播放失败，请检查网络后点击重试'
  publishState()
}
const playMusic = () => startPlayback(false)
const resumeMusic = (userInitiated = true) => startPlayback(true, userInitiated)
const togglePlay = () => { if (isPlaying.value || loading.value) pauseMusic(); else void startPlayback(isPaused.value) }
const selectTrack = (index: number) => {
  if (index < 0 || index >= musicList.value.length) return
  actionVersion++
  const continuePlaying = isPlaying.value || loading.value
  stopMusic()
  currentIndex.value = index
  currentMusic.value = musicList.value[index]
  showPlaylist.value = false
  if (continuePlaying) void startPlayback(false, false)
}
const playPrev = () => { if (musicList.value.length > 1) selectTrack((currentIndex.value - 1 + musicList.value.length) % musicList.value.length) }
const playNext = () => { if (musicList.value.length > 1) selectTrack((currentIndex.value + 1) % musicList.value.length) }
const togglePlaylist = () => { showPlaylist.value = !showPlaylist.value }
const handleFabClick = () => {
  isCollapsed.value = !isCollapsed.value
  if (!isCollapsed.value && !isPlaying.value && !loading.value) void startPlayback(isPaused.value)
  if (isCollapsed.value) showPlaylist.value = false
}
onMounted(async () => {
  try {
    const items = await getMusicList()
    if (disposed) return
    musicList.value = items.sort((a, b) => a.sortOrder - b.sortOrder)
    currentMusic.value = musicList.value[0] || null
  } catch (error) { console.warn('[music] 歌单加载失败', error) }
})
onBeforeUnmount(() => { disposed = true; stopMusic() })
defineExpose({ playMusic, pauseMusic, resumeMusic, stopMusic, togglePlay, playNext, playPrev, selectTrack, togglePlaylist, getCurrentAudio, getActionVersion: () => actionVersion, isPlaying: () => isPlaying.value, isPaused: () => isPaused.value })
</script>

<style lang="scss" scoped>
@use "@/assets/styles/tokens" as *;

.music-fab-wrapper {
  position: relative;
  width: 50px;
  height: 50px;
}

// 折叠状态：与 BottomNavigation 中的 .fab 视觉一致
.music-fab {
  position: relative;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  overflow: hidden;
  padding: 0;
  cursor: pointer;
  color: var(--text-main);
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease-in-out;

  &:hover {
    background: var(--bg-hover);
    transform: translateY(-2px);
    box-shadow: var(--shadow-lg);
  }

  &.is-expanded {
    // 展开时保持圆形按钮，但提示视觉：略微高亮
    box-shadow: var(--shadow-md);
  }
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;

  &.rotating {
    animation: rotate 10s linear infinite;
  }
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  display: flex;
  align-items: center;
  justify-content: center;

  &.rotating {
    animation: rotate 10s linear infinite;
  }
}

.music-icon {
  font-size: 20px;
  color: white;
}

// 展开面板：向左延伸，与 fab 圆心垂直居中
.music-panel {
  position: absolute;
  top: 50%;
  right: calc(100% + 8px);
  transform: translateY(-50%);
  height: 50px;
  padding: 6px 16px 6px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: 30px;
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;

  @include respond(md) {
    // 移动端面板收窄，避免溢出
    max-width: calc(100vw - 80px);
  }
}

.music-info {
  min-width: 0;
  max-width: 160px;
  text-align: left;
  overflow: hidden;

  @include respond(md) {
    max-width: 100px;
  }
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
}

.play-btn {
  width: 30px;
  height: 30px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  color: white;
  border-radius: 50%;

  &:hover {
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
    color: white;
    opacity: 0.9;
  }
}

// 播放列表向上弹出
.playlist-panel {
  position: absolute;
  bottom: calc(100% + 10px);
  right: 0;
  width: 280px;
  max-height: 260px;
  overflow: auto;
  padding: 8px;
  border-radius: 18px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-lg);
  z-index: 1;

  @include respond(md) {
    width: 240px;
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

// 面板过渡动画
.panel-enter-active,
.panel-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: translateY(-50%) translateX(12px);
}

.playlist-enter-active,
.playlist-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.playlist-enter-from,
.playlist-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
