<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  classifyCaptureDay,
  generateCaptureArticle,
  getDailyCaptures,
  saveCaptureArticle,
  saveCaptureDirectly,
} from '../api/captures'
import type { CaptureArticleDraft, CaptureDailyPage, CaptureItem } from '../api/captures'
import { describeApiError } from '../api/http'

const router = useRouter()
const daily = ref<CaptureDailyPage>({ date: null, items: [], page: 1, totalDays: 0 })
const selectedIds = ref<string[]>([])
const knowledgeIds = ref<string[]>([])
const loading = ref(true)
const classifying = ref(false)
const actionId = ref<string | null>(null)
const generating = ref(false)
const savingArticle = ref(false)
const error = ref('')
const notice = ref('')
const articleDraft = ref<CaptureArticleDraft | null>(null)
const articleRagEnabled = ref(false)
const imageNotes = ref<Record<string, string>>({})
let generation = 0

const unclassifiedCount = computed(() => daily.value.items.filter((item) => !item.classificationTarget).length)
const pendingRecords = computed(() => daily.value.items.filter(
  (item) => item.classificationTarget === 'RECORD' && !item.recordResolution,
))

function formatDate(value: string | null): string {
  if (!value) return '还没有碎片'
  return new Date(`${value}T12:00:00`).toLocaleDateString('zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric', weekday: 'short',
  })
}

function formatTime(value: string): string {
  return new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function checked(values: string[], id: string): boolean {
  return values.includes(id)
}

function toggle(values: string[], id: string): void {
  const index = values.indexOf(id)
  if (index >= 0) values.splice(index, 1)
  else values.push(id)
}

async function loadDay(page = daily.value.page): Promise<void> {
  const current = ++generation
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await getDailyCaptures(page)
    if (current !== generation) return
    daily.value = result
    selectedIds.value = []
    knowledgeIds.value = []
    imageNotes.value = {}
    articleDraft.value = null
  } catch (caught) {
    if (current === generation) error.value = describeApiError(caught, '这一天的碎片暂时无法读取。')
  } finally {
    if (current === generation) loading.value = false
  }
}

async function classifyDay(): Promise<void> {
  if (!daily.value.date || classifying.value || unclassifiedCount.value === 0) return
  classifying.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await classifyCaptureDay(daily.value.date)
    await loadDay(daily.value.page)
    notice.value = `已完成 ${result.classifiedCount} 条碎片的分类。`
  } catch (caught) {
    error.value = describeApiError(caught, 'AI 分类暂时没有完成，未分类碎片会保留。')
  } finally {
    classifying.value = false
  }
}

async function saveDirect(item: CaptureItem): Promise<void> {
  if (actionId.value) return
  actionId.value = item.captureId
  error.value = ''
  try {
    const record = await saveCaptureDirectly(
      item.captureId,
      checked(knowledgeIds.value, item.captureId),
      imageNotes.value[item.captureId],
    )
    await router.push(`/records/${record.recordId}`)
  } catch (caught) {
    error.value = describeApiError(caught, '没有保存成正式记录，请稍后再试。')
  } finally {
    actionId.value = null
  }
}

async function createDraft(): Promise<void> {
  if (generating.value || selectedIds.value.length === 0) return
  generating.value = true
  error.value = ''
  try {
    articleDraft.value = await generateCaptureArticle(selectedIds.value)
  } catch (caught) {
    error.value = describeApiError(caught, 'AI 暂时没有整理成功，原始碎片没有变化。')
  } finally {
    generating.value = false
  }
}

async function persistArticle(): Promise<void> {
  if (!articleDraft.value || savingArticle.value) return
  savingArticle.value = true
  error.value = ''
  try {
    const record = await saveCaptureArticle({ ...articleDraft.value, ragEnabled: articleRagEnabled.value })
    await router.push(`/records/${record.recordId}`)
  } catch (caught) {
    error.value = describeApiError(caught, '文章没有保存成功，草稿仍保留在页面上。')
  } finally {
    savingArticle.value = false
  }
}

onMounted(() => void loadDay(1))
</script>

<template>
  <div class="organize-page">
    <header class="page-heading">
      <p class="eyebrow">ORGANIZE CAPTURES</p>
      <h1>按天整理碎片</h1>
      <p>Java 只把当天未分类的内容交给 AI；已经分类的碎片不会再次参与。</p>
    </header>

    <p v-if="error" class="alert error" role="alert">{{ error }}</p>
    <p v-if="notice" class="alert success" role="status">{{ notice }}</p>

    <section class="day-panel">
      <div class="day-heading">
        <button type="button" :disabled="loading || daily.page >= daily.totalDays" @click="loadDay(daily.page + 1)">← 更早一天</button>
        <div><span>第 {{ daily.totalDays ? daily.page : 0 }} / {{ daily.totalDays }} 天</span><h2>{{ formatDate(daily.date) }}</h2></div>
        <button type="button" :disabled="loading || daily.page <= 1" @click="loadDay(daily.page - 1)">较新一天 →</button>
      </div>
      <div class="classify-row">
        <span>未分类 {{ unclassifiedCount }} 条</span>
        <button type="button" :disabled="classifying || unclassifiedCount === 0" @click="classifyDay">
          {{ classifying ? 'AI 分类中…' : '分类这一天' }}
        </button>
      </div>
    </section>

    <p v-if="loading" class="state">正在读取这一天…</p>
    <p v-else-if="daily.items.length === 0" class="state">还没有可以整理的碎片。</p>
    <section v-else class="capture-grid">
      <article v-for="item in daily.items" :key="item.captureId" class="capture-card">
        <div class="card-meta">
          <time :datetime="item.capturedAt">{{ formatTime(item.capturedAt) }}</time>
          <span v-if="!item.classificationTarget" class="chip pending">未分类</span>
          <span v-else-if="item.classificationTarget === 'EMOTION'" class="chip emotion">心情</span>
          <span v-else class="chip record">记录</span>
          <span v-if="item.recordResolution" class="chip done">已写入</span>
        </div>
        <p v-if="item.content" class="capture-content">{{ item.content }}</p>
        <a v-if="item.imageUrl" :href="item.imageUrl" target="_blank" rel="noopener">
          <img :src="item.imageUrl" alt="原始碎片图片" />
        </a>
        <div v-if="item.classificationTarget === 'RECORD' && !item.recordResolution" class="record-actions">
          <label v-if="!item.content" class="image-note">图片记录说明<textarea v-model="imageNotes[item.captureId]" rows="3" maxlength="10000" required></textarea></label>
          <label><input type="checkbox" :checked="checked(selectedIds, item.captureId)" @change="toggle(selectedIds, item.captureId)" /> 加入本次 AI 整理</label>
          <label><input type="checkbox" :checked="checked(knowledgeIds, item.captureId)" @change="toggle(knowledgeIds, item.captureId)" /> 保存后加入知识库</label>
          <button type="button" :disabled="!!actionId" @click="saveDirect(item)">
            {{ actionId === item.captureId ? '保存中…' : '原文直接保存' }}
          </button>
        </div>
      </article>
    </section>

    <section v-if="pendingRecords.length" class="article-panel">
      <div class="article-heading">
        <div><h2>整理成一篇文章</h2><p>已选择 {{ selectedIds.length }} 条记录碎片。</p></div>
        <button type="button" :disabled="generating || selectedIds.length === 0" @click="createDraft">
          {{ generating ? '整理中…' : 'AI 润色总结' }}
        </button>
      </div>
      <form v-if="articleDraft" class="article-form" @submit.prevent="persistArticle">
        <label>标题<input v-model="articleDraft.title" maxlength="120" required /></label>
        <label>记录日期<input v-model="articleDraft.recordDate" type="date" required /></label>
        <label>正文<textarea v-model="articleDraft.content" rows="16" required></textarea></label>
        <label class="knowledge-option"><input v-model="articleRagEnabled" type="checkbox" /> 保存后加入知识库</label>
        <button type="submit" :disabled="savingArticle">{{ savingArticle ? '保存中…' : '确认保存文章' }}</button>
      </form>
    </section>
  </div>
</template>

<style scoped>
.organize-page { max-width: 920px; margin: 0 auto; padding: 36px 20px 80px; color: #493d35; }
.page-heading { margin-bottom: 24px; }.eyebrow { color: #9b735b; letter-spacing: .12em; }
h1 { margin: 8px 0; font-size: clamp(28px, 5vw, 42px); }.page-heading p:last-child { color: #75695f; }
.day-panel,.capture-card,.article-panel { border: 1px solid #eadfd2; border-radius: 18px; background: #fffdf9; box-shadow: 0 8px 28px rgb(70 47 25 / 5%); }
.day-panel,.article-panel { padding: 20px; margin-bottom: 22px; }.day-heading,.classify-row,.article-heading { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.day-heading div { text-align: center; }.day-heading h2 { margin: 4px 0; }.day-heading span,.card-meta { color: #867568; font-size: .9rem; }
.classify-row { margin-top: 16px; padding-top: 14px; border-top: 1px solid #eee3d8; }
button { padding: 9px 14px; border: 1px solid #b6957e; border-radius: 9px; background: #fffaf4; color: #593f31; cursor: pointer; } button:disabled { opacity: .55; cursor: wait; }
.capture-grid { display: grid; gap: 14px; }.capture-card { display: grid; gap: 12px; padding: 18px; }.card-meta { display: flex; align-items: center; gap: 8px; }
.chip { padding: 3px 8px; border-radius: 999px; }.pending { background: #eee9e3; }.emotion { background: #f7e4e3; }.record { background: #e5eee8; }.done { background: #eee4f4; }
.capture-content { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }.capture-card img { max-width: 100%; max-height: 380px; object-fit: contain; border-radius: 10px; }
.record-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 14px; padding-top: 10px; border-top: 1px solid #eee3d8; }.record-actions label { display: flex; align-items: center; gap: 6px; }
.record-actions .image-note { display: grid; width: 100%; gap: 7px; }.image-note textarea { box-sizing: border-box; width: 100%; padding: 10px 12px; border: 1px solid #d9c8b7; border-radius: 10px; font: inherit; resize: vertical; }
.article-panel { margin-top: 28px; }.article-heading h2 { margin: 0; }.article-heading p { margin: 5px 0 0; color: #796c62; }
.article-form { display: grid; gap: 14px; margin-top: 20px; }.article-form label { display: grid; gap: 7px; font-weight: 700; }.article-form input,.article-form textarea { box-sizing: border-box; width: 100%; padding: 11px 13px; border: 1px solid #d9c8b7; border-radius: 10px; font: inherit; color: inherit; }.article-form textarea { resize: vertical; line-height: 1.65; }.knowledge-option { grid-template-columns: auto 1fr !important; align-items: center; justify-content: start; }.knowledge-option input { width: auto; }
.alert,.state { padding: 12px 15px; border-radius: 10px; }.error { background: #fff0ed; color: #9a3d37; }.success { background: #eef7f0; color: #42664c; }.state { text-align: center; color: #796c62; }
@media (max-width: 640px) { .organize-page { padding: 24px 14px 60px; }.day-heading { align-items: stretch; }.day-heading button { max-width: 95px; }.article-heading { align-items: flex-start; }.record-actions { justify-content: flex-start; } }
</style>
