import request from './request'

export const authAPI = {
  // 登录
  login(data) {
    return request.post('/auth/login', data)
  },

  // 获取图片验证码
  getCaptcha() {
    return request.get('/auth/captcha')
  },

  // 注册
  register(data) {
    return request.post('/auth/register', data)
  },

  // 登出
  logout() {
    return request.post('/auth/logout')
  },

  // 刷新token
  refreshToken(refreshToken) {
    return request.post('/auth/refresh', null, {
      params: { refreshToken }
    })
  },

  // 获取当前用户信息
  getCurrentUser() {
    return request.get('/auth/me')
  }
}

