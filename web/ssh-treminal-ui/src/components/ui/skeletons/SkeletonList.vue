<template>
  <div class="skeleton-list">
    <div 
      v-for="n in count" 
      :key="n" 
      class="skeleton-list-item"
    >
      <div class="skeleton-avatar" />
      <div class="skeleton-content">
        <div class="skeleton-title" />
        <div class="skeleton-subtitle" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  count: {
    type: Number,
    default: 3
  }
})
</script>

<style scoped>
.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 8px;
  background: var(--color-surface);
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

.skeleton-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-border);
}

.skeleton-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-title {
  height: 16px;
  background: var(--color-border);
  border-radius: 4px;
  width: 70%;
}

.skeleton-subtitle {
  height: 12px;
  background: var(--color-border);
  border-radius: 4px;
  width: 50%;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>