<template>
  <div class="json-tree-node">
    <details v-if="isCollection" :open="depth < 2">
      <summary>
        <span v-if="label" class="json-key">{{ label }}: </span
        ><span class="json-bracket">{{ isArray ? "[" : "{" }}</span
        ><span class="json-size">{{ size }} {{ isArray ? "items" : "keys" }}</span
        ><span class="json-bracket">{{ isArray ? "]" : "}" }}</span>
      </summary>
      <div class="json-tree-children">
        <JsonTree v-for="entry in entries" :key="entry[0]" :value="entry[1]" :label="entry[0]" :depth="depth + 1" />
      </div>
    </details>
    <div v-else class="json-leaf">
      <span v-if="label" class="json-key">{{ label }}: </span><span :class="valueClass">{{ displayValue }}</span>
    </div>
  </div>
</template>

<script>
export default {
  name: "JsonTree",
  props: { value: { default: null }, label: { type: String, default: "" }, depth: { type: Number, default: 0 } },
  computed: {
    isArray() {
      return Array.isArray(this.value);
    },
    isCollection() {
      return this.value !== null && typeof this.value === "object";
    },
    entries() {
      return this.isCollection ? Object.entries(this.value) : [];
    },
    size() {
      return this.entries.length;
    },
    valueClass() {
      return "json-value json-" + (this.value === null ? "null" : typeof this.value);
    },
    displayValue() {
      if (this.value === null) return "null";
      if (this.value === undefined) return "undefined";
      if (typeof this.value === "string") return JSON.stringify(this.value);
      return String(this.value);
    }
  }
};
</script>
