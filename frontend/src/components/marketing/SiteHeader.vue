<script setup lang="ts">
import { ChevronRight, Menu, PanelLeftOpen, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'

withDefaults(defineProps<{ workspaceToggle?: boolean }>(), { workspaceToggle: false })
const emit = defineEmits<{ workspaceToggle: [] }>()

const auth = useAuthStore()
const menuOpen = ref(false)

const isCustomerWorkspace = computed(() => !auth.isAuthenticated || auth.user?.role === 'CUSTOMER')
const primaryAction = computed(() =>
  isCustomerWorkspace.value
    ? { to: '/requirements/new', label: '创建传播项目' }
    : { to: '/work-items', label: '查看待办' },
)

const links = [
  { label: '首页', to: '/' },
  { label: '解决方案', to: '/#solutions' },
  { label: '行业资讯', to: '/insights' },
  { label: '方法论', to: '/methodology' },
  { label: '案例展示', to: '/cases' },
  { label: '关于我们', to: '/about' },
]

function closeMenu() {
  menuOpen.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeMenu()
}

watch(menuOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <header class="mkt-header">
    <RouterLink class="mkt-logo" to="/" aria-label="云发布官网">
      <img src="/winpress-logo.png" alt="云发布" />
    </RouterLink>
    <nav class="mkt-nav" aria-label="网站导航">
      <RouterLink v-for="item in links" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
    </nav>
    <div class="mkt-header-actions">
      <RouterLink
        class="mkt-button mkt-button-ghost"
        :to="auth.isAuthenticated ? '/dashboard' : '/login'"
      >
        {{ auth.isAuthenticated ? '传播看板' : '登录操作台' }}
      </RouterLink>
      <RouterLink class="mkt-button mkt-button-primary mkt-header-cta" :to="primaryAction.to">
        {{ primaryAction.label }}
      </RouterLink>
      <button
        v-if="workspaceToggle"
        class="mkt-menu-button mkt-workspace-button"
        type="button"
        aria-label="打开操作菜单"
        title="打开操作菜单"
        @click="emit('workspaceToggle')"
      >
        <PanelLeftOpen :size="22" />
      </button>
      <button
        class="mkt-menu-button"
        type="button"
        aria-label="打开导航"
        :aria-expanded="menuOpen"
        aria-controls="mobile-navigation"
        @click="menuOpen = true"
      >
        <Menu :size="22" />
      </button>
    </div>
  </header>

  <Teleport to="body">
    <div
      v-if="menuOpen"
      class="mkt-mobile-menu"
      role="dialog"
      aria-modal="true"
      aria-label="网站导航"
    >
      <div class="mkt-mobile-menu-head">
        <img src="/winpress-logo.png" alt="云发布" />
        <button class="mkt-menu-button" type="button" aria-label="关闭导航" @click="closeMenu">
          <X :size="22" />
        </button>
      </div>
      <nav id="mobile-navigation">
        <RouterLink v-for="item in links" :key="item.to" :to="item.to" @click="closeMenu">
          {{ item.label }}<ChevronRight :size="18" />
        </RouterLink>
      </nav>
      <RouterLink class="mkt-button mkt-button-primary" :to="primaryAction.to" @click="closeMenu">
        {{ primaryAction.label }}
      </RouterLink>
      <RouterLink
        class="mkt-button mkt-button-secondary"
        :to="auth.isAuthenticated ? '/dashboard' : '/login'"
        @click="closeMenu"
      >
        {{ auth.isAuthenticated ? '传播看板' : '登录操作台' }}
      </RouterLink>
    </div>
  </Teleport>
</template>
