<template>
  <div class="knowledge-view">
    <div class="header">
      <h2>知识库管理</h2>
      <div>
        <el-alert 
          v-if="knowledgeBases.length === 0"
          title="提示：先创建知识库，然后添加文档（文本/URL/文件）"
          type="info"
          :closable="false"
          style="margin-right: 20px; display: inline-block;"
        />
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon>
          创建知识库
        </el-button>
      </div>
    </div>
    
    <div class="knowledge-list">
      <el-row :gutter="20">
        <el-col
          v-for="kb in knowledgeBases"
          :key="kb.id"
          :span="6"
        >
          <el-card class="knowledge-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <span>{{ kb.title }}</span>
                <el-dropdown @command="(cmd) => handleCommand(cmd, kb)">
                  <el-icon><More /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="view">查看</el-dropdown-item>
                      <el-dropdown-item command="addDoc">添加文档</el-dropdown-item>
                      <el-dropdown-item command="delete">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
            
            <div class="card-content">
              <p class="description">{{ kb.description || '暂无描述' }}</p>
              <div class="meta">
                <span>来源: {{ kb.sourceType }}</span>
                <span>创建时间: {{ formatDate(kb.createdAt) }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
    
    <!-- 创建知识库对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建知识库" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="createForm.title" placeholder="请输入标题" />
        </el-form-item>
        
        <el-form-item label="描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
        
        <el-form-item label="来源类型">
          <el-select v-model="createForm.sourceType" placeholder="请选择">
            <el-option label="文件" value="FILE" />
            <el-option label="URL" value="URL" />
            <el-option label="文本" value="TEXT" />
          </el-select>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
    
    <!-- 添加文档对话框 -->
    <el-dialog v-model="showAddDocDialog" title="添加文档" width="600px">
      <el-tabs v-model="activeDocTab">
        <!-- 文本上传 -->
        <el-tab-pane label="文本" name="text">
          <el-form :model="textForm" label-width="100px">
            <el-form-item label="文本内容">
              <el-input
                v-model="textForm.text"
                type="textarea"
                :rows="8"
                placeholder="请输入文本内容"
              />
            </el-form-item>
            <el-form-item label="切分策略">
              <el-select v-model="textForm.splitStrategy">
                <el-option label="按段落" value="PARAGRAPH" />
                <el-option label="按句子" value="SENTENCE" />
                <el-option label="重叠Token" value="TOKEN_OVERLAP" />
                <el-option label="按段落+重叠Token" value="PARAGRAPH_TOKEN_OVERLAP" />
              </el-select>
            </el-form-item>
            <el-form-item label="块大小" v-if="['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(textForm.splitStrategy)">
              <el-input-number v-model="textForm.chunkSize" :min="100" :max="2000" />
            </el-form-item>
            <el-form-item label="重叠大小" v-if="['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(textForm.splitStrategy)">
              <el-input-number v-model="textForm.overlapSize" :min="0" :max="500" />
            </el-form-item>
          </el-form>
          <div v-if="showPreview && activeDocTab === 'text'" class="preview-section">
            <el-divider>切分预览（共 {{ previewChunks.length }} 块）</el-divider>
            <div class="preview-chunks">
              <el-card v-for="(chunk, index) in previewChunks" :key="index" class="chunk-card">
                <template #header>
                  <span>块 {{ index + 1 }}</span>
                  <el-tag size="small">{{ chunk.length }} 字符</el-tag>
                </template>
                <div class="chunk-content">{{ chunk }}</div>
              </el-card>
            </div>
          </div>
        </el-tab-pane>
        
        <!-- URL上传 -->
        <el-tab-pane label="URL" name="url">
          <el-form :model="urlForm" label-width="100px">
            <el-form-item label="URL地址">
              <el-input v-model="urlForm.url" placeholder="请输入URL地址" />
            </el-form-item>
            <el-form-item label="切分策略">
              <el-select v-model="urlForm.splitStrategy">
                <el-option label="按段落" value="PARAGRAPH" />
                <el-option label="按句子" value="SENTENCE" />
                <el-option label="重叠Token" value="TOKEN_OVERLAP" />
                <el-option label="按段落+重叠Token" value="PARAGRAPH_TOKEN_OVERLAP" />
              </el-select>
            </el-form-item>
            <el-form-item label="块大小" v-if="['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(urlForm.splitStrategy)">
              <el-input-number v-model="urlForm.chunkSize" :min="100" :max="2000" />
            </el-form-item>
            <el-form-item label="重叠大小" v-if="['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(urlForm.splitStrategy)">
              <el-input-number v-model="urlForm.overlapSize" :min="0" :max="500" />
            </el-form-item>
          </el-form>
          <div v-if="showPreview && activeDocTab === 'url'" class="preview-section">
            <el-divider>切分预览（共 {{ previewChunks.length }} 块）</el-divider>
            <div class="preview-chunks">
              <el-card v-for="(chunk, index) in previewChunks" :key="index" class="chunk-card">
                <template #header>
                  <span>块 {{ index + 1 }}</span>
                  <el-tag size="small">{{ chunk.length }} 字符</el-tag>
                </template>
                <div class="chunk-content">{{ chunk }}</div>
              </el-card>
            </div>
          </div>
        </el-tab-pane>
        
        <!-- 文件上传 -->
        <el-tab-pane label="文件" name="file">
          <el-form :model="fileForm" label-width="100px">
            <el-form-item label="选择文件">
              <el-upload
                ref="uploadRef"
                :auto-upload="false"
                :limit="1"
                :on-change="handleFileChange"
                accept=".txt,.pdf,.doc,.docx,.md,.html,.htm"
              >
                <template #trigger>
                  <el-button type="primary">选择文件</el-button>
                </template>
                <template #tip>
                  <div class="el-upload__tip">
                    支持 txt, pdf, doc, docx, md, html 等格式，文件大小不超过10MB
                  </div>
                </template>
              </el-upload>
            </el-form-item>
            <el-form-item label="切分策略">
              <el-select v-model="fileForm.splitStrategy">
                <el-option label="按段落" value="PARAGRAPH" />
                <el-option label="按句子" value="SENTENCE" />
                <el-option label="重叠Token" value="TOKEN_OVERLAP" />
                <el-option label="按段落+重叠Token" value="PARAGRAPH_TOKEN_OVERLAP" />
              </el-select>
            </el-form-item>
            <el-form-item label="块大小" v-if="['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(fileForm.splitStrategy)">
              <el-input-number v-model="fileForm.chunkSize" :min="100" :max="2000" />
            </el-form-item>
            <el-form-item label="重叠大小" v-if="['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(fileForm.splitStrategy)">
              <el-input-number v-model="fileForm.overlapSize" :min="0" :max="500" />
            </el-form-item>
          </el-form>
          <div v-if="showPreview && activeDocTab === 'file'" class="preview-section">
            <el-divider>切分预览（共 {{ previewChunks.length }} 块）</el-divider>
            <div class="preview-chunks">
              <el-card v-for="(chunk, index) in previewChunks" :key="index" class="chunk-card">
                <template #header>
                  <span>块 {{ index + 1 }}</span>
                  <el-tag size="small">{{ chunk.length }} 字符</el-tag>
                </template>
                <div class="chunk-content">{{ chunk }}</div>
              </el-card>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
      
      <!-- 对话框底部按钮 -->
      <template #footer>
        <el-button @click="showAddDocDialog = false">取消</el-button>
        
        <!-- 文本标签页的按钮 -->
        <template v-if="activeDocTab === 'text'">
          <el-button v-if="!showPreview" type="primary" @click="handlePreviewText" :loading="uploading">预览切分</el-button>
          <el-button v-if="showPreview" type="success" @click="handleConfirmText" :loading="uploading">确认保存到数据库</el-button>
        </template>
        
        <!-- URL标签页的按钮 -->
        <template v-if="activeDocTab === 'url'">
          <el-button v-if="!showPreview" type="primary" @click="handlePreviewUrl" :loading="uploading">预览切分</el-button>
          <el-button v-if="showPreview" type="success" @click="handleConfirmUrl" :loading="uploading">确认保存到数据库</el-button>
        </template>
        
        <!-- 文件标签页的按钮 -->
        <template v-if="activeDocTab === 'file'">
          <el-button v-if="!showPreview" type="primary" @click="handlePreviewFile" :loading="uploading">预览切分</el-button>
          <el-button v-if="showPreview" type="success" @click="handleConfirmFile" :loading="uploading">确认保存到数据库</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 查看文档对话框 -->
    <el-dialog v-model="showViewDocsDialog" title="知识库文档" width="800px">
      <div v-if="selectedKnowledgeBase" class="view-docs-header">
        <h3>{{ selectedKnowledgeBase.title }}</h3>
        <p>{{ selectedKnowledgeBase.description }}</p>
        <el-divider />
      </div>
      
      <el-empty v-if="documents.length === 0" description="暂无文档" />
      
      <div v-else class="documents-list">
        <el-card v-for="(doc, index) in documents" :key="doc.id" class="doc-card" shadow="hover">
          <template #header>
            <div class="doc-header">
              <span>文档 #{{ index + 1 }}</span>
              <div>
                <el-tag v-if="doc.tokenCount" size="small">{{ doc.tokenCount }} tokens</el-tag>
                <el-tag v-if="doc.metadata && doc.metadata.chunkIndex !== undefined" size="small" type="info">
                  块 {{ doc.metadata.chunkIndex + 1 }}/{{ doc.metadata.totalChunks }}
                </el-tag>
              </div>
            </div>
          </template>
          <div class="doc-content">{{ doc.content }}</div>
          <div v-if="doc.metadata && Object.keys(doc.metadata).length > 0" class="doc-meta">
            <el-divider />
            <div class="meta-info">
              <span v-if="doc.metadata.sourceType">来源: {{ doc.metadata.sourceType }}</span>
              <span v-if="doc.metadata.filename">文件: {{ doc.metadata.filename }}</span>
              <span v-if="doc.metadata.url">URL: {{ doc.metadata.url }}</span>
            </div>
          </div>
        </el-card>
      </div>
      
      <template #footer>
        <el-button @click="showViewDocsDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { knowledgeAPI } from '@/api/knowledge'
import { ElMessage, ElMessageBox } from 'element-plus'

const knowledgeBases = ref([])
const showCreateDialog = ref(false)
const showAddDocDialog = ref(false)
const showViewDocsDialog = ref(false)
const activeDocTab = ref('text')
const uploading = ref(false)
const selectedKnowledgeBase = ref(null)
const previewChunks = ref([])
const showPreview = ref(false)
const documents = ref([])

const createForm = ref({
  title: '',
  description: '',
  sourceType: 'TEXT'
})

const textForm = ref({
  text: '',
  splitStrategy: 'PARAGRAPH',
  chunkSize: 500,
  overlapSize: 50
})

const urlForm = ref({
  url: '',
  splitStrategy: 'PARAGRAPH',
  chunkSize: 500,
  overlapSize: 50
})

const fileForm = ref({
  file: null,
  splitStrategy: 'PARAGRAPH',
  chunkSize: 500,
  overlapSize: 50
})

onMounted(() => {
  loadKnowledgeBases()
})

const loadKnowledgeBases = async () => {
  try {
    const res = await knowledgeAPI.getKnowledgeBases()
    knowledgeBases.value = res.data.content
  } catch (error) {
    ElMessage.error('加载知识库失败')
  }
}

const handleCreate = async () => {
  if (!createForm.value.title) {
    ElMessage.warning('请输入标题')
    return
  }
  
  try {
    const res = await knowledgeAPI.createKnowledgeBase(createForm.value)
    ElMessage.success('创建成功！现在可以添加文档了')
    showCreateDialog.value = false
    createForm.value = { title: '', description: '', sourceType: 'TEXT' }
    loadKnowledgeBases()
    
    // 创建成功后自动打开添加文档对话框
    setTimeout(() => {
      selectedKnowledgeBase.value = res.data
      showAddDocDialog.value = true
      activeDocTab.value = 'text'
    }, 500)
  } catch (error) {
    ElMessage.error('创建失败')
  }
}

const handleCommand = async (command, kb) => {
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm('确定要删除这个知识库吗？', '提示', {
        type: 'warning'
      })
      
      await knowledgeAPI.deleteKnowledgeBase(kb.id)
      ElMessage.success('删除成功')
      loadKnowledgeBases()
    } catch (error) {
      // 取消操作
    }
  } else if (command === 'addDoc') {
    selectedKnowledgeBase.value = kb
    showAddDocDialog.value = true
    activeDocTab.value = 'text'
    showPreview.value = false
    previewChunks.value = []
  } else if (command === 'view') {
    selectedKnowledgeBase.value = kb
    await loadDocuments(kb.id)
    showViewDocsDialog.value = true
  }
}

const loadDocuments = async (knowledgeBaseId) => {
  try {
    const res = await knowledgeAPI.getDocuments(knowledgeBaseId)
    documents.value = res.data
    ElMessage.success(`加载了 ${res.data.length} 个文档`)
  } catch (error) {
    ElMessage.error('加载文档失败')
    documents.value = []
  }
}

const handleFileChange = (file) => {
  fileForm.value.file = file.raw
}

// 预览文本切分
const handlePreviewText = async () => {
  if (!textForm.value.text.trim()) {
    ElMessage.warning('请输入文本内容')
    return
  }
  
  uploading.value = true
  try {
    const payload = {
      text: textForm.value.text,
      splitStrategy: textForm.value.splitStrategy
    }
    if (['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(textForm.value.splitStrategy)) {
      payload.chunkSize = textForm.value.chunkSize
      payload.overlapSize = textForm.value.overlapSize
    }
    const res = await knowledgeAPI.previewTextSplit(selectedKnowledgeBase.value.id, payload)
    previewChunks.value = res.data
    showPreview.value = true
    ElMessage.success(`切分成功，共 ${res.data.length} 块`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '预览失败')
  } finally {
    uploading.value = false
  }
}

// 确认保存文本
const handleConfirmText = async () => {
  uploading.value = true
  try {
    const payload = {
      text: textForm.value.text,
      splitStrategy: textForm.value.splitStrategy
    }
    if (['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(textForm.value.splitStrategy)) {
      payload.chunkSize = textForm.value.chunkSize
      payload.overlapSize = textForm.value.overlapSize
    }
    await knowledgeAPI.addDocumentFromText(selectedKnowledgeBase.value.id, payload)
    ElMessage.success('文本已保存到数据库')
    showAddDocDialog.value = false
    showPreview.value = false
    previewChunks.value = []
    textForm.value = { text: '', splitStrategy: 'PARAGRAPH', chunkSize: 500, overlapSize: 50 }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    uploading.value = false
  }
}

// 预览URL切分
const handlePreviewUrl = async () => {
  if (!urlForm.value.url.trim()) {
    ElMessage.warning('请输入URL地址')
    return
  }
  
  uploading.value = true
  try {
    const payload = {
      url: urlForm.value.url,
      splitStrategy: urlForm.value.splitStrategy
    }
    if (['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(urlForm.value.splitStrategy)) {
      payload.chunkSize = urlForm.value.chunkSize
      payload.overlapSize = urlForm.value.overlapSize
    }
    const res = await knowledgeAPI.previewUrlSplit(selectedKnowledgeBase.value.id, payload)
    previewChunks.value = res.data
    showPreview.value = true
    ElMessage.success(`URL内容已抓取，共 ${res.data.length} 块`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '预览失败')
  } finally {
    uploading.value = false
  }
}

// 确认保存URL
const handleConfirmUrl = async () => {
  uploading.value = true
  try {
    const payload = {
      url: urlForm.value.url,
      splitStrategy: urlForm.value.splitStrategy
    }
    if (['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(urlForm.value.splitStrategy)) {
      payload.chunkSize = urlForm.value.chunkSize
      payload.overlapSize = urlForm.value.overlapSize
    }
    await knowledgeAPI.addDocumentFromUrl(selectedKnowledgeBase.value.id, payload)
    ElMessage.success('URL内容已保存到数据库')
    showAddDocDialog.value = false
    showPreview.value = false
    previewChunks.value = []
    urlForm.value = { url: '', splitStrategy: 'PARAGRAPH', chunkSize: 500, overlapSize: 50 }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    uploading.value = false
  }
}

// 预览文件切分
const handlePreviewFile = async () => {
  if (!fileForm.value.file) {
    ElMessage.warning('请选择文件')
    return
  }
  
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', fileForm.value.file)
    formData.append('splitStrategy', fileForm.value.splitStrategy)
    if (['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(fileForm.value.splitStrategy)) {
      if (fileForm.value.chunkSize) {
        formData.append('chunkSize', fileForm.value.chunkSize)
      }
      if (fileForm.value.overlapSize) {
        formData.append('overlapSize', fileForm.value.overlapSize)
      }
    }
    
    const res = await knowledgeAPI.previewFileSplit(selectedKnowledgeBase.value.id, formData)
    previewChunks.value = res.data
    showPreview.value = true
    ElMessage.success(`文件解析成功，共 ${res.data.length} 块`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '预览失败')
  } finally {
    uploading.value = false
  }
}

// 确认保存文件
const handleConfirmFile = async () => {
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', fileForm.value.file)
    formData.append('splitStrategy', fileForm.value.splitStrategy)
    if (['TOKEN_OVERLAP','PARAGRAPH_TOKEN_OVERLAP'].includes(fileForm.value.splitStrategy)) {
      if (fileForm.value.chunkSize) {
        formData.append('chunkSize', fileForm.value.chunkSize)
      }
      if (fileForm.value.overlapSize) {
        formData.append('overlapSize', fileForm.value.overlapSize)
      }
    }
    
    await knowledgeAPI.addDocumentFromFile(selectedKnowledgeBase.value.id, formData)
    ElMessage.success('文件已保存到数据库')
    showAddDocDialog.value = false
    showPreview.value = false
    previewChunks.value = []
    fileForm.value = { file: null, splitStrategy: 'PARAGRAPH', chunkSize: 500, overlapSize: 50 }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    uploading.value = false
  }
}

const formatDate = (date) => {
  return new Date(date).toLocaleDateString()
}
</script>

<style scoped>
.knowledge-view {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header h2 {
  margin: 0;
}

.knowledge-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-content .description {
  margin: 0 0 12px 0;
  color: #666;
  min-height: 40px;
}

.card-content .meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #999;
}

.preview-section {
  margin-top: 20px;
  max-height: 400px;
  overflow-y: auto;
}

.preview-chunks {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chunk-card {
  margin-bottom: 0;
}

.chunk-card :deep(.el-card__header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #f5f7fa;
}

.chunk-content {
  max-height: 150px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 查看文档对话框样式 */
.view-docs-header h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
  color: #333;
}

.view-docs-header p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.documents-list {
  max-height: 500px;
  overflow-y: auto;
}

.doc-card {
  margin-bottom: 16px;
}

.doc-card:last-child {
  margin-bottom: 0;
}

.doc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.doc-header > div {
  display: flex;
  gap: 8px;
}

.doc-content {
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 14px;
  color: #333;
  max-height: 300px;
  overflow-y: auto;
}

.doc-meta {
  margin-top: 8px;
}

.meta-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #999;
}
</style>

