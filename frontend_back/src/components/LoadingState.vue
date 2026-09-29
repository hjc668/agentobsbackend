<template>
  <div class="loading-state">
    <i class="el-icon-loading"></i>
    <span>{{ message || $t("common.loading") }}</span>
  </div>
</template>

<script>
export default {
  name: "LoadingState",
  props: {
    message: {
      type: String,
      default: ""
    }
  }
};
</script>
