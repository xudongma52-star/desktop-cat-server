<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { gsap } from 'gsap'
import { Flip } from 'gsap/Flip'
import painting from '../assets/dusk-writer-round.webp'
import flythroughLandscape from '../assets/dusk-flythrough-landscape.webp'
import arrivalVideoMp4 from '../assets/dusk-arrival.mp4'
import arrivalVideoWebm from '../assets/dusk-arrival.webm'
import { createPaintedScene } from './paintedScene'

gsap.registerPlugin(Flip)

const holder = ref<HTMLElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const arrivalSequence = ref<HTMLElement | null>(null)
const arrivalVideo = ref<HTMLVideoElement | null>(null)
const arrivalRoom = ref<HTMLElement | null>(null)
const arrivalPortal = ref<HTMLElement | null>(null)
const arrivalScrim = ref<HTMLElement | null>(null)
const arrivalVignette = ref<HTMLElement | null>(null)
const moving = ref(false)
const paused = ref(false)
const reducedMotion = ref(false)
const entering = ref(false)
const available = ref(false)
const canMove = computed(() => available.value && !reducedMotion.value && !entering.value)
let renderer: ReturnType<typeof createPaintedScene> = null
let frame = 0
let elapsed = 0
let lastFrame = 0
let inView = true
let disposed = false
let observer: IntersectionObserver | undefined
let media: MediaQueryList | undefined
let entryFallback = 0
let asset: HTMLImageElement | undefined
let entryContext: gsap.Context | undefined
let entryTimeline: gsap.core.Timeline | undefined
let handoffAnimation: ReturnType<typeof Flip.from> | undefined
let entryViewportWidth = 0
let entryViewportHeight = 0
let entryScrollY = 0

function stopFrames() {
  cancelAnimationFrame(frame)
  frame = 0
  lastFrame = 0
}
function tick(now: number) {
  frame = requestAnimationFrame(tick)
  if (!lastFrame) lastFrame = now
  if (now - lastFrame < 1000 / 30) return
  elapsed += Math.min(now - lastFrame, 100) / 1000
  lastFrame = now
  renderer?.draw(elapsed)
}
function syncPlayback() {
  stopFrames()
  moving.value = canMove.value && !paused.value && inView && !document.hidden
  if (moving.value) frame = requestAnimationFrame(tick)
}
function toggleMotion() { paused.value = !paused.value; syncPlayback() }
function finishEntry() {
  if (!entering.value) return
  entryTimeline?.kill()
  handoffAnimation?.kill()
  entryContext?.revert()
  entryTimeline = undefined
  handoffAnimation = undefined
  entryContext = undefined
  entering.value = false
  arrivalVideo.value?.pause()
  clearTimeout(entryFallback)
  document.documentElement.classList.remove('arrival-active')
  try { sessionStorage.setItem('cat-corner-arrival-v10', 'seen') } catch { /* 禁用存储时仍可正常进入页面。 */ }
  if (!disposed) syncPlayback()
}
function waitForVideoReady(video: HTMLVideoElement) {
  if (video.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA) return Promise.resolve()
  return new Promise<void>((resolve) => {
    const done = () => {
      clearTimeout(timeout)
      video.removeEventListener('canplay', done)
      video.removeEventListener('error', done)
      resolve()
    }
    const timeout = window.setTimeout(done, 2800)
    video.addEventListener('canplay', done, { once: true })
    video.addEventListener('error', done, { once: true })
    video.load()
  })
}
async function startArrivalVideo(video: HTMLVideoElement) {
  video.currentTime = 0
  // 线上资源先缓冲到可连续播放再启动时间轴，避免下载速度被看成镜头卡顿。
  await waitForVideoReady(video)
  if (!entering.value || disposed) return
  try { await video.play() } catch { /* 自动播放失败时使用视频封面完成降级过渡。 */ }
}
function setRoomCoverGeometry() {
  if (!arrivalRoom.value) return
  // 全屏阶段和页面画作保持同一 3:2 画幅，落位时图像内容不会因 object-fit 重算而跳动。
  const width = Math.max(innerWidth, innerHeight * 1.5)
  const height = width / 1.5
  gsap.set(arrivalRoom.value, {
    left: (innerWidth - width) / 2,
    top: (innerHeight - height) / 2,
    width,
    height,
  })
}
function handoffRoomToPage() {
  const room = arrivalRoom.value
  const destination = holder.value
  if (!room || !destination || !entering.value) {
    finishEntry()
    return
  }
  const destinationRect = destination.getBoundingClientRect()
  const state = Flip.getState(room)
  gsap.set(room, {
    left: destinationRect.left,
    top: destinationRect.top,
    width: destinationRect.width,
    height: destinationRect.height,
    borderRadius: getComputedStyle(destination).borderRadius,
  })
  if (arrivalScrim.value) gsap.to(arrivalScrim.value, { autoAlpha: 0, duration: .9, ease: 'power2.out' })
  if (arrivalVignette.value) gsap.to(arrivalVignette.value, { autoAlpha: 0, duration: .7, ease: 'power2.out' })
  handoffAnimation = Flip.from(state, {
    absolute: true,
    duration: 1.05,
    ease: 'power3.inOut',
    scale: true,
    simple: true,
    onComplete: () => {
      handoffAnimation = undefined
      finishEntry()
    },
  })
}
async function playEntry(force = false) {
  if (disposed || entering.value || reducedMotion.value || !asset?.complete || !asset.naturalWidth || !holder.value) return
  if (!force) {
    try { if (sessionStorage.getItem('cat-corner-arrival-v10')) return } catch { /* 存储不可用时只影响跨页面记忆。 */ }
    // 图片迟到或用户已开始滚动时，不打断正在进行的阅读。
    if (window.scrollY > 80 || document.hidden) return
  }
  const destination = holder.value.getBoundingClientRect()
  if (destination.bottom < 0 || destination.top > innerHeight) return
  entering.value = true
  document.documentElement.classList.add('arrival-active')
  // 入场视频播放期间暂停被遮住的 WebGL 画作，避免两个渲染循环争抢 GPU。
  syncPlayback()
  await nextTick()
  if (disposed || !entering.value || !arrivalSequence.value || !arrivalRoom.value) return
  entryViewportWidth = innerWidth
  entryViewportHeight = innerHeight
  entryScrollY = window.scrollY
  setRoomCoverGeometry()
  // 缓冲或媒体解码异常时仍保证遮罩能够退出。
  entryFallback = window.setTimeout(finishEntry, 10500)
  if (arrivalVideo.value) await startArrivalVideo(arrivalVideo.value)
  if (disposed || !entering.value) return
  // 视频负责连续推进，窗框暗部遮住空间翻转，最终原画再以同一画幅无缝落入页面。
  entryContext = gsap.context(() => {
    if (!arrivalVideo.value || !arrivalRoom.value || !arrivalPortal.value || !arrivalScrim.value || !arrivalVignette.value) return
    gsap.set(arrivalScrim.value, { autoAlpha: 1 })
    gsap.set(arrivalRoom.value, { autoAlpha: 0, scale: 1.025 })
    gsap.set(arrivalPortal.value, { autoAlpha: 0, scale: .72, rotation: -.8 })
    gsap.set(arrivalVignette.value, { autoAlpha: .68 })
    entryTimeline = gsap.timeline({ defaults: { overwrite: 'auto' } })
      .set(arrivalVideo.value, { autoAlpha: 1, scale: 1, xPercent: 0, yPercent: 0, rotation: 0 }, 0)
      .fromTo(arrivalPortal.value,
        { autoAlpha: 0, scale: .72, rotation: -.8 },
        { autoAlpha: .96, scale: 1.06, rotation: .35, duration: .62, ease: 'power3.in' },
        3.50)
      .to(arrivalVideo.value, { autoAlpha: 0, duration: .36, ease: 'power2.out' }, 3.82)
      .to(arrivalRoom.value, { autoAlpha: 1, scale: 1, duration: .44, ease: 'power2.out' }, 3.84)
      .to(arrivalPortal.value, { autoAlpha: 0, scale: 1.2, duration: .52, ease: 'power2.out' }, 4.02)
      .to(arrivalVignette.value, { autoAlpha: .28, duration: .45, ease: 'sine.out' }, 4.06)
      .call(handoffRoomToPage, [], 4.52)
  }, arrivalSequence.value)
}
function changeMotion(event: MediaQueryListEvent) {
  reducedMotion.value = event.matches
  if (event.matches) finishEntry()
  syncPlayback()
}
function handleKey(event: KeyboardEvent) { if (event.key === 'Escape') finishEntry() }
function handleEntryResize() {
  if (!entering.value) return
  const widthChanged = Math.abs(innerWidth - entryViewportWidth) > 40
  const heightChanged = Math.abs(innerHeight - entryViewportHeight) > 40
  if (widthChanged || heightChanged) finishEntry()
}
function handleEntryScroll() {
  // 锁定根滚动条可能触发一次零位移 scroll；只有用户实际移动页面时才中断入场。
  if (entering.value && Math.abs(window.scrollY - entryScrollY) > 2) finishEntry()
}
function contextLost(event: Event) {
  event.preventDefault()
  stopFrames()
  available.value = false
  moving.value = false
  finishEntry()
}
onMounted(() => {
  media = matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = media.matches
  media.addEventListener('change', changeMotion)
  document.addEventListener('visibilitychange', syncPlayback)
  window.addEventListener('keydown', handleKey)
  window.addEventListener('resize', handleEntryResize)
  window.addEventListener('scroll', handleEntryScroll, { passive: true })
  canvas.value?.addEventListener('webglcontextlost', contextLost)
  observer = new IntersectionObserver(entries => { inView = entries[0]?.isIntersecting ?? false; syncPlayback() })
  if (holder.value) observer.observe(holder.value)
  asset = new Image()
  asset.onload = () => {
    if (disposed || !canvas.value) return
    renderer = createPaintedScene(canvas.value, asset!)
    available.value = Boolean(renderer)
    renderer?.draw(0)
    syncPlayback()
    void playEntry()
  }
  // 只使用室内原画，加载失败时不播放入场，不引入独立的窗框或替代场景。
  asset.src = painting
})
onBeforeUnmount(() => {
  disposed = true
  finishEntry()
  document.documentElement.classList.remove('arrival-active')
  stopFrames()
  observer?.disconnect()
  media?.removeEventListener('change', changeMotion)
  document.removeEventListener('visibilitychange', syncPlayback)
  window.removeEventListener('keydown', handleKey)
  window.removeEventListener('resize', handleEntryResize)
  window.removeEventListener('scroll', handleEntryScroll)
  canvas.value?.removeEventListener('webglcontextlost', contextLost)
  if (asset) asset.onload = null
  renderer?.dispose()
})
</script>

<template>
  <figure class="writing-scene">
    <div ref="holder" class="painting-holder">
      <div class="painting-stage" :class="{ 'scene-still': !moving }">
        <div class="painting-surface">
          <img :src="painting" width="1536" height="1024" fetchpriority="high" alt="黄昏油画：戴复古细圆框眼镜的中长发青年侧背着身静静执笔，头发遮住几乎全部面容，黑猫卧在窗台" />
          <canvas ref="canvas" v-show="available" aria-hidden="true"></canvas>
        </div>
      </div>
    </div>
    <figcaption class="painting-caption"><span>暮色 · 习作</span><div><button v-if="!reducedMotion" type="button" class="scene-control" @click="playEntry(true)">重看入场</button><button v-if="canMove" type="button" class="scene-control" :aria-pressed="paused" @click="toggleMotion">{{ paused ? '播放画面' : '暂停画面' }}</button><span v-else>静静坐一会儿</span></div></figcaption>
    <Teleport to="body">
      <div v-if="entering" ref="arrivalSequence" class="arrival-sequence" aria-hidden="true">
        <div ref="arrivalScrim" class="arrival-scrim"></div>
        <video ref="arrivalVideo" class="arrival-video" muted playsinline preload="auto" :poster="flythroughLandscape">
          <source :src="arrivalVideoMp4" type="video/mp4" />
          <source :src="arrivalVideoWebm" type="video/webm" />
        </video>
        <div ref="arrivalRoom" class="arrival-room"><img :src="painting" alt="" /></div>
        <div ref="arrivalPortal" class="arrival-portal"></div>
        <div ref="arrivalVignette" class="arrival-vignette"></div>
      </div>
      <button v-if="entering" class="skip-arrival" type="button" @click="finishEntry">跳过入场 <span aria-hidden="true">↗</span></button>
    </Teleport>
  </figure>
</template>

<style scoped>
.writing-scene { margin: 0; min-width: 0; }
.painting-holder { aspect-ratio: 3 / 2; width: 100%; position: relative; }
.painting-stage { width: 100%; height: 100%; overflow: hidden; background: #493b30; }
.painting-surface { width: 100%; height: 100%; position: relative; }
.painting-surface img, .painting-surface canvas { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; display: block; }
.painting-caption { display: flex; justify-content: space-between; align-items: center; margin-top: 14px; gap: 12px; font-size: 11px; color: #8c7e70; letter-spacing: 1px; }
.painting-caption > div { display: flex; gap: 16px; align-items: center; }
.scene-control { padding: 4px 0; background: transparent; color: #806e5b; font-size: 10px; border-radius: 0; }
.scene-control:hover { color: #984f35; text-decoration: underline; text-underline-offset: 4px; }
</style>

<style>
.arrival-active { overflow: hidden; }
.arrival-sequence { position: fixed; inset: 0; z-index: 999; overflow: hidden; contain: strict; isolation: isolate; background: #211713; pointer-events: none; }
.arrival-scrim, .arrival-video, .arrival-portal, .arrival-vignette { position: absolute; inset: 0; }
.arrival-scrim { z-index: 0; background: #211713; }
.arrival-video { z-index: 1; width: 100%; height: 100%; object-fit: cover; background: #211713; will-change: transform, opacity; }
.arrival-room { position: fixed; z-index: 2; overflow: hidden; background: #493b30; transform-origin: center; will-change: transform, opacity; }
.arrival-room img { width: 100%; height: 100%; display: block; object-fit: cover; }
.arrival-portal {
  z-index: 3;
  background:
    repeating-linear-gradient(101deg, transparent 0 5%, #21150ee8 7% 11%, transparent 14% 19%),
    radial-gradient(ellipse at 52% 48%, transparent 5%, #bd78432e 25%, #382117d9 64%, #160f0ded 100%);
  transform-origin: 52% 48%;
  will-change: transform, opacity;
}
.arrival-vignette { z-index: 4; background: radial-gradient(ellipse at 50% 48%, transparent 42%, #17100dcc 100%); pointer-events: none; will-change: opacity; }
.skip-arrival { position: fixed; right: 26px; bottom: 24px; z-index: 1001; border: 1px solid #f7ead26b; padding: 13px 20px; background: #30291fce; color: #fff5e7; gap: 24px; }
.skip-arrival:hover { background: #514332; }
@media (prefers-reduced-motion: reduce) { .arrival-sequence { display: none; } }
</style>
