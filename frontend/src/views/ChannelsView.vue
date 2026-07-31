<script setup lang="ts">
import { Check, CircleAlert, FilterX, Search, Send, X } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import MediaDiscoveryPanel from '@/components/MediaDiscoveryPanel.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import type {
  ApiResponse,
  MediaCandidate,
  PageResult,
  ProjectSummary,
  PublicChannel,
} from '@/types'

interface ProjectDetail {
  project: { projectName: string; requestedService?: string }
  manuscripts: { id: number; approvedVersionId?: number }[]
  versions: { id: number; status: string }[]
}

interface PublishPlanResult {
  planNo: string
  status: 'WAITING_CONFIRMATION'
  itemCount: number
  estimatedAmount: number
  message: string
}

interface PublishPlanConfirmation {
  status: string
  taskNos: string[]
  message: string
}

interface ChannelTaxonomy {
  regions: string[]
  categories: string[]
  publishForms: string[]
  linkTypes: string[]
  newsSources: string[]
  entryLevels: string[]
  specialIndustries: string[]
  weekendPolicies: string[]
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToastStore()
const projectId = computed(() => Number(route.query.projectId || 0))
type ChannelWorkflow = 'MEDIA_PR' | 'DIRECT_PUBLISHING'
function channelTypeFromRoute(): ChannelWorkflow {
  const routeType = route.meta.channelType
  if (routeType === 'MEDIA_PR' || routeType === 'DIRECT_PUBLISHING') return routeType
  return route.query.type === 'MEDIA_PR' ? 'MEDIA_PR' : 'DIRECT_PUBLISHING'
}
const project = ref<ProjectDetail | null>(null)
const items = ref<PublicChannel[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const submitting = ref(false)
const type = ref<ChannelWorkflow>(channelTypeFromRoute())
const selected = ref<Record<number, PublicChannel>>({})
const invitationTargets = ref<Record<string, MediaCandidate>>({})
const projectOptions = ref<ProjectSummary[]>([])
const projectOptionsLoading = ref(false)
const selectedProjectId = ref(0)
const submissionProject = ref<ProjectDetail | null>(null)
const showSubmissionDialog = ref(false)
const dialogError = ref('')
const createdPlan = ref<PublishPlanResult | null>(null)
const channelTaxonomy = ref<ChannelTaxonomy>({
  regions: [],
  categories: [],
  publishForms: [],
  linkTypes: [],
  newsSources: [],
  entryLevels: [],
  specialIndustries: [],
  weekendPolicies: [],
})
const PUBLISH_PLAN_SUBMISSION_STATE_KEY = 'winpress:publish-plan-submission'
let searchTimer = 0

const filters = reactive({
  keyword: '',
  region: '',
  category: '',
  publishForm: '',
  minPrice: undefined as number | undefined,
  maxPrice: undefined as number | undefined,
  maxDays: undefined as number | undefined,
  linkSupport: '' as '' | 'true' | 'false',
  linkType: '',
  newsSource: '',
  entryLevel: '',
  specialIndustry: '',
  weekendPolicy: '',
  sort: 'PRICE_ASC',
})

const typeCopy = {
  MEDIA_PR: {
    title: '媒体邀请',
    text: '拟邀名单、邀请记录与实际回复统一归档。',
  },
  DIRECT_PUBLISHING: {
    title: '直编发稿',
    text: '按价格、时效、地区、分类和发布形式整理渠道计划；提交后由平台核验可用性、稿件要求和排期。',
  },
}

const selectedItems = computed(() => Object.values(selected.value))
const selectedInvitationTargets = computed(() => Object.values(invitationTargets.value))
const selectedInvitationKeys = computed(() => Object.keys(invitationTargets.value))
const selectedCount = computed(() =>
  type.value === 'MEDIA_PR' ? selectedInvitationTargets.value.length : selectedItems.value.length,
)
const selectedTotal = computed(() =>
  selectedItems.value.reduce((amount, item) => amount + Number(item.customerPrice || 0), 0),
)
const pageAllSelected = computed(
  () => items.value.length > 0 && items.value.every((item) => Boolean(selected.value[item.id])),
)
const publishableProjects = computed(() =>
  projectOptions.value.filter((item) => item.hasApprovedManuscript),
)
const selectableProjects = computed(() =>
  type.value === 'DIRECT_PUBLISHING' ? publishableProjects.value : projectOptions.value,
)
const submissionManuscript = computed(() =>
  submissionProject.value?.manuscripts.find((item) => Boolean(item.approvedVersionId)),
)
const directCatalogAwaitingConfirmation = computed(() => {
  const filtersClear =
    !filters.keyword &&
    !filters.region &&
    !filters.category &&
    !filters.publishForm &&
    filters.minPrice == null &&
    filters.maxPrice == null &&
    filters.maxDays == null &&
    !filters.linkSupport &&
    !filters.linkType &&
    !filters.newsSource &&
    !filters.entryLevel &&
    !filters.specialIndustry &&
    !filters.weekendPolicy
  const taxonomyEmpty =
    !channelTaxonomy.value.regions.length &&
    !channelTaxonomy.value.categories.length &&
    !channelTaxonomy.value.publishForms.length &&
    !channelTaxonomy.value.linkTypes.length &&
    !channelTaxonomy.value.newsSources.length &&
    !channelTaxonomy.value.entryLevels.length &&
    !channelTaxonomy.value.specialIndustries.length &&
    !channelTaxonomy.value.weekendPolicies.length
  return (
    type.value === 'DIRECT_PUBLISHING' &&
    !loading.value &&
    !error.value &&
    total.value === 0 &&
    filtersClear &&
    taxonomyEmpty
  )
})
const directCatalogNotice = computed(() =>
  directCatalogAwaitingConfirmation.value
    ? '外部媒体目录与报价尚待验收，当前不能在线选择渠道。可先创建直编发稿项目并提交客户定稿，平台会在项目核验后再建立渠道计划。'
    : '当前频道目录、目录属性和客户服务价仅用于初筛与预算参考。提交计划后，由平台核验渠道可用性、稿件要求、报价有效期和排期；这不是实时媒体库存或媒体发布承诺。',
)
const directCatalogEmptyTitle = computed(() =>
  directCatalogAwaitingConfirmation.value ? '渠道目录待确认' : '未找到匹配渠道',
)
const directCatalogEmptyText = computed(() =>
  directCatalogAwaitingConfirmation.value
    ? '未经授权和对客字段核验的外部媒体数据不会显示在客户目录中。'
    : '调整筛选条件后再试。',
)

function independentProjectMessage() {
  return type.value === 'MEDIA_PR'
    ? '媒体邀请须使用独立的媒体邀请项目，请先创建该服务项目。'
    : '直编发稿须使用独立的直编发稿项目，请先创建该服务项目。'
}

function activeSubmissionProjectId() {
  const value = Number(selectedProjectId.value || projectId.value)
  return Number.isSafeInteger(value) && value > 0 ? value : 0
}

function isCurrentServiceProject(candidate: ProjectDetail) {
  return candidate.project.requestedService === type.value
}

function money(value?: number) {
  return value == null
    ? '提交后核价'
    : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (type.value === 'DIRECT_PUBLISHING') {
      const response = await http.get<ApiResponse<PageResult<PublicChannel>>>('/channels', {
        params: {
          type: 'DIRECT_PUBLISHING',
          keyword: filters.keyword,
          region: filters.region,
          category: filters.category,
          publish_form: filters.publishForm,
          min_price: filters.minPrice,
          max_price: filters.maxPrice,
          max_days: filters.maxDays,
          link_support: filters.linkSupport ? filters.linkSupport === 'true' : undefined,
          link_type: filters.linkType,
          news_source: filters.newsSource,
          entry_level: filters.entryLevel,
          special_industry: filters.specialIndustry,
          weekend_policy: filters.weekendPolicy,
          sort: filters.sort,
          page: page.value,
          pageSize: 30,
        },
      })
      items.value = response.data.data.items
      total.value = response.data.data.total
      for (const item of items.value) {
        if (selected.value[item.id]) selected.value[item.id] = item
      }
    } else {
      // 媒体邀请的候选来自独立的媒体发现能力。未验收的数据不能借用渠道目录作为执行渠道。
      items.value = []
      total.value = 0
    }
    if (projectId.value && !project.value) {
      const projectResponse = await http.get<ApiResponse<ProjectDetail>>(
        `/projects/${projectId.value}`,
      )
      project.value = projectResponse.data.data
    }
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    loading.value = false
  }
}

async function loadChannelTaxonomy() {
  if (type.value !== 'DIRECT_PUBLISHING') return
  try {
    const { data } = await http.get<ApiResponse<ChannelTaxonomy>>('/channels/taxonomy', {
      params: { type: 'DIRECT_PUBLISHING' },
    })
    channelTaxonomy.value = data.data
  } catch (requestError) {
    actionError.value = apiError(requestError)
  }
}

function resetChannelState() {
  page.value = 1
  selected.value = {}
  invitationTargets.value = {}
  projectOptions.value = []
  selectedProjectId.value = 0
  submissionProject.value = null
  actionError.value = ''
}

function applyType(next: ChannelWorkflow) {
  type.value = next
  resetChannelState()
  load()
  if (next === 'DIRECT_PUBLISHING') loadChannelTaxonomy()
}

function changePage(next: number) {
  page.value = next
  load()
}

function toggle(channel: PublicChannel) {
  if (selected.value[channel.id]) {
    const next = { ...selected.value }
    delete next[channel.id]
    selected.value = next
    return
  }
  if (selectedCount.value >= 50) {
    toast.show('单次最多选择 50 个直编渠道，请分批提交。', 'error')
    return
  }
  selected.value = { ...selected.value, [channel.id]: channel }
}

function togglePage() {
  if (pageAllSelected.value) {
    const next = { ...selected.value }
    for (const item of items.value) delete next[item.id]
    selected.value = next
    return
  }
  const next = { ...selected.value }
  for (const item of items.value) {
    if (Object.keys(next).length >= 50 && !next[item.id]) break
    next[item.id] = item
  }
  selected.value = next
}

function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    region: '',
    category: '',
    publishForm: '',
    minPrice: undefined,
    maxPrice: undefined,
    maxDays: undefined,
    linkSupport: '',
    linkType: '',
    newsSource: '',
    entryLevel: '',
    specialIndustry: '',
    weekendPolicy: '',
    sort: 'PRICE_ASC',
  })
  page.value = 1
  load()
}

async function loadProjectOptions() {
  if (projectOptions.value.length || projectOptionsLoading.value) return
  projectOptionsLoading.value = true
  try {
    const { data } = await http.get<ApiResponse<PageResult<ProjectSummary>>>('/projects', {
      params: {
        page: 1,
        pageSize: 100,
        serviceType: type.value,
      },
    })
    projectOptions.value = data.data.items
    if (selectableProjects.value.length === 1) {
      selectedProjectId.value = selectableProjects.value[0].id
    }
  } catch (requestError) {
    dialogError.value = apiError(requestError)
  } finally {
    projectOptionsLoading.value = false
  }
}

async function openSubmissionDialog() {
  actionError.value = ''
  if (!selectedCount.value) {
    actionError.value =
      type.value === 'MEDIA_PR' ? '请至少选择一个拟邀对象。' : '请至少选择一个渠道。'
    return
  }
  dialogError.value = ''
  createdPlan.value = null
  if (project.value) {
    if (!isCurrentServiceProject(project.value)) {
      actionError.value = independentProjectMessage()
      return
    }
    const manuscript = project.value.manuscripts.find((item) => Boolean(item.approvedVersionId))
    if (type.value === 'DIRECT_PUBLISHING' && !manuscript) {
      actionError.value = '当前项目尚无已确认的定稿版本，请先在项目中完成定稿。'
      return
    }
    submissionProject.value = project.value
    selectedProjectId.value = projectId.value
  } else {
    submissionProject.value = null
    await loadProjectOptions()
    if (!projectOptions.value.length) {
      actionError.value =
        type.value === 'MEDIA_PR'
          ? '请先创建媒体邀请项目，创建后会回到这里继续筛选媒体。'
          : '请先创建直编发稿项目，并在项目中提交客户定稿后继续选择渠道。'
      if (type.value === 'MEDIA_PR') {
        router.push('/requirements/new?service=MEDIA_PR&returnTo=media-invitation')
      } else {
        router.push('/requirements/new?service=DIRECT_PUBLISHING')
      }
      return
    }
  }
  showSubmissionDialog.value = true
}

async function useSelectedProject() {
  dialogError.value = ''
  if (!selectedProjectId.value) {
    dialogError.value = '请选择本次发稿使用的项目。'
    return
  }
  try {
    const { data } = await http.get<ApiResponse<ProjectDetail>>(
      `/projects/${selectedProjectId.value}`,
    )
    if (!isCurrentServiceProject(data.data)) {
      dialogError.value = independentProjectMessage()
      return
    }
    const manuscript = data.data.manuscripts.find((item) => Boolean(item.approvedVersionId))
    if (type.value === 'DIRECT_PUBLISHING' && !manuscript) {
      dialogError.value = '该项目尚无已确认的定稿版本，请先在项目中完成定稿。'
      return
    }
    submissionProject.value = data.data
  } catch (requestError) {
    dialogError.value = apiError(requestError)
  }
}

function closeSubmissionDialog() {
  if (submitting.value) return
  showSubmissionDialog.value = false
  dialogError.value = ''
  createdPlan.value = null
}

async function publishPlanFingerprint(projectIdValue: number, payload: object) {
  const bytes = new TextEncoder().encode(JSON.stringify({ projectId: projectIdValue, payload }))
  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes)
    return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join(
      '',
    )
  }
  let hash = 2166136261
  for (const value of bytes) {
    hash ^= value
    hash = Math.imul(hash, 16777619)
  }
  return `${bytes.length}-${(hash >>> 0).toString(16)}`
}

function newPublishPlanIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `wp-plan-${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`
}

async function publishPlanIdempotencyKeyFor(projectIdValue: number, payload: object) {
  const fingerprint = await publishPlanFingerprint(projectIdValue, payload)
  try {
    const stored = JSON.parse(
      sessionStorage.getItem(PUBLISH_PLAN_SUBMISSION_STATE_KEY) || 'null',
    ) as { fingerprint?: string; key?: string } | null
    if (stored?.fingerprint === fingerprint && stored.key) return stored.key
  } catch {
    sessionStorage.removeItem(PUBLISH_PLAN_SUBMISSION_STATE_KEY)
  }
  const key = newPublishPlanIdempotencyKey()
  sessionStorage.setItem(PUBLISH_PLAN_SUBMISSION_STATE_KEY, JSON.stringify({ fingerprint, key }))
  return key
}

function clearPublishPlanSubmissionState(key: string) {
  try {
    const stored = JSON.parse(
      sessionStorage.getItem(PUBLISH_PLAN_SUBMISSION_STATE_KEY) || 'null',
    ) as { key?: string } | null
    if (stored?.key === key) sessionStorage.removeItem(PUBLISH_PLAN_SUBMISSION_STATE_KEY)
  } catch {
    sessionStorage.removeItem(PUBLISH_PLAN_SUBMISSION_STATE_KEY)
  }
}

async function savePublishPlan() {
  const context = submissionProject.value
  const manuscript = submissionManuscript.value
  const targetProjectId = activeSubmissionProjectId()
  if (!context) {
    dialogError.value = '请先选择本次执行使用的项目。'
    return
  }
  if (!targetProjectId) {
    dialogError.value = '项目编号无效，请重新选择本次执行使用的项目。'
    return
  }
  if (type.value === 'DIRECT_PUBLISHING' && !manuscript?.approvedVersionId) {
    dialogError.value = '直编发稿必须选择已有定稿版本的项目。'
    return
  }
  submitting.value = true
  dialogError.value = ''
  try {
    const payload = {
      manuscriptId: manuscript?.id || null,
      manuscriptVersionId: manuscript?.approvedVersionId || null,
      planName:
        type.value === 'MEDIA_PR'
          ? `${context.project.projectName}媒体邀请计划`
          : `${context.project.projectName}直编发稿计划`,
      objective:
        type.value === 'MEDIA_PR' ? '保存拟邀名单，待项目核验' : '保存发稿渠道计划，待项目核验',
      selections:
        type.value === 'MEDIA_PR'
          ? selectedInvitationTargets.value.map((candidate) => ({
              // 候选名单不等同于可履约渠道；必须由项目核验后再形成实际执行关系。
              channelId: null,
              journalistName: candidate.reporterName || null,
              mediaName: candidate.displayName,
              mediaCandidate: candidate,
            }))
          : selectedItems.value.map((channel) => ({
              channelId: channel.id,
              journalistName: null,
              mediaName: null,
              mediaCandidate: null,
            })),
    }
    const idempotencyKey = await publishPlanIdempotencyKeyFor(targetProjectId, payload)
    const { data } = await http.post<ApiResponse<PublishPlanResult>>(
      `/projects/${targetProjectId}/publish-plans`,
      payload,
      {
        headers: { 'Idempotency-Key': idempotencyKey },
      },
    )
    clearPublishPlanSubmissionState(idempotencyKey)
    createdPlan.value = data.data
    toast.show(
      type.value === 'MEDIA_PR'
        ? '媒体邀请计划已保存，请提交项目核验。'
        : '发稿计划已保存，请提交项目核验。',
      'success',
    )
  } catch (requestError) {
    dialogError.value = apiError(requestError)
  } finally {
    submitting.value = false
  }
}

async function confirmPublishPlan() {
  const context = submissionProject.value
  if (!context || !createdPlan.value) return
  const targetProjectId = activeSubmissionProjectId()
  if (!targetProjectId) {
    dialogError.value = '项目编号无效，请重新选择本次执行使用的项目。'
    return
  }
  submitting.value = true
  dialogError.value = ''
  try {
    const { data } = await http.post<ApiResponse<PublishPlanConfirmation>>(
      `/publish-plans/${encodeURIComponent(createdPlan.value.planNo)}/confirm`,
    )
    toast.show(
      type.value === 'MEDIA_PR'
        ? '媒体邀请计划已提交项目核验，任务已建立。'
        : '发稿计划已提交项目核验，任务已建立。',
      'success',
    )
    showSubmissionDialog.value = false
    router.push({
      path: '/tasks',
      query: {
        created: data.data.taskNos.join(','),
        projectId: String(targetProjectId),
      },
    })
  } catch (requestError) {
    dialogError.value = apiError(requestError)
  } finally {
    submitting.value = false
  }
}

function useMediaCandidate(candidate: MediaCandidate) {
  if (!candidate.available) {
    toast.show('该对象当前不可邀约，请更换候选或由平台核验后人工补充。', 'error')
    return
  }
  const next = { ...invitationTargets.value }
  if (next[candidate.candidateKey]) {
    delete next[candidate.candidateKey]
  } else {
    if (selectedInvitationTargets.value.length >= 50) {
      toast.show('单次最多选择 50 个邀请对象，请分批建立计划。', 'error')
      return
    }
    next[candidate.candidateKey] = candidate
  }
  invitationTargets.value = next
  actionError.value = ''
}

watch(
  () => [
    filters.keyword,
    filters.region,
    filters.category,
    filters.publishForm,
    filters.minPrice,
    filters.maxPrice,
    filters.maxDays,
    filters.linkSupport,
    filters.linkType,
    filters.newsSource,
    filters.entryLevel,
    filters.specialIndustry,
    filters.weekendPolicy,
    filters.sort,
  ],
  () => {
    window.clearTimeout(searchTimer)
    searchTimer = window.setTimeout(() => {
      page.value = 1
      load()
    }, 350)
  },
)

watch(
  () => [route.path, route.query.type, route.query.projectId],
  () => {
    const next = channelTypeFromRoute()
    if (next !== type.value) {
      applyType(next)
      return
    }
    project.value = null
    load()
  },
)

onMounted(() => {
  load()
  loadChannelTaxonomy()
})
</script>

<template>
  <PageHeader eyebrow="渠道资源" :title="typeCopy[type].title" :description="typeCopy[type].text">
    <span v-if="project" class="context-chip">项目：{{ project.project.projectName }}</span>
  </PageHeader>

  <section
    v-if="type === 'DIRECT_PUBLISHING'"
    class="media-discovery-notice channel-verification-notice"
    role="status"
  >
    <CircleAlert :size="17" />
    <span>
      {{ directCatalogNotice }}
    </span>
  </section>

  <MediaDiscoveryPanel
    v-if="type === 'MEDIA_PR'"
    workflow="MEDIA_PR"
    action-label="选择对象"
    :selected-keys="selectedInvitationKeys"
    :busy="submitting"
    @select="useMediaCandidate"
  />

  <section v-if="type === 'DIRECT_PUBLISHING'" class="panel channel-filter-panel">
    <div class="input-icon search-input">
      <Search :size="17" /><input
        v-model="filters.keyword"
        aria-label="搜索媒体渠道"
        placeholder="搜索媒体名称或分类"
      />
    </div>
    <select v-model="filters.region" aria-label="按地区筛选">
      <option value="">全部地区</option>
      <option v-for="option in channelTaxonomy.regions" :key="option" :value="option">
        {{ option }}
      </option>
    </select>
    <select v-model="filters.category" aria-label="按分类筛选">
      <option value="">全部分类</option>
      <option v-for="option in channelTaxonomy.categories" :key="option" :value="option">
        {{ option }}
      </option>
    </select>
    <select v-model="filters.publishForm" aria-label="按发布形式筛选">
      <option value="">全部发布形式</option>
      <option v-for="option in channelTaxonomy.publishForms" :key="option" :value="option">
        {{ option }}
      </option>
    </select>
    <template v-if="type === 'DIRECT_PUBLISHING'">
      <input
        v-model.number="filters.minPrice"
        inputmode="decimal"
        type="number"
        min="0"
        aria-label="最低客户服务价"
        placeholder="最低价格"
      />
      <input
        v-model.number="filters.maxPrice"
        inputmode="decimal"
        type="number"
        min="0"
        aria-label="最高客户服务价"
        placeholder="最高价格"
      />
      <input
        v-model.number="filters.maxDays"
        inputmode="numeric"
        type="number"
        min="1"
        max="365"
        aria-label="最长发布时效"
        placeholder="最长时效（工作日）"
      />
      <select v-model="filters.linkSupport" aria-label="按链接支持方式筛选">
        <option value="">链接要求不限</option>
        <option value="true">支持保留链接</option>
        <option value="false">不要求保留链接</option>
      </select>
      <select v-model="filters.sort" aria-label="渠道排序方式">
        <option value="PRICE_ASC">价格从低到高</option>
        <option value="PRICE_DESC">价格从高到低</option>
        <option value="DELIVERY_ASC">时效优先</option>
        <option value="NAME_ASC">名称排序</option>
      </select>
    </template>
    <details class="channel-advanced-filters">
      <summary>更多筛选 <small>目录参考</small></summary>
      <p>以下字段从现有渠道说明中的同名标签整理，仅用于缩小范围，最终要求以项目核验为准。</p>
      <div>
        <select v-model="filters.linkType" aria-label="按链接类型筛选">
          <option value="">链接类型不限</option>
          <option v-for="option in channelTaxonomy.linkTypes" :key="option" :value="option">
            {{ option }}
          </option>
        </select>
        <select v-model="filters.newsSource" aria-label="按新闻源参考筛选">
          <option value="">新闻源参考不限</option>
          <option v-for="option in channelTaxonomy.newsSources" :key="option" :value="option">
            {{ option }}
          </option>
        </select>
        <select v-model="filters.entryLevel" aria-label="按入口级别筛选">
          <option value="">入口级别不限</option>
          <option v-for="option in channelTaxonomy.entryLevels" :key="option" :value="option">
            {{ option }}
          </option>
        </select>
        <select v-model="filters.specialIndustry" aria-label="按特殊行业要求筛选">
          <option value="">特殊行业要求不限</option>
          <option v-for="option in channelTaxonomy.specialIndustries" :key="option" :value="option">
            {{ option }}
          </option>
        </select>
        <select v-model="filters.weekendPolicy" aria-label="按周末发布参考筛选">
          <option value="">周末发布不限</option>
          <option v-for="option in channelTaxonomy.weekendPolicies" :key="option" :value="option">
            周末{{ option }}
          </option>
        </select>
      </div>
    </details>
    <button class="button ghost filter-reset" type="button" @click="resetFilters">
      <FilterX :size="16" />重置
    </button>
    <span class="filter-count">{{ total.toLocaleString('zh-CN') }} 项</span>
  </section>

  <DataState
    v-if="type === 'DIRECT_PUBLISHING'"
    :loading="loading"
    :error="error"
    :empty="!items.length"
    :empty-title="directCatalogEmptyTitle"
    :empty-text="directCatalogEmptyText"
    @retry="load"
  >
    <template #content>
      <section class="panel table-panel direct-channel-table">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th v-if="auth.user?.role === 'CUSTOMER'">
                  <button
                    class="table-check"
                    type="button"
                    :class="{ checked: pageAllSelected }"
                    :aria-label="pageAllSelected ? '取消本页选择' : '选择本页渠道'"
                    @click="togglePage"
                  >
                    <Check :size="15" />
                  </button>
                </th>
                <th>渠道</th>
                <th>分类 / 地区</th>
                <th>发布形式</th>
                <th>目录属性</th>
                <th>预计时效</th>
                <th>客户服务价</th>
                <th>报价有效期</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="channel in items"
                :key="channel.id"
                :class="{ selected: selected[channel.id] }"
                @click="auth.user?.role === 'CUSTOMER' && toggle(channel)"
              >
                <td v-if="auth.user?.role === 'CUSTOMER'">
                  <button
                    class="table-check"
                    type="button"
                    :class="{ checked: selected[channel.id] }"
                    :aria-label="selected[channel.id] ? '取消选择' : '选择渠道'"
                    @click.stop="toggle(channel)"
                  >
                    <Check :size="15" />
                  </button>
                </td>
                <td>
                  <strong>{{ channel.channelName }}</strong>
                </td>
                <td>
                  {{ channel.category || '未分类' }}<small>{{ channel.region || '未设置' }}</small>
                </td>
                <td>{{ channel.publishForm || '图文' }}</td>
                <td>
                  <div class="channel-reference-tags">
                    <span v-if="channel.linkType">{{ channel.linkType }}</span>
                    <span v-if="channel.newsSource">{{ channel.newsSource }}</span>
                    <span v-if="channel.entryLevel">{{ channel.entryLevel }}</span>
                    <span
                      v-if="channel.specialIndustry && channel.specialIndustry !== '无特别标注'"
                      >{{ channel.specialIndustry }}</span
                    >
                    <span v-if="channel.weekendPolicy">周末{{ channel.weekendPolicy }}</span>
                    <small
                      v-if="
                        !channel.linkType &&
                        !channel.newsSource &&
                        !channel.entryLevel &&
                        !channel.weekendPolicy
                      "
                      >待核验</small
                    >
                  </div>
                </td>
                <td>
                  {{ channel.expectedDays ? `${channel.expectedDays} 个工作日` : '另行确认' }}
                </td>
                <td>
                  <strong>{{ money(channel.customerPrice) }}</strong>
                </td>
                <td>
                  {{
                    channel.validUntil
                      ? new Date(channel.validUntil).toLocaleDateString('zh-CN')
                      : '—'
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <PaginationBar :page="page" :page-size="30" :total="total" @change="changePage" />
    </template>
    <RouterLink
      v-if="directCatalogAwaitingConfirmation && auth.user?.role === 'CUSTOMER'"
      class="button primary"
      to="/requirements/new?service=DIRECT_PUBLISHING"
    >
      创建直编发稿项目
    </RouterLink>
  </DataState>

  <section
    v-if="
      auth.user?.role === 'CUSTOMER' &&
      selectedCount > 0 &&
      !(type === 'DIRECT_PUBLISHING' && directCatalogAwaitingConfirmation)
    "
    class="selection-dock channel-selection-dock"
  >
    <div>
      <strong>已选 {{ selectedCount }} 项</strong
      ><span v-if="type === 'DIRECT_PUBLISHING'"
        >当前客户服务价合计
        {{ money(selectedTotal) }}；提交后进入项目核验，不代表已向渠道提交。</span
      ><span v-else>邀请对象、沟通进度和反馈将进入任务管理；媒体自主决定是否报道。</span>
    </div>
    <div v-if="type === 'MEDIA_PR' && selectedCount" class="invitation-target-summary">
      <article
        v-for="candidate in selectedInvitationTargets.slice(0, 6)"
        :key="candidate.candidateKey"
      >
        <span>
          <strong>{{ candidate.reporterName || candidate.displayName }}</strong>
          <small v-if="candidate.reporterName">{{ candidate.displayName }}</small>
        </span>
        <button
          type="button"
          :aria-label="`移除${candidate.reporterName || candidate.displayName}`"
          @click="useMediaCandidate(candidate)"
        >
          <X :size="14" />
        </button>
      </article>
      <small v-if="selectedInvitationTargets.length > 6">
        另有 {{ selectedInvitationTargets.length - 6 }} 个对象已选择
      </small>
    </div>
    <p v-if="actionError" class="form-error">{{ actionError }}</p>
    <button
      class="button primary"
      type="button"
      :disabled="submitting || !selectedCount"
      @click="openSubmissionDialog"
    >
      <Send :size="17" />{{
        submitting ? '正在提交' : type === 'MEDIA_PR' ? '提交媒体邀请' : '提交发稿计划'
      }}
    </button>
  </section>

  <div v-if="showSubmissionDialog" class="modal-backdrop" @click.self="closeSubmissionDialog">
    <section
      class="modal-panel publish-plan-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="publish-plan-dialog-title"
    >
      <header>
        <div>
          <span class="eyebrow">提交确认</span>
          <h2 id="publish-plan-dialog-title">
            {{ type === 'MEDIA_PR' ? '核对媒体邀请计划' : '核对发稿计划' }}
          </h2>
          <p v-if="!createdPlan">
            {{
              type === 'MEDIA_PR'
                ? '先保存邀约计划，核对项目和拟邀名单后再提交项目核验。'
                : '先保存发稿计划，核对渠道、价格和项目后再提交项目核验。'
            }}
          </p>
          <p v-else>计划已保存。提交项目核验后，系统会建立任务；渠道可用性、价格和排期仍需确认。</p>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="closeSubmissionDialog">
          <X :size="19" />
        </button>
      </header>

      <div v-if="!submissionProject" class="publish-project-step">
        <label>
          {{ type === 'MEDIA_PR' ? '选择媒体邀请项目' : '选择已有定稿的项目' }}
          <select v-model.number="selectedProjectId" :disabled="projectOptionsLoading">
            <option :value="0">请选择项目</option>
            <option v-for="option in selectableProjects" :key="option.id" :value="option.id">
              {{ option.projectName }}（{{ option.projectNo }}）
            </option>
          </select>
        </label>
        <p v-if="projectOptionsLoading" class="form-hint">正在读取可发稿项目…</p>
        <p v-else-if="!selectableProjects.length" class="empty-inline">
          {{
            type === 'MEDIA_PR'
              ? '暂无可用项目。请先创建媒体邀请项目。'
              : '暂无可发稿的客户定稿。请先在直编发稿项目中提交或确认稿件版本。'
          }}
        </p>
        <div
          v-if="type === 'DIRECT_PUBLISHING' && !selectableProjects.length && projectOptions.length"
          class="project-choice-links"
        >
          <RouterLink
            v-for="option in projectOptions"
            :key="option.id"
            :to="`/projects/${option.id}`"
          >
            {{ option.projectName }}（{{ option.projectNo }}）
            <small>去提交客户定稿</small>
          </RouterLink>
        </div>
      </div>

      <template v-else>
        <div class="publish-confirm-summary">
          <div>
            <small>执行项目</small>
            <strong>{{ submissionProject.project.projectName }}</strong>
          </div>
          <button
            v-if="!project && !createdPlan"
            class="button text-button"
            type="button"
            @click="submissionProject = null"
          >
            更换项目
          </button>
        </div>

        <div class="publish-plan-items">
          <h3>本次计划包含</h3>
          <ul v-if="type === 'DIRECT_PUBLISHING'">
            <li v-for="channel in selectedItems" :key="channel.id">
              <span>
                <strong>{{ channel.channelName }}</strong>
                <small>{{ channel.category || '综合' }} · {{ channel.region || '全国' }}</small>
              </span>
              <em>{{
                type === 'DIRECT_PUBLISHING' ? money(channel.customerPrice) : '媒体邀请'
              }}</em>
            </li>
          </ul>
          <ul v-else>
            <li v-for="candidate in selectedInvitationTargets" :key="candidate.candidateKey">
              <span>
                <strong>{{ candidate.reporterName || candidate.displayName }}</strong>
                <small>
                  {{
                    candidate.reporterName
                      ? `${candidate.displayName} · 记者邀请`
                      : `${candidate.attribute || '媒体'} · 媒体邀请`
                  }}
                </small>
              </span>
              <em>{{ createdPlan ? '待处理' : '待核验' }}</em>
            </li>
          </ul>
          <p v-if="type === 'MEDIA_PR'">
            名单核验完成后，项目专员按任务逐一联系并记录真实回复；媒体是否到场、采访或报道由媒体自主决定。
          </p>
          <p v-if="type === 'MEDIA_PR'">
            保存计划只进入项目核验，不会自动向媒体发出邀请，也不代表媒体已确认参与。
          </p>
          <p v-if="type === 'DIRECT_PUBLISHING'">
            提交后进入项目核验清单。渠道可用性、稿件要求、价格和排期确认后，才进入实际执行。
          </p>
        </div>

        <div class="publish-confirm-total">
          <span>共 {{ selectedCount }} 个计划项</span>
          <strong v-if="type === 'DIRECT_PUBLISHING'">{{ money(selectedTotal) }}</strong>
          <small v-if="!createdPlan">保存计划不会立即生成任务，也不会开始执行。</small>
          <small v-else>计划编号 {{ createdPlan.planNo }}，等待最后确认。</small>
        </div>
      </template>

      <p v-if="dialogError" class="form-error">{{ dialogError }}</p>
      <div class="form-actions">
        <button
          class="button ghost"
          type="button"
          :disabled="submitting"
          @click="closeSubmissionDialog"
        >
          取消
        </button>
        <button
          v-if="!submissionProject"
          class="button primary"
          type="button"
          :disabled="projectOptionsLoading || !selectedProjectId"
          @click="useSelectedProject"
        >
          下一步
        </button>
        <button
          v-else
          class="button primary"
          type="button"
          :disabled="submitting"
          @click="createdPlan ? confirmPublishPlan() : savePublishPlan()"
        >
          <Send :size="17" />{{
            submitting
              ? '正在处理'
              : createdPlan
                ? '提交项目核验并查看任务'
                : type === 'MEDIA_PR'
                  ? '保存邀约计划'
                  : '保存发稿计划'
          }}
        </button>
      </div>
    </section>
  </div>
</template>
