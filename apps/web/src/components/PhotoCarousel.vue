<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { deleteCarouselPhoto, getCarouselPhotos, uploadCarouselPhoto } from '../api/photos'
import type { CarouselPhoto } from '../api/photos'
import { describeApiError } from '../api/http'
import CurvedPhotoStrip from './CurvedPhotoStrip.vue'

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

let motionQuery: MediaQueryList | undefined
let sourceImage: HTMLImageElement | null = null
let sourceUrl = ''
let dragPointerId: number | null = null
let dragClientX = 0
let dragClientY = 0

const canRotate = computed(() => photos.value.length > 1)
const isAutoPaused = computed(() => paused.value || cropOpen.value || reducedMotion.value)
const currentPhoto = computed(() => photos.value[currentIndex.value] ?? null)
function move(step: number) {
  if (!canRotate.value) return
  currentIndex.value = (currentIndex.value + step + photos.value.length) % photos.value.length
}

function togglePause() {
  paused.value = !paused.value
}

function handleMotionChange(event: MediaQueryListEvent) {
  reducedMotion.value = event.matches
}

async function loadPhotos() {
  loading.value = true
  error.value = ''
  try {
    photos.value = await getCarouselPhotos()
    currentIndex.value = 0
  } catch (caught) {
    error.value = describeApiError(caught, '照片暂时没有加载成功，请稍后再试。')
  } finally {
    loading.value = false
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
  closeSourceImage()
  motionQuery?.removeEventListener('change', handleMotionChange)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <section
    class="photo-section"
    aria-label="照片轮播"
  >
    <div class="photo-section-heading">
      <div class="photo-heading-actions">
        <input
          ref="fileInput"
          class="visually-hidden"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          @change="handleFileSelection"
        />
        <button type="button" class="photo-upload-button" :disabled="uploading" @click="choosePhoto">
          <span aria-hidden="true">＋</span> 上传照片
        </button>
      </div>
    </div>

    <p v-if="error" class="photo-alert" role="alert">{{ error }}</p>
    <span v-if="loading" class="visually-hidden" role="status">加载中</span>
    <div v-if="photos.length" class="photo-carousel-shell">
      <div class="photo-stage" aria-live="polite">
        <CurvedPhotoStrip
          :photos="photos"
          :index="currentIndex"
          :paused="isAutoPaused || loading || deleting"
          @change="currentIndex = $event"
        />
      </div>
      <div class="photo-carousel-footer">
        <div class="photo-carousel-controls" aria-label="照片轮播控制">
          <button type="button" class="icon-button" aria-label="上一张照片" @click="move(-1)">←</button>
          <button type="button" class="icon-button" aria-label="下一张照片" @click="move(1)">→</button>
          <button type="button" class="pause-button" :aria-label="paused ? '继续轮播' : '暂停轮播'" :title="reducedMotion ? '已关闭自动轮播' : paused ? '继续轮播' : '暂停轮播'" :disabled="reducedMotion" @click="togglePause">
            <span aria-hidden="true">{{ paused ? '▷' : 'Ⅱ' }}</span>
          </button>
        </div>
        <button type="button" class="photo-delete-button" aria-label="移出轮播" title="移出轮播" :disabled="deleting" @click="deleteCurrentPhoto">
          <span aria-hidden="true">×</span>
        </button>
      </div>
    </div>
  </section>

  <Teleport to="body">
    <div v-if="cropOpen" class="crop-overlay" role="presentation" @mousedown.self="closeCrop">
      <section class="crop-dialog" role="dialog" aria-modal="true" aria-labelledby="crop-title">
        <div class="crop-dialog-heading">
          <div>
            <h2 id="crop-title">裁剪照片</h2>
          </div>
          <button type="button" class="crop-close" aria-label="关闭裁剪窗口" :disabled="uploading" @click="closeCrop">×</button>
        </div>
        <p class="crop-hint">拖动调整位置，滑动缩放。</p>
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

<style scoped>
.photo-section {
  position: relative;
  width: min(1100px, 100%);
  margin: 0 auto 56px;
  padding: 0;
  overflow: visible;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.photo-section-heading {
  display: flex;
  justify-content: flex-end;
}

.photo-heading-actions {
  width: auto;
}

.photo-upload-button {
  width: auto;
  min-width: 0;
  padding: 7px 0 7px 10px;
  color: var(--ink);
  background: transparent;
  border-radius: 0;
}

.photo-upload-button:hover {
  color: #2f6346;
  background: transparent;
}

.photo-carousel-shell {
  margin-top: 8px;
}

.photo-stage {
  min-height: 0;
  aspect-ratio: 100 / 48;
  border-radius: 0;
  background: transparent;
}

.photo-stage::after {
  display: none;
}

.photo-slide,
.photo-slide:hover {
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

/* 操作控件保留，默认融入画面，鼠标或键盘进入轮播时才显现。 */
.photo-carousel-footer {
  position: absolute;
  right: 12px;
  bottom: 12px;
  left: 12px;
  margin: 0;
  flex-direction: row;
  align-items: center;
  opacity: 0;
  transition: opacity .18s ease;
}

.photo-carousel-shell:hover .photo-carousel-footer,
.photo-carousel-shell:focus-within .photo-carousel-footer {
  opacity: 1;
}

.photo-carousel-controls {
  width: auto;
}

.icon-button,
.pause-button,
.photo-delete-button {
  min-width: 32px;
  min-height: 32px;
  padding: 6px;
  color: var(--ink);
  border: 0;
  background: rgba(244, 242, 236, .8);
}

@media (hover: none), (prefers-reduced-motion: reduce) {
  .photo-carousel-footer { opacity: 1; }
}

@media (max-width: 700px) {
  .photo-stage { aspect-ratio: 100 / 59; }
}
</style>
