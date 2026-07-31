<script setup lang="ts">
withDefaults(
  defineProps<{
    eyebrow: string
    title: string
    description?: string
    remark?: string
    centered?: boolean
  }>(),
  { description: '', centered: false },
)
</script>

<template>
  <div class="mkt-section-head" :class="{ 'mkt-section-head-center': centered }">
    <span class="mkt-eyebrow">{{ eyebrow }}</span>
    <h2>{{ title }}</h2>
    <p v-if="description">{{ description }}</p>
    <p v-if="remark" class="mkt-section-head-remark">{{ remark }}</p>
  </div>
</template>

<style scoped>
.mkt-section-head-remark {
  margin-top: 10px;
  margin-bottom: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
</style>
