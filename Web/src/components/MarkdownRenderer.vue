<template>
  <div
    ref="contentRef"
    v-html="renderedContent"
    class="markdown-content"
    :class="{ 'streaming': isStreaming }"
    @click="onContentClick"
  ></div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import css from 'highlight.js/lib/languages/css'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import markdown from 'highlight.js/lib/languages/markdown'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('java', java)
hljs.registerLanguage('python', python)
hljs.registerLanguage('css', css)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('markdown', markdown)
import { useMarkdown } from '@/composables/useMarkdown'
import { useRouter } from 'vue-router'
import 'highlight.js/styles/github-dark.css'

interface Props {
  content: string
  isStreaming?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isStreaming: false
})

const { processMarkdown } = useMarkdown()
const contentRef = ref<HTMLElement | null>(null)
const router = useRouter()

const appendStreamingCaret = (html: string) => {
  if (!html) {
    return '<span class="streaming-caret" aria-hidden="true"></span>'
  }

  const container = document.createElement('div')
  container.innerHTML = html

  const candidates = container.querySelectorAll('p, li, blockquote, h1, h2, h3, h4, h5, h6, td, th, code')
  const target = candidates[candidates.length - 1]
  const caret = document.createElement('span')
  caret.className = 'streaming-caret'
  caret.setAttribute('aria-hidden', 'true')

  if (target) {
    target.appendChild(caret)
  } else {
    container.appendChild(caret)
  }

  return container.innerHTML
}

const renderedContent = computed(() => {
  if (!props.content) return ''
  const html = processMarkdown(props.content, props.isStreaming)
  return props.isStreaming ? appendStreamingCaret(html) : html
})

// Watch for rendered content changes to re-highlight code blocks
watch(renderedContent, async () => {
  await nextTick()
  highlightCodeBlocks()
}, { immediate: true })

watch(() => props.isStreaming, async (streaming) => {
  if (streaming) return
  await nextTick()
  contentRef.value?.querySelectorAll('.streaming-caret').forEach((node) => node.remove())
})

const onContentClick = (e: MouseEvent) => {
  const anchor = (e.target as HTMLElement).closest('a[href^="/"]') as HTMLAnchorElement | null
  if (anchor) {
    e.preventDefault()
    router.push(anchor.getAttribute('href') || '/')
  }
}

const highlightCodeBlocks = () => {
  const codeBlocks = contentRef.value?.querySelectorAll('pre code') || []
  codeBlocks.forEach((block) => {
    // Only highlight if not already highlighted
    if (!block.classList.contains('hljs')) {
      hljs.highlightElement(block as HTMLElement)
    }
  })
}
</script>

<style scoped>
.markdown-content {
  line-height: 1.6;
  word-wrap: break-word;
}

.markdown-content.streaming {
  animation: fadeIn 0.3s ease-in-out;
}

.markdown-content :deep(p),
.markdown-content :deep(ul),
.markdown-content :deep(ol),
.markdown-content :deep(blockquote),
.markdown-content :deep(pre),
.markdown-content :deep(table),
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 0;
}

.markdown-content :deep(p + p),
.markdown-content :deep(p + ul),
.markdown-content :deep(p + ol),
.markdown-content :deep(p + blockquote),
.markdown-content :deep(ul + p),
.markdown-content :deep(ol + p),
.markdown-content :deep(blockquote + p),
.markdown-content :deep(pre + p),
.markdown-content :deep(p + pre),
.markdown-content :deep(ul + ul),
.markdown-content :deep(ol + ol),
.markdown-content :deep(ul + ol),
.markdown-content :deep(ol + ul) {
  margin-top: 0.75rem;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 1.2rem;
}

.markdown-content :deep(li + li) {
  margin-top: 0.35rem;
}

.markdown-content :deep(pre) {
  margin-top: 0.75rem;
  overflow-x: auto;
  border-radius: 12px;
}

.markdown-content :deep(blockquote) {
  margin-top: 0.75rem;
  padding-left: 0.9rem;
  border-left: 3px solid rgba(59, 130, 246, 0.24);
}

.markdown-content :deep(.streaming-caret) {
  display: inline-block;
  width: 0.55rem;
  height: 1.1em;
  margin-left: 0.16rem;
  vertical-align: -0.12em;
  border-radius: 999px;
  background: currentColor;
  animation: caretBlink 1s step-end infinite;
}

@keyframes fadeIn {
  from {
    opacity: 0.95;
  }
  to {
    opacity: 1;
  }
}

@keyframes caretBlink {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0;
  }
}


.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  font-size: 1em;
  font-weight: 600;
  margin-top: 0.5rem;
  margin-bottom: 0.25rem;
}

.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  font-size: 0.95em;
  font-weight: 600;
  margin-top: 0.4rem;
  margin-bottom: 0.2rem;
}

/* Base markdown styles will be imported from markdown.css */
</style>
