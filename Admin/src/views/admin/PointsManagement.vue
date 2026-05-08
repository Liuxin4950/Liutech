<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import PointsService from '../../services/points'
import { UserService } from '../../services/user'
import type { TransactionListParams, CheckinListParams, PointsStats } from '../../services/points'
import type { PointsTransaction, UserCheckin } from '../../services/points'
import { formatDateTime } from '../../utils/utils'

// 当前激活的 Tab
const activeTab = ref('transactions')

// =================== 积分统计 ===================
const statsLoading = ref(false)
const stats = ref<PointsStats>({
  totalIssued: 0,
  totalConsumed: 0,
  totalBalance: 0
})

const loadStats = async () => {
  try {
    statsLoading.value = true
    const response = await PointsService.getPointsStats()
    if (response.code === 200) {
      stats.value = response.data
    }
  } catch (error) {
    console.error('加载积分统计失败:', error)
  } finally {
    statsLoading.value = false
  }
}

// =================== 积分流水 Tab ===================
const txLoading = ref(false)
const txDataSource = ref<PointsTransaction[]>([])

const txPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条记录`
})

const txSearchParams = ref<TransactionListParams>({
  userId: undefined,
  transactionType: undefined,
  startTime: undefined,
  endTime: undefined
})

// 交易类型选项
const transactionTypeOptions = [
  { label: '签到', value: 'checkin' },
  { label: '消费', value: 'consumption' },
  { label: '退款', value: 'refund' },
  { label: '管理员调整', value: 'admin_adjust' }
]

// 交易类型显示映射
const transactionTypeMap: Record<string, { label: string; color: string }> = {
  checkin: { label: '签到', color: 'green' },
  consumption: { label: '消费', color: 'red' },
  refund: { label: '退款', color: 'orange' },
  admin_adjust: { label: '管理员调整', color: 'blue' }
}

// 积分流水表格列
const txColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
  { title: '交易类型', dataIndex: 'transactionType', key: 'transactionType', width: 120 },
  { title: '金额', dataIndex: 'amount', key: 'amount', width: 100 },
  { title: '余额', dataIndex: 'balanceAfter', key: 'balanceAfter', width: 100 },
  { title: '来源', dataIndex: 'sourceType', key: 'sourceType', width: 120 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 }
]

// 加载积分流水
const loadTransactions = async () => {
  try {
    txLoading.value = true
    const params = {
      page: txPagination.current,
      size: txPagination.pageSize,
      ...txSearchParams.value
    }
    const response = await PointsService.getTransactionList(params)
    if (response.code === 200) {
      txDataSource.value = response.data.records
      txPagination.total = response.data.total
    } else {
      message.error(response.message || '加载积分流水失败')
    }
  } catch (error) {
    message.error('加载积分流水失败')
    console.error('加载积分流水失败:', error)
  } finally {
    txLoading.value = false
  }
}

// 积分流水搜索
const handleTxSearch = () => {
  txPagination.current = 1
  loadTransactions()
}

// 积分流水重置
const handleTxReset = () => {
  txSearchParams.value = {
    userId: undefined,
    transactionType: undefined,
    startTime: undefined,
    endTime: undefined
  }
  txPagination.current = 1
  loadTransactions()
}

// 积分流水分页变化
const handleTxTableChange = (p: any) => {
  txPagination.current = p.current
  txPagination.pageSize = p.pageSize
  loadTransactions()
}

// =================== 签到记录 Tab ===================
const checkinLoading = ref(false)
const checkinDataSource = ref<UserCheckin[]>([])

const checkinPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条记录`
})

const checkinSearchParams = ref<CheckinListParams>({
  userId: undefined,
  startDate: undefined,
  endDate: undefined
})

// 签到记录表格列
const checkinColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
  { title: '签到日期', dataIndex: 'checkinDate', key: 'checkinDate', width: 120 },
  { title: '获得积分', dataIndex: 'pointsEarned', key: 'pointsEarned', width: 100 },
  { title: '连续签到天数', dataIndex: 'consecutiveDays', key: 'consecutiveDays', width: 120 },
  { title: '签到时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 }
]

// 加载签到记录
const loadCheckins = async () => {
  try {
    checkinLoading.value = true
    const params = {
      page: checkinPagination.current,
      size: checkinPagination.pageSize,
      ...checkinSearchParams.value
    }
    const response = await PointsService.getCheckinList(params)
    if (response.code === 200) {
      checkinDataSource.value = response.data.records
      checkinPagination.total = response.data.total
    } else {
      message.error(response.message || '加载签到记录失败')
    }
  } catch (error) {
    message.error('加载签到记录失败')
    console.error('加载签到记录失败:', error)
  } finally {
    checkinLoading.value = false
  }
}

// 签到记录搜索
const handleCheckinSearch = () => {
  checkinPagination.current = 1
  loadCheckins()
}

// 签到记录重置
const handleCheckinReset = () => {
  checkinSearchParams.value = {
    userId: undefined,
    startDate: undefined,
    endDate: undefined
  }
  checkinPagination.current = 1
  loadCheckins()
}

// 签到记录分页变化
const handleCheckinTableChange = (p: any) => {
  checkinPagination.current = p.current
  checkinPagination.pageSize = p.pageSize
  loadCheckins()
}

// =================== 手动调整积分弹窗 ===================
const adjustModalVisible = ref(false)
const adjustLoading = ref(false)
const adjustFormRef = ref()
const adjustForm = ref({
  userId: undefined as number | undefined,
  amount: undefined as number | undefined,
  description: ''
})

const adjustRules = {
  userId: [{ required: true, message: '请选择用户' }],
  amount: [{ required: true, message: '请输入调整金额' }]
}

// 用户搜索
const userOptions = ref<{ label: string; value: number }[]>([])
const userSearchLoading = ref(false)
const handleUserSearch = async (value: string) => {
  if (!value || value.length < 1) {
    userOptions.value = []
    return
  }
  try {
    userSearchLoading.value = true
    const res = await UserService.getUserList({ page: 1, size: 20, username: value })
    if (res.code === 200) {
      userOptions.value = res.data.records.map((u: any) => ({
        label: `${u.username} (${u.nickname || u.email || 'ID:' + u.id})`,
        value: u.id
      }))
    }
  } catch (e) {
    // ignore
  } finally {
    userSearchLoading.value = false
  }
}

const openAdjustModal = () => {
  adjustForm.value = {
    userId: undefined,
    amount: undefined,
    description: ''
  }
  adjustModalVisible.value = true
}

const handleAdjustOk = async () => {
  try {
    adjustLoading.value = true
    await adjustFormRef.value?.validate?.()

    if (!adjustForm.value.userId || adjustForm.value.amount === undefined) {
      message.error('请填写完整信息')
      return
    }

    const response = await PointsService.adjustPoints({
      userId: adjustForm.value.userId,
      amount: adjustForm.value.amount,
      description: adjustForm.value.description || undefined
    })

    if (response.code === 200) {
      message.success('积分调整成功')
      adjustModalVisible.value = false
      loadTransactions()
      loadStats()
    } else {
      message.error(response.message || '积分调整失败')
    }
  } catch (e) {
    // 表单校验失败或请求错误
  } finally {
    adjustLoading.value = false
  }
}

const handleAdjustCancel = () => {
  adjustModalVisible.value = false
}

// =================== Tab 切换 ===================
const handleTabChange = (key: string) => {
  activeTab.value = key
  if (key === 'transactions' && txDataSource.value.length === 0) {
    loadTransactions()
  } else if (key === 'checkins' && checkinDataSource.value.length === 0) {
    loadCheckins()
  }
}

// =================== 初始化 ===================
onMounted(() => {
  loadStats()
  loadTransactions()
})
</script>

<template>
  <div class="p-24">
    <!-- 统计卡片 -->
    <a-row :gutter="16" class="mb-16">
      <a-col :span="8">
        <a-card :bordered="false" :loading="statsLoading">
          <a-statistic
            title="总发放积分"
            :value="stats.totalIssued"
            :precision="2"
            :value-style="{ color: 'var(--color-success)' }"
          />
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card :bordered="false" :loading="statsLoading">
          <a-statistic
            title="总消耗积分"
            :value="stats.totalConsumed"
            :precision="2"
            :value-style="{ color: 'var(--color-error)' }"
          />
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card :bordered="false" :loading="statsLoading">
          <a-statistic
            title="总用户积分余额"
            :value="stats.totalBalance"
            :precision="2"
            :value-style="{ color: 'var(--color-info)' }"
          />
        </a-card>
      </a-col>
    </a-row>

    <!-- 主内容区域 -->
    <a-card :bordered="false">
      <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
        <!-- 积分流水 Tab -->
        <a-tab-pane key="transactions" tab="积分流水">
          <!-- 搜索区域 -->
          <a-form layout="horizontal" :model="txSearchParams" class="mb-16">
            <a-row :gutter="24">
              <a-col :span="4">
                <a-form-item label="用户ID" class="mb-0">
                  <a-input-number
                    v-model:value="txSearchParams.userId"
                    placeholder="请输入用户ID"
                    style="width: 100%"
                    :min="1"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="4">
                <a-form-item label="交易类型" class="mb-0">
                  <a-select
                    v-model:value="txSearchParams.transactionType"
                    placeholder="请选择类型"
                    allow-clear
                  >
                    <a-select-option
                      v-for="opt in transactionTypeOptions"
                      :key="opt.value"
                      :value="opt.value"
                    >
                      {{ opt.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="开始时间" class="mb-0">
                  <a-date-picker
                    v-model:value="txSearchParams.startTime"
                    show-time
                    format="YYYY-MM-DD HH:mm:ss"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    placeholder="选择开始时间"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="结束时间" class="mb-0">
                  <a-date-picker
                    v-model:value="txSearchParams.endTime"
                    show-time
                    format="YYYY-MM-DD HH:mm:ss"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    placeholder="选择结束时间"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="4" class="text-right mt-16">
                <a-space>
                  <a-button type="primary" @click="handleTxSearch">搜索</a-button>
                  <a-button @click="handleTxReset">重置</a-button>
                </a-space>
              </a-col>
            </a-row>
          </a-form>

          <!-- 操作栏 -->
          <div class="mb-16">
            <a-button type="primary" @click="openAdjustModal">手动调整积分</a-button>
          </div>

          <!-- 表格 -->
          <a-table
            :columns="txColumns"
            :data-source="txDataSource"
            :loading="txLoading"
            :pagination="txPagination"
            @change="handleTxTableChange"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'transactionType'">
                <a-tag :color="transactionTypeMap[record.transactionType]?.color || 'default'">
                  {{ transactionTypeMap[record.transactionType]?.label || record.transactionType }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'amount'">
                <span :style="{ color: record.amount >= 0 ? 'var(--color-success)' : 'var(--color-error)', fontWeight: 600 }">
                  {{ record.amount >= 0 ? '+' : '' }}{{ record.amount }}
                </span>
              </template>
              <template v-else-if="column.key === 'createdAt'">
                {{ formatDateTime(record.createdAt) }}
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- 签到记录 Tab -->
        <a-tab-pane key="checkins" tab="签到记录">
          <!-- 搜索区域 -->
          <a-form layout="horizontal" :model="checkinSearchParams" class="mb-16">
            <a-row :gutter="24">
              <a-col :span="4">
                <a-form-item label="用户ID" class="mb-0">
                  <a-input-number
                    v-model:value="checkinSearchParams.userId"
                    placeholder="请输入用户ID"
                    style="width: 100%"
                    :min="1"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="开始日期" class="mb-0">
                  <a-date-picker
                    v-model:value="checkinSearchParams.startDate"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                    placeholder="选择开始日期"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="结束日期" class="mb-0">
                  <a-date-picker
                    v-model:value="checkinSearchParams.endDate"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                    placeholder="选择结束日期"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="8" class="text-right mt-16">
                <a-space>
                  <a-button type="primary" @click="handleCheckinSearch">搜索</a-button>
                  <a-button @click="handleCheckinReset">重置</a-button>
                </a-space>
              </a-col>
            </a-row>
          </a-form>

          <!-- 表格 -->
          <a-table
            :columns="checkinColumns"
            :data-source="checkinDataSource"
            :loading="checkinLoading"
            :pagination="checkinPagination"
            @change="handleCheckinTableChange"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'pointsEarned'">
                <span style="color: var(--color-success); font-weight: 600">+{{ record.pointsEarned }}</span>
              </template>
              <template v-else-if="column.key === 'consecutiveDays'">
                <a-tag :color="record.consecutiveDays >= 7 ? 'gold' : record.consecutiveDays >= 3 ? 'blue' : 'default'">
                  {{ record.consecutiveDays }} 天
                </a-tag>
              </template>
              <template v-else-if="column.key === 'createdAt'">
                {{ formatDateTime(record.createdAt) }}
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 手动调整积分弹窗 -->
    <a-modal
      v-model:open="adjustModalVisible"
      title="手动调整积分"
      :confirm-loading="adjustLoading"
      @ok="handleAdjustOk"
      @cancel="handleAdjustCancel"
      destroy-on-close
    >
      <a-form :model="adjustForm" :rules="adjustRules" ref="adjustFormRef" layout="vertical">
        <a-form-item name="userId" label="选择用户" required>
          <a-select
            v-model:value="adjustForm.userId"
            placeholder="输入用户名搜索"
            show-search
            :filter-option="false"
            :options="userOptions"
            :loading="userSearchLoading"
            @search="handleUserSearch"
            style="width: 100%"
            allow-clear
          />
        </a-form-item>
        <a-form-item name="amount" label="调整金额" required>
          <a-input-number
            v-model:value="adjustForm.amount"
            placeholder="正数为增加，负数为扣减"
            style="width: 100%"
          />
          <div class="form-tip">正数表示增加积分，负数表示扣减积分</div>
        </a-form-item>
        <a-form-item name="description" label="调整原因">
          <a-textarea
            v-model:value="adjustForm.description"
            placeholder="请输入调整原因（可选）"
            :rows="3"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.mb-0 {
  margin-bottom: 0;
}

.mb-16 {
  margin-bottom: 16px;
}

.mt-16 {
  margin-top: 16px;
}

.text-right {
  text-align: right;
}

.form-tip {
  font-size: 12px;
  color: var(--text-tertiary, #999);
  margin-top: 4px;
}
</style>
