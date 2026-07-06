<script setup lang="ts">
import { DownloadOutlined, FileExcelOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import type { useTableExport } from '@/composables/useTableExport'

/**
 * 表格导出按钮：CSV / JSON 下拉
 * ---------------------------------------------------------------------
 * <TableExportButton :ctrl="exportCtrl" />
 *
 * 使用当前列偏好（列显隐、顺序）导出，只导当前页数据。
 */

type Ctrl = ReturnType<typeof useTableExport>
defineProps<{ ctrl: Ctrl }>()
</script>

<template>
  <a-dropdown :trigger="['click']">
    <template #overlay>
      <a-menu>
        <a-menu-item key="csv" @click="ctrl.exportCsv">
          <FileExcelOutlined /> 导出 CSV（Excel）
        </a-menu-item>
        <a-menu-item key="json" @click="ctrl.exportJson">
          <FileTextOutlined /> 导出 JSON
        </a-menu-item>
      </a-menu>
    </template>
    <a-tooltip :title="`导出当前页 ${ctrl.rowCount.value} 条`">
      <a-button type="text" size="small">
        <DownloadOutlined />
      </a-button>
    </a-tooltip>
  </a-dropdown>
</template>
