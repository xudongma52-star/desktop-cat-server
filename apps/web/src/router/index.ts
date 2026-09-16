import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import RecordDetailView from '../views/RecordDetailView.vue'
import RecordEditorView from '../views/RecordEditorView.vue'
import RecordListView from '../views/RecordListView.vue'
import EmotionDayView from '../views/EmotionDayView.vue'

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/records', name: 'records', component: RecordListView },
    { path: '/records/new', name: 'record-create', component: RecordEditorView },
    { path: '/records/:recordId', name: 'record-detail', component: RecordDetailView },
    { path: '/records/:recordId/edit', name: 'record-edit', component: RecordEditorView },
    { path: '/emotions', name: 'emotions', component: EmotionDayView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
