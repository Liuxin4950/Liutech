import { useHead } from '@vueuse/head'
import { useRoute } from 'vue-router'
import { site } from '@/config/site'

/** 根组件提供页面默认 SEO；文章详情用同一 head 管理器覆盖其内容字段。 */
export function usePageSeo() {
  const route = useRoute()
  useHead(() => {
    const privateNames = ['login', 'forgot-password', 'profile', 'favorites', 'view-history', 'create-post', 'my-posts', 'drafts', 'ai-chat-full', 'not-found']
    const isPrivate = privateNames.includes(String(route.name))
    const title = route.name === 'home' ? `${site.name} - 个人技术博客` : `${route.meta.title || '博客'} - ${site.name}`
    const description = route.name === 'home' ? '分享编程技术、全栈开发、AI 应用与软件工程实践。' : `浏览 ${site.name} 的${route.meta.title || '技术文章'}，阅读文章与系列内容。`
    const url = site.url + route.path
    return {
      title,
      meta: [
        { name: 'description', content: description },
        { name: 'robots', content: isPrivate ? 'noindex, follow' : 'index, follow' },
        { property: 'og:type', content: 'website' }, { property: 'og:url', content: url },
        { property: 'og:title', content: title }, { property: 'og:description', content: description },
        { property: 'og:image', content: `${site.url}/og-image.svg` },
        { name: 'twitter:title', content: title }, { name: 'twitter:description', content: description },
        { name: 'twitter:image', content: `${site.url}/og-image.svg` },
      ],
      link: [{ rel: 'canonical', href: url }],
    }
  })
}
