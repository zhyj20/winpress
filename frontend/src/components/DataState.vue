<script setup lang="ts">
import { CircleAlert, Inbox, LoaderCircle, RefreshCw } from 'lucide-vue-next'

defineProps<{
  loading?: boolean
  error?: string
  empty?: boolean
  emptyTitle?: string
  emptyText?: string
}>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="data-state" role="status" aria-live="polite">
    <LoaderCircle class="spin" :size="28" /><strong>正在加载</strong>
  </div>
  <div v-else-if="error" class="data-state error-state" role="alert">
    <CircleAlert :size="30" /><strong>加载失败</strong>
    <p>{{ error }}</p>
    <button class="button secondary" type="button" @click="$emit('retry')">
      <RefreshCw :size="16" />重新加载
    </button>
  </div>
  <div v-else-if="empty" class="data-state">
    <Inbox :size="32" /><strong>{{ emptyTitle || '暂无数据' }}</strong>
    <p>{{ emptyText }}</p>
    <slot />
  </div>
  <slot v-else name="content" />
</template>
