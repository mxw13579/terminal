<!-- Modal.vue -->
<template>
  <div class="modal-mask" @click.self="close">
    <div class="modal-dialog">
      <div class="modal-title">{{ title }}</div>
      <div class="modal-message">{{ message }}</div>
      <button class="modal-close-btn" @click="close">关闭</button>
    </div>
  </div>
</template>
<script setup>
const props = defineProps({
  title: String,
  message: String
});
const emit = defineEmits(['close']);
function close() {
  emit('close');
}
</script>
<style scoped>
.modal-mask {
  position: fixed; left: 0; top: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.4);
  z-index: 9999;
  display: flex; align-items: center; justify-content: center;
}
.modal-dialog {
  background: #222;
  color: #fff;
  border-radius: 10px;
  padding: 30px 40px;
  min-width: 260px;
  max-width: 90vw;
  box-shadow: 0 8px 32px 0 rgba(0,0,0,0.37);
  text-align: center;
}
.modal-title {
  font-size: 1.2em;
  font-weight: bold;
  margin-bottom: 18px;
}
.modal-message {
  margin-bottom: 22px;
  word-break: break-all;
}
.modal-close-btn {
  background: #6d28d9;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 8px 24px;
  font-size: 1em;
  cursor: pointer;
}
</style>
