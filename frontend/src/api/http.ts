import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('winpress_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('winpress_token')
      localStorage.removeItem('winpress_user')
      if (!location.pathname.includes('/login')) {
        const redirect = `${location.pathname}${location.search}${location.hash}`
        location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)
      }
    }
    return Promise.reject(error)
  },
)

export function apiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED') {
      return '请求处理超时，请稍后重试。'
    }
    if (error.code === 'ERR_NETWORK') {
      return '当前连接异常，请稍后重试或联系服务团队。'
    }
    if (error.response?.status === 401) {
      return '账号或密码不正确'
    }
    if (error.response?.status === 403) {
      return '当前账号无权执行此操作。'
    }
    const payload = error.response?.data as { code?: unknown; message?: unknown } | undefined
    const code = typeof payload?.code === 'string' ? payload.code : ''
    const message = typeof payload?.message === 'string' ? payload.message : ''
    const internalMarker =
      /supplier|cost|upstream|token|secret|jdbc|sql|postgres|redis|stack|exception|federation|internal/i
    if (error.response?.status && error.response.status >= 500) {
      return '服务暂时无法完成请求，请稍后重试。'
    }
    if (internalMarker.test(`${code} ${message}`)) {
      return '本次操作暂时无法完成，请稍后重试或联系服务团队。'
    }
    return message || '本次操作未完成，请核对填写内容后重试。'
  }
  return '本次操作未完成，请稍后重试。'
}

export default http
