<script setup lang="ts">
import type { Component } from 'vue'
import { ArrowRight } from 'lucide-vue-next'

defineProps<{
  eyebrow: string
  title: string
  description: string
  boundary: string
  action: string
  to: string
  tone: 'orange' | 'purple' | 'blue' | 'green'
  icon: Component
}>()
</script>

<template>
  <RouterLink class="mkt-task-card" :class="`tone-${tone}`" :to="to">
    <span class="mkt-task-card-icon"><component :is="icon" :size="21" /></span>
    <span class="mkt-task-card-eyebrow">{{ eyebrow }}</span>
    <strong>{{ title }}</strong>
    <p>{{ description }}</p>
    <small>{{ boundary }}</small>
    <span class="mkt-task-card-action">{{ action }}<ArrowRight :size="16" /></span>
  </RouterLink>
</template>
