<template>
  <div class="admin-scripts">
    <div class="page-header">
      <h2>脚本管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        新建脚本
      </el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="filterGroupId" placeholder="选择分组" clearable @change="loadScripts">
        <el-option label="全部分组" value="" />
        <el-option
          v-for="group in scriptGroups"
          :key="group.id"
          :label="group.name"
          :value="group.id"
        />
      </el-select>
    </div>

    <el-table :data="scripts" v-loading="loading" style="width: 100%">
      <el-table-column prop="name" label="脚本名称" width="200" />
      <el-table-column prop="description" label="描述" />
      <el-table-column label="所属分组" width="150">
        <template #default="scope">
          {{ getGroupName(scope.row.groupId) }}
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
          <el-button size="small" @click="editScript(scope.row)">编辑</el-button>
          <el-button size="small" @click="configScript(scope.row)">配置</el-button>
          <el-button size="small" type="success" @click="testScript(scope.row)">测试</el-button>
          <el-button size="small" type="danger" @click="deleteScript(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑脚本对话框 -->
    <el-dialog
      v-model="showCreateDialog"
      :title="editingScript ? '编辑脚本' : '新建脚本'"
      width="600px"
    >
      <el-form ref="scriptForm" :model="scriptForm" :rules="scriptRules" label-width="100px">
        <el-form-item label="脚本名称" prop="name">
          <el-input v-model="scriptForm.name" placeholder="请输入脚本名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="scriptForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入脚本描述"
          />
        </el-form-item>
        <el-form-item label="所属分组" prop="groupId">
          <el-select v-model="scriptForm.groupId" placeholder="选择分组">
            <el-option
              v-for="group in scriptGroups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="scriptForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="scriptForm.status">
            <el-option label="激活" value="ACTIVE" />
            <el-option label="禁用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="saveScript">保存</el-button>
      </template>
    </el-dialog>

    <!-- 脚本配置对话框 -->
    <el-dialog
      v-model="showConfigDialog"
      title="脚本配置"
      width="80%"
      :close-on-click-modal="false"
    >
      <div class="script-config">
        <div class="config-tips">
          <el-alert
            title="配置说明"
            type="info"
            :closable="false"
            show-icon
          >
            <p>请使用JSON格式配置脚本执行步骤，支持以下步骤类型：</p>
            <ul>
              <li><code>system_check</code>：系统检测，检查操作系统、Java版本等</li>
              <li><code>command</code>：执行命令，运行Shell命令</li>
              <li><code>file_operation</code>：文件操作，创建、复制、删除文件</li>
              <li><code>condition</code>：条件判断，根据条件执行不同分支</li>
            </ul>
          </el-alert>
        </div>
        
        <div class="config-editor">
          <el-input
            v-model="scriptConfig"
            type="textarea"
            :rows="20"
            placeholder="请输入脚本配置JSON"
          />
        </div>

        <div class="config-example">
          <h4>配置示例：</h4>
          <pre>{{ exampleConfig }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="showConfigDialog = false">取消</el-button>
        <el-button @click="validateConfig">验证配置</el-button>
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
const scripts = ref([])
const scriptGroups = ref([])
const filterGroupId = ref('')
const showCreateDialog = ref(false)
const showConfigDialog = ref(false)
const editingScript = ref(null)
const scriptConfig = ref('')

const scriptForm = reactive({
  name: '',
  description: '',
  groupId: null,
  sortOrder: 0,
  status: 'ACTIVE'
})

const scriptRules = {
  name: [{ required: true, message: '请输入脚本名称', trigger: 'blur' }],
  groupId: [{ required: true, message: '请选择分组', trigger: 'change' }]
}

const exampleConfig = `{
  "steps": [
    {
      "type": "system_check",
      "name": "系统检测",
      "description": "检测操作系统信息"
    },
    {
      "type": "command",
      "name": "更新软件包",
      "command": "apt update && apt upgrade -y"
    },
    {
      "type": "file_operation",
      "name": "创建目录",
      "operation": "mkdir",
      "filePath": "/opt/myapp"
    },
    {
      "type": "condition",
      "name": "检查端口",
      "condition": "port_available:8080"
    }
  ]
}`

const loadScripts = async () => {
  loading.value = true
  try {
    let url = '/api/admin/scripts'
    if (filterGroupId.value) {
      url = `/api/admin/scripts/group/${filterGroupId.value}`
    }
    const response = await http.get(url)
    scripts.value = response.data
  } catch (error) {
    ElMessage.error('加载脚本列表失败')
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

const getGroupName = (groupId) => {
  const group = scriptGroups.value.find(g => g.id === groupId)
  return group ? group.name : '未分组'
}

const editScript = (script) => {
  editingScript.value = script
  Object.assign(scriptForm, script)
  showCreateDialog.value = true
}

const saveScript = async () => {
  try {
    if (editingScript.value) {
      await http.put(`/api/admin/scripts/${editingScript.value.id}`, scriptForm)
      ElMessage.success('脚本更新成功')
    } else {
      await http.post('/api/admin/scripts', scriptForm)
      ElMessage.success('脚本创建成功')
    }
    showCreateDialog.value = false
    resetForm()
    loadScripts()
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const configScript = (script) => {
  editingScript.value = script
  scriptConfig.value = script.config || exampleConfig
  showConfigDialog.value = true
}

const validateConfig = () => {
  try {
    JSON.parse(scriptConfig.value)
    ElMessage.success('配置格式正确')
  } catch (error) {
    ElMessage.error('配置格式错误：' + error.message)
  }
}

const saveScriptConfig = async () => {
  try {
    // 验证JSON格式
    JSON.parse(scriptConfig.value)
    
    const updatedScript = { ...editingScript.value, config: scriptConfig.value }
    await http.put(`/api/admin/scripts/${editingScript.value.id}`, updatedScript)
    ElMessage.success('脚本配置保存成功')
    showConfigDialog.value = false
    loadScripts()
  } catch (error) {
    if (error instanceof SyntaxError) {
      ElMessage.error('配置格式错误：' + error.message)
    } else {
      ElMessage.error('保存失败')
    }
  }
}

const testScript = async (script) => {
  try {
    ElMessage.info('开始测试脚本执行...')
    const response = await http.post(`/api/user/script-execution/execute/${script.id}`)
    ElMessage.success('脚本测试已启动，请查看执行日志')
  } catch (error) {
    ElMessage.error('测试失败')
  }
}

const deleteScript = async (script) => {
  try {
    await ElMessageBox.confirm('确定要删除这个脚本吗？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await http.delete(`/api/admin/scripts/${script.id}`)
    ElMessage.success('删除成功')
    loadScripts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const resetForm = () => {
  Object.assign(scriptForm, {
    name: '',
    description: '',
    groupId: null,
    sortOrder: 0,
    status: 'ACTIVE'
  })
  editingScript.value = null
}

onMounted(() => {
  loadScriptGroups()
  loadScripts()
})
</script>

<style scoped>
.admin-scripts {
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

.config-tips .el-alert {
  margin-bottom: 0;
}

.config-tips ul {
  margin: 10px 0 0 0;
  padding-left: 20px;
}

.config-tips li {
  margin-bottom: 5px;
}

.config-tips code {
  background: #f1f2f3;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: monospace;
}

.config-editor {
  flex: 1;
}

.config-example {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 4px;
  padding: 15px;
}

.config-example h4 {
  margin: 0 0 10px 0;
  color: #333;
}

.config-example pre {
  margin: 0;
  white-space: pre-wrap;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.4;
  color: #666;
}
</style>