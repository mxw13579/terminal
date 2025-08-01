<template>
  <div class="user-home">
    <div class="home-header">
      <h2>欢迎使用SSH管理工具</h2>
      <p>选择脚本分组执行自动化任务，或直接连接SSH终端</p>
    </div>

    <div class="content-grid">
      <!-- SSH连接卡片 -->
      <div class="card-item ssh-card">
        <div class="card-content">
          <div class="card-icon-wrapper">
            <div class="card-icon">
              <el-icon><Monitor /></el-icon>
            </div>
          </div>
          <div class="card-info">
            <h3>服务器一键连接</h3>
            <p>直接连接到SSH服务器，使用专业终端界面</p>
            <el-button type="primary" @click="goToTerminal" class="action-btn">
              连接SSH终端
            </el-button>
          </div>
        </div>
      </div>

      <!-- 分组展示：按维度分类 -->
      <div v-if="projectGroups.length > 0" class="section-divider">
        <h2 class="section-title">项目管理</h2>
        <div class="group-cards">
          <div
            v-for="group in projectGroups"
            :key="group.id"
            class="card-item script-group-card project-card"
            @click="goToScriptExecution(group.id)"
          >
            <div class="card-content">
              <div class="card-icon-wrapper">
                <div class="card-icon">
                  <el-icon><Box /></el-icon>
                </div>
              </div>
              <div class="card-info">
                <h3>{{ group.name }}</h3>
                <p>{{ group.description || '暂无描述' }}</p>
                <div class="card-footer">
                  <el-tag size="small" type="primary">项目维度</el-tag>
                  <el-tag size="small">{{ getScriptCount(group) }} 个脚本</el-tag>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="functionGroups.length > 0" class="section-divider">
        <h2 class="section-title">功能工具</h2>
        <div class="group-cards">
          <div
            v-for="group in functionGroups"
            :key="group.id"
            class="card-item script-group-card function-card"
            @click="goToScriptExecution(group.id)"
          >
            <div class="card-content">
              <div class="card-icon-wrapper">
                <div class="card-icon">
                  <el-icon><Tools /></el-icon>
                </div>
              </div>
              <div class="card-info">
                <h3>{{ group.name }}</h3>
                <p>{{ group.description || '暂无描述' }}</p>
                <div class="card-footer">
                  <el-tag size="small" type="success">功能维度</el-tag>
                  <el-tag size="small">{{ getScriptCount(group) }} 个脚本</el-tag>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 兼容：显示未分类的分组 -->
      <div v-if="otherGroups.length > 0" class="section-divider">
        <h2 class="section-title">其他分组</h2>
        <div class="group-cards">
          <div
            v-for="group in otherGroups"
            :key="group.id"
            class="card-item script-group-card"
            @click="goToScriptExecution(group.id)"
          >
            <div class="card-content">
              <div class="card-icon-wrapper">
                <div class="card-icon">
                  <el-icon><Document /></el-icon>
                </div>
              </div>
              <div class="card-info">
                <h3>{{ group.name }}</h3>
                <p>{{ group.description || '暂无描述' }}</p>
                <div class="card-footer">
                  <el-tag size="small">{{ getScriptCount(group) }} 个脚本</el-tag>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Monitor, Document, Box, Tools } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { http } from '@/utils/http'

const router = useRouter()
const scriptGroups = ref([])

// 按分组类型分类
const projectGroups = computed(() =>
  scriptGroups.value.filter(group => group.type === 'PROJECT_DIMENSION')
)

const functionGroups = computed(() =>
  scriptGroups.value.filter(group => group.type === 'FUNCTION_DIMENSION')
)

const otherGroups = computed(() =>
  scriptGroups.value.filter(group => !group.type || (group.type !== 'PROJECT_DIMENSION' && group.type !== 'FUNCTION_DIMENSION'))
)

const loadScriptGroups = async () => {
  try {
    const response = await http.get('/api/user/script-groups')
    scriptGroups.value = response.data
  } catch (error) {
    console.error('加载脚本分组失败:', error)
    ElMessage.error('加载脚本分组失败')
  }
}

const getScriptCount = (group) => {
  return group.aggregatedScripts ? group.aggregatedScripts.length : 0
}

const goToTerminal = () => {
  router.push('/terminal')
}

const goToScriptExecution = (groupId) => {
  router.push(`/script-execution/${groupId}`)
}

onMounted(() => {
  loadScriptGroups()
})
</script>

<style scoped>
.user-home {
  padding: 60px 40px;
  max-width: 1200px;
  margin: 0 auto;
  position: relative;
  z-index: 10;
}

.home-header {
  text-align: center;
  margin-bottom: 80px;
}

.home-header h2 {
  color: var(--text-primary);
  margin-bottom: 16px;
  font-family: 'OPPO Sans 4.0', sans-serif;
  font-weight: 300;
  font-size: 30px;
  line-height: 35px;
}

.home-header p {
  color: var(--text-secondary);
  font-size: 16px;
}

.content-grid {
  display: flex;
  flex-direction: column;
  gap: 40px;
}

.section-divider {
  width: 100%;
}

.section-title {
  font-size: 20px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 20px;
  padding-left: 8px;
  border-left: 4px solid var(--el-color-primary);
}

.group-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 30px;
  justify-items: center;
}

.card-item {
  width: 540px;
  height: 180px;
  background: var(--card-bg);
  border-radius: 30px;
  border: 1px solid var(--border-color);
  transition: all 0.3s ease;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.card-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 40px var(--shadow-color);
}

.card-content {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 30px;
  gap: 20px;
}

.card-icon-wrapper {
  flex: none;
  width: 150px;
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-icon {
  width: 120px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 60px;
  color: var(--text-secondary);
  background: radial-gradient(circle, rgba(93, 151, 255, 0.2) 0%, transparent 70%);
  border-radius: 50%;
  mix-blend-mode: screen;
}

.card-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-info h3 {
  margin: 0;
  color: var(--text-primary);
  font-family: 'OPPO Sans 4.0', sans-serif;
  font-weight: 600;
  font-size: 28px;
  line-height: 32px;
}

.card-info p {
  margin: 0;
  color: var(--text-primary);
  font-family: 'OPPO Sans 4.0', sans-serif;
  font-weight: 400;
  font-size: 16px;
  line-height: 20px;
  opacity: 0.8;
}

.action-btn {
  width: fit-content;
  margin-top: 8px;
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 项目维度卡片样式 */
.project-card .card-icon {
  background: radial-gradient(circle, rgba(24, 144, 255, 0.2) 0%, transparent 70%);
}

/* 功能维度卡片样式 */
.function-card .card-icon {
  background: radial-gradient(circle, rgba(82, 196, 26, 0.2) 0%, transparent 70%);
}

/* 暗色主题特殊样式 */
.dark-theme .ssh-card {
  background: rgba(93, 151, 255, 0.1);
}

.dark-theme .project-card {
  background: rgba(24, 144, 255, 0.05);
}

.dark-theme .function-card {
  background: rgba(82, 196, 26, 0.05);
}

.dark-theme .script-group-card {
  background: rgba(93, 151, 255, 0.1);
}

/* 白天主题样式 */
.user-home:not(.dark-theme) .card-item {
  background: white;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.user-home:not(.dark-theme) .home-header h2 {
  color: #333;
}

.user-home:not(.dark-theme) .home-header p {
  color: #666;
}

.user-home:not(.dark-theme) .card-info h3 {
  color: #333;
}

.user-home:not(.dark-theme) .card-info p {
  color: #333;
}

@media (max-width: 1200px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
  
  .card-item {
    width: 100%;
    max-width: 540px;
  }
}

@media (max-width: 600px) {
  .user-home {
    padding: 30px 20px;
  }
  
  .card-content {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }
  
  .card-icon-wrapper {
    width: 100px;
    height: 100px;
  }
  
  .card-icon {
    width: 80px;
    height: 80px;
    font-size: 40px;
  }
}
</style>