import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { Role } from '@/types'

const routes = [
  {
    path: '/',
    component: () => import('@/views/HomeView.vue'),
    alias: '/home',
    meta: { public: true },
  },
  {
    path: '/methodology',
    component: () => import('@/views/MethodologyView.vue'),
    meta: {
      public: true,
      title: '方法论｜云发布',
      description:
        '云发布新闻发布会项目管理方法：三项基本资料立项，媒体名单核验，会前、现场和会后清单执行。',
    },
  },
  { path: '/media-insights', redirect: '/insights', meta: { public: true } },
  {
    path: '/insights',
    component: () => import('@/views/InsightsView.vue'),
    meta: { public: true },
  },
  { path: '/solutions', redirect: '/#solutions', meta: { public: true } },
  {
    path: '/cases',
    component: () => import('@/views/CasesView.vue'),
    meta: {
      public: true,
      title: '项目示例｜云发布',
      description: '现场采写、媒体邀请、直编发稿和新闻发布会四类传播项目的执行流程与可查交付示例。',
    },
  },
  {
    path: '/cases/industry-forum',
    component: () => import('@/views/CaseStudyView.vue'),
    meta: {
      public: true,
      robots: 'noindex, nofollow, noarchive, nosnippet',
      title: '媒体邀请项目（脱敏样本）｜云发布',
      description:
        '云发布媒体邀请服务的脱敏项目摘要：只说明公开边界，不展示名单、操作过程或项目成果。',
    },
  },
  {
    path: '/cases/chipsea-computex-2026',
    component: () => import('@/views/ChipseaCaseStudyView.vue'),
    meta: {
      public: true,
      robots: 'noindex, nofollow, noarchive, nosnippet',
      title: '芯海科技 COMPUTEX 2026 媒体云发布会案例｜云发布',
      description:
        '芯海科技 COMPUTEX 2026 媒体云发布会案例：项目启动、内容准备、媒体执行、展会现场与公开报道核验。',
    },
  },
  {
    path: '/case-evidence/historical-reports',
    component: () => import('@/views/HistoricalReportCatalogView.vue'),
    meta: {
      public: true,
      robots: 'noindex, nofollow, noarchive, nosnippet',
      title: '历史传播汇报归集｜云发布',
      description:
        '云发布按活动传播和直编发稿归集历史汇报资料，只展示项目类型与资料结构，不公开原始文件或内部明细。',
    },
  },
  { path: '/about', component: () => import('@/views/AboutView.vue'), meta: { public: true } },
  {
    path: '/api-integration',
    component: () => import('@/views/ApiIntegrationView.vue'),
    meta: { public: true },
  },
  {
    path: '/cloud-writing',
    component: () => import('@/views/CloudWritingView.vue'),
    meta: { public: true },
  },
  {
    path: '/contact',
    component: () => import('@/views/ContactView.vue'),
    meta: { public: true },
  },
  {
    path: '/legal',
    component: () => import('@/views/TrustPageView.vue'),
    props: { page: 'legal' },
    meta: { public: true },
  },
  {
    path: '/privacy',
    component: () => import('@/views/TrustPageView.vue'),
    props: { page: 'privacy' },
    meta: { public: true },
  },
  {
    path: '/terms',
    component: () => import('@/views/TrustPageView.vue'),
    props: { page: 'terms' },
    meta: { public: true },
  },
  {
    path: '/service-boundaries',
    component: () => import('@/views/TrustPageView.vue'),
    props: { page: 'boundaries' },
    meta: { public: true },
  },
  {
    path: '/case-evidence',
    component: () => import('@/views/TrustPageView.vue'),
    props: { page: 'cases' },
    meta: { public: true },
  },
  {
    path: '/login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, guestOnly: true },
  },
  {
    path: '/register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true, guestOnly: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AppShell.vue'),
    children: [
      { path: 'dashboard', component: () => import('@/views/DashboardView.vue') },
      {
        path: 'requirements/new',
        component: () => import('@/views/RequirementCreateView.vue'),
        meta: { roles: ['CUSTOMER'] },
      },
      {
        path: 'requirements/cloud-writing',
        // Keep the service name stable in customer-facing deep links.  Earlier
        // materials used "onsite-writing" while the current information
        // architecture calls this service 云采写; both paths must open the same
        // independently orderable service, not fall through to the marketing home.
        alias: '/requirements/onsite-writing',
        component: () => import('@/views/RequirementCreateView.vue'),
        meta: { roles: ['CUSTOMER'], service: 'ONSITE_WRITING' },
      },
      {
        path: 'requirements/news-conference',
        component: () => import('@/views/RequirementCreateView.vue'),
        meta: { roles: ['CUSTOMER'], service: 'NEWS_CONFERENCE' },
      },
      {
        path: 'media-invitation',
        component: () => import('@/views/ChannelsView.vue'),
        meta: { roles: ['CUSTOMER'], channelType: 'MEDIA_PR' },
      },
      {
        path: 'direct-publishing',
        component: () => import('@/views/ChannelsView.vue'),
        meta: { roles: ['CUSTOMER'], channelType: 'DIRECT_PUBLISHING' },
      },
      {
        path: 'channels',
        redirect: '/direct-publishing',
        meta: { roles: ['CUSTOMER'] },
      },
      {
        path: 'orders',
        // "任务记录" is the customer label; preserve its direct route for
        // bookmarks and external navigation while retaining the existing page.
        alias: '/task-records',
        component: () => import('@/views/OrdersView.vue'),
      },
      {
        path: 'order-management',
        component: () => import('@/views/OrderManagementView.vue'),
        meta: { roles: ['CUSTOMER'] },
      },
      { path: 'projects', component: () => import('@/views/ProjectsView.vue') },
      { path: 'projects/:id', component: () => import('@/views/ProjectDetailView.vue') },
      {
        path: 'writing-assignments',
        component: () => import('@/views/WritingAssignmentsView.vue'),
        meta: { roles: ['PUBLISH_OPERATOR', 'PLATFORM_ADMIN'] },
      },
      { path: 'tasks', component: () => import('@/views/TasksView.vue') },
      { path: 'work-items', component: () => import('@/views/WorkItemsView.vue') },
      {
        path: 'admin/channels',
        component: () => import('@/views/ChannelAdminView.vue'),
        meta: { roles: ['PLATFORM_ADMIN'] },
      },
      {
        path: 'admin/pricing',
        component: () => import('@/views/PricingAdminView.vue'),
        meta: { roles: ['PLATFORM_ADMIN'] },
      },
      {
        path: 'admin/suppliers',
        component: () => import('@/views/SuppliersView.vue'),
        meta: { roles: ['PLATFORM_ADMIN'] },
      },
      {
        path: 'admin/integrations',
        component: () => import('@/views/IntegrationAdminView.vue'),
        meta: { roles: ['PLATFORM_ADMIN'] },
      },
      {
        path: 'admin/open-api',
        component: () => import('@/views/OpenApiAdminView.vue'),
        meta: { roles: ['PLATFORM_ADMIN'] },
      },
      {
        path: 'admin/inquiries',
        component: () => import('@/views/InquiriesAdminView.vue'),
        meta: { roles: ['PLATFORM_ADMIN'] },
      },
      {
        path: 'admin/settlements',
        component: () => import('@/views/SettlementsView.vue'),
        meta: { roles: ['PLATFORM_ADMIN'] },
      },
      {
        path: 'admin/users',
        component: () => import('@/views/UsersView.vue'),
        meta: { roles: ['PLATFORM_ADMIN'] },
      },
      {
        path: 'admin/audit',
        component: () => import('@/views/AuditView.vue'),
        meta: { roles: ['PLATFORM_ADMIN'] },
      },
      { path: 'account', component: () => import('@/views/AccountView.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 84, behavior: 'smooth' }
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  // API integration is an assessment service, never a normal requirement type. Legacy or
  // malformed service links must not silently fall through to a media-invitation order.
  if (to.path === '/requirements/new') {
    const requestedService = typeof to.query.service === 'string' ? to.query.service : ''
    if (['INTEGRATED_PROJECT', 'API_INTEGRATION'].includes(requestedService)) {
      return { path: '/api-integration', query: { from: 'order' } }
    }
    if (
      requestedService &&
      !['ONSITE_WRITING', 'MEDIA_PR', 'DIRECT_PUBLISHING', 'NEWS_CONFERENCE'].includes(
        requestedService,
      )
    ) {
      return { path: '/requirements/new', query: { invalidService: '1' } }
    }
  }
  const auth = useAuthStore()
  if (to.meta.public) return to.meta.guestOnly && auth.isAuthenticated ? '/dashboard' : true
  if (!auth.isAuthenticated) return { path: '/login', query: { redirect: to.fullPath } }
  const roles = to.meta.roles as Role[] | undefined
  if (roles && auth.user && !roles.includes(auth.user.role)) return '/dashboard'
  return true
})

router.afterEach((to) => {
  const defaultTitle = '云发布｜企业新闻传播项目操作执行系统'
  const defaultDescription =
    '云发布提供现场采写、媒体邀请、直编发稿和新闻发布会项目管理，需求、任务、费用和交付分别确认。'
  document.title = typeof to.meta.title === 'string' ? to.meta.title : defaultTitle
  let description = document.querySelector<HTMLMetaElement>('meta[name="description"]')
  if (!description) {
    description = document.createElement('meta')
    description.name = 'description'
    document.head.appendChild(description)
  }
  description.content =
    typeof to.meta.description === 'string' ? to.meta.description : defaultDescription

  let robots = document.querySelector<HTMLMetaElement>('meta[name="robots"]')
  if (!robots) {
    robots = document.createElement('meta')
    robots.name = 'robots'
    document.head.appendChild(robots)
  }
  robots.content = typeof to.meta.robots === 'string' ? to.meta.robots : 'index,follow'
})

export default router
