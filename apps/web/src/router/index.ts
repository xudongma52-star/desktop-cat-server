import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import RecordDetailView from '../views/RecordDetailView.vue'
import RecordEditorView from '../views/RecordEditorView.vue'
import RecordListView from '../views/RecordListView.vue'
import EmotionDayView from '../views/EmotionDayView.vue'
import ReminderView from '../views/ReminderView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import DesktopConnectView from '../views/DesktopConnectView.vue'
import KnowledgeView from '../views/KnowledgeView.vue'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true, layout: 'auth' } },
    { path: '/register', name: 'register', component: RegisterView, meta: { guestOnly: true, layout: 'auth' } },
    { path: '/desktop/connect', name: 'desktop-connect', component: DesktopConnectView, meta: { requiresAuth: true, layout: 'auth' } },
    { path: '/', name: 'home', component: HomeView, meta: { requiresAuth: true } },
    { path: '/records', name: 'records', component: RecordListView, meta: { requiresAuth: true } },
    { path: '/records/new', name: 'record-create', component: RecordEditorView, meta: { requiresAuth: true } },
    { path: '/records/:recordId', name: 'record-detail', component: RecordDetailView, meta: { requiresAuth: true } },
    { path: '/records/:recordId/edit', name: 'record-edit', component: RecordEditorView, meta: { requiresAuth: true } },
    { path: '/knowledge', name: 'knowledge', component: KnowledgeView, meta: { requiresAuth: true } },
    { path: '/emotions', name: 'emotions', component: EmotionDayView, meta: { requiresAuth: true } },
    { path: '/reminders', name: 'reminders', component: ReminderView, meta: { requiresAuth: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore(pinia)
  await auth.initialize()

  if (to.meta.requiresAuth && !auth.authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && auth.authenticated) {
    return { name: 'home' }
  }
  return true
})
