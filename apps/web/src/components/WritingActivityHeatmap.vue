<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { describeApiError } from '../api/http'
import { getRecordActivity } from '../api/records'
import type { RecordActivity } from '../api/records'

interface ActivityCell {
  date: string
  count: number
  level: number
  inRange: boolean
  isToday: boolean
  label: string
}

interface ActivityWeek {
  key: string
  monthLabel: string
  cells: ActivityCell[]
}

const DAYS_IN_ACTIVITY_RANGE = 365
const WEEKDAY_LABELS = ['', '一', '', '三', '', '五', '']
const today = startOfLocalDay(new Date())
const rangeStart = addDays(today, -(DAYS_IN_ACTIVITY_RANGE - 1))
const rangeEnd = today
const startDate = toDateString(rangeStart)
const endDate = toDateString(rangeEnd)

const activity = ref<RecordActivity | null>(null)
const loading = ref(true)
const error = ref('')
const selectedDate = ref<ActivityCell | null>(null)
let disposed = false

const countsByDate = computed(() => new Map(
  activity.value?.days.map((day) => [day.recordDate, day.recordCount]) ?? [],
))

const weeks = computed<ActivityWeek[]>(() => {
  const gridStart = addDays(rangeStart, -rangeStart.getDay())
  const gridEnd = addDays(rangeEnd, 6 - rangeEnd.getDay())
  const result: ActivityWeek[] = []

  for (let weekStart = gridStart; weekStart <= gridEnd; weekStart = addDays(weekStart, 7)) {
    const cells = Array.from({ length: 7 }, (_, dayIndex) => {
      const date = addDays(weekStart, dayIndex)
      const dateString = toDateString(date)
      const inRange = date >= rangeStart && date <= rangeEnd
      const count = inRange ? countsByDate.value.get(dateString) ?? 0 : 0
      return {
        date: dateString,
        count,
        level: activityLevel(count),
        inRange,
        isToday: dateString === endDate,
        label: inRange ? `${formatChineseDate(date)}，${count ? `写了 ${count} 篇日记` : '没有写日记'}` : '',
      }
    })
    const firstDayOfMonth = cells.find((cell) => cell.inRange && Number(cell.date.slice(8, 10)) === 1)
    const monthLabel = result.length === 0
      ? `${rangeStart.getMonth() + 1}月`
      : firstDayOfMonth ? `${Number(firstDayOfMonth.date.slice(5, 7))}月` : ''
    result.push({ key: toDateString(weekStart), monthLabel, cells })
  }
  return result
})

const selectedMessage = computed(() => {
  const selected = selectedDate.value
  if (!selected) return '轻点任意一天，看看那天留下了多少篇日记。'
  const date = parseLocalDate(selected.date)
  return selected.count
    ? `${formatChineseDate(date)}写了 ${selected.count} 篇日记。`
    : `${formatChineseDate(date)}还没有日记。`
})

function startOfLocalDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function addDays(date: Date, amount: number): Date {
  const next = new Date(date)
  next.setDate(next.getDate() + amount)
  return next
}

function toDateString(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseLocalDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function formatChineseDate(date: Date): string {
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

function activityLevel(count: number): number {
  if (count <= 0) return 0
  if (count >= 4) return 4
  return count
}

function selectCell(cell: ActivityCell) {
  if (cell.inRange) selectedDate.value = cell
}

async function loadActivity() {
  loading.value = true
  error.value = ''
  try {
    const result = await getRecordActivity(startDate, endDate)
    if (!disposed) activity.value = result
  } catch (caught) {
    if (!disposed) error.value = describeApiError(caught, '写作足迹暂时没有加载成功，请稍后再试。')
  } finally {
    if (!disposed) loading.value = false
  }
}

onMounted(() => void loadActivity())
onBeforeUnmount(() => {
  disposed = true
})
</script>

<template>
  <section class="writing-activity" aria-labelledby="writing-activity-title">
    <div class="activity-heading">
      <div>
        <p class="eyebrow">WRITING ACTIVITY</p>
        <h2 id="writing-activity-title">写作足迹</h2>
        <p class="activity-description">每一个亮起的格子，都是你认真记录过的一天。</p>
      </div>
      <RouterLink class="button secondary" to="/records/new">写一篇日记</RouterLink>
    </div>

    <div class="activity-statistics" aria-live="polite">
      <template v-if="activity">
        <div><strong>{{ activity.totalRecords }}</strong><span>过去一年写下的日记</span></div>
        <div><strong>{{ activity.activeDays }}</strong><span>留下记录的日子</span></div>
      </template>
      <p v-else-if="loading">正在整理过去一年的写作痕迹…</p>
      <p v-else>热力图仍然为你保留着，写下第一篇就会亮起来。</p>
    </div>

    <p v-if="error" class="activity-error" role="alert">
      {{ error }}
      <button type="button" @click="loadActivity">重新加载</button>
    </p>

    <div class="activity-scroll" tabindex="0" aria-label="过去一年的日记写作热力图，可横向滚动">
      <div class="activity-chart" role="grid" aria-label="日记写作日期和篇数">
        <div class="month-row" aria-hidden="true">
          <span></span>
          <span v-for="week in weeks" :key="week.key">{{ week.monthLabel }}</span>
        </div>
        <div class="activity-grid">
          <div class="weekday-labels" aria-hidden="true">
            <span v-for="(label, index) in WEEKDAY_LABELS" :key="index">{{ label }}</span>
          </div>
          <div class="activity-weeks">
            <div v-for="week in weeks" :key="week.key" class="activity-week" role="row">
              <span
                v-for="cell in week.cells"
                :key="cell.date"
                class="activity-cell"
                :class="[`level-${cell.level}`, { outside: !cell.inRange, today: cell.isToday }]"
                :title="cell.label"
                :aria-label="cell.label || undefined"
                :tabindex="cell.inRange ? 0 : -1"
                role="gridcell"
                @click="selectCell(cell)"
                @keydown.enter.prevent="selectCell(cell)"
                @keydown.space.prevent="selectCell(cell)"
              ></span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="activity-footer">
      <p>{{ selectedMessage }}</p>
      <div class="activity-legend" aria-label="颜色越深，当天日记越多">
        <span>少</span>
        <i v-for="level in [0, 1, 2, 3, 4]" :key="level" :class="`level-${level}`"></i>
        <span>多</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.writing-activity {
  margin-bottom: 24px;
  padding: 30px 32px 26px;
  overflow: hidden;
  border: 1px solid #dce2d6;
  border-radius: 24px;
  background: var(--paper);
  box-shadow: 0 8px 26px rgba(40, 62, 55, .03);
}

.activity-heading,
.activity-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.activity-heading .eyebrow {
  margin-bottom: 8px;
}

.activity-description {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.activity-statistics {
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 34px;
  margin: 25px 0 18px;
}

.activity-statistics div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.activity-statistics strong {
  color: var(--green-dark);
  font-family: Georgia, 'Microsoft YaHei', serif;
  font-size: 31px;
  font-weight: 500;
}

.activity-statistics span,
.activity-statistics p {
  color: #7d877d;
  font-size: 11px;
}

.activity-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin: 0 0 14px;
  padding: 10px 12px;
  border-radius: 9px;
  color: #9a5145;
  background: #f7eae6;
  font-size: 11px;
}

.activity-error button {
  flex-shrink: 0;
  padding: 5px 8px;
  color: #8d4d43;
  border: 1px solid #dfbcb5;
  background: transparent;
}

.activity-scroll {
  overflow-x: auto;
  padding: 3px 1px 11px;
  scrollbar-color: #bdc7ba #eef0ea;
  scrollbar-width: thin;
}

.activity-scroll:focus-visible {
  border-radius: 8px;
  outline: 3px solid #bd9d61;
  outline-offset: 3px;
}

.activity-chart {
  width: max-content;
  min-width: 100%;
}

.month-row {
  display: grid;
  grid-template-columns: 30px repeat(53, 14px);
  gap: 4px;
  margin-bottom: 7px;
  color: #869087;
  font-size: 9px;
  line-height: 14px;
}

.month-row span:not(:first-child) {
  white-space: nowrap;
}

.activity-grid,
.activity-weeks {
  display: flex;
  gap: 4px;
}

.weekday-labels,
.activity-week {
  display: grid;
  grid-template-rows: repeat(7, 14px);
  gap: 4px;
}

.weekday-labels {
  width: 30px;
  flex: 0 0 30px;
  color: #8b948b;
  font-size: 9px;
  line-height: 14px;
}

.activity-cell,
.activity-legend i {
  width: 14px;
  height: 14px;
  border: 1px solid rgba(56, 85, 70, .06);
  border-radius: 4px;
  background: #ebede7;
}

.activity-cell {
  display: block;
  cursor: pointer;
  transition: transform .14s ease, box-shadow .14s ease;
}

.activity-cell:hover,
.activity-cell:focus-visible {
  position: relative;
  z-index: 1;
  transform: scale(1.24);
  box-shadow: 0 0 0 2px var(--paper), 0 0 0 3px #65796b;
  outline: none;
}

.activity-cell.outside {
  visibility: hidden;
  cursor: default;
}

.activity-cell.today {
  box-shadow: 0 0 0 2px var(--paper), 0 0 0 3px #b38f50;
}

.activity-cell.level-1,
.activity-legend i.level-1 { background: #c6dfc4; }

.activity-cell.level-2,
.activity-legend i.level-2 { background: #91bd98; }

.activity-cell.level-3,
.activity-legend i.level-3 { background: #5d956e; }

.activity-cell.level-4,
.activity-legend i.level-4 { background: #2f6346; }

.activity-footer {
  margin-top: 13px;
  color: #7e887e;
  font-size: 10px;
}

.activity-footer p {
  margin: 0;
}

.activity-legend {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
}

.activity-legend i {
  display: block;
}

@media (max-width: 700px) {
  .writing-activity {
    padding: 24px 20px 22px;
  }

  .activity-heading {
    align-items: flex-start;
  }

  .activity-heading .button {
    padding-inline: 11px;
  }

  .activity-statistics {
    gap: 20px;
  }

  .activity-statistics div {
    align-items: flex-start;
    flex-direction: column;
    gap: 1px;
  }

  .activity-footer {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }
}
</style>
