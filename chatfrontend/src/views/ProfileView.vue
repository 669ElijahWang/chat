<template>
  <div class="profile-view">
    <el-row :gutter="20">
      <!-- 个人信息 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <h3>个人信息</h3>
              <el-button type="primary" @click="showEditDialog = true">编辑</el-button>
            </div>
          </template>
          
          <div v-if="loading" style="text-align: center; padding: 40px;">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>加载中...</p>
          </div>
          
          <el-descriptions v-else-if="userInfo" :column="1" border>
            <el-descriptions-item label="用户名">
              {{ userInfo.username }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              {{ userInfo.email }}
            </el-descriptions-item>
            <el-descriptions-item label="昵称">
              {{ userInfo.nickname || '未设置' }}
            </el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag :type="getRoleType(userInfo.role)">
                {{ getRoleText(userInfo.role) }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      
      <!-- 修改密码 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <h3>修改密码</h3>
          </template>
          
          <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" show-password />
            </el-form-item>
            
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" show-password />
            </el-form-item>
            
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="handleUpdatePassword" :loading="passwordLoading">
                修改密码
              </el-button>
              <el-button @click="resetPasswordForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 编辑个人信息对话框 -->
    <el-dialog v-model="showEditDialog" title="编辑个人信息" width="500px">
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="editForm.username" placeholder="请输入用户名" />
        </el-form-item>
        
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="editForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateProfile" :loading="editLoading">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { authAPI } from '@/api/auth'
import { userAPI } from '@/api/user'
import { ElMessage } from 'element-plus'

const authStore = useAuthStore()
const userInfo = ref(null)
const loading = ref(false)
const showEditDialog = ref(false)
const editLoading = ref(false)
const passwordLoading = ref(false)

const editFormRef = ref(null)
const passwordFormRef = ref(null)

const editForm = reactive({
  username: '',
  email: '',
  nickname: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const editRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3-20个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在6-20个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

onMounted(async () => {
  await loadUserInfo()
})

const loadUserInfo = async () => {
  loading.value = true
  try {
    const res = await authAPI.getCurrentUser()
    userInfo.value = res.data
    // 更新编辑表单
    editForm.username = res.data.username
    editForm.email = res.data.email
    editForm.nickname = res.data.nickname || ''
    // 更新store
    authStore.user = res.data
  } catch (error) {
    ElMessage.error('加载用户信息失败')
  } finally {
    loading.value = false
  }
}

const handleUpdateProfile = async () => {
  if (!editFormRef.value) return
  
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    editLoading.value = true
    try {
      const res = await userAPI.updateProfile(editForm)
      userInfo.value = res.data
      authStore.user = res.data
      ElMessage.success('更新成功')
      showEditDialog.value = false
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '更新失败')
    } finally {
      editLoading.value = false
    }
  })
}

const handleUpdatePassword = async () => {
  if (!passwordFormRef.value) return
  
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    passwordLoading.value = true
    try {
      await userAPI.updatePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      ElMessage.success('密码修改成功，请重新登录')
      resetPasswordForm()
      // 延迟后退出登录
      setTimeout(() => {
        authStore.logout()
      }, 1500)
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '修改失败')
    } finally {
      passwordLoading.value = false
    }
  })
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.resetFields()
}

const getRoleType = (role) => {
  const types = {
    'SUPER_ADMIN': 'danger',
    'ADMIN': 'warning',
    'USER': 'success'
  }
  return types[role] || 'info'
}

const getRoleText = (role) => {
  const texts = {
    'SUPER_ADMIN': '超级管理员',
    'ADMIN': '管理员',
    'USER': '普通用户'
  }
  return texts[role] || role
}
</script>

<style scoped>
.profile-view {
  padding: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  margin: 0;
}

.card-header h3 {
  margin: 0;
}

.profile-content {
  padding: 20px 0;
}
</style>

