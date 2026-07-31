<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Cable,
  CheckCircle2,
  ClipboardCheck,
  FileWarning,
  History,
  KeyRound,
  PencilLine,
  Plus,
  RefreshCw,
  ServerCog,
  ShieldCheck,
  X,
} from 'lucide-vue-next'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import http, { apiError } from '@/api/http'
import { useToastStore } from '@/stores/toast'

type Tab = 'connections' | 'acceptance' | 'legacy'

interface Summary {
  connectionCount: number
  enabledConnectionCount: number
  configurationReadyCount: number
  pendingGateCount: number
  requiredEvidenceItemCount: number
  verifiedRequiredEvidenceItemCount: number
  legacyReviewCount: number
  pendingLegacyReviewCount: number
}

interface BuiltInAdapter {
  code: string
  name: string
  connectionKind: string
  runtimeConfigured: boolean
  operationalStatus: string
  acceptanceStatus: string
  customerFallback: string
}

interface Connection {
  id: number
  connectionNo: string
  supplierId?: number
  supplierName?: string
  connectionName: string
  providerCode: string
  connectionKind: string
  environment: string
  baseUrl: string
  authType: string
  authHeaderName?: string
  credentialEnvKey?: string
  capabilityScope?: string
  mediaSearchPath?: string
  reporterSearchPath?: string
  quotePath?: string
  orderPath?: string
  orderStatusPath?: string
  callbackPath?: string
  reconciliationPath?: string
  slaReference?: string
  rateLimitPerMinute: number
  timeoutSeconds: number
  maxRetries: number
  dataScope?: string
  contractReference?: string
  authorizationStatus: string
  authorizationEvidenceRef?: string
  sandboxStatus: string
  sandboxEvidenceRef?: string
  productionStatus: string
  productionEvidenceRef?: string
  internalNote?: string
  enabled: boolean
  credentialConfigured: boolean
  acceptanceReady: boolean
  configurationReady: boolean
  lastConfigCheckedAt?: string
  lastConfigCheckStatus?: string
  lastConfigCheckDetail?: string
}

interface AcceptanceGate {
  id: number
  gateCode: string
  gateName: string
  status: string
  evidenceReference?: string
  reviewNote?: string
  reviewedAt?: string
  reviewedBy?: string
  requiredItemCount: number
  verifiedRequiredItemCount: number
  pendingRequiredItemCount: number
}

interface AcceptanceEvidenceItem {
  id: number
  gateCode: string
  itemCode: string
  itemName: string
  required: boolean
  itemStatus: string
  evidenceReference?: string
  reviewNote?: string
  reviewedAt?: string
  reviewedBy?: string
}

interface LegacyReview {
  id: number
  reviewNo: string
  requirementNo: string
  title: string
  organizationName: string
  requirementStatus: string
  reviewStatus: string
  approvedAction?: string
  evidenceReference?: string
  businessNote?: string
  projectNos?: string
  taskCount: number
  settlementCount: number
  reviewedAt?: string
  reviewedBy?: string
}

interface Overview {
  summary: Summary
  builtInAdapters: BuiltInAdapter[]
  connections: Connection[]
  acceptanceGates: AcceptanceGate[]
  acceptanceEvidenceItems: AcceptanceEvidenceItem[]
  legacyServiceReviews: LegacyReview[]
  securityNotice: string
}

interface SupplierOption {
  id: number
  supplierName: string
}

const toast = useToastStore()
const tab = ref<Tab>('connections')
const loading = ref(true)
const saving = ref(false)
const checkingId = ref<number | null>(null)
const error = ref('')
const actionError = ref('')
const overview = ref<Overview | null>(null)
const suppliers = ref<SupplierOption[]>([])
const connectionTarget = ref<Connection | null>(null)
const connectionModal = ref(false)
const gateTarget = ref<AcceptanceGate | null>(null)
const evidenceTarget = ref<AcceptanceEvidenceItem | null>(null)
const legacyTarget = ref<LegacyReview | null>(null)

const connectionForm = reactive({
  supplierId: '' as number | '',
  connectionName: '',
  providerCode: '',
  connectionKind: 'MEDIA_DATA',
  environment: 'SANDBOX',
  baseUrl: '',
  authType: 'BEARER',
  authHeaderName: 'Authorization',
  credentialEnvKey: '',
  capabilityScope: '',
  mediaSearchPath: '',
  reporterSearchPath: '',
  quotePath: '',
  orderPath: '',
  orderStatusPath: '',
  callbackPath: '',
  reconciliationPath: '',
  slaReference: '',
  rateLimitPerMinute: 60,
  timeoutSeconds: 15,
  maxRetries: 2,
  dataScope: '',
  contractReference: '',
  authorizationStatus: 'NOT_SUBMITTED',
  authorizationEvidenceRef: '',
  sandboxStatus: 'NOT_TESTED',
  sandboxEvidenceRef: '',
  productionStatus: 'NOT_APPROVED',
  productionEvidenceRef: '',
  internalNote: '',
  enabled: false,
})

const gateForm = reactive({
  status: 'PENDING',
  evidenceReference: '',
  reviewNote: '',
})

const evidenceForm = reactive({
  itemStatus: 'PENDING',
  evidenceReference: '',
  reviewNote: '',
})

const legacyForm = reactive({
  reviewStatus: 'PENDING',
  approvedAction: '',
  evidenceReference: '',
  businessNote: '',
})

const summary = computed(() => overview.value?.summary)

const statusLabels: Record<string, string> = {
  READY: '配置齐备',
  BLOCKED: '仍有缺项',
  PASSED: '已通过',
  PENDING: '待验收',
  IN_REVIEW: '审核中',
  VERIFIED: '已核验',
  NOT_SUBMITTED: '未提交',
  REJECTED: '未通过',
  NOT_TESTED: '未测试',
  FAILED: '未通过',
  NOT_APPROVED: '未批准',
  NOT_APPLICABLE: '不适用',
  APPROVED: '已批准',
  REVOKED: '已撤销',
  ACCEPTED: '已验收',
  UNAVAILABLE: '暂不可用',
}

const kindLabels: Record<string, string> = {
  MEDIA_DATA: '媒体数据',
  ORDER_FULFILLMENT: '订单履约',
  QUOTE_SYNC: '报价同步',
  GEO_FEDERATION: 'GEO 联动',
}

const legacyActionLabels: Record<string, string> = {
  ARCHIVE_ONLY: '仅归档',
  MAP_TO_ONSITE_WRITING: '映射为云采写',
  MAP_TO_MEDIA_PR: '映射为媒体邀请',
  MAP_TO_DIRECT_PUBLISHING: '映射为直编发稿',
  MAP_TO_NEWS_CONFERENCE: '映射为举办新闻发布会',
  MANUAL_RECONSTRUCTION: '人工重建',
}

function statusTone(status?: string) {
  if (['READY', 'PASSED', 'VERIFIED', 'APPROVED', 'ACCEPTED'].includes(status || '')) {
    return 'success'
  }
  if (['BLOCKED', 'FAILED', 'REJECTED', 'REVOKED', 'UNAVAILABLE'].includes(status || '')) {
    return 'danger'
  }
  return 'warning'
}

function resetConnectionForm() {
  Object.assign(connectionForm, {
    supplierId: '',
    connectionName: '',
    providerCode: '',
    connectionKind: 'MEDIA_DATA',
    environment: 'SANDBOX',
    baseUrl: '',
    authType: 'BEARER',
    authHeaderName: 'Authorization',
    credentialEnvKey: '',
    capabilityScope: '',
    mediaSearchPath: '',
    reporterSearchPath: '',
    quotePath: '',
    orderPath: '',
    orderStatusPath: '',
    callbackPath: '',
    reconciliationPath: '',
    slaReference: '',
    rateLimitPerMinute: 60,
    timeoutSeconds: 15,
    maxRetries: 2,
    dataScope: '',
    contractReference: '',
    authorizationStatus: 'NOT_SUBMITTED',
    authorizationEvidenceRef: '',
    sandboxStatus: 'NOT_TESTED',
    sandboxEvidenceRef: '',
    productionStatus: 'NOT_APPROVED',
    productionEvidenceRef: '',
    internalNote: '',
    enabled: false,
  })
}

function openConnection(item?: Connection) {
  connectionTarget.value = item || null
  resetConnectionForm()
  if (item) {
    Object.assign(connectionForm, {
      supplierId: item.supplierId || '',
      connectionName: item.connectionName,
      providerCode: item.providerCode,
      connectionKind: item.connectionKind,
      environment: item.environment,
      baseUrl: item.baseUrl,
      authType: item.authType,
      authHeaderName: item.authHeaderName || '',
      credentialEnvKey: item.credentialEnvKey || '',
      capabilityScope: item.capabilityScope || '',
      mediaSearchPath: item.mediaSearchPath || '',
      reporterSearchPath: item.reporterSearchPath || '',
      quotePath: item.quotePath || '',
      orderPath: item.orderPath || '',
      orderStatusPath: item.orderStatusPath || '',
      callbackPath: item.callbackPath || '',
      reconciliationPath: item.reconciliationPath || '',
      slaReference: item.slaReference || '',
      rateLimitPerMinute: item.rateLimitPerMinute,
      timeoutSeconds: item.timeoutSeconds,
      maxRetries: item.maxRetries,
      dataScope: item.dataScope || '',
      contractReference: item.contractReference || '',
      authorizationStatus: item.authorizationStatus,
      authorizationEvidenceRef: item.authorizationEvidenceRef || '',
      sandboxStatus: item.sandboxStatus,
      sandboxEvidenceRef: item.sandboxEvidenceRef || '',
      productionStatus: item.productionStatus,
      productionEvidenceRef: item.productionEvidenceRef || '',
      internalNote: item.internalNote || '',
      enabled: item.enabled,
    })
  }
  actionError.value = ''
  connectionModal.value = true
}

function openGate(item: AcceptanceGate) {
  gateTarget.value = item
  Object.assign(gateForm, {
    status: item.status,
    evidenceReference: item.evidenceReference || '',
    reviewNote: item.reviewNote || '',
  })
  actionError.value = ''
}

function evidenceForGate(gateCode: string) {
  return (overview.value?.acceptanceEvidenceItems || []).filter(
    (item) => item.gateCode === gateCode,
  )
}

function openEvidence(item: AcceptanceEvidenceItem) {
  evidenceTarget.value = item
  Object.assign(evidenceForm, {
    itemStatus: item.itemStatus,
    evidenceReference: item.evidenceReference || '',
    reviewNote: item.reviewNote || '',
  })
  actionError.value = ''
}

function openLegacy(item: LegacyReview) {
  legacyTarget.value = item
  Object.assign(legacyForm, {
    reviewStatus: item.reviewStatus,
    approvedAction: item.approvedAction || '',
    evidenceReference: item.evidenceReference || '',
    businessNote: item.businessNote || '',
  })
  actionError.value = ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [overviewResponse, supplierResponse] = await Promise.all([
      http.get('/admin/integrations'),
      http.get('/admin/suppliers/options'),
    ])
    overview.value = overviewResponse.data.data
    suppliers.value = supplierResponse.data.data
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    loading.value = false
  }
}

async function saveConnection() {
  saving.value = true
  actionError.value = ''
  const payload = {
    ...connectionForm,
    supplierId: connectionForm.supplierId || null,
    authHeaderName:
      connectionForm.authType === 'NONE' ? null : connectionForm.authHeaderName || null,
    credentialEnvKey:
      connectionForm.authType === 'NONE' ? null : connectionForm.credentialEnvKey || null,
  }
  try {
    if (connectionTarget.value) {
      await http.put(`/admin/integrations/${connectionTarget.value.id}`, payload)
    } else {
      await http.post('/admin/integrations', payload)
    }
    connectionModal.value = false
    toast.show('接口配置已保存', 'success')
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    saving.value = false
  }
}

async function checkConnection(item: Connection) {
  checkingId.value = item.id
  actionError.value = ''
  try {
    const response = await http.post(`/admin/integrations/${item.id}/check`)
    const result = response.data.data
    toast.show(
      result.configurationReady ? '配置检查通过，未向外部接口发起请求' : result.blockers.join('；'),
      result.configurationReady ? 'success' : 'error',
    )
    await load()
  } catch (requestError) {
    toast.show(apiError(requestError), 'error')
  } finally {
    checkingId.value = null
  }
}

async function saveGate() {
  if (!gateTarget.value) return
  saving.value = true
  actionError.value = ''
  try {
    await http.put(`/admin/integrations/acceptance-gates/${gateTarget.value.gateCode}`, gateForm)
    gateTarget.value = null
    toast.show('上线验收状态已更新', 'success')
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    saving.value = false
  }
}

async function saveEvidence() {
  if (!evidenceTarget.value) return
  saving.value = true
  actionError.value = ''
  try {
    await http.put(
      `/admin/integrations/acceptance-evidence/${evidenceTarget.value.id}`,
      evidenceForm,
    )
    evidenceTarget.value = null
    toast.show('验收材料已更新', 'success')
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    saving.value = false
  }
}

async function saveLegacyReview() {
  if (!legacyTarget.value) return
  saving.value = true
  actionError.value = ''
  try {
    await http.put(`/admin/integrations/legacy-service-reviews/${legacyTarget.value.id}`, {
      ...legacyForm,
      approvedAction:
        legacyForm.reviewStatus === 'APPROVED' ? legacyForm.approvedAction || null : null,
    })
    legacyTarget.value = null
    toast.show('历史组合记录审核已保存，原业务记录未被改动', 'success')
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <PageHeader
    eyebrow="平台运营"
    title="接口管理"
    description="管理接口参数、凭据引用和逐项验收。内部配置不代表外部数据或供应商履约已经可用。"
  >
    <button class="button primary" type="button" @click="openConnection()">
      <Plus :size="16" />新增接口
    </button>
  </PageHeader>

  <DataState :loading="loading" :error="error" :empty="false" @retry="load">
    <template #content>
      <section class="integration-security-note">
        <KeyRound :size="20" />
        <div>
          <strong>凭据不进数据库</strong>
          <p>{{ overview?.securityNotice }}</p>
        </div>
      </section>

      <section class="metric-grid integration-metrics">
        <article class="metric-card plain-metric-card">
          <span>接口配置</span><strong>{{ summary?.connectionCount ?? 0 }}</strong>
        </article>
        <article class="metric-card plain-metric-card">
          <span>已启用</span><strong>{{ summary?.enabledConnectionCount ?? 0 }}</strong>
        </article>
        <article class="metric-card plain-metric-card">
          <span>配置齐备</span><strong>{{ summary?.configurationReadyCount ?? 0 }}</strong>
        </article>
        <article class="metric-card plain-metric-card warning">
          <span>待验收关卡</span><strong>{{ summary?.pendingGateCount ?? 0 }}</strong>
        </article>
        <article class="metric-card plain-metric-card">
          <span>必备证据</span
          ><strong
            >{{ summary?.verifiedRequiredEvidenceItemCount ?? 0 }} /
            {{ summary?.requiredEvidenceItemCount ?? 0 }}</strong
          >
        </article>
        <article class="metric-card plain-metric-card warning">
          <span>待审历史记录</span><strong>{{ summary?.pendingLegacyReviewCount ?? 0 }}</strong>
        </article>
      </section>

      <nav class="integration-tabs" aria-label="接口管理栏目">
        <button
          :class="{ active: tab === 'connections' }"
          type="button"
          @click="tab = 'connections'"
        >
          <Cable :size="17" />接口配置
        </button>
        <button :class="{ active: tab === 'acceptance' }" type="button" @click="tab = 'acceptance'">
          <ClipboardCheck :size="17" />上线验收
        </button>
        <button :class="{ active: tab === 'legacy' }" type="button" @click="tab = 'legacy'">
          <History :size="17" />历史组合审核
        </button>
      </nav>

      <template v-if="tab === 'connections'">
        <section class="adapter-grid">
          <article
            v-for="adapter in overview?.builtInAdapters || []"
            :key="adapter.code"
            class="panel adapter-card"
          >
            <div class="adapter-icon"><ServerCog :size="20" /></div>
            <div>
              <span class="eyebrow">内置适配器</span>
              <h2>{{ adapter.name }}</h2>
              <p>{{ adapter.customerFallback }}</p>
            </div>
            <span class="readiness-badge" :class="`tone-${statusTone(adapter.operationalStatus)}`">
              {{ statusLabels[adapter.operationalStatus] || adapter.operationalStatus }}
            </span>
            <dl>
              <div>
                <dt>运行配置</dt>
                <dd>{{ adapter.runtimeConfigured ? '已检测' : '未配置' }}</dd>
              </div>
              <div>
                <dt>验收状态</dt>
                <dd>{{ statusLabels[adapter.acceptanceStatus] || adapter.acceptanceStatus }}</dd>
              </div>
            </dl>
          </article>
        </section>

        <section class="panel table-panel integration-table">
          <div class="panel-heading">
            <div>
              <h2>供应商接口</h2>
              <p>保存接口参数和验收凭据位置；令牌由部署环境提供。</p>
            </div>
          </div>
          <div v-if="overview?.connections.length" class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>接口</th>
                  <th>用途 / 环境</th>
                  <th>供应商</th>
                  <th>授权 / 沙箱 / 生产</th>
                  <th>凭据</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in overview.connections" :key="item.id">
                  <td>
                    <strong>{{ item.connectionName }}</strong>
                    <small>{{ item.providerCode }} · {{ item.connectionNo }}</small>
                  </td>
                  <td>
                    {{ kindLabels[item.connectionKind] || item.connectionKind }}
                    <small>{{ item.environment === 'PRODUCTION' ? '生产' : '沙箱' }}</small>
                  </td>
                  <td>{{ item.supplierName || '未关联供应商' }}</td>
                  <td>
                    {{ statusLabels[item.authorizationStatus] || item.authorizationStatus }} /
                    {{ statusLabels[item.sandboxStatus] || item.sandboxStatus }} /
                    {{ statusLabels[item.productionStatus] || item.productionStatus }}
                  </td>
                  <td>
                    {{
                      item.authType === 'NONE' ? '无需凭据' : item.credentialEnvKey || '未填写引用'
                    }}
                    <small>{{
                      item.credentialConfigured ? '运行环境已配置' : '运行环境未配置'
                    }}</small>
                  </td>
                  <td>
                    <span
                      class="readiness-badge"
                      :class="`tone-${statusTone(item.configurationReady ? 'READY' : 'BLOCKED')}`"
                    >
                      {{ item.configurationReady ? '配置齐备' : '仍有缺项' }}
                    </span>
                    <small>{{ item.enabled ? '已启用' : '保持停用' }}</small>
                  </td>
                  <td class="row-actions">
                    <button class="text-button" type="button" @click="openConnection(item)">
                      <PencilLine :size="15" />编辑
                    </button>
                    <button
                      class="text-button"
                      type="button"
                      :disabled="checkingId === item.id"
                      @click="checkConnection(item)"
                    >
                      <RefreshCw :size="15" />{{ checkingId === item.id ? '检查中' : '配置检查' }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="integration-empty">
            <Cable :size="28" />
            <strong>尚未登记供应商接口</strong>
            <p>先建立供应商，再登记接口和验收材料。未登记不影响人工履约。</p>
          </div>
        </section>
      </template>

      <section v-else-if="tab === 'acceptance'" class="acceptance-grid">
        <article
          v-for="gate in overview?.acceptanceGates || []"
          :key="gate.id"
          class="panel gate-card"
        >
          <div class="gate-heading">
            <ShieldCheck :size="20" />
            <div>
              <h2>{{ gate.gateName }}</h2>
              <small>{{ gate.gateCode }}</small>
            </div>
            <span class="readiness-badge" :class="`tone-${statusTone(gate.status)}`">
              {{ statusLabels[gate.status] || gate.status }}
            </span>
          </div>
          <p>{{ gate.reviewNote || '尚未登记验收说明。' }}</p>
          <div class="gate-progress">
            <span>必备材料</span>
            <strong>{{ gate.verifiedRequiredItemCount }} / {{ gate.requiredItemCount }}</strong>
          </div>
          <div class="evidence-list">
            <button
              v-for="item in evidenceForGate(gate.gateCode)"
              :key="item.id"
              class="evidence-row"
              type="button"
              @click="openEvidence(item)"
            >
              <span
                class="evidence-state"
                :class="`tone-${statusTone(item.itemStatus)}`"
                aria-hidden="true"
              />
              <span>
                <strong>{{ item.itemName }}</strong>
                <small>{{
                  item.evidenceReference
                    ? `${statusLabels[item.itemStatus] || item.itemStatus} · 已登记证据`
                    : statusLabels[item.itemStatus] || item.itemStatus
                }}</small>
              </span>
              <PencilLine :size="14" />
            </button>
          </div>
          <dl>
            <div>
              <dt>验收证据</dt>
              <dd>{{ gate.evidenceReference || '未登记' }}</dd>
            </div>
            <div>
              <dt>复核人员</dt>
              <dd>{{ gate.reviewedBy || '待安排' }}</dd>
            </div>
          </dl>
          <button class="button secondary" type="button" @click="openGate(gate)">
            <PencilLine :size="15" />更新验收
          </button>
        </article>
      </section>

      <section v-else class="panel table-panel integration-table">
        <div class="panel-heading">
          <div>
            <h2>历史组合服务审核</h2>
            <p>只登记业务决定，不自动拆单、删除、归档或改写历史项目。</p>
          </div>
        </div>
        <div v-if="overview?.legacyServiceReviews.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>审核记录</th>
                <th>客户 / 原需求</th>
                <th>关联项目</th>
                <th>任务 / 结算</th>
                <th>审核状态</th>
                <th>确认动作</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in overview.legacyServiceReviews" :key="item.id">
                <td>
                  <strong>{{ item.reviewNo }}</strong
                  ><small>{{ item.requirementNo }}</small>
                </td>
                <td>
                  <strong>{{ item.organizationName }}</strong
                  ><small>{{ item.title }}</small>
                </td>
                <td>{{ item.projectNos || '无关联项目' }}</td>
                <td>{{ item.taskCount }} / {{ item.settlementCount }}</td>
                <td>
                  <span class="readiness-badge" :class="`tone-${statusTone(item.reviewStatus)}`">
                    {{ statusLabels[item.reviewStatus] || item.reviewStatus }}
                  </span>
                </td>
                <td>
                  {{ item.approvedAction ? legacyActionLabels[item.approvedAction] : '待业务确认' }}
                </td>
                <td>
                  <button class="text-button" type="button" @click="openLegacy(item)">
                    <PencilLine :size="15" />审核
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="integration-empty">
          <CheckCircle2 :size="28" />
          <strong>没有待登记的历史组合记录</strong>
          <p>新业务链路只允许四项独立服务下单。</p>
        </div>
      </section>
    </template>
  </DataState>

  <div v-if="connectionModal" class="modal-backdrop" @click.self="connectionModal = false">
    <form class="modal-panel integration-modal" @submit.prevent="saveConnection">
      <header>
        <div>
          <span class="eyebrow">平台内部配置</span>
          <h2>{{ connectionTarget ? '编辑接口' : '新增接口' }}</h2>
        </div>
        <button
          class="icon-button"
          type="button"
          aria-label="关闭"
          @click="connectionModal = false"
        >
          <X :size="19" />
        </button>
      </header>

      <section class="modal-section">
        <h3>基本信息</h3>
        <div class="form-grid two-columns">
          <label class="full"
            >接口名称<span class="required">*</span
            ><input v-model="connectionForm.connectionName" required maxlength="180"
          /></label>
          <label
            >关联供应商<select v-model.number="connectionForm.supplierId">
              <option value="">暂不关联</option>
              <option v-for="item in suppliers" :key="item.id" :value="item.id">
                {{ item.supplierName }}
              </option>
            </select></label
          >
          <label
            >供应商标识<span class="required">*</span
            ><input
              v-model="connectionForm.providerCode"
              required
              maxlength="80"
              placeholder="例如 NIUMEDIA"
          /></label>
          <label
            >接口用途<span class="required">*</span
            ><select v-model="connectionForm.connectionKind" required>
              <option value="MEDIA_DATA">媒体数据</option>
              <option value="ORDER_FULFILLMENT">订单履约</option>
              <option value="QUOTE_SYNC">报价同步</option>
              <option value="GEO_FEDERATION">GEO 联动</option>
            </select></label
          >
          <label
            >运行环境<span class="required">*</span
            ><select v-model="connectionForm.environment" required>
              <option value="SANDBOX">沙箱</option>
              <option value="PRODUCTION">生产</option>
            </select></label
          >
          <label class="full"
            >基础地址<span class="required">*</span
            ><input
              v-model="connectionForm.baseUrl"
              required
              maxlength="500"
              type="url"
              placeholder="https://api.example.com/v1"
          /></label>
          <label
            >鉴权方式<span class="required">*</span
            ><select v-model="connectionForm.authType" required>
              <option value="BEARER">Bearer Token</option>
              <option value="API_KEY_HEADER">API Key 请求头</option>
              <option value="HMAC_SHA256">HMAC-SHA256</option>
              <option value="NONE">无需鉴权（仅沙箱）</option>
            </select></label
          >
          <label
            >鉴权请求头<input
              v-model="connectionForm.authHeaderName"
              maxlength="100"
              :disabled="connectionForm.authType === 'NONE'"
          /></label>
          <label class="full"
            >凭据环境变量<input
              v-model="connectionForm.credentialEnvKey"
              maxlength="160"
              :disabled="connectionForm.authType === 'NONE'"
              placeholder="只填变量名，例如 WINPRESS_PROVIDER_TOKEN"
            /><small>不得在此粘贴令牌、密钥或密码。</small></label
          >
          <label class="full"
            >能力范围<textarea
              v-model="connectionForm.capabilityScope"
              rows="2"
              maxlength="2000"
              placeholder="明确允许检索、报价、下单或回执的边界"
            />
          </label>
        </div>
      </section>

      <section class="modal-section">
        <h3>接口路径与限流</h3>
        <div class="form-grid two-columns">
          <label
            >媒体检索路径<input
              v-model="connectionForm.mediaSearchPath"
              maxlength="300"
              placeholder="/media/search"
          /></label>
          <label
            >记者检索路径<input
              v-model="connectionForm.reporterSearchPath"
              maxlength="300"
              placeholder="/reporter/search"
          /></label>
          <label
            >报价路径<input
              v-model="connectionForm.quotePath"
              maxlength="300"
              placeholder="/quotes"
          /></label>
          <label
            >下单路径<input
              v-model="connectionForm.orderPath"
              maxlength="300"
              placeholder="/orders"
          /></label>
          <label
            >订单状态路径<input
              v-model="connectionForm.orderStatusPath"
              maxlength="300"
              placeholder="/orders/status"
          /></label>
          <label
            >回调路径<input
              v-model="connectionForm.callbackPath"
              maxlength="300"
              placeholder="/callbacks/status"
          /></label>
          <label
            >对账路径<input
              v-model="connectionForm.reconciliationPath"
              maxlength="300"
              placeholder="/orders/reconciliation"
          /></label>
          <label
            >服务等级凭据<input
              v-model="connectionForm.slaReference"
              maxlength="300"
              placeholder="合同章节、附件或受控文档编号"
          /></label>
          <label
            >每分钟请求上限<input
              v-model.number="connectionForm.rateLimitPerMinute"
              type="number"
              min="1"
              max="10000"
              required
          /></label>
          <label
            >超时秒数<input
              v-model.number="connectionForm.timeoutSeconds"
              type="number"
              min="1"
              max="120"
              required
          /></label>
          <label
            >最大重试次数<input
              v-model.number="connectionForm.maxRetries"
              type="number"
              min="0"
              max="10"
              required
          /></label>
          <label class="full"
            >数据范围<textarea
              v-model="connectionForm.dataScope"
              rows="2"
              maxlength="3000"
              placeholder="登记合同允许的数据类型、地区、字段和使用范围"
            />
          </label>
        </div>
      </section>

      <section class="modal-section">
        <h3>验收材料</h3>
        <div class="form-grid two-columns">
          <label
            >合同或授权编号<input v-model="connectionForm.contractReference" maxlength="300"
          /></label>
          <label
            >授权状态<select v-model="connectionForm.authorizationStatus">
              <option value="NOT_SUBMITTED">未提交</option>
              <option value="PENDING">审核中</option>
              <option value="VERIFIED">已核验</option>
              <option value="REJECTED">未通过</option>
            </select></label
          >
          <label class="full"
            >授权证据位置<input
              v-model="connectionForm.authorizationEvidenceRef"
              maxlength="500"
              placeholder="受控文档编号或内部存储位置"
          /></label>
          <label
            >沙箱状态<select v-model="connectionForm.sandboxStatus">
              <option value="NOT_TESTED">未测试</option>
              <option value="PENDING">联调中</option>
              <option value="PASSED">已通过</option>
              <option value="FAILED">未通过</option>
            </select></label
          >
          <label
            >沙箱证据位置<input v-model="connectionForm.sandboxEvidenceRef" maxlength="500"
          /></label>
          <label
            >生产状态<select v-model="connectionForm.productionStatus">
              <option value="NOT_APPROVED">未批准</option>
              <option value="PENDING">审批中</option>
              <option value="APPROVED">已批准</option>
              <option value="REVOKED">已撤销</option>
            </select></label
          >
          <label
            >生产批准证据<input v-model="connectionForm.productionEvidenceRef" maxlength="500"
          /></label>
          <label class="full"
            >内部备注<textarea v-model="connectionForm.internalNote" rows="2" maxlength="3000" />
          </label>
          <label class="full enable-checkbox"
            ><input v-model="connectionForm.enabled" type="checkbox" /><span
              >启用接口。系统会先检查授权、沙箱、生产批准和运行环境凭据。</span
            ></label
          >
        </div>
      </section>

      <p v-if="actionError" class="form-error">{{ actionError }}</p>
      <div class="form-actions">
        <button class="button secondary" type="button" @click="connectionModal = false">
          取消
        </button>
        <button class="button primary" type="submit" :disabled="saving">
          <Cable :size="16" />{{ saving ? '保存中' : '保存接口' }}
        </button>
      </div>
    </form>
  </div>

  <div v-if="gateTarget" class="modal-backdrop" @click.self="gateTarget = null">
    <form class="modal-panel compact-form" @submit.prevent="saveGate">
      <header>
        <div>
          <span class="eyebrow">上线关卡</span>
          <h2>{{ gateTarget.gateName }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="gateTarget = null">
          <X :size="19" />
        </button>
      </header>
      <div class="form-grid">
        <label
          >验收状态<span class="required">*</span
          ><select v-model="gateForm.status" required>
            <option value="PENDING">待验收</option>
            <option value="IN_REVIEW">审核中</option>
            <option value="PASSED">已通过</option>
            <option value="BLOCKED">受阻</option>
          </select></label
        >
        <label
          >验收证据位置<input v-model="gateForm.evidenceReference" maxlength="500" /><small
            >标记为已通过时必填。</small
          ></label
        >
        <label>复核说明<textarea v-model="gateForm.reviewNote" rows="4" maxlength="3000" /></label>
      </div>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
      <div class="form-actions">
        <button class="button secondary" type="button" @click="gateTarget = null">取消</button>
        <button class="button primary" type="submit" :disabled="saving">
          <ClipboardCheck :size="16" />保存验收
        </button>
      </div>
    </form>
  </div>

  <div v-if="evidenceTarget" class="modal-backdrop" @click.self="evidenceTarget = null">
    <form class="modal-panel compact-form" @submit.prevent="saveEvidence">
      <header>
        <div>
          <span class="eyebrow">逐项验收材料</span>
          <h2>{{ evidenceTarget.itemName }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="evidenceTarget = null">
          <X :size="19" />
        </button>
      </header>
      <p class="form-hint">
        {{ evidenceTarget.required ? '必备项目。未核验时，上线关卡不能通过。' : '辅助项目。' }}
      </p>
      <div class="form-grid">
        <label
          >核验状态<span class="required">*</span
          ><select v-model="evidenceForm.itemStatus" required>
            <option value="PENDING">待核验</option>
            <option value="IN_REVIEW">核验中</option>
            <option value="VERIFIED">已核验</option>
            <option value="REJECTED">未通过</option>
            <option v-if="!evidenceTarget.required" value="NOT_APPLICABLE">不适用</option>
          </select></label
        >
        <label
          >证据位置<input v-model="evidenceForm.evidenceReference" maxlength="500" /><small
            >标记为已核验时必填；仅登记受控文档编号或存储位置。</small
          ></label
        >
        <label
          >复核说明<textarea v-model="evidenceForm.reviewNote" rows="4" maxlength="3000" />
        </label>
      </div>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
      <div class="form-actions">
        <button class="button secondary" type="button" @click="evidenceTarget = null">取消</button>
        <button class="button primary" type="submit" :disabled="saving">
          <ClipboardCheck :size="16" />{{ saving ? '保存中' : '保存材料' }}
        </button>
      </div>
    </form>
  </div>

  <div v-if="legacyTarget" class="modal-backdrop" @click.self="legacyTarget = null">
    <form class="modal-panel compact-form" @submit.prevent="saveLegacyReview">
      <header>
        <div>
          <span class="eyebrow">历史组合记录</span>
          <h2>{{ legacyTarget.requirementNo }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="legacyTarget = null">
          <X :size="19" />
        </button>
      </header>
      <section class="legacy-warning">
        <FileWarning :size="20" />
        <p>
          这里只记录业务决定，不执行拆单、迁移、删除或归档。实际数据变更必须另行编制清单并备份验收。
        </p>
      </section>
      <div class="form-grid">
        <label
          >审核状态<span class="required">*</span
          ><select v-model="legacyForm.reviewStatus" required>
            <option value="PENDING">待审核</option>
            <option value="IN_REVIEW">审核中</option>
            <option value="APPROVED">已批准方案</option>
            <option value="REJECTED">不采用</option>
          </select></label
        >
        <label v-if="legacyForm.reviewStatus === 'APPROVED'"
          >批准动作<span class="required">*</span
          ><select v-model="legacyForm.approvedAction" required>
            <option value="">请选择</option>
            <option v-for="(label, value) in legacyActionLabels" :key="value" :value="value">
              {{ label }}
            </option>
          </select></label
        >
        <label
          >业务确认凭据<input v-model="legacyForm.evidenceReference" maxlength="500" /><small
            >批准方案时必填。</small
          ></label
        >
        <label
          >业务说明<textarea v-model="legacyForm.businessNote" rows="4" maxlength="3000" />
        </label>
      </div>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
      <div class="form-actions">
        <button class="button secondary" type="button" @click="legacyTarget = null">取消</button>
        <button class="button primary" type="submit" :disabled="saving">
          <ShieldCheck :size="16" />保存审核
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.integration-security-note {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
  padding: 16px 18px;
  border: 1px solid #cddff8;
  border-radius: 10px;
  background: #f3f7fd;
  color: #173f73;
}

.integration-security-note p,
.panel-heading p {
  margin: 4px 0 0;
  color: var(--muted);
  line-height: 1.6;
}

.integration-metrics {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-bottom: 18px;
}

.integration-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  overflow-x: auto;
}

.integration-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: #fff;
  color: var(--muted);
  font-weight: 750;
  white-space: nowrap;
}

.integration-tabs button.active {
  border-color: #1f5db6;
  background: #eef5ff;
  color: #174d9b;
}

.adapter-grid,
.acceptance-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.adapter-card,
.gate-card {
  position: relative;
  padding: 20px;
}

.adapter-card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 14px;
  align-items: start;
}

.adapter-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 10px;
  background: #edf4ff;
  color: #1f5db6;
}

.adapter-card h2,
.gate-card h2 {
  margin: 5px 0 7px;
  font-size: 18px;
}

.adapter-card p,
.gate-card p {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.adapter-card dl {
  grid-column: 2 / -1;
}

.adapter-card dl,
.gate-card dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0 0;
}

.adapter-card dl div,
.gate-card dl div {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f8fa;
}

dt {
  color: var(--muted);
  font-size: 12px;
}

dd {
  margin: 3px 0 0;
  font-weight: 750;
}

.readiness-badge {
  display: inline-flex;
  align-items: center;
  width: max-content;
  min-height: 27px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.tone-success {
  background: #e9f8ef;
  color: #1d7741;
}

.tone-warning {
  background: #fff5dc;
  color: #8a5c00;
}

.tone-danger {
  background: #fff0ef;
  color: #ae302d;
}

.integration-table {
  padding: 20px;
}

.integration-table .panel-heading {
  margin-bottom: 16px;
}

.integration-table td small {
  display: block;
  margin-top: 4px;
  color: var(--muted);
}

.row-actions {
  min-width: 150px;
}

.row-actions .text-button {
  margin-right: 10px;
}

.integration-empty {
  display: grid;
  min-height: 180px;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--muted);
  text-align: center;
}

.integration-empty p {
  margin: 0;
}

.gate-heading {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 10px;
  align-items: start;
}

.gate-card dl {
  margin: 14px 0;
}

.gate-progress {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 15px 0 8px;
  color: var(--muted);
  font-size: 13px;
}

.gate-progress strong {
  color: var(--ink);
}

.evidence-list {
  display: grid;
  gap: 7px;
}

.evidence-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 9px;
  align-items: center;
  width: 100%;
  padding: 10px 11px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  color: var(--ink);
  text-align: left;
}

.evidence-row:hover {
  border-color: #9cbce8;
  background: #f8fbff;
}

.evidence-row strong,
.evidence-row small {
  display: block;
}

.evidence-row strong {
  font-size: 13px;
}

.evidence-row small {
  margin-top: 2px;
  color: var(--muted);
  font-size: 12px;
}

.evidence-state {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.integration-modal {
  width: min(920px, calc(100vw - 32px));
  max-height: calc(100vh - 32px);
  overflow-y: auto;
}

.modal-section {
  padding: 17px 0;
  border-top: 1px solid var(--border);
}

.modal-section h3 {
  margin: 0 0 14px;
  font-size: 16px;
}

.form-grid label small {
  display: block;
  margin-top: 6px;
  color: var(--muted);
  font-weight: 500;
}

.enable-checkbox {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  padding: 13px;
  border-radius: 8px;
  background: #f7f8fa;
}

.enable-checkbox input {
  width: 18px;
  height: 18px;
}

.legacy-warning {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  padding: 13px;
  border-radius: 8px;
  background: #fff8e8;
  color: #7a5709;
}

.legacy-warning p {
  margin: 0;
  line-height: 1.6;
}

@media (max-width: 1020px) {
  .integration-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .integration-metrics,
  .adapter-grid,
  .acceptance-grid {
    grid-template-columns: 1fr;
  }

  .adapter-card {
    grid-template-columns: auto 1fr;
  }

  .adapter-card > .readiness-badge {
    grid-column: 2;
  }

  .adapter-card dl {
    grid-column: 1 / -1;
  }

  .adapter-card dl,
  .gate-card dl {
    grid-template-columns: 1fr;
  }
}
</style>
