<template>
  <div class="login-container">
    <MeteorBackground />
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <h2>王益民的智能小屋</h2>
          <p>欢迎回来</p>
        </div>
      </template>
      
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="rules"
        label-position="top"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            clearable
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <!-- 验证码：放在密码下面，登录按钮上面 -->
        <el-form-item label="验证码" prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="loginForm.captchaCode"
              placeholder="请输入验证码"
              size="large"
              clearable
            >
              <template #prefix>
                <el-icon><Picture /></el-icon>
              </template>
            </el-input>
            <img
              v-if="captcha.imageBase64"
              :src="captcha.imageBase64"
              class="captcha-img"
              :title="'点击刷新'"
              @click="loadCaptcha"
              alt="验证码"
            />
            <el-button link type="primary" @click="loadCaptcha" class="captcha-refresh">刷新</el-button>
          </div>
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
      
      <div class="footer">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </el-card>
  </div>
  <div class="page-bottom-link">
    <a href="https://github.com/669ElijahWang/" target="_blank" rel="noopener noreferrer">恬不可吃的代码仓库</a>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authAPI } from '@/api/auth'
import { ElMessage } from 'element-plus'
import MeteorBackground from '@/components/MeteorBackground.vue'

const router = useRouter()
const authStore = useAuthStore()

const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = ref({
  username: '',
  password: '',
  captchaCode: ''
})

const captcha = ref({
  captchaId: '',
  imageBase64: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ],
  captchaCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
}

onMounted(() => {
  loadCaptcha()
})

const loadCaptcha = async () => {
  try {
    const res = await authAPI.getCaptcha()
    captcha.value.captchaId = res.data.captchaId
    captcha.value.imageBase64 = res.data.imageBase64
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取验证码失败')
  }
}

const handleLogin = async () => {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return
  
  loading.value = true
  const result = await authStore.login({
    username: loginForm.value.username,
    password: loginForm.value.password,
    captchaId: captcha.value.captchaId,
    captchaCode: loginForm.value.captchaCode
  })
  loading.value = false
  
  if (result.success) {
    ElMessage.success('登录成功')
    await nextTick()
    router.push({ name: 'chat' })
  } else {
    // 刷新验证码，避免旧图影响再次输入
    loginForm.value.captchaCode = ''
    loadCaptcha()
  }
}
</script>

<style scoped>
.login-container {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%); /* Darker space-like gradient */
  overflow: hidden;
}

.login-card {
  width: 400px;
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.9); /* Glassmorphism base */
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.37);
  animation: card-entry 0.8s cubic-bezier(0.2, 0.8, 0.2, 1);
}

@keyframes card-entry {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0 0 10px 0;
  color: #303133;
}

.card-header p {
  margin: 0;
  color: #606266;
}

.footer {
  text-align: center;
  margin-top: 20px;
  color: #606266;
}

.footer a {
  color: #409eff;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.3s;
}

.footer a:hover {
  text-decoration: underline;
  color: #66b1ff;
}

.page-bottom-link {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 10;
}

.page-bottom-link a {
  color: rgba(255, 255, 255, 0.8); /* Lighter for dark background */
  text-decoration: none;
  font-size: 16px;
  font-weight: 500;
  display: inline-block;
  transition: all 0.3s ease;
  text-shadow: 0 2px 4px rgba(0,0,0,0.3);
}

.page-bottom-link a:hover {
  color: #fff;
  transform: translateX(-50%) scale(1.05);
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.captcha-img {
  height: 40px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.captcha-img:hover {
  opacity: 0.8;
}

.captcha-refresh {
  margin-left: auto;
}
</style>

