<script setup lang="ts">
import {
  Check,
  GitCompareArrows,
  History,
  PencilLine,
  Search,
  SlidersHorizontal,
  X,
} from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { useToastStore } from '@/stores/toast'
import type {
  ApiResponse,
  PageResult,
  PricingChannel,
  QuoteAdjustment,
  QuoteState,
  SupplierOption,
} from '@/types'

interface PricingSummary {
  totalChannels: number
  activeQuotes: number
  expiringQuotes: number
  expiredQuotes: number
  unquotedChannels: number
}

const toast = useToastStore()
const items = ref<PricingChannel[]>([])
const summary = ref<PricingSummary | null>(null)
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const selected = ref<Record<number, PricingChannel>>({})
const comparison = ref<PricingChannel[]>([])
const quoteTarget = ref<PricingChannel | null>(null)
const quoteSubmitting = ref(false)
const batchOpen = ref(false)
const batchSubmitting = ref(false)
const historyTarget = ref<PricingChannel | null>(null)
const history = ref<QuoteAdjustment[]>([])
const historyLoading = ref(false)
const supplierOptions = ref<SupplierOption[]>([])
const BATCH_QUOTE_SUBMISSION_STATE_KEY = 'winpress:batch-quote-adjustment-submission:v1'
let batchQuoteSubmissionFallback: { fingerprint: string; key: string } | null = null

const filters = reactive({
  keyword: '',
  category: '',
  region: '',
  publishForm: '',
  channelStatus: 'ACTIVE',
  quoteState: '',
})
const quoteForm = reactive({
  supplierId: undefined as number | undefined,
  costPrice: undefined as number | undefined,
  customerPrice: undefined as number | undefined,
  validUntil: '',
  publicTerms: '',
  reason: '',
})
const batchForm = reactive({
  percentage: undefined as number | undefined,
  validUntil: '',
  publicTerms: '',
  reason: '',
})

const selectedItems = computed(() => Object.values(selected.value))
const selectedCount = computed(() => selectedItems.value.length)
const selectedTotal = computed(() =>
  selectedItems.value.reduce((totalPrice, item) => totalPrice + Number(item.customerPrice || 0), 0),
)
const pageAllSelected = computed(
  () => items.value.length > 0 && items.value.every((item) => Boolean(selected.value[item.id])),
)
const quoteGrossMargin = computed(() => {
  const cost = Number(quoteForm.costPrice)
  const price = Number(quoteForm.customerPrice)
  if (!Number.isFinite(cost) || !Number.isFinite(price) || price <= 0) return null
  return ((price - cost) / price) * 100
})

function localDateTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

function apiDateTime(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString()
}

function money(value?: number, emptyLabel = '待报价') {
  return value == null
    ? emptyLabel
    : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}

function date(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function stateLabel(value: QuoteState) {
  return { ACTIVE: '有效', EXPIRING: '即将到期', EXPIRED: '已失效', UNQUOTED: '待报价' }[value]
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [listResponse, summaryResponse, supplierResponse] = await Promise.all([
      http.get<ApiResponse<PageResult<PricingChannel>>>('/admin/pricing', {
        params: {
          keyword: filters.keyword,
          category: filters.category,
          region: filters.region,
          publish_form: filters.publishForm,
          channel_status: filters.channelStatus,
          quote_state: filters.quoteState,
          page: page.value,
          pageSize: 30,
        },
      }),
      http.get<ApiResponse<PricingSummary>>('/admin/pricing/summary'),
      http.get<ApiResponse<SupplierOption[]>>('/admin/suppliers/options'),
    ])
    items.value = listResponse.data.data.items
    total.value = listResponse.data.data.total
    summary.value = summaryResponse.data.data
    supplierOptions.value = supplierResponse.data.data
    for (const item of items.value) {
      if (selected.value[item.id]) selected.value[item.id] = item
    }
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  comparison.value = []
  load()
}

function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    category: '',
    region: '',
    publishForm: '',
    channelStatus: 'ACTIVE',
    quoteState: '',
  })
  applyFilters()
}

function changePage(next: number) {
  page.value = next
  load()
}

function toggle(item: PricingChannel) {
  if (selected.value[item.id]) {
    const next = { ...selected.value }
    delete next[item.id]
    selected.value = next
    return
  }
  if (selectedCount.value >= 5) {
    toast.show('一次最多选择 5 个渠道进行比价或批量调价', 'error')
    return
  }
  selected.value = { ...selected.value, [item.id]: item }
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
    if (Object.keys(next).length >= 5 && !next[item.id]) break
    next[item.id] = item
  }
  selected.value = next
}

function clearSelection() {
  selected.value = {}
  comparison.value = []
}

async function compare() {
  actionError.value = ''
  if (selectedCount.value < 2) {
    actionError.value = '请至少选择两个渠道再进行比价。'
    return
  }
  try {
    const { data } = await http.get<ApiResponse<PricingChannel[]>>('/admin/pricing/compare', {
      params: { channelIds: selectedItems.value.map((item) => item.id).join(',') },
    })
    comparison.value = data.data
  } catch (requestError) {
    actionError.value = apiError(requestError)
  }
}

function openQuote(item: PricingChannel) {
  quoteTarget.value = item
  Object.assign(quoteForm, {
    supplierId: item.supplierId == null ? undefined : Number(item.supplierId),
    costPrice: item.costPrice == null ? undefined : Number(item.costPrice),
    customerPrice: item.customerPrice == null ? undefined : Number(item.customerPrice),
    validUntil: localDateTime(item.validUntil),
    publicTerms: item.publicTerms || '',
    reason: '',
  })
  actionError.value = ''
}

async function saveQuote() {
  if (!quoteTarget.value) return
  const validUntil = apiDateTime(quoteForm.validUntil)
  if (!validUntil) {
    actionError.value = '请选择有效的报价截止时间。'
    return
  }
  quoteSubmitting.value = true
  actionError.value = ''
  try {
    await http.post('/admin/pricing/quotes', {
      channelId: quoteTarget.value.id,
      supplierId: quoteForm.supplierId || null,
      costPrice: quoteForm.costPrice,
      customerPrice: quoteForm.customerPrice,
      validUntil,
      publicTerms: quoteForm.publicTerms || null,
      reason: quoteForm.reason,
    })
    toast.show('新报价已生效，原报价已保留在调价记录中。', 'success')
    quoteTarget.value = null
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    quoteSubmitting.value = false
  }
}

function openBatch() {
  if (!selectedCount.value) {
    actionError.value = '请先选择需要调整的渠道。'
    return
  }
  batchOpen.value = true
  Object.assign(batchForm, { percentage: undefined, validUntil: '', publicTerms: '', reason: '' })
  actionError.value = ''
}

async function batchQuoteFingerprint(payload: object) {
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

function newBatchQuoteIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `wp-price-${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random()
    .toString(36)
    .slice(2)}`
}

async function batchQuoteIdempotencyKeyFor(payload: object) {
  const fingerprint = await batchQuoteFingerprint(payload)
  try {
    const stored = JSON.parse(
      sessionStorage.getItem(BATCH_QUOTE_SUBMISSION_STATE_KEY) || 'null',
    ) as { fingerprint?: string; key?: string } | null
    if (stored?.fingerprint === fingerprint && stored.key) return stored.key
  } catch {
    // Restricted browser storage must not make pricing operations unusable.
  }
  if (batchQuoteSubmissionFallback?.fingerprint === fingerprint) {
    return batchQuoteSubmissionFallback.key
  }
  const key = newBatchQuoteIdempotencyKey()
  batchQuoteSubmissionFallback = { fingerprint, key }
  try {
    sessionStorage.setItem(BATCH_QUOTE_SUBMISSION_STATE_KEY, JSON.stringify({ fingerprint, key }))
  } catch {
    // The in-memory fallback still protects retries in this open page.
  }
  return key
}

function clearBatchQuoteSubmissionState(key: string) {
  if (batchQuoteSubmissionFallback?.key === key) batchQuoteSubmissionFallback = null
  try {
    const stored = JSON.parse(
      sessionStorage.getItem(BATCH_QUOTE_SUBMISSION_STATE_KEY) || 'null',
    ) as { key?: string } | null
    if (stored?.key === key) {
      sessionStorage.removeItem(BATCH_QUOTE_SUBMISSION_STATE_KEY)
    }
  } catch {
    // Nothing else is required when browser storage is unavailable.
  }
}

async function saveBatch() {
  const validUntil = apiDateTime(batchForm.validUntil)
  if (!validUntil) {
    actionError.value = '请选择有效的新报价截止时间。'
    return
  }
  batchSubmitting.value = true
  actionError.value = ''
  try {
    const payload = {
      channelIds: selectedItems.value.map((item) => item.id).sort((left, right) => left - right),
      percentage: batchForm.percentage,
      validUntil,
      publicTerms: batchForm.publicTerms.trim() || null,
      reason: batchForm.reason.trim(),
    }
    const idempotencyKey = await batchQuoteIdempotencyKeyFor(payload)
    const { data } = await http.post<ApiResponse<{ adjustedCount: number }>>(
      '/admin/pricing/adjustments',
      payload,
      { headers: { 'Idempotency-Key': idempotencyKey } },
    )
    clearBatchQuoteSubmissionState(idempotencyKey)
    toast.show(`已调整 ${data.data.adjustedCount} 个渠道的客户服务价。`, 'success')
    batchOpen.value = false
    clearSelection()
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    batchSubmitting.value = false
  }
}

async function openHistory(item: PricingChannel) {
  historyTarget.value = item
  history.value = []
  historyLoading.value = true
  try {
    const { data } = await http.get<ApiResponse<QuoteAdjustment[]>>(
      `/admin/pricing/${item.id}/adjustments`,
    )
    history.value = data.data
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    historyLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <PageHeader
    eyebrow="平台运营"
    title="定价与比价"
    description="维护直编发稿服务价、有效期和调价记录；已提交订单保留提交时的报价。"
  />

  <section class="metric-grid pricing-metrics" aria-label="报价概览">
    <article class="metric-card plain-metric-card">
      <span>直编渠道</span><strong>{{ summary?.totalChannels ?? '—' }}</strong>
    </article>
    <article class="metric-card plain-metric-card">
      <span>有效报价</span><strong>{{ summary?.activeQuotes ?? '—' }}</strong>
    </article>
    <article class="metric-card plain-metric-card warning">
      <span>7 日内到期</span><strong>{{ summary?.expiringQuotes ?? '—' }}</strong>
    </article>
    <article class="metric-card plain-metric-card muted">
      <span>待报价 / 已失效</span
      ><strong>{{ (summary?.unquotedChannels ?? 0) + (summary?.expiredQuotes ?? 0) }}</strong>
    </article>
  </section>

  <section class="panel pricing-filters">
    <div class="input-icon search-input">
      <Search :size="17" /><input
        v-model="filters.keyword"
        aria-label="搜索报价渠道"
        placeholder="媒体名称、编号或分类"
        @keyup.enter="applyFilters"
      />
    </div>
    <input
      v-model="filters.category"
      aria-label="按分类筛选报价"
      placeholder="分类"
      @keyup.enter="applyFilters"
    />
    <input
      v-model="filters.region"
      aria-label="按地区筛选报价"
      placeholder="地区"
      @keyup.enter="applyFilters"
    />
    <input
      v-model="filters.publishForm"
      aria-label="按发布形式筛选报价"
      placeholder="发布形式"
      @keyup.enter="applyFilters"
    />
    <select v-model="filters.quoteState" aria-label="按报价状态筛选">
      <option value="">全部报价状态</option>
      <option value="ACTIVE">有效报价</option>
      <option value="EXPIRING">7 日内到期</option>
      <option value="EXPIRED">已失效</option>
      <option value="UNQUOTED">待报价</option>
    </select>
    <select v-model="filters.channelStatus" aria-label="按渠道状态筛选报价">
      <option value="ACTIVE">可用渠道</option>
      <option value="REVIEW_REQUIRED">待复核渠道</option>
      <option value="INACTIVE">停用渠道</option>
      <option value="">全部渠道状态</option>
    </select>
    <button class="button secondary" type="button" @click="applyFilters">
      <SlidersHorizontal :size="16" />筛选
    </button>
    <button class="button ghost" type="button" @click="resetFilters">重置</button>
  </section>

  <section v-if="comparison.length" class="panel comparison-panel">
    <header>
      <div>
        <span class="eyebrow">渠道比价</span>
        <h2>已选渠道对比</h2>
      </div>
      <button class="icon-button" type="button" aria-label="关闭比价" @click="comparison = []">
        <X :size="18" />
      </button>
    </header>
    <div class="table-wrap">
      <table class="comparison-table">
        <thead>
          <tr>
            <th>渠道</th>
            <th>分类 / 地区</th>
            <th>形式</th>
            <th>时效</th>
            <th title="仅平台运营可见">供应商</th>
            <th title="仅平台运营可见">成本价</th>
            <th>客户服务价</th>
            <th>有效期</th>
            <th>报价状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in comparison" :key="item.id">
            <td>
              <strong>{{ item.channelName }}</strong
              ><small>{{ item.channelNo }}</small>
            </td>
            <td>{{ item.category || '未分类' }} / {{ item.region || '未设置' }}</td>
            <td>{{ item.publishForm || '图文' }}</td>
            <td>{{ item.expectedDays ? `${item.expectedDays} 个工作日` : '另行确认' }}</td>
            <td>{{ item.supplierName || '待分配' }}</td>
            <td class="price-cell cost-price">
              <strong>{{ money(item.costPrice, '未录入') }}</strong>
            </td>
            <td>
              <strong>{{ money(item.customerPrice) }}</strong>
            </td>
            <td>{{ date(item.validUntil) }}</td>
            <td>
              <span class="quote-state" :class="item.quoteState.toLowerCase()">{{
                stateLabel(item.quoteState)
              }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <section class="panel table-panel pricing-table-panel">
    <DataState
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-title="没有符合条件的直编渠道"
      @retry="load"
    >
      <template #content>
        <div class="table-wrap">
          <table class="pricing-table">
            <thead>
              <tr>
                <th>
                  <button
                    class="table-check"
                    type="button"
                    :aria-label="pageAllSelected ? '取消本页选择' : '选择本页渠道'"
                    @click="togglePage"
                  >
                    <Check :size="15" />
                  </button>
                </th>
                <th>渠道</th>
                <th>分类 / 地区</th>
                <th>形式 / 时效</th>
                <th title="仅平台运营可见">供应商</th>
                <th title="仅平台运营可见">成本价</th>
                <th>客户服务价</th>
                <th>报价有效期</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in items"
                :key="item.id"
                :class="{ selected: selected[item.id] }"
                @click="toggle(item)"
              >
                <td>
                  <button
                    class="table-check"
                    type="button"
                    :class="{ checked: selected[item.id] }"
                    :aria-label="selected[item.id] ? '取消选择' : '选择渠道'"
                    @click.stop="toggle(item)"
                  >
                    <Check :size="15" />
                  </button>
                </td>
                <td>
                  <strong>{{ item.channelName }}</strong
                  ><small>{{ item.channelNo }}</small>
                </td>
                <td>
                  {{ item.category || '未分类' }}<small>{{ item.region || '未设置' }}</small>
                </td>
                <td>
                  {{ item.publishForm || '图文'
                  }}<small>{{
                    item.expectedDays ? `${item.expectedDays} 个工作日` : '时效另议'
                  }}</small>
                </td>
                <td>
                  {{ item.supplierName || '待分配'
                  }}<small v-if="item.supplierName">内部执行关系</small>
                </td>
                <td class="price-cell cost-price">
                  <strong>{{ money(item.costPrice, '未录入') }}</strong>
                </td>
                <td>
                  <strong>{{ money(item.customerPrice) }}</strong>
                </td>
                <td>{{ date(item.validUntil) }}</td>
                <td>
                  <span class="quote-state" :class="item.quoteState.toLowerCase()">{{
                    stateLabel(item.quoteState)
                  }}</span>
                </td>
                <td class="pricing-actions">
                  <button class="text-button" type="button" @click.stop="openQuote(item)">
                    <PencilLine :size="15" />调价
                  </button>
                  <button class="text-button" type="button" @click.stop="openHistory(item)">
                    <History :size="15" />记录
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar :page="page" :page-size="30" :total="total" @change="changePage" />
      </template>
    </DataState>
  </section>

  <section v-if="selectedCount" class="selection-dock pricing-selection-dock">
    <div>
      <strong>已选 {{ selectedCount }} 个渠道</strong
      ><span>当前客户服务价合计 {{ money(selectedTotal) }}；最多选择 5 个，便于逐项核对。</span>
    </div>
    <p v-if="actionError" class="form-error">{{ actionError }}</p>
    <div class="selection-dock-actions">
      <button class="button secondary" type="button" :disabled="selectedCount < 2" @click="compare">
        <GitCompareArrows :size="16" />比价
      </button>
      <button class="button primary" type="button" @click="openBatch">
        <PencilLine :size="16" />批量调价
      </button>
      <button class="button ghost" type="button" @click="clearSelection">清空</button>
    </div>
  </section>

  <div v-if="quoteTarget" class="modal-backdrop" @click.self="quoteTarget = null">
    <form class="modal-panel compact-form" @submit.prevent="saveQuote">
      <header>
        <div>
          <span class="eyebrow">人工调价</span>
          <h2>{{ quoteTarget.channelName }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="quoteTarget = null">
          <X :size="19" />
        </button>
      </header>
      <p class="form-hint">新报价会替换当前有效报价，并记录本次调整原因；已提交订单不受影响。</p>
      <div class="form-grid two-columns">
        <label class="full"
          >供应商<select v-model.number="quoteForm.supplierId">
            <option :value="undefined">暂不指定</option>
            <option v-for="supplier in supplierOptions" :key="supplier.id" :value="supplier.id">
              {{ supplier.supplierName }} · {{ supplier.supplierNo }}
            </option>
          </select>
          <small>只可选择已在“供应商管理”中关联当前渠道的供应商。</small>
        </label>
        <label
          >成本价<input
            v-model.number="quoteForm.costPrice"
            type="number"
            min="0"
            step="0.01"
            placeholder="仅平台运营可见"
        /></label>
        <label>
          <span class="field-label">客户服务价<span class="required">*</span></span>
          <input
            v-model.number="quoteForm.customerPrice"
            required
            type="number"
            min="0.01"
            step="0.01"
          />
        </label>
        <label class="full">
          <span class="field-label">报价有效期<span class="required">*</span></span>
          <input v-model="quoteForm.validUntil" required type="datetime-local" />
        </label>
        <p v-if="quoteGrossMargin != null" class="form-hint full">
          当前毛利率约 {{ quoteGrossMargin.toFixed(1) }}%。客户服务价不得低于成本价。
        </p>
        <label class="full"
          >对客说明<textarea
            v-model="quoteForm.publicTerms"
            rows="3"
            maxlength="1000"
            placeholder="如无特别说明，将使用平台默认说明。"
          />
        </label>
        <label class="full">
          <span class="field-label">调价原因<span class="required">*</span></span>
          <input
            v-model="quoteForm.reason"
            required
            maxlength="300"
            placeholder="例如：渠道年度价格调整"
          />
        </label>
      </div>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
      <div class="form-actions">
        <button class="button secondary" type="button" @click="quoteTarget = null">取消</button
        ><button class="button primary" type="submit" :disabled="quoteSubmitting">
          {{ quoteSubmitting ? '正在保存' : '确认调价' }}
        </button>
      </div>
    </form>
  </div>

  <div v-if="batchOpen" class="modal-backdrop" @click.self="batchOpen = false">
    <form class="modal-panel compact-form" @submit.prevent="saveBatch">
      <header>
        <div>
          <span class="eyebrow">批量调价</span>
          <h2>调整 {{ selectedCount }} 个已选渠道</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="batchOpen = false">
          <X :size="19" />
        </button>
      </header>
      <p class="form-hint">
        仅对当前已选渠道生效。调整比例限制在 -50% 至 50%，提交前请完成比价核对。
      </p>
      <div class="form-grid two-columns">
        <label>
          <span class="field-label">调整比例（%）<span class="required">*</span></span>
          <input
            v-model.number="batchForm.percentage"
            required
            type="number"
            min="-50"
            max="50"
            step="0.01"
            placeholder="正数上调，负数下调"
          />
        </label>
        <label>
          <span class="field-label">新报价有效期<span class="required">*</span></span>
          <input v-model="batchForm.validUntil" required type="datetime-local" />
        </label>
        <label class="full"
          >对客说明<textarea
            v-model="batchForm.publicTerms"
            rows="3"
            maxlength="1000"
            placeholder="如无特别说明，将使用平台默认说明。"
          />
        </label>
        <label class="full">
          <span class="field-label">调价原因<span class="required">*</span></span>
          <input
            v-model="batchForm.reason"
            required
            maxlength="300"
            placeholder="例如：季度统一调整"
          />
        </label>
      </div>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
      <div class="form-actions">
        <button class="button secondary" type="button" @click="batchOpen = false">取消</button
        ><button class="button primary" type="submit" :disabled="batchSubmitting">
          {{ batchSubmitting ? '正在保存' : '确认批量调价' }}
        </button>
      </div>
    </form>
  </div>

  <div v-if="historyTarget" class="modal-backdrop" @click.self="historyTarget = null">
    <section class="modal-panel history-modal">
      <header>
        <div>
          <span class="eyebrow">调价记录</span>
          <h2>{{ historyTarget.channelName }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="historyTarget = null">
          <X :size="19" />
        </button>
      </header>
      <DataState :loading="historyLoading" :empty="!history.length" empty-title="尚无人工调价记录"
        ><template #content
          ><div class="history-list">
            <article v-for="record in history" :key="record.adjustmentNo">
              <div>
                <strong
                  >{{ money(record.previousCustomerPrice) }} →
                  {{ money(record.currentCustomerPrice) }}</strong
                ><small v-if="record.previousCostPrice != null || record.currentCostPrice != null"
                  >成本 {{ money(record.previousCostPrice, '未录入') }} →
                  {{ money(record.currentCostPrice, '未录入') }}</small
                ><small
                  >{{ record.adjustmentMode === 'MANUAL' ? '人工调价' : '批量比例调整' }} ·
                  {{ date(record.createdAt) }} · {{ record.adjustedBy || '平台运营' }}</small
                >
              </div>
              <p>{{ record.reason }}</p>
            </article>
          </div></template
        ></DataState
      >
    </section>
  </div>
</template>
