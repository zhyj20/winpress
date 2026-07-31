<script setup lang="ts">
import { CheckCircle2, CircleAlert, Info, X } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const icon = { success: CheckCircle2, error: CircleAlert, info: Info }
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <div v-for="item in toast.items" :key="item.id" class="toast" :class="`toast-${item.kind}`">
      <component :is="icon[item.kind]" :size="18" />
      <span>{{ item.message }}</span>
      <button
        class="icon-button compact"
        type="button"
        aria-label="关闭提示"
        @click="toast.remove(item.id)"
      >
        <X :size="16" />
      </button>
    </div>
  </div>
</template>
