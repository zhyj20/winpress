<script setup lang="ts">
import { ArrowRight, Plus, Search } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { type ServiceType } from '@/constants/services'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, PageResult, ProjectSummary } from '@/types'

const auth = useAuthStore()
const route = useRoute()
const items = ref<ProjectSummary[]>([])
const loading = ref(true)
const error = ref('')
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const status = ref('')
const serviceType = ref<ServiceType | ''>('')
const serviceTypes: { value: ServiceType; label: string }[] = [
  { value: 'ONSITE_WRITING', label: '云采写' },
  { value: 'MEDIA_PR', label: '媒体邀请' },
  { value: 'DIRECT_PUBLISHING', label: '直编发稿' },
  { value: 'NEWS_CONFERENCE', label: '举办新闻发布会' },
]

const isCustomer = computed(() => auth.user?.role === 'CUSTOMER')
const pageMeta = computed(() => {
  if (auth.user?.role === 'PLATFORM_ADMIN') {
    return {
      eyebrow: '平台运营',
      title: '项目管理',
      description: '统筹查看客户项目、服务进度、负责人和已核验的交付结果。',
    }
  }
  if (auth.user?.role === 'PUBLISH_OPERATOR') {
    return {
      eyebrow: '服务执行',
      title: '项目协同',
      description: '查看已参与项目的服务安排、当前进度和待处理事项。',
    }
  }
  return {
    eyebrow: '项目与进度',
    title: '项目管理',
    description: '集中查看每项服务的进度、待确认事项和已核验成果。',
  }
})

function routeText(value: unknown) {
  return typeof value === 'string' ? value : ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const scope = status.value === 'ACTIVE_PROJECTS' ? 'active' : ''
    const projectStatus = status.value === 'ACTIVE_PROJECTS' ? '' : status.value
    const { data } = await http.get<ApiResponse<PageResult<ProjectSummary>>>('/projects', {
      params: {
        page: page.value,
        pageSize: 12,
        keyword: keyword.value,
        status: projectStatus,
        scope,
        serviceType: serviceType.value || undefined,
      },
    })
    items.value = data.data.items
    total.value = data.data.total
  } catch (e) {
    error.value = apiError(e)
  } finally {
    loading.value = false
  }
}
function search() {
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

function applyRouteFilters() {
  keyword.value = routeText(route.query.keyword)
  status.value = route.query.scope === 'active' ? 'ACTIVE_PROJECTS' : routeText(route.query.status)
  const requestedServiceType = routeText(route.query.serviceType)
  serviceType.value = serviceTypes.some((item) => item.value === requestedServiceType)
    ? (requestedServiceType as ServiceType)
    : ''
  page.value = Number(route.query.page || 1) || 1
}

applyRouteFilters()
onMounted(load)
watch(
  () => route.fullPath,
  () => {
    applyRouteFilters()
    load()
  },
)
</script>

<template>
  <PageHeader
    :eyebrow="pageMeta.eyebrow"
    :title="pageMeta.title"
    :description="pageMeta.description"
  >
    <RouterLink v-if="auth.user?.role === 'CUSTOMER'" class="button primary" to="/requirements/new"
      ><Plus :size="17" />提交新需求</RouterLink
    >
  </PageHeader>
  <section class="panel filter-bar">
    <div class="input-icon search-input">
      <Search :size="17" /><input
        v-model="keyword"
        aria-label="搜索项目"
        placeholder="搜索项目名称或编号"
        @keyup.enter="search"
      />
    </div>
    <select v-model="status" aria-label="按项目状态筛选" @change="search">
      <option value="">全部状态</option>
      <option value="ACTIVE_PROJECTS">进行中的项目</option>
      <option value="PLANNING">待调度</option>
      <option value="CLIENT_REVIEW">待客户审核</option>
      <option value="PUBLISHING">发布中</option>
      <option value="MONITORING">监测中</option>
      <option value="COMPLETED">已完成</option>
    </select>
    <select v-model="serviceType" aria-label="按服务类型筛选" @change="search">
      <option value="">全部服务</option>
      <option v-for="item in serviceTypes" :key="item.value" :value="item.value">
        {{ item.label }}
      </option>
    </select>
    <button class="button secondary" type="button" @click="search">查询</button>
  </section>
  <section class="panel table-panel">
    <DataState
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-title="暂无匹配项目"
      empty-text="调整筛选条件后再试。"
      @retry="load"
    >
      <template #content>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>项目</th>
                <th v-if="!isCustomer">客户</th>
                <th>稿件</th>
                <th>任务 / 成果</th>
                <th v-if="auth.user?.role !== 'CUSTOMER'">负责人</th>
                <th>状态</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td>
                  <strong>{{ item.projectName }}</strong
                  ><small>{{ item.projectNo }}</small>
                </td>
                <td v-if="!isCustomer">{{ item.organizationName }}</td>
                <td><StatusTag :status="item.manuscriptStatus" /></td>
                <td>{{ item.taskCount }} / {{ item.resultCount }}</td>
                <td v-if="auth.user?.role !== 'CUSTOMER'">{{ item.operatorName || '待分配' }}</td>
                <td><StatusTag :status="item.status" /></td>
                <td>
                  <RouterLink class="icon-button" :to="`/projects/${item.id}`" aria-label="查看项目"
                    ><ArrowRight :size="18"
                  /></RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar :page="page" :page-size="12" :total="total" @change="changePage" />
      </template>
    </DataState>
  </section>
</template>
