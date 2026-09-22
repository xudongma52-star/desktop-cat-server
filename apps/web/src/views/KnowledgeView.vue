<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError, describeApiError } from '../api/http'
import { retrieveKnowledge } from '../api/knowledge'
import type { KnowledgeSearchResult } from '../api/knowledge'
import { formatRecordDate, recordTypeLabels } from '../api/records'

const question = ref('')
const result = ref<KnowledgeSearchResult | null>(null)
const loading = ref(false)
const error = ref('')

const examples = ['我以前是怎么缓解压力的？', '最近学会了哪些新东西？', '有哪些让我觉得温暖的时刻？']

async function search() {
  const normalizedQuestion = question.value.trim()
  if (!normalizedQuestion || loading.value) return

  loading.value = true
  error.value = ''
  try {
    result.value = await retrieveKnowledge(normalizedQuestion)
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 503) {
      error.value = '知识检索服务暂时没有启动，请先启动 Python 服务后再试。'
    } else {
      error.value = describeApiError(caught, '知识检索暂时没有完成，请稍后再试。')
    }
  } finally {
    loading.value = false
  }
}

function useExample(example: string) {
  question.value = example
  void search()
}

function scoreLabel(score: number): string {
  return `${Math.round(score * 100)}% 相关`
}
</script>

<template>
  <div class="knowledge-page content-page">
    <section class="page-heading knowledge-heading">
      <div>
        <p class="eyebrow">MY KNOWLEDGE</p>
        <h1>从写过的话里，找回当时的自己。</h1>
        <p>第一版会寻找与你的问题最相关的原文片段，并保留每一处来源。</p>
      </div>
      <span class="knowledge-version">检索版 · 暂无大模型</span>
    </section>

    <section class="knowledge-search-panel" aria-labelledby="knowledge-search-title">
      <div class="knowledge-search-copy">
        <h2 id="knowledge-search-title">想从记录里找些什么？</h2>
        <p>只有开启“允许 AI 检索”的日记、心得和实习笔记会参与搜索。</p>
      </div>

      <form class="knowledge-form" @submit.prevent="search">
        <label for="knowledge-question" class="visually-hidden">输入要检索的问题</label>
        <textarea
          id="knowledge-question"
          v-model="question"
          maxlength="500"
          rows="3"
          placeholder="例如：我以前遇到困难时，是怎么让自己慢慢好起来的？"
        />
        <div class="knowledge-form-footer">
          <span>{{ question.length }} / 500</span>
          <button type="submit" :disabled="loading || !question.trim()">
            {{ loading ? '正在翻找记录…' : '查找相关片段' }}
          </button>
        </div>
      </form>

      <div class="knowledge-examples" aria-label="问题示例">
        <span>可以试试：</span>
        <button v-for="example in examples" :key="example" type="button" :disabled="loading" @click="useExample(example)">
          {{ example }}
        </button>
      </div>
    </section>

    <p v-if="error" class="inline-alert knowledge-alert" role="alert">{{ error }}</p>

    <section v-if="result" class="knowledge-results" aria-live="polite">
      <header class="knowledge-results-heading">
        <div>
          <p class="eyebrow">SEARCH RESULT</p>
          <h2>与你的问题相关的记录</h2>
        </div>
        <span>本次检索了 {{ result.searchableRecordCount }} 篇记录</span>
      </header>

      <p v-if="result.candidateLimitReached" class="knowledge-limit-note">
        第一版优先检索最近 100 篇记录，更早的内容会在后续索引版本中加入。
      </p>

      <div v-if="result.matches.length" class="knowledge-match-list">
        <article v-for="match in result.matches" :key="`${match.recordId}-${match.content}`" class="knowledge-match-card">
          <div class="knowledge-match-meta">
            <span class="type-chip">{{ recordTypeLabels[match.recordType] }}</span>
            <time :datetime="match.recordDate">{{ formatRecordDate(match.recordDate) }}</time>
            <span class="knowledge-score">{{ scoreLabel(match.score) }}</span>
          </div>
          <h3>{{ match.title || '没有标题的一天' }}</h3>
          <blockquote>{{ match.content }}</blockquote>
          <RouterLink class="text-link strong" :to="`/records/${match.recordId}`">查看这篇原文 →</RouterLink>
        </article>
      </div>

      <div v-else class="state-panel knowledge-empty">
        <span class="empty-icon" aria-hidden="true">⌕</span>
        <div v-if="result.searchableRecordCount === 0">
          <h3>知识库里还没有记录</h3>
          <p>编辑一篇记录并开启“允许 AI 检索”，就可以从这里找到它。</p>
        </div>
        <div v-else>
          <h3>暂时没有找到相关内容</h3>
          <p>换一种更具体的说法，或者在问题里加入记录中出现过的词。</p>
        </div>
        <RouterLink v-if="result.searchableRecordCount === 0" class="button secondary" to="/records">去整理记录</RouterLink>
      </div>
    </section>
  </div>
</template>
