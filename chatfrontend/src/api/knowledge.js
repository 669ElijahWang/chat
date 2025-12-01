import request from './request'

export const knowledgeAPI = {
  // 创建知识库
  createKnowledgeBase(data) {
    return request.post('/knowledge/bases', data)
  },

  // 获取知识库列表
  getKnowledgeBases(params) {
    return request.get('/knowledge/bases', { params })
  },

  // 获取知识库详情
  getKnowledgeBase(id) {
    return request.get(`/knowledge/bases/${id}`)
  },

  // 删除知识库
  deleteKnowledgeBase(id) {
    return request.delete(`/knowledge/bases/${id}`)
  },

  // 添加文档
  addDocument(id, data) {
    return request.post(`/knowledge/bases/${id}/documents`, data)
  },

  // 批量添加文档
  addDocuments(id, data) {
    return request.post(`/knowledge/bases/${id}/documents/batch`, data)
  },

  // 搜索文档
  searchDocuments(id, data) {
    return request.post(`/knowledge/bases/${id}/search`, data)
  },

  // 从文件添加文档
  addDocumentFromFile(id, formData) {
    return request.post(`/knowledge/bases/${id}/documents/file`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 从URL添加文档
  addDocumentFromUrl(id, data) {
    return request.post(`/knowledge/bases/${id}/documents/url`, data)
  },

  // 从文本添加文档
  addDocumentFromText(id, data) {
    return request.post(`/knowledge/bases/${id}/documents/text`, data)
  },

  // 预览文本切分结果
  previewTextSplit(id, data) {
    return request.post(`/knowledge/bases/${id}/documents/text/preview`, data)
  },

  // 预览URL切分结果
  previewUrlSplit(id, data) {
    return request.post(`/knowledge/bases/${id}/documents/url/preview`, data)
  },

  // 预览文件切分结果
  previewFileSplit(id, formData) {
    return request.post(`/knowledge/bases/${id}/documents/file/preview`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 在多个知识库中搜索
  searchInMultipleBases(data) {
    return request.post('/knowledge/search', data)
  },

  // 获取知识库的文档列表
  getDocuments(id) {
    return request.get(`/knowledge/bases/${id}/documents`)
  }
}

