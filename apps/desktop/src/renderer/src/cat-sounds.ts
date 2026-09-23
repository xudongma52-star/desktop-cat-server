import purrTwitOneUrl from './assets/audio/cat-purr-twit-1.mp3'
import purrTwitTwoUrl from './assets/audio/cat-purr-twit-2.mp3'
import purrTwitThreeUrl from './assets/audio/cat-purr-twit-3.mp3'
import purrTwitFourUrl from './assets/audio/cat-purr-twit-4.mp3'
import purrTwitFiveUrl from './assets/audio/cat-purr-twit-5.mp3'
import purrTwitSixUrl from './assets/audio/cat-purr-twit-6.mp3'

export type CatSoundCue = 'greeting' | 'touch' | 'reminder' | 'happy' | 'protest'

type CatSoundVariant = {
  url: string
  volume: number
}

const SOUND_VARIANTS: Record<CatSoundCue, readonly CatSoundVariant[]> = {
  greeting: [
    { url: purrTwitFourUrl, volume: 0.3 },
    { url: purrTwitSixUrl, volume: 0.3 },
    { url: purrTwitOneUrl, volume: 0.28 },
  ],
  touch: [
    { url: purrTwitFourUrl, volume: 0.24 },
    { url: purrTwitFiveUrl, volume: 0.24 },
    { url: purrTwitSixUrl, volume: 0.24 },
  ],
  reminder: [
    { url: purrTwitOneUrl, volume: 0.34 },
    { url: purrTwitTwoUrl, volume: 0.34 },
  ],
  happy: [
    { url: purrTwitTwoUrl, volume: 0.28 },
    { url: purrTwitThreeUrl, volume: 0.28 },
    { url: purrTwitSixUrl, volume: 0.28 },
  ],
  protest: [
    { url: purrTwitOneUrl, volume: 0.26 },
    { url: purrTwitFiveUrl, volume: 0.26 },
  ],
}

let activeAudio: HTMLAudioElement | undefined
const fadingAudios = new Set<HTMLAudioElement>()
const fadeTimers = new Set<number>()
const lastVariantIndex = new Map<CatSoundCue, number>()

function pickVariant(cue: CatSoundCue): CatSoundVariant {
  const variants = SOUND_VARIANTS[cue]
  const previousIndex = lastVariantIndex.get(cue)
  let nextIndex = Math.floor(Math.random() * variants.length)
  if (variants.length > 1 && nextIndex === previousIndex) {
    nextIndex = (nextIndex + 1 + Math.floor(Math.random() * (variants.length - 1))) % variants.length
  }
  lastVariantIndex.set(cue, nextIndex)
  return variants[nextIndex]
}

function fadeOut(audio: HTMLAudioElement): void {
  fadingAudios.add(audio)
  const startingVolume = audio.volume
  let step = 0
  const timer = window.setInterval(() => {
    step += 1
    audio.volume = Math.max(0, startingVolume * (1 - step / 4))
    if (step < 4) return
    window.clearInterval(timer)
    fadeTimers.delete(timer)
    fadingAudios.delete(audio)
    audio.pause()
    audio.src = ''
  }, 12)
  fadeTimers.add(timer)
}

export function playCatSound(cue: CatSoundCue): void {
  if (activeAudio) fadeOut(activeAudio)

  const variant = pickVariant(cue)
  const audio = new Audio(variant.url)
  audio.volume = variant.volume
  activeAudio = audio
  audio.addEventListener('ended', () => {
    if (activeAudio === audio) activeAudio = undefined
    audio.src = ''
  }, { once: true })
  void audio.play().catch((error: unknown) => {
    if (activeAudio === audio) activeAudio = undefined
    console.warn('Failed to play the cat sound.', error)
  })
}

export function disposeCatSounds(): void {
  if (activeAudio) {
    activeAudio.pause()
    activeAudio.src = ''
    activeAudio = undefined
  }
  fadeTimers.forEach((timer) => window.clearInterval(timer))
  fadeTimers.clear()
  fadingAudios.forEach((audio) => {
    audio.pause()
    audio.src = ''
  })
  fadingAudios.clear()
  lastVariantIndex.clear()
}
