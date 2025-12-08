<template>
  <div
    v-html="renderedContent"
    class="markdown-content"
    :class="{ 'streaming': isStreaming }"
  ></div>
</template>

<script setup lang="ts">
import { computed, watch, ref, onMounted } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import { useMarkdown } from '@/composables/useMarkdown'
// Import highlight.js CSS
import 'highlight.js/styles/github-dark.css'

interface Props {
  content: string
  isStreaming?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isStreaming: false
})

const { processMarkdown } = useMarkdown()

const renderedContent = computed(() => {
  if (!props.content) return ''
  return processMarkdown(props.content, props.isStreaming)
})

// Watch for content changes to re-highlight code blocks
watch(() => props.content, () => {
  // Use nextTick to ensure DOM is updated before highlighting
  setTimeout(() => {
    highlightCodeBlocks()
  }, 0)
}, { immediate: true })

const highlightCodeBlocks = () => {
  const codeBlocks = document.querySelectorAll('.markdown-content pre code')
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

@keyframes fadeIn {
  from {
    opacity: 0.95;
  }
  to {
    opacity: 1;
  }
}

/* Base markdown styles will be imported from markdown.css */
</style>