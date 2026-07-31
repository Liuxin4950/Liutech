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
            <span>{{ formatDateTime(checkinStatus.lastCheckinDate) }}</span>
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
    <!-- 签到月历：GitHub 风格贡献格子 -->
    <div class="checkin-calendar" v-if="calendarCells.length > 0">
      <!-- 日历头部 -->
      <div class="calendar-header">
        <div class="calendar-title">
          <button
            v-if="canGoPrev"
            class="month-nav-btn"
            title="上一个签到月份"
            @click="goPrevMonth"
          >
            <Icon name="chevronLeft" size="16" />
          </button>
          <div class="calendar-month-wrap">
            <span class="calendar-month-text">{{ calendarYear }}年{{ calendarMonth }}月</span>
            <span v-if="isCurrentMonth" class="calendar-sub">本月签到日历</span>
            <button v-else class="calendar-sub back-today-btn" @click="goCurrentMonth">回到本月</button>
          </div>
          <button
            v-if="canGoNext"
            class="month-nav-btn"
            title="下一个签到月份"
            @click="goNextMonth"
          >
            <Icon name="chevronRight" size="16" />
          </button>
        </div>
        <div class="calendar-legend">
          <span class="legend-label">积分</span>
          <div v-for="item in legendItems" :key="item.label" class="legend-item">
            <span class="legend-cell" :style="{ backgroundColor: getCellStyle(item.level) }"></span>
            <span class="legend-item-label">{{ item.label }}</span>
          </div>
        </div>
      </div>

      <!-- 日历主体：左侧紧凑网格 + 右侧本月信息 -->
      <div class="calendar-main">
        <!-- 左侧：紧凑格子网格 -->
        <div class="calendar-grid-panel">
          <!-- 星期表头 -->
          <div class="calendar-weekdays">
            <span v-for="w in weekdays" :key="w" class="weekday">{{ w }}</span>
          </div>

          <!-- 日期格子 -->
          <div class="calendar-grid">
            <template v-for="(cell, index) in calendarCells" :key="index">
              <div
                v-if="cell"
                class="calendar-cell"
                :class="{
                  'is-checkin': cell.isCheckin,
                  'is-today': cell.isToday,
                  'is-future': cell.isFuture,
                  'level-4': cell.level === 4
                }"
                :style="cell.isCheckin ? { backgroundColor: getCellStyle(cell.level) } : {}"
                @mouseenter="hoveredCell = cell"
                @mouseleave="hoveredCell = null"
              >
                <span class="cell-day">{{ cell.day }}</span>
                <!-- 悬停提示：当天签到积分 -->
                <Transition name="tooltip">
                  <div v-if="hoveredCell === cell" class="cell-tooltip">
                    <strong class="tooltip-date">{{ cell.month }}月{{ cell.day }}日</strong>
                    <span v-if="cell.isCheckin" class="tooltip-points">
                      已签到 +{{ cell.pointsEarned }} 积分
                    </span>
                    <span v-else class="tooltip-empty">未签到</span>
                  </div>
                </Transition>
              </div>
              <div v-else class="calendar-cell placeholder"></div>
            </template>
          </div>
        </div>

        <!-- 右侧：本月统计与奖励规则 -->
        <div class="calendar-side">
          <div class="side-title">本月签到</div>
          <div class="side-stats">
            <div class="side-stat">
              <Icon name="check" size="14" class="side-stat-icon" />
              <span>已签 <strong>{{ monthCheckinCount }}</strong> 天</span>
            </div>
            <div class="side-stat">
              <Icon name="star" size="14" class="side-stat-icon" />
              <span>累计 <strong>+{{ monthTotalPoints }}</strong> 积分</span>
            </div>
          </div>
          <div class="side-rules">
            <div class="rule-title">签到奖励</div>
            <div class="rule-item"><span class="rule-dot"></span>每日签到 +1 积分</div>
            <div class="rule-item"><span class="rule-dot"></span>连续 7 天额外 +1</div>
            <div class="rule-item"><span class="rule-dot"></span>连续 30 天额外 +5</div>
          </div>
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
import { ref, computed, onMounted } from 'vue'
import { UserService, type CheckinStatus, type CheckinResponse, type CheckinCalendarItem } from '@/services/user'
import { formatDateTime } from '@/utils/utils'
import { showError } from '@/utils/errorHandler'
import Icon from './Icon.vue'

const checkinStatus = ref<CheckinStatus | null>(null)
const isLoading = ref(false)
const showSuccessMessage = ref(false)
const lastCheckinResult = ref<CheckinResponse | null>(null)

const emit = defineEmits<{
  checkinSuccess: [result: CheckinResponse]
}>()

// ===== 签到月历（GitHub 风格格子） =====
interface CalendarCell {
  day: number
  month: number
  year: number
  dateStr: string
  isCheckin: boolean
  pointsEarned: number
  level: number
  isToday: boolean
  isFuture: boolean
}

const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const calendarYear = ref(0)
const calendarMonth = ref(0)
const calendarCells = ref<(CalendarCell | null)[]>([])
const hoveredCell = ref<CalendarCell | null>(null)

/** 用户有过签到记录的月份集合（YYYY-MM），决定切换按钮显隐 */
const checkinMonths = ref<Set<string>>(new Set())

/** 图例：色块深浅对应的积分档位说明 */
const legendItems = [
  { level: 1, label: '+1' },
  { level: 2, label: '+2' },
  { level: 4, label: '+6' }
]

const nowYear = new Date().getFullYear()
const nowMonth = new Date().getMonth() + 1
const nowDate = new Date().getDate()

const pad2 = (n: number) => String(n).padStart(2, '0')

/** 根据当天积分换算格子深浅档位（1-4 级） */
const getCellLevel = (points: number): number => {
  if (points >= 6) return 4
  if (points >= 3) return 3
  if (points >= 2) return 2
  return 1
}

/** 生成格子背景色：按档位递增透明度，自动适配深浅色模式 */
const getCellStyle = (level: number) => {
  const alphas = [0.2, 0.4, 0.65, 1]
  return `rgba(var(--color-primary-rgb), ${alphas[level - 1]})`
}

/** 构建当月日历网格（周一开头，含前置占位） */
const buildCalendar = (year: number, month: number, records: Map<string, number>) => {
  const firstWeekday = (new Date(year, month - 1, 1).getDay() + 6) % 7
  const daysInMonth = new Date(year, month, 0).getDate()
  const cells: (CalendarCell | null)[] = []

  for (let i = 0; i < firstWeekday; i++) {
    cells.push(null)
  }

  for (let day = 1; day <= daysInMonth; day++) {
    const dateStr = `${year}-${pad2(month)}-${pad2(day)}`
    const points = records.get(dateStr)
    const isCheckin = points !== undefined
    cells.push({
      day,
      month,
      year,
      dateStr,
      isCheckin,
      pointsEarned: points ?? 0,
      level: isCheckin ? getCellLevel(points) : 0,
      isToday: year === nowYear && month === nowMonth && day === nowDate,
      isFuture:
        year > nowYear ||
        (year === nowYear && month > nowMonth) ||
        (year === nowYear && month === nowMonth && day > nowDate)
    })
  }
  return cells
}

/** 拉取指定月份的签到记录并渲染月历 */
const fetchCheckinCalendar = async (year: number, month: number) => {
  calendarYear.value = year
  calendarMonth.value = month
  hoveredCell.value = null
  // 先用空数据渲染当月网格骨架，避免加载闪烁
  calendarCells.value = buildCalendar(year, month, new Map())

  try {
    const records = await UserService.getCheckinCalendar(year, month)
    const recordMap = new Map(records.map((r: CheckinCalendarItem) => [r.date, Number(r.pointsEarned)]))
    calendarCells.value = buildCalendar(year, month, recordMap)
  } catch {
    // 静默处理，保留无签到标记的网格
  }
}

/** 拉取用户有过签到记录的月份列表 */
const fetchCheckinMonths = async () => {
  try {
    const months = await UserService.getCheckinMonths()
    checkinMonths.value = new Set(months.map(m => `${m.year}-${pad2(m.month)}`))
  } catch {
    // 静默处理
  }
}

/** 月份键：YYYY-MM -> 可比较数值（如 2026-07 -> 202607） */
const monthKeyValue = (key: string) => {
  const [y, m] = key.split('-').map(Number)
  return y * 100 + m
}

/** 当前显示的是否为当月 */
const isCurrentMonth = computed(() => calendarYear.value === nowYear && calendarMonth.value === nowMonth)

/** 更早月份中是否存在签到记录（决定「上一个签到月份」按钮显隐） */
const canGoPrev = computed(() => {
  const current = calendarYear.value * 100 + calendarMonth.value
  return [...checkinMonths.value].some(key => monthKeyValue(key) < current)
})

/** 更晚月份中是否存在签到记录（决定「下一个签到月份」按钮显隐） */
const canGoNext = computed(() => {
  const current = calendarYear.value * 100 + calendarMonth.value
  return [...checkinMonths.value].some(key => monthKeyValue(key) > current)
})

/** 切换到最近的一个有签到记录的更早月份 */
const goPrevMonth = () => {
  if (!canGoPrev.value) return
  const current = calendarYear.value * 100 + calendarMonth.value
  let target = 0
  for (const key of checkinMonths.value) {
    const v = monthKeyValue(key)
    if (v < current && v > target) target = v
  }
  fetchCheckinCalendar(Math.floor(target / 100), target % 100)
}

/** 切换到最近的一个有签到记录的更晚月份 */
const goNextMonth = () => {
  if (!canGoNext.value) return
  const current = calendarYear.value * 100 + calendarMonth.value
  let target = Number.POSITIVE_INFINITY
  for (const key of checkinMonths.value) {
    const v = monthKeyValue(key)
    if (v > current && v < target) target = v
  }
  fetchCheckinCalendar(Math.floor(target / 100), target % 100)
}

/** 回到本月 */
const goCurrentMonth = () => {
  fetchCheckinCalendar(nowYear, nowMonth)
}

/** 本月已签天数 */
const monthCheckinCount = computed(() => calendarCells.value.filter(c => c?.isCheckin).length)

/** 本月累计获得积分 */
const monthTotalPoints = computed(() =>
  calendarCells.value.reduce((sum, c) => sum + (c?.isCheckin ? c.pointsEarned : 0), 0)
)

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

    // 同步更新月历中今天的格子
    const todayStr = `${nowYear}-${pad2(nowMonth)}-${pad2(nowDate)}`
    const todayCell = calendarCells.value.find(c => c && c.dateStr === todayStr)
    if (todayCell) {
      todayCell.isCheckin = true
      todayCell.pointsEarned = Number(result.pointsEarned)
      todayCell.level = getCellLevel(Number(result.pointsEarned))
    }
  } catch (error: any) {
    showError(error.message || '签到失败，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  fetchCheckinStatus()
  fetchCheckinMonths()
  fetchCheckinCalendar(nowYear, nowMonth)
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
  border-radius: inherit;
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

/* 签到月历（GitHub 风格格子） */
.checkin-calendar {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.calendar-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.calendar-month-wrap {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-title);
}

.calendar-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
}

.back-today-btn {
  color: var(--color-primary);
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.month-nav-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid var(--border-base);
  border-radius: 50%;
  background: var(--bg-element);
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    color: var(--color-primary);
    border-color: var(--color-primary);
    background: var(--state-primary-bg);
  }

  &:active {
    transform: scale(0.92);
  }
}

.calendar-legend {
  display: flex;
  align-items: center;
  gap: 10px;
}

.legend-label {
  font-size: 11px;
  color: var(--text-muted);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 3px;
}

.legend-cell {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.legend-item-label {
  font-size: 11px;
  color: var(--text-muted);
}

/* 日历主体：两栏布局（左侧紧凑网格 + 右侧本月信息） */
.calendar-main {
  display: flex;
  align-items: stretch;
  gap: 16px;
}

/* 左侧：紧凑网格面板 */
.calendar-grid-panel {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 10px 14px;
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  border-radius: 10px;
}

/* 右侧：本月统计与奖励规则面板 */
.calendar-side {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  border-radius: 10px;
}

.side-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-title);
}

.side-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
}

.side-stat {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);

  strong {
    color: var(--color-primary);
    font-weight: 700;
  }
}

.side-stat-icon {
  color: var(--color-primary);
  opacity: 0.8;
}

.side-rules {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}

.rule-title {
  width: 100%;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-subtle);
}

.rule-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.rule-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  opacity: 0.6;
}

.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 18px);
  gap: 4px;
  justify-content: center;
  margin-bottom: 4px;
}

.weekday {
  text-align: center;
  font-size: 11px;
  color: var(--text-muted);
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 32px);
  gap: 4px;
  justify-content: center;
}

.calendar-cell {
  position: relative;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: var(--bg-soft);
  font-size: 10px;
  line-height: 1;
  color: var(--text-muted);
  cursor: default;
  transition: transform 0.15s ease, box-shadow 0.15s ease;

  &.placeholder {
    background: transparent;
  }

  &.is-checkin {
    color: var(--color-primary);
    font-weight: 600;

    &.level-4 {
      color: #fff;
    }
  }

  &.is-today {
    box-shadow: 0 0 0 1px var(--color-primary);
  }

  &.is-future {
    opacity: 0.55;
  }

  &:hover {
    transform: scale(1.12);
    z-index: 20;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  }
}

/* 悬停提示 */
.cell-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 10px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: 8px;
  box-shadow: var(--shadow-md);
  white-space: nowrap;
  pointer-events: none;
  font-size: 12px;
  color: var(--text-main);

  &::after {
    content: '';
    position: absolute;
    top: 100%;
    left: 50%;
    transform: translateX(-50%);
    border: 5px solid transparent;
    border-top-color: var(--border-base);
  }
}

.tooltip-date {
  font-size: 12px;
  color: var(--text-title);
}

.tooltip-points {
  color: var(--color-primary);
  font-weight: 600;
}

.tooltip-empty {
  color: var(--text-muted);
}

/* 悬停提示动画 */
.tooltip-enter-active,
.tooltip-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.tooltip-enter-from,
.tooltip-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(4px);
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

  /* 日历两栏变上下堆叠 */
  .calendar-main {
    flex-direction: column;
    align-items: stretch;
  }

  .calendar-grid-panel {
    align-self: center;
  }

  .calendar-side {
    width: 100%;
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

  /* 日历头部：标题与图例纵向排列 */
  .calendar-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  /* 日历：统计纵向排列，奖励规则纵向堆叠 */
  .calendar-side {
    gap: 10px;
  }

  .side-stats {
    flex-direction: column;
    gap: 6px;
  }

  .side-rules {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .rule-title {
    width: auto;
  }
}
</style>
