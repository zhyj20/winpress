import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastKind = 'success' | 'error' | 'info'
export interface Toast {
  id: number
  message: string
  kind: ToastKind
}

export const useToastStore = defineStore('toast', () => {
  const items = ref<Toast[]>([])
  function show(message: string, kind: ToastKind = 'info') {
    const id = Date.now() + Math.floor(Math.random() * 1000)
    items.value.push({ id, message, kind })
    window.setTimeout(() => remove(id), 3600)
  }
  function remove(id: number) {
    items.value = items.value.filter((item) => item.id !== id)
  }
  return { items, show, remove }
})
