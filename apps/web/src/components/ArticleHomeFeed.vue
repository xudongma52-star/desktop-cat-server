<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getHomeArticles } from '../api/records'
import type { HomeArticle } from '../api/records'
import { describeApiError } from '../api/http'
import HomeArticlePair from './HomeArticlePair.vue'

const articles = ref<HomeArticle[]>([])
const loading = ref(false)
const error = ref('')
const hasMore = ref(true)
const sentinel = ref<HTMLElement | null>(null)
const abort = new AbortController()
let page = 1
let observer: IntersectionObserver | undefined
let disposed = false

async function loadNext() {
  if (loading.value || !hasMore.value || disposed) return
  loading.value = true
  error.value = ''
  try {
    const result = await getHomeArticles(page, abort.signal)
    if (disposed) return
    const seen = new Set(articles.value.map((article) => article.recordId))
    articles.value.push(...result.items.filter((article) => !seen.has(article.recordId)))
    hasMore.value = result.hasMore
    page += 1
  } catch (caught) {
    if (!disposed) error.value = describeApiError(caught, '文章暂时没有加载成功。')
  } finally {
    if (!disposed) loading.value = false
  }
}

onMounted(async () => {
  await loadNext()
  await nextTick()
  if (disposed || !sentinel.value) return
  observer = new IntersectionObserver(([entry]) => {
    if (entry.isIntersecting && !error.value) void loadNext()
  }, { rootMargin: '0px 0px 500px 0px' })
  observer.observe(sentinel.value)
})

onBeforeUnmount(() => { disposed = true; abort.abort(); observer?.disconnect() })
</script>

<template>
  <section class="article-home-feed" aria-label="图文回忆">
    <HomeArticlePair v-for="(article, index) in articles" :key="article.recordId" :article="article" :layout="index % 4" />
    <div ref="sentinel" class="article-feed-sentinel">
      <span v-if="loading" role="status">加载中…</span>
      <button v-else-if="error" type="button" @click="loadNext">{{ error }} 重试</button>
      <RouterLink v-else-if="!articles.length" to="/records">选择首页文章 ↗</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.article-home-feed { padding: 30px 0 80px; background: transparent; }
.article-feed-sentinel { min-height: 24px; text-align: center; color: #53594c; font-size: 12px; }
.article-feed-sentinel button { background: transparent; color: inherit; }
</style>
