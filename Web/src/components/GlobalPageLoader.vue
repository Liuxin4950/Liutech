<script setup lang="ts">
// 全屏页面加载动画 - LiuTech字母方块动画（适配主题颜色）
// 作者：刘鑫  修改时间：2025-08-26 15:50:49 +08:00
const props = defineProps<{ show: boolean }>()
</script>

 <template>
  <Transition name="fade" appear>
    <div v-if="show" class="loader-overlay">
    <div class='base'>
      <!-- L字母 -->
      <div class='cube letter-l pos-1'></div>
      <div class='cube letter-l pos-2'></div>
      <div class='cube letter-l pos-3'></div>
      <div class='cube letter-l pos-4'></div>
      <div class='cube letter-l pos-5'></div>
      
      <!-- i字母 -->
      <div class='cube letter-i pos-6'></div>
      <div class='cube letter-i pos-7'></div>
      <div class='cube letter-i pos-8'></div>
      
      <!-- u字母 -->
        <div class='cube letter-u pos-9'></div>
        <div class='cube letter-u pos-10'></div>
        <div class='cube letter-u pos-11'></div>
        <div class='cube letter-u pos-12'></div>
        <div class='cube letter-u pos-13'></div>
        <div class='cube letter-u pos-36'></div>
        <div class='cube letter-u pos-37'></div>
        <div class='cube letter-u pos-43'></div>
        <div class='cube letter-u pos-44'></div>
      
      <!-- T字母 -->
       <div class='cube letter-t pos-14'></div>
       <div class='cube letter-t pos-15'></div>
       <div class='cube letter-t pos-16'></div>
       <div class='cube letter-t pos-17'></div>
       <div class='cube letter-t pos-18'></div>
       <div class='cube letter-t pos-38'></div>
      
      <!-- e字母 -->
       <div class='cube letter-e pos-19'></div>
       <div class='cube letter-e pos-20'></div>
       <div class='cube letter-e pos-21'></div>
       <div class='cube letter-e pos-22'></div>
       <div class='cube letter-e pos-23'></div>
       <div class='cube letter-e pos-24'></div>
       <div class='cube letter-e pos-25'></div>
       <div class='cube letter-e pos-45'></div>
       <div class='cube letter-e pos-46'></div>
       <div class='cube letter-e pos-47'></div>
      
      <!-- c字母 -->
        <div class='cube letter-c pos-26'></div>
        <div class='cube letter-c pos-27'></div>
        <div class='cube letter-c pos-28'></div>
        <div class='cube letter-c pos-29'></div>
        <div class='cube letter-c pos-39'></div>
        <div class='cube letter-c pos-40'></div>
        <div class='cube letter-c pos-48'></div>
        <div class='cube letter-c pos-49'></div>
      
      <!-- h字母 -->
         <div class='cube letter-h pos-30'></div>
         <div class='cube letter-h pos-31'></div>
         <div class='cube letter-h pos-32'></div>
         <div class='cube letter-h pos-33'></div>
         <div class='cube letter-h pos-34'></div>
         <div class='cube letter-h pos-35'></div>
         <div class='cube letter-h pos-41'></div>
         <div class='cube letter-h pos-42'></div>
         <div class='cube letter-h pos-50'></div>
         <div class='cube letter-h pos-51'></div>
         <div class='cube letter-h pos-52'></div>
    </div>
    </div>
  </Transition>
 </template>

 <style>
.loader-overlay {
	position: fixed;
	top: 0;
	left: 0;
	width: 100vw;
	height: 100vh;
	background-color: var(--bg-main);
	backdrop-filter: blur(8px);
	z-index: 9999;
	display: flex;
	align-items: center;
	justify-content: center;
}

/* Vue Transition 渐变动画 */
.fade-enter-active,
.fade-leave-active {
	transition: opacity 0.2s ease-in-out;
}

.fade-enter-from,
.fade-leave-to {
	opacity: 0;
}

.base {
	position: relative;
	width: 32em;
	height: 5.5em;
	transform: rotateX(12deg) rotateZ(3deg);
	transform-style: preserve-3d;
}

.cube,
.cube:after,
.cube:before {
	content: '';
	height: 1em;
	position: absolute;
	width: 1em;
}

.cube {
	background-color: var(--color-primary);
	position: absolute;
	transform: translateZ(1em);
	transform-style: preserve-3d;
	transition: .3s;
	box-shadow: var(--shadow-md);
	animation: letterPulse 1.2s infinite;
	border-radius: 3px;
}
.cube:after {
	background-color: var(--color-primary-dark);
	transform: rotateX(-90deg) translateY(1em);
	transform-origin: 100% 100%;
}
.cube:before {
	background-color: var(--color-primary-light);
	transform: rotateY(90deg) translateX(1em);
	transform-origin: 100% 0;
}
/* L字母位置 */
.pos-1 { left: 0em; top: 0em; animation-delay: 0.1s; }
.pos-2 { left: 0em; top: 1.2em; animation-delay: 0.15s; }
.pos-3 { left: 0em; top: 2.4em; animation-delay: 0.2s; }
.pos-4 { left: 0em; top: 3.6em; animation-delay: 0.25s; }
.pos-5 { left: 1.2em; top: 3.6em; animation-delay: 0.3s; }

/* i字母位置 */
.pos-6 { left: 3.8em; top: 0em; animation-delay: 0.35s; }
.pos-7 { left: 3.8em; top: 1.8em; animation-delay: 0.4s; }
.pos-8 { left: 3.8em; top: 3.6em; animation-delay: 0.45s; }

/* u字母位置 */
.pos-9 { left: 6.2em; top: 0em; animation-delay: 0.3s; }
.pos-10 { left: 6.2em; top: 1.2em; animation-delay: 0.32s; }
.pos-11 { left: 6.2em; top: 2.4em; animation-delay: 0.34s; }
.pos-12 { left: 6.2em; top: 3.6em; animation-delay: 0.36s; }
.pos-13 { left: 7.4em; top: 3.6em; animation-delay: 0.38s; }
.pos-36 { left: 8.6em; top: 3.6em; animation-delay: 0.4s; }
.pos-37 { left: 8.6em; top: 2.4em; animation-delay: 0.42s; }
.pos-43 { left: 8.6em; top: 1.2em; animation-delay: 0.44s; }
.pos-44 { left: 8.6em; top: 0em; animation-delay: 0.46s; }

/* T字母位置 */
.pos-14 { left: 11.2em; top: 0em; animation-delay: 0.5s; }
.pos-15 { left: 12.4em; top: 0em; animation-delay: 0.52s; }
.pos-16 { left: 13.6em; top: 0em; animation-delay: 0.54s; }
.pos-17 { left: 12.4em; top: 1.2em; animation-delay: 0.56s; }
.pos-18 { left: 12.4em; top: 2.4em; animation-delay: 0.58s; }
.pos-38 { left: 12.4em; top: 3.6em; animation-delay: 0.6s; }

/* e字母位置 */
.pos-19 { left: 16.2em; top: 0em; animation-delay: 0.65s; }
.pos-20 { left: 17.4em; top: 0em; animation-delay: 0.67s; }
.pos-21 { left: 18.6em; top: 0em; animation-delay: 0.69s; }
.pos-22 { left: 16.2em; top: 1.2em; animation-delay: 0.71s; }
.pos-23 { left: 16.2em; top: 1.8em; animation-delay: 0.73s; }
.pos-24 { left: 17.4em; top: 1.8em; animation-delay: 0.75s; }
.pos-25 { left: 16.2em; top: 2.4em; animation-delay: 0.77s; }
.pos-45 { left: 16.2em; top: 3.6em; animation-delay: 0.79s; }
.pos-46 { left: 17.4em; top: 3.6em; animation-delay: 0.81s; }
.pos-47 { left: 18.6em; top: 3.6em; animation-delay: 0.83s; }

/* c字母位置 */
.pos-26 { left: 21.2em; top: 0em; animation-delay: 0.85s; }
.pos-27 { left: 22.4em; top: 0em; animation-delay: 0.87s; }
.pos-28 { left: 23.6em; top: 0em; animation-delay: 0.89s; }
.pos-29 { left: 21.2em; top: 1.2em; animation-delay: 0.91s; }
.pos-39 { left: 21.2em; top: 2.4em; animation-delay: 0.93s; }
.pos-40 { left: 21.2em; top: 3.6em; animation-delay: 0.95s; }
.pos-48 { left: 22.4em; top: 3.6em; animation-delay: 0.97s; }
.pos-49 { left: 23.6em; top: 3.6em; animation-delay: 0.99s; }

/* h字母位置 */
.pos-30 { left: 26.2em; top: 0em; animation-delay: 1.01s; }
.pos-31 { left: 26.2em; top: 1.2em; animation-delay: 1.03s; }
.pos-32 { left: 26.2em; top: 1.8em; animation-delay: 1.05s; }
.pos-33 { left: 27.4em; top: 1.8em; animation-delay: 1.07s; }
.pos-34 { left: 28.6em; top: 1.8em; animation-delay: 1.09s; }
.pos-35 { left: 26.2em; top: 2.4em; animation-delay: 1.11s; }
.pos-41 { left: 26.2em; top: 3.6em; animation-delay: 1.13s; }
.pos-42 { left: 28.6em; top: 0em; animation-delay: 1.15s; }
.pos-50 { left: 28.6em; top: 1.2em; animation-delay: 1.17s; }
.pos-51 { left: 28.6em; top: 2.4em; animation-delay: 1.19s; }
.pos-52 { left: 28.6em; top: 3.6em; animation-delay: 1.21s; }

/* 字母颜色区分（使用主题变量） */
.letter-l { background-color: var(--color-error) !important; }
.letter-i { background-color: var(--color-info) !important; }
.letter-u { background-color: var(--color-primary) !important; }
.letter-t { background-color: var(--color-success) !important; }
.letter-e { background-color: var(--color-warning) !important; }
.letter-c { background-color: var(--color-primary-light) !important; }
.letter-h { background-color: var(--color-primary-dark) !important; }

@keyframes letterPulse {
	0%, 100% {
		transform: translateZ(1em) scale(1);
	}
	50% {
		transform: translateZ(2em) scale(1.1);
	}
}
</style>