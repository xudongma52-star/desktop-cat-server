<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { gsap } from 'gsap'

const props = defineProps<{ title: string | null; text: string }>()
const root = ref<HTMLElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
function textRuns(value: string) {
  const runs: string[] = []
  for (const char of Array.from(value)) {
    const last = runs.at(-1)
    // 标点跟随前一个字，英文单词也保持连续，避免逐字动画破坏自然换行。
    if (last && last !== '\n' && /[，。！？、；：）》】」』…,.!?;:)]/.test(char)) runs[runs.length - 1] += char
    else if (last && /^[a-zA-Z0-9]+$/.test(last) && /^[a-zA-Z0-9]$/.test(char)) runs[runs.length - 1] += char
    else runs.push(char)
  }
  return runs
}
const titleRuns = computed(() => textRuns(props.title ?? ''))
const bodyRuns = computed(() => textRuns(props.text))
const padding = 160

interface Glyph {
  node: HTMLElement; char: string; x: number; y: number; width: number; height: number
  font: string; size: number
}
interface Grain {
  x: number; y: number; vx: number; vy: number; phase: number
  size: number; opacity: number; start: number; life: number; pressure: number
}
interface Infection { pressure: number; timeline: gsap.core.Timeline }
let glyphs: Glyph[] = []
let grains: Grain[] = []
const infections = new Map<HTMLElement, Infection>()
const grainShapes = new Map<string, { x: number; y: number }[]>()
let samples: { x: number; y: number; time: number }[] = []
let context: CanvasRenderingContext2D | null = null
let observer: ResizeObserver | undefined
let motionQuery: MediaQueryList | undefined
let reduceMotion = false
let disposed = false
let frame = 0
let width = 0
let height = 0
let leftPadding = padding
let inkColor = '#292d25'

function clearEffects() {
  for (const { timeline } of infections.values()) timeline.kill()
  infections.clear()
  for (const glyph of glyphs) gsap.set(glyph.node, { clearProps: 'transform,opacity,filter,textShadow,willChange' })
  grains = []
  samples = []
  cancelAnimationFrame(frame)
  frame = 0
  context?.clearRect(0, 0, width, height)
}

function measure() {
  if (!root.value || !canvas.value || disposed) return
  clearEffects()
  const box = root.value.getBoundingClientRect()
  // 所有字的位置一次读取；鼠标移动和逐帧绘制不再逐字读取布局。
  glyphs = Array.from(root.value.querySelectorAll<HTMLElement>('.ink-letter'))
    .filter(node => node.textContent?.trim())
    .map(node => {
      const rect = node.getBoundingClientRect()
      const style = getComputedStyle(node)
      return { node, char: node.textContent!, x: rect.left - box.left + rect.width / 2,
        y: rect.top - box.top + rect.height / 2, width: rect.width, height: rect.height,
        font: `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`, size: parseFloat(style.fontSize) }
    })
  // 只在视口最外沿结束绘制；文字四周留出扩散空间，同时不制造横向滚动条。
  leftPadding = Math.min(padding, Math.max(0, box.left))
  const rightPadding = Math.min(padding, Math.max(0, document.documentElement.clientWidth - box.right))
  width = Math.floor(box.width + leftPadding + rightPadding)
  height = Math.ceil(box.height + padding * 2)
  const density = Math.min(devicePixelRatio, 2)
  canvas.value.width = Math.ceil(width * density)
  canvas.value.height = Math.ceil(height * density)
  canvas.value.style.width = `${width}px`
  canvas.value.style.height = `${height}px`
  canvas.value.style.left = `${-leftPadding}px`
  context = canvas.value.getContext('2d')
  context?.setTransform(density, 0, 0, density, 0, 0)
  inkColor = getComputedStyle(root.value).color
  grainShapes.clear()
}

function shapeFor(glyph: Glyph) {
  const key = `${glyph.font}:${glyph.char}`
  const cached = grainShapes.get(key)
  if (cached) return cached
  const bitmap = document.createElement('canvas')
  bitmap.width = Math.ceil(glyph.size * 1.6)
  bitmap.height = Math.ceil(glyph.size * 1.8)
  const paint = bitmap.getContext('2d', { willReadFrequently: true })!
  paint.font = glyph.font
  paint.textAlign = 'center'
  paint.textBaseline = 'middle'
  paint.fillText(glyph.char, bitmap.width / 2, bitmap.height / 2)
  const pixels = paint.getImageData(0, 0, bitmap.width, bitmap.height).data
  const points: { x: number; y: number }[] = []
  for (let y = 1; y < bitmap.height; y += 2) {
    for (let x = 1; x < bitmap.width; x += 2) {
      if (pixels[(y * bitmap.width + x) * 4 + 3]! > 50) {
        points.push({ x: x - bitmap.width / 2, y: y - bitmap.height / 2 })
      }
    }
  }
  grainShapes.set(key, points)
  return points
}

function releaseInk(glyph: Glyph, pressure: number, start: number, direction: number) {
  const shape = shapeFor(glyph)
  const count = Math.min(shape.length, Math.round(8 + pressure * 42))
  for (let index = 0; index < count; index++) {
    const point = shape[Math.floor(Math.random() * shape.length)]!
    const angle = Math.random() * Math.PI * 2
    const speed = 6 + Math.random() * 24 + pressure * 24
    grains.push({ x: glyph.x + point.x + leftPadding, y: glyph.y + point.y + padding,
      vx: Math.cos(angle) * speed + direction * pressure * 24,
      vy: Math.sin(angle) * speed - pressure * 15,
      phase: Math.random() * Math.PI * 2, size: .35 + Math.pow(Math.random(), 3) * 1.6,
      opacity: .12 + pressure * .45, start: start + Math.random() * 160,
      life: 1250 + pressure * 650 + Math.random() * 500, pressure })
  }
  if (!frame && grains.length) frame = requestAnimationFrame(drawInk)
}

function drawInk(now: number) {
  frame = 0
  if (!context || disposed) return
  context.clearRect(0, 0, width, height)
  context.fillStyle = inkColor
  context.strokeStyle = inkColor
  grains = grains.filter(grain => now < grain.start + grain.life)
  for (const grain of grains) {
    const age = now - grain.start
    if (age < 0) continue
    const progress = age / grain.life
    const seconds = age / 1000
    // 墨点来自笔画本身，带轻微涡流向外散开，不绘制黑色区域或矩形遮罩。
    const drift = 1 - Math.exp(-seconds * 1.5)
    const wave = Math.sin(grain.phase + seconds * 3.4) - Math.sin(grain.phase)
    const x = grain.x + grain.vx * drift + wave * grain.pressure * 8
    const y = grain.y + grain.vy * drift + Math.cos(grain.phase + seconds * 2.6) * grain.pressure * seconds * 9
    context.globalAlpha = grain.opacity * Math.min(1, age / 120) * Math.pow(1 - progress, 1.6)
    context.beginPath()
    context.ellipse(x, y, grain.size * (1 + progress * 1.4), grain.size * .65, grain.phase, 0, Math.PI * 2)
    context.fill()
    if (grain.pressure > .6 && progress < .65) {
      context.globalAlpha *= .3
      context.lineWidth = .45
      context.beginPath()
      context.moveTo(x, y)
      context.quadraticCurveTo(x - wave * 2, y + 2, x - grain.vx * .07, y - grain.vy * .07)
      context.stroke()
    }
  }
  context.globalAlpha = 1
  if (grains.length) frame = requestAnimationFrame(drawInk)
}

function infect(glyph: Glyph, pressure: number, delay: number, direction: number, now: number) {
  const existing = infections.get(glyph.node)
  // 同一笔划过只在力度明显增加时升级，避免每个 pointermove 重建动画。
  if (existing && pressure <= existing.pressure + .16) return
  existing?.timeline.kill()
  const dissolving = pressure > .42
  const loss = dissolving ? Math.min(1, (pressure - .3) / .48) : pressure * .12
  const spreadTime = dissolving ? .8 - pressure * .48 : .35
  const holdTime = dissolving ? 1.25 + pressure * .45 : .45
  const timeline = gsap.timeline({ delay, onComplete: () => {
    infections.delete(glyph.node)
    gsap.set(glyph.node, { clearProps: 'transform,opacity,filter,textShadow,willChange' })
  } })
  infections.set(glyph.node, { pressure, timeline })
  glyph.node.style.willChange = 'transform,opacity'
  timeline.to(glyph.node, { opacity: 1 - loss, filter: `blur(${dissolving ? 1 + pressure * 3 : .25 + pressure * 2}px)`,
    textShadow: `0 0 ${1 + pressure * 3}px rgba(41,45,37,${pressure * .3})`,
    x: dissolving ? direction * pressure * 5 : 0,
    y: dissolving ? -pressure * (2 + Math.random() * 4) : 0,
    duration: spreadTime, ease: 'power2.out' }, 0)
  timeline.to(glyph.node, { opacity: 1, filter: 'blur(0px)', textShadow: '0 0 0px rgba(41,45,37,0)',
    x: 0, y: 0, duration: dissolving ? 1.45 : .9, ease: 'sine.inOut' }, spreadTime + holdTime)
  if (dissolving) releaseInk(glyph, pressure, now + delay * 1000 + 40, direction)
}

function move(event: PointerEvent) {
  if (event.pointerType === 'touch' || reduceMotion || !root.value || !glyphs.length) return
  const box = root.value.getBoundingClientRect()
  const x = event.clientX - box.left
  const y = event.clientY - box.top
  const now = performance.now()
  const previous = samples.at(-1)
  samples = samples.filter(sample => now - sample.time < 220)
  samples.push({ x, y, time: now })
  const speed = previous && now - previous.time < 250
    ? Math.hypot(x - previous.x, y - previous.y) / Math.max(8, now - previous.time) : 0
  const amplitude = Math.max(...samples.map(sample => sample.x)) - Math.min(...samples.map(sample => sample.x))
  // 快速左右扫动需要速度和幅度同时成立；小范围抖动只让墨边轻轻洇开。
  const pressure = Math.min(1, .12 + Math.min(1, speed / 1.1) * Math.min(1, amplitude / 65))
  const nearest = glyphs.reduce((best, glyph) => {
    const distance = Math.hypot(Math.max(0, Math.abs(x - glyph.x) - glyph.width / 2),
      Math.max(0, Math.abs(y - glyph.y) - glyph.height / 2))
    return distance < best.distance ? { glyph, distance } : best
  }, { glyph: glyphs[0]!, distance: Infinity })
  if (nearest.distance > nearest.glyph.size * .55) return
  const radius = nearest.glyph.size * .75 + pressure * 110
  const direction = previous ? Math.sign(x - previous.x) : 0
  for (const glyph of glyphs) {
    const distance = Math.hypot(glyph.x - nearest.glyph.x, glyph.y - nearest.glyph.y)
    if (distance > radius) continue
    infect(glyph, pressure * (1 - .4 * distance / radius), pressure > .42 ? distance / radius * .5 : 0, direction, now)
  }
}

function motionChanged(event: MediaQueryListEvent) { reduceMotion = event.matches; if (reduceMotion) clearEffects() }
onMounted(() => {
  motionQuery = matchMedia('(prefers-reduced-motion: reduce)')
  reduceMotion = motionQuery.matches
  motionQuery.addEventListener('change', motionChanged)
  observer = new ResizeObserver(measure)
  observer.observe(root.value!)
  void document.fonts.ready.then(() => { if (!disposed) measure() })
})
watch(() => [props.title, props.text], async () => { await nextTick(); measure() })
onBeforeUnmount(() => {
  disposed = true
  observer?.disconnect()
  motionQuery?.removeEventListener('change', motionChanged)
  clearEffects()
})
</script>

<template>
  <div ref="root" class="ink-dissolve-text" @pointermove="move" @pointerleave="samples = []">
    <h2 v-if="title"><span v-for="(run, index) in titleRuns" :key="index" class="ink-run"><span v-for="(char, offset) in Array.from(run)" :key="offset" class="ink-letter">{{ char }}</span></span></h2>
    <p><template v-for="(run, index) in bodyRuns" :key="index"><br v-if="run === '\n'" /><span v-else class="ink-run"><span v-for="(char, offset) in Array.from(run)" :key="offset" class="ink-letter">{{ char }}</span></span></template></p>
    <canvas ref="canvas" class="ink-grains" aria-hidden="true" />
  </div>
</template>

<style scoped>
.ink-dissolve-text { position: relative; overflow: visible; }
h2 { margin: 0 0 22px; font-size: .7em; font-weight: 400; line-height: 1.5; }
p { margin: 0; overflow-wrap: anywhere; }
.ink-run { display: inline-block; max-width: 100%; }
.ink-letter { display: inline-block; white-space: pre; transform-origin: center; }
.ink-grains { position: absolute; left: -160px; top: -160px; pointer-events: none; background: transparent; }
</style>
