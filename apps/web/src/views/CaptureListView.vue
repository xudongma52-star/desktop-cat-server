<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { createCapture, createCaptureWithImage, deleteCapture, getCaptures, updateCapture } from '../api/captures'
import type { CaptureInput, CaptureItem } from '../api/captures'
import { describeApiError } from '../api/http'

const DRAFT_KEY = 'desktop-cat:capture-web-draft'
const PENDING_KEY = 'desktop-cat:capture-web-pending'
const MAX_IMAGE_BYTES = 10 * 1024 * 1024
const IMAGE_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp'])
const draft = ref(readDraft())
const imageFile = ref<File | null>(null)
const imagePreviewUrl = ref<string | null>(null)
const pending = ref<CaptureInput | null>(readPending(draft.value))
const items = ref<CaptureItem[]>([])
const page = ref(1)
const totalPages = ref(0)
const total = ref(0)
const searchInput = ref('')
const query = ref('')
const editingId = ref<string | null>(null)
const editingContent = ref('')
const loading = ref(true)
const saving = ref(false)
const actionId = ref<string | null>(null)
const error = ref('')
const formError = ref('')
let loadGeneration = 0

function setImage(file: File | null): void {
  if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
  imageFile.value = file
  imagePreviewUrl.value = file ? URL.createObjectURL(file) : null
  pending.value = null
  try { window.localStorage.removeItem(PENDING_KEY) } catch { /* 当前页面仍可重试。 */ }
}

function handlePaste(event: ClipboardEvent): void {
  const file = Array.from(event.clipboardData?.files ?? []).find((item) => IMAGE_TYPES.has(item.type))
  if (file) setImage(file)
}

function handleDrop(event: DragEvent): void {
  const file = Array.from(event.dataTransfer?.files ?? []).find((item) => IMAGE_TYPES.has(item.type))
  if (file) setImage(file)
}

function handleImageSelection(event: Event): void {
  const input = event.target as HTMLInputElement
  setImage(input.files?.[0] ?? null)
  input.value = ''
}

function readDraft(): string {
  try { return window.localStorage.getItem(DRAFT_KEY) ?? '' }
  catch { return '' }
}

function readPending(content: string): CaptureInput | null {
  try {
    const raw = window.localStorage.getItem(PENDING_KEY)
    if (!raw) return null
    const value: unknown = JSON.parse(raw)
    if (value && typeof value === 'object') {
      const item = value as Partial<CaptureInput>
      if (item.content === content && typeof item.captureId === 'string'
          && typeof item.capturedAt === 'string') return item as CaptureInput
    }
  } catch { /* 草稿仍可使用新编号重新保存。 */ }
  return null
}

watch(draft, (value) => {
  pending.value = null
  try {
    if (value) window.localStorage.setItem(DRAFT_KEY, value)
    else window.localStorage.removeItem(DRAFT_KEY)
    window.localStorage.removeItem(PENDING_KEY)
  } catch { /* 浏览器禁用本地存储时，当前页面仍保留草稿。 */ }
})

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

async function loadCaptures(): Promise<void> {
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  try {
    const result = await getCaptures(page.value, query.value)
    if (generation !== loadGeneration) return
    items.value = result.items
    total.value = result.total
    totalPages.value = result.totalPages
  } catch (caught) {
    if (generation === loadGeneration) error.value = describeApiError(caught, '记录暂时无法读取，请稍后再试。')
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function saveDraft(): Promise<void> {
  if (saving.value) return
  formError.value = ''
  if (!draft.value.trim() && !imageFile.value) {
    formError.value = '写点文字或选择一张图片。'
    return
  }
  if (Array.from(draft.value).length > 20_000) {
    formError.value = '一次最多记录 20000 个字符。'
    return
  }
  if (imageFile.value && !IMAGE_TYPES.has(imageFile.value.type)) {
    formError.value = '请选择 PNG、JPEG 或 WebP 图片。'
    return
  }
  if (imageFile.value && imageFile.value.size > MAX_IMAGE_BYTES) {
    formError.value = '图片不能超过 10 MB。'
    return
  }
  const input = pending.value?.content === draft.value
    ? pending.value
    : { captureId: crypto.randomUUID(), content: draft.value, capturedAt: new Date().toISOString() }
  pending.value = input
  if (!imageFile.value) {
    try { window.localStorage.setItem(PENDING_KEY, JSON.stringify(input)) }
    catch { /* 页面内仍沿用同一编号重试。 */ }
  }
  saving.value = true
  try {
    if (imageFile.value) await createCaptureWithImage(input, imageFile.value)
    else await createCapture(input)
    draft.value = ''
    setImage(null)
    pending.value = null
    page.value = 1
    await loadCaptures()
  } catch (caught) {
    formError.value = describeApiError(caught, '暂时没有保存成功，文字还在这里。')
  } finally {
    saving.value = false
  }
}

function search(): void {
  query.value = searchInput.value.trim()
  page.value = 1
  void loadCaptures()
}

function startEdit(item: CaptureItem): void {
  editingId.value = item.captureId
  editingContent.value = item.content
}

async function saveEdit(item: CaptureItem): Promise<void> {
  if (actionId.value) return
  if (!editingContent.value.trim() && !item.imageUrl) {
    error.value = '记录内容不能为空。'
    return
  }
  actionId.value = item.captureId
  error.value = ''
  try {
    await updateCapture(item, editingContent.value)
    editingId.value = null
    await loadCaptures()
  } catch (caught) {
    error.value = describeApiError(caught, '修改失败，请刷新后重试。')
  } finally {
    actionId.value = null
  }
}

async function remove(item: CaptureItem): Promise<void> {
  if (actionId.value || !window.confirm('删除这条记录？')) return
  actionId.value = item.captureId
  error.value = ''
  try {
    await deleteCapture(item)
    if (items.value.length === 1 && page.value > 1) page.value--
    await loadCaptures()
  } catch (caught) {
    error.value = describeApiError(caught, '删除失败，请刷新后重试。')
  } finally {
    actionId.value = null
  }
}

onMounted(() => void loadCaptures())
onBeforeUnmount(() => { if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value) })
</script>

<template>
  <div class="capture-page">
    <header class="page-heading">
      <p class="eyebrow">随手记录</p>
      <h1>想到什么，就记下什么。</h1>
      <p>不用起标题，也不用现在整理。小猫记下的文字也会出现在这里。</p>
      <RouterLink class="organize-link" to="/captures/organize">按天分类和整理 →</RouterLink>
    </header>

    <form class="capture-form" @submit.prevent="saveDraft" @dragover.prevent @drop.prevent="handleDrop">
      <label for="capture-draft">记下此刻</label>
      <textarea id="capture-draft" v-model="draft" rows="5" placeholder="一句想法、一段摘录，或者刚遇到的问题……" :disabled="saving" @paste="handlePaste"></textarea>
      <label class="image-picker">添加图片<input type="file" accept="image/png,image/jpeg,image/webp" :disabled="saving" @change="handleImageSelection" /></label>
      <div v-if="imagePreviewUrl" class="image-preview"><img :src="imagePreviewUrl" alt="待保存的图片预览" /><button type="button" :disabled="saving" @click="setImage(null)">移除图片</button></div>
      <div class="form-footer"><span>Enter 换行，点击按钮保存</span><button type="submit" :disabled="saving">{{ saving ? '保存中…' : '记下' }}</button></div>
      <p v-if="formError" role="alert" class="error">{{ formError }}</p>
    </form>

    <section class="capture-list" aria-labelledby="capture-list-title">
      <div class="list-heading"><h2 id="capture-list-title">已经记下的</h2><span>{{ total }} 条</span></div>
      <form class="search-form" @submit.prevent="search">
        <input v-model="searchInput" aria-label="搜索记录" maxlength="100" placeholder="搜索记录内容" />
        <button type="submit">搜索</button>
      </form>
      <p v-if="error" class="error" role="alert">{{ error }} <button type="button" @click="loadCaptures">重新加载</button></p>
      <p v-if="loading">正在读取记录…</p>
      <p v-else-if="!items.length">{{ query ? '没有找到相关记录。' : '第一条记录，等你写下。' }}</p>
      <article v-for="item in items" :key="item.captureId" class="capture-card">
        <time :datetime="item.capturedAt">{{ formatTime(item.capturedAt) }}</time>
        <template v-if="editingId === item.captureId">
          <textarea v-model="editingContent" rows="5" :disabled="actionId === item.captureId"></textarea>
          <div class="card-actions"><button type="button" :disabled="!!actionId" @click="saveEdit(item)">保存修改</button><button type="button" :disabled="!!actionId" @click="editingId = null">取消</button></div>
        </template>
        <template v-else>
          <p v-if="item.content" class="capture-content">{{ item.content }}</p>
          <a v-if="item.imageUrl" :href="item.imageUrl" target="_blank" rel="noopener"><img class="capture-image" :src="item.imageUrl" alt="查看记录原图" /></a>
          <div class="card-actions"><button type="button" :disabled="!!actionId" @click="startEdit(item)">修改</button><button type="button" :disabled="!!actionId" @click="remove(item)">删除</button></div>
        </template>
      </article>
      <div v-if="totalPages > 1" class="pagination">
        <button type="button" :disabled="page <= 1 || loading" @click="page--; loadCaptures()">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button type="button" :disabled="page >= totalPages || loading" @click="page++; loadCaptures()">下一页</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.capture-page { max-width: 860px; margin: 0 auto; padding: 36px 20px 80px; color: #493d35; }
.page-heading { margin-bottom: 28px; }
.eyebrow { color: #9b735b; letter-spacing: .12em; }
h1 { margin: 8px 0; font-size: clamp(28px, 5vw, 42px); }
.page-heading > p:last-of-type { color: #75695f; }
.organize-link { display: inline-flex; margin-top: 8px; color: #72594d; font-weight: 700; }
.capture-form, .capture-card { padding: 22px; border: 1px solid #eadfd2; border-radius: 18px; background: #fffdf9; box-shadow: 0 8px 28px rgb(70 47 25 / 5%); }
.capture-form { display: grid; gap: 12px; margin-bottom: 36px; }
.capture-form label { font-weight: 700; }
.image-picker { display: inline-flex; align-items: center; gap: 8px; width: fit-content; cursor: pointer; }
.image-picker input { max-width: 250px; }
.image-preview { display: grid; justify-items: start; gap: 8px; }
.image-preview img, .capture-image { max-width: 100%; max-height: 360px; object-fit: contain; border-radius: 8px; }
textarea, input { box-sizing: border-box; width: 100%; padding: 12px 14px; border: 1px solid #d9c8b7; border-radius: 10px; background: white; color: inherit; font: inherit; }
textarea { resize: vertical; line-height: 1.65; }
button { padding: 8px 14px; border: 1px solid #b6957e; border-radius: 9px; background: #fffaf4; color: #593f31; cursor: pointer; }
button:disabled { cursor: wait; opacity: .55; }
.form-footer, .list-heading, .card-actions, .pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.form-footer span, .list-heading span, time { color: #867568; font-size: .9em; }
.form-footer button { background: #72594d; color: white; }
.capture-list { display: grid; gap: 16px; }
.list-heading h2 { margin: 0; }
.search-form { display: flex; gap: 8px; }
.capture-card { display: grid; gap: 12px; }
.capture-content { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
.card-actions { justify-content: flex-end; }
.pagination { justify-content: center; }
.error { color: #a3453f; }
@media (max-width: 560px) { .capture-page { padding: 24px 14px 60px; } .capture-form, .capture-card { padding: 16px; } }
</style>
