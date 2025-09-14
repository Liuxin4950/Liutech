<template>
    <div class="container">
        <canvas @click="playTestAudio" id="canvas"></canvas>
    </div>
</template>


<script setup lang="ts">
/**
 * Live2D 模型展示组件
 * 作者: 刘鑫
 * 功能: 纯净的Live2D模型展示，支持基本交互和拖拽
 */
import { onMounted ,watch } from 'vue';
import { ref } from 'vue';

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

// 模型实例
let model: any = null;

// 拖拽相关变量
let isDragging = false;
let dragOffset = { x: 0, y: 0 };

// 音频播放状态
const isAuto = ref<boolean>(true);//音频是否空闲
let audioQueue = ref<string[]>([]); // 音频队列

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
function talk(audioPath:string) {
    return new Promise((resolve, reject) => {
        isAuto.value = false; // 播放开始
        const audioChecker = new Audio(audioPath); // 用于判断播放完成

        audioChecker.volume = 0; // 静音，用于检测播放状态
        audioChecker.crossOrigin = "anonymous";

        // 播放结束
        audioChecker.onended = () => {
            isAuto.value = true;
            resolve(void 0);
        };

        // 播放失败
        audioChecker.onerror = (err) => {
            console.error("检测音频播放出错：", err);
            isAuto.value = true; // 即使失败也恢复状态
            reject(err);
        };

        // 播放检测对象
        audioChecker.play().catch((err) => {
            console.error("检测音频无法播放：", err);
            isAuto.value = true;
            reject(err);
        });
        let exp = Math.floor(Math.random() * 10) + 1;
        // 实际播放音频
        model.speak(audioPath, {
            volume: 1,
            expression: exp,
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
            const audioPath = "http://127.0.0.1:8081" + `${audioQueue.value[0]}`;
            try {
                console.log("正在播放", audioPath);
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

// 播放测试音频
function playTestAudio() {
    // 判断是否已经有音频在播放了
    if (!isAuto.value) {
        return;
    }
    console.log("播放");
    
    audioQueue.value.push("/static/毕业旅行.flac");
}

onMounted(() => {
    // 等待全局脚本加载完成
    const initLive2D = () => {
        console.log('检查Live2D依赖:', {
            PIXI: !!window.PIXI,
            Live2DModel: !!(window as any).Live2DModel,
            PIXIlive2d: !!(window as any).PIXI?.live2d
        });
        
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

        console.log('开始初始化Live2D模型');
        
        // 创建 PIXI 应用
        const canvas = document.getElementById('canvas') as HTMLCanvasElement;
        const container = canvas.parentElement;
        const containerWidth = container?.clientWidth || 400;
        const containerHeight = container?.clientHeight || 400;
        
        const app = new window.PIXI.Application({
            view: canvas,
            width: containerWidth,
            height: containerHeight,
            backgroundColor: 0x000000,
            backgroundAlpha: 0, // 设置背景透明
            antialias: true,
            resolution: window.devicePixelRatio || 1
        });

        console.log('PIXI应用创建成功，开始加载模型:', cubism4Model);
        
        // 加载Live2D模型
        Live2DModelClass.from(cubism4Model).then((live2dModel: any) => {
            console.log('Live2D模型加载成功:', live2dModel);
            model = live2dModel;
            app.stage.addChild(live2dModel);
            // 设置模型锚点为中心
            live2dModel.anchor.set(0.5, 0.28);
            // 模型居中显示 - 动态获取容器尺寸
            live2dModel.x = containerWidth / 2;
            live2dModel.y = containerHeight / 2;
            console.log('模型位置:', live2dModel.x, live2dModel.y);
            // 固定模型大小，不受页面缩放影响
            const fixedScale = 0.15;
            live2dModel.scale.set(fixedScale);


            // 启用交互
            // 启用模型的交互功能
            live2dModel.interactive = true;
            // 监听鼠标按下事件，开始拖拽
            live2dModel.on('pointerdown', onPointerDown);
            // 监听鼠标移动事件，实现拖拽移动
            live2dModel.on('pointermove', onPointerMove);
            // 监听鼠标松开事件，结束拖拽
            live2dModel.on('pointerup', onPointerUp);
            // 监听鼠标移出模型区域事件，同样结束拖拽
            live2dModel.on('pointerupoutside', onPointerUp);

            // 鼠标跟随
            app.stage.interactive = true;
            app.stage.on('pointermove', (event: any) => {
                const point = event.data.global;
                live2dModel.focus(point.x, point.y);
            });

            // 点击触发动作
            live2dModel.on('hit', (hitAreas: any) => {
                if (hitAreas.includes('body')) {
                    live2dModel.motion('tap_body');
                }
            });

            // 自动眨眼和呼吸
            live2dModel.internalModel.motionManager.startRandomMotion('idle');
        }).catch((error: any) => {
            console.error('Live2D模型加载失败:', error);
        });

        // 窗口大小调整
        const handleResize = () => {
            // 动态获取容器尺寸
            const canvas = document.getElementById('canvas') as HTMLCanvasElement;
            const container = canvas.parentElement;
            const currentWidth = container?.clientWidth || 400;
            const currentHeight = container?.clientHeight || 400;
            
            app.renderer.resize(currentWidth, currentHeight);
            if (model) {
                // 模型位置居中显示 - 动态计算
                model.x = currentWidth / 2;
                model.y = currentHeight / 2;
                // 保持原有锚点设置，不重置
                // model.anchor.set(0.5, 0.5); // 注释掉，避免覆盖用户设置的锚点
            }
        };
        
        window.addEventListener('resize', handleResize);
        
        // 初始调整大小
        handleResize();
    };

    // 开始初始化
    initLive2D();
});


</script>

<style lang="scss" scoped>
/**
 * Live2D 模型展示组件样式
 * 作者: 刘鑫
 * 功能: 纯净的Live2D模型展示样式
 */
.container{
    width:100%;
    height: 100%;
    display: block;
}
#canvas {
    width: 100%;
    height: 100%;
}
</style>
