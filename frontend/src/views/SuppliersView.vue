<script setup lang="ts">
import {
  Boxes,
  Building2,
  CircleDollarSign,
  Link2,
  PencilLine,
  Plus,
  Search,
  Truck,
  X,
} from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useToastStore } from '@/stores/toast'
import type { ApiResponse, PageResult, Supplier, SupplierOption, SupplierOrder } from '@/types'

interface SupplierOrderSummary {
  totalOrders: number
  pendingSubmission: number
  executingOrders: number
  unverifiedOrders: number
  exceptionOrders: number
  completedOrders: number
  customerAmount: number
  costAmount: number
}

interface SupplierOrderHistoryItem {
  historyNo: string
  previousStatus?: string
  currentStatus: string
  note?: string
  changedByName?: string
  createdAt: string
}

interface SupplierChannel {
  id: number
  mappingNo: string
  supplierId: number
  supplierNo: string
  supplierName: string
  channelId: number
  channelNo: string
  channelName: string
  channelType: string
  externalProductCode?: string
  serviceScope?: string
  priority: number
  status: string
}

interface ChannelOption {
  id: number
  channelNo: string
  channelName: string
  channelType: string
  category?: string
  region?: string
}

type Tab = 'suppliers' | 'mappings' | 'orders'

const SUPPLIER_ORDER_TRANSITIONS: Record<string, readonly string[]> = {
  PENDING_SUBMISSION: ['PENDING_SUBMISSION', 'SUBMITTED', 'CANCELLED'],
  SUBMITTED: ['SUBMITTED', 'ACCEPTED', 'IN_PROGRESS', 'EXCEPTION', 'CANCELLED'],
  ACCEPTED: ['ACCEPTED', 'IN_PROGRESS', 'EXCEPTION', 'CANCELLED'],
  IN_PROGRESS: ['IN_PROGRESS', 'COMPLETED', 'EXCEPTION', 'CANCELLED'],
  EXCEPTION: ['EXCEPTION', 'PENDING_SUBMISSION', 'SUBMITTED', 'CANCELLED'],
  COMPLETED: ['COMPLETED'],
  CANCELLED: ['CANCELLED'],
}

const route = useRoute()
const router = useRouter()
const toast = useToastStore()
const tab = ref<Tab>(normalizeTab(route.query.tab))
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const page = ref(1)
const total = ref(0)
const suppliers = ref<Supplier[]>([])
const supplierOptions = ref<SupplierOption[]>([])
const orderSupplierOptions = ref<SupplierOption[]>([])
const mappings = ref<SupplierChannel[]>([])
const orders = ref<SupplierOrder[]>([])
const summary = ref<SupplierOrderSummary | null>(null)
const supplierModal = ref(false)
const orderTarget = ref<SupplierOrder | null>(null)
const orderHistory = ref<SupplierOrderHistoryItem[]>([])
const orderHistoryLoading = ref(false)
const orderHistoryError = ref('')
const orderSupplierOptionsLoading = ref(false)
const orderSupplierOptionsError = ref('')
const saving = ref(false)
const channelSearching = ref(false)
const channelOptions = ref<ChannelOption[]>([])

const supplierFilters = reactive({ keyword: '', type: '', status: '' })
const orderFilters = reactive({ keyword: '', status: '', supplierId: '' as number | '' })
const supplierForm = reactive({
  id: undefined as number | undefined,
  supplierName: '',
  supplierType: 'DIRECT_PUBLISHING',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  serviceScope: '',
  internalNote: '',
  status: 'ACTIVE',
})
const mappingForm = reactive({
  supplierId: '' as number | '',
  channelId: '' as number | '',
  externalProductCode: '',
  serviceScope: '',
  priority: 100,
})
const channelKeyword = ref('')
const orderForm = reactive({
  supplierId: '' as number | '',
  status: 'PENDING_SUBMISSION',
  fulfillmentMode: 'UNCONFIRMED',
  externalOrderNo: '',
  submissionEvidenceReference: '',
  note: '',
  exceptionReason: '',
})

const grossMargin = computed(() => {
  const customer = Number(summary.value?.customerAmount || 0)
  const cost = Number(summary.value?.costAmount || 0)
  return customer > 0 ? ((customer - cost) / customer) * 100 : null
})

const currentOrderSupplierIsEligible = computed(() => {
  if (!orderForm.supplierId) return true
  return orderSupplierOptions.value.some((item) => item.id === orderForm.supplierId)
})

const availableOrderStatuses = computed(() => {
  const persistedStatus = orderTarget.value?.status || orderForm.status
  return SUPPLIER_ORDER_TRANSITIONS[persistedStatus] || [persistedStatus]
})

function money(value?: number) {
  return value == null
    ? '—'
    : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function supplierTypeLabel(value: string) {
  return (
    {
      MEDIA_PR: '媒体公关',
      DIRECT_PUBLISHING: '直编发稿',
      WRITING: '云采写',
      EVENT_SERVICE: '会务执行',
      MULTI_SERVICE: '综合服务',
    }[value] || value
  )
}

function orderStatusLabel(value: string) {
  return (
    {
      PENDING_SUBMISSION: '待提交',
      SUBMITTED: '已提交',
      ACCEPTED: '已接单',
      IN_PROGRESS: '执行中',
      EXCEPTION: '异常',
      COMPLETED: '供应商完成',
      CANCELLED: '已取消',
    }[value] || value
  )
}

function fulfillmentModeLabel(value?: string) {
  return (
    {
      UNCONFIRMED: '尚未提交',
      MANUAL: '人工凭据',
      API: '接口回执',
    }[value || 'UNCONFIRMED'] || value
  )
}

function orderEvidenceRequired(status: string) {
  return ['SUBMITTED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED'].includes(status)
}

function isPendingSupplierOrder(status: string) {
  return status === 'PENDING_SUBMISSION'
}

function orderNeedsVerification(item: SupplierOrder) {
  return (
    orderEvidenceRequired(item.status) &&
    (item.fulfillmentMode === 'UNCONFIRMED' || !item.submissionEvidenceReference)
  )
}

async function loadOptions() {
  const { data } = await http.get<ApiResponse<SupplierOption[]>>('/admin/suppliers/options')
  supplierOptions.value = data.data
}

async function loadOrderSupplierOptions(channelId: number) {
  orderSupplierOptionsLoading.value = true
  orderSupplierOptionsError.value = ''
  try {
    const { data } = await http.get<ApiResponse<SupplierOption[]>>('/admin/suppliers/options', {
      params: { channelId },
    })
    orderSupplierOptions.value = data.data
  } catch (requestError) {
    orderSupplierOptions.value = []
    orderSupplierOptionsError.value = apiError(requestError)
  } finally {
    orderSupplierOptionsLoading.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  actionError.value = ''
  try {
    if (tab.value === 'suppliers') {
      const { data } = await http.get<ApiResponse<PageResult<Supplier>>>('/admin/suppliers', {
        params: { ...supplierFilters, page: page.value, pageSize: 20 },
      })
      suppliers.value = data.data.items
      total.value = data.data.total
    } else if (tab.value === 'mappings') {
      const { data } = await http.get<ApiResponse<PageResult<SupplierChannel>>>(
        '/admin/supplier-channels',
        {
          params: { page: page.value, pageSize: 30 },
        },
      )
      mappings.value = data.data.items
      total.value = data.data.total
      await loadOptions()
    } else {
      const [listResponse, summaryResponse] = await Promise.all([
        http.get<ApiResponse<PageResult<SupplierOrder>>>('/admin/supplier-orders', {
          params: {
            keyword: orderFilters.keyword,
            status: orderFilters.status,
            supplierId: orderFilters.supplierId || undefined,
            page: page.value,
            pageSize: 20,
          },
        }),
        http.get<ApiResponse<SupplierOrderSummary>>('/admin/supplier-orders/summary'),
        loadOptions(),
      ])
      orders.value = listResponse.data.data.items
      total.value = listResponse.data.data.total
      summary.value = summaryResponse.data.data
    }
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    loading.value = false
  }
}

function switchTab(next: Tab) {
  if (tab.value === next) return
  tab.value = next
  page.value = 1
  void router.replace({ query: { ...route.query, tab: next } })
  load()
}

function normalizeTab(value: unknown): Tab {
  const candidate = Array.isArray(value) ? value[0] : value
  return candidate === 'mappings' || candidate === 'orders' ? candidate : 'suppliers'
}

function applyFilters() {
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

function openSupplier(item?: Supplier) {
  Object.assign(supplierForm, {
    id: item?.id,
    supplierName: item?.supplierName || '',
    supplierType: item?.supplierType || 'DIRECT_PUBLISHING',
    contactName: item?.contactName || '',
    contactPhone: item?.contactPhone || '',
    contactEmail: item?.contactEmail || '',
    serviceScope: item?.serviceScope || '',
    internalNote: item?.internalNote || '',
    status: item?.status || 'ACTIVE',
  })
  actionError.value = ''
  supplierModal.value = true
}

async function saveSupplier() {
  saving.value = true
  actionError.value = ''
  try {
    const payload = {
      supplierName: supplierForm.supplierName,
      supplierType: supplierForm.supplierType,
      contactName: supplierForm.contactName || null,
      contactPhone: supplierForm.contactPhone || null,
      contactEmail: supplierForm.contactEmail || null,
      serviceScope: supplierForm.serviceScope || null,
      internalNote: supplierForm.internalNote || null,
      status: supplierForm.status,
    }
    if (supplierForm.id) {
      await http.put(`/admin/suppliers/${supplierForm.id}`, payload)
    } else {
      await http.post('/admin/suppliers', payload)
    }
    toast.show(supplierForm.id ? '供应商资料已更新' : '供应商已创建', 'success')
    supplierModal.value = false
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    saving.value = false
  }
}

async function searchChannels() {
  channelSearching.value = true
  actionError.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<ChannelOption>>>('/admin/channels', {
      params: { keyword: channelKeyword.value, page: 1, pageSize: 20 },
    })
    channelOptions.value = data.data.items
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    channelSearching.value = false
  }
}

async function saveMapping() {
  saving.value = true
  actionError.value = ''
  try {
    await http.post('/admin/supplier-channels', {
      supplierId: mappingForm.supplierId,
      channelId: mappingForm.channelId,
      externalProductCode: mappingForm.externalProductCode || null,
      serviceScope: mappingForm.serviceScope || null,
      priority: mappingForm.priority,
    })
    toast.show('供应商渠道关系已保存', 'success')
    Object.assign(mappingForm, {
      supplierId: '',
      channelId: '',
      externalProductCode: '',
      serviceScope: '',
      priority: 100,
    })
    channelOptions.value = []
    channelKeyword.value = ''
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    saving.value = false
  }
}

async function openOrder(item: SupplierOrder) {
  orderTarget.value = item
  orderHistory.value = []
  orderHistoryError.value = ''
  orderSupplierOptions.value = []
  orderSupplierOptionsError.value = ''
  Object.assign(orderForm, {
    supplierId: item.supplierId || '',
    status: item.status,
    fulfillmentMode: item.fulfillmentMode || 'UNCONFIRMED',
    externalOrderNo: item.externalOrderNo || '',
    submissionEvidenceReference: item.submissionEvidenceReference || '',
    note: item.submissionNote || '',
    exceptionReason: item.exceptionReason || '',
  })
  actionError.value = ''
  orderHistoryLoading.value = true
  void loadOrderSupplierOptions(item.channelId)
  try {
    const { data } = await http.get<ApiResponse<SupplierOrderHistoryItem[]>>(
      `/admin/supplier-orders/${item.id}/history`,
    )
    orderHistory.value = data.data
  } catch (requestError) {
    orderHistoryError.value = apiError(requestError)
  } finally {
    orderHistoryLoading.value = false
  }
}

watch(
  () => orderForm.status,
  (status) => {
    if (!isPendingSupplierOrder(status)) return
    orderForm.fulfillmentMode = 'UNCONFIRMED'
    orderForm.externalOrderNo = ''
    orderForm.submissionEvidenceReference = ''
  },
)

watch(
  () => orderForm.fulfillmentMode,
  (mode) => {
    if (mode !== 'API') orderForm.externalOrderNo = ''
  },
)

async function saveOrder() {
  if (!orderTarget.value) return
  saving.value = true
  actionError.value = ''
  try {
    await http.patch(`/admin/supplier-orders/${orderTarget.value.id}`, {
      supplierId: orderForm.supplierId || null,
      status: orderForm.status,
      fulfillmentMode: orderForm.fulfillmentMode,
      externalOrderNo: orderForm.externalOrderNo || null,
      submissionEvidenceReference: orderForm.submissionEvidenceReference || null,
      note: orderForm.note || null,
      exceptionReason: orderForm.exceptionReason || null,
    })
    toast.show('供应商订单状态已更新', 'success')
    orderTarget.value = null
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    saving.value = false
  }
}

watch(
  () => route.query.tab,
  (value) => {
    const next = normalizeTab(value)
    if (next === tab.value) return
    tab.value = next
    page.value = 1
    load()
  },
)

onMounted(load)
</script>

<template>
  <PageHeader
    eyebrow="平台运营"
    title="供应商与订单"
    description="管理供应商、渠道执行关系和供应商订单；联系方式与成本信息仅平台运营可见。"
  />

  <nav class="subnav-tabs" aria-label="供应商管理栏目">
    <button :class="{ active: tab === 'suppliers' }" @click="switchTab('suppliers')">
      <Building2 :size="17" />供应商
    </button>
    <button :class="{ active: tab === 'mappings' }" @click="switchTab('mappings')">
      <Link2 :size="17" />渠道关系
    </button>
    <button :class="{ active: tab === 'orders' }" @click="switchTab('orders')">
      <Truck :size="17" />供应商订单
    </button>
  </nav>

  <template v-if="tab === 'suppliers'">
    <section class="panel supplier-toolbar">
      <div class="input-icon">
        <Search :size="17" />
        <input
          v-model="supplierFilters.keyword"
          placeholder="供应商名称、编号或联系人"
          @keyup.enter="applyFilters"
        />
      </div>
      <select v-model="supplierFilters.type" aria-label="供应商类型">
        <option value="">全部类型</option>
        <option value="MEDIA_PR">媒体公关</option>
        <option value="DIRECT_PUBLISHING">直编发稿</option>
        <option value="WRITING">云采写</option>
        <option value="EVENT_SERVICE">会务执行</option>
        <option value="MULTI_SERVICE">综合服务</option>
      </select>
      <select v-model="supplierFilters.status" aria-label="供应商状态">
        <option value="">全部状态</option>
        <option value="ACTIVE">可用</option>
        <option value="INACTIVE">停用</option>
      </select>
      <button class="button secondary" type="button" @click="applyFilters">筛选</button>
      <button class="button primary" type="button" @click="openSupplier()">
        <Plus :size="16" />新增供应商
      </button>
    </section>
    <section class="panel table-panel">
      <DataState
        :loading="loading"
        :error="error"
        :empty="!suppliers.length"
        empty-title="暂无供应商"
        @retry="load"
      >
        <template #content>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>供应商</th>
                  <th>类型</th>
                  <th>联系人</th>
                  <th>服务范围</th>
                  <th>渠道 / 在途订单</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in suppliers" :key="item.id">
                  <td>
                    <strong>{{ item.supplierName }}</strong
                    ><small>{{ item.supplierNo }}</small>
                  </td>
                  <td>{{ supplierTypeLabel(item.supplierType) }}</td>
                  <td>
                    {{ item.contactName || '未录入'
                    }}<small>{{ item.contactPhone || item.contactEmail || '—' }}</small>
                  </td>
                  <td>{{ item.serviceScope || '未填写' }}</td>
                  <td>{{ item.channelCount }} / {{ item.activeOrderCount }}</td>
                  <td><StatusTag :status="item.status" /></td>
                  <td>
                    <button class="text-button" type="button" @click="openSupplier(item)">
                      <PencilLine :size="15" />编辑
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <PaginationBar :page="page" :page-size="20" :total="total" @change="changePage" />
        </template>
      </DataState>
    </section>
  </template>

  <template v-else-if="tab === 'mappings'">
    <section class="panel mapping-editor">
      <div>
        <span class="eyebrow">执行关系</span>
        <h2>渠道供应商配置</h2>
        <p>报价可进一步指定供应商；未指定时，系统按这里的优先级生成待提交订单。</p>
      </div>
      <form class="form-grid two-columns" @submit.prevent="saveMapping">
        <label
          ><span class="field-label">供应商<span class="required">*</span></span
          ><select v-model.number="mappingForm.supplierId" required>
            <option value="">选择供应商</option>
            <option v-for="item in supplierOptions" :key="item.id" :value="item.id">
              {{ item.supplierName }}
            </option>
          </select></label
        >
        <label
          >优先级<input v-model.number="mappingForm.priority" type="number" min="1" max="999"
        /></label>
        <label class="full channel-search-label"
          >查找渠道
          <span>
            <input
              v-model="channelKeyword"
              placeholder="输入媒体名称或渠道编号"
              @keyup.enter.prevent="searchChannels"
            />
            <button
              class="button secondary"
              type="button"
              :disabled="channelSearching"
              @click="searchChannels"
            >
              <Search :size="16" />{{ channelSearching ? '查找中' : '查找' }}
            </button>
          </span>
        </label>
        <label class="full"
          ><span class="field-label">渠道<span class="required">*</span></span
          ><select v-model.number="mappingForm.channelId" required>
            <option value="">从查找结果选择</option>
            <option v-for="item in channelOptions" :key="item.id" :value="item.id">
              {{ item.channelName }} · {{ item.channelNo }}
            </option>
          </select></label
        >
        <label
          >供应商产品编码<input v-model="mappingForm.externalProductCode" maxlength="120"
        /></label>
        <label>服务范围<input v-model="mappingForm.serviceScope" maxlength="1000" /></label>
        <div class="form-actions full">
          <button class="button primary" type="submit" :disabled="saving">
            <Link2 :size="16" />保存关系
          </button>
        </div>
      </form>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
    </section>
    <section class="panel table-panel">
      <DataState :loading="loading" :error="error" :empty="!mappings.length" @retry="load">
        <template #content>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>供应商</th>
                  <th>渠道</th>
                  <th>类型</th>
                  <th>产品编码</th>
                  <th>优先级</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in mappings" :key="item.id">
                  <td>
                    <strong>{{ item.supplierName }}</strong
                    ><small>{{ item.supplierNo }}</small>
                  </td>
                  <td>
                    <strong>{{ item.channelName }}</strong
                    ><small>{{ item.channelNo }}</small>
                  </td>
                  <td>{{ item.channelType === 'MEDIA_PR' ? '媒体公关' : '直编发稿' }}</td>
                  <td>{{ item.externalProductCode || '—' }}</td>
                  <td>{{ item.priority }}</td>
                  <td><StatusTag :status="item.status" /></td>
                </tr>
              </tbody>
            </table>
          </div>
          <PaginationBar :page="page" :page-size="30" :total="total" @change="changePage" />
        </template>
      </DataState>
    </section>
  </template>

  <template v-else>
    <section class="metric-grid supplier-order-metrics">
      <article class="metric-card plain-metric-card">
        <span>供应商订单</span><strong>{{ summary?.totalOrders ?? '—' }}</strong>
      </article>
      <article class="metric-card plain-metric-card warning">
        <span>待提交</span><strong>{{ summary?.pendingSubmission ?? '—' }}</strong>
      </article>
      <article class="metric-card plain-metric-card">
        <span>已核验在途</span><strong>{{ summary?.executingOrders ?? '—' }}</strong>
      </article>
      <article class="metric-card plain-metric-card warning">
        <span>历史状态待核验</span><strong>{{ summary?.unverifiedOrders ?? '—' }}</strong>
      </article>
      <article class="metric-card plain-metric-card danger">
        <span>异常</span><strong>{{ summary?.exceptionOrders ?? '—' }}</strong>
      </article>
      <article class="metric-card plain-metric-card">
        <span>订单毛利率</span
        ><strong>{{ grossMargin == null ? '—' : `${grossMargin.toFixed(1)}%` }}</strong>
      </article>
    </section>
    <section class="panel supplier-toolbar">
      <div class="input-icon">
        <Search :size="17" />
        <input
          v-model="orderFilters.keyword"
          placeholder="任务号、计划号、项目号、订单号、渠道或供应商"
          @keyup.enter="applyFilters"
        />
      </div>
      <select v-model="orderFilters.supplierId" aria-label="按供应商筛选">
        <option value="">全部供应商</option>
        <option v-for="item in supplierOptions" :key="item.id" :value="item.id">
          {{ item.supplierName }}
        </option>
      </select>
      <select v-model="orderFilters.status" aria-label="按订单状态筛选">
        <option value="">全部状态</option>
        <option value="PENDING_SUBMISSION">待提交</option>
        <option value="SUBMITTED">已提交</option>
        <option value="ACCEPTED">已接单</option>
        <option value="IN_PROGRESS">执行中</option>
        <option value="EXCEPTION">异常</option>
        <option value="COMPLETED">供应商完成</option>
        <option value="CANCELLED">已取消</option>
      </select>
      <button class="button secondary" type="button" @click="applyFilters">筛选</button>
    </section>
    <section class="panel table-panel">
      <DataState
        :loading="loading"
        :error="error"
        :empty="!orders.length"
        empty-title="暂无供应商订单"
        @retry="load"
      >
        <template #content>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>订单 / 任务 / 项目</th>
                  <th>供应商</th>
                  <th>渠道</th>
                  <th>客户价</th>
                  <th title="仅平台运营可见">成本价</th>
                  <th>履约依据</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in orders" :key="item.id">
                  <td>
                    <strong>{{ item.supplierOrderNo }}</strong
                    ><small>{{ item.taskNo }} · {{ item.planNo }}</small
                    ><small>{{ item.projectName }} · {{ item.projectNo }}</small>
                  </td>
                  <td>
                    {{ item.supplierName
                    }}<small>{{ item.externalOrderNo || '未提交上游单号' }}</small>
                  </td>
                  <td>
                    {{ item.channelName }}<small>{{ item.channelNo }}</small>
                  </td>
                  <td>{{ money(item.customerPrice) }}</td>
                  <td class="cost-price">{{ money(item.costPrice) }}</td>
                  <td>
                    {{ fulfillmentModeLabel(item.fulfillmentMode) }}
                    <small>{{
                      item.submissionEvidenceReference ? '已登记证据' : '暂无可核验依据'
                    }}</small>
                  </td>
                  <td>
                    <StatusTag
                      :status="orderNeedsVerification(item) ? 'PENDING_VERIFICATION' : item.status"
                    />
                    <small>{{
                      orderNeedsVerification(item)
                        ? `原记录：${orderStatusLabel(item.status)}`
                        : orderStatusLabel(item.status)
                    }}</small>
                  </td>
                  <td>{{ new Date(item.updatedAt).toLocaleString('zh-CN') }}</td>
                  <td>
                    <button class="text-button" type="button" @click="openOrder(item)">
                      <PencilLine :size="15" />处理
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <PaginationBar :page="page" :page-size="20" :total="total" @change="changePage" />
        </template>
      </DataState>
    </section>
  </template>

  <div v-if="supplierModal" class="modal-backdrop" @click.self="supplierModal = false">
    <form class="modal-panel compact-form" @submit.prevent="saveSupplier">
      <header>
        <div>
          <span class="eyebrow">内部供应商资料</span>
          <h2>{{ supplierForm.id ? '编辑供应商' : '新增供应商' }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="supplierModal = false">
          <X :size="19" />
        </button>
      </header>
      <div class="form-grid two-columns">
        <label class="full"
          ><span class="field-label">供应商名称<span class="required">*</span></span
          ><input v-model="supplierForm.supplierName" required maxlength="180"
        /></label>
        <label
          ><span class="field-label">供应商类型<span class="required">*</span></span
          ><select v-model="supplierForm.supplierType" required>
            <option value="MEDIA_PR">媒体公关</option>
            <option value="DIRECT_PUBLISHING">直编发稿</option>
            <option value="WRITING">云采写</option>
            <option value="EVENT_SERVICE">会务执行</option>
            <option value="MULTI_SERVICE">综合服务</option>
          </select></label
        >
        <label
          >状态<select v-model="supplierForm.status">
            <option value="ACTIVE">可用</option>
            <option value="INACTIVE">停用</option>
          </select></label
        >
        <label>联系人<input v-model="supplierForm.contactName" maxlength="80" /></label>
        <label>联系电话<input v-model="supplierForm.contactPhone" maxlength="30" /></label>
        <label class="full"
          >联系邮箱<input v-model="supplierForm.contactEmail" type="email" maxlength="160"
        /></label>
        <label class="full"
          >服务范围<textarea v-model="supplierForm.serviceScope" rows="3" maxlength="2000" />
        </label>
        <label class="full"
          >内部备注<textarea v-model="supplierForm.internalNote" rows="3" maxlength="2000" />
        </label>
      </div>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
      <div class="form-actions">
        <button class="button secondary" type="button" @click="supplierModal = false">取消</button>
        <button class="button primary" type="submit" :disabled="saving">
          <Boxes :size="16" />{{ saving ? '保存中' : '保存供应商' }}
        </button>
      </div>
    </form>
  </div>

  <div v-if="orderTarget" class="modal-backdrop" @click.self="orderTarget = null">
    <form class="modal-panel compact-form" @submit.prevent="saveOrder">
      <header>
        <div>
          <span class="eyebrow">供应商订单</span>
          <h2>{{ orderTarget.supplierOrderNo }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="orderTarget = null">
          <X :size="19" />
        </button>
      </header>
      <p class="form-hint">
        {{ orderTarget.projectName }} ·
        {{
          orderTarget.channelName
        }}。内部生成订单不代表已经向供应商提交；状态推进必须有人工凭据或已验收接口回执。
      </p>
      <p v-if="isPendingSupplierOrder(orderForm.status)" class="form-hint reset-hint">
        重新进入待提交会清除本轮上游订单号与履约凭据；此前状态记录仍可追溯。
      </p>
      <div class="form-grid two-columns">
        <label
          ><span class="field-label">供应商</span
          ><select
            v-model.number="orderForm.supplierId"
            :disabled="orderSupplierOptionsLoading || Boolean(orderSupplierOptionsError)"
          >
            <option value="">待分配</option>
            <option
              v-if="orderForm.supplierId && !currentOrderSupplierIsEligible"
              :value="orderForm.supplierId"
              disabled
            >
              当前供应商未关联当前渠道或已停用
            </option>
            <option v-for="item in orderSupplierOptions" :key="item.id" :value="item.id">
              {{ item.supplierName }}
            </option></select
          ><small v-if="orderSupplierOptionsLoading">正在加载当前渠道可用供应商…</small
          ><small v-else-if="orderSupplierOptionsError" class="form-error"
            >候选供应商暂不可加载：{{ orderSupplierOptionsError }}</small
          ><small v-else-if="orderSupplierOptions.length === 0"
            >当前渠道暂无可用供应商，请先在“渠道关系”中建立关联。</small
          ><small v-else>仅展示已关联当前渠道的可用供应商。</small></label
        >
        <label
          ><span class="field-label">订单状态<span class="required">*</span></span
          ><select v-model="orderForm.status" required>
            <option v-for="status in availableOrderStatuses" :key="status" :value="status">
              {{ orderStatusLabel(status) }}
            </option></select
          ><small>仅显示当前订单可进入的状态；保存时仍由后端复核。</small></label
        >
        <label
          ><span class="field-label"
            >履约记录方式<span v-if="orderEvidenceRequired(orderForm.status)" class="required"
              >*</span
            ></span
          ><select
            v-model="orderForm.fulfillmentMode"
            :disabled="isPendingSupplierOrder(orderForm.status)"
            required
          >
            <option value="UNCONFIRMED">尚未提交或待核验</option>
            <option value="MANUAL">人工提交凭据</option>
            <option value="API">受控接口回执</option>
          </select></label
        >
        <label v-if="orderForm.fulfillmentMode === 'API'"
          ><span class="field-label">上游订单号<span class="required">*</span></span
          ><input
            v-model="orderForm.externalOrderNo"
            maxlength="120"
            :required="orderEvidenceRequired(orderForm.status)"
        /></label>
        <label
          v-if="orderEvidenceRequired(orderForm.status) || orderForm.submissionEvidenceReference"
          class="full"
          ><span class="field-label"
            >提交或回执证据<span v-if="orderEvidenceRequired(orderForm.status)" class="required"
              >*</span
            ></span
          ><input
            v-model="orderForm.submissionEvidenceReference"
            maxlength="500"
            :required="orderEvidenceRequired(orderForm.status)"
            placeholder="受控工单、邮件、回执或验收记录编号"
          /><small>只填证据编号或受控存储位置，不填写令牌或内部密钥。</small></label
        >
        <label class="full"
          >处理记录<textarea v-model="orderForm.note" rows="3" maxlength="2000" />
        </label>
        <label v-if="orderForm.status === 'EXCEPTION'" class="full"
          ><span class="field-label">异常原因<span class="required">*</span></span
          ><textarea v-model="orderForm.exceptionReason" required rows="3" maxlength="1000" />
        </label>
      </div>
      <section class="supplier-order-history" aria-label="供应商订单状态记录">
        <div class="section-heading">
          <div>
            <span class="eyebrow">状态记录</span>
            <h3>履约轨迹</h3>
          </div>
          <span v-if="orderHistoryLoading" class="history-loading">读取中</span>
        </div>
        <p v-if="orderHistoryError" class="form-error">{{ orderHistoryError }}</p>
        <p v-else-if="!orderHistoryLoading && orderHistory.length === 0" class="history-empty">
          暂无状态记录。
        </p>
        <ol v-else class="history-list">
          <li v-for="item in orderHistory" :key="item.historyNo">
            <div>
              <strong>{{ orderStatusLabel(item.currentStatus) }}</strong>
              <span v-if="item.previousStatus">
                由 {{ orderStatusLabel(item.previousStatus) }} 更新
              </span>
              <span v-else>订单创建</span>
            </div>
            <small
              >{{ formatDateTime(item.createdAt) }} · {{ item.changedByName || '系统记录' }}</small
            >
            <p v-if="item.note">{{ item.note }}</p>
          </li>
        </ol>
      </section>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
      <div class="form-actions">
        <button class="button secondary" type="button" @click="orderTarget = null">取消</button>
        <button class="button primary" type="submit" :disabled="saving">
          <CircleDollarSign :size="16" />{{ saving ? '保存中' : '确认更新' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.subnav-tabs {
  display: flex;
  gap: 8px;
  margin: 0 0 18px;
}

.subnav-tabs button {
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
}

.subnav-tabs button.active {
  border-color: #1f5db6;
  color: #174d9b;
  background: #eef5ff;
}

.supplier-toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) repeat(2, minmax(150px, 190px)) auto auto;
  gap: 10px;
  padding: 16px;
  margin-bottom: 16px;
}

.mapping-editor {
  display: grid;
  grid-template-columns: minmax(220px, 0.7fr) minmax(440px, 1.3fr);
  gap: 32px;
  padding: 24px;
  margin-bottom: 16px;
}

.mapping-editor h2 {
  margin: 7px 0 10px;
}

.mapping-editor p {
  color: var(--muted);
  line-height: 1.7;
}

.channel-search-label > span {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.supplier-order-metrics {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.reset-hint {
  margin-top: -4px;
  color: #8a5a00;
}

.supplier-order-history {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
}

.section-heading {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
}

.section-heading h3 {
  margin: 4px 0 0;
  font-size: 16px;
}

.history-loading,
.history-empty,
.history-list small {
  color: var(--muted);
  font-size: 13px;
}

.history-empty {
  margin: 12px 0 0;
}

.history-list {
  display: grid;
  gap: 10px;
  padding: 0;
  margin: 14px 0 0;
  list-style: none;
}

.history-list li {
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #f8fafc;
}

.history-list li > div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
}

.history-list li > div > span {
  color: var(--muted);
  font-size: 13px;
}

.history-list p {
  margin: 7px 0 0;
  color: var(--text);
  line-height: 1.6;
  white-space: pre-wrap;
}

@media (max-width: 980px) {
  .supplier-toolbar,
  .mapping-editor {
    grid-template-columns: 1fr 1fr;
  }

  .supplier-toolbar .input-icon,
  .mapping-editor > div {
    grid-column: 1 / -1;
  }

  .supplier-order-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .subnav-tabs {
    overflow-x: auto;
  }

  .subnav-tabs button,
  .supplier-toolbar,
  .mapping-editor {
    min-width: max-content;
  }

  .supplier-toolbar,
  .mapping-editor {
    grid-template-columns: 1fr;
  }

  .supplier-toolbar {
    min-width: 0;
  }

  .mapping-editor {
    min-width: 0;
  }
}
</style>
