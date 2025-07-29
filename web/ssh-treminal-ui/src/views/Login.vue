<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h2>SSH管理工具</h2>
        <p>请登录以继续使用</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            size="large"
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="isLogging"
            @click="handleLogin"
          >
            {{ isLogging ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <p>默认管理员账号：admin / admin123</p>
        <p>默认用户账号：user / user123</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const loginFormRef = ref()
const isLogging = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  try {
    // 表单验证
    await loginFormRef.value.validate()
    
    isLogging.value = true
    
    // TODO: 实现真实的登录API调用
    // const response = await http.post('/api/auth/login', loginForm)
    
    // 模拟登录验证
    await new Promise(resolve => setTimeout(resolve, 1000))
    
    if (loginForm.username === 'admin' && loginForm.password === 'admin123') {
      // 管理员登录
      localStorage.setItem('user', JSON.stringify({
        username: 'admin',
        role: 'ADMIN',
        token: 'admin-token-123'
      }))
      ElMessage.success('管理员登录成功')
      router.push('/admin')
    } else if (loginForm.username === 'user' && loginForm.password === 'user123') {
      // 普通用户登录
      localStorage.setItem('user', JSON.stringify({
        username: 'user',
        role: 'USER',
        token: 'user-token-123'
      }))
      ElMessage.success('用户登录成功')
      router.push('/user')
    } else {
      ElMessage.error('用户名或密码错误')
    }
    
  } catch (error) {
    if (error.message) {
      ElMessage.error('登录失败：' + error.message)
    }
  } finally {
    isLogging.value = false
  }
}

onMounted(() => {
  // 检查是否已经登录
  const user = localStorage.getItem('user')
  if (user) {
    const userData = JSON.parse(user)
    if (userData.role === 'ADMIN') {
      router.push('/admin')
    } else {
      router.push('/user')
    }
  }
})
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.1);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h2 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 24px;
}

.login-header p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.login-form {
  margin-bottom: 20px;
}

.login-btn {
  width: 100%;
}

.login-footer {
  text-align: center;
  font-size: 13px;
  color: #999;
}

.login-footer p {
  margin: 5px 0;
}
</style>