<template>
    <div class="container" :class="[{ passive: !props.interactive }, `is-${loadState}`]">
        <canvas
            ref="canvasRef"
            class="live2d-canvas"
            :aria-hidden="loadState !== 'ready'"
            @click="triggerRandomExpression"
        ></canvas>

        <div
            v-if="loadState !== 'ready'"
            class="live2d-placeholder"
            :class="{ 'live2d-placeholder--error': loadState === 'error' }"
            role="status"
            aria-live="polite"
        >
            <div class="live2d-placeholder__avatar-wrap">
                <img :src="avatarUrl" alt="" class="live2d-placeholder__avatar" />
                <span v-if="loadState === 'loading'" class="live2d-placeholder__pulse" />
            </div>
            <p class="live2d-placeholder__title">
                {{ loadState === 'error' ? '纳西妲暂时没有加载成功' : '纳西妲正在赶来…' }}
            </p>
            <p class="live2d-placeholder__hint">
                {{ loadState === 'error' ? '请检查网络后重新加载' : '首次加载模型需要一点时间' }}
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
</template>


<script setup lang="ts">
/**
 * Live2D 模型展示组件
 * 作者: 刘鑫
 * 修改时间: 2025-09-24 19:33:22 +08:00
 * 功能: 纯净的Live2D模型展示，支持基本交互和拖拽，优化资源管理
 */
import { onMounted, onBeforeUnmount, ref, watch } from 'vue';
import avatarUrl from '@/assets/aifile/纳西妲.webp'

import { useAudioLipSync } from '@/composables/useAudioLipSync'

const emit = defineEmits<{
    click: []
    'speak-start': []
    'load-start': []
    ready: []
    error: []
}>()

const props = withDefaults(defineProps<{
    interactive?: boolean
    followPointer?: boolean
    visible?: boolean
}>(), {
    interactive: true,
    followPointer: true,
    visible: true
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

// 可用的表情列表
const expressions = [
    "生气",
    "遮脸",
    "半眼",
    "手势变化",
    "开心1",
    "草草",
    "嘴型变化",
    "伤心1",
    "正常害羞",
    "害羞",
    "星星眼",
    "眨眼",
    "思考",
    "生气动画",
    "星星眼动画",
    "嘟嘴",
    "挑眉",
    "嘟嘴思考",
    "生气完整",
    "伤心完整",
    "害羞嘟嘴",
    "惊讶完整",
    "开心完整",
    "好奇",
    "慌张",
    "坏笑"
];

interface AvatarCue {
    expression?: string
    motion?: string | null
    intensity?: number
    durationMs?: number
    text?: string
    skipResetTimer?: boolean
}

const EXPRESSION_MIN_INTERVAL_MS = 1200
const MOTION_MIN_INTERVAL_MS = 3000

const expressionMap: Record<string, string | null> = {
    happy: '开心1',
    sad: '伤心1',
    angry: '生气',
    thinking: '思考',
    surprised: '星星眼',
    shy: '害羞',
    confused: '遮脸',
    neutral: null,
    calm: '草草'
}

// 模型实例和PIXI应用实例
let model: any = null;
let app: any = null;
let isInitialized = false; // 初始化状态标记
type Live2dLoadState = 'loading' | 'ready' | 'error'
const loadState = ref<Live2dLoadState>('loading')
const canvasRef = ref<HTMLCanvasElement | null>(null)
let isComponentMounted = false
let lastExpressionAt = 0
let lastMotionAt = 0
let pendingAvatarCue: AvatarCue | null = null
let pendingAvatarCueTimer: ReturnType<typeof setTimeout> | null = null
let expressionResetTimer: ReturnType<typeof setTimeout> | null = null

// 拖拽相关变量
let isDragging = false;
let dragOffset = { x: 0, y: 0 };

// 窗口大小调整处理器
let resizeHandler: (() => void) | null = null;
let resizeObserver: ResizeObserver | null = null
let resizeTimer: ReturnType<typeof setTimeout> | null = null
let resizeTriggerCount = 0
let lastDpr = 0
let lastWidth = 0
let lastHeight = 0
let windowMouseMoveHandler: ((event: MouseEvent) => void) | null = null

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
    if (!model || !app?.renderer?.view) return
    const canvas = app.renderer.view as HTMLCanvasElement
    const rect = canvas.getBoundingClientRect()
    if (!rect.width || !rect.height) return

    const scaleX = app.renderer.width / rect.width
    const scaleY = app.renderer.height / rect.height
    const x = (clientX - rect.left) * scaleX
    const y = (clientY - rect.top) * scaleY

    model.focus(x, y)
}

const updatePointerTracking = () => {
    if (windowMouseMoveHandler) {
        window.removeEventListener('mousemove', windowMouseMoveHandler)
        windowMouseMoveHandler = null
    }

    if (props.followPointer !== true || props.interactive === true) {
        return
    }

    windowMouseMoveHandler = (event: MouseEvent) => {
        focusModelAtClientPoint(event.clientX, event.clientY)
    }

    window.addEventListener('mousemove', windowMouseMoveHandler, { passive: true })
}

const setMouthOpen = (value: number) => {
    try {
        const v = Math.max(0, Math.min(1, value))
        model?.internalModel?.coreModel?.setParameterValueById?.("ParamMouthOpenY", v)
    } catch {
    }
}

const lipSync = useAudioLipSync(setMouthOpen, {
    noiseFloor: 0.015,
    gain: 14,
    smoothIn: 0.78,
    smoothOut: 0.88,
    curve: 0.75
})

// 音乐播放事件处理：由 MainLayout 在收到 BottomNavigation 的音乐事件后调用
function startMusicLipSync(audio: HTMLAudioElement) {
    if (!audio) return
    if (!model) return
    void lipSync.start(audio)
}

function stopMusicLipSync() {
    lipSync.stop()
}

/**
 * 统一“让 Live2D 读音频”的入口（替代原本的 speak 思路）
 * - 传入：音频URL（例如未来 TTS 返回的 url）
 * - 行为：播放该音频，并用同一条音频驱动口型
 *
 * 注意：浏览器可能要求用户先有一次交互才能播放音频
 */
const speakAudioUrl = async (url: string) => {
    if (!model || loadState.value !== 'ready') return null
    emit('speak-start')
    return lipSync.speak({ url, play: true, volume: 1, crossOrigin: 'anonymous' })
}

/**
 * 使用“外部已创建/已预加载”的 AudioElement 播放并驱动口型
 *
 * 用途：
 * - Web 侧收到 audioUrl 后可以先行预加载（audio.load），播放时复用同一个 element
 * - 避免重复创建 Audio 导致再次等待网络/磁盘
 */
const speakAudioElement = async (audio: HTMLAudioElement) => {
    if (!model || loadState.value !== 'ready') return null
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

const resetExpression = () => {
    try {
        model?.expressionManager?.setExpression?.(null)
    } catch {
    }
}

const resolveExpressionName = (cue: AvatarCue): string | null => {
    const raw = (cue.expression || 'neutral').trim()
    if (!raw) return null
    if (expressions.includes(raw)) return raw
    return expressionMap[raw] ?? null
}

const flushAvatarCue = () => {
    if (pendingAvatarCueTimer) {
        clearTimeout(pendingAvatarCueTimer)
        pendingAvatarCueTimer = null
    }
    const cue = pendingAvatarCue
    pendingAvatarCue = null
    if (!cue || !model) return

    const expression = resolveExpressionName(cue)
    try {
        if (expression) {
            model.expression(expression)
        } else {
            resetExpression()
        }
        lastExpressionAt = Date.now()
    } catch {
        // 触发表情失败时静默处理
    }

    const motion = cue.motion?.trim()
    if (motion && motion !== 'none') {
        const now = Date.now()
        if (now - lastMotionAt >= MOTION_MIN_INTERVAL_MS) {
            try {
                const motions = model?.internalModel?.settings?.motions
                if (!motions || motions[motion]) {
                    model.motion(motion)
                    lastMotionAt = now
                }
            } catch {
            }
        }
    }

    if (expressionResetTimer) {
        clearTimeout(expressionResetTimer)
        expressionResetTimer = null
    }
    if (!cue.skipResetTimer) {
        const duration = Math.max(1600, Math.min(6000, cue.durationMs || 2600))
        expressionResetTimer = setTimeout(() => {
            resetExpression()
            expressionResetTimer = null
        }, duration)
    }
}

const applyAvatarCue = (cue: AvatarCue) => {
    if (!cue) return
    pendingAvatarCue = cue
    const waitMs = Math.max(0, EXPRESSION_MIN_INTERVAL_MS - (Date.now() - lastExpressionAt))
    if (waitMs <= 0) {
        flushAvatarCue()
        return
    }
    if (pendingAvatarCueTimer) {
        clearTimeout(pendingAvatarCueTimer)
    }
    pendingAvatarCueTimer = setTimeout(flushAvatarCue, waitMs)
}

defineExpose({
    speakAudioUrl,
    speakAudioElement,
    applyAvatarCue,
    startMusicLipSync,
    stopMusicLipSync,
    lipSyncConfig: lipSync.config,
    setLipSyncConfig: lipSync.updateConfig,
    refresh() {
        if (resizeHandler) resizeHandler()
        if (app?.renderer) app.renderer.render(app.stage)
    }
})

// 拖拽事件处理
function onPointerDown(event: any) {
    isDragging = true;
    const position = event.data.getLocalPosition(event.currentTarget.parent);
    dragOffset.x = position.x - event.currentTarget.x;
    dragOffset.y = position.y - event.currentTarget.y;
}
// 鼠标移动事件处理
function onPointerMove(event: any) {
    if (isDragging) {
        const position = event.data.getLocalPosition(event.currentTarget.parent);
        event.currentTarget.x = position.x - dragOffset.x;
        event.currentTarget.y = position.y - dragOffset.y;
    }
}
// 鼠标松开事件处理
function onPointerUp() {
    isDragging = false;
}
// 触发随机表情（点击看板娘时调用）
function triggerRandomExpression() {
    emit('click')

    if (!model) {
        return;
    }

    // 随机选择一个表情
    const randomIndex = Math.floor(Math.random() * expressions.length);
    const randomExpression = expressions[randomIndex];

    try {
        model.expression(randomExpression);
    } catch {
        // 触发表情失败时静默处理
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
    if (!props.visible) {
        app.ticker.stop()
    }

    // 加载Live2D模型
    Live2DModelClass.from(cubism4Model).then((live2dModel: any) => {
        if (!isComponentMounted || !app) {
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
        // 固定模型大小，不受页面缩放影响
        const fixedScale = 0.15;
        live2dModel.scale.set(fixedScale);

        // 启用交互
        live2dModel.interactive = props.interactive === true;
        // 监听鼠标按下事件，开始拖拽
        live2dModel.on('pointerdown', onPointerDown);
        // 监听鼠标移动事件，实现拖拽移动
        live2dModel.on('pointermove', onPointerMove);
        // 监听鼠标松开事件，结束拖拽
        live2dModel.on('pointerup', onPointerUp);
        // 监听鼠标移出模型区域事件，同样结束拖拽
        live2dModel.on('pointerupoutside', onPointerUp);

        // 鼠标跟随
        app.stage.interactive = props.interactive === true;
        app.stage.on('pointermove', (event: any) => {
            const point = event.data.global;
            live2dModel.focus(point.x, point.y);
        });

        // 点击触发动作
        live2dModel.on('hit', (hitAreas: any) => {
            if (hitAreas.includes('body')) {
                live2dModel.motion('tap_body');
                // 同时触发随机表情
                triggerRandomExpression();
            }
        });

        // 自动眨眼和呼吸
        live2dModel.internalModel.motionManager.startRandomMotion('idle');
        loadState.value = 'ready'
        emit('ready')
    }).catch(() => {
        if (!isComponentMounted) return
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

    // 2. 重置 Live2D 状态（嘴型、表情）
    try {
        if (model?.internalModel?.coreModel) {
            model.internalModel.coreModel.setParameterValueById("ParamMouthOpenY", 0);
        }
        if (model?.expressionManager) {
            model.expressionManager.setExpression(null);
        }
    } catch { }

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
        window.removeEventListener('mousemove', windowMouseMoveHandler)
        windowMouseMoveHandler = null
    }
    if (pendingAvatarCueTimer) {
        clearTimeout(pendingAvatarCueTimer)
        pendingAvatarCueTimer = null
    }
    if (expressionResetTimer) {
        clearTimeout(expressionResetTimer)
        expressionResetTimer = null
    }
    pendingAvatarCue = null

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
    cleanup();
    lipSync.destroy()
});

watch(() => [props.interactive, props.followPointer], () => {
    applyInteractionMode()
    updatePointerTracking()
})

// 模型可见性变化：不可见时停 ticker 省资源，可见时恢复并刷新尺寸
watch(() => props.visible, (visible) => {
    if (!app?.ticker) return
    if (visible) {
        app.ticker.start()
        refreshRenderer()
    } else {
        app.ticker.stop()
    }
})


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
    padding: 28px;
    text-align: center;
    color: var(--text-main);
    pointer-events: auto;
}

.live2d-placeholder__avatar-wrap {
    position: relative;
    width: 112px;
    height: 112px;
    margin-bottom: 18px;
}

.live2d-placeholder__avatar {
    width: 100%;
    height: 100%;
    object-fit: contain;
    filter: drop-shadow(0 12px 20px rgba(0, 0, 0, 0.14));
    animation: live2d-placeholder-float 2.2s ease-in-out infinite;
}

.live2d-placeholder__pulse {
    position: absolute;
    inset: -8px;
    border: 2px solid rgba(var(--color-primary-rgb), 0.34);
    border-radius: 50%;
    animation: live2d-placeholder-pulse 1.5s ease-out infinite;
}

.live2d-placeholder__title,
.live2d-placeholder__hint {
    margin: 0;
    text-shadow: 0 1px 8px var(--bg-card);
}

.live2d-placeholder__title {
    font-size: 15px;
    font-weight: 650;
}

.live2d-placeholder__hint {
    margin-top: 6px;
    color: var(--text-subtle);
    font-size: 12px;
}

.live2d-placeholder__retry {
    margin-top: 14px;
    padding: 7px 16px;
    border: 1px solid var(--border-base);
    border-radius: 999px;
    background: var(--bg-card);
    color: var(--color-primary);
    cursor: pointer;
}

@keyframes live2d-placeholder-float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-7px); }
}

@keyframes live2d-placeholder-pulse {
    from { opacity: 0.8; transform: scale(0.86); }
    to { opacity: 0; transform: scale(1.12); }
}

@media (prefers-reduced-motion: reduce) {
    .live2d-placeholder__avatar,
    .live2d-placeholder__pulse {
        animation: none;
    }
}
</style>
