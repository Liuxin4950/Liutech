<template>
    <div class="container" :class="[{ passive: !props.interactive }, `is-${loadState}`]">
        <canvas
            ref="canvasRef"
            class="live2d-canvas"
            :aria-hidden="loadState !== 'ready'"
            @click="handleCanvasClick"
            @pointerdown="onPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
            @pointercancel="cancelPointer"
            @lostpointercapture="cancelPointer"
        ></canvas>

        <div
            v-if="loadState !== 'ready'"
            class="live2d-placeholder"
            :class="{ 'live2d-placeholder--error': loadState === 'error' }"
            role="status"
            aria-live="polite"
        >
            <div class="live2d-placeholder__panel">
                <div class="live2d-placeholder__avatar-wrap">
                    <img :src="loadingArtworkUrl" alt="" class="live2d-placeholder__avatar" />
                </div>
                <p class="live2d-placeholder__title">
                    <span v-if="loadState === 'loading'" class="live2d-placeholder__spinner" aria-hidden="true"></span>
                    {{ loadState === 'error' ? '这次连接没有成功' : '正在唤醒纳西妲' }}
                </p>
                <p class="live2d-placeholder__hint">
                    {{ loadState === 'error' ? '检查网络后，可以再试一次' : '准备好后就可以和她互动了' }}
                </p>
                <button
                    v-if="loadState === 'error'"
                    type="button"
                    class="live2d-placeholder__retry"
                    @click.stop="retryLive2D"
                >
                    重新加载
                </button>
            </div>
        </div>
    </div>
</template>


<script setup lang="ts">
/**
 * Live2D 模型展示组件
 * 作者: 刘鑫
 * 修改时间: 2025-09-24 19:33:22 +08:00
 * 功能: 纯净的Live2D模型展示，支持基本交互和拖拽，优化资源管理
 */
import { onMounted, onBeforeUnmount, ref, watch } from 'vue';
import loadingArtworkUrl from '@/assets/aifile/live2d-loading.png'

import { useAudioLipSync } from '@/composables/useAudioLipSync'
import { createAvatarController, loadAvatarExpressions, type AvatarCue, type AvatarState } from '@/composables/avatarController'

const emit = defineEmits<{
    click: []
    'speak-start': []
    'load-start': []
    ready: []
    error: []
    resume: []
}>()

const props = withDefaults(defineProps<{
    interactive?: boolean
    followPointer?: boolean
    visible?: boolean
    presentationState?: AvatarState
}>(), {
    interactive: true,
    followPointer: true,
    visible: true,
    presentationState: 'idle'
})

// 声明全局变量类型
declare global {
    interface Window {
        PIXI: any;
        LIVE2DCUBISMCORE: any;
        Live2DModel: any;
        __LIUTECH_LIVE2D_SCRIPTS__?: Promise<void>;
    }
}

// Live2D模型路径
const cubism4Model = '/live2d/model/Nahida/Nahida_1080.model3.json';

// 模型实例和PIXI应用实例
let model: any = null;
let app: any = null;
let isInitialized = false; // 初始化状态标记
type Live2dLoadState = 'loading' | 'ready' | 'error'
const loadState = ref<Live2dLoadState>('loading')
const canvasRef = ref<HTMLCanvasElement | null>(null)
let isComponentMounted = false
let avatar: ReturnType<typeof createAvatarController> | null = null
let modelGeneration = 0
let reducedMotion: MediaQueryList | null = null
let pointerId: number | null = null
let dragStart = { x: 0, y: 0, modelX: 0, modelY: 0 }
let dragged = false
let ignoreClick = false

// 窗口大小调整处理器
let resizeHandler: (() => void) | null = null;
let resizeObserver: ResizeObserver | null = null
let resizeTimer: ReturnType<typeof setTimeout> | null = null
let resizeTriggerCount = 0
let lastDpr = 0
let lastWidth = 0
let lastHeight = 0
let windowMouseMoveHandler: ((event: PointerEvent) => void) | null = null

// 音乐口型同步的 suspend 标记由 MainLayout 通过 speakAudioUrl/Element 的调用来触发挂起

const applyInteractionMode = () => {
    if (model) {
        model.interactive = props.interactive === true
    }
    if (app?.stage) {
        app.stage.interactive = props.interactive === true
    }
}

const focusModelAtClientPoint = (clientX: number, clientY: number) => {
    if (!avatar || !canvasRef.value || !props.followPointer) return
    const rect = canvasRef.value.getBoundingClientRect()
    if (rect.width && rect.height) avatar.point((clientX - rect.left) / rect.width * 2 - 1, 1 - (clientY - rect.top) / rect.height * 2)
}

const updatePointerTracking = () => {
    if (windowMouseMoveHandler) window.removeEventListener('pointermove', windowMouseMoveHandler)
    windowMouseMoveHandler = null
    if (!props.followPointer) { avatar?.clearPointer(); return }
    windowMouseMoveHandler = (event: PointerEvent) => {
        if (event.pointerType === 'mouse') focusModelAtClientPoint(event.clientX, event.clientY)
    }
    window.addEventListener('pointermove', windowMouseMoveHandler, { passive: true })
}
const clearPointer = () => avatar?.clearPointer()
// 分析只交付目标，不在独立 rAF 中改 Cubism 参数。
const lipSync = useAudioLipSync(value => avatar?.setMouth(value))

// 音乐播放事件处理：由 MainLayout 在收到 BottomNavigation 的音乐事件后调用
function startMusicLipSync(audio: HTMLAudioElement) {
    if (!audio) return
    if (!model || !props.visible || document.hidden) return
    avatar?.setState('music')
    void lipSync.start(audio)
}

function stopMusicLipSync() {
    lipSync.stop()
    avatar?.setState(props.presentationState)
}

/**
 * 统一“让 Live2D 读音频”的入口（替代原本的 speak 思路）
 * - 传入：音频URL（例如未来 TTS 返回的 url）
 * - 行为：创建音频并绑定口型，调用方控制 play/暂停和生命周期
 *
 * 注意：浏览器可能要求用户先有一次交互才能播放音频
 */
const speakAudioUrl = async (url: string) => {
    if (!model || loadState.value !== 'ready') return null
    emit('speak-start')
    return lipSync.speak({ url, volume: 1, crossOrigin: 'anonymous' })
}

/**
 * 使用“外部已创建/已预加载”的 AudioElement 播放并驱动口型
 *
 * 用途：
 * - Web 侧收到 audioUrl 后可以先行预加载（audio.load），播放时复用同一个 element
 * - 避免重复创建 Audio 导致再次等待网络/磁盘
 */
const speakAudioElement = async (audio: HTMLAudioElement) => {
    if (!model || loadState.value !== 'ready' || !props.visible || document.hidden) return null
    if (!audio) return null
    emit('speak-start')
    try {
        audio.preload = 'auto'
    } catch {
    }
    try {
        await lipSync.start(audio)
    } catch (e) {
        console.warn('[lipSync] speakAudioElement start failed:', e)
    }
    return audio
}

const applyAvatarCue = (cue: AvatarCue) => {
    avatar?.applyCue(cue)
    const motion = cue.motion?.trim()
    if (motion && model) avatar?.playMotion(motion, model.internalModel.settings.motions, name => { void model.motion(name) })
}

defineExpose({
    resetAvatar() { avatar?.reset() },
    speakAudioUrl,
    speakAudioElement,
    applyAvatarCue,
    startMusicLipSync,
    stopMusicLipSync,
    /** 重试当前音频的口型分析（用户在"口型未启用"提示上点击后由 MainLayout 调用） */
    retryMouthSync() {
        return lipSync.retry()
    },
    lipSyncConfig: lipSync.config,
    setLipSyncConfig: lipSync.updateConfig,
    refresh() {
        if (resizeHandler) resizeHandler()
        if (app?.renderer) app.renderer.render(app.stage)
    }
})

// DOM Pointer Events 不依赖模型缺失的 HitAreas；点击和拖拽只走这一条路径。
function onPointerDown(event: PointerEvent) {
    if (!props.interactive || !model || loadState.value !== 'ready' || pointerId !== null || event.button !== 0) return
    pointerId = event.pointerId
    dragged = false
    ignoreClick = false
    dragStart = { x: event.clientX, y: event.clientY, modelX: model.x, modelY: model.y }
    canvasRef.value?.setPointerCapture?.(event.pointerId)
    focusModelAtClientPoint(event.clientX, event.clientY)
}
function onPointerMove(event: PointerEvent) {
    if (pointerId !== event.pointerId || !model || !canvasRef.value) return
    const dx = event.clientX - dragStart.x
    const dy = event.clientY - dragStart.y
    if (Math.hypot(dx, dy) > 6) dragged = true
    if (dragged) {
        const rect = canvasRef.value.getBoundingClientRect()
        if (rect.width && rect.height) {
            model.x = dragStart.modelX + dx * app.screen.width / rect.width
            model.y = dragStart.modelY + dy * app.screen.height / rect.height
        }
    }
}
function onPointerUp(event: PointerEvent) {
    if (pointerId !== event.pointerId) return
    ignoreClick = dragged
    pointerId = null
    canvasRef.value?.releasePointerCapture?.(event.pointerId)
}
function cancelPointer() { if (pointerId !== null) ignoreClick = true; pointerId = null }
function handleCanvasClick() {
    if (ignoreClick) { ignoreClick = false; return }
    if (!props.interactive || !avatar || loadState.value !== 'ready') return
    emit('click')
    avatar.tap()
}

const updateReducedMotion = () => avatar?.setReduced(reducedMotion?.matches === true)
const updateVisibility = () => {
    const visible = props.visible && !document.hidden
    avatar?.setEnabled(visible)
    if (visible) {
        avatar?.setState(props.presentationState)
        app?.ticker?.start()
        refreshRenderer()
        emit('resume')
    } else {
        cancelPointer()
        lipSync.stop()
        app?.ticker?.stop()
    }
}

// 动态加载 Live2D 脚本（按顺序，确保全局变量可用）
const loadLive2DScripts = (): Promise<void> => {
    const Live2DModelClass = window.PIXI?.live2d?.Live2DModel || window.Live2DModel
    if (window.PIXI && Live2DModelClass) return Promise.resolve()
    if (window.__LIUTECH_LIVE2D_SCRIPTS__) return window.__LIUTECH_LIVE2D_SCRIPTS__

    const scripts = [
        '/live2d/pixi.min.js',
        '/live2d/live2dcubismcore.min.js',
        '/live2d/live2d.min.js',
        '/live2d/cubism4.min.js'
    ]

    // 保持执行依赖顺序，下载可并行；组件按需挂载后才预取，不在首屏主动加载模型脚本。
    for (const src of scripts) {
        if (document.querySelector(`link[data-live2d-preload="${src}"]`)) continue
        const preload = document.createElement('link')
        preload.rel = 'preload'
        preload.as = 'script'
        preload.href = src
        preload.dataset.live2dPreload = src
        document.head.appendChild(preload)
    }

    const loadScript = (src: string): Promise<void> =>
        new Promise((resolve, reject) => {
            const selector = `script[data-liutech-live2d-script="${src}"]`
            const existing = document.querySelector<HTMLScriptElement>(selector)
            if (existing) {
                if (existing.dataset.loaded === 'true') {
                    resolve()
                    return
                }
                existing.addEventListener('load', () => resolve(), { once: true })
                existing.addEventListener('error', () => reject(new Error(`Failed to load ${src}`)), { once: true })
                return
            }

            const el = document.createElement('script')
            el.src = src
            el.dataset.liutechLive2dScript = src
            el.onload = () => {
                el.dataset.loaded = 'true'
                resolve()
            }
            el.onerror = () => {
                el.remove()
                reject(new Error(`Failed to load ${src}`))
            }
            document.head.appendChild(el)
        })

    window.__LIUTECH_LIVE2D_SCRIPTS__ = scripts.reduce(
        (chain, src) => chain.then(() => loadScript(src)),
        Promise.resolve()
    )

    window.__LIUTECH_LIVE2D_SCRIPTS__.catch(() => {
        delete window.__LIUTECH_LIVE2D_SCRIPTS__
    })

    return window.__LIUTECH_LIVE2D_SCRIPTS__
}

const startLive2DLoad = () => {
    if (isInitialized) return

    loadState.value = 'loading'
    performance.mark('liutech-live2d-load-start')
    emit('load-start')

    loadLive2DScripts().then(() => {
        if (isComponentMounted) initLive2D()
    }).catch(() => {
        if (!isComponentMounted) return
        loadState.value = 'error'
        emit('error')
    })
}

onMounted(() => {
    isComponentMounted = true
    reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)') ?? null
    reducedMotion?.addEventListener('change', updateReducedMotion)
    document.addEventListener('visibilitychange', updateVisibility)
    window.addEventListener('blur', clearPointer)
    document.documentElement.addEventListener('pointerleave', clearPointer)
    startLive2DLoad()
});

// 刷新渲染器尺寸与模型位置（处理窗口/容器/分辨率变化）
const refreshRenderer = () => {
    const canvas = canvasRef.value
    if (!canvas) return
    const container = canvas.parentElement
    const rawWidth = container?.clientWidth ?? 0
    const rawHeight = container?.clientHeight ?? 0
    const currentDpr = window.devicePixelRatio || 1
    const currentWidth = rawWidth || 400
    const currentHeight = rawHeight || 400

    // DPR 和尺寸都没变，跳过无意义的刷新
    if (currentDpr === lastDpr && currentWidth === lastWidth && currentHeight === lastHeight) {
        resizeTriggerCount = 0
        return
    }
    lastDpr = currentDpr
    lastWidth = currentWidth
    lastHeight = currentHeight

    console.log(`[Live2D] refresh ${currentWidth}x${currentHeight} @${currentDpr} (合并 ${resizeTriggerCount} 次触发)`)
    resizeTriggerCount = 0

    if (app && app.renderer) {
        // 同步设备像素比（拖到高 DPI 显示器、浏览器缩放时避免模糊）
        try { app.renderer.resolution = currentDpr } catch (e) { console.warn('[Live2D] setResolution failed', e) }
        try { app.renderer.resize(currentWidth, currentHeight) } catch (e) { console.warn('[Live2D] resize failed', e) }
    }
    if (model) {
        model.x = currentWidth / 2
        model.y = currentHeight / 2
    }
}

// 防抖：避免展开/折叠 transition 期间频繁刷新
const debouncedRefresh = () => {
    resizeTriggerCount++
    if (resizeTimer) clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => {
        resizeTimer = null
        refreshRenderer()
    }, 150)
}

// 等待全局脚本加载完成
const initLive2D = () => {
    if (!window.PIXI) {
        loadState.value = 'error'
        emit('error')
        return;
    }

    // 检查Live2D是否可用
    const Live2DModelClass = (window as any).PIXI?.live2d?.Live2DModel || (window as any).Live2DModel;
    if (!Live2DModelClass) {
        loadState.value = 'error'
        emit('error')
        return;
    }

    // 标记为已初始化
    isInitialized = true;

    // 创建 PIXI 应用
    const canvas = canvasRef.value;
    if (!canvas) {
        isInitialized = false
        return
    }
    const container = canvas.parentElement;
    const containerWidth = container?.clientWidth || 400;
    const containerHeight = container?.clientHeight || 400;

    app = new window.PIXI.Application({
        view: canvas,
        width: containerWidth,
        height: containerHeight,
        backgroundColor: 0x000000,
        backgroundAlpha: 0, // 设置背景透明
        antialias: true,
        resolution: window.devicePixelRatio || 1
    });

    // 模型不可见时停止 ticker，避免 PIXI 空转耗 CPU/GPU
    if (!props.visible || document.hidden) {
        app.ticker.stop()
    }

    // 加载Live2D模型
    const generation = ++modelGeneration
    const creatingApp = app
    Live2DModelClass.from(cubism4Model, { autoInteract: false }).then((live2dModel: any) => {
        if (!isComponentMounted || app !== creatingApp || generation !== modelGeneration) {
            live2dModel.destroy?.()
            return
        }
        model = live2dModel;
        app.stage.addChild(live2dModel);
        // 设置模型锚点为中心
        live2dModel.anchor.set(0.5, 0.28);
        // 模型居中显示 - 动态获取容器尺寸
        live2dModel.x = containerWidth / 2;
        live2dModel.y = containerHeight / 2;
        // 保持原有模型大小与锚点，角色优化不改变布局。
        live2dModel.scale.set(0.15);

        applyInteractionMode()
        const controller = createAvatarController(live2dModel.internalModel)
        avatar = controller
        controller.setReduced(reducedMotion?.matches === true)
        controller.setEnabled(props.visible && !document.hidden)
        controller.setState(props.presentationState)
        // 资源加载不阻塞 ready/声音；回调只补充当前控制器的能力。
        void loadAvatarExpressions(live2dModel.internalModel.settings.expressions ?? [], '/live2d/model/Nahida/').then(resources => {
            controller.setExpressions(resources)
        })
        loadState.value = 'ready'
        performance.measure('liutech-live2d-load', 'liutech-live2d-load-start')
        emit('ready')
    }).catch(() => {
        if (!isComponentMounted || generation !== modelGeneration) return
        app?.ticker?.stop()
        loadState.value = 'error'
        emit('error')
    });

    // 窗口大小/分辨率变化：防抖刷新
    resizeHandler = debouncedRefresh
    window.addEventListener('resize', resizeHandler)

    // 容器尺寸变化（展开/折叠等）：ResizeObserver 监听，弥补 window resize 无法覆盖的场景
    const canvasEl = canvasRef.value
    const containerEl = canvasEl?.parentElement
    if (containerEl && typeof ResizeObserver !== 'undefined') {
        resizeObserver = new ResizeObserver(debouncedRefresh)
        resizeObserver.observe(containerEl)
    }

    debouncedRefresh()

    applyInteractionMode()
    updatePointerTracking()
};

const retryLive2D = () => {
    modelGeneration++
    lipSync.stop()
    avatar?.destroy()
    avatar = null
    cancelPointer()
    if (resizeHandler) {
        window.removeEventListener('resize', resizeHandler)
        resizeHandler = null
    }
    resizeObserver?.disconnect()
    resizeObserver = null
    if (resizeTimer) {
        clearTimeout(resizeTimer)
        resizeTimer = null
    }
    if (model) {
        try { model.destroy?.() } catch {}
        model = null
    }
    if (app) {
        try { app.destroy(false, { children: true, texture: true, baseTexture: true }) } catch {}
        app = null
    }
    isInitialized = false
    startLive2DLoad()
}

// 资源清理函数
const cleanup = () => {
    // 重置初始化状态
    isInitialized = false;

    // 1. 停止口型同步（音乐播放器现在挂在 BottomNavigation 上，由其自身生命周期负责停止）
    lipSync.stop()

    modelGeneration++
    avatar?.destroy()
    avatar = null
    cancelPointer()

    // 4. 移除窗口事件监听器
    if (resizeHandler) {
        window.removeEventListener('resize', resizeHandler);
        resizeHandler = null;
    }
    if (resizeObserver) {
        resizeObserver.disconnect()
        resizeObserver = null
    }
    if (resizeTimer) {
        clearTimeout(resizeTimer)
        resizeTimer = null
    }
    if (windowMouseMoveHandler) {
        window.removeEventListener('pointermove', windowMouseMoveHandler)
        windowMouseMoveHandler = null
    }
    // 5. 清理模型资源
    if (model) {
        try {
            // 移除所有事件监听器
            model.removeAllListeners();

            // 停止所有动作
            if (model.internalModel?.motionManager) {
                model.internalModel.motionManager.stopAllMotions();
            }

            // 从舞台移除模型
            if (app && app.stage && model.parent) {
                app.stage.removeChild(model);
            }

            // 销毁模型
            if (model.destroy) {
                model.destroy();
            }
        } catch {
            // 清理模型时静默处理
        }
        model = null;
    }

    // 6. 清理PIXI应用
    if (app) {
        try {
            // 移除舞台事件监听器
            if (app.stage) {
                app.stage.removeAllListeners();
                app.stage.interactive = false;
            }

            // 销毁应用
            app.destroy(true, {
                children: true,
                texture: true,
                baseTexture: true
            });
        } catch {
            // 清理PIXI应用时静默处理
        }
        app = null;
    }

};

// 组件卸载时清理资源
onBeforeUnmount(() => {
    isComponentMounted = false
    document.removeEventListener('visibilitychange', updateVisibility)
    window.removeEventListener('blur', clearPointer)
    document.documentElement.removeEventListener('pointerleave', clearPointer)
    reducedMotion?.removeEventListener('change', updateReducedMotion)
    cleanup();
    lipSync.destroy()
});

watch(() => [props.interactive, props.followPointer], () => {
    applyInteractionMode()
    updatePointerTracking()
})

watch(() => props.presentationState, value => avatar?.setState(value))
watch(() => props.visible, updateVisibility)

</script>

<style lang="scss" scoped>
/**
 * Live2D 模型展示组件样式
 * 作者: 刘鑫
 * 功能: 纯净的Live2D模型展示样式
 */
.container {
    width: 100%;
    height: 100%;
    display: block;
    position: relative;
}

.live2d-canvas {
    touch-action: none;
    width: 100%;
    height: 100%;
    opacity: 1;
    transition: opacity 0.3s ease;
}

.container:not(.is-ready) .live2d-canvas {
    opacity: 0;
    pointer-events: none;
}

.live2d-placeholder {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 24px;
    text-align: center;
    color: var(--text-main);
    pointer-events: auto;
}

.live2d-placeholder__panel {
    width: min(264px, 100%);
    padding: 18px 18px 20px;
    border: 1px solid var(--border-soft);
    border-radius: 24px;
    background: var(--bg-card);
    box-shadow: 0 18px 48px rgba(15, 23, 42, 0.1);
    transform: translate(
        var(--live2d-placeholder-offset-x, 0),
        var(--live2d-placeholder-offset-y, 0)
    );
}

.live2d-placeholder__avatar-wrap {
    width: 156px;
    aspect-ratio: 1;
    margin: 0 auto 8px;
    display: grid;
    place-items: center;
}

.live2d-placeholder__avatar {
    width: 92%;
    height: 92%;
    object-fit: contain;
    filter: drop-shadow(0 14px 24px rgba(32, 88, 58, 0.14));
}

.live2d-placeholder__title,
.live2d-placeholder__hint {
    margin: 0;
    text-shadow: 0 1px 8px var(--bg-card);
}

.live2d-placeholder__title {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 650;
}

.live2d-placeholder__hint {
    margin-top: 6px;
    color: var(--text-subtle);
    font-size: 12px;
}

.live2d-placeholder__spinner {
    width: 14px;
    height: 14px;
    flex: 0 0 auto;
    border: 2px solid rgba(var(--color-primary-rgb), 0.18);
    border-top-color: var(--color-primary);
    border-radius: 50%;
    animation: live2d-placeholder-spin 0.9s linear infinite;
}

.live2d-placeholder__retry {
    margin-top: 14px;
    padding: 7px 16px;
    border: 1px solid var(--border-base);
    border-radius: 999px;
    background: var(--bg-card);
    color: var(--color-primary);
    cursor: pointer;
    transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.live2d-placeholder__retry:hover {
    border-color: rgba(var(--color-primary-rgb), 0.44);
    background: var(--bg-hover);
    transform: translateY(-1px);
}

.live2d-placeholder--error .live2d-placeholder__avatar {
    opacity: 0.7;
    filter: grayscale(0.22) drop-shadow(0 12px 20px rgba(0, 0, 0, 0.1));
    animation: none;
}

@keyframes live2d-placeholder-spin {
    to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
    .live2d-placeholder__spinner {
        animation: none;
        border-top-color: rgba(var(--color-primary-rgb), 0.18);
        border-right-color: var(--color-primary);
    }
}
</style>
