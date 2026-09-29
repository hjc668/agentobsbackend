<template>
  <div class="construction-page">
    <div class="construction-content">
      <div class="construction-icon">
        <i class="el-icon-s-platform" />
      </div>
      <span class="construction-chip">{{ $t("placeholder.badge") }}</span>
      <h1 class="construction-title">{{ $t("placeholder.title", { name: displayName }) }}</h1>
      <p class="construction-desc">{{ $t("placeholder.description") }}</p>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    name: { type: String, required: false, default: "" }
  },
  computed: {
    displayName() {
      if (this.name) return this.name;
      var routeName = this.$route && this.$route.name;
      return routeName ? this.$t("nav." + routeName) : "";
    }
  }
};
</script>

<style lang="scss" scoped>
.construction-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 120px);
  padding: 40px 20px;
}

.construction-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 420px;
}

.construction-icon {
  width: 96px;
  height: 96px;
  border-radius: 24px;
  background: linear-gradient(135deg, #eef0fb 0%, #e4e7f5 100%);
  display: grid;
  place-items: center;
  margin-bottom: 28px;

  i {
    font-size: 44px;
    color: #6374ee;
  }
}

.construction-chip {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 4px;
  background: #fef3cd;
  color: #856404;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  margin-bottom: 14px;
}

.construction-title {
  margin: 0 0 10px;
  font-size: 22px;
  font-weight: 600;
  color: #11131a;
  line-height: 1.3;
}

.construction-desc {
  margin: 0;
  font-size: 14px;
  color: #6b7080;
  line-height: 1.6;
}
</style>
