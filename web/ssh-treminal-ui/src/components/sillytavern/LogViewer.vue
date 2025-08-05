<template>
  <RealtimeLogViewer :container-name="containerName" />
</template>

<script>
import RealtimeLogViewer from './RealtimeLogViewer.vue'

export default {
  name: 'LogViewer',
  components: {
    RealtimeLogViewer
  },
  props: {
    containerName: {
      type: String,
      default: 'sillytavern'
    }
  }
}
</script>