import { get } from './api'

export interface AboutAuthor {
  name: string
  title: string
  avatar: string
  bio: string
}

export interface AboutSocialLink {
  label: string
  value: string
  href: string
}

export interface AboutSkillGroup {
  category: string
  skills: string[]
}

export interface AboutProject {
  name: string
  description: string
  technologies: string[]
  link?: string | null
}

export interface AboutHonors {
  summary: string
  imageUrl?: string | null
}

export interface AboutPageInfo {
  author: AboutAuthor
  motto: string
  introParagraphs: string[]
  socialLinks: AboutSocialLink[]
  skillGroups: AboutSkillGroup[]
  projects: AboutProject[]
  honors: AboutHonors
  contactText: string
  bannerDescription: string
  metaDescription: string
}

export async function getAboutPage(): Promise<AboutPageInfo> {
  const response = await get<AboutPageInfo>('/about')
  return response.data
}
