<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getStatistics, type StatisticsData } from '@/services/statistics'
import { message } from 'ant-design-vue'

// 统计数据
const statistics = ref<StatisticsData>({
  postCount: 0,
  categoryCount: 0,
  tagCount: 0,
  userCount: 0
})

// 加载状态
const loading = ref(true)

/**
 * 加载统计数据
 */
const loadStatistics = async () => {
  try {
    loading.value = true
    const data = await getStatistics()
    statistics.value = data
  } catch (error) {
    console.error('加载统计数据失败:', error)
    message.error('加载统计数据失败')
  } finally {
    loading.value = false
  }
}

// 组件挂载时加载数据
onMounted(() => {
  loadStatistics()
})
</script>

<template>
  <div class="dashboard-content">
    <a-card class="welcome-card">
      <h1>欢迎来到管理后台</h1>
      <p>请从左侧菜单选择要管理的内容</p>
      
      <a-row :gutter="[16, 16]" class="stats-row">
        <a-col :span="6">
          <a-card class="stat-card" :loading="loading">
            <a-statistic 
              title="文章总数" 
              :value="statistics.postCount" 
              :value-style="{ color: '#3f8600' }"
            />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card" :loading="loading">
            <a-statistic 
              title="分类总数" 
              :value="statistics.categoryCount" 
              :value-style="{ color: '#cf1322' }"
            />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card" :loading="loading">
            <a-statistic 
              title="标签总数" 
              :value="statistics.tagCount" 
              :value-style="{ color: '#1890ff' }"
            />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card" :loading="loading">
            <a-statistic 
              title="用户总数" 
              :value="statistics.userCount" 
              :value-style="{ color: '#722ed1' }"
            />
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
