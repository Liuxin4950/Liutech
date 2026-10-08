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

          <button class="control-btn mode-btn" @click.stop="cyclePlayMode" :title="modeLabel + '，点击切换'" :aria-label="modeLabel + '，点击切换播放模式'">
            {{ playMode === 'single' ? '↻1' : playMode === 'list' ? '↻' : '→|' }}
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
        <div class="playlist-toolbar">
          <span>{{ modeLabel }}</span>
          <span>{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
        </div>
        <input class="music-progress" type="range" min="0" :max="duration || 0" :value="currentTime" step="0.1" :disabled="duration <= 0 || loading" aria-label="播放进度" @change="seekTo(Number(($event.target as HTMLInputElement).value))" />
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
import { prepareAnalysisOnlyAudio, resumeAudioContext } from '@/composables/useAudioLipSync'

type PlayMode = 'list' | 'single' | 'sequence'
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
const playMode = ref<PlayMode>('list')
const currentTime = ref(0)
const duration = ref(0)
const modeLabels: Record<PlayMode, string> = { list: '列表循环', single: '单曲循环', sequence: '顺序播放' }
const modeLabel = computed(() => modeLabels[playMode.value])
let fullAudio: HTMLAudioElement | null = null
let vocalAudio: HTMLAudioElement | null = null
let vocalAvailable = false
let loadedId: number | null = null
let generation = 0
let actionVersion = 0
let disposed = false
let cleanupTracks = () => {}
const pending = new Set<() => void>()
const playOwners = new WeakMap<HTMLAudioElement, number>()
const fabTitle = computed(() => !isCollapsed.value ? '折叠音乐胶囊' : isPlaying.value ? '展开播放器' : '播放音乐并展开')
const getCurrentAudio = () => fullAudio && !fullAudio.paused && !fullAudio.ended && vocalAvailable
  && vocalAudio && !vocalAudio.paused && !vocalAudio.ended ? vocalAudio : null
const publishState = () => {
  isPlaying.value = !!fullAudio && !fullAudio.paused && !fullAudio.ended
  // 仍发布主轨播放事件以协调 TTS；口型协调器只读取 getCurrentAudio 的纯人声。
  if (isPlaying.value) emit('play', getCurrentAudio() || fullAudio!)
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
  if (fullAudio) fullAudio.muted = false
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
  vocalAvailable = false
  currentTime.value = duration.value = 0
  isPaused.value = false
}
const createAudio = (url: string, analysis = false) => {
  const audio = new Audio()
  audio.crossOrigin = 'anonymous'
  audio.preload = 'auto'
  if (analysis) prepareAnalysisOnlyAudio(audio)
  audio.src = url
  return audio
}
const ensureTrack = () => {
  const item = currentMusic.value
  if (!item || loadedId === item.id) return
  stopMusic()
  loadedId = item.id
  fullAudio = createAudio(item.fullAudioUrl)
  vocalAudio = item.vocalUrl ? createAudio(item.vocalUrl, true) : null
  vocalAvailable = !!vocalAudio
  const full = fullAudio
  const vocal = vocalAudio
  let completed = false
  const active = () => fullAudio === full && !disposed
  const synchronize = () => {
    if (!active()) return
    currentTime.value = full.currentTime
    duration.value = Number.isFinite(full.duration) ? full.duration : 0
    if (vocalAvailable && vocal && !vocal.paused && !full.paused && !vocal.ended
      && Math.abs(vocal.currentTime - full.currentTime) > 0.08) vocal.currentTime = full.currentTime
  }
  const ended = () => {
    if (!active() || completed || loading.value) return
    completed = true
    pauseMusic(false)
    isPaused.value = false
    if (playMode.value === 'sequence' && currentIndex.value === musicList.value.length - 1) return
    if (playMode.value !== 'single') currentIndex.value = (currentIndex.value + 1) % musicList.value.length
    currentMusic.value = musicList.value[currentIndex.value] || null
    // 同一首重播也必须重置完成标记。
    completed = false
    void startPlayback(false, false)
  }
  const stateChanged = () => { if (active() && !loading.value) publishState() }
  const vocalFailed = () => {
    if (!active()) return
    vocalAvailable = false
    vocal?.pause()
    playbackError.value = '人声音轨不可用，音乐继续播放，口型已暂停'
    stateChanged()
  }
  const fullFailed = () => {
    if (!active()) return
    playbackError.value = '音乐无法播放，请检查网络后重试'
    pauseMusic(false)
  }
  const buffering = (event: Event) => {
    if (!active() || loading.value || !isPlaying.value || full.ended
      || (event.currentTarget === vocal && (!vocalAvailable || vocal?.seeking))) return
    // 任一有效轨道等待数据时统一暂停，重新就绪后对齐同一主轨时间。
    pauseMusic(false)
    void startPlayback(true, false)
  }
  for (const audio of [full, vocal]) {
    audio?.addEventListener('pause', stateChanged)
    audio?.addEventListener('playing', stateChanged)
    audio?.addEventListener('waiting', buffering)
  }
  full.addEventListener('error', fullFailed)
  full.addEventListener('ended', ended)
  full.addEventListener('timeupdate', synchronize)
  full.addEventListener('durationchange', synchronize)
  vocal?.addEventListener('error', vocalFailed)
  vocal?.addEventListener('ended', vocalFailed)
  cleanupTracks = () => {
    for (const audio of [full, vocal]) {
      audio?.removeEventListener('pause', stateChanged)
      audio?.removeEventListener('playing', stateChanged)
      audio?.removeEventListener('waiting', buffering)
    }
    full.removeEventListener('error', fullFailed)
    full.removeEventListener('ended', ended)
    full.removeEventListener('timeupdate', synchronize)
    full.removeEventListener('durationchange', synchronize)
    vocal?.removeEventListener('error', vocalFailed)
    vocal?.removeEventListener('ended', vocalFailed)
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
  try {
    audio.play().then(() => {
      if (finished) { if (playOwners.get(audio) === token) audio.pause() }
      else finish(true)
    }, failed)
  } catch { failed() }
})
const startPlayback = async (resume: boolean, userInitiated = true) => {
  if (!currentMusic.value || disposed || !musicList.value.length) return
  if (userInitiated) actionVersion++
  ensureTrack()
  const token = ++generation
  const full = fullAudio!
  const vocal = vocalAudio
  const tracks = [full, vocal].filter((audio): audio is HTMLAudioElement => !!audio)
  const position = resume && !full.ended ? full.currentTime : 0
  playbackError.value = ''
  loading.value = true
  isPlaying.value = false
  full.muted = true
  // 在点击栈中解锁两个元素；先静音预备，不能让先加载完成的音轨提前出声。
  void resumeAudioContext().then(() => { if (token === generation && vocal) prepareAnalysisOnlyAudio(vocal) }).catch(() => {})
  tracks.forEach(audio => { audio.currentTime = position })
  const primed = await Promise.all(tracks.map(audio => playTrack(audio, token).then(ok => {
    if (token === generation && !disposed) audio.pause()
    return ok
  })))
  if (token !== generation || disposed) return
  if (!primed[0]) {
    full.muted = false
    loading.value = false
    isPaused.value = true
    playbackError.value = '播放失败，请检查网络后点击重试'
    publishState()
    return
  }
  vocalAvailable = !!vocal && !!primed[1]
  const readyTracks = vocalAvailable ? tracks : [full]
  readyTracks.forEach(audio => { audio.currentTime = position })
  const results = await Promise.all(readyTracks.map(audio => playTrack(audio, token)))
  if (token !== generation || disposed) return
  loading.value = false
  if (!results[0]) {
    readyTracks.forEach(audio => audio.pause())
    playbackError.value = '播放失败，请检查网络后点击重试'
  } else {
    vocalAvailable = vocalAvailable && !!results[1]
    if (vocalAvailable && vocal && Math.abs(vocal.currentTime - full.currentTime) > 0.08) vocal.currentTime = full.currentTime
    if (!vocalAvailable) playbackError.value = '人声音轨不可用，音乐继续播放，口型已暂停'
  }
  full.muted = false
  isPaused.value = !results[0]
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
const setPlayMode = (mode: PlayMode) => { playMode.value = mode }
const cyclePlayMode = () => { playMode.value = ({ list: 'single', single: 'sequence', sequence: 'list' } as const)[playMode.value] }
const seekTo = (position: number) => {
  if (!fullAudio || !Number.isFinite(position) || duration.value <= 0) return
  actionVersion++
  const target = Math.max(0, Math.min(position, duration.value))
  fullAudio.currentTime = target
  if (vocalAudio && vocalAvailable) vocalAudio.currentTime = target
  currentTime.value = target
}
const formatTime = (seconds: number) => `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`
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
    musicList.value = items.sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    currentMusic.value = musicList.value[0] || null
  } catch (error) { console.warn('[music] 歌单加载失败', error) }
})
onBeforeUnmount(() => { disposed = true; stopMusic() })
defineExpose({ playMusic, pauseMusic, resumeMusic, stopMusic, togglePlay, playNext, playPrev, selectTrack, togglePlaylist, setPlayMode, seekTo, getCurrentAudio, getActionVersion: () => actionVersion, isPlaying: () => isPlaying.value, isLoading: () => loading.value, isPaused: () => isPaused.value })
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

.playlist-toolbar {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.music-progress {
  display: block;
  width: calc(100% - 24px);
  margin: 6px 12px 10px;
  accent-color: var(--color-primary);
}

.mode-btn {
  min-width: 28px;
  font-size: 16px;
  font-weight: 600;
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
