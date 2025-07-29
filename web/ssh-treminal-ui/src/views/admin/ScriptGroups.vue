<template>
  <div class="script-groups">
    <div class="page-header">
      <h2>脚本分组管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        新建分组
      </el-button>
    </div>

    <el-table :data="scriptGroups" v-loading="loading" style="width: 100%">
      <el-table-column prop="name" label="分组名称" width="200" />
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ scope.row.status === 'ACTIVE' ? '激活' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="scope">
          <el-button size="small" @click="editGroup(scope.row)">编辑</el-button>
          <el-button size="small" @click="configInitScript(scope.row)">初始化脚本</el-button>
          <el-button size="small" type="danger" @click="deleteGroup(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑分组对话框 -->
    <el-dialog
      v-model="showCreateDialog"
      :title="editingGroup ? '编辑分组' : '新建分组'"
      width="600px"
    >
      <el-form ref="groupForm" :model="groupForm" :rules="groupRules" label-width="100px">
        <el-form-item label="分组名称" prop="name">
          <el-input v-model="groupForm.name" placeholder="请输入分组名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="groupForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入分组描述"
          />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="groupForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="groupForm.status">
            <el-option label="激活" value="ACTIVE" />
            <el-option label="禁用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>

    <!-- 初始化脚本配置对话框 -->
    <el-dialog
      v-model="showInitScriptDialog"
      title="配置初始化脚本"
      width="800px"
    >
      <div class="init-script-editor">
        <p>此脚本将在用户进入该分组页面时显示，用于展示环境信息或说明：</p>
        <el-input
          v-model="initScriptContent"
          type="textarea"
          :rows="10"
          placeholder="请输入初始化脚本内容，支持Shell脚本语法"
        />
      </div>
      <template #footer>
        <el-button @click="showInitScriptDialog = false">取消</el-button>
        <el-button type="primary" @click="saveInitScript">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '@/utils/http'

const loading = ref(false)
const scriptGroups = ref([])
const showCreateDialog = ref(false)
const showInitScriptDialog = ref(false)
const editingGroup = ref(null)
const initScriptContent = ref('')

const groupForm = reactive({
  name: '',
  description: '',
  sortOrder: 0,
  status: 'ACTIVE'
})

const groupRules = {
  name: [{ required: true, message: '请输入分组名称', trigger: 'blur' }]
}

const loadScriptGroups = async () => {
  loading.value = true
  try {
    const response = await http.get('/api/admin/script-groups')
    scriptGroups.value = response.data
  } catch (error) {
    console.error('加载分组列表失败:', error)
    ElMessage.error('加载分组列表失败')
  } finally {
    loading.value = false
  }
}

const editGroup = (group) => {
  editingGroup.value = group
  Object.assign(groupForm, group)
  showCreateDialog.value = true
}

const saveGroup = async () => {
  try {
    if (editingGroup.value) {
      await http.put(`/api/admin/script-groups/${editingGroup.value.id}`, groupForm)
      ElMessage.success('分组更新成功')
    } else {
      await http.post('/api/admin/script-groups', groupForm)
      ElMessage.success('分组创建成功')
    }
    showCreateDialog.value = false
    resetForm()
    loadScriptGroups()
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败')
  }
}

const deleteGroup = async (group) => {
  try {
    await ElMessageBox.confirm('确定要删除这个分组吗？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await http.delete(`/api/admin/script-groups/${group.id}`)
    ElMessage.success('删除成功')
    loadScriptGroups()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const configInitScript = (group) => {
  editingGroup.value = group
  initScriptContent.value = group.initScript || ''
  showInitScriptDialog.value = true
}

const saveInitScript = async () => {
  try {
    const updatedGroup = { ...editingGroup.value, initScript: initScriptContent.value }
    await http.put(`/api/admin/script-groups/${editingGroup.value.id}`, updatedGroup)
    ElMessage.success('初始化脚本保存成功')
    showInitScriptDialog.value = false
    loadScriptGroups()
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const resetForm = () => {
  Object.assign(groupForm, {
    name: '',
    description: '',
    sortOrder: 0,
    status: 'ACTIVE'
  })
  editingGroup.value = null
}

onMounted(() => {
  loadScriptGroups()
})
</script>

<style scoped>
.script-groups {
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

.init-script-editor {
  margin-bottom: 20px;
}

.init-script-editor p {
  margin-bottom: 10px;
  color: #666;
}
</style>