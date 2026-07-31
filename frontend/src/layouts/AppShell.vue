<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BarChart3,
  BookOpenCheck,
  BriefcaseBusiness,
  Building2,
  Cable,
  ClipboardPlus,
  KeyRound,
  Library,
  ListTodo,
  Newspaper,
  LogOut,
  Presentation,
  ReceiptText,
  MessagesSquare,
  PackageSearch,
  Settings2,
  PenTool,
  RadioTower,
  UserRoundSearch,
  Tags,
  ShieldCheck,
  UserCog,
  UserRound,
  X,
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import '@/assets/marketing.css'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const open = ref(false)
const isLocalDemo = import.meta.env.VITE_LOCAL_DEMO === 'true'

const menus = computed(() => {
  if (auth.user?.role === 'CUSTOMER') {
    return [
      {
        label: '项目总览',
        items: [
          { to: '/dashboard', label: '传播看板', icon: BarChart3 },
          { to: '/projects', label: '项目管理', icon: BriefcaseBusiness, matchChildren: true },
        ],
      },
      {
        label: '服务下单',
        items: [
          { to: '/requirements/new', label: '提交需求', icon: ClipboardPlus },
          { to: '/requirements/cloud-writing', label: '云采写', icon: PenTool },
          { to: '/media-invitation', label: '邀请媒体', icon: UserRoundSearch },
          { to: '/direct-publishing', label: '直编发稿', icon: Newspaper },
          {
            to: '/requirements/news-conference',
            label: '举办新闻发布会',
            icon: Presentation,
          },
        ],
      },
      {
        label: '任务与订单',
        items: [
          { to: '/work-items', label: '待办事项', icon: ListTodo },
          { to: '/tasks', label: '媒体与发布任务', icon: RadioTower },
          { to: '/orders', label: '任务记录', icon: Library },
          { to: '/order-management', label: '订单管理', icon: ReceiptText },
        ],
      },
    ]
  }

  if (auth.user?.role === 'PUBLISH_OPERATOR') {
    return [
      {
        label: '工作总览',
        items: [
          { to: '/dashboard', label: '传播看板', icon: BarChart3 },
          { to: '/projects', label: '项目协同', icon: BriefcaseBusiness, matchChildren: true },
          { to: '/work-items', label: '当前待办', icon: BookOpenCheck },
        ],
      },
      {
        label: '服务执行',
        items: [
          { to: '/writing-assignments', label: '云采写任务', icon: PenTool },
          { to: '/tasks', label: '媒体与发布任务', icon: RadioTower },
        ],
      },
      {
        label: '历史记录',
        items: [{ to: '/orders', label: '任务记录', icon: Library }],
      },
    ]
  }

  return [
    {
      label: '运营总览',
      items: [
        { to: '/dashboard', label: '传播看板', icon: BarChart3 },
        { to: '/projects', label: '项目管理', icon: BriefcaseBusiness, matchChildren: true },
        { to: '/work-items', label: '当前待办', icon: ListTodo },
      ],
    },
    {
      label: '服务执行',
      items: [
        { to: '/writing-assignments', label: '采写派单', icon: PenTool },
        { to: '/tasks', label: '媒体与发布任务', icon: RadioTower },
        { to: '/orders', label: '任务记录', icon: Library },
      ],
    },
    {
      label: '渠道与交易',
      items: [
        { to: '/admin/channels', label: '渠道管理', icon: Settings2 },
        { to: '/admin/pricing', label: '定价与比价', icon: Tags },
        { to: '/admin/suppliers', label: '供应商与订单', icon: PackageSearch },
        { to: '/admin/integrations', label: '接口管理', icon: Cable },
        { to: '/admin/open-api', label: '开放 API', icon: KeyRound },
        { to: '/admin/settlements', label: '结算与交易', icon: ReceiptText },
      ],
    },
    {
      label: '平台管理',
      items: [
        { to: '/admin/inquiries', label: '商务咨询', icon: MessagesSquare },
        { to: '/admin/users', label: '账号与权限', icon: UserCog },
        { to: '/admin/audit', label: '操作日志', icon: ShieldCheck },
      ],
    },
  ]
})

const roleLabel = computed(
  () =>
    ({
      CUSTOMER: '客户',
      PUBLISH_OPERATOR: '服务运营',
      PLATFORM_ADMIN: '平台运营',
    })[auth.user?.role || 'CUSTOMER'],
)

function isMenuActive(item: { to: string; matchChildren?: boolean }) {
  if (route.path === item.to) return true
  return Boolean(item.matchChildren && route.path.startsWith(`${item.to}/`))
}

const workspaceLabel = computed(() => {
  if (auth.user?.role === 'CUSTOMER') return '品牌工作台'
  if (auth.user?.role === 'PUBLISH_OPERATOR') return '服务运营台'
  return '平台运营台'
})

async function signOut() {
  await auth.logout()
  await router.replace('/login')
}

function closeSidebar() {
  open.value = false
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ open }">
      <div class="brand-row">
        <img src="/winpress-logo.png" alt="WinPress" />
        <button
          class="icon-button sidebar-close"
          type="button"
          aria-label="关闭侧边栏"
          @click="closeSidebar"
        >
          <X :size="20" />
        </button>
      </div>

      <div class="workspace-label">
        <Building2 :size="16" />
        <span>{{ workspaceLabel }}</span>
      </div>

      <nav class="sidebar-nav" aria-label="主导航">
        <section v-for="group in menus" :key="group.label" class="sidebar-nav-group">
          <p class="sidebar-nav-group-label">{{ group.label }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            :class="{ 'nav-current': isMenuActive(item) }"
            :aria-current="isMenuActive(item) ? 'page' : undefined"
            @click="closeSidebar"
          >
            <component :is="item.icon" :size="19" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </section>
      </nav>

      <div class="sidebar-footer">
        <RouterLink class="sidebar-account-link" to="/account" @click="open = false">
          <UserRound :size="18" />
          <span>账号信息</span>
        </RouterLink>
        <div class="user-block">
          <span class="avatar">{{ auth.user?.displayName?.slice(0, 1) }}</span>
          <div>
            <strong>{{ auth.user?.displayName }}</strong>
            <small>{{ roleLabel }}</small>
          </div>
        </div>
        <button
          class="icon-button"
          type="button"
          title="退出登录"
          aria-label="退出登录"
          @click="signOut"
        >
          <LogOut :size="18" />
        </button>
      </div>
    </aside>

    <div v-if="open" class="sidebar-scrim" @click="closeSidebar" />

    <main class="main-area">
      <SiteHeader workspace-toggle @workspace-toggle="open = true" />
      <div v-if="isLocalDemo" class="local-demo-notice" role="status">
        <strong>本机演示环境</strong>
        <span>示例与测试数据仅用于功能验证，不代表生产服务、媒体资源或履约结果。</span>
      </div>
      <div class="content-wrap"><RouterView /></div>
    </main>
  </div>
</template>
