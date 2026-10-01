<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError, describeApiError } from '../api/http'
import { deleteKnowledgeChat, listKnowledgeChats, listKnowledgeMessages, renameKnowledgeChat, sendKnowledgeChat } from '../api/knowledge'
import type { KnowledgeChatResponse, KnowledgeChatSummary, KnowledgeMessage } from '../api/knowledge'
import { formatRecordDate, recordTypeLabels } from '../api/records'

const chats = ref<KnowledgeChatSummary[]>([])
const activeChatId = ref<number | null>(null)
const messages = ref<KnowledgeMessage[]>([])
const question = ref('')
const loadingChats = ref(true)
const loadingMessages = ref(false)
const loadingOlder = ref(false)
const sending = ref(false)
const hasMore = ref(false)
const nextBeforeMessageId = ref<number | null>(null)
const error = ref('')
const menuChatId = ref<number | null>(null)
const managedChat = ref<KnowledgeChatSummary | null>(null)
const managementAction = ref<'rename' | 'delete' | null>(null)
const titleDraft = ref('')
const titleInput = ref<HTMLInputElement | null>(null)
const managementDialog = ref<HTMLDialogElement | null>(null)
const managementError = ref('')
const managing = ref(false)
const chatBusy = computed(() => sending.value || loadingMessages.value || loadingOlder.value || managing.value)

const examples = ['我以前是怎么缓解压力的？', '最近学会了哪些新东西？', '有哪些让我觉得温暖的时刻？']

onMounted(async () => {
  try {
    chats.value = await listKnowledgeChats()
    if (chats.value.length) await selectChat(chats.value[0].chatId)
  } catch (caught) {
    error.value = describeApiError(caught, '知识对话暂时没有加载完成，请稍后再试。')
  } finally {
    loadingChats.value = false
  }
})

async function selectChat(chatId: number) {
  if (chatBusy.value) return
  menuChatId.value = null
  activeChatId.value = chatId
  messages.value = []
  hasMore.value = false
  nextBeforeMessageId.value = null
  loadingMessages.value = true
  error.value = ''
  try {
    const page = await listKnowledgeMessages(chatId)
    if (activeChatId.value !== chatId) return
    messages.value = page.items
    hasMore.value = page.hasMore
    nextBeforeMessageId.value = page.nextBeforeMessageId
  } catch (caught) {
    error.value = describeApiError(caught, '这段对话暂时没有加载完成，请稍后再试。')
  } finally {
    loadingMessages.value = false
  }
}

async function loadOlderMessages() {
  if (!activeChatId.value || !nextBeforeMessageId.value || chatBusy.value) return
  loadingOlder.value = true
  error.value = ''
  try {
    const page = await listKnowledgeMessages(activeChatId.value, nextBeforeMessageId.value)
    messages.value = [...page.items, ...messages.value]
    hasMore.value = page.hasMore
    nextBeforeMessageId.value = page.nextBeforeMessageId
  } catch (caught) {
    error.value = describeApiError(caught, '更早的消息暂时没有加载完成。')
  } finally {
    loadingOlder.value = false
  }
}

function startNewChat() {
  if (chatBusy.value) return
  menuChatId.value = null
  activeChatId.value = null
  messages.value = []
  question.value = ''
  error.value = ''
  hasMore.value = false
  nextBeforeMessageId.value = null
}

async function send() {
  const normalizedQuestion = question.value.trim()
  if (!normalizedQuestion || chatBusy.value || managementAction.value) return

  sending.value = true
  error.value = ''
  try {
    const response = await sendKnowledgeChat(activeChatId.value, normalizedQuestion)
    activeChatId.value = response.chat.chatId
    messages.value.push(response.userMessage, response.assistantMessage)
    question.value = ''
    upsertChat(response)
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 503) {
      error.value = '知识问答服务暂时不可用，请稍后再试。'
    } else if (caught instanceof ApiError && caught.status === 409) {
      error.value = '这段对话已在其他页面更新，请重新打开后再提问。'
    } else {
      error.value = describeApiError(caught, '这次问答暂时没有完成，请稍后再试。')
    }
  } finally {
    sending.value = false
  }
}

async function openManagement(chat: KnowledgeChatSummary, action: 'rename' | 'delete') {
  if (chatBusy.value) return
  menuChatId.value = null
  managedChat.value = chat
  managementAction.value = action
  titleDraft.value = chat.title
  managementError.value = ''
  await nextTick()
  managementDialog.value?.showModal()
  titleInput.value?.focus()
  titleInput.value?.select()
}

function closeManagement() {
  if (managing.value) return
  managementDialog.value?.close()
  managementAction.value = null
  managedChat.value = null
  managementError.value = ''
}

async function saveManagement() {
  const chat = managedChat.value
  const action = managementAction.value
  if (!chat || !action || managing.value) return
  const title = titleDraft.value.trim()
  if (action === 'rename' && !title) {
    managementError.value = '请输入对话标题。'
    return
  }
  if (action === 'rename' && Array.from(title).length > 80) {
    managementError.value = '对话标题最多 80 字。'
    return
  }
  managing.value = true
  managementError.value = ''
  try {
    if (action === 'rename') {
      const updated = await renameKnowledgeChat(chat.chatId, title)
      chats.value = chats.value.map((item) => item.chatId === chat.chatId ? updated : item)
    } else {
      await deleteKnowledgeChat(chat.chatId)
      chats.value = chats.value.filter((item) => item.chatId !== chat.chatId)
      if (activeChatId.value === chat.chatId) {
        activeChatId.value = null
        messages.value = []
        question.value = ''
        hasMore.value = false
        nextBeforeMessageId.value = null
      }
    }
    error.value = ''
    managementAction.value = null
    managedChat.value = null
  } catch (caught) {
    managementError.value = describeApiError(caught, action === 'rename' ? '标题暂时没有保存成功。' : '对话暂时没有删除成功。')
  } finally {
    managing.value = false
  }
}

function upsertChat(response: KnowledgeChatResponse) {
  chats.value = [
    response.chat,
    ...chats.value.filter((chat) => chat.chatId !== response.chat.chatId),
  ]
}

function useExample(example: string) {
  question.value = example
  void send()
}

function scoreLabel(score: number): string {
  return `${Math.round(score * 100)}% 相关`
}

function formatMessageTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="knowledge-page content-page">
    <section class="page-heading knowledge-heading">
      <div>
        <p class="eyebrow">MY KNOWLEDGE</p>
        <h1>从写过的话里，找回当时的自己。</h1>
        <p>每段问答都会保存，并从你的原文中寻找可以追溯的依据。</p>
      </div>
      <span class="knowledge-version">多轮对话 · 原文可追溯</span>
    </section>

    <section class="knowledge-chat-shell">
      <aside class="knowledge-chat-sidebar" aria-label="知识对话列表">
        <button class="knowledge-new-chat" type="button" :disabled="chatBusy" @click="startNewChat">＋ 新对话</button>
        <p v-if="loadingChats" class="knowledge-sidebar-state">正在加载对话…</p>
        <p v-else-if="!chats.length" class="knowledge-sidebar-state">还没有历史对话</p>
        <div
          v-for="chat in chats"
          :key="chat.chatId"
          class="knowledge-chat-row"
          :class="{ active: activeChatId === chat.chatId }"
        >
          <button class="knowledge-chat-item" type="button" :disabled="chatBusy" :title="chat.title" @click="selectChat(chat.chatId)">
            <strong>{{ chat.title }}</strong>
            <time :datetime="chat.updatedAt">{{ formatMessageTime(chat.updatedAt) }}</time>
          </button>
          <button class="knowledge-chat-menu-toggle" type="button" :disabled="chatBusy" :aria-label="`管理对话：${chat.title}`" :aria-expanded="menuChatId === chat.chatId" @click="menuChatId = menuChatId === chat.chatId ? null : chat.chatId">⋯</button>
          <div v-if="menuChatId === chat.chatId" class="knowledge-chat-actions">
            <button type="button" :disabled="chatBusy" @click="openManagement(chat, 'rename')">修改标题</button>
            <button class="danger" type="button" :disabled="chatBusy" @click="openManagement(chat, 'delete')">删除对话</button>
          </div>
        </div>
      </aside>

      <section class="knowledge-conversation" aria-live="polite">
        <header class="knowledge-conversation-heading">
          <div>
            <p class="eyebrow">KNOWLEDGE CHAT</p>
            <h2>{{ activeChatId ? chats.find((chat) => chat.chatId === activeChatId)?.title : '新的知识对话' }}</h2>
          </div>
          <span>最近 50 轮会作为连续上下文</span>
        </header>

        <div class="knowledge-message-list">
          <button
            v-if="hasMore"
            class="knowledge-load-older"
            type="button"
            :disabled="chatBusy"
            @click="loadOlderMessages"
          >
            {{ loadingOlder ? '正在加载…' : '加载更早的消息' }}
          </button>

          <p v-if="loadingMessages" class="knowledge-conversation-state">正在打开这段对话…</p>

          <div v-else-if="!messages.length" class="knowledge-welcome">
            <span aria-hidden="true">⌕</span>
            <h3>想从记录里找些什么？</h3>
            <p>只有开启“允许 AI 检索”的日记、心得和笔记会参与回答。</p>
            <div class="knowledge-examples" aria-label="问题示例">
              <button v-for="example in examples" :key="example" type="button" :disabled="sending" @click="useExample(example)">
                {{ example }}
              </button>
            </div>
          </div>

          <article
            v-for="message in messages"
            :key="message.messageId"
            class="knowledge-message"
            :class="message.role === 'USER' ? 'user' : 'assistant'"
          >
            <div class="knowledge-message-meta">
              <strong>{{ message.role === 'USER' ? '你' : '小猫' }}</strong>
              <time :datetime="message.createdAt">{{ formatMessageTime(message.createdAt) }}</time>
            </div>
            <p>{{ message.content }}</p>

            <details v-if="message.role === 'ASSISTANT' && message.sources.length" class="knowledge-sources">
              <summary>查看本次引用的 {{ message.sources.length }} 条记录</summary>
              <div class="knowledge-match-list">
                <article
                  v-for="(source, index) in message.sources"
                  :key="`${message.messageId}-${source.recordId}-${index}`"
                  class="knowledge-match-card"
                >
                  <div class="knowledge-match-meta">
                    <span>来源 {{ index + 1 }}</span>
                    <span class="type-chip">{{ recordTypeLabels[source.recordType] }}</span>
                    <time :datetime="source.recordDate">{{ formatRecordDate(source.recordDate) }}</time>
                    <span class="knowledge-score">{{ scoreLabel(source.score) }}</span>
                  </div>
                  <h3>{{ source.title || '没有标题的一天' }}</h3>
                  <blockquote>{{ source.content }}</blockquote>
                  <RouterLink class="text-link strong" :to="`/records/${source.recordId}`">查看这篇原文 →</RouterLink>
                </article>
              </div>
            </details>
          </article>

          <article v-if="sending" class="knowledge-message assistant pending">
            <div class="knowledge-message-meta"><strong>小猫</strong></div>
            <p>正在翻找记录并整理回答…</p>
          </article>
        </div>

        <p v-if="error" class="inline-alert knowledge-alert" role="alert">{{ error }}</p>

        <form class="knowledge-composer" @submit.prevent="send">
          <label for="knowledge-question" class="visually-hidden">输入知识库问题</label>
          <textarea
            id="knowledge-question"
            v-model="question"
            maxlength="500"
            rows="3"
            placeholder="继续提问，或者开始一个新的话题…"
          />
          <div class="knowledge-composer-footer">
            <span>{{ question.length }} / 500</span>
            <button type="submit" :disabled="chatBusy || !question.trim()">
              {{ sending ? '正在回答…' : '发送问题' }}
            </button>
          </div>
        </form>
      </section>
    </section>
    <dialog v-if="managementAction && managedChat" ref="managementDialog" class="knowledge-manage-dialog" aria-labelledby="knowledge-manage-title" @cancel.prevent="closeManagement">
        <form @submit.prevent="saveManagement">
          <h2 id="knowledge-manage-title">{{ managementAction === 'rename' ? '修改对话标题' : '删除对话' }}</h2>
          <template v-if="managementAction === 'rename'">
            <label for="knowledge-chat-title">对话标题</label>
            <input id="knowledge-chat-title" ref="titleInput" v-model="titleDraft" :disabled="managing" required placeholder="请输入对话标题" />
            <p class="knowledge-title-count">{{ Array.from(titleDraft.trim()).length }} / 80 字</p>
          </template>
          <p v-else>确定删除“{{ managedChat.title }}”吗？删除后将无法在列表中查看这段对话。</p>
          <p v-if="managementError" class="inline-alert" role="alert">{{ managementError }}</p>
          <div class="knowledge-dialog-actions">
            <button type="button" class="secondary" :disabled="managing" @click="closeManagement">取消</button>
            <button type="submit" :class="{ danger: managementAction === 'delete' }" :disabled="managing">{{ managing ? '正在保存…' : managementAction === 'rename' ? '保存' : '确认删除' }}</button>
          </div>
        </form>
    </dialog>
  </div>
</template>
