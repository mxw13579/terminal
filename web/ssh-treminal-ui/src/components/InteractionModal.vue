<template>
  <el-dialog
    v-model="dialogVisible"
    :title="interactionRequest.promptMessage"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    width="500px"
  >
    <div v-if="interactionRequest.interactionType === 'CONFIRMATION'">
      <p>{{ interactionRequest.promptMessage }}</p>
    </div>
    <div v-if="interactionRequest.interactionType === 'TEXT_INPUT'">
      <el-input v-model="responseText" :placeholder="interactionRequest.promptMessage"></el-input>
    </div>
    <div v-if="interactionRequest.interactionType === 'PASSWORD'">
      <el-input v-model="responseText" :placeholder="interactionRequest.promptMessage" type="password"></el-input>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleResponse(false)" v-if="interactionRequest.interactionType === 'CONFIRMATION'">No</el-button>
        <el-button type="primary" @click="handleResponse(true)">
          {{ interactionRequest.interactionType === 'CONFIRMATION' ? 'Yes' : 'Submit' }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, defineProps, defineEmits } from 'vue'
import { ElDialog, ElButton, ElInput } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const dialogVisible = ref(false)
const interactionRequest = ref(null)
const responseText = ref('')

watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    interactionRequest.value = newVal
    dialogVisible.value = true
  } else {
    dialogVisible.value = false
  }
})

const handleResponse = (response) => {
  let responseData = response
  if (interactionRequest.value.interactionType !== 'CONFIRMATION') {
    responseData = responseText.value
  }
  emit('submit', {
    interactionId: interactionRequest.value.id,
    responseData: responseData
  })
  dialogVisible.value = false
  emit('update:modelValue', null)
  responseText.value = ''
}
</script>

<style scoped>
.dialog-footer {
  text-align: right;
}
</style>
