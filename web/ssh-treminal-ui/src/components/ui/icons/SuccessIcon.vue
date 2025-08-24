<template>
  <svg 
    :width="size" 
    :height="size" 
    :fill="color" 
    viewBox="0 0 24 24"
    aria-hidden="true"
  >
    <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
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