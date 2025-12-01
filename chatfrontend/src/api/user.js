import request from './request'

export const userAPI = {
  // 更新用户信息
  updateProfile(data) {
    return request.put('/user/profile', data)
  },

  // 修改密码
  updatePassword(data) {
    return request.put('/user/password', data)
  }
}

