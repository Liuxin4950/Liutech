<template>
  <div class="checkin-card" :class="{ 'is-checked': checkinStatus?.hasCheckedInToday }">
    <!-- 背景装饰 -->
    <div class="checkin-bg">
      <div class="bg-circle circle-1"></div>
      <div class="bg-circle circle-2"></div>
    </div>

    <div class="checkin-body">
      <!-- 左侧：签到信息 -->
      <div class="checkin-left">
        <div class="checkin-title">
          <Icon name="calendar" size="20" class="title-icon" />
          <span>每日签到</span>
        </div>

        <!-- 连续签到天数 -->
        <div class="streak-display" v-if="checkinStatus">
          <div class="streak-number">
            <span class="streak-value">{{ checkinStatus.consecutiveDays }}</span>
            <span class="streak-label">天</span>
          </div>
          <div class="streak-text">连续签到</div>
        </div>

        <!-- 统计信息 -->
        <div class="checkin-stats" v-if="checkinStatus">
          <div class="stat-item">
            <Icon name="check" size="14" />
            <span>累计 {{ checkinStatus.totalCheckins }} 次</span>
          </div>
          <div class="stat-item" v-if="checkinStatus.lastCheckinDate">
            <Icon name="clock" size="14" />
            <span>{{ formatDate(checkinStatus.lastCheckinDate) }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧：签到按钮 -->
      <div class="checkin-right">
        <button
          class="checkin-btn"
          :class="{
            'checked-in': checkinStatus?.hasCheckedInToday,
            'loading': isLoading
          }"
          :disabled="checkinStatus?.hasCheckedInToday || isLoading"
          @click="handleCheckin"
        >
          <div class="btn-content">
            <Icon v-if="!isLoading && !checkinStatus?.hasCheckedInToday" name="star" size="20" class="btn-icon" />
            <Icon v-else-if="checkinStatus?.hasCheckedInToday" name="check" size="20" class="btn-icon" />
            <span v-if="isLoading" class="loading-text">签到中...</span>
            <span v-else-if="checkinStatus?.hasCheckedInToday">已签到</span>
            <span v-else>签到</span>
          </div>
          <div class="btn-points" v-if="!checkinStatus?.hasCheckedInToday && !isLoading">
            +1 积分
          </div>
        </button>

        <!-- 连续签到奖励提示 -->
        <div class="reward-hint" v-if="checkinStatus && checkinStatus.consecutiveDays >= 3">
          <Icon name="fire" size="14" />
          <span v-if="checkinStatus.consecutiveDays < 7">再签 {{ 7 - checkinStatus.consecutiveDays }} 天得奖励</span>
          <span v-else-if="checkinStatus.consecutiveDays < 30">再签 {{ 30 - checkinStatus.consecutiveDays }} 天得大奖</span>
          <span v-else>已达最高连续签到</span>
        </div>
      </div>
    </div>

    <!-- 签到成功提示 -->
    <Transition name="success">
      <div v-if="showSuccessMessage" class="success-toast">
        <div class="toast-content">
          <Icon name="check" size="18" class="toast-icon" />
          <span>签到成功 +{{ lastCheckinResult?.pointsEarned }} 积分</span>
          <span v-if="(lastCheckinResult?.consecutiveDays || 0) >= 7" class="toast-bonus">
            连续签到奖励
          </span>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { UserService, type CheckinStatus, type CheckinResponse } from '@/services/user'
import { formatDateTime } from '@shared/utils'
import { showError } from '@/utils/errorHandler'
import Icon from './Icon.vue'

const checkinStatus = ref<CheckinStatus | null>(null)
const isLoading = ref(false)
const showSuccessMessage = ref(false)
const lastCheckinResult = ref<CheckinResponse | null>(null)

const emit = defineEmits<{
  checkinSuccess: [result: CheckinResponse]
}>()

const formatDate = (dateStr: string) => {
  return formatDateTime(dateStr)
}

const fetchCheckinStatus = async () => {
  try {
    checkinStatus.value = await UserService.getCheckinStatus()
  } catch {
    // 静默处理
  }
}

const handleCheckin = async () => {
  if (isLoading.value || checkinStatus.value?.hasCheckedInToday) return

  isLoading.value = true
  try {
    const result = await UserService.checkin()
    lastCheckinResult.value = result

    if (checkinStatus.value) {
      checkinStatus.value.hasCheckedInToday = true
      checkinStatus.value.consecutiveDays = result.consecutiveDays
      checkinStatus.value.totalCheckins += 1
      checkinStatus.value.lastCheckinDate = result.checkinDate
    }

    showSuccessMessage.value = true
    setTimeout(() => { showSuccessMessage.value = false }, 3000)

    emit('checkinSuccess', result)
  } catch (error: any) {
    showError(error.message || '签到失败，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  fetchCheckinStatus()
})

defineExpose({ fetchCheckinStatus })
</script>

<style lang="scss" scoped>
@use "@/assets/styles/tokens" as *;

.checkin-card {
  position: relative;
  background: var(--bg-card);
  border-radius: 16px;
  padding: 24px;
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition: all 0.3s ease;

  &.is-checked {
    border-color: var(--color-success);
  }
}

/* 背景装饰 */
.checkin-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.06;

  &.circle-1 {
    width: 120px;
    height: 120px;
    background: var(--color-primary);
    top: -30px;
    right: -20px;
  }

  &.circle-2 {
    width: 80px;
    height: 80px;
    background: var(--color-warning);
    bottom: -20px;
    left: 30%;
  }
}

.checkin-card.is-checked .bg-circle {
  opacity: 0.08;
  background: var(--color-success);
}

/* 主体布局 */
.checkin-body {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

/* 左侧 */
.checkin-left {
  flex: 1;
}

.checkin-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-title);
  margin-bottom: 16px;
}

.title-icon {
  color: var(--color-primary);
}

.checkin-card.is-checked .title-icon {
  color: var(--color-success);
}

/* 连续签到天数 */
.streak-display {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}

.streak-number {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.streak-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1;
}

.checkin-card.is-checked .streak-value {
  color: var(--color-success);
}

.streak-label {
  font-size: 14px;
  color: var(--text-muted);
  font-weight: 500;
}

.streak-text {
  font-size: 13px;
  color: var(--text-subtle);
  padding: 4px 10px;
  background: var(--bg-soft);
  border-radius: 12px;
}

/* 统计信息 */
.checkin-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
}

.stat-item svg {
  opacity: 0.7;
}

/* 右侧 */
.checkin-right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

/* 签到按钮 */
.checkin-btn {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  color: white;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  box-shadow: 0 4px 15px rgba(var(--color-primary-rgb), 0.3);

  &:hover:not(:disabled) {
    transform: scale(1.05);
    box-shadow: 0 6px 20px rgba(var(--color-primary-rgb), 0.4);
  }

  &:active:not(:disabled) {
    transform: scale(0.98);
  }

  &.checked-in {
    background: var(--bg-soft);
    color: var(--color-success);
    box-shadow: none;
    cursor: default;
  }

  &.loading {
    background: var(--border-base);
    cursor: wait;
  }
}

.btn-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 600;
}

.btn-icon {
  font-size: 20px;
}

.btn-points {
  font-size: 11px;
  opacity: 0.8;
}

.checkin-btn.checked-in .btn-points {
  display: none;
}

/* 奖励提示 */
.reward-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-warning);
  white-space: nowrap;
}

/* 成功提示 */
.success-toast {
  position: absolute;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
}

.toast-content {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--color-success);
  color: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 4px 15px rgba(52, 168, 83, 0.3);
  white-space: nowrap;
}

.toast-icon {
  font-size: 16px;
}

.toast-bonus {
  padding: 2px 8px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  font-size: 12px;
}

/* 成功动画 */
.success-enter-active {
  animation: toastIn 0.3s ease-out;
}

.success-leave-active {
  animation: toastOut 0.3s ease-in;
}

@keyframes toastIn {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

@keyframes toastOut {
  from {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
  to {
    opacity: 0;
    transform: translateX(-50%) translateY(-10px);
  }
}

/* 响应式 */
@include respond(md) {
  .checkin-card {
    padding: 20px;
  }

  .checkin-body {
    flex-direction: column;
    align-items: stretch;
    gap: 20px;
  }

  .checkin-right {
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
  }

  .checkin-btn {
    width: 80px;
    height: 80px;
  }

  .streak-value {
    font-size: 28px;
  }
}

@include respond(sm) {
  .checkin-right {
    flex-direction: column;
    gap: 12px;
  }

  .checkin-stats {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
