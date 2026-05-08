<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { message } from 'ant-design-vue'
import { DownOutlined } from '@ant-design/icons-vue'
import UserService from '../../services/user'
import type { UserListParams, User } from '../../services/user'
import { formatDateTime } from '../../utils/utils'

// 响应式数据
const loading = ref(false)
const dataSource = ref<User[]>([])
const selectedRowKeys = ref<number[]>([])

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条记录`
})

// 搜索参数
const searchParams = ref<UserListParams>({
  username: '',
  email: '',
  status: undefined,
  includeDeleted: false,
  role: undefined
})

// 表格列定义
const columns = [
  {
    title: '用户名',
    dataIndex: 'username',
    key: 'username'
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    key: 'email',
    customRender: ({ text }: { text: string }) => h('div', { class: 'email-cell' }, [
      h('svg', { 
        class: 'email-icon', 
        viewBox: '0 0 24 24', 
        fill: 'none', 
        stroke: 'currentColor', 
        'stroke-width': '2',
        style: { width: '16px', height: '16px', flexShrink: 0 } 
      }, [
        h('path', { d: 'M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z' }),
        h('polyline', { points: '22,6 12,13 2,6' })
      ]),
      h('span', text)
    ])
  },
  {
    title: '昵称',
    dataIndex: 'nickname',
    key: 'nickname'
  },
  {
    title: '角色',
    dataIndex: 'role',
    key: 'role'
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status'
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    key: 'createdAt'
  },
  {
    title: '操作',
    key: 'action'
  }
]

// 状态选项
const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

// 角色选项
const roleOptions = [
  { label: '管理员', value: 'admin' },
  { label: '普通用户', value: 'user' }
]

// =================== 新建/编辑 弹窗与表单 ===================
const modalVisible = ref(false)
const modalTitle = ref('新建用户')
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const confirmLoading = ref(false)

// 使用 Partial<User> 以简化创建/编辑时的必填校验
const formRef = ref()
const formModel = ref<Partial<User>>({
  username: '',
  email: '',
  nickname: '',
  role: 'user',
  status: 1,
  passwordHash: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  email: [{ required: true, message: '请输入邮箱' }]
}

const openCreate = () => {
  isEdit.value = false
  modalTitle.value = '新建用户'
  editingId.value = null
  formModel.value = {
    username: '',
    email: '',
    nickname: '',
    role: 'user',
    status: 1,
    passwordHash: ''
  }
  modalVisible.value = true
}

const openEdit = (record: User) => {
  isEdit.value = true
  modalTitle.value = '编辑用户'
  editingId.value = record.id
  formModel.value = {
    username: record.username,
    email: record.email,
    nickname: record.nickname,
    role: record.role ?? 'user',
    status: record.status,
    // 更新时密码可留空表示不修改
    passwordHash: ''
  }
  modalVisible.value = true
}

const handleOk = async () => {
  try {
    confirmLoading.value = true
    await formRef.value?.validate?.()
    if (isEdit.value) {
      const res = await UserService.updateUser(editingId.value as number, formModel.value as any)
      if (res.code === 200) {
        message.success('更新成功')
        modalVisible.value = false
        loadUsers()
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const res = await UserService.createUser(formModel.value as any)
      if (res.code === 200) {
        message.success('创建成功')
        modalVisible.value = false
        // 创建后返回第一页便于看到新数据
        pagination.current = 1
        loadUsers()
      } else {
        message.error(res.message || '创建失败')
      }
    }
  } catch (e) {
    // 表单校验失败或请求错误
  } finally {
    confirmLoading.value = false
  }
}

const handleCancel = () => {
  modalVisible.value = false
}

// =================== 列表与查询 ===================
// 加载用户列表
const loadUsers = async () => {
  try {
    loading.value = true
    const params = {
      page: pagination.current,
      size: pagination.pageSize,
      ...searchParams.value
    }
    const response = await UserService.getUserList(params)
    if (response.code === 200) {
      dataSource.value = response.data.records
      pagination.total = response.data.total
    } else {
      message.error(response.message || '加载用户列表失败')
    }
  } catch (error) {
    message.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadUsers()
}

// 重置搜索
const handleReset = () => {
  searchParams.value = {
    username: '',
    email: '',
    status: undefined,
    includeDeleted: false,
    role: undefined
  }
  pagination.current = 1
  loadUsers()
}

// 分页变化
const handleTableChange = (p: any) => {
  pagination.current = p.current
  pagination.pageSize = p.pageSize
  loadUsers()
}

// 选择变化
const onSelectChange = (newSelectedRowKeys: number[]) => {
  selectedRowKeys.value = newSelectedRowKeys
}

// 删除用户
const handleDelete = async (id: number) => {
  try {
    const response = await UserService.deleteUser(id)
    if (response.code === 200) {
      message.success('删除成功')
      loadUsers()
    } else {
      message.error(response.message || '删除失败')
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择要删除的用户')
    return
  }

  try {
    const response = await UserService.batchDeleteUsers(selectedRowKeys.value)
    if (response.code === 200) {
      message.success('批量删除成功')
      selectedRowKeys.value = []
      loadUsers()
    } else {
      message.error(response.message || '批量删除失败')
    }
  } catch (error) {
    message.error('批量删除失败')
  }
}

// 恢复用户
const handleRestore = async (id: number) => {
  try {
    const response = await UserService.restoreUser(id)
    if (response.code === 200) {
      message.success('用户恢复成功')
      loadUsers()
    } else {
      message.error(response.message || '恢复失败')
    }
  } catch (error) {
    message.error('恢复失败')
  }
}

// 批量恢复用户
const handleBatchRestore = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择要恢复的用户')
    return
  }

  try {
    const response = await UserService.batchRestoreUsers(selectedRowKeys.value)
    if (response.code === 200) {
      message.success('批量恢复成功')
      selectedRowKeys.value = []
      loadUsers()
    } else {
      message.error(response.message || '批量恢复失败')
    }
  } catch (error) {
    message.error('批量恢复失败')
  }
}

// 批量启用用户
const handleBatchEnable = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择要启用的用户')
    return
  }

  try {
    const response = await UserService.batchUpdateUserStatus(selectedRowKeys.value, true)
    if (response.code === 200) {
      message.success('批量启用成功')
      selectedRowKeys.value = []
      loadUsers()
    } else {
      message.error(response.message || '批量启用失败')
    }
  } catch (error) {
    message.error('批量启用失败')
  }
}

// 批量禁用用户
const handleBatchDisable = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择要禁用的用户')
    return
  }

  try {
    const response = await UserService.batchUpdateUserStatus(selectedRowKeys.value, false)
    if (response.code === 200) {
      message.success('批量禁用成功')
      selectedRowKeys.value = []
      loadUsers()
    } else {
      message.error(response.message || '批量禁用失败')
    }
  } catch (error) {
    message.error('批量禁用失败')
  }
}

// 更新用户状态
const handleStatusChange = async (id: number, status: number) => {
  try {
    // status参数是要设置的目标状态：1表示启用(true)，0表示禁用(false)
    const enabled = status === 1
    const response = await UserService.updateUserStatus(id, enabled)
    if (response.code === 200) {
      message.success(status === 1 ? '用户已启用' : '用户已禁用')
      loadUsers()
    } else {
      message.error(response.message || '状态更新失败')
    }
  } catch (error) {
    message.error('状态更新失败')
  }
}

// 组件挂载时加载数据
onMounted(() => {
  loadUsers()
})
</script>

<template>
  <div class="p-24">
    <!-- 搜索区域 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="4">
            <a-form-item label="用户名" class="mb-0">
              <a-input v-model:value="searchParams.username" placeholder="请输入用户名" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="邮箱" class="mb-0">
              <a-input v-model:value="searchParams.email" placeholder="请输入邮箱" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="角色" class="mb-0">
              <a-select v-model:value="searchParams.role" placeholder="请选择角色" allow-clear>
                <a-select-option v-for="option in roleOptions" :key="option.value" :value="option.value">{{ option.label }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="状态" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear>
                <a-select-option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8" class="text-right mt-16">
            <a-space>
               <a-tooltip title="显示已删除的用户">
                 <a-switch v-model:checked="searchParams.includeDeleted" checked-children="删" un-checked-children="正常" />
               </a-tooltip>
              <a-button type="primary" @click="handleSearch">搜索</a-button>
              <a-button @click="handleReset">重置</a-button>
            </a-space>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 表格区域 -->
    <a-card :bordered="false">
      <template #title>
        <span>用户列表</span>
      </template>
      <template #extra>
        <a-space>
          <a-button type="primary" @click="openCreate">新建用户</a-button>
          <a-button
            danger
            :disabled="selectedRowKeys.length === 0"
            @click="handleBatchDelete"
          >
            批量删除
          </a-button>
          <a-button
            :disabled="selectedRowKeys.length === 0"
            @click="handleBatchRestore"
          >
            批量恢复
          </a-button>
          <a-dropdown>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="handleBatchEnable">批量启用</a-menu-item>
                <a-menu-item @click="handleBatchDisable">批量禁用</a-menu-item>
              </a-menu>
            </template>
            <a-button :disabled="selectedRowKeys.length === 0">
              批量操作 <DownOutlined />
            </a-button>
          </a-dropdown>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{
          selectedRowKeys,
          onChange: onSelectChange
        }"
        @change="handleTableChange"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag v-if="record.deletedAt" color="gray">已删除</a-tag>
            <a-tag v-else-if="record.status === 1" color="green">启用</a-tag>
            <a-tag v-else color="red">禁用</a-tag>
          </template>
          <template v-else-if="column.key === 'role'">
            <a-tag :color="record.role === 'admin' ? 'blue' : 'default'">
              {{ record.role === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <template v-if="!record.deletedAt">
                <a-button
                  type="link"
                  size="small"
                  @click="openEdit(record)"
                >
                  编辑
                </a-button>
                <a-button
                  type="link"
                  size="small"
                  :class="record.status === 1 ? 'text-orange-500' : 'text-green-500'"
                  @click="handleStatusChange(record.id, record.status === 1 ? 0 : 1)"
                >
                  {{ record.status === 1 ? '禁用' : '启用' }}
                </a-button>
                <a-popconfirm
                  title="确定要删除这个用户吗？"
                  @confirm="handleDelete(record.id)"
                >
                  <a-button type="link" size="small" danger>
                    删除
                  </a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm
                  title="确定要恢复这个用户吗？"
                  @confirm="handleRestore(record.id)"
                >
                  <a-button type="link" size="small" success>
                    恢复
                  </a-button>
                </a-popconfirm>
              </template>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 新建/编辑 弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :confirm-loading="confirmLoading"
      @ok="handleOk"
      @cancel="handleCancel"
      destroy-on-close
    >
      <a-form :model="formModel" :rules="rules" ref="formRef" layout="vertical">
        <a-form-item name="username" label="用户名" required>
          <a-input v-model:value="formModel.username" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item name="email" label="邮箱" required>
          <a-input v-model:value="formModel.email" placeholder="请输入邮箱" />
        </a-form-item>
        <a-form-item name="nickname" label="昵称">
          <a-input v-model:value="formModel.nickname" placeholder="请输入昵称" />
        </a-form-item>
        <a-form-item name="role" label="角色">
          <a-select v-model:value="formModel.role" placeholder="请选择角色">
            <a-select-option v-for="opt in roleOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="status" label="状态">
          <a-radio-group v-model:value="formModel.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">禁用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="!isEdit" name="passwordHash" label="密码">
          <a-input-password v-model:value="formModel.passwordHash" placeholder="请输入密码(仅创建时)" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.users-management {
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--text-main);
}

.search-card,
.action-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.email-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.email-icon {
  width: 16px;
  height: 16px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}
</style>