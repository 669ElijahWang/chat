import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
// 移除静态导入
// import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.accessToken) {
      config.headers.Authorization = `Bearer ${authStore.accessToken}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data

    // 如果返回的code不是200，则认为是错误
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res
  },
  async (error) => {
    if (error.response) {
      const { status, config } = error.response

      if (status === 401) {
        const reqUrl = config?.url || ''
        const isLoginRequest = reqUrl.includes('/auth/login') || reqUrl.includes('/api/auth/login')
        const isRegisterRequest = reqUrl.includes('/auth/register') || reqUrl.includes('/api/auth/register')
        // 登录/注册接口的 401 错误不应显示“登录已过期”，但需展示后端错误消息
        if (isLoginRequest || isRegisterRequest) {
          ElMessage.error(error.response?.data?.message || '请求失败')
          return Promise.reject(error)
        }

        const authStore = useAuthStore()
        // 动态导入 router 以避免循环依赖
        const router = (await import('@/router')).default

        // 如果有refreshToken，尝试刷新
        if (authStore.refreshToken && !config._retry) {
          config._retry = true

          try {
            const result = await authStore.refreshAccessToken()
            if (result.success) {
              // 刷新成功，重试原请求
              config.headers.Authorization = `Bearer ${authStore.accessToken}`
              return request(config)
            }
          } catch (refreshError) {
            // 刷新失败，清除token并跳转登录
            authStore.logout()
            router.push({ name: 'login' })
            ElMessage.error('登录已过期，请重新登录')
            return Promise.reject(refreshError)
          }
        } else {
          // 没有refreshToken或刷新失败
          authStore.logout()
          router.push({ name: 'login' })
          ElMessage.error('登录已过期，请重新登录')
        }
      } else if (status === 403) {
        ElMessage.error('没有权限访问')
      } else if (status === 404) {
        ElMessage.error('请求的资源不存在')
      } else if (status === 500) {
        ElMessage.error('服务器错误')
      } else {
        ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络连接失败')
    }

    return Promise.reject(error)
  }
)

export default request

