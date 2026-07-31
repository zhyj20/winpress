<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { computed } from 'vue'

const props = defineProps<{ page: number; pageSize: number; total: number }>()
const emit = defineEmits<{ change: [page: number] }>()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
</script>

<template>
  <div class="pagination">
    <span>共 {{ total }} 条</span>
    <button
      class="icon-button"
      type="button"
      :disabled="page <= 1"
      aria-label="上一页"
      @click="emit('change', page - 1)"
    >
      <ChevronLeft :size="18" />
    </button>
    <strong>{{ page }} / {{ pages }}</strong>
    <button
      class="icon-button"
      type="button"
      :disabled="page >= pages"
      aria-label="下一页"
      @click="emit('change', page + 1)"
    >
      <ChevronRight :size="18" />
    </button>
  </div>
</template>
