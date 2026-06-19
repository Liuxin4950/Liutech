import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/__tests__/setup.ts'],
    include: ['src/__tests__/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      include: [
        'src/services/post.ts',
        'src/services/user.ts',
        'src/services/ai.ts',
        'src/stores/user.ts',
        'src/stores/chat.ts',
        'src/stores/category.ts',
        'src/composables/useImageFallback.ts',
        'src/composables/useMarkdown.ts',
        'src/components/TheHeader.vue',
        'src/components/Pagination.vue',
        'src/components/CommentForm.vue'
      ]
    }
  }
})
