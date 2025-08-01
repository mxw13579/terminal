<template>
  <div class="script-builder">
    <!-- Header -->
    <div class="builder-header">
      <div class="header-left">
        <h1><el-icon><Setting /></el-icon> Aggregated Script Builder</h1>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="saveScript" :disabled="scriptCommands.length === 0 || !aggregatedScript.name">
          Save Script
        </el-button>
        <!-- Other buttons remain -->
      </div>
    </div>

    <div class="builder-content">
      <!-- Left Panel: Available Commands -->
      <div class="command-panel">
        <div class="panel-header"><h3>Available Atomic Scripts</h3></div>
        <draggable :list="availableAtomicScripts" :group="{ name: 'commands', pull: 'clone', put: false }" item-key="id">
          <template #item="{ element }">
            <div class="command-item">{{ element.name }}</div>
          </template>
        </draggable>
      </div>

      <!-- Center Panel: Script Editor -->
      <div class="script-editor">
        <div class="editor-header">
            <h3>Script Workflow</h3>
        </div>
        
        <!-- Metadata Form -->
        <div class="script-metadata-form">
            <el-input v-model="aggregatedScript.name" placeholder="Enter Script Name" />
            <el-input v-model="aggregatedScript.description" placeholder="Enter Script Description" type="textarea" />
            <el-select v-model="aggregatedScript.type" placeholder="Select Script Type">
                <el-option label="Generic Template" value="GENERIC_TEMPLATE"></el-option>
                <el-option label="Project Specific" value="PROJECT_SPECIFIC"></el-option>
            </el-select>
        </div>

        <!-- Draggable Area -->
        <div class="script-flow-container">
          <draggable v-model="scriptCommands" group="commands" class="script-flow" item-key="id">
            <template #item="{ element, index }">
              <div class="script-command">
                <span class="step-number">{{ index + 1 }}</span>
                <span class="command-name">{{ element.name }}</span>
                <el-input v-model="element.conditionExpression" placeholder="Condition (e.g., ${OS_TYPE} == 'Debian')" />
                <el-button @click="removeCommand(index)" type="danger" size="small">Remove</el-button>
              </div>
            </template>
          </draggable>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue';
import draggable from 'vuedraggable';
import http from '@/utils/http'; // Assuming a pre-configured axios instance
import { ElMessage } from 'element-plus';

export default {
  name: 'ScriptBuilder',
  components: { draggable },
  setup() {
    const availableAtomicScripts = ref([]);
    const scriptCommands = ref([]);

    const aggregatedScript = reactive({
        name: '',
        description: '',
        type: 'GENERIC_TEMPLATE',
    });

    // Fetch available atomic scripts on mount
    onMounted(async () => {
      try {
        const response = await http.get('/api/admin/atomic-scripts'); // Assuming an endpoint to get atomic scripts
        availableAtomicScripts.value = response.data;
      } catch (error) {
        ElMessage.error('Failed to load atomic scripts.');
      }
    });

    const removeCommand = (index) => {
        scriptCommands.value.splice(index, 1);
    };

    const saveScript = async () => {
      if (!aggregatedScript.name) {
        ElMessage.warning('Please provide a name for the script.');
        return;
      }

      const payload = {
        name: aggregatedScript.name,
        description: aggregatedScript.description,
        type: aggregatedScript.type,
        steps: scriptCommands.value.map((command, index) => ({
          atomicScriptId: command.id,
          executionOrder: index + 1,
          conditionExpression: command.conditionExpression || null,
          variableMapping: command.variableMapping || null, // Assuming this could be configured in the UI later
        })),
      };

      try {
        await http.post('/api/admin/aggregated-scripts', payload);
        ElMessage.success('Aggregated script saved successfully!');
        // Optionally, clear the builder or navigate away
        scriptCommands.value = [];
        Object.assign(aggregatedScript, { name: '', description: '', type: 'GENERIC_TEMPLATE' });
      } catch (error) {
        console.error('Failed to save aggregated script:', error);
        ElMessage.error('Failed to save script. See console for details.');
      }
    };

    return {
      availableAtomicScripts,
      scriptCommands,
      aggregatedScript,
      removeCommand,
      saveScript,
    };
  },
};
</script>

<style scoped>
/* Basic styling for layout */
.script-builder { display: flex; flex-direction: column; height: 100vh; }
.builder-header { padding: 10px; border-bottom: 1px solid #ccc; }
.builder-content { display: flex; flex: 1; }
.command-panel { width: 250px; padding: 10px; border-right: 1px solid #ccc; }
.script-editor { flex: 1; padding: 10px; }
.script-metadata-form { margin-bottom: 20px; display: grid; gap: 10px; }
.script-flow-container { min-height: 400px; border: 1px dashed #ccc; padding: 10px; }
.script-flow { min-height: 380px; }
.command-item, .script-command { padding: 10px; margin: 5px; border: 1px solid #ddd; background: #f9f9f9; cursor: pointer; }
.script-command { display: flex; align-items: center; gap: 10px; }
</style>