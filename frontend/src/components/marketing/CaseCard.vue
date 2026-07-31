<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  category: string
  title: string
  challenge: string
  response: string
  result: string
  icon: Component
}>()
</script>

<template>
  <article class="home-case-card">
    <div class="home-case-label"><component :is="icon" :size="19" />{{ category }}</div>
    <h3>{{ title }}</h3>
    <dl>
      <div>
        <dt>客户现状</dt>
        <dd>{{ challenge }}</dd>
      </div>
      <div>
        <dt>执行安排</dt>
        <dd>{{ response }}</dd>
      </div>
      <div>
        <dt>可查交付</dt>
        <dd>{{ result }}</dd>
      </div>
    </dl>
  </article>
</template>
