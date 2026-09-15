export type CatActivityId = 'idle' | 'sleeping' | 'grooming' | 'playing' | 'eating' | 'walking' | 'running'

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
  automaticMessages: readonly string[]
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
    automaticMessages: ['我先在这里陪你发会儿呆。', '窗外好像有朵猫猫形状的云。', '安静待在你旁边，也很好呀。'],
    acceptedMessages: ['好呀，我们一起安静一会儿。', '那我坐近一点陪你。', '发呆时间，我把尾巴借你靠靠。', '什么都不做，也算认真休息喵。'],
    refusalMessages: ['我还有一点小精神，再转一圈嘛。', '等我巡视完这里，就回来陪你。'],
  },
  sleeping: {
    id: 'sleeping', label: '睡觉', icon: '☾', minMinutes: 5, maxMinutes: 10,
    movement: 'still', speedPixelsPerSecond: 0, refusalChance: 0.22,
    automaticMessages: ['眼睛变重啦，我团起来睡一会儿。', '借你一点呼噜声，做个软软的梦。', '这里暖暖的，刚好可以睡成一小团。'],
    acceptedMessages: ['好呀，我把尾巴盖在身上。', '晚安一小会儿，醒来还陪你。', '我先替你试试这个梦甜不甜。', '呼噜呼噜，你也别太晚睡呀。'],
    refusalMessages: ['我现在精神着呢，还想陪陪你。', '再玩一下嘛，困意还没追上我。', '我的耳朵还醒着，先不睡喵。'],
  },
  grooming: {
    id: 'grooming', label: '舔毛', icon: '✦', minMinutes: 2, maxMinutes: 5,
    movement: 'still', speedPixelsPerSecond: 0, refusalChance: 0.18,
    automaticMessages: ['等一下，我把毛毛整理得软乎乎。', '耳朵后面也要认真洗干净。', '今天也要做一只香香的小黑猫。'],
    acceptedMessages: ['好呀，看我把爪爪洗干净。', '整理好毛毛，再来贴贴你。', '这边舔两下，那边也不能落下。', '漂亮小猫正在认真营业喵。'],
    refusalMessages: ['我今天已经很干净啦，先陪你玩。', '这根毛先让它自由一会儿吧。', '等等，我想先趴在你旁边。'],
  },
  playing: {
    id: 'playing', label: '玩耍', icon: '●', minMinutes: 2, maxMinutes: 6,
    movement: 'wander', speedPixelsPerSecond: 48, refusalChance: 0.12,
    automaticMessages: ['发现一颗想象中的毛球，开玩！', '尾巴已经兴奋地翘起来啦。', '前爪准备好，我要轻轻扑过去了。'],
    acceptedMessages: ['好耶，陪我玩一小会儿吧。', '我要扑过去啦，接住我喵！', '游戏开始，输的人要交一颗冻干。', '看好啦，这是小黑猫的认真一扑。'],
    refusalMessages: ['我先观察一下，等毛球放松警惕。', '今天想玩安静一点的游戏喵。'],
  },
  eating: {
    id: 'eating', label: '吃冻干', icon: '◇', minMinutes: 1, maxMinutes: 3,
    movement: 'still', speedPixelsPerSecond: 0, refusalChance: 0.08,
    automaticMessages: ['肚子咕噜一声，该吃颗冻干啦。', '空气里有冻干的香味，我闻到啦。', '补充一点小猫能量，咔嚓咔嚓。'],
    acceptedMessages: ['闻到了！我要吃冻干。', '嗷呜一口，谢谢你的投喂。', '这一颗好香，我会慢慢嚼的。', '冻干收到，开心也分你一半。'],
    refusalMessages: ['我刚吃饱，先把冻干好好留着。', '先放进我的小碗，待会儿再吃喵。'],
  },
  walking: {
    id: 'walking', label: '散步', icon: '♧', minMinutes: 3, maxMinutes: 8,
    movement: 'wander', speedPixelsPerSecond: 34, refusalChance: 0.16,
    automaticMessages: ['我去桌面上慢慢巡视一圈。', '走几步看看，也许会遇见好心情。', '跟着自己的小脚印，去附近转转。'],
    acceptedMessages: ['走吧，我顺便替你看看四周。', '慢慢走一圈，再回到你身边。', '散步开始，你要不要和我一起数脚步？', '我去兜一小圈，很快就回来喵。'],
    refusalMessages: ['我现在想再趴一会儿，等下再走。', '外面有点忙，我先陪着你吧。', '爪爪今天想偷个小懒。'],
  },
  running: {
    id: 'running', label: '奔跑', icon: '➜', minMinutes: 1, maxMinutes: 4,
    movement: 'run', speedPixelsPerSecond: 92, refusalChance: 0.24,
    automaticMessages: ['突然充满精神，我要跑一小圈！', '尾巴举好，准备出发喽。', '把不开心甩在身后，冲呀！'],
    acceptedMessages: ['好呀，我要开始加速啦。', '冲呀！跑完就回来找你。', '看我的小爪子跑得多认真。', '风从耳边过去，好像在给我加油。'],
    refusalMessages: ['今天想慢一点，我陪你走走吧。', '刚刚才跑过，让小爪子歇一会儿。', '不急着冲刺，慢慢来也很可爱。'],
  },
}

export const CAT_ACTIVITY_IDS = Object.keys(CAT_ACTIVITY_DEFINITIONS) as CatActivityId[]

export function isCatActivityId(value: unknown): value is CatActivityId {
  return typeof value === 'string' && value in CAT_ACTIVITY_DEFINITIONS
}
