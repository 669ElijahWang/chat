import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authAPI } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const accessToken = ref(localStorage.getItem('accessToken') || '')
  const refreshToken = ref(localStorage.getItem('refreshToken') || '')

  const isAuthenticated = computed(() => !!accessToken.value)

  async function login(data) {
    try {
      const response = await authAPI.login(data)
      if (response.code === 200) {
        setAuth(response.data)
        return { success: true }
      }
      return { success: false, message: response.message }
    } catch (error) {
      // 优先使用后端返回的错误信息（如：用户名或密码错误）
      const message = error?.response?.data?.message || error.message || '登录失败'
      return { success: false, message }
    }
  }

  async function register(data) {
    try {
      const response = await authAPI.register(data)
      if (response.code === 200) {
        setAuth(response.data)
        return { success: true }
      }
      return { success: false, message: response.message }
    } catch (error) {
      return { success: false, message: error.message }
    }
  }

  function setAuth(data) {
    user.value = data.userInfo
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
  }

  async function refreshAccessToken() {
    try {
      const response = await authAPI.refreshToken(refreshToken.value)
      if (response.code === 200) {
        setAuth(response.data)
        return { success: true }
      }
      return { success: false, message: response.message }
    } catch (error) {
      return { success: false, message: error.message }
    }
  }

  function logout() {
    user.value = null
    accessToken.value = ''
    refreshToken.value = ''
    
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
  }

  return {
    user,
    accessToken,
    refreshToken,
    isAuthenticated,
    login,
    register,
    refreshAccessToken,
    logout,
    setAuth
  }
})

