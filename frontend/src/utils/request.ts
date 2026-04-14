import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'

interface Result<T = any> {
  code: number
  message: string
  data: T
}

class Request {
  private instance: AxiosInstance

  constructor() {
    this.instance = axios.create({
      baseURL: '/api',
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json;charset=utf-8'
      }
    })

    this.setupInterceptors()
  }

  private setupInterceptors() {
    this.instance.interceptors.request.use(
      (config) => {
        const token = localStorage.getItem('token')
        if (token) {
          config.headers.Authorization = `Bearer ${token}`
        }
        return config
      },
      (error) => {
        return Promise.reject(error)
      }
    )

    this.instance.interceptors.response.use(
      (response: AxiosResponse<Result>) => {
        const { code, message } = response.data
        if (code === 200) {
          return response.data
        } else {
          console.error(message)
          return Promise.reject(new Error(message))
        }
      },
      (error) => {
        if (error.response) {
          const { status, data } = error.response
          switch (status) {
            case 401:
              console.error('未授权，请重新登录')
              localStorage.removeItem('token')
              localStorage.removeItem('user')
              window.location.href = '/login'
              break
            case 403:
              console.error('没有权限访问')
              break
            case 404:
              console.error('请求的资源不存在')
              break
            case 500:
              console.error('服务器内部错误')
              break
            default:
              console.error(data?.message || '请求失败')
          }
        } else if (error.request) {
          console.error('网络错误，请检查网络连接')
        } else {
          console.error('请求配置错误', error.message)
        }
        return Promise.reject(error)
      }
    )
  }

  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
    return this.instance.get(url, config)
  }

  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return this.instance.post(url, data, config)
  }

  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return this.instance.put(url, data, config)
  }

  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
    return this.instance.delete(url, config)
  }
}

export default new Request()