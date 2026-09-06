/** 公开站点资料。备案信息仅展示已配置的真实资料，不生成占位编号。 */
export const site = {
  name: 'LiuTech',
  url: 'https://liuxin.chat',
  owner: 'liuxin',
  icpNumber: import.meta.env.VITE_ICP_NUMBER?.trim() || '',
  policeNumber: import.meta.env.VITE_POLICE_NUMBER?.trim() || '',
  policeCode: import.meta.env.VITE_POLICE_CODE?.trim() || '',
}
