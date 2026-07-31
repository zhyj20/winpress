<script setup lang="ts">
import type { Component } from 'vue'
import { ArrowUpRight } from 'lucide-vue-next'

defineProps<{
  label: string
  title: string
  value: string
  description: string
  note: string
  action: string
  to: string
  tone: 'amber' | 'blue' | 'green' | 'purple'
  icon: Component
}>()
</script>

<template>
  <article class="home-channel-card" :class="`is-${tone}`">
    <div class="home-channel-card-head">
      <span><component :is="icon" :size="22" /></span><small>{{ label }}</small>
    </div>
    <h3>{{ title }}</h3>
    <strong>{{ value }}</strong>
    <p>{{ description }}</p>
    <small class="home-channel-note">{{ note }}</small>
    <RouterLink :to="to">{{ action }}<ArrowUpRight :size="16" /></RouterLink>
  </article>
</template>
