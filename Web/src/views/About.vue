<template>
  <div class="content">
    <!-- 页头信息 -->
    <div class="card text-center mb-20">
      <img src="/default-avatar.svg" alt="刘鑫" class="w-120 h-120 rounded-full mx-auto mb-16 border-4" />
      <h1 class="text-2xl font-bold text-primary mb-8">刘鑫</h1>
      <p class="text-lg text-muted mb-0">全栈工程师 & 技术博主</p>
    </div>

    <div class="flex flex-col gap-20">
      <!-- 关于我 -->
      <section class="card">
        <h2 class="card-title">👋 关于我</h2>
        <p class="text-base text-muted mb-12 leading-relaxed">
          你好！我是刘鑫，一名热爱技术的全栈工程师。专注于前端开发、后端架构设计，
          喜欢探索新技术并分享技术心得。
        </p>
        <p class="text-base text-muted mb-0 leading-relaxed">
          这个博客是我记录技术学习和生活感悟的地方，希望能够帮助到更多的开发者朋友。
        </p>
      </section>

      <!-- 技术栈 -->
      <section class="card">
        <h2 class="card-title">💻 技术栈</h2>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-16">
          <div class="skill-category">
            <h3 class="text-lg font-semibold text-primary mb-12">前端技术</h3>
            <div class="tags-cloud">
              <span class="tag">Vue.js</span>
              <span class="tag">React</span>
              <span class="tag">TypeScript</span>
              <span class="tag">JavaScript</span>
              <span class="tag">HTML/CSS</span>
              <span class="tag">Vite</span>
            </div>
          </div>
          <div class="skill-category">
            <h3 class="text-lg font-semibold text-primary mb-12">后端技术</h3>
            <div class="tags-cloud">
              <span class="tag">Java</span>
              <span class="tag">Spring Boot</span>
              <span class="tag">MyBatis</span>
              <span class="tag">MySQL</span>
              <span class="tag">Redis</span>
              <span class="tag">Docker</span>
            </div>
          </div>
          <div class="skill-category">
            <h3 class="text-lg font-semibold text-primary mb-12">开发工具</h3>
            <div class="tags-cloud">
              <span class="tag">Git</span>
              <span class="tag">VS Code</span>
              <span class="tag">IntelliJ IDEA</span>
              <span class="tag">Postman</span>
              <span class="tag">Maven</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 联系方式 -->
      <section class="card">
        <h2 class="card-title">📞 联系方式</h2>
        <div class="list gap-12">
          <div class="flex flex-ac gap-12 p-12 bg-hover rounded">
            <span class="text-lg">📧</span>
            <span class="text-base text-muted">liuxin@example.com</span>
          </div>
          <div class="flex flex-ac gap-12 p-12 bg-hover rounded">
            <span class="text-lg">🐙</span>
            <span class="text-base text-muted">GitHub: @liuxin</span>
          </div>
          <div class="flex flex-ac gap-12 p-12 bg-hover rounded">
            <span class="text-lg">🌐</span>
            <span class="text-base text-muted">个人网站: liutech.blog</span>
          </div>
        </div>
      </section>

      <!-- 关于本站 -->
      <section class="card">
        <h2 class="card-title">🚀 关于本站</h2>
        <p class="text-base text-muted mb-16 leading-relaxed">
          本站采用现代化的技术栈构建，前后端分离架构，
          支持文章发布、分类管理、标签系统、用户评论等功能。
        </p>
        <div class="list gap-12">
          <div class="p-12 bg-hover rounded border-l-3">
            <strong class="text-primary">前端：</strong>Vue 3 + TypeScript + Vite
          </div>
          <div class="p-12 bg-hover rounded border-l-3">
            <strong class="text-primary">后端：</strong>Spring Boot + MyBatis + MySQL
          </div>
          <div class="p-12 bg-hover rounded border-l-3">
            <strong class="text-primary">部署：</strong>Vercel + 云服务器
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
// 关于页面组件
</script>

<style scoped>
/* 使用全局样式类，保持简洁 */
.w-120 {
  width: 120px;
}

.h-120 {
  height: 120px;
}

.grid {
  display: grid;
}

.grid-cols-1 {
  grid-template-columns: repeat(1, minmax(0, 1fr));
}

@media (min-width: 768px) {
  .md\:grid-cols-3 {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.leading-relaxed {
  line-height: 1.625;
}
</style>