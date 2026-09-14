export type CatActivityId = 'idle' | 'sleeping' | 'grooming' | 'playing' | 'walking' | 'running'

export type CatMovementMode = 'still' | 'wander' | 'run'

export type CatFacing = 'left' | 'right'

export type CatActivityDefinition = {
  id: CatActivityId
  label: string
  icon: string
  minMinutes: number
  maxMinutes: number
  movement: CatMovementMode
  speedPixelsPerSecond: number
  refusalChance: number
  defaultMessage: string
  automaticMessage: string
  acceptedMessages: readonly string[]
  refusalMessages: readonly string[]
}

export type CatActivitySnapshot = {
  id: CatActivityId
  startedAt: number
  endsAt: number
  durationMinutes: number
  facing: CatFacing
}

export type CatActivityRequestResult = {
  accepted: boolean
  message: string
  snapshot: CatActivitySnapshot
}

export const CAT_ACTIVITY_DEFINITIONS: Record<CatActivityId, CatActivityDefinition> = {
  idle: {
    id: 'idle', label: '发呆', icon: '☁', minMinutes: 1, maxMinutes: 4,
    movement: 'still', speedPixelsPerSecond: 0, refusalChance: 0.08,
    defaultMessage: '陪你安静地待一会儿～', automaticMessage: '跑累啦，我发会儿呆。',
    acceptedMessages: ['好呀，我陪你安静一会儿。', '那我先看看窗外～'],
    refusalMessages: ['我现在还不想停下来喵。'],
  },
  sleeping: {
    id: 'sleeping', label: '睡觉', icon: '☾', minMinutes: 5, maxMinutes: 10,
    movement: 'still', speedPixelsPerSecond: 0, refusalChance: 0.22,
    defaultMessage: '呼噜呼噜……', automaticMessage: '眼睛睁不开啦，睡一会儿。',
    acceptedMessages: ['好困呀，我要团成一小团。', '晚安一小会儿，呼噜～'],
    refusalMessages: ['我现在精神着呢，还不想睡！', '再玩一下嘛，我不困。'],
  },
  grooming: {
    id: 'grooming', label: '舔毛', icon: '✦', minMinutes: 2, maxMinutes: 5,
    movement: 'still', speedPixelsPerSecond: 0, refusalChance: 0.18,
    defaultMessage: '要把毛毛舔得整整齐齐。', automaticMessage: '等一下，我整理整理毛毛。',
    acceptedMessages: ['好吧，要保持漂亮！', '看我把爪爪洗干净。'],
    refusalMessages: ['我今天已经很干净啦。', '等等，现在不想舔毛。'],
  },
  playing: {
    id: 'playing', label: '玩耍', icon: '●', minMinutes: 2, maxMinutes: 6,
    movement: 'wander', speedPixelsPerSecond: 48, refusalChance: 0.12,
    defaultMessage: '抓住那颗小毛球！', automaticMessage: '发现一颗毛球，开玩！',
    acceptedMessages: ['好耶，毛球在哪里？', '我要扑过去啦！'],
    refusalMessages: ['我先观察一下，不急着扑。'],
  },
  walking: {
    id: 'walking', label: '散步', icon: '♧', minMinutes: 3, maxMinutes: 8,
    movement: 'wander', speedPixelsPerSecond: 34, refusalChance: 0.16,
    defaultMessage: '慢慢巡视你的桌面。', automaticMessage: '该去桌面上巡视一圈啦。',
    acceptedMessages: ['走吧，我去巡视领地。', '慢慢走一圈也不错。'],
    refusalMessages: ['我现在不想散步，想再趴一会儿。', '外面有点忙，我先不去。'],
  },
  running: {
    id: 'running', label: '奔跑', icon: '➜', minMinutes: 1, maxMinutes: 4,
    movement: 'run', speedPixelsPerSecond: 92, refusalChance: 0.24,
    defaultMessage: '快看，我跑起来啦！', automaticMessage: '突然很有精神，冲呀！',
    acceptedMessages: ['冲呀！快跟上我～', '好！我要开始加速啦。'],
    refusalMessages: ['不要，我今天想慢一点。', '刚刚才跑过，让我歇一会儿嘛。'],
  },
}

export const CAT_ACTIVITY_IDS = Object.keys(CAT_ACTIVITY_DEFINITIONS) as CatActivityId[]

export function isCatActivityId(value: unknown): value is CatActivityId {
  return typeof value === 'string' && value in CAT_ACTIVITY_DEFINITIONS
}
