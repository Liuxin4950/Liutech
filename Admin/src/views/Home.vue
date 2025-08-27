<script setup lang="ts">
// Home页面 - 管理后台首页
</script>

<template>
  <div class="dashboard-content">
    <a-card class="welcome-card">
      <h1>欢迎来到管理后台</h1>
      <p>请从左侧菜单选择要管理的内容</p>
      
      <a-row :gutter="[16, 16]" class="stats-row">
        <a-col :span="6">
          <a-card class="stat-card">
            <a-statistic title="文章总数" :value="0" />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card">
            <a-statistic title="分类总数" :value="0" />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card">
            <a-statistic title="标签总数" :value="0" />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card">
            <a-statistic title="用户总数" :value="0" />
          </a-card>
        </a-col>
      </a-row>
    </a-card>
  </div>
</template>

<style scoped>
.dashboard-content {
  padding: 0;
}

.welcome-card {
  text-align: center;
  margin-bottom: 24px;
}

.welcome-card h1 {
  color: #1890ff;
  margin-bottom: 16px;
  font-size: 28px;
}

.welcome-card p {
  color: #666;
  font-size: 16px;
  margin-bottom: 32px;
}

.stats-row {
  margin-top: 24px;
}

.stat-card {
  text-align: center;
  border-radius: 8px;
  transition: all 0.3s;
}

.stat-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}
</style>
