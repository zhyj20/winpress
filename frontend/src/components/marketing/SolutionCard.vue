<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{ step: string; title: string; description: string; icon: Component }>()
</script>

<template>
  <article class="home-solution-card">
    <span class="home-solution-icon"><component :is="icon" :size="22" /></span>
    <small>STEP {{ step }}</small>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
  </article>
</template>
