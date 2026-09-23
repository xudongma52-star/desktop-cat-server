<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getRecords, recordTypeLabels, type RecordListItem } from '../api/records'
import { describeApiError } from '../api/http'
import { useAuthStore } from '../stores/auth'
import PhotoCarousel from '../components/PhotoCarousel.vue'
import ArticleRecallCarousel from '../components/ArticleRecallCarousel.vue'
import DuskWritingScene from '../components/DuskWritingScene.vue'

const auth = useAuthStore()
const records = ref<RecordListItem[]>([])
const loading = ref(true)
const error = ref('')
const dateLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
async function loadRecent() {
  loading.value = true
  error.value = ''
  try { records.value = (await getRecords(1, 3)).items }
  catch (caught) { error.value = describeApiError(caught, '暂时没有读到记录，请再试一次。') }
  finally { loading.value = false }
}
onMounted(loadRecent)
</script>

<template>
  <div class="home-view dusk-home">
    <div class="edition-line"><span>生活手记 · 猫的角落</span><span>{{ dateLabel }}</span></div>
    <section class="dusk-hero" aria-labelledby="dusk-title">
      <div class="dusk-copy">
        <p class="eyebrow">日记、随笔，和一些日常</p>
        <h1 id="dusk-title">窗边，<br /><span>写一会儿。</span></h1>
        <p class="dusk-description">欢迎回来，{{ auth.user?.username }}。</p>
        <div class="dusk-actions"><RouterLink class="button prominent" to="/records/new">写下今天 <span aria-hidden="true">↗</span></RouterLink><RouterLink class="quiet-link" to="/records">查看记录 →</RouterLink></div>
        <div class="hero-footnote"><span class="fine-rule"></span> 不急，从一句话开始。</div>
      </div>
      <DuskWritingScene />
    </section>
    <nav class="daily-paths" aria-label="日常入口">
      <RouterLink to="/emotions"><span class="path-number">01</span><div><strong>此刻的心情</strong><span>记一句今天的感受</span></div><span aria-hidden="true">↗</span></RouterLink>
      <RouterLink to="/knowledge"><span class="path-number">02</span><div><strong>灵感与知识</strong><span>从自己的笔记里找答案</span></div><span aria-hidden="true">↗</span></RouterLink>
      <RouterLink to="/reminders"><span class="path-number">03</span><div><strong>惦记的小事</strong><span>查看和安排提醒</span></div><span aria-hidden="true">↗</span></RouterLink>
    </nav>
    <section class="journal-section" aria-labelledby="recent-title">
      <div class="journal-heading"><div><p class="eyebrow">手记</p><h2 id="recent-title">最近写下的</h2></div><RouterLink class="quiet-link" to="/records">全部记录 ↗</RouterLink></div>
      <p v-if="loading" class="journal-state" role="status">正在翻开你的记录…</p>
      <div v-else-if="error" class="journal-state" role="alert"><p>{{ error }}</p><button @click="loadRecent">重新加载</button></div>
      <div v-else-if="!records.length" class="journal-state"><p>第一张书页，正等着你的故事。</p><RouterLink class="text-link" to="/records/new">写下第一篇 →</RouterLink></div>
      <div v-else class="journal-entries"><RouterLink v-for="(record, index) in records" :key="record.recordId" :to="'/records/' + record.recordId" class="journal-entry"><span class="entry-index">0{{ index + 1 }}</span><div class="entry-copy"><p class="entry-meta">{{ recordTypeLabels[record.recordType] }} · {{ record.recordDate }}</p><h3>{{ record.title || '无题，也是生活' }}</h3><p class="entry-excerpt">{{ record.excerpt || record.content || '打开这一页，看看那天的故事。' }}</p></div><span class="entry-arrow" aria-hidden="true">↗</span></RouterLink></div>
    </section>
    <div class="memory-heading"><span>偶尔，翻翻以前。</span><span>照片与旧文</span></div>
    <div class="memory-spread"><PhotoCarousel /><ArticleRecallCarousel /></div>
    <div class="closing-note"><RouterLink to="/cottage">我的小屋 →</RouterLink></div>
  </div>
</template>
