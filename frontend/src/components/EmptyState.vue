<template>
  <el-empty class="empty-state" :description="title || $t('common.noData')">
    <template slot="image">
      <i :class="iconClass" class="empty-state-icon"></i>
    </template>
    <p v-if="description" class="empty-state-desc">{{ description }}</p>
  </el-empty>
</template>

<script>
export default {
  name: "EmptyState",
  props: {
    title: {
      type: String,
      default: ""
    },
    description: {
      type: String,
      default: ""
    },
    iconClass: {
      type: String,
      default: "el-icon-document"
    }
  }
};
</script>

<style lang="scss" scoped>
.empty-state {
  min-height: 270px;
}
.empty-state-icon {
  font-size: 34px;
  color: #777b85;
}
.empty-state-desc {
  font-size: 10px;
  color: #777b85;
}
</style>
