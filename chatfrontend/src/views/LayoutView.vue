<template>
  <el-container class="layout-container">
    <el-aside width="200px" class="sidebar">
      <div class="logo">
        <h2>AI Chat</h2>
      </div>
      
      <el-menu
        :default-active="currentRoute"
        router
        class="sidebar-menu"
        @select="onMenuSelect"
      >
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>对话</span>
        </el-menu-item>
        
        <el-menu-item index="/knowledge">
          <el-icon><Collection /></el-icon>
          <span>知识库</span>
        </el-menu-item>

        <el-menu-item index="/nocode">
          <el-icon><Edit /></el-icon>
          <span>nocode</span>
        </el-menu-item>
        
        <el-menu-item index="/agents">
          <el-icon><User /></el-icon>
          <span>智能体</span>
        </el-menu-item>
        
        <el-menu-item index="/polyp">
          <el-icon><Picture /></el-icon>
          <span>息肉分割</span>
        </el-menu-item>
        
        <el-menu-item index="/profile">
          <el-icon><Setting /></el-icon>
          <span>个人中心</span>
        </el-menu-item>
      </el-menu>
      
      <div class="sidebar-footer">
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-avatar :size="32">{{ authStore.user?.nickname?.[0] || 'U' }}</el-avatar>
            <span class="username">{{ authStore.user?.nickname || '用户' }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人信息</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-aside>
    
    <el-main class="main-content">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Collection, User, Setting, Edit, Picture } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const currentRoute = computed(() => route.path)

// 兼容性处理：确保菜单选择时一定进行路由跳转
const onMenuSelect = (index) => {
  // index 是菜单项的 index，此处配置为绝对路径，如 '/profile'
  if (typeof index === 'string' && index) {
    router.push(index)
  }
}

const handleCommand = async (command) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      
      authStore.logout()
      ElMessage.success('已退出登录')
      router.push({ name: 'login' })
    } catch {
      // 取消操作
    }
  } else if (command === 'profile') {
    router.push({ name: 'profile' })
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: #001529;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo h2 {
  color: #fff;
  margin: 0;
  font-size: 20px;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  background-color: transparent;
}

:deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.65);
}

:deep(.el-menu-item:hover),
:deep(.el-menu-item.is-active) {
  color: #fff;
  background-color: #1890ff !important;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #fff;
}

.username {
  font-size: 14px;
}

.main-content {
  padding: 0;
  background-color: #f0f2f5;
}
</style>

