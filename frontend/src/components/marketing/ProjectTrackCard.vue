<script setup lang="ts">
defineProps<{
  stage: string
  title: string
  description: string
  confirmation: string
  state: 'planned' | 'active' | 'confirmed'
}>()
</script>

<template>
  <article class="mkt-track-card" :class="`is-${state}`">
    <div>
      <span>{{ stage }}</span>
      <em>{{ state === 'planned' ? '待安排' : state === 'active' ? '执行中' : '客户确认' }}</em>
    </div>
    <strong>{{ title }}</strong>
    <p>{{ description }}</p>
    <small>{{ confirmation }}</small>
  </article>
</template>
