<template>
  <div class="chat-view">
    <div class="conversations-panel">
      <div class="panel-header">
        <el-button type="primary" @click="createNewConversation" style="width: 100%">
          <el-icon><Plus /></el-icon>
          新建对话
        </el-button>
      </div>

      <div class="conversations-list" @scroll="onConversationsScroll">
        <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conversation-item"
            :class="{ active: currentConversationId === conv.id }"
            @click="selectConversation(conv.id)"
        >
          <div class="conversation-title">{{ conv.title }}</div>
          <div class="conversation-time">{{ formatTime(conv.lastMessageAt || conv.createdAt) }}</div>
          <el-icon class="conversation-delete" @click.stop="deleteConversation(conv.id)"><Delete /></el-icon>
        </div>
        <div v-if="convLoading" style="padding: 12px; text-align: center; color: #999;">加载中…</div>
      </div>
    </div>

    <div class="chat-panel">
      <div v-if="!currentConversationId" class="empty-state">
        <el-empty description="请选择或创建一个对话" />
      </div>

      <template v-else>
        <div class="messages-container" ref="messagesContainer">
          <transition-group name="message-fade">
            <div
                v-for="msg in messages"
                :key="msg.messageId"
                class="message-item"
                :class="msg.role"
            >
              <div class="message-avatar">
                <el-avatar v-if="msg.role === 'USER'" :size="32" class="user-avatar">U</el-avatar>
                <el-avatar v-else :size="32" class="ai-avatar">AI</el-avatar>
              </div>
              <div class="message-content-wrapper">
                <!-- RAG文档引用（仅AI回复且有RAG文档时显示） -->
                <div v-if="msg.role === 'ASSISTANT' && msg.ragDocs && msg.ragDocs.length > 0" class="rag-docs-section">
                  <el-collapse>
                    <el-collapse-item>
                      <template #title>
                        <div class="rag-docs-title">
                          <el-icon><Document /></el-icon>
                          <span>参考了 {{ msg.ragDocs.length }} 个知识库文档</span>
                        </div>
                      </template>
                      <div class="rag-docs-list">
                        <el-card v-for="(doc, index) in msg.ragDocs" :key="doc.documentId" class="rag-doc-card" shadow="hover">
                          <div class="rag-doc-header">
                            <span>文档 #{{ index + 1 }}</span>
                            <el-tag size="small" type="success">{{ doc.knowledgeBaseTitle }}</el-tag>
                          </div>
                          <div class="rag-doc-content">{{ doc.content }}</div>
                        </el-card>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </div>
                <!-- 流式阶段：增量 Markdown 渲染（节流），即时显示格式 -->
                <div v-if="msg.streaming" class="message-content" v-html="streamingHtml"></div>
                <!-- 完成后：一次性使用 Markdown 渲染 -->
                <div v-else class="message-content" v-html="formatContent(msg.content)"></div>
                <span v-if="msg.streaming" class="streaming-cursor">▊</span>
              </div>
            </div>
          </transition-group>
        </div>

        <div class="input-container">
          <div class="knowledge-selector">
            <el-select
              v-model="selectedKnowledgeBases"
              multiple
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择知识库（可选）"
              style="width: 100%; margin-bottom: 10px;"
              clearable
            >
              <el-option
                v-for="kb in availableKnowledgeBases"
                :key="kb.id"
                :label="kb.title"
                :value="kb.id"
              />
            </el-select>
          </div>
          <div style="display:flex; gap:12px; margin-bottom:10px; align-items:center;">
            <span style="font-size:12px; color:#666;">模型</span>
            <el-select v-model="selectedModel" placeholder="选择模型" style="flex:1;" size="small">
              <el-option label="DeepSeek" value="deepseek-chat" />
              <el-option label="Qwen" value="qwen-turbo" />
              <el-option label="智谱 GLM" value="glm-4" />
            </el-select>
          </div>
          
          <!-- 文件上传区域 -->
          <div v-if="uploadedFile" class="uploaded-file-preview">
            <el-card shadow="hover">
              <!-- 图片预览 -->
              <div v-if="isImageFile(uploadedFile.name)" class="image-preview-container">
                <img :src="imagePreviewUrl" alt="预览" class="image-preview" />
              </div>
              
              <!-- 文件信息 -->
              <div class="file-info">
                <el-icon class="file-icon">
                  <Picture v-if="isImageFile(uploadedFile.name)" />
                  <Document v-else />
                </el-icon>
                <div class="file-details">
                  <span class="file-name">{{ uploadedFile.name }}</span>
                  <span class="file-size">{{ formatFileSize(uploadedFile.size) }}</span>
                  <span v-if="getFileType(uploadedFile.name)" class="file-type-badge">
                    {{ getFileType(uploadedFile.name) }}
                  </span>
                </div>
                <el-button 
                  type="danger" 
                  size="small" 
                  :icon="Delete" 
                  circle 
                  @click="removeFile"
                  class="remove-btn"
                />
              </div>
            </el-card>
          </div>
          
          <div class="input-row">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleFileChange"
              accept=".pdf,.txt,.doc,.docx,.md,.jpg,.jpeg,.png,.gif,.bmp,.webp,.svg,.java,.py,.js,.ts,.jsx,.tsx,.cpp,.c,.h,.hpp,.cs,.go,.rs,.rb,.php,.swift,.kt,.scala,.r,.sql,.sh,.bash,.ps1,.bat,.cmd,.xml,.json,.yaml,.yml,.css,.scss,.sass,.less,.vue,.html,.htm"
              :limit="1"
            >
              <el-button :icon="Paperclip" circle title="上传文件（文档、图片、代码等）" />
            </el-upload>
            
            <el-input
                v-model="inputMessage"
                type="textarea"
                :rows="3"
                placeholder="输入消息... (Shift+Enter换行，Enter发送)"
                @keydown.enter.exact.prevent="sendMessage"
            />
            <el-button type="primary" @click="sendMessage" :loading="loading">
              发送
            </el-button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { chatAPI } from '@/api/chat'
import { knowledgeAPI } from '@/api/knowledge'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Delete, Paperclip, Picture } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

// 初始化Markdown渲染器
const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  breaks: true, // 将 \n 转换为 <br>
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' +
            hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
            '</code></pre>'
      } catch (err) {
        console.error('Highlight error:', err)
      }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  }
})

const conversations = ref([])
const currentConversationId = ref(null)
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)
// 流式阶段的增量 Markdown HTML
const streamingHtml = ref('')
// 知识库相关
const availableKnowledgeBases = ref([])
const selectedKnowledgeBases = ref([])
const selectedModel = ref('deepseek-chat')
// 文件上传相关
const uploadedFile = ref(null)
const uploadRef = ref(null)
const imagePreviewUrl = ref(null)

// 会话列表滚动加载（分页）
const convPage = ref(0)
const convPageSize = ref(50)
const convHasMore = ref(true)
const convLoading = ref(false)

// 统一的排序：按最近改动时间（lastMessageAt/updatedAt）倒序
const sortConversations = () => {
  conversations.value.sort((a, b) => {
    const ta = new Date(a?.lastMessageAt || a?.updatedAt || a?.createdAt || 0).getTime()
    const tb = new Date(b?.lastMessageAt || b?.updatedAt || b?.createdAt || 0).getTime()
    return tb - ta
  })
}

// 将不完整的“管道表格”在流式阶段乐观转换为表格
// 说明：模型常在完成前才补齐"|---|---|"分隔行，导致前期无法按表格渲染。
// 该方法在检测到可能的表头行时为渲染副本补齐分隔行，不修改原始内容。
const makeStreamingTablesOptimistic = (raw) => {
  if (!raw) return ''
  const lines = raw.split('\n')
  const out = []
  let inCode = false
  let tableActive = false

  const isSeparator = (s) => /^(\s*\|)?\s*:?-{2,}\s*(\|\s*:?-{2,}\s*)+(\|\s*)?$/.test(s.trim())
  const isPipeLine = (s) => {
    const t = s.trim()
    // 排除引用、标题、代码栈等常见干扰
    if (!t || t.startsWith('#') || t.startsWith('>')) return false
    const parts = t.split('|').filter(seg => seg.trim().length > 0)
    return parts.length >= 2
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()

    // 代码块保护：不在代码块内做表格处理
    if (trimmed.startsWith('```')) {
      inCode = !inCode
      out.push(line)
      continue
    }

    out.push(line)

    if (inCode) continue

    // 检测可能表头并补齐分隔行（仅在新表开始时做一次）
    if (!tableActive && isPipeLine(line)) {
      const next = lines[i + 1] ?? ''
      if (!isSeparator(next)) {
        const colCount = line.split('|').filter(seg => seg.trim().length > 0).length
        if (colCount >= 2) {
          const sep = '|' + Array(colCount).fill('---').join('|') + '|'
          out.push(sep)
          tableActive = true
        }
      } else {
        tableActive = true
      }
      continue
    }

    // 空行视为表格结束
    if (!trimmed) {
      tableActive = false
    }
  }

  return out.join('\n')
}

// 渲染节流，避免每个片段都触发重渲染导致卡顿
const RENDER_INTERVAL = 80
let renderTimer = null
const scheduleStreamingRender = async (text) => {
  if (renderTimer) return
  renderTimer = setTimeout(async () => {
    const optimistic = makeStreamingTablesOptimistic(text)
    streamingHtml.value = md.render(optimistic)
    renderTimer = null
    await nextTick()
    scrollToBottom()
  }, RENDER_INTERVAL)
}

onMounted(() => {
  loadConversations(true)
  loadKnowledgeBases()
})

const loadKnowledgeBases = async () => {
  try {
    const res = await knowledgeAPI.getKnowledgeBases({ size: 100 })
    availableKnowledgeBases.value = res.data.content || []
  } catch (error) {
    console.error('加载知识库列表失败', error)
  }
}

const loadConversations = async (reset = false) => {
  if (convLoading.value) return
  if (reset) {
    convPage.value = 0
    convHasMore.value = true
    conversations.value = []
  }
  if (!convHasMore.value) return
  convLoading.value = true
  try {
    const res = await chatAPI.getConversations({ page: convPage.value, size: convPageSize.value, sort: 'lastMessageAt,DESC' })
    const pageData = res?.data || {}
    const list = pageData?.content || []
    // 过滤：不在“对话”中展示 nocode 的 AI 应用生成会话
    const filtered = list.filter((c) => {
      const title = (c?.title || '').trim()
      return !/^AI应用生成\s*[:：]/.test(title)
    })
    const existingIds = new Set(conversations.value.map(c => c.id))
    filtered.forEach(item => {
      if (!existingIds.has(item.id)) {
        conversations.value.push(item)
      }
    })
    sortConversations()
    const number = typeof pageData?.number === 'number' ? pageData.number : convPage.value
    const totalPages = pageData?.totalPages
    let hasMore
    if (typeof totalPages === 'number') {
      hasMore = number + 1 < totalPages
    } else {
      hasMore = filtered.length >= convPageSize.value
    }
    convHasMore.value = hasMore
    convPage.value += 1
  } catch (error) {
    ElMessage.error('加载对话列表失败')
  } finally {
    convLoading.value = false
  }
}

const onConversationsScroll = (e) => {
  const el = e.target
  if (!el) return
  const nearBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 20
  if (nearBottom && convHasMore.value && !convLoading.value) {
    loadConversations(false)
  }
}

const createNewConversation = async () => {
  try {
    // 创建时不设置标题，待首条问题发送后再命名
    const res = await chatAPI.createConversation()
    conversations.value.unshift(res.data)
    sortConversations()
    selectConversation(res.data.id)
  } catch (error) {
    ElMessage.error('创建对话失败')
  }
}

const selectConversation = async (id) => {
  currentConversationId.value = id
  await loadMessages(id)
}

const loadMessages = async (conversationId) => {
  try {
    const res = await chatAPI.getMessages(conversationId)
    messages.value = res.data
    await nextTick()
    scrollToBottom()
  } catch (error) {
    ElMessage.error('加载消息失败')
  }
}

// 处理文件选择
const handleFileChange = (file) => {
  const maxSize = 10 * 1024 * 1024 // 10MB
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过10MB')
    return
  }
  
  // 支持的文件类型
  const allowedExtensions = [
    // 文档
    '.pdf', '.txt', '.doc', '.docx', '.md', '.markdown', '.html', '.htm',
    // 图片
    '.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.svg',
    // 代码文件
    '.java', '.py', '.js', '.ts', '.jsx', '.tsx', '.vue',
    '.cpp', '.c', '.h', '.hpp', '.cs', '.go', '.rs', '.rb', 
    '.php', '.swift', '.kt', '.scala', '.r', '.sql',
    '.sh', '.bash', '.ps1', '.bat', '.cmd',
    '.xml', '.json', '.yaml', '.yml',
    '.css', '.scss', '.sass', '.less'
  ]
  
  const fileName = file.name.toLowerCase()
  const isAllowed = allowedExtensions.some(type => fileName.endsWith(type))
  
  if (!isAllowed) {
    ElMessage.error('不支持的文件类型。支持：文档、图片、代码文件等')
    return
  }
  
  uploadedFile.value = file.raw
  
  // 根据文件类型显示不同的提示
  const fileExt = fileName.substring(fileName.lastIndexOf('.')).toLowerCase()
  const imageExts = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.svg']
  const codeExts = ['.java', '.py', '.js', '.ts', '.jsx', '.tsx', '.cpp', '.c', '.go', '.rs']
  
  // 如果是图片，生成预览
  if (imageExts.includes(fileExt)) {
    const reader = new FileReader()
    reader.onload = (e) => {
      imagePreviewUrl.value = e.target.result
    }
    reader.readAsDataURL(file.raw)
    ElMessage.warning({
      message: '图片已选择，但AI暂不支持图片内容识别，只能提供基本信息。建议用文字描述图片内容。',
      duration: 5000
    })
  } else if (codeExts.includes(fileExt)) {
    imagePreviewUrl.value = null
    ElMessage.success('代码文件已选择: ' + file.name)
  } else {
    imagePreviewUrl.value = null
    ElMessage.success('文件已选择: ' + file.name)
  }
}

// 判断是否是图片文件
const isImageFile = (filename) => {
  if (!filename) return false
  const imageExts = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.svg']
  const ext = filename.substring(filename.lastIndexOf('.')).toLowerCase()
  return imageExts.includes(ext)
}

// 获取文件类型标签
const getFileType = (filename) => {
  if (!filename) return ''
  const ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
  
  const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
  const codeExts = ['java', 'py', 'js', 'ts', 'jsx', 'tsx', 'cpp', 'c', 'go', 'rs', 'vue', 'html', 'css']
  const docExts = ['pdf', 'doc', 'docx', 'txt', 'md']
  
  if (imageExts.includes(ext)) return '图片'
  if (codeExts.includes(ext)) return '代码'
  if (docExts.includes(ext)) return '文档'
  return ''
}

// 移除已选择的文件
const removeFile = () => {
  uploadedFile.value = null
  imagePreviewUrl.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const sendMessage = async () => {
  if ((!inputMessage.value.trim() && !uploadedFile.value) || loading.value) return

  const content = inputMessage.value.trim() || '请帮我分析这个文件'
  const fileToUpload = uploadedFile.value
  inputMessage.value = ''
  uploadedFile.value = null
  imagePreviewUrl.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }

  // 是否为该会话的首条消息，用于将标题命名为首个问题
  const isFirstMessage = messages.value.length === 0

  // 添加用户消息到界面
  messages.value.push({
    messageId: Date.now(),
    conversationId: currentConversationId.value,
    role: 'USER',
    content: content
  })

  await nextTick()
  scrollToBottom()

  // 添加一个临时的助手消息用于流式输出（使用 reactive 确保内容变更触发视图更新）
  const tempAssistantMessage = reactive({
    messageId: Date.now() + 1,
    conversationId: currentConversationId.value,
    role: 'ASSISTANT',
    content: '',
    streaming: true
  })
  messages.value.push(tempAssistantMessage)
  // 初始化流式 HTML 清空
  streamingHtml.value = ''

  loading.value = true

  try {
    // 若为首条问题，立即用该问题更新会话标题
    if (isFirstMessage) {
      try {
        const titleContent = fileToUpload ? `上传文件: ${fileToUpload.name}` : content
        await chatAPI.updateConversationTitle(currentConversationId.value, titleContent)
        await loadConversations(true)
      } catch (e) {
        console.warn('更新会话标题失败：', e)
      }
    }
    
    // 根据是否有文件选择不同的发送方式
    if (fileToUpload) {
      // 使用文件上传API
      chatAPI.sendMessageWithFile(
        {
          conversationId: currentConversationId.value,
          content: content,
          model: selectedModel.value,
          temperature: 0.7,
          knowledgeBaseIds: selectedKnowledgeBases.value,
          ragTopK: 3,
          file: fileToUpload
        },
        async (data) => {
          
          tempAssistantMessage.content += data
          scheduleStreamingRender(tempAssistantMessage.content)
        },
        (err) => {
          console.error('[UI error]', err)
          ElMessage.error('AI响应失败: ' + err.message)
          const index = messages.value.indexOf(tempAssistantMessage)
          if (index > -1) messages.value.splice(index, 1)
        },
        async () => {
          
          tempAssistantMessage.streaming = false
          streamingHtml.value = md.render(tempAssistantMessage.content)
          await loadMessages(currentConversationId.value)
          await loadConversations(true)
        }
      )
    } else {
      // 使用普通流式API
      chatAPI.sendMessageStream(
        {
          conversationId: currentConversationId.value,
          content: content,
          model: selectedModel.value,
          temperature: 0.7,
          knowledgeBaseIds: selectedKnowledgeBases.value,
          ragTopK: 3
        },
        async (data) => {
          
          tempAssistantMessage.content += data
          scheduleStreamingRender(tempAssistantMessage.content)
        },
        (err) => {
          console.error('[UI error]', err)
          ElMessage.error('AI响应失败: ' + err.message)
          const index = messages.value.indexOf(tempAssistantMessage)
          if (index > -1) messages.value.splice(index, 1)
        },
        async () => {
          
          tempAssistantMessage.streaming = false
          streamingHtml.value = md.render(tempAssistantMessage.content)
          await loadMessages(currentConversationId.value)
          await loadConversations(true)
        }
      )
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    ElMessage.error('发送消息失败: ' + error.message)
    // 移除临时消息
    const index = messages.value.indexOf(tempAssistantMessage)
    if (index > -1) {
      messages.value.splice(index, 1)
    }
  } finally {
    loading.value = false
  }
}

// 删除会话
const deleteConversation = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该对话？此操作不可恢复。', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await chatAPI.deleteConversation(id)
    conversations.value = conversations.value.filter(c => c.id !== id)
    if (currentConversationId.value === id) {
      currentConversationId.value = null
      messages.value = []
    }
    ElMessage.success('已删除对话')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除对话失败')
    }
  }
}

// 格式化消息内容，使用Markdown渲染
const formatContent = (content) => {
  if (!content) return ''
  try {
    // 使用Markdown渲染，自动处理换行、代码块等
    return md.render(content)
  } catch (error) {
    console.error('Markdown render error:', error)
    // 如果Markdown渲染失败，降级为纯文本处理
    return content.replace(/\n/g, '<br>')
  }
}

const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return date.toLocaleDateString()
}
</script>

<style scoped>
.chat-view {
  display: flex;
  height: 100vh;
}

.conversations-panel {
  width: 260px;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.conversations-list {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.2s;
  position: relative;
}

.conversation-item:hover {
  background-color: #f5f5f5;
}

.conversation-item.active {
  background-color: #e6f7ff;
}

.conversation-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-time {
  font-size: 12px;
  color: #999;
}

.conversation-delete {
  position: absolute;
  right: 12px;
  top: 12px;
  color: #bbb;
  display: none;
}

.conversation-item:hover .conversation-delete {
  display: inline-block;
  color: #999;
}

.conversation-delete:hover {
  color: #f56c6c;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
}

.message-item.USER {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
  margin: 0 12px;
}

.message-content-wrapper {
  max-width: 60%;
  display: flex;
  align-items: flex-end;
  gap: 4px;
}

.message-content {
  padding: 12px 16px;
  border-radius: 8px;
  word-wrap: break-word;
  line-height: 1.6;
}

/* 流式纯文本渲染，保留换行并实时追加 */
.streaming-text {
  white-space: pre-wrap;
}

.message-item.USER .message-content-wrapper {
  flex-direction: row-reverse;
}

.message-item.USER .message-content {
  background-color: #1890ff;
  color: #fff;
}

.message-item.ASSISTANT .message-content {
  background-color: #f0f0f0;
  color: #333;
}

.input-container {
  padding: 20px;
  border-top: 1px solid #e8e8e8;
}

.input-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.input-container :deep(.el-textarea) {
  flex: 1;
}

/* 文件上传预览样式 */
.uploaded-file-preview {
  margin-bottom: 12px;
}

.image-preview-container {
  margin-bottom: 12px;
  text-align: center;
}

.image-preview {
  max-width: 100%;
  max-height: 200px;
  border-radius: 4px;
  object-fit: contain;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-icon {
  font-size: 24px;
  color: #409eff;
  flex-shrink: 0;
}

.file-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.file-name {
  font-weight: 500;
  color: #333;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  color: #999;
  font-size: 12px;
}

.file-type-badge {
  display: inline-block;
  padding: 2px 8px;
  background: #e8f4fd;
  color: #409eff;
  border-radius: 4px;
  font-size: 11px;
  width: fit-content;
}

.remove-btn {
  margin-left: auto;
  flex-shrink: 0;
}

.streaming-cursor {
  display: inline-block;
  animation: blink 1s infinite;
  margin-left: 2px;
}

@keyframes blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}

/* Markdown样式 */
.message-content :deep(pre) {
  background-color: #f6f8fa;
  border-radius: 6px;
  padding: 16px;
  overflow-x: auto;
  margin: 8px 0;
}

.message-content :deep(code) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
}

.message-content :deep(p) {
  margin: 8px 0;
}

.message-content :deep(p:first-child) {
  margin-top: 0;
}

.message-content :deep(p:last-child) {
  margin-bottom: 0;
}

.message-content :deep(ul),
.message-content :deep(ol) {
  margin: 8px 0;
  padding-left: 24px;
}

.message-content :deep(li) {
  margin: 4px 0;
}

.message-content :deep(blockquote) {
  border-left: 4px solid #dfe2e5;
  padding-left: 16px;
  margin: 8px 0;
  color: #6a737d;
}

.message-content :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}

.message-content :deep(th),
.message-content :deep(td) {
  border: 1px solid #dfe2e5;
  padding: 6px 13px;
}

.message-content :deep(th) {
  background-color: #f6f8fa;
  font-weight: 600;
}

.message-content :deep(a) {
  color: #0366d6;
  text-decoration: none;
}

.message-content :deep(a:hover) {
  text-decoration: underline;
}

/* USER消息的代码块样式调整 */
.message-item.USER .message-content :deep(pre) {
  background-color: rgba(255, 255, 255, 0.2);
}

.message-item.USER .message-content :deep(code) {
  color: #fff;
}

/* RAG文档样式 */
/* Message Animations */
.message-fade-enter-active,
.message-fade-leave-active {
  transition: all 0.3s ease;
}

.message-fade-enter-from,
.message-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.user-avatar {
  background-color: #409eff;
  font-weight: 600;
}

.ai-avatar {
  background-color: #67c23a;
  font-weight: 600;
}

.message-content {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: box-shadow 0.3s;
}

.message-content:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.rag-docs-section {
  margin-bottom: 12px;
  width: 100%;
}

.rag-docs-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #409eff;
}

.rag-docs-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.rag-doc-card {
  margin-bottom: 0;
  border-radius: 8px;
}

.rag-doc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 500;
  font-size: 14px;
}

.rag-doc-content {
  font-size: 13px;
  color: #666;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

