<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { deleteCarouselPhoto, getCarouselPhotos, uploadCarouselPhoto } from '../api/photos'
import type { CarouselPhoto } from '../api/photos'
import { describeApiError } from '../api/http'

const CROP_WIDTH = 800
const CROP_HEIGHT = 600
const OUTPUT_WIDTH = 1200
const OUTPUT_HEIGHT = 900
const MAX_SOURCE_SIZE = 20 * 1024 * 1024
const MAX_UPLOAD_SIZE = 2 * 1024 * 1024
const acceptedTypes = new Set(['image/jpeg', 'image/png', 'image/webp'])

const photos = ref<CarouselPhoto[]>([])
const currentIndex = ref(0)
const loading = ref(true)
const uploading = ref(false)
const deleting = ref(false)
const error = ref('')
const paused = ref(false)
const reducedMotion = ref(false)
const cropOpen = ref(false)
const cropError = ref('')
const zoom = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)
const fileInput = ref<HTMLInputElement | null>(null)
const cropCanvas = ref<HTMLCanvasElement | null>(null)

let timer: number | undefined
let motionQuery: MediaQueryList | undefined
let sourceImage: HTMLImageElement | null = null
let sourceUrl = ''
let dragPointerId: number | null = null
let dragClientX = 0
let dragClientY = 0
let disposed = false

const canRotate = computed(() => photos.value.length > 1)
const isAutoPaused = computed(() => paused.value || cropOpen.value || reducedMotion.value)
const currentPhoto = computed(() => photos.value[currentIndex.value] ?? null)
const visiblePhotos = computed(() => {
  const count = photos.value.length
  if (count === 0) return []
  if (count === 1) return [{ photo: photos.value[0], position: 'center' }]
  if (count === 2) {
    return [
      { photo: photos.value[currentIndex.value], position: 'center' },
      { photo: photos.value[(currentIndex.value + 1) % count], position: 'right' },
    ]
  }
  return [
    { photo: photos.value[(currentIndex.value - 1 + count) % count], position: 'left' },
    { photo: photos.value[currentIndex.value], position: 'center' },
    { photo: photos.value[(currentIndex.value + 1) % count], position: 'right' },
  ]
})

function stopTimer() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

function startTimer() {
  stopTimer()
  if (disposed || !canRotate.value || isAutoPaused.value) return
  timer = window.setInterval(() => move(1), 3000)
}

function move(step: number) {
  if (!canRotate.value) return
  currentIndex.value = (currentIndex.value + step + photos.value.length) % photos.value.length
  startTimer()
}

function showPhoto(photoId: number) {
  const index = photos.value.findIndex((photo) => photo.photoId === photoId)
  if (index >= 0 && index !== currentIndex.value) {
    currentIndex.value = index
    startTimer()
  }
}

function togglePause() {
  paused.value = !paused.value
  startTimer()
}

function handleMotionChange(event: MediaQueryListEvent) {
  reducedMotion.value = event.matches
  startTimer()
}

async function loadPhotos() {
  loading.value = true
  error.value = ''
  stopTimer()
  try {
    photos.value = await getCarouselPhotos()
    currentIndex.value = 0
  } catch (caught) {
    error.value = describeApiError(caught, '照片暂时没有加载成功，请稍后再试。')
  } finally {
    loading.value = false
    startTimer()
  }
}

function choosePhoto() {
  fileInput.value?.click()
}

function resetFileInput() {
  if (fileInput.value) fileInput.value.value = ''
}

async function handleFileSelection(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0]
  if (!selected) return
  cropError.value = ''
  if (!acceptedTypes.has(selected.type)) {
    error.value = '请选择 JPG、PNG 或 WebP 图片。'
    resetFileInput()
    return
  }
  if (selected.size > MAX_SOURCE_SIZE) {
    error.value = '原图不能超过 20 MB。'
    resetFileInput()
    return
  }

  closeSourceImage()
  sourceUrl = URL.createObjectURL(selected)
  const image = new Image()
  sourceImage = image
  image.onload = async () => {
    if (sourceImage !== image) return
    zoom.value = 1
    offsetX.value = 0
    offsetY.value = 0
    cropOpen.value = true
    await nextTick()
    drawCrop()
    startTimer()
  }
  image.onerror = () => {
    error.value = '这张图片无法读取，请换一张试试。'
    closeSourceImage()
    resetFileInput()
  }
  image.src = sourceUrl
}

function getScale() {
  if (!sourceImage) return 1
  return Math.max(CROP_WIDTH / sourceImage.naturalWidth, CROP_HEIGHT / sourceImage.naturalHeight) * zoom.value
}

function clampOffsets() {
  if (!sourceImage) return
  const scale = getScale()
  const limitX = Math.max(0, (sourceImage.naturalWidth * scale - CROP_WIDTH) / 2)
  const limitY = Math.max(0, (sourceImage.naturalHeight * scale - CROP_HEIGHT) / 2)
  offsetX.value = Math.max(-limitX, Math.min(limitX, offsetX.value))
  offsetY.value = Math.max(-limitY, Math.min(limitY, offsetY.value))
}

function drawImage(context: CanvasRenderingContext2D, outputScale: number) {
  if (!sourceImage) return
  const scale = getScale() * outputScale
  const width = sourceImage.naturalWidth * scale
  const height = sourceImage.naturalHeight * scale
  context.drawImage(
    sourceImage,
    (CROP_WIDTH / 2 + offsetX.value) * outputScale - width / 2,
    (CROP_HEIGHT / 2 + offsetY.value) * outputScale - height / 2,
    width,
    height,
  )
}

function drawCrop() {
  const canvas = cropCanvas.value
  if (!canvas || !sourceImage) return
  clampOffsets()
  const context = canvas.getContext('2d')
  if (!context) return
  context.clearRect(0, 0, CROP_WIDTH, CROP_HEIGHT)
  drawImage(context, 1)
}

function updateZoom() {
  drawCrop()
}

function startDragging(event: PointerEvent) {
  if (!cropCanvas.value) return
  dragPointerId = event.pointerId
  dragClientX = event.clientX
  dragClientY = event.clientY
  cropCanvas.value.setPointerCapture(event.pointerId)
}

function dragCrop(event: PointerEvent) {
  if (dragPointerId !== event.pointerId || !cropCanvas.value) return
  const widthRatio = CROP_WIDTH / cropCanvas.value.getBoundingClientRect().width
  offsetX.value += (event.clientX - dragClientX) * widthRatio
  offsetY.value += (event.clientY - dragClientY) * widthRatio
  dragClientX = event.clientX
  dragClientY = event.clientY
  drawCrop()
}

function stopDragging(event: PointerEvent) {
  if (dragPointerId !== event.pointerId) return
  dragPointerId = null
  cropCanvas.value?.releasePointerCapture(event.pointerId)
}

function canvasToBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) reject(new Error('WEBP_EXPORT_FAILED'))
        else if (blob.type !== 'image/webp') reject(new Error('WEBP_NOT_SUPPORTED'))
        else resolve(blob)
      },
      'image/webp',
      quality,
    )
  })
}

async function createUploadBlob() {
  const output = document.createElement('canvas')
  output.width = OUTPUT_WIDTH
  output.height = OUTPUT_HEIGHT
  const context = output.getContext('2d')
  if (!context || !sourceImage) throw new Error('CROP_NOT_READY')
  drawImage(context, OUTPUT_WIDTH / CROP_WIDTH)
  let blob = await canvasToBlob(output, 0.86)
  if (blob.size > MAX_UPLOAD_SIZE) blob = await canvasToBlob(output, 0.72)
  if (blob.size > MAX_UPLOAD_SIZE) throw new Error('OUTPUT_TOO_LARGE')
  return blob
}

async function submitCrop() {
  if (uploading.value) return
  uploading.value = true
  cropError.value = ''
  try {
    const blob = await createUploadBlob()
    const created = await uploadCarouselPhoto(blob)
    photos.value.unshift(created)
    currentIndex.value = 0
    uploading.value = false
    closeCrop()
  } catch (caught) {
    if (caught instanceof Error && caught.message === 'OUTPUT_TOO_LARGE') {
      cropError.value = '图片压缩后仍超过 2 MB，请换一张细节稍少的图片。'
    } else if (caught instanceof Error && caught.message === 'WEBP_NOT_SUPPORTED') {
      cropError.value = '当前浏览器无法生成 WebP 图片，请升级浏览器后再试。'
    } else {
      cropError.value = describeApiError(caught, '上传失败，请稍后再试。')
    }
  } finally {
    uploading.value = false
    startTimer()
  }
}

function closeSourceImage() {
  sourceImage = null
  if (sourceUrl) URL.revokeObjectURL(sourceUrl)
  sourceUrl = ''
}

function closeCrop() {
  if (uploading.value) return
  cropOpen.value = false
  cropError.value = ''
  dragPointerId = null
  closeSourceImage()
  resetFileInput()
  startTimer()
}

async function deleteCurrentPhoto() {
  const photo = currentPhoto.value
  if (!photo || deleting.value) return
  if (!window.confirm('要把这张照片移出轮播吗？')) return
  deleting.value = true
  error.value = ''
  try {
    await deleteCarouselPhoto(photo.photoId)
    const index = photos.value.findIndex((item) => item.photoId === photo.photoId)
    if (index >= 0) photos.value.splice(index, 1)
    currentIndex.value = photos.value.length === 0
      ? 0
      : Math.min(currentIndex.value, photos.value.length - 1)
  } catch (caught) {
    error.value = describeApiError(caught, '删除失败，请稍后再试。')
  } finally {
    deleting.value = false
    startTimer()
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && cropOpen.value && !uploading.value) closeCrop()
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  motionQuery.addEventListener('change', handleMotionChange)
  window.addEventListener('keydown', handleKeydown)
  void loadPhotos()
})

onBeforeUnmount(() => {
  disposed = true
  stopTimer()
  closeSourceImage()
  motionQuery?.removeEventListener('change', handleMotionChange)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <section
    class="photo-section"
    aria-labelledby="photo-carousel-title"
  >
    <div class="section-top photo-section-heading">
      <div>
        <p class="eyebrow">温馨回忆 · 照片</p>
        <h2 id="photo-carousel-title">让喜欢的画面，在回家时迎接你</h2>
      </div>
      <div class="photo-heading-actions">
        <input
          ref="fileInput"
          class="visually-hidden"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          @change="handleFileSelection"
        />
        <button type="button" class="button photo-upload-button" :disabled="uploading" @click="choosePhoto">
          <span aria-hidden="true">＋</span> 上传照片
        </button>
      </div>
    </div>

    <p v-if="error" class="photo-alert" role="alert">{{ error }}</p>
    <div v-if="loading" class="state-panel photo-state" role="status">正在整理你的照片…</div>
    <div v-else-if="photos.length === 0" class="state-panel photo-empty">
      <span class="empty-icon" aria-hidden="true">🖼️</span>
      <div>
        <h3>这里还在等第一张照片</h3>
        <p>上传后先裁成统一的 4:3，照片会自动加入轮播。</p>
      </div>
      <button type="button" class="button" @click="choosePhoto">选择照片</button>
    </div>
    <div v-else class="photo-carousel-shell">
      <div class="photo-stage" aria-live="polite">
        <TransitionGroup name="photo-shift">
          <button
            v-for="item in visiblePhotos"
            :key="item.photo.photoId"
            type="button"
            class="photo-slide"
            :class="`photo-slide--${item.position}`"
            :aria-label="item.position === 'center' ? '当前照片' : '切换到这张照片'"
            :tabindex="item.position === 'center' ? -1 : 0"
            @click="showPhoto(item.photo.photoId)"
          >
            <img :src="item.photo.contentUrl" alt="轮播中的生活照片" draggable="false" />
          </button>
        </TransitionGroup>
      </div>
      <div class="photo-carousel-footer">
        <div class="photo-carousel-controls" aria-label="照片轮播控制">
          <button type="button" class="icon-button" aria-label="上一张照片" @click="move(-1)">←</button>
          <span class="carousel-count">{{ currentIndex + 1 }} / {{ photos.length }}</span>
          <button type="button" class="icon-button" aria-label="下一张照片" @click="move(1)">→</button>
          <button type="button" class="pause-button" :disabled="reducedMotion" @click="togglePause">
            {{ reducedMotion ? '已关闭自动轮播' : paused ? '继续轮播' : '暂停轮播' }}
          </button>
        </div>
        <button type="button" class="photo-delete-button" :disabled="deleting" @click="deleteCurrentPhoto">
          {{ deleting ? '移除中…' : '移出轮播' }}
        </button>
      </div>
    </div>
  </section>

  <Teleport to="body">
    <div v-if="cropOpen" class="crop-overlay" role="presentation" @mousedown.self="closeCrop">
      <section class="crop-dialog" role="dialog" aria-modal="true" aria-labelledby="crop-title">
        <div class="crop-dialog-heading">
          <div>
            <p class="eyebrow">上传前整理一下</p>
            <h2 id="crop-title">裁成适合轮播的 4:3 画面</h2>
          </div>
          <button type="button" class="crop-close" aria-label="关闭裁剪窗口" :disabled="uploading" @click="closeCrop">×</button>
        </div>
        <p class="crop-hint">拖动画面调整位置，用滑块放大。保存后会生成统一的 1200 × 900 图片。</p>
        <div class="crop-canvas-frame">
          <canvas
            ref="cropCanvas"
            :width="CROP_WIDTH"
            :height="CROP_HEIGHT"
            aria-label="照片裁剪预览"
            @pointerdown="startDragging"
            @pointermove="dragCrop"
            @pointerup="stopDragging"
            @pointercancel="stopDragging"
          ></canvas>
          <span class="crop-grid" aria-hidden="true"></span>
        </div>
        <label class="crop-zoom">
          <span>缩放</span>
          <input v-model.number="zoom" type="range" min="1" max="3" step="0.01" @input="updateZoom" />
          <span>{{ zoom.toFixed(1) }}×</span>
        </label>
        <p v-if="cropError" class="crop-error" role="alert">{{ cropError }}</p>
        <div class="crop-actions">
          <button type="button" class="button secondary" :disabled="uploading" @click="closeCrop">取消</button>
          <button type="button" class="button" :disabled="uploading" @click="submitCrop">
            {{ uploading ? '正在上传…' : '裁剪并加入轮播' }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
