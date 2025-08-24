<template>
  <svg 
    :width="size" 
    :height="size" 
    :fill="color" 
    viewBox="0 0 24 24"
    class="loading-icon"
    aria-hidden="true"
  >
    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-dasharray="31.416" stroke-dashoffset="31.416" />
  </svg>
</template>

<script setup>
defineProps({
  size: {
    type: Number,
    default: 20
  },
  color: {
    type: String,
    default: 'currentColor'
  }
})
</script>

<style scoped>
.loading-icon {
  animation: rotate 2s linear infinite;
}

.loading-icon circle {
  animation: dash 1.5s ease-in-out infinite;
}

@keyframes rotate {
  100% {
    transform: rotate(360deg);
  }
}

@keyframes dash {
  0% {
    stroke-dasharray: 1, 150;
    stroke-dashoffset: 0;
  }
  50% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -35;
  }
  100% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -124;
  }
}
</style>