<script setup lang="ts">
import { ArrowRight } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import http, { apiError } from '@/api/http'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, PageResult, TaskRecord } from '@/types'

const auth = useAuthStore()
const route = useRoute()
const records = ref<TaskRecord[]>([])
const taskTotal = ref(0)
const taskPage = ref(1)
const taskPageSize = 20

const loadingTasks = ref(true)
const error = ref('')
const isCustomer = computed(() => auth.user?.role === 'CUSTOMER')
const pendingExecutionScope = computed(() => route.query.scope === 'pendingExecution')
const pageMeta = computed(() => {
  if (pendingExecutionScope.value) {
    if (auth.user?.role === 'PLATFORM_ADMIN') {
      return {
        eyebrow: '平台执行',
        title: '待平台执行',
        description: '查看平台范围内尚待推进的四项服务执行任务。',
      }
    }
    if (auth.user?.role === 'PUBLISH_OPERATOR') {
      return {
        eyebrow: '服务执行',
        title: '待平台执行',
        description: '查看当前分配给您的四项服务执行任务。',
      }
    }
    return {
      eyebrow: '项目执行',
      title: '待平台执行',
      description: '查看四项服务中正在由平台或服务人员推进的任务。',
    }
  }
  if (auth.user?.role === 'PLATFORM_ADMIN') {
    return {
      eyebrow: '平台运营',
      title: '任务记录',
      description: '查看平台范围内的项目任务、执行状态、负责人和处理记录。',
    }
  }
  if (auth.user?.role === 'PUBLISH_OPERATOR') {
    return {
      eyebrow: '服务执行',
      title: '任务记录',
      description: '查看您参与项目的任务、计划节点和处理记录。',
    }
  }
  return {
    eyebrow: '项目执行',
    title: '任务记录',
    description: '按时间查看四项服务的任务、状态和已公开的处理说明。',
  }
})

function formatDate(value?: string) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString('zh-CN')
}

async function loadTasks() {
  const { data } = await http.get<ApiResponse<PageResult<TaskRecord>>>('/task-records', {
    params: {
      scope: pendingExecutionScope.value ? 'pendingExecution' : undefined,
      page: taskPage.value,
      pageSize: taskPageSize,
    },
  })
  records.value = data.data.items
  taskTotal.value = data.data.total
}

async function load() {
  loadingTasks.value = true
  error.value = ''
  try {
    await loadTasks()
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    loadingTasks.value = false
  }
}

function changeTaskPage(next: number) {
  taskPage.value = next
  loadTasks()
}

onMounted(load)
watch(
  () => route.query.scope,
  () => {
    taskPage.value = 1
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
    <RouterLink v-if="pendingExecutionScope" class="button secondary" to="/orders">
      全部任务记录
    </RouterLink>
    <RouterLink v-else-if="isCustomer" class="button primary" to="/requirements/new">
      提交服务需求
    </RouterLink>
  </PageHeader>

  <p v-if="error" class="form-error">{{ error }}</p>

  <section class="panel table-panel">
    <div class="panel-heading">
      <div>
        <span class="eyebrow">全部任务</span>
        <h2>{{ pendingExecutionScope ? '待平台执行' : '任务明细' }}</h2>
      </div>
      <div class="task-record-actions">
        <RouterLink v-if="!pendingExecutionScope" class="button secondary" to="/projects">
          项目管理
        </RouterLink>
        <RouterLink class="button secondary" to="/work-items">查看当前待办</RouterLink>
      </div>
    </div>
    <div v-if="loadingTasks" class="order-hub-empty">正在加载任务记录...</div>
    <div v-else-if="records.length" class="table-wrap">
      <table class="task-record-table" :class="{ 'task-record-table-internal': !isCustomer }">
        <thead>
          <tr>
            <th>任务</th>
            <th>项目与内容</th>
            <th>状态</th>
            <th v-if="!isCustomer">负责人</th>
            <th>时间</th>
            <th>处理说明</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in records" :key="item.recordNo">
            <td>
              <strong>{{ item.recordNo }}</strong>
              <small>{{ item.itemLabel }}</small>
            </td>
            <td class="task-record-project">
              <RouterLink :to="`/projects/${item.projectId}`">{{ item.projectName }}</RouterLink>
              <small>{{ item.title }}</small>
            </td>
            <td><StatusTag :status="item.status" /></td>
            <td v-if="!isCustomer">{{ item.ownerName || '待分配' }}</td>
            <td class="task-record-time">
              <span><b>计划</b>{{ formatDate(item.dueAt) }}</span>
              <span v-if="item.completedAt"><b>完成</b>{{ formatDate(item.completedAt) }}</span>
              <span><b>更新</b>{{ formatDate(item.updatedAt) }}</span>
            </td>
            <td class="task-record-note">{{ item.note || '—' }}</td>
            <td>
              <RouterLink
                class="icon-button"
                :to="`/projects/${item.projectId}`"
                aria-label="查看项目"
              >
                <ArrowRight :size="17" />
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="muted">
      {{ pendingExecutionScope ? '当前没有待平台执行的任务。' : '暂无任务记录。' }}
    </p>
    <PaginationBar
      v-if="!loadingTasks && taskTotal > taskPageSize"
      :page="taskPage"
      :page-size="taskPageSize"
      :total="taskTotal"
      @change="changeTaskPage"
    />
  </section>
</template>

<style scoped>
.task-record-table {
  min-width: 900px;
  table-layout: fixed;
}

.task-record-table-internal {
  min-width: 1020px;
}

.task-record-table th:nth-child(1) {
  width: 154px;
}

.task-record-table th:nth-child(2) {
  width: 250px;
}

.task-record-table td:nth-child(1),
.task-record-table td:nth-child(2),
.task-record-note {
  white-space: normal;
  overflow-wrap: anywhere;
}

.task-record-time {
  width: 166px;
  white-space: normal;
}

.task-record-time span {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 6px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.55;
}

.task-record-time b {
  color: #384152;
  font-weight: 750;
}

.task-record-note {
  width: 158px;
  line-height: 1.55;
}

.task-record-project small {
  line-height: 1.45;
}

.task-record-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
</style>
