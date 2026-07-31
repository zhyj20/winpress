<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{ index: string; title: string; description: string; icon: Component }>()
</script>

<template>
  <article class="home-pain-card">
    <div>
      <span>{{ index }}</span
      ><component :is="icon" :size="20" />
    </div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
  </article>
</template>
