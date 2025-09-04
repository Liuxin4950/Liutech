<template>
  <div class="checkin-card">
    <div class="checkin-header">
      <h3>每日签到</h3>
      <div class="checkin-streak" v-if="checkinStatus">
        <span class="streak-text">连续签到</span>
        <span class="streak-days">{{ checkinStatus.consecutiveDays }}</span>
        <span class="streak-unit">天</span>
      </div>
    </div>
    
    <div class="checkin-content">
      <div class="checkin-info" v-if="checkinStatus">
        <div class="info-item">
          <span class="info-label">总签到次数</span>
          <span class="info-value">{{ checkinStatus.totalCheckins }}</span>
        </div>
        <div class="info-item" v-if="checkinStatus.lastCheckinDate">
          <span class="info-label">最后签到</span>
          <span class="info-value">{{ formatDate(checkinStatus.lastCheckinDate) }}</span>
        </div>
      </div>
      
      <button 
        class="checkin-btn"
        :class="{
          'checked-in': checkinStatus?.hasCheckedInToday,
          'loading': isLoading
        }"
        :disabled="checkinStatus?.hasCheckedInToday || isLoading"
        @click="handleCheckin"
      >
        <span v-if="isLoading">签到中...</span>
        <span v-else-if="checkinStatus?.hasCheckedInToday">今日已签到</span>
        <span v-else>每日签到 +1积分</span>
      </button>
    </div>
    
    <!-- 签到成功提示 -->
    <div v-if="showSuccessMessage" class="success-message">
      <div class="success-content">
        <span class="success-icon">✓</span>
        <span class="success-text">
          签到成功！获得 {{ lastCheckinResult?.pointsEarned }} 积分
        </span>
        <span v-if="lastCheckinResult?.consecutiveDays && lastCheckinResult.consecutiveDays >= 7" class="bonus-text">
          连续签到奖励！
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { UserService, type CheckinStatus, type CheckinResponse } from '@/services/user'
import { formatDateTime } from '@/utils/uitls'

// 响应式数据
const checkinStatus = ref<CheckinStatus | null>(null)
const isLoading = ref(false)
const showSuccessMessage = ref(false)
const lastCheckinResult = ref<CheckinResponse | null>(null)

// 定义事件
const emit = defineEmits<{
  checkinSuccess: [result: CheckinResponse]
}>()

/**
 * 格式化日期
 */
const formatDate = (dateStr: string) => {
  return formatDateTime(dateStr)
}

/**
 * 获取签到状态
 */
const fetchCheckinStatus = async () => {
  try {
    checkinStatus.value = await UserService.getCheckinStatus()
  } catch (error) {
    console.error('获取签到状态失败:', error)
  }
}

/**
 * 处理签到
 */
const handleCheckin = async () => {
  if (isLoading.value || checkinStatus.value?.hasCheckedInToday) {
    return
  }
  
  isLoading.value = true
  
  try {
    const result = await UserService.checkin()
    lastCheckinResult.value = result
    
    // 更新签到状态
    if (checkinStatus.value) {
      checkinStatus.value.hasCheckedInToday = true
      checkinStatus.value.consecutiveDays = result.consecutiveDays
      checkinStatus.value.totalCheckins += 1
      checkinStatus.value.lastCheckinDate = result.checkinDate
    }
    
    // 显示成功消息
    showSuccessMessage.value = true
    setTimeout(() => {
      showSuccessMessage.value = false
    }, 3000)
    
    // 触发事件，通知父组件更新积分
    emit('checkinSuccess', result)
    
  } catch (error: any) {
    console.error('签到失败:', error)
    // 这里可以添加错误提示
    alert(error.message || '签到失败，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

// 组件挂载时获取签到状态
onMounted(() => {
  fetchCheckinStatus()
})

// 暴露方法给父组件
defineExpose({
  fetchCheckinStatus
})
</script>

<style scoped>
.checkin-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin-bottom: 24px;
  position: relative;
}

.checkin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.checkin-header h3 {
  margin: 0;
  color: #333;
  font-size: 18px;
  font-weight: 600;
}

.checkin-streak {
  display: flex;
  align-items: center;
  gap: 4px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 14px;
}

.streak-days {
  font-weight: bold;
  font-size: 16px;
}

.checkin-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.checkin-info {
  display: flex;
  gap: 24px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: #666;
}

.info-value {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.checkin-btn {
  width: 100%;
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.checkin-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.checkin-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.checkin-btn.checked-in {
  background: #e0e0e0;
  color: #666;
}

.checkin-btn.loading {
  background: #ccc;
  cursor: not-allowed;
}

.success-message {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(76, 175, 80, 0.95);
  color: white;
  padding: 16px 24px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  z-index: 10;
  animation: fadeInOut 3s ease-in-out;
}

.success-content {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
}

.success-icon {
  font-size: 18px;
  font-weight: bold;
}

.bonus-text {
  color: #ffeb3b;
  font-weight: bold;
}

@keyframes fadeInOut {
  0%, 100% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.9);
  }
  20%, 80% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}

@media (max-width: 768px) {
  .checkin-card {
    padding: 16px;
    margin-bottom: 16px;
  }
  
  .checkin-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .checkin-info {
    flex-direction: column;
    gap: 12px;
  }
  
  .checkin-btn {
    padding: 14px 20px;
  }
}
</style>