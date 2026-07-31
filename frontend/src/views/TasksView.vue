<script setup lang="ts">
import { CheckCircle2, ExternalLink, Save, SlidersHorizontal, X } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import type { ApiResponse, PageResult, PublishTask } from '@/types'

const auth = useAuthStore()
const route = useRoute()
const toast = useToastStore()
const items = ref<PublishTask[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const submitting = ref(false)
const status = ref('')
const scope = ref('')
const channelType = ref('')
const active = ref<PublishTask | null>(null)
const updateForm = reactive({ status: 'IN_PROGRESS', executionNote: '', exceptionReason: '' })
const invitationForm = reactive({ status: 'INVITED', note: '' })
const resultForm = reactive({ title: '', url: '', publishedAt: '', note: '' })
const canOperate = computed(() => auth.user?.role !== 'CUSTOMER')
const canAcceptResults = computed(() => auth.user?.role === 'CUSTOMER')
const assignmentStatusLabel = computed(() =>
  auth.user?.role === 'CUSTOMER' ? '待平台安排' : '待分配',
)
const terminalTaskStatuses = new Set(['COMPLETED', 'CLIENT_ACCEPTED', 'NOT_PROCEEDING'])
const createdTaskNos = computed(
  () =>
    new Set(
      String(route.query.created || '')
        .split(',')
        .map((taskNo) => taskNo.trim())
        .filter(Boolean),
    ),
)
const pageTitle = computed(() => '媒体与发布任务')
const pageEyebrow = computed(
  () =>
    ({
      CUSTOMER: '项目执行',
      PUBLISH_OPERATOR: '服务执行',
      PLATFORM_ADMIN: '平台运营',
    })[auth.user?.role || 'CUSTOMER'],
)
const pageDescription = computed(
  () =>
    ({
      CUSTOMER: '查看媒体邀请与直编发稿的执行进度，验收已核验的发布成果。',
      PUBLISH_OPERATOR: '处理已分配的媒体邀请与直编发稿任务，并回填执行事实与成果。',
      PLATFORM_ADMIN: '查看发布任务、安排负责人、处理异常并核验成果。',
    })[auth.user?.role || 'CUSTOMER'],
)

function routeText(value: unknown) {
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

function needsAssignment(task: PublishTask) {
  return (
    auth.user?.role === 'PLATFORM_ADMIN' &&
    task.status === 'PENDING_ASSIGNMENT' &&
    !task.operatorName
  )
}

function canProcessTask(task: PublishTask) {
  return (
    canOperate.value &&
    typeof task.id === 'number' &&
    !terminalTaskStatuses.has(task.status) &&
    !needsAssignment(task)
  )
}

function invitationStatusLabel(value?: string) {
  return (
    {
      PENDING: '待发出邀请',
      INVITED: '已发出邀请',
      RESPONDED: '媒体已回复',
      DECLINED: '媒体婉拒',
      ATTENDING: '确认到场',
      REPORTED: '成果已核验',
      NOT_PROCEEDING: '不再继续',
    }[value || ''] || '待登记'
  )
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<PublishTask>>>('/publish-tasks', {
      params: {
        status: status.value,
        scope: scope.value,
        channelType: channelType.value,
        page: page.value,
        pageSize: 15,
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
function openTask(task: PublishTask) {
  if (!canProcessTask(task)) return
  active.value = task
  actionError.value = ''
  updateForm.status = task.status === 'PENDING_ASSIGNMENT' ? 'PENDING_EXECUTION' : task.status
  updateForm.executionNote = task.executionNote || ''
  updateForm.exceptionReason = task.exceptionReason || ''
  invitationForm.status =
    task.mediaInvitationStatus === 'PENDING' ? 'INVITED' : task.mediaInvitationStatus || 'INVITED'
  invitationForm.note = ''
  resultForm.title = task.manuscriptTitle || `${task.projectName}媒体邀请`
}
async function updateTask() {
  const taskId = active.value?.id
  if (typeof taskId !== 'number') return
  submitting.value = true
  actionError.value = ''
  try {
    await http.patch(`/operator/publish-tasks/${taskId}`, updateForm)
    toast.show('任务状态已更新', 'success')
    active.value = null
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    submitting.value = false
  }
}

async function updateMediaInvitation() {
  const taskId = active.value?.id
  if (typeof taskId !== 'number' || active.value?.channelType !== 'MEDIA_PR') return
  submitting.value = true
  actionError.value = ''
  try {
    await http.patch(`/operator/publish-tasks/${taskId}/media-invitation`, invitationForm)
    toast.show('媒体沟通状态已记录', 'success')
    active.value = null
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    submitting.value = false
  }
}
async function submitResult() {
  const taskId = active.value?.id
  if (typeof taskId !== 'number') return
  submitting.value = true
  actionError.value = ''
  try {
    await http.post(`/operator/publish-tasks/${taskId}/results`, {
      ...resultForm,
      publishedAt: resultForm.publishedAt || null,
    })
    toast.show('发布成果已回填', 'success')
    active.value = null
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    submitting.value = false
  }
}
async function accept(task: PublishTask) {
  submitting.value = true
  try {
    await http.post(`/publish-tasks/${encodeURIComponent(task.taskNo)}/accept`)
    toast.show('发布成果已验收', 'success')
    await load()
  } catch (e) {
    toast.show(apiError(e), 'error')
  } finally {
    submitting.value = false
  }
}

function applyFilters() {
  page.value = 1
  load()
}

function applyRouteFilters() {
  const requestedStatus = routeText(route.query.status)
  const requestedChannelType = routeText(route.query.channelType)
  status.value = [
    'PENDING_ASSIGNMENT',
    'PENDING_EXECUTION',
    'IN_PROGRESS',
    'NEEDS_INFO',
    'EXCEPTION',
    'COMPLETED',
    'CLIENT_ACCEPTED',
    'NOT_PROCEEDING',
  ].includes(requestedStatus)
    ? requestedStatus
    : ''
  scope.value = ['pending', 'withResults', 'awaitingAcceptance'].includes(
    routeText(route.query.scope),
  )
    ? routeText(route.query.scope)
    : ''
  channelType.value = ['MEDIA_PR', 'DIRECT_PUBLISHING'].includes(requestedChannelType)
    ? requestedChannelType
    : ''
  page.value = Math.max(1, Number(routeText(route.query.page)) || 1)
}

function changePage(next: number) {
  page.value = next
  load()
}

applyRouteFilters()
onMounted(load)
watch(
  () => route.fullPath,
  () => {
    applyRouteFilters()
    void load()
  },
)
</script>

<template>
  <PageHeader :eyebrow="pageEyebrow" :title="pageTitle" :description="pageDescription" />
  <section v-if="createdTaskNos.size" class="task-created-banner" aria-live="polite">
    <CheckCircle2 :size="19" />
    <div>
      <strong>发稿计划已提交</strong>
      <span>已生成 {{ createdTaskNos.size }} 项任务，后续进度和发布结果将在这里更新。</span>
    </div>
  </section>
  <section class="panel filter-bar">
    <span class="filter-title"><SlidersHorizontal :size="17" />筛选任务</span>
    <select v-model="scope" aria-label="按任务范围筛选" @change="applyFilters">
      <option value="">全部任务</option>
      <option value="pending">待处理任务</option>
      <option value="awaitingAcceptance">待客户验收</option>
      <option value="withResults">已回填成果</option>
    </select>
    <select v-model="channelType" aria-label="按渠道类型筛选任务" @change="applyFilters">
      <option value="">全部渠道</option>
      <option value="MEDIA_PR">媒体邀请</option>
      <option value="DIRECT_PUBLISHING">直编发稿</option>
    </select>
    <select v-model="status" aria-label="按任务状态筛选" @change="applyFilters">
      <option value="">全部状态</option>
      <option value="PENDING_ASSIGNMENT">{{ assignmentStatusLabel }}</option>
      <option value="PENDING_EXECUTION">待执行</option>
      <option value="IN_PROGRESS">执行中</option>
      <option value="NEEDS_INFO">需补充</option>
      <option value="EXCEPTION">异常</option>
      <option value="COMPLETED">已完成</option>
      <option value="CLIENT_ACCEPTED">客户已验收</option>
      <option value="NOT_PROCEEDING">暂不推进</option>
    </select>
  </section>
  <section class="panel table-panel">
    <DataState
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-title="暂无匹配任务"
      empty-text="调整渠道或状态筛选后再试。"
      @retry="load"
    >
      <template #content
        ><div class="table-wrap">
          <table class="publish-task-table" :class="{ 'publish-task-table-operator': canOperate }">
            <thead>
              <tr>
                <th>任务</th>
                <th>项目</th>
                <th>渠道</th>
                <th v-if="canOperate">负责人</th>
                <th>状态</th>
                <th>更新时间</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="task in items"
                :key="task.taskNo"
                :class="{ 'new-task-row': createdTaskNos.has(task.taskNo) }"
              >
                <td class="task-cell">
                  <strong>{{ task.taskNo }}</strong
                  ><small>{{ task.manuscriptTitle || '活动媒体邀请' }}</small>
                </td>
                <td class="project-cell" data-label="项目">
                  <RouterLink :to="`/projects/${task.projectId}`">{{
                    task.projectName
                  }}</RouterLink>
                </td>
                <td class="channel-cell" data-label="渠道">
                  {{ task.channelName
                  }}<small>{{
                    task.channelType === 'MEDIA_PR'
                      ? '媒体邀请'
                      : task.channelType === 'DIRECT_PUBLISHING'
                        ? '直编发稿'
                        : '历史渠道记录'
                  }}</small>
                </td>
                <td v-if="canOperate" class="owner-cell" data-label="负责人">
                  {{ task.operatorName || '待分配' }}
                </td>
                <td class="status-cell">
                  <StatusTag :status="task.status" />
                  <small v-if="task.channelType === 'MEDIA_PR' && task.mediaInvitationStatus">
                    邀约：{{ invitationStatusLabel(task.mediaInvitationStatus) }}
                  </small>
                </td>
                <td class="updated-cell" data-label="更新时间">
                  {{ new Date(task.updatedAt).toLocaleString('zh-CN') }}
                </td>
                <td class="action-cell">
                  <RouterLink
                    v-if="needsAssignment(task)"
                    class="button text-button"
                    :to="`/projects/${task.projectId}`"
                  >
                    安排负责人</RouterLink
                  ><button
                    v-else-if="canProcessTask(task)"
                    class="button text-button"
                    type="button"
                    @click="openTask(task)"
                  >
                    处理</button
                  ><button
                    v-else-if="canAcceptResults && task.status === 'COMPLETED'"
                    class="button text-button"
                    type="button"
                    :disabled="submitting"
                    @click="accept(task)"
                  >
                    确认验收</button
                  ><RouterLink
                    v-else
                    class="icon-button"
                    :to="`/projects/${task.projectId}`"
                    aria-label="查看项目"
                    ><ExternalLink :size="17"
                  /></RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar :page="page" :page-size="15" :total="total" @change="changePage"
      /></template>
    </DataState>
  </section>

  <div v-if="active" class="modal-backdrop" @click.self="active = null">
    <section
      class="modal-panel"
      role="dialog"
      aria-modal="true"
      aria-labelledby="task-dialog-title"
    >
      <header>
        <div>
          <span class="eyebrow">{{ active.taskNo }}</span>
          <h2 id="task-dialog-title">{{ active.channelName }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="active = null">
          <X :size="19" />
        </button>
      </header>
      <div class="modal-columns">
        <form @submit.prevent="updateTask">
          <h3>更新执行状态</h3>
          <label
            >任务状态<select v-model="updateForm.status" required>
              <option value="PENDING_EXECUTION">待执行</option>
              <option value="IN_PROGRESS">执行中</option>
              <option value="NEEDS_INFO">需补充</option>
              <option value="EXCEPTION">异常</option>
            </select></label
          ><label>执行说明<textarea v-model="updateForm.executionNote" rows="4"></textarea></label
          ><label v-if="updateForm.status === 'EXCEPTION'"
            >异常原因<span class="required">*</span
            ><textarea v-model="updateForm.exceptionReason" required rows="3"></textarea></label
          ><button class="button secondary" type="submit" :disabled="submitting">
            <Save :size="17" />保存状态
          </button>
        </form>
        <form v-if="active.channelType === 'MEDIA_PR'" @submit.prevent="updateMediaInvitation">
          <h3>记录媒体沟通</h3>
          <p class="form-hint">
            仅在实际发出邀请或收到回复后更新；媒体是否采访或报道仍由媒体自主决定。
          </p>
          <p v-if="active.mediaInvitationStatus" class="form-hint">
            当前：{{ invitationStatusLabel(active.mediaInvitationStatus) }}
          </p>
          <label
            >沟通状态<select v-model="invitationForm.status" required>
              <option value="INVITED">已发出邀请</option>
              <option value="RESPONDED">媒体已回复</option>
              <option value="ATTENDING">确认到场</option>
              <option value="DECLINED">媒体婉拒</option>
              <option value="NOT_PROCEEDING">不再继续</option>
            </select></label
          ><label
            >沟通说明<span class="required">*</span
            ><textarea
              v-model="invitationForm.note"
              required
              rows="3"
              placeholder="简要记录本次实际沟通情况"
            ></textarea></label
          ><button class="button secondary" type="submit" :disabled="submitting">
            <Save :size="17" />记录沟通
          </button>
        </form>
        <form @submit.prevent="submitResult">
          <h3>回填发布成果</h3>
          <label
            >成果标题<span class="required">*</span
            ><input v-model="resultForm.title" required maxlength="240" /></label
          ><label
            >成果链接<span class="required">*</span
            ><input v-model="resultForm.url" required type="url" placeholder="https://" /></label
          ><label>发布时间<input v-model="resultForm.publishedAt" type="datetime-local" /></label
          ><label>核验说明<textarea v-model="resultForm.note" rows="2"></textarea></label
          ><button class="button primary" type="submit" :disabled="submitting">
            <CheckCircle2 :size="17" />提交成果
          </button>
        </form>
      </div>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
    </section>
  </div>
</template>

<style scoped>
.publish-task-table {
  table-layout: fixed;
}

.publish-task-table th:nth-child(1) {
  width: 20%;
}

.publish-task-table th:nth-child(2) {
  width: 24%;
}

.publish-task-table th:nth-child(3) {
  width: 20%;
}

.publish-task-table th:nth-child(4) {
  width: 14%;
}

.publish-task-table th:nth-child(5) {
  width: 16%;
}

.publish-task-table th:last-child {
  width: 58px;
}

.publish-task-table-operator th:nth-child(1) {
  width: 18%;
}

.publish-task-table-operator th:nth-child(2) {
  width: 21%;
}

.publish-task-table-operator th:nth-child(3) {
  width: 17%;
}

.publish-task-table-operator th:nth-child(4) {
  width: 10%;
}

.publish-task-table-operator th:nth-child(5) {
  width: 12%;
}

.publish-task-table-operator th:nth-child(6) {
  width: 13%;
}

.publish-task-table-operator th:nth-child(7) {
  width: 9%;
}

.publish-task-table-operator .action-cell .button {
  min-width: 80px;
  white-space: nowrap;
}

.publish-task-table td:nth-child(-n + 3) {
  white-space: normal;
  overflow-wrap: anywhere;
}

.publish-task-table td:nth-last-child(2) {
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.status-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
}

.status-cell small {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.35;
}

@media (max-width: 760px) {
  .publish-task-table,
  .publish-task-table-operator {
    min-width: 0;
    display: block;
  }

  .publish-task-table thead {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
  }

  .publish-task-table tbody {
    display: grid;
    gap: 12px;
    padding: 12px;
  }

  .publish-task-table tbody tr {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 0 14px;
    padding: 14px;
    border: 1px solid #e4e9f0;
    border-radius: 12px;
    background: #fff;
  }

  .publish-task-table tbody td {
    min-height: 0;
    padding: 7px 0;
    border: 0;
    white-space: normal;
  }

  .publish-task-table .task-cell {
    grid-column: 1;
    grid-row: 1;
    padding-top: 2px;
  }

  .publish-task-table .status-cell {
    grid-column: 2;
    grid-row: 1;
    align-self: start;
    justify-self: end;
    padding-top: 0;
    align-items: flex-end;
    text-align: right;
  }

  .publish-task-table .project-cell,
  .publish-task-table .channel-cell,
  .publish-task-table .owner-cell {
    grid-column: 1 / -1;
  }

  .publish-task-table .updated-cell {
    grid-column: 1;
    align-self: end;
  }

  .publish-task-table .action-cell {
    grid-column: 2;
    align-self: end;
    justify-self: end;
    padding-bottom: 0;
  }

  .publish-task-table .project-cell::before,
  .publish-task-table .channel-cell::before,
  .publish-task-table .owner-cell::before,
  .publish-task-table .updated-cell::before {
    content: attr(data-label);
    display: block;
    margin-bottom: 3px;
    color: #7a8493;
    font-size: 11px;
    font-weight: 700;
  }

  .publish-task-table td:nth-last-child(2) {
    font-size: 12px;
  }

  .publish-task-table .project-cell a {
    min-height: 44px;
    display: inline-flex;
    align-items: center;
  }

  .publish-task-table .action-cell .button {
    min-height: 44px;
  }
}
</style>
