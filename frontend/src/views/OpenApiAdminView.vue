<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Copy,
  FileKey2,
  KeyRound,
  PencilLine,
  Plus,
  RefreshCw,
  ShieldCheck,
  X,
} from 'lucide-vue-next'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import http, { apiError } from '@/api/http'
import { useToastStore } from '@/stores/toast'

type Tab = 'applications' | 'keys' | 'logs' | 'contract'

interface Summary {
  applicationCount: number
  activeApplicationCount: number
  activeKeyCount: number
  last24hRequestCount: number
}

interface CustomerOwner {
  id: number
  displayName: string
  username: string
  organizationName: string
}

interface Application {
  id: number
  applicationNo: string
  applicationName: string
  clientCode: string
  customerUserId: number
  customerName: string
  customerUsername: string
  organizationName: string
  environment: string
  serviceScopes: string
  rateLimitPerMinute: number
  authorizationStatus: string
  authorizationEvidenceRef?: string
  sandboxStatus: string
  sandboxEvidenceRef?: string
  productionStatus: string
  productionEvidenceRef?: string
  contractReference?: string
  internalNote?: string
  status: string
  activeKeyCount: number
  lastKeyUsedAt?: string
  last24hRequestCount: number
}

interface AccessKey {
  id: number
  keyNo: string
  applicationId: number
  applicationNo: string
  applicationName: string
  environment: string
  keyLabel: string
  keyPrefix: string
  status: string
  expiresAt?: string
  lastUsedAt?: string
  revokedAt?: string
  createdAt: string
}

interface AccessLog {
  id: number
  applicationNo?: string
  applicationName?: string
  keyPrefix?: string
  externalRequestId?: string
  operationCode: string
  responseStatus: number
  outcomeCode: string
  durationMillis?: number
  createdAt: string
}

interface Capability {
  code: string
  name: string
  detail: string
}

interface Overview {
  summary: Summary
  applications: Application[]
  customerOwners: CustomerOwner[]
  accessKeys: AccessKey[]
  accessLogs: AccessLog[]
  capabilities: Capability[]
  securityNotice: string
}

const toast = useToastStore()
const tab = ref<Tab>('applications')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const actionError = ref('')
const overview = ref<Overview | null>(null)
const applicationModal = ref(false)
const keyModal = ref(false)
const oneTimeKeyModal = ref(false)
const keyTarget = ref<Application | null>(null)
const issuedKey = ref('')
const issuedKeyMeta = ref<{ prefix: string; expiresAt?: string } | null>(null)

const applicationForm = reactive({
  id: undefined as number | undefined,
  applicationName: '',
  clientCode: '',
  customerUserId: '' as number | '',
  environment: 'SANDBOX',
  serviceScopes: ['SERVICE_CATALOG', 'REQUIREMENT_CREATE', 'PROJECT_READ'],
  rateLimitPerMinute: 60,
  authorizationStatus: 'NOT_SUBMITTED',
  authorizationEvidenceRef: '',
  sandboxStatus: 'NOT_TESTED',
  sandboxEvidenceRef: '',
  productionStatus: 'NOT_APPROVED',
  productionEvidenceRef: '',
  contractReference: '',
  internalNote: '',
  status: 'DRAFT',
})

const keyForm = reactive({
  keyLabel: '',
  expiresAt: '',
})

const summary = computed(() => overview.value?.summary)
const applications = computed(() => overview.value?.applications || [])
const accessKeys = computed(() => overview.value?.accessKeys || [])
const accessLogs = computed(() => overview.value?.accessLogs || [])
const customerOwners = computed(() => overview.value?.customerOwners || [])
const capabilities = computed(() => overview.value?.capabilities || [])

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '已启用',
  SUSPENDED: '已暂停',
  REVOKED: '已撤销',
  VERIFIED: '已核验',
  NOT_SUBMITTED: '未提交',
  PENDING: '待确认',
  REJECTED: '未通过',
  NOT_TESTED: '未测试',
  PASSED: '已通过',
  FAILED: '未通过',
  NOT_APPROVED: '未批准',
  APPROVED: '已批准',
  EXPIRED: '已到期',
}

const scopeLabel: Record<string, string> = {
  SERVICE_CATALOG: '服务目录',
  REQUIREMENT_CREATE: '提交需求',
  PROJECT_READ: '查询项目',
  DIRECT_CHANNEL_CATALOG: '直编渠道目录',
}

function statusLabel(value?: string) {
  return statusLabels[value || ''] || value || '—'
}

function statusTone(value?: string) {
  if (['ACTIVE', 'VERIFIED', 'PASSED', 'APPROVED'].includes(value || '')) return 'success'
  if (['SUSPENDED', 'REVOKED', 'REJECTED', 'FAILED', 'EXPIRED'].includes(value || ''))
    return 'danger'
  return 'warning'
}

function scopes(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function defaultExpiry() {
  const date = new Date()
  date.setDate(date.getDate() + 90)
  date.setSeconds(0, 0)
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function resetApplicationForm() {
  Object.assign(applicationForm, {
    id: undefined,
    applicationName: '',
    clientCode: '',
    customerUserId: '',
    environment: 'SANDBOX',
    serviceScopes: ['SERVICE_CATALOG', 'REQUIREMENT_CREATE', 'PROJECT_READ'],
    rateLimitPerMinute: 60,
    authorizationStatus: 'NOT_SUBMITTED',
    authorizationEvidenceRef: '',
    sandboxStatus: 'NOT_TESTED',
    sandboxEvidenceRef: '',
    productionStatus: 'NOT_APPROVED',
    productionEvidenceRef: '',
    contractReference: '',
    internalNote: '',
    status: 'DRAFT',
  })
}

function openApplication(item?: Application) {
  actionError.value = ''
  if (!item) {
    resetApplicationForm()
  } else {
    Object.assign(applicationForm, {
      id: item.id,
      applicationName: item.applicationName,
      clientCode: item.clientCode,
      customerUserId: item.customerUserId,
      environment: item.environment,
      serviceScopes: scopes(item.serviceScopes),
      rateLimitPerMinute: item.rateLimitPerMinute,
      authorizationStatus: item.authorizationStatus,
      authorizationEvidenceRef: item.authorizationEvidenceRef || '',
      sandboxStatus: item.sandboxStatus,
      sandboxEvidenceRef: item.sandboxEvidenceRef || '',
      productionStatus: item.productionStatus,
      productionEvidenceRef: item.productionEvidenceRef || '',
      contractReference: item.contractReference || '',
      internalNote: item.internalNote || '',
      status: item.status,
    })
  }
  applicationModal.value = true
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get('/admin/open-api')
    overview.value = data.data
  } catch (err) {
    error.value = apiError(err)
  } finally {
    loading.value = false
  }
}

async function saveApplication() {
  saving.value = true
  actionError.value = ''
  const payload = {
    applicationName: applicationForm.applicationName,
    clientCode: applicationForm.clientCode,
    customerUserId: applicationForm.customerUserId,
    environment: applicationForm.environment,
    serviceScopes: applicationForm.serviceScopes,
    rateLimitPerMinute: applicationForm.rateLimitPerMinute,
    authorizationStatus: applicationForm.authorizationStatus,
    authorizationEvidenceRef: applicationForm.authorizationEvidenceRef || null,
    sandboxStatus: applicationForm.sandboxStatus,
    sandboxEvidenceRef: applicationForm.sandboxEvidenceRef || null,
    productionStatus: applicationForm.productionStatus,
    productionEvidenceRef: applicationForm.productionEvidenceRef || null,
    contractReference: applicationForm.contractReference || null,
    internalNote: applicationForm.internalNote || null,
    status: applicationForm.status,
  }
  try {
    if (applicationForm.id) {
      await http.put(`/admin/open-api/applications/${applicationForm.id}`, payload)
      toast.show('开放 API 应用已更新', 'success')
    } else {
      await http.post('/admin/open-api/applications', payload)
      toast.show('开放 API 应用已创建', 'success')
    }
    applicationModal.value = false
    await load()
  } catch (err) {
    actionError.value = apiError(err)
  } finally {
    saving.value = false
  }
}

function openKeyModal(item: Application) {
  keyTarget.value = item
  keyForm.keyLabel = ''
  keyForm.expiresAt = defaultExpiry()
  actionError.value = ''
  keyModal.value = true
}

async function issueKey() {
  if (!keyTarget.value) return
  saving.value = true
  actionError.value = ''
  try {
    const expiresAt = keyForm.expiresAt ? new Date(keyForm.expiresAt).toISOString() : null
    const { data } = await http.post(`/admin/open-api/applications/${keyTarget.value.id}/keys`, {
      keyLabel: keyForm.keyLabel,
      expiresAt,
    })
    issuedKey.value = data.data.accessKey
    issuedKeyMeta.value = { prefix: data.data.keyPrefix, expiresAt: data.data.expiresAt }
    keyModal.value = false
    oneTimeKeyModal.value = true
    await load()
  } catch (err) {
    actionError.value = apiError(err)
  } finally {
    saving.value = false
  }
}

async function revokeKey(item: AccessKey) {
  if (!window.confirm(`确认撤销 ${item.keyPrefix}？撤销后不可恢复。`)) return
  try {
    await http.post(`/admin/open-api/keys/${item.id}/revoke`)
    toast.show('访问密钥已撤销', 'success')
    await load()
  } catch (err) {
    toast.show(apiError(err), 'error')
  }
}

async function copyIssuedKey() {
  try {
    await navigator.clipboard.writeText(issuedKey.value)
    toast.show('访问密钥已复制', 'success')
  } catch {
    toast.show('无法自动复制，请手动保存访问密钥', 'error')
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack open-api-page">
    <PageHeader
      eyebrow="平台管理"
      title="开放 API"
      description="管理客户系统接入、访问密钥与请求留痕。接口创建的需求直接进入云发布项目链路。"
    >
      <button class="button secondary" type="button" :disabled="loading" @click="load">
        <RefreshCw :size="16" /> 刷新
      </button>
      <button class="button" type="button" @click="openApplication()">
        <Plus :size="16" /> 新增接入应用
      </button>
    </PageHeader>

    <DataState v-if="loading" loading />
    <DataState v-else-if="error" :error="error" @retry="load" />

    <template v-else-if="overview && summary">
      <section class="metric-grid open-api-metrics" aria-label="开放 API 概览">
        <article class="metric-card">
          <span>接入应用</span><strong>{{ summary.applicationCount }}</strong
          ><small>已登记的客户系统</small>
        </article>
        <article class="metric-card">
          <span>已启用应用</span><strong>{{ summary.activeApplicationCount }}</strong
          ><small>完成当前启用条件</small>
        </article>
        <article class="metric-card">
          <span>有效密钥</span><strong>{{ summary.activeKeyCount }}</strong
          ><small>不展示原始密钥</small>
        </article>
        <article class="metric-card">
          <span>最近请求</span><strong>{{ summary.last24hRequestCount }}</strong
          ><small>仅保留请求摘要</small>
        </article>
      </section>

      <section class="panel api-security-note">
        <ShieldCheck :size="20" />
        <p>{{ overview.securityNotice }}</p>
      </section>

      <nav class="tabs" aria-label="开放 API 管理栏目">
        <button
          :class="{ active: tab === 'applications' }"
          type="button"
          @click="tab = 'applications'"
        >
          接入应用
        </button>
        <button :class="{ active: tab === 'keys' }" type="button" @click="tab = 'keys'">
          访问密钥
        </button>
        <button :class="{ active: tab === 'logs' }" type="button" @click="tab = 'logs'">
          请求记录
        </button>
        <button :class="{ active: tab === 'contract' }" type="button" @click="tab = 'contract'">
          服务契约
        </button>
      </nav>

      <section v-if="tab === 'applications'" class="panel table-panel">
        <div class="panel-heading">
          <div>
            <h2>接入应用</h2>
            <p>应用须绑定一个客户账号；API 写入的数据只进入该客户的项目范围。</p>
          </div>
        </div>
        <DataState v-if="!applications.length" empty empty-text="还没有登记接入应用" />
        <div v-else class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>应用</th>
                <th>归属客户</th>
                <th>环境</th>
                <th>能力</th>
                <th>验收</th>
                <th>使用情况</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in applications" :key="item.id">
                <td>
                  <strong>{{ item.applicationName }}</strong
                  ><small>{{ item.applicationNo }} · {{ item.clientCode }}</small>
                </td>
                <td>
                  {{ item.organizationName
                  }}<small>{{ item.customerName }} · {{ item.customerUsername }}</small>
                </td>
                <td>
                  {{ item.environment === 'SANDBOX' ? '沙箱' : '生产'
                  }}<small>{{ item.rateLimitPerMinute }} 次/分钟</small>
                </td>
                <td>
                  <div class="scope-list">
                    <span v-for="scope in scopes(item.serviceScopes)" :key="scope">{{
                      scopeLabel[scope] || scope
                    }}</span>
                  </div>
                </td>
                <td>
                  <span class="status-tag" :class="statusTone(item.authorizationStatus)">{{
                    statusLabel(item.authorizationStatus)
                  }}</span
                  ><small>沙箱：{{ statusLabel(item.sandboxStatus) }}</small>
                </td>
                <td>
                  {{ item.activeKeyCount }} 个有效密钥<small
                    >{{ item.last24hRequestCount }} 次近 24 小时请求</small
                  >
                </td>
                <td>
                  <span class="status-tag" :class="statusTone(item.status)">{{
                    statusLabel(item.status)
                  }}</span>
                </td>
                <td class="table-actions">
                  <button class="text-button" type="button" @click="openApplication(item)">
                    <PencilLine :size="15" /> 编辑</button
                  ><button
                    class="text-button"
                    type="button"
                    :disabled="item.status !== 'ACTIVE'"
                    @click="openKeyModal(item)"
                  >
                    <KeyRound :size="15" /> 签发密钥
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="tab === 'keys'" class="panel table-panel">
        <div class="panel-heading">
          <div>
            <h2>访问密钥</h2>
            <p>仅显示用途、前缀与状态；原始密钥只在签发时显示一次。</p>
          </div>
        </div>
        <DataState v-if="!accessKeys.length" empty empty-text="还没有签发访问密钥" />
        <div v-else class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>密钥</th>
                <th>接入应用</th>
                <th>环境</th>
                <th>有效期</th>
                <th>最近使用</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in accessKeys" :key="item.id">
                <td>
                  <strong>{{ item.keyLabel }}</strong
                  ><small>{{ item.keyNo }} · {{ item.keyPrefix }}…</small>
                </td>
                <td>
                  {{ item.applicationName }}<small>{{ item.applicationNo }}</small>
                </td>
                <td>{{ item.environment === 'SANDBOX' ? '沙箱' : '生产' }}</td>
                <td>{{ formatDate(item.expiresAt) }}</td>
                <td>{{ formatDate(item.lastUsedAt) }}</td>
                <td>
                  <span class="status-tag" :class="statusTone(item.status)">{{
                    statusLabel(item.status)
                  }}</span>
                </td>
                <td>
                  <button
                    v-if="item.status === 'ACTIVE'"
                    class="text-button danger"
                    type="button"
                    @click="revokeKey(item)"
                  >
                    撤销
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="tab === 'logs'" class="panel table-panel">
        <div class="panel-heading">
          <div>
            <h2>请求记录</h2>
            <p>记录访问结果和耗时，不存储原始密钥、正文、供应商字段或成本信息。</p>
          </div>
        </div>
        <DataState v-if="!accessLogs.length" empty empty-text="暂时没有开放 API 请求记录" />
        <div v-else class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>时间</th>
                <th>应用</th>
                <th>操作</th>
                <th>外部请求号</th>
                <th>响应</th>
                <th>结果</th>
                <th>耗时</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in accessLogs" :key="item.id">
                <td>{{ formatDate(item.createdAt) }}</td>
                <td>
                  {{ item.applicationName || '—'
                  }}<small>{{ item.keyPrefix ? `${item.keyPrefix}…` : '—' }}</small>
                </td>
                <td>{{ item.operationCode }}</td>
                <td>{{ item.externalRequestId || '—' }}</td>
                <td>{{ item.responseStatus }}</td>
                <td>{{ item.outcomeCode }}</td>
                <td>{{ item.durationMillis == null ? '—' : `${item.durationMillis} ms` }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else class="contract-grid">
        <article class="panel contract-card">
          <div class="panel-heading">
            <div>
              <h2>已迁入能力</h2>
              <p>以下能力进入云发布现有项目与订单体系，不再依赖独立 API 工具后台。</p>
            </div>
          </div>
          <div class="capability-list">
            <div v-for="item in capabilities" :key="item.code">
              <FileKey2 :size="18" />
              <div>
                <strong>{{ item.name }}</strong
                ><small>{{ item.code }}</small>
                <p>{{ item.detail }}</p>
              </div>
            </div>
          </div>
        </article>
        <article class="panel contract-card">
          <div class="panel-heading">
            <div>
              <h2>接入边界</h2>
              <p>应用启用需留存授权、沙箱与生产验收依据；生产密钥不随演示数据或本机配置发布。</p>
            </div>
          </div>
          <ul class="contract-points">
            <li>认证方式：<code>X-WinPress-API-Key</code></li>
            <li>请求路径：<code>/api/v1/open-api/v1/*</code></li>
            <li>下单边界：仅现场采写、媒体邀请、直编发稿、新闻发布会四项独立服务。</li>
            <li>外部媒体与供应商数据未经授权或联调时，不在接口中伪装为实时能力。</li>
          </ul>
        </article>
      </section>
    </template>

    <div v-if="applicationModal" class="modal-backdrop" @click.self="applicationModal = false">
      <section
        class="modal api-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="api-application-title"
      >
        <header class="modal-header">
          <div>
            <p class="eyebrow">开放 API</p>
            <h2 id="api-application-title">
              {{ applicationForm.id ? '编辑接入应用' : '新增接入应用' }}
            </h2>
          </div>
          <button
            class="icon-button"
            type="button"
            aria-label="关闭"
            @click="applicationModal = false"
          >
            <X :size="20" />
          </button>
        </header>
        <p class="modal-intro">
          先绑定客户账号和可调用范围，再补齐授权及验收记录。启用后才能签发访问密钥。
        </p>
        <p v-if="actionError" class="form-error">{{ actionError }}</p>
        <form class="form-grid" @submit.prevent="saveApplication">
          <label
            >应用名称*<input
              v-model="applicationForm.applicationName"
              required
              maxlength="160"
              placeholder="例如：客户 CRM 接入"
          /></label>
          <label
            >客户标识*<input
              v-model="applicationForm.clientCode"
              required
              maxlength="80"
              placeholder="例如：GEO_CLIENT_A"
              @input="applicationForm.clientCode = applicationForm.clientCode.toUpperCase()"
          /></label>
          <label
            >归属客户账号*<select v-model.number="applicationForm.customerUserId" required>
              <option disabled value="">请选择客户账号</option>
              <option v-for="item in customerOwners" :key="item.id" :value="item.id">
                {{ item.organizationName }} · {{ item.displayName }}（{{ item.username }}）
              </option>
            </select></label
          >
          <label
            >运行环境*<select v-model="applicationForm.environment">
              <option value="SANDBOX">沙箱</option>
              <option value="PRODUCTION">生产</option>
            </select></label
          >
          <label
            >每分钟限流*<input
              v-model.number="applicationForm.rateLimitPerMinute"
              required
              min="1"
              max="10000"
              type="number"
          /></label>
          <label
            >应用状态*<select v-model="applicationForm.status">
              <option value="DRAFT">草稿</option>
              <option value="ACTIVE">已启用</option>
              <option value="SUSPENDED">已暂停</option>
              <option value="REVOKED">已撤销</option>
            </select></label
          >
          <fieldset class="scope-field full">
            <legend>接口能力*</legend>
            <label v-for="item in capabilities" :key="item.code" class="check-row"
              ><input
                v-model="applicationForm.serviceScopes"
                type="checkbox"
                :value="item.code"
              /><span
                ><strong>{{ item.name }}</strong
                ><small>{{ item.detail }}</small></span
              ></label
            >
          </fieldset>
          <label
            >授权状态*<select v-model="applicationForm.authorizationStatus">
              <option value="NOT_SUBMITTED">未提交</option>
              <option value="PENDING">待确认</option>
              <option value="VERIFIED">已核验</option>
              <option value="REJECTED">未通过</option>
            </select></label
          >
          <label
            >合同编号／位置<input
              v-model="applicationForm.contractReference"
              maxlength="300"
              placeholder="内部合同或文件索引"
          /></label>
          <label class="full"
            >授权证据位置<input
              v-model="applicationForm.authorizationEvidenceRef"
              maxlength="500"
              placeholder="核验记录或受控文件索引"
          /></label>
          <label
            >沙箱状态*<select v-model="applicationForm.sandboxStatus">
              <option value="NOT_TESTED">未测试</option>
              <option value="PENDING">待确认</option>
              <option value="PASSED">已通过</option>
              <option value="FAILED">未通过</option>
            </select></label
          >
          <label
            >沙箱证据位置<input
              v-model="applicationForm.sandboxEvidenceRef"
              maxlength="500"
              placeholder="联调记录或受控文件索引"
          /></label>
          <label
            >生产状态*<select v-model="applicationForm.productionStatus">
              <option value="NOT_APPROVED">未批准</option>
              <option value="PENDING">待确认</option>
              <option value="APPROVED">已批准</option>
              <option value="REVOKED">已撤销</option>
            </select></label
          >
          <label
            >生产证据位置<input
              v-model="applicationForm.productionEvidenceRef"
              maxlength="500"
              placeholder="上线验收记录索引"
          /></label>
          <label class="full"
            >内部备注<textarea
              v-model="applicationForm.internalNote"
              rows="3"
              maxlength="3000"
              placeholder="仅供平台运营留存，不会向客户接口返回"
            />
          </label>
          <footer class="modal-actions full">
            <button class="button secondary" type="button" @click="applicationModal = false">
              取消</button
            ><button class="button" type="submit" :disabled="saving">
              {{ saving ? '保存中…' : '保存应用' }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="keyModal && keyTarget" class="modal-backdrop" @click.self="keyModal = false">
      <section
        class="modal key-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="api-key-title"
      >
        <header class="modal-header">
          <div>
            <p class="eyebrow">{{ keyTarget.applicationName }}</p>
            <h2 id="api-key-title">签发访问密钥</h2>
          </div>
          <button class="icon-button" type="button" aria-label="关闭" @click="keyModal = false">
            <X :size="20" />
          </button>
        </header>
        <p class="modal-intro">
          密钥仅在签发成功后显示一次。请在受控密钥库保存，勿放入前端代码、截图或普通文档。
        </p>
        <p v-if="actionError" class="form-error">{{ actionError }}</p>
        <form class="form-grid" @submit.prevent="issueKey">
          <label
            >密钥用途*<input
              v-model="keyForm.keyLabel"
              required
              maxlength="120"
              placeholder="例如：CRM 生产服务端" /></label
          ><label
            >到期时间*<input v-model="keyForm.expiresAt" required type="datetime-local"
          /></label>
          <footer class="modal-actions full">
            <button class="button secondary" type="button" @click="keyModal = false">取消</button
            ><button class="button" type="submit" :disabled="saving">
              <KeyRound :size="16" /> {{ saving ? '签发中…' : '签发密钥' }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="oneTimeKeyModal" class="modal-backdrop" @click.self="oneTimeKeyModal = false">
      <section
        class="modal one-time-key"
        role="dialog"
        aria-modal="true"
        aria-labelledby="issued-key-title"
      >
        <header class="modal-header">
          <div>
            <p class="eyebrow">一次性显示</p>
            <h2 id="issued-key-title">访问密钥已签发</h2>
          </div>
          <button
            class="icon-button"
            type="button"
            aria-label="关闭"
            @click="oneTimeKeyModal = false"
          >
            <X :size="20" />
          </button>
        </header>
        <p>请立即复制并保存在受控密钥库。关闭此窗口后，平台无法再次显示该密钥。</p>
        <div class="key-value">
          <code>{{ issuedKey }}</code
          ><button class="button secondary" type="button" @click="copyIssuedKey">
            <Copy :size="16" /> 复制
          </button>
        </div>
        <p class="key-meta">
          前缀：{{ issuedKeyMeta?.prefix }}…　到期：{{ formatDate(issuedKeyMeta?.expiresAt) }}
        </p>
        <footer class="modal-actions">
          <button class="button" type="button" @click="oneTimeKeyModal = false">已安全保存</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.open-api-page {
  gap: 20px;
}
.open-api-metrics {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.metric-card small,
td small {
  display: block;
  margin-top: 5px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}
.api-security-note {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 15px 18px;
  border-color: #cddbef;
  background: #f5f8ff;
  color: #38536f;
}
.api-security-note svg {
  flex: 0 0 auto;
  margin-top: 2px;
  color: #1767c5;
}
.api-security-note p {
  margin: 0;
  line-height: 1.65;
}
.tabs {
  display: flex;
  gap: 6px;
  padding: 4px;
  width: max-content;
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: #f8fafc;
}
.tabs button {
  min-height: 36px;
  padding: 0 14px;
  border: 0;
  border-radius: 6px;
  color: var(--muted);
  background: transparent;
  font: inherit;
  white-space: nowrap;
  cursor: pointer;
}
.tabs button.active {
  color: #fff;
  background: #1f5ca8;
  box-shadow: 0 1px 2px rgb(25 60 105 / 18%);
}
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.panel-heading h2 {
  margin: 0 0 6px;
  font-size: 20px;
}
.panel-heading p {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}
.table-panel {
  padding: 22px;
}
.table-scroll {
  overflow-x: auto;
}
table {
  width: 100%;
  min-width: 1040px;
  border-collapse: collapse;
}
th,
td {
  padding: 13px 12px;
  vertical-align: top;
  border-bottom: 1px solid var(--border);
  text-align: left;
}
th {
  color: #667388;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}
td {
  color: #2a3950;
  font-size: 13px;
  line-height: 1.5;
}
td strong {
  color: #1d2e47;
}
.scope-list {
  display: flex;
  min-width: 180px;
  flex-wrap: wrap;
  gap: 5px;
}
.scope-list span {
  padding: 3px 7px;
  border-radius: 999px;
  background: #edf3fb;
  color: #315b8f;
  font-size: 11px;
  white-space: nowrap;
}
.status-tag {
  display: inline-flex;
  align-items: center;
  min-height: 23px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.status-tag.success {
  background: #e7f7ed;
  color: #197343;
}
.status-tag.warning {
  background: #fff4dd;
  color: #966207;
}
.status-tag.danger {
  background: #fff0ef;
  color: #b33b35;
}
.table-actions {
  white-space: nowrap;
}
.text-button {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: #1d63bd;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.text-button + .text-button {
  margin-left: 12px;
}
.text-button:disabled {
  color: #9da9b8;
  cursor: not-allowed;
}
.text-button.danger {
  color: #bd3b34;
}
.contract-grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 18px;
}
.contract-card {
  padding: 24px;
}
.capability-list {
  display: grid;
  gap: 15px;
}
.capability-list > div {
  display: flex;
  gap: 12px;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--border);
}
.capability-list > div:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}
.capability-list svg {
  flex: 0 0 auto;
  margin-top: 2px;
  color: #1d63bd;
}
.capability-list strong,
.capability-list small,
.capability-list p {
  display: block;
}
.capability-list small {
  margin-top: 2px;
  color: #6b7890;
  font-size: 12px;
}
.capability-list p {
  margin: 4px 0 0;
  color: #516176;
  line-height: 1.6;
}
.contract-points {
  display: grid;
  gap: 12px;
  margin: 0;
  padding-left: 20px;
  color: #41516a;
  line-height: 1.6;
}
.contract-points code {
  color: #2d5d97;
}
.modal {
  width: min(760px, calc(100vw - 32px));
  max-height: min(860px, calc(100vh - 32px));
  overflow: auto;
}
.api-modal {
  width: min(920px, calc(100vw - 32px));
}
.modal-intro {
  margin: 0 0 18px;
  color: var(--muted);
  line-height: 1.65;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.form-grid label,
.scope-field {
  display: grid;
  gap: 7px;
  color: #37465d;
  font-size: 13px;
  font-weight: 600;
}
.form-grid input,
.form-grid select,
.form-grid textarea {
  width: 100%;
  min-height: 40px;
  padding: 9px 10px;
  border: 1px solid #cfd8e4;
  border-radius: 7px;
  color: #27364c;
  background: #fff;
  font: inherit;
  font-weight: 400;
}
.form-grid textarea {
  min-height: 78px;
  resize: vertical;
}
.full {
  grid-column: 1 / -1;
}
.scope-field {
  padding: 13px;
  border: 1px solid var(--border);
  border-radius: 8px;
}
.scope-field legend {
  padding: 0 5px;
  color: #37465d;
}
.check-row {
  display: flex !important;
  grid-template-columns: none !important;
  align-items: flex-start;
  gap: 9px;
  font-weight: 400 !important;
  cursor: pointer;
}
.check-row input {
  width: auto;
  min-height: auto;
  margin-top: 3px;
}
.check-row strong,
.check-row small {
  display: block;
}
.check-row strong {
  color: #2d3d54;
}
.check-row small {
  margin-top: 3px;
  color: var(--muted);
  line-height: 1.5;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}
.form-error {
  margin: 0 0 14px;
  color: #b42318;
  font-size: 13px;
}
.key-value {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px;
  border: 1px solid #bfd1ec;
  border-radius: 8px;
  background: #f5f8ff;
}
.key-value code {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  color: #193e6e;
  font-size: 13px;
}
.key-meta {
  color: var(--muted);
  font-size: 13px;
}
@media (max-width: 900px) {
  .open-api-metrics,
  .contract-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .table-panel {
    padding: 18px;
  }
}
@media (max-width: 620px) {
  .open-api-metrics,
  .contract-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
  .page-header {
    align-items: stretch;
  }
  .page-actions {
    justify-content: stretch;
  }
  .page-actions .button {
    flex: 1;
  }
  .tabs {
    width: 100%;
  }
  .tabs button {
    flex: 1;
    padding: 0 10px;
  }
  .modal {
    width: min(100vw - 18px, 760px);
  }
  .key-value {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
