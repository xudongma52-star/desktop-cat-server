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

const WEEKDAY_LABELS = ['', '一', '', '三', '', '五', '']
const today = startOfLocalDay(new Date())
// 按自然月取最近半年，月底日期夹到目标月末，避免 setMonth 将日期溢出到下一月。
const halfYearAgo = new Date(today.getFullYear(), today.getMonth() - 6, 1)
halfYearAgo.setDate(Math.min(today.getDate(), new Date(halfYearAgo.getFullYear(), halfYearAgo.getMonth() + 1, 0).getDate()))
const rangeStart = addDays(halfYearAgo, 1)
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

// 半年周格分为上下两段，日期从上段左侧读到下段右侧，适配正方形布局。
const weekGroups = computed(() => {
  const middle = Math.ceil(weeks.value.length / 2)
  return [weeks.value.slice(0, middle), weeks.value.slice(middle)].map((group) => group.map((week, index) => ({
    ...week,
    monthLabel: index === 0 && !week.monthLabel
      ? `${Number(week.cells.find((cell) => cell.inRange)!.date.slice(5, 7))}月`
      : week.monthLabel,
  })))
})

const selectedMessage = computed(() => {
  const selected = selectedDate.value
  if (!selected) return ''
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
      <div class="activity-summary">
        <h2 id="writing-activity-title">写作足迹</h2>
        <span v-if="activity" class="activity-statistics" title="最近半年的日记篇数与写作天数" aria-live="polite">
          {{ activity.totalRecords }} 篇 <span aria-hidden="true">·</span> {{ activity.activeDays }} 天
        </span>
      </div>
      <RouterLink class="activity-write" to="/records/new">写日记 <span aria-hidden="true">↗</span></RouterLink>
    </div>

    <p v-if="loading" class="activity-loading" role="status">加载中…</p>

    <p v-if="error" class="activity-error" role="alert">
      {{ error }}
      <button type="button" @click="loadActivity">重新加载</button>
    </p>

    <div class="activity-scroll" tabindex="0" aria-label="最近半年的写作热力图，从上段到下段依次排列，可横向滚动">
      <div v-for="(group, groupIndex) in weekGroups" :key="groupIndex" class="activity-chart" role="grid" :aria-label="`半年写作日期和篇数，第 ${groupIndex + 1} 段`" :style="{ '--activity-weeks': group.length }">
        <div class="month-row" aria-hidden="true">
          <span></span>
          <span v-for="week in group" :key="week.key">{{ week.monthLabel }}</span>
        </div>
        <div class="activity-grid">
          <div class="weekday-labels" aria-hidden="true">
            <span v-for="(label, index) in WEEKDAY_LABELS" :key="index">{{ label }}</span>
          </div>
          <div class="activity-weeks">
            <div v-for="week in group" :key="week.key" class="activity-week" role="row">
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
      <p aria-live="polite">{{ selectedMessage }}</p>
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
  width: min(320px, 100%);
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  margin-bottom: 72px;
  padding: 8px 0;
  background: transparent;
}

.activity-summary,
.activity-heading,
.activity-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.activity-summary {
  align-items: flex-start;
  flex-direction: column;
  gap: 5px;
}

.activity-summary h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
}

.activity-write {
  padding: 8px 0 8px 12px;
  color: var(--ink);
  font-size: 12px;
  white-space: nowrap;
}

.activity-write:hover {
  color: #2f6346;
}

.activity-heading {
  margin-bottom: 10px;
}

.activity-statistics,
.activity-loading {
  color: #515b50;
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
  padding: 3px 1px;
  scrollbar-color: #8d9b86 transparent;
  scrollbar-width: thin;
}

.activity-scroll:focus-visible {
  border-radius: 8px;
  outline: 3px solid #bd9d61;
  outline-offset: 3px;
}

.activity-chart {
  width: max-content;
  margin-inline: auto;
}

.activity-chart + .activity-chart {
  margin-top: 10px;
}

.month-row {
  display: grid;
  grid-template-columns: 24px repeat(var(--activity-weeks), 10px);
  gap: 3px;
  margin-bottom: 5px;
  color: #515b50;
  font-size: 9px;
  line-height: 11px;
}

.month-row span:not(:first-child) {
  white-space: nowrap;
}

.activity-grid,
.activity-weeks {
  display: flex;
  gap: 3px;
}

.weekday-labels,
.activity-week {
  display: grid;
  grid-template-rows: repeat(7, 10px);
  gap: 3px;
}

.weekday-labels {
  width: 24px;
  flex: 0 0 24px;
  color: #515b50;
  font-size: 9px;
  line-height: 11px;
}

.activity-cell,
.activity-legend i {
  width: 10px;
  height: 10px;
  border: 1px solid rgba(56, 85, 70, .14);
  border-radius: 3px;
  background: rgba(235, 237, 231, .28);
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
  box-shadow: 0 0 0 1px #65796b;
  outline: none;
}

.activity-cell.outside {
  visibility: hidden;
  cursor: default;
}

.activity-cell.today {
  outline: 1px solid #b38f50;
  outline-offset: 2px;
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
  min-height: 16px;
  margin-top: 7px;
  color: #515b50;
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
    margin-inline: auto;
    margin-bottom: 48px;
  }

  .activity-heading {
    align-items: flex-start;
  }

  .activity-summary {
    align-items: flex-start;
    flex-direction: column;
    gap: 7px;
  }

  .activity-footer {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
