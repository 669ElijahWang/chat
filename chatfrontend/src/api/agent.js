import request from './request'

export const agentAPI = {
  // 创建Agent
  createAgent(data) {
    return request.post('/agents', data)
  },

  // 获取Agent列表
  getAgents(params) {
    return request.get('/agents', { params })
  },

  // 获取Agent详情
  getAgent(id) {
    return request.get(`/agents/${id}`)
  },

  // 更新Agent
  updateAgent(id, data) {
    return request.put(`/agents/${id}`, data)
  },

  // 删除Agent
  deleteAgent(id) {
    return request.delete(`/agents/${id}`)
  },

  // 获取可用工具
  getAvailableTools() {
    return request.get('/agents/tools')
  }
}

