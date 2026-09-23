<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, describeApiError } from '../api/http'
import { deleteRecord, formatRecordDate, getRecord, recordTypeLabels } from '../api/records'
import type { PersonalRecord } from '../api/records'

const route = useRoute()
const router = useRouter()
const record = ref<PersonalRecord | null>(null)
const loading = ref(true)
const deleting = ref(false)
const error = ref('')
let loadGeneration = 0

function routeRecordId(): number | null {
  const value = Number(route.params.recordId)
  return Number.isInteger(value) && value > 0 ? value : null
}

async function loadRecord(): Promise<boolean> {
  const generation = ++loadGeneration
  const recordId = routeRecordId()
  record.value = null
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
    await router.push('/records')
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
    <div class="breadcrumb"><RouterLink to="/records">← 返回记录</RouterLink></div>
    <div v-if="loading" class="state-panel" role="status">正在打开这篇记录…</div>
    <div v-else-if="!record" class="state-panel error" role="alert">
      <p>{{ error }}</p>
      <div class="state-actions">
        <button type="button" class="button secondary" @click="loadRecord">重新加载</button>
        <RouterLink class="button" to="/records">回到记录列表</RouterLink>
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
          <span v-if="record.recallEnabled">🍃 已加入温馨回忆</span>
          <span v-if="record.ragEnabled">⌁ 允许 AI 检索</span>
        </div>
      </header>
      <div class="record-content">{{ record.content }}</div>
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
