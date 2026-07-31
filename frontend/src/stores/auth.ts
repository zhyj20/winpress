import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import http from '@/api/http'
import type { ApiResponse, User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('winpress_token') || '')
  const user = ref<User | null>(readStoredUser())
  const isAuthenticated = computed(() => Boolean(token.value && user.value))

  function readStoredUser(): User | null {
    const rawUser = localStorage.getItem('winpress_user')
    if (!rawUser) return null
    try {
      return JSON.parse(rawUser) as User
    } catch {
      localStorage.removeItem('winpress_token')
      localStorage.removeItem('winpress_user')
      token.value = ''
      return null
    }
  }

  function persist(nextToken: string, nextUser: User) {
    token.value = nextToken
    user.value = nextUser
    localStorage.setItem('winpress_token', nextToken)
    localStorage.setItem('winpress_user', JSON.stringify(nextUser))
  }

  async function login(username: string, password: string) {
    const response = await http.post<ApiResponse<{ token: string; user: User }>>('/auth/login', {
      username: username.trim(),
      password: password.trim(),
    })
    const data = response.data
    if (data?.success !== true || !data?.data?.token) {
      throw new Error(data?.message || '登录返回异常')
    }
    persist(data.data.token, data.data.user)
    return data.data.user
  }

  async function register(payload: Record<string, string>) {
    const { data } = await http.post<ApiResponse<{ token: string; user: User }>>(
      '/auth/register',
      payload,
    )
    persist(data.data.token, data.data.user)
  }

  async function loadMe() {
    if (!token.value) return null
    const { data } = await http.get<ApiResponse<User>>('/auth/me')
    user.value = data.data
    localStorage.setItem('winpress_user', JSON.stringify(data.data))
    return user.value
  }

  async function logout() {
    try {
      await http.post('/auth/logout')
    } catch {
      // 服务异常暂不处理，确保前端会话清理完成
    } finally {
      token.value = ''
      user.value = null
      localStorage.removeItem('winpress_token')
      localStorage.removeItem('winpress_user')
    }
  }

  return { token, user, isAuthenticated, login, register, loadMe, logout }
})
