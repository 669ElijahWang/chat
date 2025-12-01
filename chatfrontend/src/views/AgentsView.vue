<template>
  <div class="agents-view">
    <div class="header">
      <h2>智能体管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        创建智能体
      </el-button>
    </div>
    
    <div class="agents-list">
      <el-row :gutter="20">
        <el-col v-for="agent in agents" :key="agent.id" :span="8">
          <el-card class="agent-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <span>{{ agent.name }}</span>
                <el-tag :type="agent.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ agent.status === 'ACTIVE' ? '激活' : '未激活' }}
                </el-tag>
              </div>
            </template>
            
            <div class="card-content">
              <p class="description">{{ agent.description || '暂无描述' }}</p>
              <div class="meta">
                <div><span>模型:</span> {{ agent.model }}</div>
                <div><span>温度:</span> {{ agent.temperature }}</div>
                <div><span>创建时间:</span> {{ formatDate(agent.createdAt) }}</div>
              </div>
            </div>
            
            <template #footer>
              <el-button size="small" @click="editAgent(agent)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteAgent(agent.id)">删除</el-button>
            </template>
          </el-card>
        </el-col>
      </el-row>
    </div>
    
    <!-- 创建/编辑智能体对话框 -->
    <el-dialog v-model="showCreateDialog" :title="isEdit ? '编辑智能体' : '创建智能体'" width="600px">
      <el-form :model="agentForm" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="agentForm.name" placeholder="请输入名称" />
        </el-form-item>
        
        <el-form-item label="描述">
          <el-input
            v-model="agentForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
        
        <el-form-item label="系统提示词">
          <el-input
            v-model="agentForm.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="定义智能体的行为和角色"
          />
        </el-form-item>
        
        <el-form-item label="模型">
          <el-input v-model="agentForm.model" placeholder="deepseek-chat" />
        </el-form-item>
        
        <el-form-item label="温度">
          <el-slider v-model="agentForm.temperature" :min="0" :max="1" :step="0.1" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { agentAPI } from '@/api/agent'
import { ElMessage, ElMessageBox } from 'element-plus'

const agents = ref([])
const showCreateDialog = ref(false)
const isEdit = ref(false)

const agentForm = ref({
  name: '',
  description: '',
  systemPrompt: '',
  model: 'deepseek-chat',
  temperature: 0.7
})

onMounted(() => {
  loadAgents()
})

const loadAgents = async () => {
  try {
    const res = await agentAPI.getAgents()
    agents.value = res.data.content
  } catch (error) {
    ElMessage.error('加载智能体失败')
  }
}

const editAgent = (agent) => {
  isEdit.value = true
  agentForm.value = { ...agent }
  showCreateDialog.value = true
}

const handleSubmit = async () => {
  if (!agentForm.value.name) {
    ElMessage.warning('请输入名称')
    return
  }
  
  try {
    if (isEdit.value) {
      await agentAPI.updateAgent(agentForm.value.id, agentForm.value)
      ElMessage.success('更新成功')
    } else {
      await agentAPI.createAgent(agentForm.value)
      ElMessage.success('创建成功')
    }
    
    showCreateDialog.value = false
    resetForm()
    loadAgents()
  } catch (error) {
    ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
  }
}

const deleteAgent = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这个智能体吗？', '提示', {
      type: 'warning'
    })
    
    await agentAPI.deleteAgent(id)
    ElMessage.success('删除成功')
    loadAgents()
  } catch (error) {
    // 取消操作
  }
}

const resetForm = () => {
  isEdit.value = false
  agentForm.value = {
    name: '',
    description: '',
    systemPrompt: '',
    model: 'deepseek-chat',
    temperature: 0.7
  }
}

const formatDate = (date) => {
  return new Date(date).toLocaleDateString()
}
</script>

<style scoped>
.agents-view {
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

.agent-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-content .description {
  margin: 0 0 16px 0;
  color: #666;
  min-height: 40px;
}

.card-content .meta {
  font-size: 13px;
  color: #666;
}

.card-content .meta > div {
  margin-bottom: 6px;
}

.card-content .meta span {
  color: #999;
}
</style>

