<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '../utils/markdown'

const props = defineProps<{ content: string }>()
const html = computed(() => renderMarkdown(props.content))
</script>

<template>
  <div class="markdown-content" v-html="html"></div>
</template>

<style scoped>
.markdown-content { white-space: normal; overflow-wrap: anywhere; line-height: 1.85; }
.markdown-content :deep(> :first-child) { margin-top: 0; }
.markdown-content :deep(> :last-child) { margin-bottom: 0; }
.markdown-content :deep(p) { margin: 0 0 1em; }
.markdown-content :deep(h1), .markdown-content :deep(h2), .markdown-content :deep(h3),
.markdown-content :deep(h4), .markdown-content :deep(h5), .markdown-content :deep(h6) {
  margin: 1.4em 0 .6em; color: inherit; line-height: 1.4; letter-spacing: normal;
}
.markdown-content :deep(h1) { font-size: 1.75em; }
.markdown-content :deep(h2) { font-size: 1.45em; }
.markdown-content :deep(h3) { font-size: 1.2em; }
.markdown-content :deep(h4), .markdown-content :deep(h5), .markdown-content :deep(h6) { font-size: 1em; }
.markdown-content :deep(ul), .markdown-content :deep(ol) { margin: 0 0 1em; padding-left: 1.6em; }
.markdown-content :deep(li > ul), .markdown-content :deep(li > ol) { margin-bottom: 0; }
.markdown-content :deep(blockquote) { margin: 1em 0; padding: .6em 1em; border-left: 3px solid #99a18c; background: #e7e9df; }
.markdown-content :deep(blockquote > :last-child) { margin-bottom: 0; }
.markdown-content :deep(a) { color: #526c45; text-decoration: underline; text-underline-offset: 3px; }
.markdown-content :deep(img) { display: block; max-width: 100%; height: auto; margin: 1em 0; border-radius: 8px; }
.markdown-content :deep(code) { padding: .15em .35em; border-radius: 4px; background: #e7e9df; font-family: Consolas, monospace; font-size: .9em; }
.markdown-content :deep(pre) { max-width: 100%; margin: 1em 0; padding: 16px; overflow-x: auto; border-radius: 8px; background: #e7e9df; white-space: pre; overflow-wrap: normal; line-height: 1.6; }
.markdown-content :deep(pre code) { padding: 0; background: none; }
.markdown-content :deep(table) { display: block; width: max-content; max-width: 100%; margin: 1em 0; overflow-x: auto; border-collapse: collapse; }
.markdown-content :deep(th), .markdown-content :deep(td) { padding: 8px 12px; border: 1px solid #d7ddd2; }
.markdown-content :deep(th) { background: #e7e9df; }
.markdown-content :deep(hr) { margin: 1.5em 0; border: 0; border-top: 1px solid #d7ddd2; }
</style>
