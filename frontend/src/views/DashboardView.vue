<script setup lang="ts">
import {
  ArrowRight,
  CheckCircle2,
  CircleGauge,
  FileCheck2,
  ListChecks,
  RadioTower,
} from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, PageResult, ProjectSummary, PublishTask } from '@/types'

const auth = useAuthStore()
const loading = ref(true)
const error = ref('')
const dashboardMetricKeys = [
  'projectCount',
  'activeProjects',
  'taskCount',
  'pendingTasks',
  'resultCount',
  'todoCount',
  'pendingPlanConfirmations',
  'awaitingAcceptanceTasks',
  'mediaInvitationTasks',
  'pendingMediaInvitationTasks',
  'directPublishingTasks',
  'pendingDirectPublishingTasks',
  'writingAssignments',
  'pendingWritingAssignments',
  'conferenceProjects',
  'activeConferenceProjects',
  'pendingConferenceWorkItems',
  'taskRecordCount',
  'pendingPlatformExecutions',
  'inquiryTickets',
  'pendingInquiryTickets',
] as const

type DashboardMetricKey = (typeof dashboardMetricKeys)[number]
type DashboardStats = Record<DashboardMetricKey, number | null>

function emptyDashboardStats(): DashboardStats {
  return Object.fromEntries(dashboardMetricKeys.map((key) => [key, null])) as DashboardStats
}

function metricNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
}

function normaliseDashboardStats(value: unknown): DashboardStats {
  const source = value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
  const next = emptyDashboardStats()
  for (const key of dashboardMetricKeys) next[key] = metricNumber(source[key]) ? source[key] : null
  return next
}

function metricText(value: unknown) {
  return metricNumber(value) ? value.toLocaleString('zh-CN') : '数据暂不可用'
}

function metricClass(value: unknown) {
  return metricNumber(value) ? undefined : 'metric-unavailable'
}

const stats = ref<DashboardStats>(emptyDashboardStats())
const projects = ref<ProjectSummary[]>([])
const tasks = ref<PublishTask[]>([])
const isAdmin = computed(() => auth.user?.role === 'PLATFORM_ADMIN')
const isCustomer = computed(() => auth.user?.role === 'CUSTOMER')
const roleTitle = computed(() => '传播看板')
const roleEyebrow = computed(
  () =>
    ({ CUSTOMER: '项目与进度', PUBLISH_OPERATOR: '服务运营', PLATFORM_ADMIN: '平台运营' })[
      auth.user?.role || 'CUSTOMER'
    ],
)
const roleDescription = computed(
  () =>
    ({
      CUSTOMER: '集中查看项目进度、待确认事项和已核验的交付结果。',
      PUBLISH_OPERATOR: '处理已分配的采写与发布任务，并回填执行进度与结果。',
      PLATFORM_ADMIN: '统筹项目、资源、服务咨询与结算事项。',
    })[auth.user?.role || 'CUSTOMER'],
)
const todoLabel = computed(() => '待我处理')
const acceptanceLabel = computed(() => (isCustomer.value ? '已完成待验收' : '待客户验收'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [dashboard, projectList, taskList] = await Promise.all([
      http.get<ApiResponse<Record<string, unknown>>>('/dashboard'),
      http.get<ApiResponse<PageResult<ProjectSummary>>>('/projects', { params: { pageSize: 5 } }),
      http.get<ApiResponse<PageResult<PublishTask>>>('/publish-tasks', { params: { pageSize: 5 } }),
    ])
    stats.value = normaliseDashboardStats(dashboard.data.data)
    projects.value = projectList.data.data.items
    tasks.value = taskList.data.data.items
  } catch (e) {
    error.value = apiError(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <PageHeader :eyebrow="roleEyebrow" :title="roleTitle" :description="roleDescription" />
  <DataState :loading="loading" :error="error" @retry="load">
    <template #content>
      <section
        class="metric-grid dashboard-primary-metrics"
        :class="{ 'dashboard-primary-metrics--customer': isCustomer }"
      >
        <RouterLink class="metric-card metric-link" to="/work-items" aria-label="查看待办任务">
          <span><ListChecks :size="20" /></span>
          <div>
            <strong :class="metricClass(stats.todoCount)">{{ metricText(stats.todoCount) }}</strong
            ><small>{{ todoLabel }}</small>
          </div>
        </RouterLink>
        <RouterLink
          class="metric-card metric-link"
          to="/projects?scope=active"
          aria-label="查看进行中的项目列表"
        >
          <span><CircleGauge :size="20" /></span>
          <div>
            <strong :class="metricClass(stats.activeProjects)">{{
              metricText(stats.activeProjects)
            }}</strong
            ><small>进行中项目</small>
          </div>
        </RouterLink>
        <RouterLink
          v-if="isCustomer"
          class="metric-card metric-link"
          to="/work-items?scope=planConfirmation"
          aria-label="查看待客户确认的发布计划"
        >
          <span><FileCheck2 :size="20" /></span>
          <div>
            <strong :class="metricClass(stats.pendingPlanConfirmations)">{{
              metricText(stats.pendingPlanConfirmations)
            }}</strong
            ><small>待客户确认</small>
          </div>
        </RouterLink>
        <RouterLink
          class="metric-card metric-link"
          to="/orders?scope=pendingExecution"
          aria-label="查看四项服务待平台执行任务"
        >
          <span><ListChecks :size="20" /></span>
          <div>
            <strong :class="metricClass(stats.pendingPlatformExecutions)">{{
              metricText(stats.pendingPlatformExecutions)
            }}</strong
            ><small>待平台执行</small>
          </div>
        </RouterLink>
        <RouterLink class="metric-card metric-link" to="/orders" aria-label="查看全部任务记录">
          <span><RadioTower :size="20" /></span>
          <div>
            <strong :class="metricClass(stats.taskRecordCount)">{{
              metricText(stats.taskRecordCount)
            }}</strong
            ><small>任务记录</small>
          </div>
        </RouterLink>
        <RouterLink
          v-if="isAdmin"
          class="metric-card metric-link"
          to="/admin/inquiries?status=NEW"
          aria-label="查看待处理商务咨询"
        >
          <span><FileCheck2 :size="20" /></span>
          <div>
            <strong :class="metricClass(stats.pendingInquiryTickets)">{{
              metricText(stats.pendingInquiryTickets)
            }}</strong
            ><small>待处理咨询</small>
          </div>
        </RouterLink>
        <RouterLink
          v-else
          class="metric-card metric-link"
          to="/tasks?scope=awaitingAcceptance"
          aria-label="查看待验收成果任务列表"
        >
          <span><CheckCircle2 :size="20" /></span>
          <div>
            <strong :class="metricClass(stats.awaitingAcceptanceTasks)">{{
              metricText(stats.awaitingAcceptanceTasks)
            }}</strong
            ><small>{{ acceptanceLabel }}</small>
          </div>
        </RouterLink>
      </section>
      <section class="panel dashboard-task-summary">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">四项服务</span>
            <h2>服务概况</h2>
          </div>
          <RouterLink to="/orders">任务记录<ArrowRight :size="16" /></RouterLink>
        </div>
        <div class="metric-grid compact-metric-grid">
          <RouterLink class="metric-card metric-link" to="/tasks?channelType=MEDIA_PR">
            <span><RadioTower :size="20" /></span>
            <div>
              <strong :class="metricClass(stats.mediaInvitationTasks)">{{
                metricText(stats.mediaInvitationTasks)
              }}</strong>
              <small>邀请媒体</small>
            </div>
          </RouterLink>
          <RouterLink class="metric-card metric-link" to="/tasks?channelType=DIRECT_PUBLISHING">
            <span><FileCheck2 :size="20" /></span>
            <div>
              <strong :class="metricClass(stats.directPublishingTasks)">{{
                metricText(stats.directPublishingTasks)
              }}</strong>
              <small>直编发稿</small>
            </div>
          </RouterLink>
          <RouterLink
            class="metric-card metric-link"
            :to="
              auth.user?.role === 'CUSTOMER'
                ? '/order-management?serviceType=ONSITE_WRITING'
                : '/writing-assignments'
            "
          >
            <span><FileCheck2 :size="20" /></span>
            <div>
              <strong :class="metricClass(stats.writingAssignments)">{{
                metricText(stats.writingAssignments)
              }}</strong>
              <small>云采写</small>
            </div>
          </RouterLink>
          <RouterLink class="metric-card metric-link" to="/projects?serviceType=NEWS_CONFERENCE">
            <span><CircleGauge :size="20" /></span>
            <div>
              <strong :class="metricClass(stats.conferenceProjects)">{{
                metricText(stats.conferenceProjects)
              }}</strong>
              <small>举办新闻发布会</small>
            </div>
          </RouterLink>
        </div>
      </section>
      <section class="dashboard-grid">
        <article class="panel">
          <div class="panel-heading">
            <div>
              <span class="eyebrow">项目</span>
              <h2>最近更新</h2>
            </div>
            <RouterLink to="/projects">全部项目<ArrowRight :size="16" /></RouterLink>
          </div>
          <div v-if="projects.length" class="compact-list">
            <RouterLink
              v-for="project in projects"
              :key="project.id"
              :to="`/projects/${project.id}`"
              class="compact-row"
            >
              <span class="list-icon"><FileCheck2 :size="18" /></span>
              <div>
                <strong>{{ project.projectName }}</strong
                ><small>{{ project.projectNo }} · {{ project.taskCount }} 项发布任务</small>
              </div>
              <StatusTag :status="project.status" />
            </RouterLink>
          </div>
          <div v-else class="inline-empty">暂无项目记录</div>
        </article>
        <article class="panel">
          <div class="panel-heading">
            <div>
              <span class="eyebrow">任务</span>
              <h2>发布进度</h2>
            </div>
            <RouterLink to="/tasks">全部任务<ArrowRight :size="16" /></RouterLink>
          </div>
          <div v-if="tasks.length" class="compact-list">
            <RouterLink v-for="task in tasks" :key="task.taskNo" to="/tasks" class="compact-row">
              <span class="list-icon alt"><RadioTower :size="18" /></span>
              <div>
                <strong>{{ task.channelName }}</strong
                ><small>{{ task.projectName }}</small>
              </div>
              <StatusTag :status="task.status" />
            </RouterLink>
          </div>
          <div v-else class="inline-empty">暂无发布任务</div>
        </article>
      </section>
    </template>
  </DataState>
</template>
