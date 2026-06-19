<script setup lang="ts">
import { h } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, DownOutlined } from '@ant-design/icons-vue'
import UserService from '../../services/user'
import type { User } from '../../services/user'
import { formatDateTime } from '../../utils/utils'
import { useTablePage, useCrudActions, useModalForm } from '@/composables'

interface UserFormData {
  username: string
  email: string
  nickname?: string
  role?: string
  status?: number
  passwordHash?: string
}

const {
  loading,
  dataSource,
  selectedRowKeys,
  searchParams,
  pagination,
  load,
  handleSearch,
  handleReset,
  handleTableChange,
  onSelectChange,
  clearSelection
} = useTablePage<User, {
  username: string
  email: string
  status: number | undefined
  includeDeleted: boolean
  role: string | undefined
}>({
  loadFn: async (params) => UserService.getUserList(params),
  defaultSearchParams: {
    username: '',
    email: '',
    status: undefined,
    includeDeleted: false,
    role: undefined
  },
  loadErrorMessage: '加载用户列表失败'
})

const {
  handleDelete,
  handleBatchDelete,
  handleRestore,
  handlePermanentDelete
} = useCrudActions({
  deleteFn: (id) => UserService.deleteUser(id),
  batchDeleteFn: (ids) => UserService.batchDeleteUsers(ids),
  restoreFn: (id) => UserService.restoreUser(id),
  permanentDeleteFn: (id) => UserService.permanentDeleteUser(id),
  onRefresh: load,
  clearSelection,
  entityName: '用户'
})

const {
  modalVisible,
  modalTitle,
  isEdit,
  confirmLoading,
  formRef,
  formModel,
  openCreate,
  openEdit,
  handleOk,
  handleCancel
} = useModalForm<UserFormData>({
  createFn: (data) => UserService.createUser(data) as any,
  updateFn: (id, data) => UserService.updateUser(id, data) as any,
  onCreateSuccess: () => {
    pagination.current = 1
    load()
  },
  onUpdateSuccess: load,
  defaultForm: () => ({
    username: '',
    email: '',
    nickname: '',
    role: 'user',
    status: 1,
    passwordHash: ''
  }),
  entityName: '用户'
})

const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  email: [{ required: true, message: '请输入邮箱' }],
  passwordHash: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

const roleOptions = [
  { label: '管理员', value: 'admin' },
  { label: '普通用户', value: 'user' }
]

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
    customRender: ({ text }: { text: string }) => h('div', { style: { display: 'flex', alignItems: 'center', gap: '8px' } }, [
      h('svg', {
        viewBox: '0 0 24 24',
        fill: 'none',
        stroke: 'currentColor',
        'stroke-width': '2',
        style: { width: '16px', height: '16px', flexShrink: 0, color: 'var(--text-tertiary)' }
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
    key: 'action',
    width: 200,
    fixed: 'right' as const
  }
]

const handleBatchRestore = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择要恢复的用户')
    return
  }

  try {
    const response = await UserService.batchRestoreUsers(selectedRowKeys.value)
    if (response.code === 200) {
      message.success('批量恢复成功')
      clearSelection()
      load()
    } else {
      message.error(response.message || '批量恢复失败')
    }
  } catch {
    message.error('批量恢复失败')
  }
}

const handleStatusChange = async (id: number, newStatus: number) => {
  try {
    const response = await UserService.updateUserStatus(id, newStatus === 1)
    if (response.code === 200) {
      message.success(newStatus === 1 ? '已启用' : '已禁用')
      load()
    } else {
      message.error(response.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  }
}

const handleBatchEnable = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择要启用的用户')
    return
  }

  try {
    const response = await UserService.batchUpdateUserStatus(selectedRowKeys.value, true)
    if (response.code === 200) {
      message.success('批量启用成功')
      clearSelection()
      load()
    } else {
      message.error(response.message || '批量启用失败')
    }
  } catch {
    message.error('批量启用失败')
  }
}

const handleBatchDisable = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择要禁用的用户')
    return
  }

  try {
    const response = await UserService.batchUpdateUserStatus(selectedRowKeys.value, false)
    if (response.code === 200) {
      message.success('批量禁用成功')
      clearSelection()
      load()
    } else {
      message.error(response.message || '批量禁用失败')
    }
  } catch {
    message.error('批量禁用失败')
  }
}

</script>

<template>
  <div class="p-24">
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
              <a-tooltip title="显示已删除">
                <a-switch v-model:checked="searchParams.includeDeleted" @change="handleSearch" checked-children="删" un-checked-children="正常" />
              </a-tooltip>
              <a-button type="primary" @click="handleSearch"><template #icon><SearchOutlined /></template>搜索</a-button>
              <a-button @click="handleReset"><template #icon><ReloadOutlined /></template>重置</a-button>
            </a-space>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <a-card :bordered="false">
      <template #title><span>用户列表</span></template>
      <template #extra>
        <a-space>
          <a-button type="primary" @click="openCreate">新建用户</a-button>
          <a-popconfirm title="确定批量删除选中的用户吗？" @confirm="handleBatchDelete(selectedRowKeys)">
            <a-button danger :disabled="selectedRowKeys.length === 0">批量删除</a-button>
          </a-popconfirm>
          <a-button :disabled="selectedRowKeys.length === 0" @click="handleBatchRestore">批量恢复</a-button>
          <a-dropdown>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="handleBatchEnable">批量启用</a-menu-item>
                <a-menu-item @click="handleBatchDisable">批量禁用</a-menu-item>
              </a-menu>
            </template>
            <a-button :disabled="selectedRowKeys.length === 0">批量操作 <DownOutlined /></a-button>
          </a-dropdown>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        :scroll="{ x: 1200 }"
        @change="handleTableChange"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
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
                <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
                <a-button type="link" size="small" :class="record.status === 1 ? 'text-orange-500' : 'text-green-500'" @click="handleStatusChange(record.id, record.status === 1 ? 0 : 1)">{{ record.status === 1 ? '禁用' : '启用' }}</a-button>
                <a-popconfirm title="确定删除该用户吗？" @confirm="handleDelete(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm title="确定恢复该用户吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm title="确定要彻底删除该用户吗？此操作不可恢复！" ok-text="确定" cancel-text="取消" @confirm="handlePermanentDelete(record.id)">
                  <a-button type="link" size="small" danger>彻底删除</a-button>
                </a-popconfirm>
              </template>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalVisible" :title="modalTitle" :confirm-loading="confirmLoading" @ok="handleOk" @cancel="handleCancel" destroy-on-close>
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
            <a-select-option v-for="opt in roleOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="status" label="状态">
          <a-radio-group v-model:value="formModel.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">禁用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="!isEdit" name="passwordHash" label="密码" required>
          <a-input-password v-model:value="formModel.passwordHash" placeholder="请输入密码(仅创建时)" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>



