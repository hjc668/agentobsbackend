<template>
  <div class="page-view">
    <header class="page-header">
      <div>
        <h1>{{ name }}</h1>
      </div>
      <div class="page-actions">
        <el-button size="mini" icon="el-icon-date">{{ $t("tracing.last7") }}</el-button>
      </div>
    </header>
    <section class="module-roadmap">
      <div class="module-roadmap-icon"><i class="el-icon-setting" /></div>
      <div>
        <span class="migration-chip">{{ $t("placeholder.badge") }}</span>
        <h2>{{ $t("placeholder.title", { name }) }}</h2>
        <p>{{ $t("placeholder.description") }}</p>
      </div>
    </section>
  </div>
</template>

<script>
export default { props: { name: { type: String, required: true } } };
</script>
