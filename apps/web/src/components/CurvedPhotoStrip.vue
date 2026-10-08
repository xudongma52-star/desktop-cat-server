<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { CarouselPhoto } from '../api/photos'

const props = defineProps<{ photos: CarouselPhoto[]; index: number; paused: boolean }>()
const emit = defineEmits<{ change: [index: number] }>()
const canvas = ref<HTMLCanvasElement | null>(null)
const images = new Map<number, HTMLImageElement>()
// 参考视频约 1.5 秒移过一张；用 3 秒连续移过一张实现半速。
const SECONDS_PER_PHOTO = 3
let phase = props.index
let target: number | null = null
let frame = 0
let previousTime = 0
let visible = true
let resizeObserver: ResizeObserver | undefined
let intersectionObserver: IntersectionObserver | undefined

function nearestIndex() {
  const count = props.photos.length
  return count ? ((Math.round(phase) % count) + count) % count : 0
}

function syncImages() {
  const ids = new Set(props.photos.map((photo) => photo.photoId))
  for (const id of images.keys()) if (!ids.has(id)) images.delete(id)
  for (const photo of props.photos) {
    if (images.has(photo.photoId)) continue
    const image = new Image()
    image.onload = draw
    image.src = photo.contentUrl
    images.set(photo.photoId, image)
  }
  phase = props.index
  target = null
  draw()
}

function draw() {
  const element = canvas.value
  if (!element) return
  const context = element.getContext('2d')
  if (!context) return
  const width = element.clientWidth
  const height = element.clientHeight
  const ratio = Math.min(window.devicePixelRatio || 1, 2)
  if (element.width !== Math.round(width * ratio) || element.height !== Math.round(height * ratio)) {
    element.width = Math.round(width * ratio)
    element.height = Math.round(height * ratio)
  }
  context.setTransform(ratio, 0, 0, ratio, 0, 0)
  context.clearRect(0, 0, width, height)
  const count = props.photos.length
  if (!count || !width) return
  const spacing = count === 1 ? width : width * .8
  const gap = count === 1 ? 0 : width * .016
  const photoWidth = spacing - gap
  // 图片按原比例 cover 裁剪，保持直线与人物比例；仅在外沿裁掉上下两个半椭圆。
  context.save()
  context.beginPath()
  context.ellipse(width / 2, 0, width / 2, height * .14, 0, Math.PI, 0, true)
  context.lineTo(width, height)
  context.ellipse(width / 2, height, width / 2, height * .14, 0, 0, -Math.PI, true)
  context.closePath()
  context.clip()
  const first = count === 1 ? 0 : Math.floor(phase) - 1
  const last = count === 1 ? 0 : Math.ceil(phase) + 1
  for (let slot = first; slot <= last; slot++) {
    const index = ((slot % count) + count) % count
    const image = images.get(props.photos[index].photoId)
    if (!image?.complete || !image.naturalWidth) continue
    const imageAspect = image.naturalWidth / image.naturalHeight
    const frameAspect = photoWidth / height
    const sourceWidth = imageAspect > frameAspect ? image.naturalHeight * frameAspect : image.naturalWidth
    const sourceHeight = imageAspect > frameAspect ? image.naturalHeight : image.naturalWidth / frameAspect
    const x = width / 2 + (slot - phase) * spacing - photoWidth / 2
    context.drawImage(image, (image.naturalWidth - sourceWidth) / 2,
      (image.naturalHeight - sourceHeight) / 2, sourceWidth, sourceHeight,
      x, 0, photoWidth, height)
  }
  context.restore()
}

function animate(time: number) {
  const delta = previousTime ? Math.min((time - previousTime) / 1000, .05) : 0
  previousTime = time
  if (visible) {
    if (target !== null) {
      phase += (target - phase) * (1 - Math.exp(-delta * 7))
      if (Math.abs(target - phase) < .001) { phase = target; target = null }
    } else if (!props.paused && props.photos.length > 1) {
      phase += delta / SECONDS_PER_PHOTO
    }
    const index = nearestIndex()
    if (target === null && index !== props.index) emit('change', index)
    draw()
  }
  frame = requestAnimationFrame(animate)
}

watch(() => props.photos.map((photo) => photo.photoId).join(','), syncImages)
watch(() => props.index, (index) => {
  if (index === nearestIndex()) return
  const count = props.photos.length
  let distance = index - nearestIndex()
  if (distance > count / 2) distance -= count
  if (distance < -count / 2) distance += count
  target = Math.round(phase) + distance
})

onMounted(() => {
  syncImages()
  resizeObserver = new ResizeObserver(draw)
  resizeObserver.observe(canvas.value!)
  intersectionObserver = new IntersectionObserver(([entry]) => { visible = entry.isIntersecting })
  intersectionObserver.observe(canvas.value!)
  frame = requestAnimationFrame(animate)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
  intersectionObserver?.disconnect()
  for (const image of images.values()) image.onload = null
  images.clear()
})
</script>

<template>
  <canvas ref="canvas" class="curved-photo-strip" role="img" aria-label="弧形照片轮播"
    :data-photo-id="photos[index]?.photoId" :data-seconds-per-photo="SECONDS_PER_PHOTO" />
</template>

<style scoped>
.curved-photo-strip { display: block; width: 100%; height: 100%; }
</style>
