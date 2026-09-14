<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import runningCatUrl from './assets/cat/cat-running-v2.png'
import sleepingCatUrl from './assets/cat/cat-sleeping-v2.png'
import meowUrl from './assets/audio/happy-cat-meow.mp3'

type CatState = 'running' | 'sleeping'

const RUN_DURATION_MS = 9_000
const SLEEP_DURATION_MS = 12_000

const catState = ref<CatState>('running')
const message = ref('一起跑一圈～')
const isReacting = ref(false)
const isDragging = ref(false)

const catImageUrl = computed(() => (
  catState.value === 'running' ? runningCatUrl : sleepingCatUrl
))
const catLabel = computed(() => (
  catState.value === 'running' ? '正在奔跑的黑色小猫' : '正在睡觉的黑色小猫'
))

let meowAudio: HTMLAudioElement | null = null
let stateTimer: number | undefined
let clickTimer: number | undefined
let reactionTimer: number | undefined
let messageTimer: number | undefined
let isIgnoringMouseEvents = false
let activePointerId: number | undefined
let dragStartScreenX = 0
let dragStartScreenY = 0
let lastPointerScreenX = 0
let lastPointerScreenY = 0
let suppressNextClick = false

const DRAG_THRESHOLD_PX = 4

function setMousePassThrough(ignore: boolean): void {
  if (ignore === isIgnoringMouseEvents) return
  isIgnoringMouseEvents = ignore
  window.desktopCat.setIgnoreMouseEvents(ignore)
}

function handleMouseMove(event: MouseEvent): void {
  const target = event.target
  const isInteractive = target instanceof Element && target.closest('[data-interactive]') !== null
  setMousePassThrough(!isInteractive)
}

function handleMouseLeave(): void {
  setMousePassThrough(true)
}

function playMeow(): void {
  meowAudio ??= new Audio(meowUrl)
  meowAudio.pause()
  meowAudio.currentTime = 0
  meowAudio.volume = 0.58
  void meowAudio.play().catch((error: unknown) => {
    console.warn('Failed to play the cat meow.', error)
  })
}

function clearStateTimer(): void {
  if (!stateTimer) return
  window.clearTimeout(stateTimer)
  stateTimer = undefined
}

function scheduleNextState(): void {
  clearStateTimer()
  const delay = catState.value === 'running' ? RUN_DURATION_MS : SLEEP_DURATION_MS
  stateTimer = window.setTimeout(() => {
    setCatState(catState.value === 'running' ? 'sleeping' : 'running')
  }, delay)
}

function setCatState(nextState: CatState, customMessage?: string): void {
  catState.value = nextState
  message.value = customMessage ?? (
    nextState === 'running' ? '睡醒啦，去跑一圈！' : '跑累了，呼噜呼噜…'
  )
  scheduleNextState()
}

function toggleState(): void {
  setCatState(catState.value === 'running' ? 'sleeping' : 'running')
}

function reactToTouch(): void {
  playMeow()
  isReacting.value = false

  if (reactionTimer) window.clearTimeout(reactionTimer)
  if (messageTimer) window.clearTimeout(messageTimer)

  if (catState.value === 'sleeping') {
    setCatState('running', '你把我叫醒啦，喵～')
  } else {
    message.value = '喵！快跟上我～'
    scheduleNextState()
  }

  window.requestAnimationFrame(() => {
    isReacting.value = true
  })
  reactionTimer = window.setTimeout(() => {
    isReacting.value = false
  }, 480)
  messageTimer = window.setTimeout(() => {
    message.value = catState.value === 'running' ? '一起跑一圈～' : '呼噜呼噜…'
  }, 2_200)
}

function handleCatClick(): void {
  if (suppressNextClick) {
    suppressNextClick = false
    return
  }

  if (clickTimer) return
  clickTimer = window.setTimeout(() => {
    clickTimer = undefined
    reactToTouch()
  }, 220)
}

function handleCatPointerDown(event: PointerEvent): void {
  if (event.button !== 0 || activePointerId !== undefined) return

  activePointerId = event.pointerId
  dragStartScreenX = event.screenX
  dragStartScreenY = event.screenY
  lastPointerScreenX = event.screenX
  lastPointerScreenY = event.screenY
  isDragging.value = false
  setMousePassThrough(false)
  event.currentTarget instanceof Element && event.currentTarget.setPointerCapture(event.pointerId)
}

function handleCatPointerMove(event: PointerEvent): void {
  if (event.pointerId !== activePointerId) return

  const totalX = event.screenX - dragStartScreenX
  const totalY = event.screenY - dragStartScreenY
  if (!isDragging.value && Math.hypot(totalX, totalY) < DRAG_THRESHOLD_PX) return

  isDragging.value = true
  const deltaX = event.screenX - lastPointerScreenX
  const deltaY = event.screenY - lastPointerScreenY
  lastPointerScreenX = event.screenX
  lastPointerScreenY = event.screenY

  if (deltaX !== 0 || deltaY !== 0) {
    window.desktopCat.moveWindowBy(deltaX, deltaY)
  }
}

function finishCatDrag(event: PointerEvent): void {
  if (event.pointerId !== activePointerId) return

  const target = event.currentTarget
  if (target instanceof Element && target.hasPointerCapture(event.pointerId)) {
    target.releasePointerCapture(event.pointerId)
  }

  if (isDragging.value) {
    suppressNextClick = true
    window.setTimeout(() => {
      suppressNextClick = false
    }, 0)
  }

  activePointerId = undefined
  isDragging.value = false
}

function handleCatDoubleClick(): void {
  if (clickTimer) {
    window.clearTimeout(clickTimer)
    clickTimer = undefined
  }
  toggleState()
}

onMounted(() => {
  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseleave', handleMouseLeave)
  scheduleNextState()
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseleave', handleMouseLeave)
  clearStateTimer()
  if (clickTimer) window.clearTimeout(clickTimer)
  if (reactionTimer) window.clearTimeout(reactionTimer)
  if (messageTimer) window.clearTimeout(messageTimer)
  if (meowAudio) {
    meowAudio.pause()
    meowAudio.src = ''
  }
})
</script>

<template>
  <main class="desktop-pet" :class="`state-${catState}`">
    <p
      class="speech"
      role="status"
      data-interactive
      title="按住气泡拖动小猫"
    >{{ message }}</p>
    <div class="drag-handle" data-interactive title="按住这里拖动小猫" aria-label="拖动小猫" />

    <button
      class="cat-button"
      :class="[catState, { reacting: isReacting, dragging: isDragging }]"
      type="button"
      data-interactive
      :aria-label="catLabel"
      title="拖动身体移动；单击听猫叫；双击切换奔跑和睡觉"
      @pointerdown="handleCatPointerDown"
      @pointermove="handleCatPointerMove"
      @pointerup="finishCatDrag"
      @pointercancel="finishCatDrag"
      @click="handleCatClick"
      @dblclick="handleCatDoubleClick"
    >
      <img class="cat-image" :src="catImageUrl" :alt="catLabel" draggable="false" />
    </button>

    <div v-if="catState === 'running'" class="speed-lines" aria-hidden="true">
      <i /><i /><i />
    </div>
    <div v-else class="sleep-marks" aria-hidden="true">
      <i>z</i><i>Z</i>
    </div>
  </main>
</template>

<style scoped>
.desktop-pet {
  position: relative;
  width: 100%;
  height: 100%;
}

.speech {
  position: absolute;
  z-index: 6;
  top: 4px;
  left: 50%;
  min-width: 126px;
  max-width: 188px;
  margin: 0;
  padding: 7px 11px;
  transform: translateX(-50%);
  border: 1px solid rgb(206 168 119 / 62%);
  border-radius: 15px;
  background: rgb(255 250 239 / 94%);
  box-shadow: 0 4px 12px rgb(38 29 28 / 16%);
  color: #564441;
  font-size: 11px;
  line-height: 1.25;
  text-align: center;
  cursor: grab;
  pointer-events: auto;
  -webkit-app-region: drag;
  app-region: drag;
}

.speech:active {
  cursor: grabbing;
}

.speech::after {
  position: absolute;
  bottom: -6px;
  left: calc(50% - 6px);
  width: 10px;
  height: 10px;
  transform: rotate(45deg);
  border-right: 1px solid rgb(206 168 119 / 62%);
  border-bottom: 1px solid rgb(206 168 119 / 62%);
  background: rgb(255 250 239 / 94%);
  content: "";
}

.drag-handle {
  position: absolute;
  z-index: 7;
  top: 38px;
  left: 50px;
  width: 120px;
  height: 34px;
  border-radius: 10px;
  cursor: grab;
  -webkit-app-region: drag;
  app-region: drag;
}

.drag-handle:active {
  cursor: grabbing;
}

.cat-button {
  position: absolute;
  z-index: 3;
  bottom: 9px;
  left: 5px;
  width: 210px;
  height: 154px;
  margin: 0;
  padding: 0;
  transform-origin: 50% 78%;
  border: 0;
  outline: 0;
  background: transparent;
  cursor: grab;
  touch-action: none;
  -webkit-app-region: no-drag;
  app-region: no-drag;
}

.cat-button.dragging {
  cursor: grabbing;
}

.cat-button.running {
  clip-path: polygon(0 15%, 28% 3%, 47% 15%, 56% 0, 93% 8%, 100% 45%, 93% 95%, 57% 100%, 37% 88%, 10% 97%);
}

.cat-button.sleeping {
  clip-path: ellipse(49% 43% at 50% 57%);
}

.cat-button:focus-visible {
  filter: drop-shadow(0 0 6px rgb(255 211 98 / 90%));
}

.cat-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
  user-select: none;
}

.cat-button.running .cat-image {
  animation: run-cycle 560ms cubic-bezier(.45, 0, .55, 1) infinite;
}

.cat-button.sleeping .cat-image {
  transform-origin: 55% 78%;
  animation: sleep-breathe 3.6s ease-in-out infinite;
}

.cat-button.reacting .cat-image {
  animation: touch-pop 480ms cubic-bezier(.2, .85, .3, 1);
}

.speed-lines {
  position: absolute;
  z-index: 2;
  bottom: 62px;
  left: 5px;
  width: 48px;
  pointer-events: none;
}

.speed-lines i {
  display: block;
  width: 32px;
  height: 2px;
  margin-top: 8px;
  border-radius: 2px;
  background: rgb(238 179 74 / 52%);
  animation: speed-line 760ms ease-out infinite;
}

.speed-lines i:nth-child(2) {
  width: 44px;
  animation-delay: -240ms;
}

.speed-lines i:nth-child(3) {
  width: 24px;
  animation-delay: -480ms;
}

.sleep-marks {
  position: absolute;
  z-index: 5;
  top: 65px;
  right: 18px;
  color: rgb(243 184 70 / 88%);
  font-family: Georgia, serif;
  font-weight: 700;
  pointer-events: none;
}

.sleep-marks i {
  position: absolute;
  font-style: normal;
  animation: float-z 2.4s ease-in-out infinite;
}

.sleep-marks i:first-child {
  top: 18px;
  right: 12px;
  font-size: 14px;
}

.sleep-marks i:last-child {
  top: 0;
  right: 0;
  font-size: 20px;
  animation-delay: -1.2s;
}

@keyframes run-cycle {
  0%, 100% { transform: translate(-3px, 1px) rotate(-1deg) scaleX(1); }
  50% { transform: translate(4px, -5px) rotate(1.5deg) scaleX(1.015); }
}

@keyframes sleep-breathe {
  0%, 100% { transform: translateY(1px) scale(1); }
  50% { transform: translateY(3px) scale(1.012, .988); }
}

@keyframes touch-pop {
  0% { transform: translateY(0) scale(1); }
  45% { transform: translateY(-10px) scale(1.04) rotate(-2deg); }
  75% { transform: translateY(-3px) scale(.99) rotate(1deg); }
  100% { transform: translateY(0) scale(1); }
}

@keyframes speed-line {
  from { transform: translateX(18px) scaleX(.45); opacity: 0; }
  35% { opacity: .8; }
  to { transform: translateX(-8px) scaleX(1); opacity: 0; }
}

@keyframes float-z {
  0%, 100% { transform: translate(0, 3px) scale(.9); opacity: .25; }
  50% { transform: translate(4px, -5px) scale(1.08); opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .cat-image,
  .speed-lines i,
  .sleep-marks i {
    animation: none !important;
  }
}
</style>
