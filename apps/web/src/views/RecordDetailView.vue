<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, describeApiError } from '../api/http'
import { deleteRecord, formatRecordDate, getRecord, recordTypeLabels } from '../api/records'
import type { PersonalRecord } from '../api/records'
import MarkdownContent from '../components/MarkdownContent.vue'
import { getRecordSources } from '../api/captures'
import type { CaptureItem } from '../api/captures'

const route = useRoute()
const router = useRouter()
const returnLocation = computed(() => ({ path: '/records', query: route.query }))
const record = ref<PersonalRecord | null>(null)
const sources = ref<CaptureItem[]>([])
const loading = ref(true)
const deleting = ref(false)
const error = ref('')
const sourceError = ref('')
let loadGeneration = 0

function returnToRecords(event: MouseEvent) {
  if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  // 从列表进入时回退到原历史项，连同筛选、分页和滚动位置一起恢复；直达详情仍用正常链接。
  if (router.options.history.state.back === router.resolve(returnLocation.value).fullPath) {
    router.back()
  } else void router.push(returnLocation.value)
}

function routeRecordId(): number | null {
  const value = Number(route.params.recordId)
  return Number.isInteger(value) && value > 0 ? value : null
}

async function loadRecord(): Promise<boolean> {
  const generation = ++loadGeneration
  const recordId = routeRecordId()
  record.value = null
  sources.value = []
  sourceError.value = ''
  if (!recordId) {
    error.value = '记录编号无效。'
    loading.value = false
    return false
  }
  loading.value = true
  error.value = ''
  try {
    const nextRecord = await getRecord(recordId)
    if (generation !== loadGeneration) return false
    record.value = nextRecord
    try {
      const nextSources = await getRecordSources(nextRecord.recordId)
      if (generation !== loadGeneration) return false
      sources.value = nextSources
    } catch (caught) {
      if (generation === loadGeneration) {
        sourceError.value = describeApiError(caught, '原始来源暂时无法读取。')
      }
    }
    return true
  } catch (caught) {
    if (generation !== loadGeneration) return false
    error.value = describeApiError(caught, '这篇记录暂时没有加载成功，请稍后再试。')
    return false
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function removeCurrentRecord() {
  if (!record.value || deleting.value) return
  const displayTitle = record.value.title || '这篇无标题记录'
  if (!window.confirm(`确定删除“${displayTitle}”吗？删除后不会继续出现在列表和回忆中。`)) return

  deleting.value = true
  error.value = ''
  try {
    await deleteRecord(record.value.recordId, record.value.version)
    await router.push(returnLocation.value)
  } catch (caught) {
    if (caught instanceof ApiError && (caught.status === 409 || caught.code.includes('VERSION_CONFLICT'))) {
      const reloaded = await loadRecord()
      if (reloaded) {
        error.value = '这篇记录刚刚在别处更新过，已为你载入最新内容。请确认后再删除。'
      }
    } else {
      error.value = describeApiError(caught, '删除失败，请稍后再试。')
    }
  } finally {
    deleting.value = false
  }
}

watch(() => route.params.recordId, () => void loadRecord(), { immediate: true })
onBeforeUnmount(() => {
  loadGeneration += 1
})
</script>

<template>
  <div class="detail-page content-page narrow-page">
    <div class="breadcrumb"><a :href="router.resolve(returnLocation).href" @click="returnToRecords">← 返回记录</a></div>
    <div v-if="loading" class="state-panel" role="status">正在打开这篇记录…</div>
    <div v-else-if="!record" class="state-panel error" role="alert">
      <p>{{ error }}</p>
      <div class="state-actions">
        <button type="button" class="button secondary" @click="loadRecord">重新加载</button>
        <a class="button" :href="router.resolve(returnLocation).href" @click="returnToRecords">回到记录列表</a>
      </div>
    </div>
    <article v-else class="record-detail">
      <p v-if="error" class="inline-alert" role="alert">{{ error }}</p>
      <header>
        <div class="detail-meta">
          <span class="type-chip">{{ recordTypeLabels[record.recordType] }}</span>
          <time :datetime="record.recordDate">{{ formatRecordDate(record.recordDate) }}</time>
          <span v-if="record.mood">心情 · {{ record.mood }}</span>
        </div>
        <h1>{{ record.title || '没有标题的一天' }}</h1>
        <div class="detail-flags">
          <span v-if="record.recallEnabled">🍃 已加入首页图文</span>
          <span v-if="record.ragEnabled">⌁ 允许智能检索</span>
        </div>
      </header>
      <img v-if="record.imageUrl" :src="record.imageUrl" :alt="record.title || '文章配图'" style="display:block;max-width:100%;max-height:70vh;margin-bottom:32px;object-fit:contain" />
      <MarkdownContent class="record-content" :content="record.content" />
      <section v-if="sources.length || sourceError" class="record-sources" aria-labelledby="record-sources-title">
        <h2 id="record-sources-title">原始碎片</h2>
        <p v-if="sourceError" class="inline-alert" role="alert">{{ sourceError }}</p>
        <article v-for="source in sources" :key="source.captureId" class="source-item">
          <time :datetime="source.capturedAt">{{ new Date(source.capturedAt).toLocaleString('zh-CN') }}</time>
          <p v-if="source.content">{{ source.content }}</p>
          <a v-if="source.imageUrl" :href="source.imageUrl" target="_blank" rel="noopener">
            <img :src="source.imageUrl" alt="文章来源原图" />
          </a>
        </article>
      </section>
      <footer class="detail-footer">
        <div class="detail-times">
          <span>创建于 {{ new Date(record.createdAt).toLocaleString('zh-CN') }}</span>
          <span>更新于 {{ new Date(record.updatedAt).toLocaleString('zh-CN') }} · v{{ record.version }}</span>
        </div>
        <div class="detail-actions">
          <button type="button" class="danger-button" :disabled="deleting" @click="removeCurrentRecord">
            {{ deleting ? '删除中…' : '删除记录' }}
          </button>
          <RouterLink class="button" :to="`/records/${record.recordId}/edit`">编辑记录</RouterLink>
        </div>
      </footer>
    </article>
  </div>
</template>

<style scoped>
.record-sources { margin-top: 32px; padding-top: 24px; border-top: 1px solid #eadfd2; }
.record-sources h2 { margin: 0 0 16px; }
.source-item { display: grid; gap: 9px; padding: 14px 0; border-top: 1px solid #f0e7de; }
.source-item time { color: #867568; font-size: .9rem; }
.source-item p { margin: 0; white-space: pre-wrap; line-height: 1.65; }
.source-item img { max-width: 100%; max-height: 420px; object-fit: contain; border-radius: 10px; }
</style>
