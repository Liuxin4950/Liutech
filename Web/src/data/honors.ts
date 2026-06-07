import softwareTestingFirstPlace from '@/assets/image/honors/职业技能大赛重庆市选拔赛软件测试项目-第一名.jpg'
import webAppFirstPrize from '@/assets/image/honors/Web应用开发-一等奖.jpg'
import wechatMiniappFirstPrize from '@/assets/image/honors/微信小程序开发赛项-一等奖.jpg'
import lowcodeNvwaFirstPrize from '@/assets/image/honors/低代码女娲杯-一等奖.jpg'
import mobileAppSecondPrize from '@/assets/image/honors/移动应用开发-二等奖.jpg'
import appSoftwareThirdPrize from '@/assets/image/honors/应用软件开发-三等奖.jpg'
import appSoftwareBronzeAward from '@/assets/image/honors/应用软件开发-铜奖.jpg'
import textAppExcellenceAward from '@/assets/image/honors/文本应用开发-优秀奖.jpg'
import cProgrammingEngineerCert from '@/assets/image/honors/C语言程序设计工程师证书.jpg'

import webFrontendSecondPrize from '@/assets/image/honors/中职技能大赛Web前端开发-二等奖.jpg'
import mobileAppNationalSecondPrize from '@/assets/image/honors/全国职业院校技能大赛移动应用与开发-二等奖.jpg'
import bricsDataVizThirdPrize from '@/assets/image/honors/金砖国家技能大赛数据分析与可视化-三等奖.jpg'

export type HonorCategory = 'all' | 'dev' | 'test' | 'miniapp' | 'lowcode' | 'cert'

export interface HonorItem {
  id: string
  title: string
  level: string
  year: string
  category: Exclude<HonorCategory, 'all'>
  image: string
}

export const honorCategories: Array<{ label: string; value: HonorCategory }> = [
  { label: '全部', value: 'all' },
  { label: '软件开发', value: 'dev' },
  { label: '软件测试', value: 'test' },
  { label: '小程序', value: 'miniapp' },
  { label: '低代码', value: 'lowcode' },
  { label: '技术认证', value: 'cert' }
]

export const honors: HonorItem[] = [
  {
    id: 'software-testing-first-place',
    title: '职业技能大赛重庆市选拔赛软件测试项目',
    level: '第一名',
    year: '2026',
    category: 'test',
    image: softwareTestingFirstPlace
  },
  {
    id: 'web-app-first-prize',
    title: 'Web 应用开发',
    level: '一等奖',
    year: '2026',
    category: 'dev',
    image: webAppFirstPrize
  },
  {
    id: 'wechat-miniapp-first-prize',
    title: '微信小程序开发赛项',
    level: '一等奖',
    year: '2026',
    category: 'miniapp',
    image: wechatMiniappFirstPrize
  },
  {
    id: 'lowcode-nvwa-first-prize',
    title: '低代码女娲杯',
    level: '一等奖',
    year: '2025',
    category: 'lowcode',
    image: lowcodeNvwaFirstPrize
  },
  {
    id: 'mobile-app-second-prize',
    title: '移动应用开发',
    level: '二等奖',
    year: '2025',
    category: 'dev',
    image: mobileAppSecondPrize
  },
  {
    id: 'c-programming-engineer-cert',
    title: 'C 语言程序设计工程师证书',
    level: '工程师证书',
    year: '2025',
    category: 'cert',
    image: cProgrammingEngineerCert
  },
  {
    id: 'app-software-third-prize',
    title: '应用软件开发',
    level: '三等奖',
    year: '2025',
    category: 'dev',
    image: appSoftwareThirdPrize
  },
  {
    id: 'app-software-bronze-award',
    title: '应用软件开发',
    level: '铜奖',
    year: '2025',
    category: 'dev',
    image: appSoftwareBronzeAward
  },
  {
    id: 'text-app-excellence-award',
    title: '文本应用开发',
    level: '优秀奖',
    year: '2026',
    category: 'dev',
    image: textAppExcellenceAward
  },
  {
    id: 'web-frontend-second-prize',
    title: '重庆市第十三届中职技能大赛 Web 前端开发',
    level: '二等奖',
    year: '2021',
    category: 'dev',
    image: webFrontendSecondPrize
  },
  {
    id: 'mobile-app-national-second-prize',
    title: '全国职业院校技能大赛中职组 移动应用与开发',
    level: '团体二等奖',
    year: '2023',
    category: 'dev',
    image: mobileAppNationalSecondPrize
  },
  {
    id: 'brics-data-viz-third-prize',
    title: '一带一路暨金砖国家技能发展与技术创新大赛 数据分析与可视化',
    level: '团体三等奖',
    year: '2021',
    category: 'dev',
    image: bricsDataVizThirdPrize
  }
]
