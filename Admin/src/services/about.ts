import { get, put } from './api'
import type { ApiResponse } from './api'

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

export class AboutPageService {
  private static readonly BASE_URL = '/admin/about'

  static async get(): Promise<ApiResponse<AboutPageInfo>> {
    return get<AboutPageInfo>(this.BASE_URL)
  }

  static async update(content: AboutPageInfo): Promise<ApiResponse<AboutPageInfo>> {
    return put<AboutPageInfo>(this.BASE_URL, content)
  }
}

export default AboutPageService
