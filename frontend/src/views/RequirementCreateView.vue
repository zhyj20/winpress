<script setup lang="ts">
import {
  CalendarDays,
  Check,
  CircleDollarSign,
  FilePlus2,
  MapPin,
  Newspaper,
  PenTool,
  Phone,
  Presentation,
  UserRound,
  UserRoundSearch,
} from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http, { apiError } from '@/api/http'
import MediaDiscoveryPanel from '@/components/MediaDiscoveryPanel.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  calculateOnsiteWritingAmount,
  isOnsiteService,
  isServiceType,
  ONSITE_WRITING_DAILY_RATE,
  type ServiceType,
} from '@/constants/services'
import { useToastStore } from '@/stores/toast'
import type { ApiResponse, MediaCandidate } from '@/types'

const services: {
  value: ServiceType
  title: string
  copy: string
  badge: string
  icon: typeof PenTool
}[] = [
  {
    value: 'MEDIA_PR',
    title: '媒体邀请',
    copy: '确认议题与采访范围，联系媒体并跟进到场、采访与反馈。媒体是否报道由媒体方自主决定。',
    badge: '按项目确认',
    icon: UserRoundSearch,
  },
  {
    value: 'ONSITE_WRITING',
    title: '云采写（现场服务）',
    copy: '按项目就近匹配专业写手，现场采集素材并完成可发布稿件。',
    badge: `${ONSITE_WRITING_DAILY_RATE} 元 / 人 / 天`,
    icon: PenTool,
  },
  {
    value: 'DIRECT_PUBLISHING',
    title: '已有稿件·直编发稿',
    copy: '提交客户定稿，按媒体、地区、价格与时效选择合适渠道。',
    badge: '按所选媒体计价',
    icon: Newspaper,
  },
  {
    value: 'NEWS_CONFERENCE',
    title: '举办新闻发布会',
    copy: '建立会前、现场和会后项目清单；现场采写、媒体邀请和直编发稿按需分别下单。',
    badge: '确认会务联系人后开通服务',
    icon: Presentation,
  },
]

const visibleServices = services
const route = useRoute()
const router = useRouter()
const toast = useToastStore()
const loading = ref(false)
const error = ref('')
const SUBMISSION_STATE_KEY = 'winpress:requirement-submission:v1'
const conferenceMediaCandidates = ref<Record<string, MediaCandidate>>({})
const conferenceSourceInput = ref<HTMLInputElement | null>(null)
const conferenceSourceFiles = ref<File[]>([])
const MAX_CONFERENCE_SOURCE_FILES = 5
const MAX_CONFERENCE_SOURCE_FILE_SIZE = 20 * 1024 * 1024
interface ApprovedManuscriptSource {
  manuscriptId: number
  versionId: number
  projectId: number
  projectNo: string
  projectName: string
  title: string
  versionNumber: number
  confirmedAt?: string
}
const approvedManuscriptSources = ref<ApprovedManuscriptSource[]>([])
const sourceManuscriptLoading = ref(false)
const sourceManuscriptError = ref('')
const selectedSourceManuscriptKey = ref('')
const invalidServiceLink = computed(() => route.query.invalidService === '1')
const hasRelatedProjectQuery = computed(() => typeof route.query.relatedProjectId === 'string')
const relatedProjectId = computed<number | null>(() => {
  const raw = route.query.relatedProjectId
  const value = typeof raw === 'string' && /^\d+$/.test(raw) ? Number(raw) : Number.NaN
  return Number.isSafeInteger(value) && value > 0 ? value : null
})
const invalidRelatedProjectLink = computed(
  () => hasRelatedProjectQuery.value && relatedProjectId.value === null,
)

function requestedServiceFromRoute() {
  const routeService = route.meta.service
  if (typeof routeService === 'string') return routeService
  return typeof route.query.service === 'string' ? route.query.service : ''
}

function serviceFromRoute(): ServiceType {
  const requested = requestedServiceFromRoute()
  return isServiceType(requested) ? requested : 'MEDIA_PR'
}

const form = reactive({
  title: '',
  eventTime: '',
  eventLocation: '',
  facts: '',
  objective: '',
  targetAudience: '',
  requestedService: serviceFromRoute(),
  dueAt: '',
  serviceDays: 1,
  writerCount: 1,
  onsiteContactName: '',
  onsiteContactMobile: '',
  deliverableRequirement: '',
  conferenceType: '',
  conferenceFormat: '',
  conferenceScale: '',
  conferenceMediaGoal: '',
  conferenceAgendaStatus: '',
  conferenceVenueStatus: '',
  conferenceContactName: '',
  conferenceContactMobile: '',
})

const isOnsite = computed(() => isOnsiteService(form.requestedService))
const isConference = computed(() => form.requestedService === 'NEWS_CONFERENCE')
const conferenceMediaCandidateItems = computed(() => Object.values(conferenceMediaCandidates.value))
const conferenceMediaCandidateKeys = computed(() => Object.keys(conferenceMediaCandidates.value))
const fixedServiceMode = computed(
  () => route.meta.service === 'ONSITE_WRITING' || route.meta.service === 'NEWS_CONFERENCE',
)
const factsLabel = computed(() => {
  if (isConference.value) return '补充说明'
  if (form.requestedService === 'MEDIA_PR') return '邀请事项'
  if (form.requestedService === 'ONSITE_WRITING') return '采写事项'
  return '稿件事实'
})
const selectedService = computed(() =>
  services.find((item) => item.value === form.requestedService)!,
)
const selectedSourceManuscript = computed(() =>
  approvedManuscriptSources.value.find(
    (item) => `${item.manuscriptId}:${item.versionId}` === selectedSourceManuscriptKey.value,
  ),
)
const pageTitle = computed(() =>
  fixedServiceMode.value
    ? form.requestedService === 'NEWS_CONFERENCE'
      ? '举办新闻发布会'
      : '云采写'
    : '提交服务需求',
)
const pageDescription = computed(() =>
  fixedServiceMode.value
    ? form.requestedService === 'NEWS_CONFERENCE'
      ? '标题、会务联系人和联系电话为必填；其他资料可后补，拟邀媒体可在本页筛选。'
      : '填写活动时间、地点、写手人数和稿件要求，平台按所在地及档期匹配写手。'
    : '选择本次服务并填写基本信息；四项服务分别下单、分别计价。',
)
const fixedServicePoints = computed(() =>
  form.requestedService === 'NEWS_CONFERENCE'
    ? ['仅三项必填', '本页筛选拟邀媒体', '其他传播服务分别下单']
    : ['按活动地点与档期就近匹配写手', '写手负责资料梳理、现场采集、稿件撰写与修改'],
)
const estimatedAmount = computed(() =>
  calculateOnsiteWritingAmount(form.serviceDays, form.writerCount),
)
const toIso = (value: string) => (value ? new Date(value).toISOString() : null)

function addConferenceMediaCandidates(candidates: MediaCandidate[]) {
  const next = { ...conferenceMediaCandidates.value }
  for (const candidate of candidates) {
    next[candidate.candidateKey] = candidate
  }
  conferenceMediaCandidates.value = next
  error.value = ''
}

function removeConferenceMediaCandidate(candidate: MediaCandidate) {
  const next = { ...conferenceMediaCandidates.value }
  delete next[candidate.candidateKey]
  conferenceMediaCandidates.value = next
}

function conferenceSourceFileKey(file: File) {
  return `${file.name}:${file.size}:${file.lastModified}`
}

function selectConferenceSourceFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files || [])
  input.value = ''
  if (!selected.length) return

  const oversized = selected.filter((file) => file.size > MAX_CONFERENCE_SOURCE_FILE_SIZE)
  if (oversized.length) {
    error.value = '单个资料不得超过 20MB，请移除超出限制的文件后重试。'
    return
  }

  const existing = new Set(conferenceSourceFiles.value.map(conferenceSourceFileKey))
  const additions = selected.filter((file) => !existing.has(conferenceSourceFileKey(file)))
  const next = [...conferenceSourceFiles.value, ...additions]
  if (next.length > MAX_CONFERENCE_SOURCE_FILES) {
    error.value = `一次最多上传 ${MAX_CONFERENCE_SOURCE_FILES} 份选题资料。`
    return
  }
  conferenceSourceFiles.value = next
  error.value = ''
}

function removeConferenceSourceFile(file: File) {
  const key = conferenceSourceFileKey(file)
  conferenceSourceFiles.value = conferenceSourceFiles.value.filter(
    (item) => conferenceSourceFileKey(item) !== key,
  )
}

async function uploadConferenceSourceFiles(projectId: number) {
  let uploaded = 0
  let failed = 0
  for (const file of conferenceSourceFiles.value) {
    const body = new FormData()
    body.append('file', file)
    body.append('projectId', String(projectId))
    try {
      await http.post('/files', body, { headers: { 'Content-Type': 'multipart/form-data' } })
      uploaded += 1
    } catch {
      failed += 1
    }
  }
  return { uploaded, failed }
}

async function loadApprovedManuscriptSources() {
  if (form.requestedService !== 'DIRECT_PUBLISHING' || sourceManuscriptLoading.value) return
  sourceManuscriptLoading.value = true
  sourceManuscriptError.value = ''
  try {
    const { data } = await http.get<ApiResponse<ApprovedManuscriptSource[]>>(
      '/customer/approved-manuscripts',
    )
    approvedManuscriptSources.value = data.data
    if (selectedSourceManuscriptKey.value && !selectedSourceManuscript.value) {
      selectedSourceManuscriptKey.value = ''
    }
  } catch (sourceError) {
    sourceManuscriptError.value = apiError(sourceError)
  } finally {
    sourceManuscriptLoading.value = false
  }
}

watch(
  () => [route.path, route.query.service],
  () => {
    const next = serviceFromRoute()
    if (form.requestedService !== next) form.requestedService = next
    error.value = ''
  },
)

watch(
  () => form.requestedService,
  (serviceType) => {
    if (serviceType === 'DIRECT_PUBLISHING') {
      void loadApprovedManuscriptSources()
    } else {
      selectedSourceManuscriptKey.value = ''
      sourceManuscriptError.value = ''
    }
  },
)

onMounted(() => {
  if (form.requestedService === 'DIRECT_PUBLISHING') {
    void loadApprovedManuscriptSources()
  }
})

async function payloadFingerprint(payload: object) {
  const bytes = new TextEncoder().encode(JSON.stringify(payload))
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

function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `wp-${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`
}

async function idempotencyKeyFor(payload: object) {
  const fingerprint = await payloadFingerprint(payload)
  try {
    const stored = JSON.parse(sessionStorage.getItem(SUBMISSION_STATE_KEY) || 'null') as {
      fingerprint?: string
      key?: string
    } | null
    if (stored?.fingerprint === fingerprint && stored.key) return stored.key
  } catch {
    sessionStorage.removeItem(SUBMISSION_STATE_KEY)
  }
  const key = newIdempotencyKey()
  sessionStorage.setItem(SUBMISSION_STATE_KEY, JSON.stringify({ fingerprint, key }))
  return key
}

function clearSubmissionState(key: string) {
  try {
    const stored = JSON.parse(sessionStorage.getItem(SUBMISSION_STATE_KEY) || 'null') as {
      key?: string
    } | null
    if (stored?.key === key) sessionStorage.removeItem(SUBMISSION_STATE_KEY)
  } catch {
    sessionStorage.removeItem(SUBMISSION_STATE_KEY)
  }
}

async function submit() {
  if (loading.value) return
  if (invalidServiceLink.value) {
    error.value = '服务链接无效，请从服务列表重新选择。'
    return
  }
  if (invalidRelatedProjectLink.value) {
    error.value = '关联活动链接无效，请返回原项目后重新发起服务。'
    return
  }
  loading.value = true
  error.value = ''

  try {
    const payload = {
      ...form,
      eventTime: toIso(form.eventTime),
      dueAt: toIso(form.dueAt),
      serviceDays: isOnsite.value ? Number(form.serviceDays) : null,
      writerCount: isOnsite.value ? Number(form.writerCount) : null,
      onsiteContactName: isOnsite.value ? form.onsiteContactName : null,
      onsiteContactMobile: isOnsite.value ? form.onsiteContactMobile : null,
      deliverableRequirement: form.deliverableRequirement || null,
      conferenceType: isConference.value ? form.conferenceType : null,
      conferenceFormat: isConference.value ? form.conferenceFormat : null,
      conferenceScale: isConference.value ? form.conferenceScale || null : null,
      conferenceMediaGoal: isConference.value ? form.conferenceMediaGoal : null,
      conferenceAgendaStatus: isConference.value ? form.conferenceAgendaStatus : null,
      conferenceVenueStatus: isConference.value ? form.conferenceVenueStatus : null,
      conferenceContactName: isConference.value ? form.conferenceContactName : null,
      conferenceContactMobile: isConference.value ? form.conferenceContactMobile : null,
      relatedProjectId: relatedProjectId.value,
      sourceManuscriptId: selectedSourceManuscript.value?.manuscriptId || null,
      sourceManuscriptVersionId: selectedSourceManuscript.value?.versionId || null,
    }
    const idempotencyKey = await idempotencyKeyFor(payload)
    const { data } = await http.post<ApiResponse<{ projectId: number }>>('/requirements', payload, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
    clearSubmissionState(idempotencyKey)
    const postCreateIssues: string[] = []
    const conferenceCandidateCount = conferenceMediaCandidateItems.value.length
    let uploadedConferenceSourceFiles = 0
    if (isConference.value && conferenceSourceFiles.value.length) {
      const uploadResult = await uploadConferenceSourceFiles(data.data.projectId)
      uploadedConferenceSourceFiles = uploadResult.uploaded
      if (uploadResult.failed) {
        postCreateIssues.push(`${uploadResult.failed} 份选题资料未上传`)
      }
    }
    if (isConference.value && conferenceCandidateCount) {
      try {
        await http.post(`/projects/${data.data.projectId}/conference-media-candidates/batch`, {
          candidates: conferenceMediaCandidateItems.value,
        })
      } catch {
        postCreateIssues.push('拟邀名单未保存')
      }
    }
    if (postCreateIssues.length) {
      toast.show(`项目已创建，但${postCreateIssues.join('；')}。请在项目详情中补充。`, 'error')
      await router.push(`/projects/${data.data.projectId}`)
      return
    }
    if (isConference.value) {
      conferenceSourceFiles.value = []
      conferenceMediaCandidates.value = {}
    }
    toast.show(
      isOnsite.value
        ? `云采写订单已提交，基础金额 ¥${estimatedAmount.value.toLocaleString('zh-CN')}`
        : isConference.value
          ? `新闻发布会项目已创建${
              uploadedConferenceSourceFiles
                ? `，已上传 ${uploadedConferenceSourceFiles} 份选题资料`
                : ''
            }${conferenceCandidateCount ? `，已保存 ${conferenceCandidateCount} 个拟邀对象` : ''}`
          : form.requestedService === 'DIRECT_PUBLISHING' && selectedSourceManuscript.value
            ? '直编发稿项目已创建，客户定稿已复制'
            : '服务需求已提交，项目已创建',
      'success',
    )
    const returnTo = route.query.returnTo
    if (returnTo === 'media-invitation' && form.requestedService === 'MEDIA_PR') {
      router.push(`/media-invitation?projectId=${data.data.projectId}`)
    } else {
      router.push(`/projects/${data.data.projectId}`)
    }
  } catch (e) {
    error.value = apiError(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-back-row">
    <RouterLink class="button secondary" to="/">返回首页</RouterLink>
  </div>

  <PageHeader
    :eyebrow="fixedServiceMode ? '' : '提交需求'"
    :title="pageTitle"
    :description="pageDescription"
  />
  <div v-if="relatedProjectId" class="activity-link-note">
    <div>
      <strong>同一活动下的新服务</strong>
      <span>本次服务会单独生成项目、任务和订单；不与原项目合并计价。</span>
    </div>
    <RouterLink class="text-link" :to="`/projects/${relatedProjectId}`">查看原项目</RouterLink>
  </div>
  <p v-if="invalidServiceLink" class="form-error">
    服务链接无效，请从下方选择四项可下单服务，或联系平台咨询 API 接入。
  </p>
  <p v-if="invalidRelatedProjectLink" class="form-error">
    关联活动链接无效，请返回原项目后重新发起服务。
  </p>

  <div class="order-layout">
    <form class="panel form-panel order-form" @submit.prevent="submit">
      <section v-if="!fixedServiceMode" class="form-section">
        <div class="form-section-title">
          <span>1</span>
          <div>
            <h2>服务类型</h2>
            <p>请选择本次要先启动的服务；其他服务需要时可另行下单。</p>
          </div>
        </div>

        <div class="choice-grid service-choice-grid">
          <label
            v-for="item in visibleServices"
            :key="item.value"
            :class="{ selected: form.requestedService === item.value }"
          >
            <input v-model="form.requestedService" type="radio" :value="item.value" />
            <component :is="item.icon" :size="20" />
            <strong>{{ item.title }}</strong>
            <span>{{ item.copy }}</span>
            <small>{{ item.badge }}</small>
            <Check :size="17" class="choice-check" />
          </label>
        </div>
      </section>

      <section v-else class="form-section">
        <div class="service-intro-card">
          <span class="service-intro-step" aria-hidden="true">1</span>
          <component :is="selectedService.icon" :size="24" />
          <div>
            <strong>{{ selectedService.title }}</strong>
            <p>{{ selectedService.copy }}</p>
            <div class="service-intro-tags">
              <span>{{ selectedService.badge }}</span>
              <span v-for="point in fixedServicePoints" :key="point">{{ point }}</span>
            </div>
          </div>
        </div>
      </section>

      <section v-if="form.requestedService === 'DIRECT_PUBLISHING'" class="form-section">
        <div class="form-section-title">
          <span>2</span>
          <div>
            <h2>稿件来源</h2>
            <p>可选用本账号已确认的稿件版本；也可创建项目后再提交已有定稿。</p>
          </div>
        </div>
        <div class="form-grid two-columns">
          <label class="full">
            客户已确认稿件（选填）
            <select v-model="selectedSourceManuscriptKey" :disabled="sourceManuscriptLoading">
              <option value="">不复用稿件，创建后在项目中提交已有定稿</option>
              <option
                v-for="item in approvedManuscriptSources"
                :key="`${item.manuscriptId}:${item.versionId}`"
                :value="`${item.manuscriptId}:${item.versionId}`"
              >
                {{ item.title }} · {{ item.projectName }}（第 {{ item.versionNumber }} 版）
              </option>
            </select>
          </label>
          <p v-if="sourceManuscriptLoading" class="form-hint full">正在读取可复用的客户定稿…</p>
          <p v-else-if="sourceManuscriptError" class="form-error full">
            {{ sourceManuscriptError }}
          </p>
          <p v-else-if="!approvedManuscriptSources.length" class="form-hint full">
            暂无可复用的客户定稿。可以先创建本项目，再在项目详情中提交已有定稿。
          </p>
          <p class="form-hint full">
            选择后会将当前确认版本复制到本次直编发稿项目；原项目、任务和订单保持独立，不合并计价。
          </p>
        </div>
      </section>

      <section class="form-section">
        <div class="form-section-title">
          <span>{{ form.requestedService === 'DIRECT_PUBLISHING' ? 3 : 2 }}</span>
          <div>
            <h2>项目信息</h2>
            <p class="small-note">活动与服务安排</p>
            <p>
              {{
                isOnsite
                  ? '现场采写建议填写准确日期、地点和联系人信息，便于排期与对接。'
                  : isConference
                    ? '举办新闻发布会强调流程完整，先填会务联系人及电话，其余信息可后补。'
                    : '请提供完整的基础信息，便于我们精确匹配服务路径。'
              }}
            </p>
          </div>
        </div>
        <div class="form-grid two-columns">
          <label class="full">
            <span class="field-label">需求标题<span class="required">*</span></span>
            <input
              v-model="form.title"
              required
              maxlength="200"
              placeholder="例如：X厂新品发布会媒体邀请与传播"
            />
          </label>

          <label>
            <span class="field-label">
              活动或服务开始时间<span v-if="isOnsite" class="required">*</span
              ><span v-else>（选填）</span>
            </span>
            <div class="input-icon">
              <CalendarDays :size="18" />
              <input v-model="form.eventTime" type="datetime-local" :required="isOnsite" />
            </div>
          </label>

          <label>
            <span class="field-label">
              服务地点<span v-if="isOnsite" class="required">*</span><span v-else>（选填）</span>
            </span>
            <div class="input-icon">
              <MapPin :size="18" />
              <input
                v-model="form.eventLocation"
                maxlength="200"
                :required="isOnsite"
                placeholder="城市 + 具体场所"
              />
            </div>
          </label>

          <template v-if="isOnsite">
            <label>
              <span class="field-label">服务天数<span class="required">*</span></span>
              <input v-model.number="form.serviceDays" type="number" min="1" max="30" required
            /></label>
            <label>
              <span class="field-label">写手人数<span class="required">*</span></span>
              <input v-model.number="form.writerCount" type="number" min="1" max="10" required />
            </label>
            <label>
              <span class="field-label">现场联系人<span class="required">*</span></span>
              <div class="input-icon">
                <UserRound :size="18" />
                <input
                  v-model="form.onsiteContactName"
                  required
                  maxlength="80"
                  placeholder="填写现场联系人姓名"
                />
              </div>
            </label>
            <label>
              <span class="field-label">现场联系电话<span class="required">*</span></span>
              <div class="input-icon">
                <Phone :size="18" />
                <input
                  v-model="form.onsiteContactMobile"
                  required
                  maxlength="30"
                  inputmode="tel"
                  pattern="1[3-9]\d{9}"
                  placeholder="11位手机号码"
                />
              </div>
            </label>
          </template>

          <template v-if="isConference">
            <label
              >发布会类型（选填）
              <select v-model="form.conferenceType">
                <option value="">选择：不限</option>
                <option value="PRODUCT_RELEASE">产品发布</option>
                <option value="STRATEGIC_SIGNING">战略合作</option>
                <option value="INDUSTRY_FORUM">行业论坛</option>
                <option value="CORPORATE_EVENT">企业活动</option>
              </select>
            </label>

            <label
              >举办形式（选填）
              <select v-model="form.conferenceFormat">
                <option value="">选择：不限</option>
                <option value="OFFLINE">线下举办</option>
                <option value="HYBRID">混合举办</option>
                <option value="ONLINE">线上举办</option>
              </select>
            </label>

            <label
              >预计参会人数（选填）
              <input v-model="form.conferenceScale" maxlength="40" placeholder="如：300人" />
            </label>

            <label
              >场地确认情况（选填）
              <select v-model="form.conferenceVenueStatus">
                <option value="">选择：不限</option>
                <option value="PENDING">未确认</option>
                <option value="CONFIRMED">已确认</option>
              </select>
            </label>

            <label
              >议程准备情况（选填）
              <select v-model="form.conferenceAgendaStatus">
                <option value="">选择：不限</option>
                <option value="PREPARING">准备中</option>
                <option value="CONFIRMED">已确认</option>
              </select>
            </label>

            <label>
              <span class="field-label">会务联系人<span class="required">*</span></span>
              <div class="input-icon">
                <UserRound :size="18" />
                <input
                  v-model="form.conferenceContactName"
                  required
                  maxlength="80"
                  placeholder="请输入会务联系人姓名"
                />
              </div>
            </label>

            <label>
              <span class="field-label">联系电话<span class="required">*</span></span>
              <div class="input-icon">
                <Phone :size="18" />
                <input
                  v-model="form.conferenceContactMobile"
                  required
                  maxlength="30"
                  inputmode="tel"
                  pattern="1[3-9]\d{9}"
                  placeholder="11位手机号码"
                />
              </div>
            </label>

            <label class="full"
              >媒体与传播目标（选填）
              <textarea
                v-model="form.conferenceMediaGoal"
                rows="3"
                maxlength="1000"
                placeholder="可填写希望沟通的媒体方向、会后发布时间或需要确认的交付。"
              ></textarea>
            </label>

            <div class="full upload-hint">
              <strong>上传选题资料</strong>
              <p>上传活动策划方案或相关议题材料，便于平台核定媒体名单；也可直接在下方筛选。</p>
              <div class="intake-upload-actions">
                <button
                  class="button secondary"
                  type="button"
                  :disabled="loading"
                  @click="conferenceSourceInput?.click()"
                >
                  <FilePlus2 :size="16" />选择资料
                </button>
                <small
                  >支持 PDF、Word、Excel、JPG、PNG、WebP、TXT；单个不超过 20MB，最多 5 份。</small
                >
              </div>
              <input
                ref="conferenceSourceInput"
                class="visually-hidden"
                type="file"
                multiple
                accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.webp,.txt"
                @change="selectConferenceSourceFiles"
              />
              <ul v-if="conferenceSourceFiles.length" class="intake-file-list">
                <li v-for="file in conferenceSourceFiles" :key="conferenceSourceFileKey(file)">
                  <span>{{ file.name }}</span>
                  <small>{{ Math.ceil(file.size / 1024) }} KB</small>
                  <button
                    type="button"
                    :aria-label="`移除${file.name}`"
                    :disabled="loading"
                    @click="removeConferenceSourceFile(file)"
                  >
                    移除
                  </button>
                </li>
              </ul>
            </div>
          </template>

          <label class="full">
            预期完成时间（选填）
            <input v-model="form.dueAt" type="datetime-local" />
          </label>
        </div>
      </section>

      <section v-if="isConference" class="form-section conference-media-step">
        <div class="form-section-title">
          <span>3</span>
          <div>
            <h2>媒体筛选</h2>
          </div>
        </div>

        <MediaDiscoveryPanel
          workflow="NEWS_CONFERENCE"
          action-label="加入名单"
          :selected-keys="conferenceMediaCandidateKeys"
          :busy="loading"
          @submit="addConferenceMediaCandidates"
        />

        <div v-if="conferenceMediaCandidateItems.length" class="conference-intake-selections">
          <article v-for="candidate in conferenceMediaCandidateItems" :key="candidate.candidateKey">
            <span>
              <strong>{{ candidate.reporterName || candidate.displayName }}</strong>
              <small v-if="candidate.reporterName">{{ candidate.displayName }}</small>
            </span>
            <button
              type="button"
              :aria-label="`移除${candidate.reporterName || candidate.displayName}`"
              @click="removeConferenceMediaCandidate(candidate)"
            >
              移除
            </button>
          </article>
        </div>
      </section>

      <section class="form-section">
        <div class="form-section-title">
          <span>{{ isConference || form.requestedService === 'DIRECT_PUBLISHING' ? 4 : 3 }}</span>
          <div>
            <h2>需求说明</h2>
            <p>补充本次服务的关键事项；未确定的内容可在项目执行中继续完善。</p>
          </div>
        </div>
        <div class="form-grid two-columns">
          <label class="full">
            <span class="field-label">
              {{ factsLabel }}<span v-if="!isConference" class="required">*</span>
            </span>
            <textarea
              v-model="form.facts"
              :required="!isConference"
              rows="5"
              placeholder="请填写活动背景、核心信息、邀请事项或稿件依据"
            ></textarea>
          </label>
          <label class="full">
            传播目标（选填）
            <textarea
              v-model="form.objective"
              rows="4"
              placeholder="可填写希望重点影响的人群、行业议题或传播效果"
            ></textarea>
          </label>
          <label class="full">
            目标受众（选填）
            <input
              v-model="form.targetAudience"
              maxlength="300"
              placeholder="客户、潜在客户、行业关注人群"
            />
          </label>
          <label class="full">
            报道规格（选填）
            <textarea
              v-model="form.deliverableRequirement"
              rows="3"
              maxlength="2000"
              placeholder="报道方向、稿件类型、配图、发布时间或其他要求"
            ></textarea>
          </label>
        </div>
      </section>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>

      <div class="form-actions">
        <RouterLink
          v-if="form.requestedService === 'DIRECT_PUBLISHING'"
          class="button secondary"
          to="/direct-publishing"
        >
          筛选媒体
        </RouterLink>
        <button class="button primary" type="submit" :disabled="loading">
          {{
            loading
              ? '提交中'
              : isOnsite
                ? '提交现场采写'
                : isConference
                  ? '提交新闻发布会'
                  : '提交服务需求'
          }}
        </button>
      </div>
    </form>

    <aside class="panel order-summary">
      <span class="eyebrow">项目预览</span>
      <h2>{{ selectedService.title }}</h2>
      <p>{{ selectedService.copy }}</p>
      <div v-if="isOnsite" class="order-price-box">
        <CircleDollarSign :size="21" />
        <div>
          <small>预计金额</small>
          <strong>¥{{ estimatedAmount.toLocaleString('zh-CN') }}</strong>
        </div>
      </div>
      <dl v-if="isOnsite">
        <div>
          <dt>单价</dt>
          <dd>¥{{ ONSITE_WRITING_DAILY_RATE }} / 人 / 天</dd>
        </div>
        <div>
          <dt>写手</dt>
          <dd>{{ form.writerCount || 1 }} 人</dd>
        </div>
        <div>
          <dt>天数</dt>
          <dd>{{ form.serviceDays || 1 }} 天</dd>
        </div>
      </dl>
      <dl v-else-if="isConference">
        <div>
          <dt>服务内容</dt>
          <dd>新闻发布会项目清单（会务联系人优先）</dd>
        </div>
        <div>
          <dt>需求说明</dt>
          <dd>请先确认会务联系人与联系电话，其他字段可后补</dd>
        </div>
      </dl>
      <dl v-else>
        <div>
          <dt>服务说明</dt>
          <dd>确认目标与基础信息，系统将按服务类型生成处理流程。</dd>
        </div>
      </dl>
      <div class="order-boundary">
        <FilePlus2 :size="18" />
        <span v-if="isOnsite">现场采写优先就近派单；如需媒体邀请，请另行下单。</span>
        <span v-else-if="isConference">发布会不做冗余强制信息采集，先把会务联系人核对到位。</span>
        <span v-else>提交需求后将进入项目执行池，请按提示上传补充信息。</span>
      </div>
    </aside>
  </div>
</template>
