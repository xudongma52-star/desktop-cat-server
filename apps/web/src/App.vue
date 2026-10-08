<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { gsap } from 'gsap'
import { RouterLink, RouterView } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import BrandMark from './components/BrandMark.vue'
import BackgroundMusic from './components/BackgroundMusic.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loggingOut = ref(false)
const authLayout = computed(() => route.meta.layout === 'auth' || !route.name)
const homeLayout = computed(() => route.name === 'home')
const botanicalMobile = ref(window.innerWidth < 700)
// 只在跨越布局断点时重建首页背景；开屏、菜单与业务页面状态留在父组件。
function updateBotanicalMode() {
  botanicalMobile.value = window.innerWidth < 700
}
const shell = ref<HTMLElement | null>(null)
const main = ref<HTMLElement | null>(null)
const menu = ref<HTMLElement | null>(null)
const menuButton = ref<HTMLButtonElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)
const menuOpen = ref(false)
const headerScrolled = ref(false)
const reducedMotion = ref(window.matchMedia('(prefers-reduced-motion: reduce)').matches)
const sceneState = reactive({ menu: 0, content: homeLayout.value ? 0 : 1, scroll: 0, hover: 0, reveal: 0, openingTone: 2 })
const CornerScene = defineAsyncComponent(() => import('./components/CornerScene.vue'))
const BotanicalScene = defineAsyncComponent(() => import('./components/BotanicalScene.vue'))
const displayedProgress = reactive({ value: 0 })
const sceneReady = ref(false)
const sceneRequested = ref(false)
const sceneFailed = ref(false)
const skipOpening = ref(false)
const openingPhase = ref<'pending' | 'loading' | 'revealing' | 'complete'>('pending')
const openingQuote = '判定认识或理论之是否真理，不是依主观上觉得如何而定……'
const openingCharacters = Array.from(openingQuote)
let openingContext: gsap.Context | undefined
let openingStarted = false
let brandReady = false
let preludeDone = false
let progressStarted = false
let progressTween: gsap.core.Tween | undefined
const loadingPercent = computed(() => Math.min(sceneReady.value ? 100 : 99, Math.round(displayedProgress.value)))
// 文字完整显露后才开始开屏计数；场景首帧未就绪时停在 99，快缓存也不会直接亮出 100。
function updateDisplayedProgress() {
  if (!progressStarted || openingPhase.value !== 'loading' || !openingContext) return
  const target = sceneReady.value ? 100 : 99
  openingContext.add(() => {
    progressTween?.kill()
    progressTween = gsap.to(displayedProgress, {
      value: Math.max(displayedProgress.value, target),
      duration: Math.max(.45, (target - displayedProgress.value) / 55),
      ease: 'power1.out',
      onComplete: () => { void revealOpening() },
    })
  })
}
watch(sceneReady, updateDisplayedProgress)
// 网页氛围与三维墙面共用色彩阶段；黑色品牌层退去后接上原墙面，开屏计数独立推进。
const openingPaper = computed(() => {
  const tone = sceneState.openingTone
  return tone < 1 ? gsap.utils.interpolate('#c0cbbc', '#f1e4ce', tone) : gsap.utils.interpolate('#f1e4ce', '#e9e7e1', tone - 1)
})

function rememberOpening() {
  if (sceneReady.value && homeLayout.value) sessionStorage.setItem('corner-wall-visited', '1')
}

function endOpening() {
  // 用户已进入、打开菜单或换页后，晚到资源只补画面，不重新播放或抢焦点。
  openingContext?.revert()
  openingContext = undefined
  progressTween = undefined
  skipOpening.value = true
  sceneState.reveal = 1
  sceneState.openingTone = 2
  openingPhase.value = 'complete'
  rememberOpening()
}

async function finishOpening(result: { failed: boolean }) {
  sceneReady.value = true
  sceneFailed.value = result.failed
  if (openingPhase.value !== 'loading' || !homeLayout.value || skipOpening.value || menuOpen.value || reducedMotion.value) {
    if (openingPhase.value !== 'complete') endOpening()
    else rememberOpening()
    return
  }
  await revealOpening()
}

function onBrandReady() {
  brandReady = true
  void startOpening()
}

async function startOpening() {
  if (!brandReady || openingStarted || openingPhase.value !== 'loading' || !shell.value) return
  if (reducedMotion.value) { endOpening(); return }
  await nextTick()
  // 只加载开屏使用的本地字体子集；失败时沿用宋体回退，不能阻止进入首页。
  await document.fonts.load('400 28px "Corner Opening Serif"', openingQuote).catch(() => [])
  if (openingStarted || openingPhase.value !== 'loading') return
  openingStarted = true
  const copy = shell.value?.querySelector('.corner-loading-copy')
  if (!copy) { endOpening(); return }
  openingContext = gsap.context(() => {
    // 参考共享开屏：黑场、横向品牌显露、由左至右错峰渐亮、完整阅读停顿。
    // 同会话刷新也保留这一品牌节奏；慢资源留在自然停顿，准备好首帧才完成计数与揭幕。
    // 黑场等待保留原来的四分之一；渐显时长加倍，文字完整显露后才计数与初始化三维场景，避免卡住半个 Logo。
    const logoDelay = .525
    const copyComplete = logoDelay + .15 + 2
    gsap.timeline({ onComplete: () => { preludeDone = true; void revealOpening() } })
      .fromTo(copy.querySelector('.corner-loading-identity'),
        { autoAlpha: 0, clipPath: 'inset(0 100% 0 0)' },
        { autoAlpha: 1, clipPath: 'inset(0 0% 0 0)', duration: 1.3, ease: 'none' }, logoDelay)
      .to(copy.querySelectorAll('.corner-loading-character'), { opacity: 1, duration: 1.6, stagger: { amount: .4 }, ease: 'sine.inOut' }, logoDelay + .15)
      .to(copy.querySelector('.corner-loading-progress'), { opacity: .5, duration: .35, ease: 'sine.inOut', onStart: () => { progressStarted = true; updateDisplayedProgress() } }, copyComplete)
      .call(() => { sceneRequested.value = true }, [], copyComplete + .1)
      .to({}, { duration: .4 }, copyComplete + .1)
  }, shell.value!)
}

async function revealOpening() {
  if (!preludeDone || !sceneReady.value || displayedProgress.value < 99.99 || openingPhase.value !== 'loading') return
  if (!homeLayout.value || menuOpen.value || reducedMotion.value) { endOpening(); return }
  openingPhase.value = 'revealing'
  await nextTick()
  if (openingPhase.value !== 'revealing' || !homeLayout.value || menuOpen.value) return
  const intro = main.value?.querySelector('.corner-intro')
  if (!intro) { endOpening(); return }
  const duration = 1.4
  // 首帧准备好才接续揭幕；标题与背景共用时序，避免提前播完或再追加一段完整前奏。
  openingContext?.add(() => {
    gsap.timeline({ onComplete: () => { openingPhase.value = 'complete'; rememberOpening() } })
      .to(sceneState, { reveal: 1, openingTone: 2, duration, ease: 'sine.inOut' }, 0)
      .to(shell.value!.querySelectorAll('.corner-loading-character'), { opacity: 0, duration: 1.15, stagger: { amount: .15, from: 'end' }, ease: 'sine.inOut' }, 0)
      .to(shell.value!.querySelector('.corner-loading-identity'), { autoAlpha: 0, clipPath: 'inset(0 100% 0 0)', duration: .65, ease: 'none' }, .6)
      .to(shell.value!.querySelector('.corner-loading-progress'), { opacity: 0, duration: .45 }, .8)
      .to(shell.value!.querySelector('.corner-loading'), { autoAlpha: 0, duration: .9, ease: 'sine.inOut' }, .4)
      .fromTo(intro.querySelector('.corner-kicker'), { autoAlpha: 0, y: 10 },
        { autoAlpha: 1, y: 0, duration: duration * .42, clearProps: 'opacity,visibility,transform' }, duration * .22)
      .fromTo(intro.querySelectorAll('.corner-title-text'),
        { autoAlpha: 0, yPercent: 100, rotateX: 14, z: -24, transformOrigin: '50% 100%' },
        { autoAlpha: 1, yPercent: 0, rotateX: 0, z: 0, duration: duration * .68, stagger: duration * .10, ease: 'power3.out', clearProps: 'opacity,visibility,transform,transformOrigin' }, duration * .17)
      .fromTo(intro.querySelectorAll('.corner-intro-description, .corner-intro-actions, .corner-companion-caption'),
        { autoAlpha: 0, y: 8 },
        { autoAlpha: 1, y: 0, duration: duration * .37, stagger: duration * .035, ease: 'power2.out', clearProps: 'opacity,visibility,transform' }, duration * .48)
  })
}
const primaryEntries = [
  { to: '/records', label: '我的记录', note: '把日子留下', number: '一' },
  { to: '/captures', label: '随手记', note: '接住一闪而过', number: '二' },
  { to: '/knowledge', label: '知识库', note: '让想法有处安放', number: '三' },
]
let menuTimeline: gsap.core.Timeline | undefined
let context: gsap.Context | undefined
let previousOverflow: string | undefined
let media: MediaQueryList | undefined
let scrollFrame = 0
let focusFrame = 0
let restoreMenuFocus = false
let focusContentAfterNavigation = false
const pageAnimations = new Map<Element, gsap.Context>()

function focusContent() {
  if (!focusContentAfterNavigation || menuOpen.value) return
  const title = homeLayout.value ? menuButton.value : main.value?.querySelector<HTMLElement>('h1')
  if (!title) return
  if (!homeLayout.value) title.setAttribute('tabindex', '-1')
  focusWhenVisible(title, () => focusContentAfterNavigation && !menuOpen.value, () => { focusContentAfterNavigation = false })
}

function unlockScroll() {
  if (previousOverflow !== undefined) document.body.style.overflow = previousOverflow
  previousOverflow = undefined
}

function finishNavigationClose() {
  if (menuOpen.value) return
  void nextTick(() => {
    if (menuOpen.value) return
    focusContent()
    if (restoreMenuFocus) focusWhenVisible(menuButton.value, () => !menuOpen.value)
    restoreMenuFocus = false
  })
}

function focusWhenVisible(element: HTMLElement | null, stillNeeded: () => boolean, onFocused?: () => void) {
  cancelAnimationFrame(focusFrame)
  if (!element) return
  const focusRoute = route.fullPath
  const previousFocus = document.activeElement
  let attempts = 0
  // 可见性落地后才聚焦；反向、换页或用户移动焦点都会取消旧目标，只重试短暂的渲染交接。
  function focus() {
    focusFrame = 0
    if (!element!.isConnected || !stillNeeded() || route.fullPath !== focusRoute || attempts++ >= 10) return
    if (document.activeElement !== previousFocus && document.activeElement !== document.body && document.activeElement !== element) return
    if (element!.closest('[inert]') || getComputedStyle(element!).visibility === 'hidden' || !element!.getClientRects().length) {
      focusFrame = requestAnimationFrame(focus)
      return
    }
    element!.focus({ preventScroll: true })
    if (document.activeElement === element) onFocused?.()
    else focusFrame = requestAnimationFrame(focus)
  }
  focusFrame = requestAnimationFrame(focus)
}

function animateNavigation(open: boolean) {
  if (!menu.value || !main.value) return
  if (!menuTimeline) context?.add(() => {
    menuTimeline = gsap.timeline({ paused: true, defaults: { ease: 'power2.out' }, onReverseComplete: finishNavigationClose })
      .addLabel('scene', 0)
      .to(sceneState, { menu: 1, duration: .44 }, 'scene')
      // 阅读层在同一时间轴里依次交接，场景的明暗变化保持连续。
      .to([main.value, shell.value!.querySelector('.corner-header'), shell.value!.querySelector('.site-footer')].filter(Boolean), { autoAlpha: 0, y: -12, duration: .1 }, 'scene')
      .to(menu.value, { autoAlpha: 1, duration: .16 }, 'scene')
      .fromTo(menu.value!.querySelectorAll('[data-menu-reveal]'),
        { y: 28, opacity: 0 },
        { y: 0, opacity: 1, duration: .3, stagger: .02, ease: 'power3.out' }, 'scene+=0.02')
  })
  // 复用并反向播放当前进度，快速开关不会重置文字位置或遗留暗色场景。
  if (reducedMotion.value) {
    menuTimeline?.progress(open ? 1 : 0, true).pause()
    if (!open) finishNavigationClose()
  }
  else if (open) menuTimeline?.play()
  else {
    menuTimeline?.reverse()
    if (menuTimeline?.time() === 0) finishNavigationClose()
  }
}

async function openNavigation() {
  if (menuOpen.value) { closeNavigation(); return }
  if (openingPhase.value !== 'complete') endOpening()
  menuOpen.value = true
  restoreMenuFocus = false
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  await nextTick()
  animateNavigation(true)
  focusWhenVisible(closeButton.value, () => menuOpen.value)
}

function closeNavigation(restoreFocus = true) {
  if (!menuOpen.value) return
  menuOpen.value = false
  restoreMenuFocus = restoreFocus
  sceneState.hover = 0
  unlockScroll()
  animateNavigation(false)
}

function trapMenuFocus(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); closeNavigation(); return }
  if (event.key !== 'Tab' || !menu.value) return
  const controls = [...menu.value.querySelectorAll<HTMLElement>('a[href], button:not(:disabled)')]
  const first = controls[0]
  const last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}

async function navigateMenu(event: MouseEvent, to: string) {
  // 保留链接的中键、组合键和新标签行为；普通点击才参与场景衔接。
  if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  focusContentAfterNavigation = true
  const failure = await router.push(to)
  if (!failure || route.path === to) {
    closeNavigation(false)
    await nextTick()
  } else {
    focusContentAfterNavigation = false
  }
}

function updateScroll() {
  scrollFrame = 0
  sceneState.scroll = homeLayout.value ? Math.min(window.scrollY / Math.max(window.innerHeight * .8, 1), 1) : 0
  headerScrolled.value = window.scrollY > 8
  if (headerScrolled.value && (openingPhase.value === 'loading' || openingPhase.value === 'revealing')) endOpening()
}
function onScroll() { if (!scrollFrame) scrollFrame = requestAnimationFrame(updateScroll) }
function updateMotion() {
  reducedMotion.value = media?.matches ?? false
  if (reducedMotion.value && (openingPhase.value === 'loading' || openingPhase.value === 'revealing')) endOpening()
  if (reducedMotion.value && menuTimeline) animateNavigation(menuOpen.value)
}

watch(authLayout, () => {
  menuTimeline?.kill()
  menuTimeline = undefined
  sceneState.menu = 0
  if (context) context.add(() => gsap.set(main.value, { autoAlpha: 1, y: 0 }))
})

// 路由初始化前后的 fullPath 都可能为“/”，同时观察名称以接上首次首页。
watch(() => [route.fullPath, route.name], () => {
  const initialScene = openingPhase.value === 'pending' && !authLayout.value
  if (initialScene) {
    if (homeLayout.value) { openingPhase.value = 'loading'; void nextTick(startOpening) }
    else endOpening()
  } else if (!homeLayout.value && (openingPhase.value === 'loading' || openingPhase.value === 'revealing')) endOpening()
  closeNavigation(false)
  // 首次解析路由时先落实取景；只有后续真实换页才做场景过渡，快资源也不会先显示内页位置。
  if (initialScene || !context) sceneState.content = homeLayout.value ? 0 : 1
  else context.add(() => gsap.to(sceneState, { content: homeLayout.value ? 0 : 1, duration: reducedMotion.value ? 0 : .64, ease: 'power3.inOut', overwrite: 'auto' }))
  updateScroll()
}, { immediate: true })

function enterPage(element: Element, done: () => void) {
  cancelPage(element)
  // 首次首页由品牌与资源首帧共同触发开场；这里只完成 Vue 挂载，不并行播放第二套文字动画。
  if (homeLayout.value && openingPhase.value !== 'complete') { void nextTick(done); return }
  // 减少动态时直接完成 Vue 的页面交接，避免零时长时间轴在挂载前消费完成回调。
  if (reducedMotion.value) { void nextTick(done); return }
  const ctx = gsap.context(() => {
    const timeline = gsap.timeline({ onComplete: done })
    timeline.fromTo(element, { autoAlpha: 0, y: 18 }, { autoAlpha: 1, y: 0, duration: reducedMotion.value ? 0 : .46, ease: 'power3.out', clearProps: 'opacity,visibility,transform' }, 0)
    const lines = element.querySelectorAll('.corner-kicker, .corner-title-line, .corner-intro-description, .corner-intro-actions')
    if (lines.length) timeline.fromTo(lines, { autoAlpha: 0, y: 22 }, { autoAlpha: 1, y: 0, duration: reducedMotion.value ? 0 : .65, stagger: reducedMotion.value ? 0 : .07, ease: 'power3.out', clearProps: 'opacity,visibility,transform' }, 0)
  }, element)
  pageAnimations.set(element, ctx)
}

function leavePage(element: Element, done: () => void) {
  cancelPage(element)
  if (reducedMotion.value) { void nextTick(done); return }
  const width = element.getBoundingClientRect().width
  const ctx = gsap.context(() => {
    // 先退出旧页面再显示新页面，三维背景持续变化，避免两页文字重叠。
    gsap.set(element, { position: 'absolute', width, top: 0, left: 0, pointerEvents: 'none' })
    gsap.to(element, { autoAlpha: 0, y: -10, duration: reducedMotion.value ? 0 : .18, ease: 'power2.in', onComplete: done })
  }, element)
  pageAnimations.set(element, ctx)
}
function cancelPage(element: Element) {
  pageAnimations.get(element)?.revert()
  pageAnimations.delete(element)
}

onMounted(() => {
  context = gsap.context(() => {}, shell.value!)
  sceneState.content = homeLayout.value ? 0 : 1
  media = window.matchMedia('(prefers-reduced-motion: reduce)')
  updateMotion()
  media.addEventListener('change', updateMotion)
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', updateBotanicalMode, { passive: true })
  updateBotanicalMode()
  updateScroll()
  void startOpening()
})
onBeforeUnmount(() => {
  openingContext?.revert()
  unlockScroll()
  cancelAnimationFrame(scrollFrame)
  cancelAnimationFrame(focusFrame)
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', updateBotanicalMode)
  media?.removeEventListener('change', updateMotion)
  context?.revert()
  pageAnimations.forEach((ctx) => ctx.revert())
  pageAnimations.clear()
})

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await auth.logout()
    await router.replace('/login')
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div ref="shell" class="app-shell" :class="{ 'auth-mode': authLayout, 'corner-app': !authLayout, 'corner-home': homeLayout, 'corner-night': menuOpen, 'corner-opening-wait': openingPhase === 'loading', 'corner-opening-active': openingPhase === 'loading' || openingPhase === 'revealing', 'corner-opening-skipped': skipOpening }" :style="{ '--corner-night': sceneState.menu, '--corner-opening-paper': openingPaper }" :data-scene-content="sceneState.content" :data-opening-tone="sceneState.openingTone" :data-opening-phase="openingPhase">
    <div v-if="!authLayout" class="corner-atmosphere" aria-hidden="true"></div>
    <component :is="homeLayout ? BotanicalScene : CornerScene" :key="homeLayout ? (botanicalMobile ? 'botanical-mobile' : 'botanical-desktop') : 'corner-scene'" v-if="!authLayout && (!homeLayout || sceneRequested || openingPhase === 'complete')" :menu="sceneState.menu" :content="sceneState.content" :scroll="sceneState.scroll" :hover="sceneState.hover" :reveal="sceneState.reveal" :opening-tone="sceneState.openingTone" :opening-color="openingPaper" :reduced-motion="reducedMotion" :skip-opening="skipOpening" @failure="sceneFailed = true" @ready="finishOpening" />
    <div v-if="!authLayout && (openingPhase === 'loading' || openingPhase === 'revealing')" class="corner-loading" :aria-hidden="openingPhase === 'revealing'">
      <div class="corner-loading-copy">
        <div class="corner-loading-identity" aria-label="max">
          <div class="corner-loading-symbol"><BrandMark @ready="onBrandReady" /></div>
          <span class="corner-loading-brand">max</span>
        </div>
        <p class="corner-loading-quote" :aria-label="openingQuote"><span v-for="(character, index) in openingCharacters" :key="index" class="corner-loading-character" aria-hidden="true">{{ character }}</span></p>
        <span class="corner-loading-progress" role="progressbar" aria-label="场景准备进度" :aria-valuenow="loadingPercent" aria-valuemin="0" aria-valuemax="100" :aria-valuetext="sceneFailed ? '部分素材未就绪，将使用简化画面' : '正在准备场景'">{{ loadingPercent }}</span>
        <span v-if="sceneFailed" class="corner-loading-note" role="status">部分素材未就绪，将使用简化画面</span>
      </div>
    </div>
    <!-- 开屏结束后保留同位置的品牌与文案；只承接阅读内容，进度仅属于加载阶段。 -->
    <div v-if="homeLayout && (openingPhase === 'revealing' || openingPhase === 'complete')" class="corner-loading-copy corner-home-copy" :style="{ opacity: sceneState.reveal * (1 - sceneState.scroll) * (1 - sceneState.menu) }" :aria-hidden="menuOpen || sceneState.scroll === 1">
      <div class="corner-loading-identity" aria-label="max">
        <div class="corner-loading-symbol"><BrandMark /></div>
        <span class="corner-loading-brand">max</span>
      </div>
      <p class="corner-loading-quote">{{ openingQuote }}</p>
    </div>
    <p v-if="homeLayout && sceneReady && sceneFailed && openingPhase === 'complete' && !menuOpen" class="corner-scene-note" role="status">画面已简化，记录照常可用。</p>
    <svg v-if="!authLayout" class="corner-surface" aria-hidden="true" focusable="false" width="100%" height="100%">
      <defs>
        <filter id="corner-paper-grain">
          <feTurbulence type="fractalNoise" baseFrequency=".68" numOctaves="3" seed="8" stitchTiles="stitch" />
          <feColorMatrix type="saturate" values="0" />
        </filter>
      </defs>
      <rect width="100%" height="100%" filter="url(#corner-paper-grain)" />
    </svg>
    <header v-if="!authLayout" class="corner-header" :class="{ 'is-scrolled': headerScrolled }" :inert="menuOpen">
      <RouterLink class="corner-brand" to="/" aria-label="猫的角落首页">
        <BrandMark />
        <span>猫的角落<span class="corner-brand-note">我们的小日子</span></span>
      </RouterLink>
      <RouterLink v-if="!homeLayout" class="corner-return" to="/">← 回到角落</RouterLink>
      <div class="corner-header-actions">
        <RouterLink class="corner-write-link" to="/records/new">写下今天 <span aria-hidden="true">↗</span></RouterLink>
        <div class="corner-playback-actions">
          <BackgroundMusic :visible="openingPhase === 'complete'" />
          <button ref="menuButton" class="corner-menu-toggle" type="button" aria-haspopup="dialog" aria-controls="corner-navigation" :aria-expanded="menuOpen" @click="openNavigation">{{ homeLayout ? 'Menu' : '菜单' }} <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 8h18M3 15h18" /></svg></button>
        </div>
      </div>
    </header>

    <main ref="main" class="site-main" :inert="menuOpen || openingPhase === 'loading'">
      <RouterView v-slot="{ Component }">
        <Transition :css="false" mode="out-in" appear @enter="enterPage" @leave="leavePage" @after-enter="focusContent" @after-leave="cancelPage" @enter-cancelled="cancelPage" @leave-cancelled="cancelPage">
          <component :is="Component" :key="route.path" />
        </Transition>
      </RouterView>
    </main>

    <footer v-if="!authLayout" class="site-footer" :inert="menuOpen || openingPhase === 'loading'">
      <span>两个人，一只猫，慢慢过日子。</span>
      <a href="/素材来源.html" target="_blank" rel="noopener">素材来源</a>
    </footer>

    <section v-if="!authLayout" id="corner-navigation" ref="menu" class="corner-navigation" role="dialog" aria-modal="true" aria-labelledby="corner-navigation-title" :aria-hidden="!menuOpen" :inert="!menuOpen" @keydown="trapMenuFocus">
      <div class="corner-navigation-top">
        <div class="corner-navigation-brand"><BrandMark /><span id="corner-navigation-title">猫的角落<span>我们的小日子</span></span></div>
        <button ref="closeButton" class="corner-menu-toggle" type="button" @click="closeNavigation()">关闭 <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 5 14 14M5 19 19 5" /></svg></button>
      </div>
      <div class="corner-navigation-body">
        <p class="corner-menu-kicker" data-menu-reveal>日子与想法，都在这里。</p>
        <nav class="corner-navigation-primary" aria-label="主要导航">
          <RouterLink v-for="(entry, index) in primaryEntries" :key="entry.to" :to="entry.to" custom v-slot="{ href, isActive }">
            <a :href="href" :aria-current="isActive ? 'page' : undefined" data-menu-reveal @click="navigateMenu($event, entry.to)" @mouseenter="sceneState.hover = index + 1" @focus="sceneState.hover = index + 1" @mouseleave="sceneState.hover = 0"><span class="corner-menu-number">{{ entry.number }}</span><span class="corner-menu-title">{{ entry.label }}</span><span class="corner-menu-note">{{ entry.note }}</span><span class="corner-menu-arrow" aria-hidden="true">↗</span></a>
          </RouterLink>
        </nav>
        <nav class="corner-navigation-secondary" aria-label="生活导航" data-menu-reveal>
          <RouterLink to="/#home-memories" custom v-slot="{ href }"><a :href="href" @click="navigateMenu($event, '/#home-memories')">生活面板 ↗</a></RouterLink>
          <RouterLink to="/emotions" custom v-slot="{ href }"><a :href="href" @click="navigateMenu($event, '/emotions')">今天的内心 ↗</a></RouterLink>
          <RouterLink to="/reminders" custom v-slot="{ href }"><a :href="href" @click="navigateMenu($event, '/reminders')">提醒 ↗</a></RouterLink>
        </nav>
      </div>
      <div class="corner-navigation-bottom" data-menu-reveal>
        <RouterLink to="/" custom v-slot="{ href }"><a :href="href" @click="navigateMenu($event, '/')">← 回到角落</a></RouterLink>
        <a href="/downloads/desktop-cat-windows-x64-setup.exe" download>下载桌面猫 ↓</a>
        <div class="corner-menu-account"><span v-if="auth.user">{{ auth.user.username }}</span><button type="button" :disabled="loggingOut" @click="logout">{{ loggingOut ? '退出中' : '退出登录' }}</button></div>
      </div>
    </section>
  </div>
</template>
