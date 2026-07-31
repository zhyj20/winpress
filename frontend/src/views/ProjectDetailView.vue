<script setup lang="ts">
import {
  ArrowLeft,
  Check,
  Download,
  ExternalLink,
  FilePenLine,
  Paperclip,
  RadioTower,
  Send,
  Upload,
  UserRoundCheck,
  X,
} from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import MediaDiscoveryPanel from '@/components/MediaDiscoveryPanel.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { SERVICE_LABELS, isServiceType, type ServiceType } from '@/constants/services'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import type { ApiResponse, MediaCandidate, PublishTask } from '@/types'
import { buildConferenceCalendar } from '@/utils/calendar'

interface ProjectInfo {
  id: number
  projectNo: string
  projectName: string
  status: string
  activityRootProjectId?: number
  budget?: number
  customerName: string
  organizationName: string
  operatorName?: string
  requirementNo: string
  facts: string
  objective: string
  requestedService: string
  eventTime?: string
  eventLocation?: string
  serviceDays?: number
  writerCount?: number
  unitPrice?: number
  estimatedAmount?: number
  onsiteContactName?: string
  onsiteContactMobile?: string
  deliverableRequirement?: string
  matchingPreference?: string
}
interface Manuscript {
  id: number
  manuscriptNo: string
  title: string
  status: string
  currentVersionNo: number
  approvedVersionId?: number
}
interface Version {
  id: number
  manuscriptId: number
  versionNo: string
  versionNumber: number
  title: string
  summary?: string
  content: string
  changeNote?: string
  reviewComment?: string
  sourceProjectName?: string
  sourceManuscriptTitle?: string
  status: string
  createdAt: string
}
interface ResultLink {
  resultNo: string
  channelName: string
  title: string
  url: string
  publishedAt?: string
  status: string
}
interface Monitoring {
  monitoringNo: string
  monitoredAt: string
  metricName: string
  metricText?: string
  status: string
}
interface FileAsset {
  fileNo: string
  originalName: string
  contentType: string
  fileSize: number
  createdAt: string
}
interface ConferencePlan {
  conferenceNo?: string
  conferenceType?: string
  conferenceFormat?: string
  theme?: string
  eventTime?: string
  eventLocation?: string
  conferenceScale?: string
  mediaGoal?: string
  guestPlan?: string
  agendaPlan?: string
  venuePlan?: string
  mediaDirection?: string
  communicationGoal?: string
  agendaStatus?: string
  venueStatus?: string
  contactName?: string
  contactMobile?: string
  status?: string
}
interface ConferenceWorkItem {
  id: number
  itemNo: string
  sortOrder: number
  phase: 'PRE_EVENT' | 'ONSITE' | 'POST_EVENT'
  title: string
  detail?: string
  dueAt?: string
  operatorName?: string
  assignedOperatorId?: number
  status: string
  note?: string
}
interface ConferenceMediaCandidate {
  id: number
  candidateKey?: string
  candidateType: 'MEDIA' | 'REPORTER' | 'MANUAL'
  displayName: string
  reporterName?: string
  attribute?: string
  province?: string
  city?: string
  channelForm?: string
  category?: string
  coverageTags?: string
  score?: number
  newsCount?: number
  fansCount?: number
  logoUrl?: string
  avatarUrl?: string
  status: string
  note?: string
  operatorName?: string
  selectedAt?: string
  invitedAt?: string
  respondedAt?: string
}
interface ServiceIntakeTask {
  taskNo: string
  serviceType: 'MEDIA_PR' | 'DIRECT_PUBLISHING'
  title: string
  customerVisibleNote?: string
  status: string
  completedAt?: string
  updatedAt: string
}
interface PublishPlanSummary {
  planNo: string
  projectId: number
  planName: string
  estimatedAmount?: number | null
  currency: string
  status: string
  confirmedAt?: string
  createdAt: string
  itemCount: number
}
interface ActivityProject {
  projectId: number
  projectNo: string
  projectName: string
  requestedService: string
  status: string
  eventTime?: string
  unitPrice?: number
  estimatedAmount?: number
  createdAt: string
}
interface Detail {
  project: ProjectInfo
  manuscripts: Manuscript[]
  versions: Version[]
  tasks: PublishTask[]
  results: ResultLink[]
  monitoring: Monitoring[]
  settlements: Record<string, unknown>[]
  files: FileAsset[]
  conference?: ConferencePlan
  conferenceWorkItems: ConferenceWorkItem[]
  conferenceMediaCandidates: ConferenceMediaCandidate[]
  serviceIntakeTasks: ServiceIntakeTask[]
  activityProjects: ActivityProject[]
}

const route = useRoute()
const auth = useAuthStore()
const toast = useToastStore()
const projectId = Number(route.params.id)
const detail = ref<Detail | null>(null)
const publishPlans = ref<PublishPlanSummary[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const submitting = ref(false)
const confirmingPlanNo = ref('')
const showEditor = ref(false)
const operators = ref<{ id: number; displayName: string }[]>([])
const selectedOperator = ref<number | ''>('')
const reviewComment = ref('')
const manuscriptForm = reactive({ title: '', summary: '', content: '', changeNote: '' })
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const downloadingFileNo = ref('')
const conferenceSaving = ref(false)
const conferenceWorkspaceTab = ref<'OVERVIEW' | 'MEDIA' | 'WORK'>('OVERVIEW')
const conferenceForm = reactive({
  theme: '',
  eventTime: '',
  eventLocation: '',
  conferenceType: '',
  conferenceFormat: '',
  conferenceScale: '',
  mediaGoal: '',
  guestPlan: '',
  agendaPlan: '',
  venuePlan: '',
  mediaDirection: '',
  communicationGoal: '',
  agendaStatus: 'PREPARING',
  venueStatus: 'PENDING',
  contactName: '',
  contactMobile: '',
})
const workItemDrafts = reactive<
  Record<
    number,
    {
      status: string
      expectedStatus: string
      note: string
      dueAt: string
      assignedOperatorId: number | ''
    }
  >
>({})
const conferenceCandidateDrafts = reactive<Record<number, { status: string; note: string }>>({})

const conferenceCandidateTransitions: Record<string, Array<{ value: string; label: string }>> = {
  CANDIDATE: [
    { value: 'CANDIDATE', label: '候选' },
    { value: 'READY_TO_INVITE', label: '待邀约确认' },
    { value: 'NOT_PROCEEDING', label: '暂不推进' },
  ],
  READY_TO_INVITE: [
    { value: 'READY_TO_INVITE', label: '待邀约确认' },
    { value: 'INVITED', label: '已发出邀请' },
    { value: 'NOT_PROCEEDING', label: '暂不推进' },
  ],
  INVITED: [
    { value: 'INVITED', label: '已发出邀请' },
    { value: 'RESPONDED', label: '已回复' },
    { value: 'ATTENDING', label: '确认到场' },
    { value: 'DECLINED', label: '婉拒' },
    { value: 'NOT_PROCEEDING', label: '暂不推进' },
  ],
  RESPONDED: [
    { value: 'RESPONDED', label: '已回复' },
    { value: 'ATTENDING', label: '确认到场' },
    { value: 'DECLINED', label: '婉拒' },
    { value: 'NOT_PROCEEDING', label: '暂不推进' },
  ],
}

function isConferenceCandidateFinal(status: string) {
  return ['DECLINED', 'ATTENDING', 'NOT_PROCEEDING'].includes(status)
}

function conferenceCandidateStatusOptions(status: string) {
  return conferenceCandidateTransitions[status] || [{ value: status, label: status }]
}

const manuscript = computed(() => detail.value?.manuscripts[0])
const reviewVersion = computed(() =>
  detail.value?.versions.find((item) => item.status === 'CLIENT_REVIEW'),
)
const approvedVersion = computed(() =>
  detail.value?.versions.find((item) => item.id === manuscript.value?.approvedVersionId),
)
const isConferenceProject = computed(() => Boolean(detail.value?.conference?.conferenceNo))
const hasConferenceScheduleDates = computed(
  () =>
    Boolean(detail.value?.conference?.eventTime) ||
    Boolean(detail.value?.conferenceWorkItems.some((item) => Boolean(item.dueAt))),
)
const canOperateConference = computed(() =>
  ['PUBLISH_OPERATOR', 'PLATFORM_ADMIN'].includes(auth.user?.role || ''),
)
const canSubmitCustomerManuscript = computed(
  () =>
    auth.user?.role === 'CUSTOMER' &&
    detail.value?.project.requestedService === 'DIRECT_PUBLISHING',
)
const canSubmitManuscript = computed(
  () =>
    canSubmitCustomerManuscript.value ||
    (['PUBLISH_OPERATOR', 'PLATFORM_ADMIN'].includes(auth.user?.role || '') &&
      detail.value?.project.requestedService === 'ONSITE_WRITING' &&
      !['CLIENT_REVIEW', 'CLIENT_APPROVED'].includes(manuscript.value?.status || '')),
)
const canCreateRelatedServices = computed(
  () =>
    auth.user?.role === 'CUSTOMER' && isServiceType(detail.value?.project.requestedService || ''),
)
const activityProjects = computed(() => detail.value?.activityProjects || [])
const conferenceCandidateKeys = computed(() =>
  canOperateConference.value
    ? detail.value?.conferenceMediaCandidates
        .map((item) => item.candidateKey)
        .filter((key): key is string => Boolean(key)) || []
    : [],
)
const conferenceWorkProgress = computed(() => {
  const items = detail.value?.conferenceWorkItems || []
  return {
    completed: items.filter((item) => item.status === 'COMPLETED').length,
    total: items.length,
  }
})
const conferencePhases = computed(() => [
  {
    key: 'PRE_EVENT',
    label: '会前准备',
    items: detail.value?.conferenceWorkItems.filter((item) => item.phase === 'PRE_EVENT') || [],
  },
  {
    key: 'ONSITE',
    label: '现场执行',
    items: detail.value?.conferenceWorkItems.filter((item) => item.phase === 'ONSITE') || [],
  },
  {
    key: 'POST_EVENT',
    label: '会后传播',
    items: detail.value?.conferenceWorkItems.filter((item) => item.phase === 'POST_EVENT') || [],
  },
])
const conferenceWorkItemStatusOptions = (status: string) => {
  const labels: Record<string, string> = {
    PENDING: '待处理',
    IN_PROGRESS: '进行中',
    NEEDS_INFO: '需补充',
    BLOCKED: '受阻',
    COMPLETED: '已完成',
  }
  const transitions: Record<string, string[]> = {
    PENDING: ['PENDING', 'IN_PROGRESS', 'NEEDS_INFO', 'BLOCKED', 'COMPLETED'],
    IN_PROGRESS: ['IN_PROGRESS', 'NEEDS_INFO', 'BLOCKED', 'COMPLETED'],
    NEEDS_INFO: ['NEEDS_INFO', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED'],
    BLOCKED: ['BLOCKED', 'IN_PROGRESS', 'NEEDS_INFO', 'COMPLETED'],
  }
  return (transitions[status] || []).map((value) => ({ value, label: labels[value] }))
}
const legacyServiceLabels: Record<string, string> = {
  WRITING: '内容撰稿',
  WRITING_AND_PUBLISHING: '历史组合记录（仅归档）',
  PUBLISHING: '已有稿件',
}

function serviceLabel(service: string): string {
  return SERVICE_LABELS[service as ServiceType] || legacyServiceLabels[service] || service
}

function relatedServiceOrderPath(service: ServiceType) {
  const relation = `relatedProjectId=${projectId}`
  if (service === 'ONSITE_WRITING') return `/requirements/cloud-writing?${relation}`
  if (service === 'NEWS_CONFERENCE') return `/requirements/news-conference?${relation}`
  if (service === 'MEDIA_PR') {
    return `/requirements/new?service=MEDIA_PR&returnTo=media-invitation&${relation}`
  }
  return `/requirements/new?service=DIRECT_PUBLISHING&${relation}`
}

function localDateTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

function exportConferenceCalendar() {
  if (!detail.value) return
  const calendar = buildConferenceCalendar({
    projectNo: detail.value.project.projectNo,
    projectName: detail.value.project.projectName,
    conference: detail.value.conference,
    workItems: detail.value.conferenceWorkItems,
  })
  if (!calendar.eventCount) {
    toast.show('请先补充举办时间或执行事项截止时间，再导出项目日程。', 'error')
    return
  }
  const blob = new Blob([`\ufeff${calendar.content}`], {
    type: 'text/calendar;charset=utf-8',
  })
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = calendar.filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(objectUrl)
  toast.show(`已导出 ${calendar.eventCount} 项项目日程`, 'success')
}

function displayDateTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN') : ''
}

function conferenceCandidateRowKey(item: ConferenceMediaCandidate) {
  return (
    item.id ||
    item.candidateKey ||
    `${item.displayName}|${item.reporterName || ''}|${item.selectedAt || ''}`
  )
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [projectResponse, plansResponse] = await Promise.all([
      http.get<ApiResponse<Detail>>(`/projects/${projectId}`),
      http.get<ApiResponse<PublishPlanSummary[]>>(`/projects/${projectId}/publish-plans`),
    ])
    detail.value = projectResponse.data.data
    publishPlans.value = plansResponse.data.data
    if (detail.value.conference) {
      const conference = detail.value.conference
      Object.assign(conferenceForm, {
        theme: conference.theme || detail.value.project.projectName || '',
        eventTime: localDateTime(conference.eventTime || detail.value.project.eventTime),
        eventLocation: conference.eventLocation || detail.value.project.eventLocation || '',
        conferenceType: conference.conferenceType || '',
        conferenceFormat: conference.conferenceFormat || '',
        conferenceScale: conference.conferenceScale || '',
        mediaGoal: conference.mediaGoal || '',
        guestPlan: conference.guestPlan || '',
        agendaPlan: conference.agendaPlan || '',
        venuePlan: conference.venuePlan || '',
        mediaDirection: conference.mediaDirection || '',
        communicationGoal: conference.communicationGoal || '',
        agendaStatus: conference.agendaStatus || 'PREPARING',
        venueStatus: conference.venueStatus || 'PENDING',
        contactName: conference.contactName || '',
        contactMobile: conference.contactMobile || '',
      })
      if (canOperateConference.value) {
        for (const item of detail.value.conferenceWorkItems) {
          workItemDrafts[item.id] = {
            status: item.status,
            expectedStatus: item.status,
            note: item.note || '',
            dueAt: localDateTime(item.dueAt),
            assignedOperatorId: item.assignedOperatorId || '',
          }
        }
        for (const item of detail.value.conferenceMediaCandidates) {
          conferenceCandidateDrafts[item.id] = {
            status: item.status,
            note: item.note || '',
          }
        }
      }
    }
    if (auth.user?.role === 'PLATFORM_ADMIN') {
      const op =
        await http.get<ApiResponse<{ id: number; displayName: string }[]>>('/admin/operators')
      operators.value = op.data.data
    }
  } catch (e) {
    error.value = apiError(e)
  } finally {
    loading.value = false
  }
}

function publishPlanAmount(plan: PublishPlanSummary) {
  if (plan.estimatedAmount === undefined || plan.estimatedAmount === null) {
    return '金额待项目核验'
  }
  const label =
    detail.value?.project.requestedService === 'DIRECT_PUBLISHING' ? '计划价' : '计划金额'
  return `${label} ¥${Number(plan.estimatedAmount).toLocaleString('zh-CN')}`
}

function publishPlanBoundary(plan: PublishPlanSummary) {
  return plan.status === 'WAITING_CONFIRMATION'
    ? '确认后进入项目核验；不会自动向媒体或渠道提交。'
    : '已进入项目核验；媒体或渠道可用性、价格与排期仍待确认。'
}

async function confirmSavedPublishPlan(planNo: string) {
  confirmingPlanNo.value = planNo
  actionError.value = ''
  try {
    await http.post(`/publish-plans/${encodeURIComponent(planNo)}/confirm`)
    toast.show('发布计划已提交项目核验，任务已建立', 'success')
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    confirmingPlanNo.value = ''
  }
}

async function saveConference() {
  conferenceSaving.value = true
  actionError.value = ''
  try {
    await http.patch(`/projects/${projectId}/conference`, {
      ...conferenceForm,
      eventTime: conferenceForm.eventTime || null,
      eventLocation: conferenceForm.eventLocation || null,
      conferenceType: conferenceForm.conferenceType || null,
      conferenceFormat: conferenceForm.conferenceFormat || null,
      conferenceScale: conferenceForm.conferenceScale || null,
      mediaGoal: conferenceForm.mediaGoal || null,
      guestPlan: conferenceForm.guestPlan || null,
      agendaPlan: conferenceForm.agendaPlan || null,
      venuePlan: conferenceForm.venuePlan || null,
      mediaDirection: conferenceForm.mediaDirection || null,
      communicationGoal: conferenceForm.communicationGoal || null,
    })
    toast.show('发布会资料已保存', 'success')
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    conferenceSaving.value = false
  }
}

async function review(decision: 'APPROVE' | 'RETURN') {
  if (!reviewVersion.value || !manuscript.value) return
  if (decision === 'RETURN' && !reviewComment.value.trim()) {
    actionError.value = '退回修改时请填写具体意见'
    return
  }
  submitting.value = true
  actionError.value = ''
  try {
    await http.post(`/manuscripts/${manuscript.value.id}/review`, {
      versionId: reviewVersion.value.id,
      decision,
      comment: reviewComment.value,
    })
    toast.show(decision === 'APPROVE' ? '稿件已确认定稿' : '稿件已退回修改', 'success')
    reviewComment.value = ''
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    submitting.value = false
  }
}

async function submitManuscript() {
  submitting.value = true
  actionError.value = ''
  try {
    if (canSubmitCustomerManuscript.value) {
      await http.post(`/projects/${projectId}/customer-manuscripts`, manuscriptForm)
      toast.show('客户定稿已保存，可继续筛选渠道', 'success')
    } else {
      await http.post(`/operator/projects/${projectId}/manuscripts`, manuscriptForm)
      toast.show('稿件已提交客户审核', 'success')
    }
    showEditor.value = false
    Object.assign(manuscriptForm, { title: '', summary: '', content: '', changeNote: '' })
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    submitting.value = false
  }
}

async function assign() {
  if (!selectedOperator.value) return
  submitting.value = true
  actionError.value = ''
  try {
    await http.patch(`/admin/projects/${projectId}/assign`, { operatorId: selectedOperator.value })
    toast.show('项目负责人已更新', 'success')
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    submitting.value = false
  }
}

async function saveConferenceWorkItem(item: ConferenceWorkItem) {
  const draft = workItemDrafts[item.id]
  if (!draft) return
  if (item.status === 'COMPLETED') {
    toast.show('该统筹事项已完成，不能重新打开或修改。', 'error')
    return
  }
  submitting.value = true
  actionError.value = ''
  try {
    await http.patch(`/operator/projects/${projectId}/conference-work-items/${item.id}`, {
      status: draft.status,
      expectedStatus: draft.expectedStatus,
      note: draft.note || null,
      dueAt: draft.dueAt || null,
      assignedOperatorId:
        auth.user?.role === 'PLATFORM_ADMIN' ? draft.assignedOperatorId || null : null,
    })
    toast.show('发布会统筹事项已更新', 'success')
    await load()
  } catch (e) {
    actionError.value = apiError(e)
    await load()
  } finally {
    submitting.value = false
  }
}

async function addConferenceMediaCandidates(candidates: MediaCandidate[]) {
  if (!canOperateConference.value || !candidates.length) return
  submitting.value = true
  actionError.value = ''
  try {
    const { data } = await http.post<
      ApiResponse<{ added: number; existing: number; message: string }>
    >(`/projects/${projectId}/conference-media-candidates/batch`, { candidates })
    toast.show(
      data.data.added ? `已将 ${data.data.added} 个候选加入拟邀名单` : '所选候选已在拟邀名单中',
      'success',
    )
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    submitting.value = false
  }
}

async function saveConferenceMediaCandidate(item: ConferenceMediaCandidate) {
  const draft = conferenceCandidateDrafts[item.id]
  if (!draft) return
  submitting.value = true
  actionError.value = ''
  try {
    await http.patch(`/operator/projects/${projectId}/conference-media-candidates/${item.id}`, {
      status: draft.status,
      expectedStatus: item.status,
      note: draft.note || null,
    })
    toast.show('媒体邀请进度已保存', 'success')
    await load()
  } catch (e) {
    actionError.value = apiError(e)
    await load()
  } finally {
    submitting.value = false
  }
}

async function uploadFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  actionError.value = ''
  const body = new FormData()
  body.append('file', file)
  body.append('projectId', String(projectId))
  try {
    await http.post('/files', body, { headers: { 'Content-Type': 'multipart/form-data' } })
    toast.show('项目材料已上传', 'success')
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function downloadFile(file: FileAsset) {
  downloadingFileNo.value = file.fileNo
  actionError.value = ''
  try {
    const response = await http.get(`/files/${encodeURIComponent(file.fileNo)}`, {
      responseType: 'blob',
    })
    const href = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = href
    link.download = file.originalName
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(href)
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    downloadingFileNo.value = ''
  }
}

onMounted(load)
</script>

<template>
  <RouterLink class="back-link page-back" to="/projects"
    ><ArrowLeft :size="17" />返回项目列表</RouterLink
  >
  <DataState :loading="loading" :error="error" :empty="!detail" @retry="load">
    <template #content>
      <PageHeader
        v-if="detail"
        :eyebrow="detail.project.projectNo"
        :title="detail.project.projectName"
        :description="`${detail.project.organizationName} · ${detail.project.customerName}`"
      >
        <StatusTag :status="detail.project.status" />
      </PageHeader>

      <section v-if="detail" class="detail-grid">
        <article class="panel detail-main">
          <div class="panel-heading">
            <div>
              <span class="eyebrow">需求</span>
              <h2>已确认事项</h2>
            </div>
          </div>
          <dl class="definition-grid">
            <div>
              <dt>需求编号</dt>
              <dd>{{ detail.project.requirementNo }}</dd>
            </div>
            <div>
              <dt>服务类型</dt>
              <dd>{{ serviceLabel(detail.project.requestedService) }}</dd>
            </div>
            <div v-if="detail.project.eventTime">
              <dt>活动时间</dt>
              <dd>{{ new Date(detail.project.eventTime).toLocaleString('zh-CN') }}</dd>
            </div>
            <div v-if="detail.project.eventLocation">
              <dt>服务地点</dt>
              <dd>{{ detail.project.eventLocation }}</dd>
            </div>
            <div v-if="detail.project.unitPrice">
              <dt>云采写计价</dt>
              <dd>
                ¥{{ Number(detail.project.unitPrice).toLocaleString('zh-CN') }} ×
                {{ detail.project.writerCount }} 人 × {{ detail.project.serviceDays }} 天
              </dd>
            </div>
            <div v-if="detail.project.estimatedAmount">
              <dt>基础金额</dt>
              <dd>¥{{ Number(detail.project.estimatedAmount).toLocaleString('zh-CN') }}</dd>
            </div>
            <div v-if="detail.project.onsiteContactName">
              <dt>现场联系人</dt>
              <dd>
                {{ detail.project.onsiteContactName }} · {{ detail.project.onsiteContactMobile }}
              </dd>
            </div>
            <div v-if="detail.project.matchingPreference === 'NEAREST_AVAILABLE'">
              <dt>匹配规则</dt>
              <dd>活动所在地及周边优先</dd>
            </div>
            <div v-if="detail.project.facts" class="full">
              <dt>事实信息</dt>
              <dd>{{ detail.project.facts }}</dd>
            </div>
            <div v-if="detail.project.objective" class="full">
              <dt>传播目标</dt>
              <dd>{{ detail.project.objective }}</dd>
            </div>
            <div v-if="detail.project.deliverableRequirement" class="full">
              <dt>报道规格</dt>
              <dd>{{ detail.project.deliverableRequirement }}</dd>
            </div>
          </dl>
        </article>
        <aside v-if="auth.user?.role !== 'CUSTOMER'" class="panel project-owner">
          <span class="eyebrow">服务负责人</span>
          <h2>{{ detail.project.operatorName || '待分配' }}</h2>
          <div v-if="auth.user?.role === 'PLATFORM_ADMIN'" class="assign-form">
            <select v-model="selectedOperator" aria-label="选择项目负责人">
              <option value="">选择项目负责人</option>
              <option v-for="op in operators" :key="op.id" :value="op.id">
                {{ op.displayName }}
              </option></select
            ><button
              class="button secondary"
              type="button"
              :disabled="!selectedOperator || submitting"
              @click="assign"
            >
              <UserRoundCheck :size="17" />确认分配
            </button>
          </div>
        </aside>
      </section>

      <section
        v-if="detail && canCreateRelatedServices"
        class="panel section-panel activity-service-actions"
      >
        <div class="panel-heading">
          <div>
            <span class="eyebrow">关联服务</span>
            <h2>新增服务</h2>
            <p>同一活动下，服务独立下单、独立执行、独立计价。</p>
          </div>
        </div>
        <div class="activity-service-action-grid">
          <RouterLink
            class="activity-service-action"
            :to="relatedServiceOrderPath('ONSITE_WRITING')"
          >
            <strong>云采写</strong>
            <span>现场采写与稿件撰写</span>
          </RouterLink>
          <RouterLink class="activity-service-action" :to="relatedServiceOrderPath('MEDIA_PR')">
            <strong>媒体邀请</strong>
            <span>建立拟邀媒体与记者名单</span>
          </RouterLink>
          <RouterLink
            class="activity-service-action"
            :to="relatedServiceOrderPath('DIRECT_PUBLISHING')"
          >
            <strong>直编发稿</strong>
            <span>选择渠道并提交发布计划</span>
          </RouterLink>
          <RouterLink
            class="activity-service-action"
            :to="relatedServiceOrderPath('NEWS_CONFERENCE')"
          >
            <strong>新闻发布会</strong>
            <span>建立会前、现场与会后执行清单</span>
          </RouterLink>
        </div>
      </section>

      <section
        v-if="detail && activityProjects.length > 1"
        class="panel section-panel activity-orders"
      >
        <div class="panel-heading">
          <div>
            <span class="eyebrow">同一活动</span>
            <h2>关联服务订单</h2>
            <p>每项服务保留各自项目、任务与订单记录。</p>
          </div>
        </div>
        <div class="activity-order-grid">
          <RouterLink
            v-for="item in activityProjects"
            :key="item.projectId"
            class="activity-order-card"
            :class="{ current: item.projectId === projectId }"
            :to="`/projects/${item.projectId}`"
          >
            <div>
              <span>{{ serviceLabel(item.requestedService) }}</span>
              <StatusTag :status="item.status" />
            </div>
            <strong>{{ item.projectName }}</strong>
            <small>{{ item.projectNo }}</small>
            <em v-if="item.estimatedAmount !== undefined && item.estimatedAmount !== null">
              基础金额 ¥{{ Number(item.estimatedAmount).toLocaleString('zh-CN') }}
            </em>
            <em v-else>按项目确认</em>
          </RouterLink>
        </div>
      </section>

      <section v-if="detail && isConferenceProject" class="panel section-panel conference-panel">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">新闻发布会</span>
            <h2>项目工作台</h2>
          </div>
          <div class="conference-heading-actions">
            <button
              class="button secondary"
              type="button"
              :disabled="!hasConferenceScheduleDates"
              :title="
                hasConferenceScheduleDates
                  ? '下载可导入系统日历的项目日程'
                  : '补充举办时间或事项截止时间后可导出'
              "
              @click="exportConferenceCalendar"
            >
              <Download :size="16" />导出项目日程
            </button>
            <StatusTag :status="detail.conference?.status" />
          </div>
        </div>
        <div class="conference-workspace-tabs" role="tablist" aria-label="发布会项目工作台">
          <button
            type="button"
            role="tab"
            :aria-selected="conferenceWorkspaceTab === 'OVERVIEW'"
            :class="{ active: conferenceWorkspaceTab === 'OVERVIEW' }"
            @click="conferenceWorkspaceTab = 'OVERVIEW'"
          >
            项目资料
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="conferenceWorkspaceTab === 'MEDIA'"
            :class="{ active: conferenceWorkspaceTab === 'MEDIA' }"
            @click="conferenceWorkspaceTab = 'MEDIA'"
          >
            媒体名单
            <span>{{ detail.conferenceMediaCandidates.length }}</span>
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="conferenceWorkspaceTab === 'WORK'"
            :class="{ active: conferenceWorkspaceTab === 'WORK' }"
            @click="conferenceWorkspaceTab = 'WORK'"
          >
            执行清单
            <span>{{ conferenceWorkProgress.completed }}/{{ conferenceWorkProgress.total }}</span>
          </button>
        </div>
        <form
          v-if="conferenceWorkspaceTab === 'OVERVIEW'"
          class="conference-project-form"
          role="tabpanel"
          @submit.prevent="saveConference"
        >
          <div class="conference-form-intro">
            <div>
              <h3>项目资料</h3>
              <p>
                建项时只要求标题、联系人和手机号。时间、地点、嘉宾、议程与媒体方向可以随后补充。
              </p>
            </div>
            <span>可分次保存</span>
          </div>
          <div class="form-grid two-columns">
            <label class="full"
              >发布会标题<span class="required">*</span
              ><input v-model="conferenceForm.theme" required maxlength="240"
            /></label>
            <label
              >会务联系人<span class="required">*</span
              ><input v-model="conferenceForm.contactName" required maxlength="80"
            /></label>
            <label
              >联系电话<span class="required">*</span
              ><input
                v-model="conferenceForm.contactMobile"
                required
                maxlength="11"
                pattern="1[3-9][0-9]{9}"
                inputmode="numeric"
            /></label>
            <label
              >举办时间<input v-model="conferenceForm.eventTime" type="datetime-local"
            /></label>
            <label>举办地点<input v-model="conferenceForm.eventLocation" maxlength="240" /></label>
          </div>
          <details class="conference-progressive-fields">
            <summary>补充策划与执行资料</summary>
            <p>以下内容不是建项必填项。资料明确一项，就补充一项。</p>
            <div class="form-grid two-columns">
              <label
                >发布会类型<select v-model="conferenceForm.conferenceType">
                  <option value="">待确认</option>
                  <option value="PRODUCT_RELEASE">新品发布</option>
                  <option value="STRATEGIC_SIGNING">战略合作或签约</option>
                  <option value="INDUSTRY_FORUM">论坛或峰会</option>
                  <option value="CORPORATE_EVENT">企业重要活动</option>
                </select></label
              >
              <label
                >举办形式<select v-model="conferenceForm.conferenceFormat">
                  <option value="">待确认</option>
                  <option value="OFFLINE">线下</option>
                  <option value="HYBRID">线上线下结合</option>
                  <option value="ONLINE">线上</option>
                </select></label
              >
              <label
                >参会规模<input
                  v-model="conferenceForm.conferenceScale"
                  maxlength="40"
                  placeholder="例如：约 200 人"
              /></label>
              <label
                >议程状态<select v-model="conferenceForm.agendaStatus">
                  <option value="PREPARING">准备中</option>
                  <option value="CONFIRMED">已确认</option>
                </select></label
              >
              <label
                >场地状态<select v-model="conferenceForm.venueStatus">
                  <option value="PENDING">待确认</option>
                  <option value="CONFIRMED">已确认</option>
                </select></label
              >
              <label class="full"
                >传播目标<textarea
                  v-model="conferenceForm.communicationGoal"
                  rows="3"
                  maxlength="1000"
                />
              </label>
              <label class="full"
                >媒体目标<textarea v-model="conferenceForm.mediaGoal" rows="3" maxlength="1000" />
              </label>
              <label class="full"
                >媒体方向<textarea
                  v-model="conferenceForm.mediaDirection"
                  rows="3"
                  maxlength="1000"
                  placeholder="行业、地区、媒体类型及优先线口"
                />
              </label>
              <label class="full"
                >嘉宾安排<textarea v-model="conferenceForm.guestPlan" rows="3" maxlength="2000" />
              </label>
              <label class="full"
                >议程安排<textarea v-model="conferenceForm.agendaPlan" rows="4" maxlength="2000" />
              </label>
              <label class="full"
                >场地与现场安排<textarea
                  v-model="conferenceForm.venuePlan"
                  rows="4"
                  maxlength="2000"
                />
              </label>
            </div>
          </details>
          <p v-if="actionError" class="form-error">{{ actionError }}</p>
          <div class="form-actions">
            <button class="button primary" type="submit" :disabled="conferenceSaving">
              {{ conferenceSaving ? '正在保存' : '保存发布会资料' }}
            </button>
          </div>
        </form>

        <div v-if="conferenceWorkspaceTab === 'WORK'" class="conference-stage-list" role="tabpanel">
          <section v-for="phase in conferencePhases" :key="phase.key">
            <header>
              <span>{{ phase.label }}</span>
              <small
                >{{ phase.items.filter((item) => item.status === 'COMPLETED').length }} /
                {{ phase.items.length }} 已完成</small
              >
            </header>
            <div class="conference-work-list">
              <article v-for="item in phase.items" :key="item.itemNo">
                <div class="conference-work-order">
                  {{ String(item.sortOrder).padStart(2, '0') }}
                </div>
                <div class="conference-work-copy">
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.detail }}</p>
                  <template
                    v-if="
                      canOperateConference && workItemDrafts[item.id] && item.status !== 'COMPLETED'
                    "
                  >
                    <div class="conference-work-fields">
                      <select
                        v-model="workItemDrafts[item.id].status"
                        :aria-label="`${item.title}状态`"
                      >
                        <option
                          v-for="option in conferenceWorkItemStatusOptions(item.status)"
                          :key="option.value"
                          :value="option.value"
                        >
                          {{ option.label }}
                        </option>
                      </select>
                      <input
                        v-model="workItemDrafts[item.id].dueAt"
                        type="datetime-local"
                        :aria-label="`${item.title}截止时间`"
                      />
                      <select
                        v-if="auth.user?.role === 'PLATFORM_ADMIN'"
                        v-model.number="workItemDrafts[item.id].assignedOperatorId"
                        :aria-label="`${item.title}负责人`"
                      >
                        <option value="">沿用项目负责人</option>
                        <option v-for="op in operators" :key="op.id" :value="op.id">
                          {{ op.displayName }}
                        </option>
                      </select>
                      <textarea
                        v-model="workItemDrafts[item.id].note"
                        rows="2"
                        maxlength="1000"
                        placeholder="补充进展；标记需补充或受阻时请说明原因"
                      />
                      <button
                        class="button secondary"
                        type="button"
                        :disabled="submitting"
                        @click="saveConferenceWorkItem(item)"
                      >
                        保存事项
                      </button>
                    </div>
                  </template>
                  <small v-else-if="item.status === 'COMPLETED'">事项已完成，状态已锁定。</small>
                  <small v-else-if="item.note">{{ item.note }}</small>
                </div>
                <div class="conference-work-state">
                  <StatusTag :status="item.status" />
                  <small v-if="canOperateConference">{{ item.operatorName || '待分配' }}</small>
                  <small v-if="item.dueAt">{{
                    new Date(item.dueAt).toLocaleString('zh-CN')
                  }}</small>
                </div>
              </article>
            </div>
          </section>
        </div>
        <MediaDiscoveryPanel
          v-if="conferenceWorkspaceTab === 'MEDIA' && canOperateConference"
          workflow="NEWS_CONFERENCE"
          action-label="勾选候选"
          :selected-keys="conferenceCandidateKeys"
          :busy="submitting"
          :max-selection="100"
          @submit="addConferenceMediaCandidates"
        />
        <div
          v-if="conferenceWorkspaceTab === 'MEDIA'"
          class="conference-media-list"
          role="tabpanel"
        >
          <div class="conference-media-list-heading">
            <div>
              <span class="eyebrow">发布会拟邀名单</span>
              <h3>已选媒体候选</h3>
            </div>
            <small>候选名单不构成到场、采访或报道承诺。</small>
          </div>
          <p v-if="!detail.conferenceMediaCandidates.length" class="muted">
            {{
              canOperateConference
                ? '从上方检索结果中加入媒体，平台再确认实际邀约对象与联系方式。'
                : '拟邀名单由平台推进；如需调整，请在项目资料中补充说明。'
            }}
          </p>
          <article
            v-for="item in detail.conferenceMediaCandidates"
            :key="conferenceCandidateRowKey(item)"
            class="conference-media-item"
          >
            <div>
              <strong>{{ item.reporterName || item.displayName }}</strong>
              <p v-if="item.reporterName">{{ item.displayName }} · 记者候选</p>
              <p>
                {{
                  [item.attribute, item.province, item.city, item.channelForm, item.category]
                    .filter(Boolean)
                    .join(' · ')
                }}
              </p>
              <small v-if="item.coverageTags">{{ item.coverageTags }}</small>
              <small v-if="item.score != null">
                综合分 {{ Math.round(item.score) }}
                <template v-if="item.newsCount != null">
                  · {{ item.newsCount.toLocaleString('zh-CN') }} 篇报道
                </template>
              </small>
            </div>
            <div class="conference-media-state">
              <div class="conference-media-status-line">
                <StatusTag :status="item.status" />
                <small v-if="canOperateConference">{{ item.operatorName || '待平台分配' }}</small>
              </div>
              <div class="conference-media-timeline">
                <span v-if="item.selectedAt">加入 {{ displayDateTime(item.selectedAt) }}</span>
                <span v-if="item.invitedAt">邀请 {{ displayDateTime(item.invitedAt) }}</span>
                <span v-if="item.respondedAt">回复 {{ displayDateTime(item.respondedAt) }}</span>
              </div>
              <template
                v-if="
                  canOperateConference &&
                  conferenceCandidateDrafts[item.id] &&
                  !isConferenceCandidateFinal(item.status)
                "
              >
                <label>
                  <span>邀约进度</span>
                  <select
                    v-model="conferenceCandidateDrafts[item.id].status"
                    :disabled="submitting"
                    :aria-label="`更新${item.displayName}候选状态`"
                  >
                    <option
                      v-for="option in conferenceCandidateStatusOptions(item.status)"
                      :key="option.value"
                      :value="option.value"
                    >
                      {{ option.label }}
                    </option>
                  </select>
                </label>
                <label>
                  <span>跟进记录</span>
                  <textarea
                    v-model="conferenceCandidateDrafts[item.id].note"
                    rows="2"
                    maxlength="1000"
                    placeholder="记录联系渠道、回复要点和下一步安排"
                  />
                </label>
                <button
                  class="button secondary"
                  type="button"
                  :disabled="submitting"
                  @click="saveConferenceMediaCandidate(item)"
                >
                  保存进度
                </button>
              </template>
              <small
                v-else-if="canOperateConference && isConferenceCandidateFinal(item.status)"
                class="conference-media-note"
              >
                邀约结果已记录，状态已锁定。
              </small>
              <small v-else-if="item.note" class="conference-media-note">
                跟进记录：{{ item.note }}
              </small>
            </div>
          </article>
        </div>
        <p v-if="actionError" class="form-error">{{ actionError }}</p>
      </section>

      <section v-if="detail" class="panel section-panel material-panel">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">项目材料</span>
            <h2>文件记录</h2>
          </div>
          <button
            class="button secondary"
            type="button"
            :disabled="uploading"
            @click="fileInput?.click()"
          >
            <Upload :size="17" />{{ uploading ? '正在上传' : '上传文件' }}</button
          ><input
            ref="fileInput"
            class="visually-hidden"
            type="file"
            accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.webp,.txt"
            aria-label="选择项目材料文件"
            @change="uploadFile"
          />
        </div>
        <div v-if="detail.files.length" class="file-list">
          <button
            v-for="file in detail.files"
            :key="file.fileNo"
            class="file-download"
            type="button"
            :disabled="downloadingFileNo === file.fileNo"
            @click="downloadFile(file)"
          >
            <Paperclip :size="17" /><span
              ><strong>{{ file.originalName }}</strong
              ><small
                >{{ (file.fileSize / 1024).toFixed(1) }} KB ·
                {{ new Date(file.createdAt).toLocaleString('zh-CN') }}</small
              ></span
            ><Download :size="16" />
          </button>
        </div>
        <div v-else class="inline-empty">尚未上传项目材料。</div>
        <p v-if="actionError" class="form-error">{{ actionError }}</p>
      </section>

      <section v-if="detail?.serviceIntakeTasks.length" class="panel section-panel">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">服务受理</span>
            <h2>范围确认</h2>
          </div>
        </div>
        <div class="compact-list">
          <article v-for="item in detail.serviceIntakeTasks" :key="item.taskNo" class="compact-row">
            <span class="list-icon"><RadioTower :size="18" /></span>
            <div>
              <strong>{{ item.title }}</strong>
              <small>{{ item.customerVisibleNote || '平台正在确认服务范围。' }}</small>
            </div>
            <StatusTag :status="item.status" />
          </article>
        </div>
      </section>

      <section id="publish-plans" v-if="detail && publishPlans.length" class="panel section-panel">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">发布计划</span>
            <h2>计划确认</h2>
          </div>
        </div>
        <p class="section-note">
          已保存的计划需要您再次确认后才会进入项目核验；这不会自动向媒体发出邀请或向渠道提交稿件。
        </p>
        <div class="publish-plan-list">
          <article v-for="plan in publishPlans" :key="plan.planNo" class="publish-plan-row">
            <div class="publish-plan-copy">
              <strong>{{ plan.planName || '未命名发布计划' }}</strong>
              <small
                >{{ plan.planNo }} · {{ plan.itemCount }} 项 · {{ publishPlanAmount(plan) }}</small
              >
              <p>{{ publishPlanBoundary(plan) }}</p>
            </div>
            <div class="publish-plan-action">
              <StatusTag :status="plan.status" />
              <button
                v-if="auth.user?.role === 'CUSTOMER' && plan.status === 'WAITING_CONFIRMATION'"
                class="button primary"
                type="button"
                :disabled="confirmingPlanNo === plan.planNo"
                @click="confirmSavedPublishPlan(plan.planNo)"
              >
                <Send :size="17" />{{
                  confirmingPlanNo === plan.planNo ? '正在提交' : '提交项目核验'
                }}
              </button>
            </div>
          </article>
        </div>
        <p v-if="actionError" class="form-error">{{ actionError }}</p>
      </section>

      <section v-if="detail" class="panel section-panel">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">稿件资产</span>
            <h2>版本与客户审核</h2>
          </div>
          <button
            v-if="canSubmitManuscript"
            class="button secondary"
            type="button"
            @click="showEditor = !showEditor"
          >
            <FilePenLine :size="17" />{{
              showEditor
                ? '收起编辑'
                : canSubmitCustomerManuscript
                  ? manuscript
                    ? '更新客户定稿'
                    : '提交已有定稿'
                  : manuscript
                    ? '提交新版本'
                    : '提交首稿'
            }}
          </button>
        </div>
        <form v-if="showEditor" class="inline-editor" @submit.prevent="submitManuscript">
          <p v-if="canSubmitCustomerManuscript" class="small-note">
            仅提交已经贵司确认、可用于发布的稿件。保存后将作为本项目定稿，用于渠道筛选与发稿计划。
          </p>
          <div class="form-grid two-columns">
            <label class="full"
              >稿件标题<span class="required">*</span
              ><input v-model="manuscriptForm.title" required maxlength="240" /></label
            ><label class="full"
              >内容摘要<textarea v-model="manuscriptForm.summary" rows="2"></textarea></label
            ><label class="full"
              >稿件正文<span class="required">*</span
              ><textarea v-model="manuscriptForm.content" required rows="10"></textarea></label
            ><label class="full"
              >本次修改说明<input v-model="manuscriptForm.changeNote" maxlength="500"
            /></label>
          </div>
          <div class="form-actions">
            <button class="button primary" type="submit" :disabled="submitting">
              <Send :size="17" />{{ canSubmitCustomerManuscript ? '保存客户定稿' : '提交客户审核' }}
            </button>
          </div>
        </form>
        <div v-if="detail.versions.length" class="version-stack">
          <article
            v-for="version in detail.versions"
            :key="version.id"
            class="version-item"
            :class="{ active: version.id === reviewVersion?.id }"
          >
            <div class="version-head">
              <div>
                <strong>第 {{ version.versionNumber }} 版 · {{ version.title }}</strong
                ><small>{{ version.changeNote || '初稿' }}</small>
              </div>
              <StatusTag :status="version.status" />
            </div>
            <p v-if="version.summary" class="version-summary">{{ version.summary }}</p>
            <p v-if="version.sourceProjectName" class="version-summary">
              来源：客户已确认稿件副本 · {{ version.sourceProjectName }}
              <template v-if="version.sourceManuscriptTitle"
                >（{{ version.sourceManuscriptTitle }}）</template
              >
            </p>
            <div class="manuscript-content">{{ version.content }}</div>
            <p v-if="version.reviewComment" class="review-note">
              客户意见：{{ version.reviewComment }}
            </p>
          </article>
        </div>
        <div v-else class="inline-empty">尚未提交稿件。</div>
        <div v-if="auth.user?.role === 'CUSTOMER' && reviewVersion" class="review-actions">
          <label
            >审核意见<textarea
              v-model="reviewComment"
              rows="3"
              placeholder="确认定稿可不填；退回修改请说明需调整的事实或表述"
            ></textarea>
          </label>
          <p v-if="actionError" class="form-error">{{ actionError }}</p>
          <div>
            <button
              class="button secondary danger-text"
              type="button"
              :disabled="submitting"
              @click="review('RETURN')"
            >
              <X :size="17" />退回修改</button
            ><button
              class="button primary"
              type="button"
              :disabled="submitting"
              @click="review('APPROVE')"
            >
              <Check :size="17" />确认定稿
            </button>
          </div>
        </div>
      </section>

      <section v-if="detail" class="panel section-panel">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">媒体与发布执行</span>
            <h2>渠道任务</h2>
          </div>
          <RouterLink
            v-if="
              auth.user?.role === 'CUSTOMER' &&
              approvedVersion &&
              detail.project.requestedService === 'DIRECT_PUBLISHING'
            "
            class="button primary"
            :to="`/direct-publishing?projectId=${projectId}`"
            ><RadioTower :size="17" />选择媒体与渠道</RouterLink
          >
        </div>
        <div v-if="detail.tasks.length" class="task-grid">
          <article v-for="task in detail.tasks" :key="task.taskNo" class="task-card">
            <div>
              <span>{{
                task.channelType === 'MEDIA_PR'
                  ? '媒体邀请'
                  : task.channelType === 'DIRECT_PUBLISHING'
                    ? '直编发稿'
                    : '历史渠道记录'
              }}</span
              ><StatusTag :status="task.status" />
            </div>
            <h3>{{ task.channelName }}</h3>
            <p>
              {{
                auth.user?.role === 'CUSTOMER'
                  ? '等待项目团队推进'
                  : task.executionNote || '等待运营人员处理'
              }}
            </p>
            <small>{{ task.taskNo }}</small>
          </article>
        </div>
        <div v-else class="inline-empty">稿件或邀请材料确认后，可选择媒体与渠道并提交任务。</div>
      </section>

      <section v-if="detail" class="two-panel-grid">
        <article class="panel section-panel">
          <div class="panel-heading">
            <div>
              <span class="eyebrow">成果</span>
              <h2>发布链接</h2>
            </div>
          </div>
          <div v-if="detail.results.length" class="result-list">
            <a
              v-for="item in detail.results"
              :key="item.resultNo"
              :href="item.url"
              target="_blank"
              rel="noreferrer"
              ><div>
                <strong>{{ item.channelName }}</strong
                ><span>{{ item.title }}</span>
              </div>
              <ExternalLink :size="18"
            /></a>
          </div>
          <div v-else class="inline-empty">暂无已核验的发布链接。</div>
        </article>
        <article class="panel section-panel">
          <div class="panel-heading">
            <div>
              <span class="eyebrow">监测</span>
              <h2>核验记录</h2>
            </div>
          </div>
          <div v-if="detail.monitoring.length" class="monitor-list">
            <div v-for="item in detail.monitoring" :key="item.monitoringNo">
              <Check :size="17" /><span
                ><strong>{{ item.metricText }}</strong
                ><small>{{ new Date(item.monitoredAt).toLocaleString('zh-CN') }}</small></span
              >
            </div>
          </div>
          <div v-else class="inline-empty">发布后生成监测记录。</div>
        </article>
      </section>
    </template>
  </DataState>
</template>

<style scoped>
.section-note {
  margin: -2px 0 16px;
  font-size: 14px;
}

.publish-plan-list {
  display: grid;
  gap: 10px;
}

.publish-plan-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 16px;
  background: var(--soft);
}

.publish-plan-copy {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.publish-plan-copy strong,
.publish-plan-copy small,
.publish-plan-copy p {
  overflow-wrap: anywhere;
}

.publish-plan-copy small {
  color: var(--muted);
}

.publish-plan-copy p {
  margin: 2px 0 0;
  font-size: 13px;
}

.publish-plan-action {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex: 0 0 auto;
}

@media (max-width: 640px) {
  .publish-plan-row {
    align-items: stretch;
    flex-direction: column;
    gap: 14px;
  }

  .publish-plan-action {
    justify-content: space-between;
  }

  .publish-plan-action .button {
    flex: 1;
  }
}
</style>
