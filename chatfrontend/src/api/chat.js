import request from './request'

export const chatAPI = {
  // 创建会话
  createConversation(title) {
    // 仅在提供标题时传递参数，支持“首条问题作为标题”的流程
    const config = {}
    if (title !== undefined && title !== null && title !== '') {
      config.params = { title }
    }
    return request.post('/chat/conversations', null, config)
  },

  // 获取会话列表
  getConversations(params) {
    return request.get('/chat/conversations', { params })
  },

  // 获取会话详情
  getConversation(id) {
    return request.get(`/chat/conversations/${id}`)
  },

  // 更新会话标题
  updateConversationTitle(id, title) {
    return request.put(`/chat/conversations/${id}/title`, null, {
      params: { title }
    })
  },

  // 删除会话
  deleteConversation(id) {
    return request.delete(`/chat/conversations/${id}`)
  },

  // 发送消息（非流式）
  sendMessage(data) {
    return request.post('/chat/messages', data)
  },

  // 发送消息（流式）
  sendMessageStream(data, onMessage, onError, onComplete) {
    // 注意：原 EventSource 方式无法携带 Authorization 头，浏览器会忽略 headers。
    // 改为 fetch + ReadableStream 解析 SSE，确保在生产与本地都能实时渲染。
    const authToken = localStorage.getItem('accessToken')
    const controller = new AbortController()

    const payload = {
      conversationId: data.conversationId,
      content: data.content,
      model: data.model || 'deepseek-chat',
      temperature: data.temperature || 0.7,
      knowledgeBaseIds: data.knowledgeBaseIds || [],
      ragTopK: data.ragTopK || 3,
      // 允许调用方提升/控制最大输出 token
      ...(data.maxTokens ? { maxTokens: data.maxTokens } : {})
    }

    fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Authorization': `Bearer ${authToken}`
      },
      body: JSON.stringify(payload),
      signal: controller.signal
    }).then(async (response) => {
      if (!response.ok) {
        const err = new Error('请求失败: ' + response.status)
        if (onError) onError(err)
        return
      }

      

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      // 统一的缓冲处理：既支持按事件分隔（\r?\n\r?\n），也支持逐行解析命名事件与 data
      const handleEvent = (evt) => {
        let eventName = null
        const dataLines = []
        for (const line of evt.split(/\r?\n/)) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5))
          }
        }
        const dataStr = dataLines.join('\n')
        const trimmed = dataStr.trim()
        if (!trimmed) return false

        // 兼容命名错误事件
        if (eventName === 'error') {
          try {
            const obj = JSON.parse(trimmed)
            const msg = obj?.message || trimmed
            console.error('[SSE error(event)]', obj)
            if (onError) onError(new Error(msg))
          } catch (e) {
            if (onError) onError(new Error(trimmed))
          }
          try { controller.abort() } catch {}
          return true
        }

        // 兼容命名 final 事件：视为完整内容并完成
        if (eventName === 'final') {
          if (onMessage) onMessage(dataStr)
          if (onComplete) onComplete()
          try { controller.abort() } catch {}
          return true
        }

        // 兼容旧式标记
        if (trimmed === '[DONE]') {
          if (onComplete) onComplete()
          try { controller.abort() } catch {}
          return true
        }
        if (trimmed.startsWith('[ERROR]')) {
          console.error('[SSE error]', trimmed)
          if (onError) onError(new Error(trimmed.substring(7)))
          try { controller.abort() } catch {}
          return true
        }

        
        if (onMessage) onMessage(dataStr)
        return false
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // 1) 先按事件分隔处理完整事件
        const events = buffer.split(/\r?\n\r?\n/)
        buffer = events.pop() || ''
        for (const evt of events) {
          const stop = handleEvent(evt)
          if (stop) return
        }

        // 2) 再处理剩余缓冲中的完整行（没有事件分隔但出现了 data: 行）
        const lines = buffer.split(/\r?\n/)
        buffer = lines.pop() || '' // 保留最后一个可能不完整的行
        // 将每个完整行当作单行事件处理（适配某些实现按行刷新的情况）
        for (const line of lines) {
          const evt = line
          const stop = handleEvent(evt)
          if (stop) return
        }
      }
      if (onComplete) onComplete()
    }).catch((err) => {
      console.error('[SSE fetch error]', err)
      if (onError) onError(err)
    })

    // 提供关闭方法供调用方使用
    return { close: () => controller.abort() }
  },

  // 获取会话消息列表
  getMessages(conversationId) {
    return request.get(`/chat/conversations/${conversationId}/messages`)
  },

  // 发送带文件的消息（流式）
  sendMessageWithFile(data, onMessage, onError, onComplete) {
    const authToken = localStorage.getItem('accessToken')
    const controller = new AbortController()

    // 构建FormData
    const formData = new FormData()
    formData.append('conversationId', data.conversationId)
    formData.append('content', data.content)
    formData.append('model', data.model || 'deepseek-chat')
    formData.append('temperature', data.temperature || 0.7)
    formData.append('stream', 'true')
    formData.append('ragTopK', data.ragTopK || 3)
    formData.append('file', data.file)
    if (data.maxTokens) formData.append('maxTokens', String(data.maxTokens))
    
    // 添加知识库IDs
    if (data.knowledgeBaseIds && data.knowledgeBaseIds.length > 0) {
      data.knowledgeBaseIds.forEach(id => {
        formData.append('knowledgeBaseIds', id)
      })
    }

    fetch('/api/chat/messages/with-file', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${authToken}`
        // 注意：不要设置Content-Type，让浏览器自动设置multipart/form-data的boundary
      },
      body: formData,
      signal: controller.signal
    }).then(async (response) => {
      if (!response.ok) {
        const err = new Error('请求失败: ' + response.status)
        if (onError) onError(err)
        return
      }

      

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      const handleEvent = (evt) => {
        let eventName = null
        const dataLines = []
        for (const line of evt.split(/\r?\n/)) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5))
          }
        }
        const dataStr = dataLines.join('\n')
        const trimmed = dataStr.trim()
        if (!trimmed) return false

        if (eventName === 'error') {
          try {
            const obj = JSON.parse(trimmed)
            const msg = obj?.message || trimmed
            console.error('[SSE error(event)]', obj)
            if (onError) onError(new Error(msg))
          } catch (e) {
            if (onError) onError(new Error(trimmed))
          }
          try { controller.abort() } catch {}
          return true
        }

        if (eventName === 'final') {
          if (onMessage) onMessage(dataStr)
          if (onComplete) onComplete()
          try { controller.abort() } catch {}
          return true
        }

        if (trimmed === '[DONE]') {
          if (onComplete) onComplete()
          try { controller.abort() } catch {}
          return true
        }
        if (trimmed.startsWith('[ERROR]')) {
          console.error('[SSE error]', trimmed)
          if (onError) onError(new Error(trimmed.substring(7)))
          try { controller.abort() } catch {}
          return true
        }

        
        if (onMessage) onMessage(dataStr)
        return false
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        const events = buffer.split(/\r?\n\r?\n/)
        buffer = events.pop() || ''
        for (const evt of events) {
          const stop = handleEvent(evt)
          if (stop) return
        }

        const lines = buffer.split(/\r?\n/)
        buffer = lines.pop() || ''
        for (const line of lines) {
          const evt = line
          const stop = handleEvent(evt)
          if (stop) return
        }
      }
      
      if (onComplete) onComplete()
    }).catch((err) => {
      console.error('[SSE fetch error]', err)
      if (onError) onError(err)
    })

    return { close: () => controller.abort() }
  }
}

