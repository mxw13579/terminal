<template>
  <svg 
    :width="size" 
    :height="size" 
    :fill="color" 
    viewBox="0 0 24 24"
    aria-hidden="true"
  >
    <path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/>
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