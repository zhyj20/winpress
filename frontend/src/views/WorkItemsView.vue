<script setup lang="ts">
import {
  ArrowRight,
  ClipboardCheck,
  FileCheck2,
  FilePenLine,
  MessageSquareText,
  RadioTower,
  ReceiptText,
} from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, PageResult, WorkItem } from '@/types'

const items = ref<WorkItem[]>([])
const auth = useAuthStore()
const route = useRoute()
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(true)
const error = ref('')
const planConfirmationScope = computed(() => route.query.scope === 'planConfirmation')

const pageMeta = computed(() => {
  if (auth.user?.role === 'CUSTOMER' && planConfirmationScope.value) {
    return {
      eyebrow: '待客户确认',
      title: '待客户确认',
      description: '核对已保存的媒体邀请或发稿计划；提交项目核验后，系统才会建立平台任务。',
    }
  }
  if (auth.user?.role === 'PLATFORM_ADMIN') {
    return {
      eyebrow: '平台运营',
      title: '当前待办',
      description: '集中处理项目调度、服务异常和待联系事项。',
    }
  }
  if (auth.user?.role === 'PUBLISH_OPERATOR') {
    return {
      eyebrow: '服务执行',
      title: '当前待办',
      description: '集中处理已分配的采写和发布事项，并及时回填进度。',
    }
  }
  return {
    eyebrow: '待办事项',
    title: '待办事项',
    description: '处理需要您补充资料、审核稿件或验收成果的项目事项。',
  }
})

function itemIcon(item: WorkItem) {
  if (item.itemType === 'BUSINESS_INQUIRY') return MessageSquareText
  if (item.itemLabel === '发布计划确认') return ReceiptText
  if (item.itemLabel === '稿件审核' || item.itemLabel === '成果验收') return FileCheck2
  if (item.itemLabel === '资料补充') return MessageSquareText
  if (item.itemLabel === '云采写') return FilePenLine
  if (item.itemLabel === '新闻发布会') return ClipboardCheck
  if (item.itemLabel === '媒体邀请' || item.itemLabel === '直编发稿') return ReceiptText
  return RadioTower
}

function target(item: WorkItem) {
  if (item.itemType === 'BUSINESS_INQUIRY') return '/admin/inquiries?status=NEW'
  if (auth.user?.role === 'CUSTOMER' && item.itemLabel === '发布计划确认') {
    return `/projects/${item.projectId}#publish-plans`
  }
  if (
    auth.user?.role === 'CUSTOMER' &&
    ['成果验收', '媒体邀请', '直编发稿'].includes(item.itemLabel)
  ) {
    const scope = item.itemLabel === '成果验收' ? '?scope=awaitingAcceptance' : ''
    return `/tasks${scope}`
  }
  return `/projects/${item.projectId}`
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('zh-CN')
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<WorkItem>>>('/work-items', {
      params: {
        page: page.value,
        pageSize,
        scope: planConfirmationScope.value ? 'planConfirmation' : undefined,
      },
    })
    items.value = data.data.items
    total.value = data.data.total
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    loading.value = false
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

onMounted(load)

watch(
  () => route.query.scope,
  () => {
    page.value = 1
    void load()
  },
)
</script>

<template>
  <PageHeader
    :eyebrow="pageMeta.eyebrow"
    :title="pageMeta.title"
    :description="pageMeta.description"
  />

  <DataState
    :loading="loading"
    :error="error"
    :empty="!items.length"
    :empty-title="planConfirmationScope ? '当前没有待确认的发布计划' : '当前没有待办事项'"
    :empty-text="
      planConfirmationScope
        ? '保存计划后，您可以在这里再次核对并提交项目核验。'
        : auth.user?.role === 'CUSTOMER'
          ? '需要您审核、补充或验收的事项会显示在这里。'
          : '新的项目任务、发布会清单和云采写安排会在这里出现。'
    "
    @retry="load"
  >
    <template #content>
      <section class="panel table-panel">
        <div class="table-wrap">
          <table class="work-items-table">
            <thead>
              <tr>
                <th>事项</th>
                <th>项目</th>
                <th>状态</th>
                <th>更新时间</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.recordNo">
                <td>
                  <span class="work-item-title">
                    <component :is="itemIcon(item)" :size="17" />
                    <span
                      ><strong>{{ item.title }}</strong
                      ><small>{{ item.itemLabel }}</small></span
                    >
                  </span>
                </td>
                <td class="work-item-project" :title="item.projectName || '商务工作台'">
                  {{ item.projectName || '商务工作台' }}
                </td>
                <td><StatusTag :status="item.status" /></td>
                <td class="work-item-updated">{{ formatDate(item.updatedAt) }}</td>
                <td>
                  <RouterLink class="icon-button" :to="target(item)" aria-label="打开事项">
                    <ArrowRight :size="17" />
                  </RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar :page="page" :page-size="pageSize" :total="total" @change="changePage" />
      </section>
    </template>
  </DataState>
</template>

<style scoped>
.work-item-title {
  min-width: 0;
  display: inline-flex;
  align-items: flex-start;
  gap: 10px;
}

.work-item-title svg {
  margin-top: 2px;
  color: var(--primary);
}

.work-item-title span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.work-item-title small {
  color: var(--muted);
}

.work-items-table {
  table-layout: fixed;
}

.work-items-table th:nth-child(1) {
  width: 33%;
}

.work-items-table th:nth-child(2) {
  width: 29%;
}

.work-items-table th:nth-child(3) {
  width: 14%;
}

.work-items-table th:nth-child(4) {
  width: 18%;
}

.work-items-table th:nth-child(5) {
  width: 52px;
}

.work-items-table td:first-child {
  white-space: normal;
}

.work-item-project {
  overflow: hidden;
  text-overflow: ellipsis;
}

.work-item-updated {
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 760px) {
  .work-items-table {
    min-width: 700px;
  }
}
</style>
