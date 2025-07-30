<template>
  <div class="admin-layout">
    <header class="admin-header">
      <div class="header-left">
        <h1>SSH管理工具 - 管理后台</h1>
      </div>
      <div class="header-right">
        <span>管理员</span>
        <el-button type="text" @click="logout">退出</el-button>
      </div>
    </header>
    
    <div class="admin-content">
      <aside class="admin-sidebar">
        <el-menu
          default-active="script-groups"
          class="sidebar-menu"
          router
          :default-openeds="['scripts']"
        >
          <el-menu-item index="/admin/dashboard/script-groups">
            <el-icon><Grid /></el-icon>
            <span>脚本分组管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/dashboard/aggregated-scripts">
            <el-icon><Document /></el-icon>
            <span>聚合脚本管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/dashboard/scripts">
            <el-icon><Edit /></el-icon>
            <span>脚本配置</span>
          </el-menu-item>
          <el-menu-item index="/admin/dashboard/script-builder">
            <el-icon><Edit /></el-icon>
            <span>脚本构建器</span>
          </el-menu-item>
          <el-menu-item index="/admin/dashboard/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
        </el-menu>
      </aside>
      
      <main class="admin-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { Grid, Document, Edit, User } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'

const router = useRouter()

const logout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    // 清除本地存储的用户信息
    localStorage.removeItem('user')
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch (error) {
    // 用户取消退出
  }
}
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.admin-header {
  height: 60px;
  background: #409eff;
  color: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
}

.admin-content {
  flex: 1;
  display: flex;
}

.admin-sidebar {
  width: 250px;
  background: #f5f5f5;
  border-right: 1px solid #e0e0e0;
}

.sidebar-menu {
  border: none;
  background: transparent;
}

.admin-main {
  flex: 1;
  padding: 20px;
  background: #fafafa;
  overflow-y: auto;
}
</style>