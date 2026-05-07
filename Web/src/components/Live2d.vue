<template>
    <div class="container" :class="{ passive: !props.interactive }">
        <!-- 音乐播放胶囊 -->
        <MusicCapsule ref="musicCapsuleRef" @play="onMusicPlay" @pause="onMusicPause" />
        <!-- 音乐播放胶囊 -->
        <canvas @click="playTestAudio" id="canvas"></canvas>

    </div>
</template>


<script setup lang="ts">
/**
 * Live2D 模型展示组件
 * 作者: 刘鑫
 * 修改时间: 2025-09-24 19:33:22 +08:00
 * 功能: 纯净的Live2D模型展示，支持基本交互和拖拽，优化资源管理
 */
import { onMounted, onBeforeUnmount, watch, ref } from 'vue';
import MusicCapsule from './MusicCapsule.vue';
import { useLipSync } from '@/composables/useLipSync'

const props = withDefaults(defineProps<{
    interactive?: boolean
    followPointer?: boolean
}>(), {
    interactive: true,
    followPointer: true
})

// 声明全局变量类型
declare global {
    interface Window {
        PIXI: any;
        LIVE2DCUBISMCORE: any;
        Live2DModel: any;
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
let lastExpressionAt = 0
let lastMotionAt = 0
let pendingAvatarCue: AvatarCue | null = null
let pendingAvatarCueTimer: ReturnType<typeof setTimeout> | null = null
let expressionResetTimer: ReturnType<typeof setTimeout> | null = null

// 拖拽相关变量
let isDragging = false;
let dragOffset = { x: 0, y: 0 };

// 音频播放状态
const isAuto = ref<boolean>(true);//音频是否空闲
let audioQueue = ref<string[]>([]); // 音频队列
const audioChecker = ref<HTMLAudioElement | null>(null); // 音频检测对象

// 窗口大小调整处理器
let resizeHandler: (() => void) | null = null;
let windowMouseMoveHandler: ((event: MouseEvent) => void) | null = null

// 音乐胶囊引用
const musicCapsuleRef = ref<InstanceType<typeof MusicCapsule> | null>(null);
let shouldResumeMusicAfterSpeech = false

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

const lipSync = useLipSync(setMouthOpen, {
    noiseFloor: 0.015,
    gain: 14,
    smoothIn: 0.78,
    smoothOut: 0.88,
    curve: 0.75
})

// 音乐播放事件处理
function onMusicPlay(audio: HTMLAudioElement) {
    if (!audio) return
    if (!model) return
    shouldResumeMusicAfterSpeech = false
    lipSync.start(audio)
}

// 音乐暂停事件处理
function onMusicPause() {
    lipSync.stop()
}

const suspendMusicForSpeech = () => {
    const capsule = musicCapsuleRef.value
    if (!capsule?.isPlaying?.()) return
    shouldResumeMusicAfterSpeech = true
    capsule.pauseMusic()
}

const resumeMusicAfterSpeechIfNeeded = async () => {
    if (!shouldResumeMusicAfterSpeech) return
    shouldResumeMusicAfterSpeech = false
    try {
        await musicCapsuleRef.value?.resumeMusic?.()
    } catch {
    }
}

/**
 * 统一“让 Live2D 读音频”的入口（替代原本的 speak 思路）
 * - 传入：音频URL（例如未来 TTS 返回的 url）
 * - 行为：播放该音频，并用同一条音频驱动口型
 *
 * 注意：浏览器可能要求用户先有一次交互才能播放音频
 */
const speakAudioUrl = async (url: string) => {
    if (!model) return null
    suspendMusicForSpeech()
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
    if (!model) return null
    if (!audio) return null
    suspendMusicForSpeech()
    try {
        audio.preload = 'auto'
        audio.crossOrigin = 'anonymous'
    } catch {
    }
    try {
        await lipSync.start(audio)
    } catch {
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
    } catch (error) {
        console.error("触发表情失败:", error)
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
    resumeMusicAfterSpeechIfNeeded,
    lipSyncConfig: lipSync.config,
    setLipSyncConfig: lipSync.updateConfig
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
//
//播放音频，同步模型嘴部。
function talk(audioPath: string) {
    return new Promise((resolve, reject) => {
        isAuto.value = false; // 播放开始
        audioChecker.value = new Audio(audioPath); // 用于判断播放完成

        audioChecker.value.volume = 0; // 静音，用于检测播放状态
        audioChecker.value.crossOrigin = "anonymous";

        // 播放结束
        audioChecker.value.onended = () => {
            isAuto.value = true;
            resolve(void 0);
        };

        // 播放失败
        audioChecker.value.onerror = (err) => {
            console.error("检测音频播放出错：", err);
            isAuto.value = true; // 即使失败也恢复状态
            reject(err);
        };

        // 播放检测对象
        audioChecker.value.play().catch((err) => {
            console.error("检测音频无法播放：", err);
            isAuto.value = true;
            reject(err);
        });

        // 随机选择一个表情
        const randomIndex = Math.floor(Math.random() * expressions.length);
        const randomExpression = expressions[randomIndex];

        // 实际播放音频
        model.speak(audioPath, {
            volume: 1,
            expression: randomExpression, // 随机表情
            resetExpression: true,
            crossOrigin: "anonymous",
        });
    });
}

// 监听队列播放音频
watch(
    audioQueue,
    async () => {
        // 队列非空且当前没有在播放时触发播放
        if (audioQueue.value.length > 0 && isAuto.value) {
            // const audioPath = getServiceBaseURL(ServiceType.AI) + `${audioQueue.value[0]}`;
            const audioPath = audioQueue.value[0];
            try {
                isAuto.value = false;
                await talk(audioPath); // 等待播放完成
                audioQueue.value.shift(); // 播放完成后移除队列
                isAuto.value = true;
            } catch (error) {
                console.error("音频播放失败：", error);
                audioQueue.value.shift(); // 出现错误也移除队列
                isAuto.value = true;
            }
        }
    },
    { deep: true }
);

// 触发随机表情
function triggerRandomExpression() {
    if (!model) {
        return;
    }

    // 随机选择一个表情
    const randomIndex = Math.floor(Math.random() * expressions.length);
    const randomExpression = expressions[randomIndex];

    try {
        model.expression(randomExpression);
        console.log(`[Live2D] 表情: ${randomExpression} (${randomIndex + 1}/${expressions.length})`)
    } catch (error) {
        console.error("[Live2D] 触发表情失败:", error);
    }
}

// 播放测试音频
function playTestAudio() {
    // 触发随机表情
    triggerRandomExpression();
    return
    // 判断是否已经有音频在播放了
    if (!isAuto.value) {
        stopSpeak()
        return;
    }
    audioQueue.value.push("http://localhost:8080/uploads/music/2026/01/01/20260101134251_ce98531e432d493b854b07eb1bfb15fb.mp3");
}
function stopSpeak() {
    try {
        // 1. 停止检测用的 audioChecker
        if (audioChecker.value) {
            audioChecker.value.pause();
            audioChecker.value.currentTime = 0;
            audioChecker.value = null;
        }

        // 2. 尝试停止 Live2D 动作（如果支持）
        if (model?.internalModel?.motionManager) {
            model.internalModel.motionManager.stopAllMotions();
        }

        // 3. 强制关闭嘴型参数
        if (model?.internalModel?.coreModel) {
            model.internalModel.coreModel.setParameterValueById("ParamMouthOpenY", 0);
        }

        // 4. 恢复默认表情（防止 expression 卡住）
        if (model?.expressionManager) {
            model.expressionManager.setExpression(null);
        }

        // 5. 状态恢复
        isAuto.value = true;
    } catch (err) {
        console.error("stopSpeak 出错:", err);
    }
}


onMounted(() => {
    // 防止重复初始化
    if (isInitialized) {
        return;
    }

    // 等待全局脚本加载完成
    const initLive2D = () => {
        if (!window.PIXI) {
            setTimeout(initLive2D, 100);
            return;
        }

        // 检查Live2D是否可用
        const Live2DModelClass = (window as any).PIXI?.live2d?.Live2DModel || (window as any).Live2DModel;
        if (!Live2DModelClass) {
            console.error('Live2D模型类未找到，请检查脚本加载');
            return;
        }

        // 标记为已初始化
        isInitialized = true;

        // 创建 PIXI 应用
        const canvas = document.getElementById('canvas') as HTMLCanvasElement;
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

        // 加载Live2D模型
        Live2DModelClass.from(cubism4Model).then((live2dModel: any) => {
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
            // 启用模型的交互功能
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
        }).catch((error: any) => {
            console.error('Live2D模型加载失败:', error);
        });

        // 窗口大小调整
        resizeHandler = () => {
            // 动态获取容器尺寸
            const canvas = document.getElementById('canvas') as HTMLCanvasElement;
            const container = canvas.parentElement;
            const currentWidth = container?.clientWidth || 400;
            const currentHeight = container?.clientHeight || 400;

            if (app && app.renderer) {
                app.renderer.resize(currentWidth, currentHeight);
            }
            if (model) {
                // 模型位置居中显示 - 动态计算
                model.x = currentWidth / 2;
                model.y = currentHeight / 2;
                // 保持原有锚点设置，不重置
                // model.anchor.set(0.5, 0.5); // 注释掉，避免覆盖用户设置的锚点
            }
        };

        window.addEventListener('resize', resizeHandler);

        // 初始调整大小
        if (resizeHandler) {
            resizeHandler();
        }

        applyInteractionMode()
        updatePointerTracking()
    };

    // 开始初始化
    initLive2D();
});

// 资源清理函数
const cleanup = () => {
    // 重置初始化状态
    isInitialized = false;

    // 1. 停止音乐胶囊播放
    if (musicCapsuleRef.value) {
        musicCapsuleRef.value.stopMusic();
    }

    // 2. 停止音频播放
    stopSpeak();

    // 3. 清理音频队列
    audioQueue.value = [];

    // 4. 移除窗口事件监听器
    if (resizeHandler) {
        window.removeEventListener('resize', resizeHandler);
        resizeHandler = null;
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
        } catch (error) {
            console.error('清理模型时出错:', error);
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
        } catch (error) {
            console.error('清理PIXI应用时出错:', error);
        }
        app = null;
    }

};

// 组件卸载时清理资源
onBeforeUnmount(() => {
    cleanup();
    lipSync.destroy()
});

watch(() => [props.interactive, props.followPointer], () => {
    applyInteractionMode()
    updatePointerTracking()
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
}

#canvas {
    width: 100%;
    height: 100%;
}
</style>
