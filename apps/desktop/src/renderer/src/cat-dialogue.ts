const EVERYDAY_MESSAGES = [
  '今天也辛苦啦，我一直在这里。',
  '不用一直很厉害，歇一会儿也很好。',
  '你忙你的，我替你守着这一小块桌面。',
  '我把今天的小烦恼踩成猫爪印啦。',
  '喵，我偷偷给你留了一点好运。',
  '慢一点没关系，我会陪你一起走。',
  '要是累了，就看看我圆圆的脸。',
  '你已经做了很多，剩下的慢慢来。',
  '刚刚想到你，尾巴就自己摇起来了。',
  '今天也有一点点值得喜欢的地方。',
  '喝口水吧，我帮你看着屏幕。',
  '你在认真生活，我有认真看见哦。',
  '我没有催你，只是想靠近一点。',
  '不开心也可以，我先安静陪着你。',
  '等你忙完，我们一起发会儿呆。',
  '这里有只小黑猫，永远站在你这边。',
  '今天的你也值得一颗小小的星星。',
  '喵呜，把肩膀放松一点点吧。',
  '我把温柔藏在爪垫里，分你一点。',
  '摸摸我的脑袋，坏心情会变小一点。',
] as const

const PERIOD_MESSAGES = {
  morning: [
    '早呀，新的一天先从伸个懒腰开始。',
    '晨光跑进来了，我也来陪你啦。',
    '先吃点东西，再和今天慢慢认识。',
    '今天的第一声喵，送给你。',
  ],
  noon: [
    '到中午啦，记得让眼睛休息一下。',
    '午饭有没有好好吃？我在闻香味呢。',
    '太阳暖暖的，适合偷偷打个小盹。',
    '忙了一上午，给自己一点夸奖吧。',
  ],
  afternoon: [
    '下午也陪你，困了就和我眨眨眼。',
    '剩下的事情，一件一件来就好。',
    '阳光挪到桌角啦，我也挪近一点。',
    '给你一小会儿猫咪充电时间。',
  ],
  evening: [
    '天色变软啦，你也可以慢下来。',
    '今天辛苦的部分，就留给夜风带走吧。',
    '忙完记得回来，我给你留着位置。',
    '晚饭时间到，我的冻干也准备好啦。',
  ],
  night: [
    '夜深啦，做不完的事可以留给明天。',
    '我把呼噜声调小一点，陪你收尾。',
    '今晚也要好好照顾自己呀。',
    '困了就去睡，我替你守一会儿夜。',
  ],
} as const

export const TOUCH_MESSAGES = [
  '嘿嘿，被你摸到啦。',
  '再摸一下也不是不可以喵。',
  '你的手心暖暖的。',
  '收到摸摸，今天也安心一点啦。',
  '喵呜，我把脑袋再凑近一点。',
  '呼噜呼噜，这是只给你的回应。',
  '好舒服，尾巴都藏不住开心啦。',
  '摸摸已经存进我的小口袋了。',
] as const

type DialoguePeriod = keyof typeof PERIOD_MESSAGES

function getDialoguePeriod(hour: number): DialoguePeriod {
  if (hour >= 5 && hour < 11) return 'morning'
  if (hour >= 11 && hour < 14) return 'noon'
  if (hour >= 14 && hour < 18) return 'afternoon'
  if (hour >= 18 && hour < 23) return 'evening'
  return 'night'
}

export function getAmbientMessages(at = new Date()): readonly string[] {
  return [...EVERYDAY_MESSAGES, ...PERIOD_MESSAGES[getDialoguePeriod(at.getHours())]]
}
