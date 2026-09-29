<template>
  <el-alert v-if="message" :title="message" type="error" :closable="dismissible" show-icon @close="$emit('dismiss')" />
</template>

<script>
export default {
  name: "ErrorBanner",
  props: {
    message: {
      type: String,
      default: ""
    },
    dismissible: {
      type: Boolean,
      default: false
    }
  }
};
</script>
