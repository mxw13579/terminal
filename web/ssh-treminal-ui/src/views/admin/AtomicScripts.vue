<template>
  <div class="atomic-scripts-admin">
    <div class="page-header">
      <h2>原子脚本管理</h2>
      <button @click="openModal()" class="btn btn-primary">新增脚本</button>
    </div>

    <div class="scripts-table-container">
      <table class="scripts-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
            <th>类型</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="text-center">加载中...</td>
          </tr>
          <tr v-else-if="scripts.length === 0">
            <td colspan="7" class="text-center">暂无数据</td>
          </tr>
          <tr v-for="script in scripts" :key="script.id">
            <td>{{ script.id }}</td>
            <td>{{ script.name }}</td>
            <td><span class="badge" :class="`badge-${script.scriptType}`">{{ getScriptTypeLabel(script.scriptType) }}</span></td>
            <td><span class="badge" :class="`badge-${script.status}`">{{ getStatusLabel(script.status) }}</span></td>
            <td>{{ formatDateTime(script.createdAt) }}</td>
            <td>{{ formatDateTime(script.updatedAt) }}</td>
            <td>
              <button @click="openModal(script)" class="btn btn-secondary btn-sm">编辑</button>
              <button @click="confirmDelete(script.id)" class="btn btn-danger btn-sm">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <div v-if="isModalOpen" class="modal-overlay">
      <div class="modal-content">
        <h3>{{ isEditing ? '编辑' : '新增' }}原子脚本</h3>
        <form @submit.prevent="handleSubmit">
          <div class="form-group">
            <label>名称</label>
            <input type="text" v-model="currentScript.name" required>
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="currentScript.description"></textarea>
          </div>
          <div class="form-group">
            <label>脚本内容</label>
            <textarea v-model="currentScript.scriptContent" required rows="10"></textarea>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>脚本类型</label>
              <select v-model="currentScript.scriptType">
                <option value="USER_SIMPLE">用户自定义</option>
                <option value="BUILT_IN_TEMPLATE">内置模板</option>
                 <option value="BUILT_IN_INTERACTIVE">内置可交互</option>
              </select>
            </div>
            <div class="form-group">
              <label>状态</label>
              <select v-model="currentScript.status">
                <option value="ACTIVE">活跃</option>
                <option value="INACTIVE">非活跃</option>
                <option value="DRAFT">草稿</option>
              </select>
            </div>
          </div>
          <div class="form-actions">
            <button type="button" @click="closeModal" class="btn btn-secondary">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="submitting">{{ submitting ? '保存中...' : '保存' }}</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue';

const scripts = ref([]);
const loading = ref(true);
const submitting = ref(false);
const isModalOpen = ref(false);
const isEditing = ref(false);

const initialScriptState = {
  name: '',
  description: '',
  scriptContent: '',
  scriptType: 'USER_SIMPLE',
  status: 'DRAFT',
};
const currentScript = reactive({ ...initialScriptState });

// 获取所有脚本
const fetchScripts = async () => {
  try {
    loading.value = true;
    const response = await fetch('/api/admin/atomic-scripts');
    if (!response.ok) throw new Error('Network response was not ok');
    scripts.value = await response.json();
  } catch (error) {
    console.error("获取原子脚本失败:", error);
    alert("获取原子脚本失败!");
  } finally {
    loading.value = false;
  }
};

onMounted(fetchScripts);

// 打开弹窗
const openModal = (script = null) => {
  if (script) {
    isEditing.value = true;
    Object.assign(currentScript, script);
  } else {
    isEditing.value = false;
    Object.assign(currentScript, initialScriptState);
  }
  isModalOpen.value = true;
};

// 关闭弹窗
const closeModal = () => {
  isModalOpen.value = false;
};

// 提交表单 (新增或更新)
const handleSubmit = async () => {
  submitting.value = true;
  const url = isEditing.value ? `/api/admin/atomic-scripts/${currentScript.id}` : '/api/admin/atomic-scripts';
  const method = isEditing.value ? 'PUT' : 'POST';

  try {
    const response = await fetch(url, {
      method: method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(currentScript),
    });
    if (!response.ok) throw new Error('保存失败');
    await fetchScripts(); // 重新加载列表
    closeModal();
  } catch (error) {
    console.error("保存脚本失败:", error);
    alert("保存脚本失败!");
  } finally {
    submitting.value = false;
  }
};

// 删除确认
const confirmDelete = async (id) => {
  if (window.confirm("确定要删除这个脚本吗？")) {
    try {
      const response = await fetch(`/api/admin/atomic-scripts/${id}`, { method: 'DELETE' });
      if (!response.ok) throw new Error('删除失败');
      await fetchScripts(); // 重新加载列表
    } catch (error) {
      console.error("删除脚本失败:", error);
      alert("删除脚本失败!");
    }
  }
};


// --- Helper Functions ---
const getScriptTypeLabel = (type) => ({
  'USER_SIMPLE': '用户自定义',
  'BUILT_IN_TEMPLATE': '内置模板',
  'BUILT_IN_INTERACTIVE': '内置可交互'
}[type] || type);

const getStatusLabel = (status) => ({
  'ACTIVE': '活跃',
  'INACTIVE': '非活跃',
  'DRAFT': '草稿'
}[status] || status);

const formatDateTime = (dateTime) => {
  if (!dateTime) return '';
  return new Date(dateTime).toLocaleString();
};

</script>

<style scoped>
.atomic-scripts-admin {
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

h2 {
  font-size: 24px;
  font-weight: 600;
}

.scripts-table-container {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  overflow-x: auto;
}

.scripts-table {
  width: 100%;
  border-collapse: collapse;
}

.scripts-table th,
.scripts-table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
}

.scripts-table th {
  background-color: #fafafa;
  font-weight: 500;
}

.text-center {
  text-align: center;
}

.btn {
  padding: 6px 12px;
  border: 1px solid transparent;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-sm {
  padding: 4px 8px;
  font-size: 12px;
}
.btn-primary {
  background-color: #1890ff;
  color: white;
}
.btn-secondary {
  background-color: #f5f5f5;
  color: #333;
  border-color: #d9d9d9;
}
.btn-danger {
  background-color: #ff4d4f;
  color: white;
}
.btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

td .btn {
  margin-right: 8px;
}

.badge {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  text-transform: uppercase;
}
.badge-USER_SIMPLE { background-color: #e6f7ff; color: #1890ff; }
.badge-BUILT_IN_TEMPLATE { background-color: #fffbe6; color: #faad14; }
.badge-BUILT_IN_INTERACTIVE { background-color: #e6fffb; color: #13c2c2; }
.badge-ACTIVE { background-color: #f6ffed; color: #52c41a; }
.badge-INACTIVE { background-color: #f5f5f5; color: #bfbfbf; }
.badge-DRAFT { background-color: #fff0f6; color: #eb2f96; }


/* Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  padding: 24px;
  border-radius: 8px;
  width: 90%;
  max-width: 600px;
}

.modal-content h3 {
  margin-top: 0;
  margin-bottom: 24px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
}

.form-group input,
.form-group textarea,
.form-group select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
}
.form-group textarea {
  resize: vertical;
  min-height: 80px;
}
.form-row {
    display: flex;
    gap: 16px;
}
.form-row .form-group {
    flex: 1;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}
</style>
