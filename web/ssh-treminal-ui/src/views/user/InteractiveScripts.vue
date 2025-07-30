<template>
  <div class="interactive-scripts-page">
    <div class="page-header">
      <h2>交互式脚本执行</h2>
      <p class="page-description">选择脚本分组和聚合脚本进行交互式执行</p>
    </div>

    <div class="page-content">
      <!-- 脚本分组列表 -->
      <div class="script-groups">
        <h3>脚本分组</h3>
        <div class="groups-grid">
          <div
              v-for="group in scriptGroups"
              :key="group.id"
              :class="['group-card', { active: selectedGroupId === group.id }]"
              @click="selectGroup(group.id)"
          >
            <div class="group-icon">
              <i :class="`fa fa-${group.icon || 'folder'}`"></i>
            </div>
            <div class="group-info">
              <h4>{{ group.name }}</h4>
              <p>{{ group.description }}</p>
              <span class="group-type">{{ getGroupTypeLabel(group.type) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 聚合脚本列表 -->
      <div v-if="selectedGroupId" class="aggregate-scripts">
        <h3>{{ selectedGroupName }} - 聚合脚本</h3>
        <div class="scripts-list">
          <div
              v-for="script in aggregateScripts"
              :key="script.id"
              class="script-item"
              @click="selectScript(script)"
          >
            <div class="script-info">
              <h4>{{ script.name }}</h4>
              <p>{{ script.description }}</p>
              <div class="script-meta">
                <span class="script-type">{{ getScriptTypeLabel(script.type) }}</span>
                <span class="script-status">{{ script.status }}</span>
              </div>
            </div>
            <div class="script-actions">
              <button class="btn btn-primary">执行脚本</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 选择提示 -->
      <div v-if="!selectedGroupId" class="selection-hint">
        <i class="fa fa-arrow-up"></i>
        <p>请先选择一个脚本分组</p>
      </div>
    </div>

    <!-- 交互式执行面板 -->
    <div v-if="selectedScript" class="execution-panel">
      <InteractiveExecutionPanel
          :script-id="selectedScript.id"
          :script-name="selectedScript.name"
          @close="closeExecutionPanel"
      />
    </div>
  </div>
</template>

<script>
import { ref, reactive, onMounted, computed } from 'vue'
import InteractiveExecutionPanel from '../../components/InteractiveExecutionPanel.vue'

export default {
  name: 'InteractiveScriptsPage',
  components: {
    InteractiveExecutionPanel
  },
  setup() {
    const scriptGroups = ref([])
    const aggregateScripts = ref([])
    const selectedGroupId = ref(null)
    const selectedScript = ref(null)
    const loading = ref(false)

    // 计算选中分组名称
    const selectedGroupName = computed(() => {
      const group = scriptGroups.value.find(g => g.id === selectedGroupId.value)
      return group ? group.name : ''
    })

    // 获取脚本分组
    const fetchScriptGroups = async () => {
      try {
        const response = await fetch('/api/user/script-groups')
        const data = await response.json()
        if (data.success) {
          scriptGroups.value = data.data || []
        }
      } catch (error) {
        console.error('获取脚本分组失败:', error)
      }
    }

    // 获取分组下的聚合脚本
    const fetchAggregateScripts = async (groupId) => {
      try {
        loading.value = true
        const response = await fetch(`/api/user/script-groups/${groupId}/aggregate-scripts`)
        const data = await response.json()
        if (data.success) {
          aggregateScripts.value = data.data || []
        }
      } catch (error) {
        console.error('获取聚合脚本失败:', error)
        aggregateScripts.value = []
      } finally {
        loading.value = false
      }
    }

    // 选择分组
    const selectGroup = (groupId) => {
      if (selectedGroupId.value === groupId) {
        return
      }

      selectedGroupId.value = groupId
      selectedScript.value = null
      fetchAggregateScripts(groupId)
    }

    // 选择脚本
    const selectScript = (script) => {
      selectedScript.value = script
    }

    // 关闭执行面板
    const closeExecutionPanel = () => {
      selectedScript.value = null
    }

    // 获取分组类型标签
    const getGroupTypeLabel = (type) => {
      const typeMap = {
        'PROJECT_DIMENSION': '项目维度',
        'FUNCTION_DIMENSION': '功能维度'
      }
      return typeMap[type] || type
    }

    // 获取脚本类型标签
    const getScriptTypeLabel = (type) => {
      const typeMap = {
        'GENERIC_TEMPLATE': '通用模板',
        'PROJECT_SPECIFIC': '项目特定'
      }
      return typeMap[type] || type
    }

    onMounted(() => {
      fetchScriptGroups()
    })

    return {
      scriptGroups,
      aggregateScripts,
      selectedGroupId,
      selectedScript,
      selectedGroupName,
      loading,
      selectGroup,
      selectScript,
      closeExecutionPanel,
      getGroupTypeLabel,
      getScriptTypeLabel
    }
  }
}
</script>

<style scoped>
.interactive-scripts-page {
  padding: 20px;
  min-height: 100vh;
  background: #f5f5f5;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 600;
}

.page-description {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.page-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 24px;
}

.script-groups h3,
.aggregate-scripts h3 {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
}

.groups-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.group-card {
  background: white;
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
  border: 2px solid transparent;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.group-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
}

.group-card.active {
  border-color: #1890ff;
  box-shadow: 0 4px 16px rgba(24, 144, 255, 0.2);
}

.group-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  background: #f0f9ff;
  border-radius: 8px;
  margin-bottom: 16px;
}

.group-icon i {
  font-size: 24px;
  color: #1890ff;
}

.group-info h4 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
}

.group-info p {
  margin: 0 0 12px 0;
  color: #666;
  font-size: 14px;
  line-height: 1.4;
}

.group-type {
  background: #f0f0f0;
  color: #666;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
}

.scripts-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.script-item {
  background: white;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.script-item:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  transform: translateY(-1px);
}

.script-info {
  flex: 1;
}

.script-info h4 {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 600;
}

.script-info p {
  margin: 0 0 8px 0;
  color: #666;
  font-size: 14px;
}

.script-meta {
  display: flex;
  gap: 8px;
}

.script-type,
.script-status {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
}

.script-type {
  background: #e6f7ff;
  color: #1890ff;
}

.script-status {
  background: #f6ffed;
  color: #52c41a;
}

.script-actions .btn {
  padding: 6px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.btn-primary {
  background: #1890ff;
  color: white;
}

.selection-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  padding: 40px 0;
}

.selection-hint i {
  font-size: 24px;
  margin-bottom: 8px;
}

.execution-panel {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.execution-panel > div {
  width: 90%;
  max-width: 1000px;
  height: 80%;
  max-height: 800px;
  background: white;
  border-radius: 8px;
  overflow: hidden;
}

@media (max-width: 768px) {
  .page-content {
    grid-template-columns: 1fr;
  }

  .groups-grid {
    grid-template-columns: 1fr;
  }
}
</style>
