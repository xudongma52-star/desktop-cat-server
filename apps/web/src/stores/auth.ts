import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getAuthStatus,
  loginUser,
  logoutUser,
  registerUser,
} from '../api/auth'
import type { AuthCredentials, AuthUser } from '../api/auth'

let initializePromise: Promise<void> | null = null

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const initialized = ref(false)
  const initializationFailed = ref(false)
  const authenticated = computed(() => user.value !== null)

  async function initialize(): Promise<void> {
    if (initialized.value) return
    if (initializePromise) return initializePromise

    initializePromise = (async () => {
      try {
        const status = await getAuthStatus()
        user.value = status.authenticated && status.userId !== null && status.username !== null
          ? { userId: status.userId, username: status.username }
          : null
        initializationFailed.value = false
      } catch {
        user.value = null
        initializationFailed.value = true
      } finally {
        initialized.value = true
        initializePromise = null
      }
    })()

    return initializePromise
  }

  async function login(credentials: AuthCredentials): Promise<void> {
    user.value = await loginUser(credentials)
    initializationFailed.value = false
  }

  async function register(credentials: AuthCredentials): Promise<void> {
    user.value = await registerUser(credentials)
    initializationFailed.value = false
  }

  async function logout(): Promise<void> {
    await logoutUser()
    user.value = null
  }

  return {
    user,
    initialized,
    initializationFailed,
    authenticated,
    initialize,
    login,
    register,
    logout,
  }
})
