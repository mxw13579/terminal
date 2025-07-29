<template>
  <div class="aggregated-scripts">
    <div class="page-header">
      <h2>聚合脚本管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        新建聚合脚本
      </el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="filterGroupId" placeholder="选择分组" clearable @change="loadAggregatedScripts">
        <el-option label="全部分组" value="" />
        <el-option
          v-for="group in scriptGroups"
          :key="group.id"
          :label="group.name"
          :value="group.id"
        />
      </el-select>
    </div>

    <el-table :data="aggregatedScripts" v-loading="loading" style="width: 100%">
      <el-table-column prop="name" label="聚合脚本名称" width="200" />
      <el-table-column prop="description" label="描述" />
      <el-table-column label="所属分组" width="150">
        <template #default="scope">
          {{ getGroupName(scope.row.groupId) }}
        </template>
      </el-table-column>
      <el-table-column label="包含脚本" width="120">
        <template #default="scope">
          <el-tag size="small">{{ getScriptCount(scope.row.scriptIds) }} 个脚本</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ scope.row.status === 'ACTIVE' ? '激活' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="250">
        <template #default="scope">
          <el-button size="small" @click="editAggregatedScript(scope.row)">编辑</el-button>
          <el-button size="small" @click="configScripts(scope.row)">配置脚本</el-button>
          <el-button size="small" type="success" @click="testAggregatedScript(scope.row)">测试</el-button>
          <el-button size="small" type="danger" @click="deleteAggregatedScript(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑聚合脚本对话框 -->
    <el-dialog
      v-model="showCreateDialog"
      :title="editingAggregatedScript ? '编辑聚合脚本' : '新建聚合脚本'"
      width="600px"
    >
      <el-form ref="aggregatedScriptForm" :model="aggregatedScriptForm" :rules="aggregatedScriptRules" label-width="100px">
        <el-form-item label="脚本名称" prop="name">
          <el-input v-model="aggregatedScriptForm.name" placeholder="请输入聚合脚本名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="aggregatedScriptForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入聚合脚本描述"
          />
        </el-form-item>
        <el-form-item label="所属分组" prop="groupId">
          <el-select v-model="aggregatedScriptForm.groupId" placeholder="选择分组" style="width: 100%">
            <el-option
              v-for="group in scriptGroups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="aggregatedScriptForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="aggregatedScriptForm.status" style="width: 100%">
            <el-option label="激活" value="ACTIVE" />
            <el-option label="禁用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="saveAggregatedScript">保存</el-button>
      </template>
    </el-dialog>

    <!-- 配置脚本对话框 -->
    <el-dialog
      v-model="showConfigDialog"
      title="配置包含的脚本"
      width="800px"
      :close-on-click-modal="false"
    >
      <div class="script-config">
        <div class="available-scripts">
          <h4>可用脚本</h4>
          <div class="script-list">
            <div
              v-for="script in availableScripts"
              :key="script.id"
              class="script-item"
              :class="{ selected: selectedScripts.includes(script.id) }"
              @click="toggleScript(script.id)"
            >
              <div class="script-info">
                <h5>{{ script.name }}</h5>
                <p>{{ script.description }}</p>
              </div>
              <el-checkbox :value="selectedScripts.includes(script.id)" />
            </div>
          </div>
        </div>
        
        <div class="execution-order">
          <h4>执行顺序</h4>
          <el-tag
            v-for="(scriptId, index) in selectedScripts"
            :key="scriptId"
            class="order-tag"
            closable
            @close="removeScript(scriptId)"
          >
            {{ index + 1 }}. {{ getScriptName(scriptId) }}
          </el-tag>
        </div>
      </div>
      <template #footer>
        <el-button @click="showConfigDialog = false">取消</el-button>
        <el-button type="primary" @click="saveScriptConfig">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '@/utils/http'

const loading = ref(false)
const aggregatedScripts = ref([])
const scriptGroups = ref([])
const availableScripts = ref([])
const filterGroupId = ref('')
const showCreateDialog = ref(false)
const showConfigDialog = ref(false)
const editingAggregatedScript = ref(null)
const selectedScripts = ref([])

const aggregatedScriptForm = reactive({
  name: '',
  description: '',
  groupId: null,
  sortOrder: 0,
  status: 'ACTIVE'
})

const aggregatedScriptRules = {
  name: [{ required: true, message: '请输入聚合脚本名称', trigger: 'blur' }],
  groupId: [{ required: true, message: '请选择分组', trigger: 'change' }]
}

const loadAggregatedScripts = async () => {
  loading.value = true
  try {
    let url = '/api/admin/aggregated-scripts'
    if (filterGroupId.value) {
      url = `/api/admin/aggregated-scripts/group/${filterGroupId.value}`
    }
    const response = await http.get(url)
    aggregatedScripts.value = response.data
  } catch (error) {
    ElMessage.error('加载聚合脚本列表失败')
  } finally {
    loading.value = false
  }
}

const loadScriptGroups = async () => {
  try {
    const response = await http.get('/api/admin/script-groups')
    scriptGroups.value = response.data
  } catch (error) {
    ElMessage.error('加载分组列表失败')
  }
}

const loadAvailableScripts = async () => {
  try {
    const response = await http.get('/api/admin/scripts')
    availableScripts.value = response.data
  } catch (error) {
    ElMessage.error('加载脚本列表失败')
  }
}

const getGroupName = (groupId) => {
  const group = scriptGroups.value.find(g => g.id === groupId)
  return group ? group.name : '未分组'
}

const getScriptCount = (scriptIds) => {
  if (!scriptIds) return 0
  try {
    return JSON.parse(scriptIds).length
  } catch {
    return 0
  }
}

const getScriptName = (scriptId) => {
  const script = availableScripts.value.find(s => s.id === scriptId)
  return script ? script.name : '未知脚本'
}

const editAggregatedScript = (aggregatedScript) => {
  editingAggregatedScript.value = aggregatedScript
  Object.assign(aggregatedScriptForm, aggregatedScript)
  showCreateDialog.value = true
}

const saveAggregatedScript = async () => {
  try {
    if (editingAggregatedScript.value) {
      await http.put(`/api/admin/aggregated-scripts/${editingAggregatedScript.value.id}`, aggregatedScriptForm)
      ElMessage.success('聚合脚本更新成功')
    } else {
      await http.post('/api/admin/aggregated-scripts', aggregatedScriptForm)
      ElMessage.success('聚合脚本创建成功')
    }
    showCreateDialog.value = false
    resetForm()
    loadAggregatedScripts()
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const configScripts = async (aggregatedScript) => {
  editingAggregatedScript.value = aggregatedScript
  
  // 加载当前配置的脚本
  try {
    selectedScripts.value = aggregatedScript.scriptIds ? JSON.parse(aggregatedScript.scriptIds) : []
  } catch {
    selectedScripts.value = []
  }
  
  await loadAvailableScripts()
  showConfigDialog.value = true
}

const toggleScript = (scriptId) => {
  const index = selectedScripts.value.indexOf(scriptId)
  if (index > -1) {
    selectedScripts.value.splice(index, 1)
  } else {
    selectedScripts.value.push(scriptId)
  }
}

const removeScript = (scriptId) => {
  const index = selectedScripts.value.indexOf(scriptId)
  if (index > -1) {
    selectedScripts.value.splice(index, 1)
  }
}

const saveScriptConfig = async () => {
  try {
    const updatedAggregatedScript = {
      ...editingAggregatedScript.value,
      scriptIds: JSON.stringify(selectedScripts.value),
      executionOrder: JSON.stringify(selectedScripts.value.map((id, index) => ({ scriptId: id, order: index + 1 })))
    }
    
    await http.put(`/api/admin/aggregated-scripts/${editingAggregatedScript.value.id}`, updatedAggregatedScript)
    ElMessage.success('脚本配置保存成功')
    showConfigDialog.value = false
    loadAggregatedScripts()
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const testAggregatedScript = async (aggregatedScript) => {
  try {
    ElMessage.info('开始测试聚合脚本执行...')
    // TODO: 实现聚合脚本测试执行
    ElMessage.success('聚合脚本测试已启动')
  } catch (error) {
    ElMessage.error('测试失败')
  }
}

const deleteAggregatedScript = async (aggregatedScript) => {
  try {
    await ElMessageBox.confirm('确定要删除这个聚合脚本吗？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await http.delete(`/api/admin/aggregated-scripts/${aggregatedScript.id}`)
    ElMessage.success('删除成功')
    loadAggregatedScripts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const resetForm = () => {
  Object.assign(aggregatedScriptForm, {
    name: '',
    description: '',
    groupId: null,
    sortOrder: 0,
    status: 'ACTIVE'
  })
  editingAggregatedScript.value = null
}

onMounted(() => {
  loadScriptGroups()
  loadAggregatedScripts()
})
</script>

<style scoped>
.aggregated-scripts {
  background: white;
  border-radius: 4px;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  color: #333;
}

.filter-bar {
  margin-bottom: 20px;
}

.script-config {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.available-scripts h4,
.execution-order h4 {
  margin: 0 0 10px 0;
  color: #333;
}

.script-list {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
}

.script-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: all 0.2s;
}

.script-item:hover {
  background: #f5f5f5;
}

.script-item.selected {
  background: #e6f7ff;
  border-color: #1890ff;
}

.script-item:last-child {
  border-bottom: none;
}

.script-info h5 {
  margin: 0 0 4px 0;
  color: #333;
  font-size: 14px;
}

.script-info p {
  margin: 0;
  color: #666;
  font-size: 12px;
}

.order-tag {
  margin: 0 8px 8px 0;
}
</style>