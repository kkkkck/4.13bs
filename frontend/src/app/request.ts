import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

export const API_BASE_URL = '/api'

type RequestInstance = {
  <T = unknown>(config: AxiosRequestConfig): Promise<T>
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
}

const service = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

service.interceptors.request.use((config) => {
  // 所有需要登录的接口都靠 Authorization 里的 Bearer token 识别当前用户。
  // 这样每个 API 文件不用重复写“取 token、放 header”的代码。
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  if (config.data instanceof FormData) {
    // 上传头像等文件时浏览器需要自己生成 multipart boundary，
    // 所以这里移除手动设置的 JSON Content-Type。
    const headers = config.headers as AxiosRequestConfig['headers'] & {
      setContentType?: (value?: string) => void
    }

    if (headers && typeof headers.setContentType === 'function') {
      headers.setContentType(undefined)
    } else if (headers && typeof headers === 'object') {
      delete (headers as Record<string, string>)['Content-Type']
    }
  }

  return config
})

service.interceptors.response.use(
  (response) => {
    const payload = response.data as {
      code?: number
      message?: string
      data?: unknown
    }

    if (payload && typeof payload === 'object' && typeof payload.code === 'number') {
      // 后端统一返回 { code, message, data }；前端只把 data 交给业务页面。
      if (payload.code === 200) {
        return payload.data
      }

      return Promise.reject(new Error(payload.message || 'Request failed'))
    }

    return response.data
  },
  (error: AxiosError<{ message?: string }>) => {
    if (error.response?.status === 401) {
      // 401 表示 token 无效或过期，清掉本地登录信息并回到登录页。
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('role')
      if (!['/login', '/register', '/forgot-password'].includes(window.location.pathname)) {
        const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
        window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`
      }
    }

    const message = error.response?.data?.message || error.message || 'Network error'
    return Promise.reject(new Error(message))
  }
)

const request = ((config: AxiosRequestConfig) => service.request(config)) as RequestInstance

request.get = <T = unknown>(url: string, config?: AxiosRequestConfig) =>
  service.get<unknown, T>(url, config)

request.post = <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
  service.post<unknown, T>(url, data, config)

request.put = <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
  service.put<unknown, T>(url, data, config)

request.delete = <T = unknown>(url: string, config?: AxiosRequestConfig) =>
  service.delete<unknown, T>(url, config)

export default request
