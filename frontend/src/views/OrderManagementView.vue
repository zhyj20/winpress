<script setup lang="ts">
import { Archive, ArrowDownUp, ExternalLink, SlidersHorizontal } from 'lucide-vue-next'
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import type {
  ApiResponse,
  OrderRecord,
  PageResult,
  SettlementRecord,
  SettlementTransactionRecord,
  SettlementTransactionType,
} from '@/types'

type ServiceType = OrderRecord['serviceType']

const route = useRoute()
const serviceType = ref<ServiceType | ''>('')
const orderStatus = ref('')
const page = ref(1)
const pageSize = 15
const loading = ref(true)
const error = ref('')
const records = ref<OrderRecord[]>([])
const total = ref(0)
const settlementStatus = ref<SettlementRecord['status'] | ''>('')
const settlementRecords = ref<SettlementRecord[]>([])
const settlementTotal = ref(0)
const settlementPage = ref(1)
const settlementPageSize = 10
const loadingSettlements = ref(true)
const settlementError = ref('')
const archivedSettlementRecords = ref<SettlementRecord[]>([])
const archivedSettlementTotal = ref(0)
const archivedSettlementPage = ref(1)
const archivedSettlementPageSize = 10
const loadingArchivedSettlements = ref(true)
const archivedSettlementError = ref('')
const transactionType = ref<SettlementTransactionType | ''>('')
const transactionStatus = ref<SettlementTransactionRecord['status'] | ''>('')
const transactionRecords = ref<SettlementTransactionRecord[]>([])
const transactionTotal = ref(0)
const transactionPage = ref(1)
const transactionPageSize = 10
const loadingTransactions = ref(true)
const transactionError = ref('')
const archivedTransactionRecords = ref<SettlementTransactionRecord[]>([])
const archivedTransactionTotal = ref(0)
const archivedTransactionPage = ref(1)
const archivedTransactionPageSize = 10
const loadingArchivedTransactions = ref(true)
const archivedTransactionError = ref('')

const serviceTypes: { value: ServiceType; label: string }[] = [
  { value: 'ONSITE_WRITING', label: '云采写' },
  { value: 'MEDIA_PR', label: '媒体邀请' },
  { value: 'DIRECT_PUBLISHING', label: '直编发稿' },
  { value: 'NEWS_CONFERENCE', label: '举办新闻发布会' },
]

const settlementStatuses: { value: SettlementRecord['status']; label: string }[] = [
  { value: 'PENDING', label: '待处理' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'PAID', label: '已结清' },
  { value: 'CANCELLED', label: '已取消' },
]

const transactionTypes: { value: SettlementTransactionType; label: string }[] = [
  { value: 'PAYMENT', label: '收款' },
  { value: 'REFUND', label: '退款' },
  { value: 'CREDIT_ADJUSTMENT', label: '贷项调整' },
  { value: 'DEBIT_ADJUSTMENT', label: '借项调整' },
  { value: 'WRITE_OFF', label: '核销' },
]

function routeText(value: unknown) {
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('zh-CN')
}

function money(value?: number, currency = 'CNY', emptyText = '待确认') {
  if (value == null) return emptyText
  const prefix = currency === 'CNY' ? '¥' : `${currency} `
  return `${prefix}${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}

function paidMoney(value?: number, currency = 'CNY') {
  if (value == null || Number(value) <= 0) return '—'
  return money(value, currency)
}

function adjustmentMoney(value?: number, currency = 'CNY') {
  if (value == null || Number(value) === 0) return '—'
  const amount = Number(value)
  const sign = amount > 0 ? '+' : ''
  return `${sign}${money(amount, currency)}`
}

function applyRouteFilters() {
  const requestedType = routeText(route.query.serviceType) || routeText(route.query.type)
  serviceType.value = serviceTypes.some((item) => item.value === requestedType)
    ? (requestedType as ServiceType)
    : ''
  orderStatus.value = routeText(route.query.status)
  page.value = Number(route.query.page || 1) || 1
}

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<OrderRecord>>>('/order-records', {
      params: {
        serviceType: serviceType.value || undefined,
        status: orderStatus.value || undefined,
        page: page.value,
        pageSize,
      },
    })
    records.value = data.data.items
    total.value = data.data.total
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    loading.value = false
  }
}

async function loadSettlements() {
  loadingSettlements.value = true
  settlementError.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<SettlementRecord>>>(
      '/settlement-records',
      {
        params: {
          status: settlementStatus.value || undefined,
          page: settlementPage.value,
          pageSize: settlementPageSize,
        },
      },
    )
    settlementRecords.value = data.data.items
    settlementTotal.value = data.data.total
  } catch (requestError) {
    settlementError.value = apiError(requestError)
  } finally {
    loadingSettlements.value = false
  }
}

async function loadArchivedSettlements() {
  loadingArchivedSettlements.value = true
  archivedSettlementError.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<SettlementRecord>>>(
      '/settlement-archive-records',
      {
        params: {
          status: settlementStatus.value || undefined,
          page: archivedSettlementPage.value,
          pageSize: archivedSettlementPageSize,
        },
      },
    )
    archivedSettlementRecords.value = data.data.items
    archivedSettlementTotal.value = data.data.total
  } catch (requestError) {
    archivedSettlementError.value = apiError(requestError)
  } finally {
    loadingArchivedSettlements.value = false
  }
}

async function loadTransactions() {
  loadingTransactions.value = true
  transactionError.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<SettlementTransactionRecord>>>(
      '/transaction-records',
      {
        params: {
          transactionType: transactionType.value || undefined,
          status: transactionStatus.value || undefined,
          page: transactionPage.value,
          pageSize: transactionPageSize,
        },
      },
    )
    transactionRecords.value = data.data.items
    transactionTotal.value = data.data.total
  } catch (requestError) {
    transactionError.value = apiError(requestError)
  } finally {
    loadingTransactions.value = false
  }
}

async function loadArchivedTransactions() {
  loadingArchivedTransactions.value = true
  archivedTransactionError.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<SettlementTransactionRecord>>>(
      '/transaction-archive-records',
      {
        params: {
          transactionType: transactionType.value || undefined,
          status: transactionStatus.value || undefined,
          page: archivedTransactionPage.value,
          pageSize: archivedTransactionPageSize,
        },
      },
    )
    archivedTransactionRecords.value = data.data.items
    archivedTransactionTotal.value = data.data.total
  } catch (requestError) {
    archivedTransactionError.value = apiError(requestError)
  } finally {
    loadingArchivedTransactions.value = false
  }
}

function applyFilters() {
  page.value = 1
  void loadOrders()
}

function changePage(next: number) {
  page.value = next
  void loadOrders()
}

function applySettlementFilters() {
  settlementPage.value = 1
  archivedSettlementPage.value = 1
  void Promise.all([loadSettlements(), loadArchivedSettlements()])
}

function changeSettlementPage(next: number) {
  settlementPage.value = next
  void loadSettlements()
}

function changeArchivedSettlementPage(next: number) {
  archivedSettlementPage.value = next
  void loadArchivedSettlements()
}

function applyTransactionFilters() {
  transactionPage.value = 1
  archivedTransactionPage.value = 1
  void Promise.all([loadTransactions(), loadArchivedTransactions()])
}

function changeTransactionPage(next: number) {
  transactionPage.value = next
  void loadTransactions()
}

function changeArchivedTransactionPage(next: number) {
  archivedTransactionPage.value = next
  void loadArchivedTransactions()
}

applyRouteFilters()
onMounted(() => {
  void Promise.all([
    loadOrders(),
    loadTransactions(),
    loadArchivedTransactions(),
    loadSettlements(),
    loadArchivedSettlements(),
  ])
})

watch(
  () => route.fullPath,
  () => {
    applyRouteFilters()
    void loadOrders()
  },
)
</script>

<template>
  <PageHeader
    eyebrow="交易与结算"
    title="订单管理"
    description="每次服务下单保留一条稳定订单记录，执行任务和资金记录分别追踪。"
  />

  <section class="panel filter-bar">
    <span class="filter-title"><SlidersHorizontal :size="17" />筛选订单</span>
    <select v-model="serviceType" aria-label="按服务类别筛选" @change="applyFilters">
      <option value="">全部服务</option>
      <option v-for="item in serviceTypes" :key="item.value" :value="item.value">
        {{ item.label }}
      </option>
    </select>
    <select v-model="orderStatus" aria-label="按订单状态筛选" @change="applyFilters">
      <option value="">全部状态</option>
      <option value="SUBMITTED">已提交</option>
      <option value="PENDING_SCOPE">待确认范围</option>
      <option value="PENDING_ACCEPTANCE">待项目确认</option>
      <option value="WAITING_CONFIRMATION">待客户确认</option>
      <option value="CONFIRMED">已确认</option>
      <option value="PLANNING">筹备中</option>
      <option value="EXECUTING">发布会执行中</option>
      <option value="WAITING_MATCH">待匹配写手</option>
      <option value="OFFERED">已派单</option>
      <option value="PARTIALLY_ACCEPTED">部分写手已接单</option>
      <option value="ACCEPTED">已接单</option>
      <option value="PENDING_ASSIGNMENT">待平台安排</option>
      <option value="PENDING_EXECUTION">待执行</option>
      <option value="IN_PROGRESS">服务执行中</option>
      <option value="NEEDS_INFO">需补充</option>
      <option value="EXCEPTION">异常</option>
      <option value="COMPLETED">已完成</option>
      <option value="CLIENT_ACCEPTED">客户已验收</option>
      <option value="NOT_PROCEEDING">暂不推进</option>
      <option value="DECLINED">未承接</option>
      <option value="CANCELLED">已取消</option>
    </select>
    <button class="button secondary" type="button" @click="applyFilters">查询</button>
  </section>

  <DataState
    :loading="loading"
    :error="error"
    :empty="!records.length"
    empty-title="暂无匹配订单"
    empty-text="可调整筛选条件，或先提交服务需求后重新查看。"
    @retry="loadOrders"
  >
    <template #content>
      <section class="panel table-panel">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">订单台账</span>
            <h2>服务订单</h2>
            <p class="form-hint">
              直编发稿显示当前计划价；具体渠道、价格和排期以项目核验结果为准。
            </p>
          </div>
        </div>
        <div class="table-wrap">
          <table class="order-record-table">
            <thead>
              <tr>
                <th>订单编号</th>
                <th>服务类别</th>
                <th>项目</th>
                <th>服务内容</th>
                <th>计划价 / 金额</th>
                <th>状态</th>
                <th>更新时间</th>
                <th />
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in records" :key="item.recordNo">
                <td data-label="订单编号">
                  <strong>{{ item.recordNo }}</strong>
                </td>
                <td data-label="服务类别">{{ item.serviceLabel }}</td>
                <td data-label="项目">
                  <RouterLink :to="`/projects/${item.projectId}`">{{
                    item.projectName
                  }}</RouterLink>
                  <small>{{ item.projectNo }}</small>
                </td>
                <td data-label="服务内容">{{ item.itemDetail || '—' }}</td>
                <td data-label="计划价 / 金额">
                  <strong>{{ money(item.amount, item.currency, '待项目核验') }}</strong>
                  <small
                    v-if="
                      item.serviceType === 'DIRECT_PUBLISHING' &&
                      item.amount != null &&
                      !['COMPLETED', 'CLIENT_ACCEPTED', 'NOT_PROCEEDING', 'CANCELLED'].includes(
                        item.status,
                      )
                    "
                  >
                    待项目核验
                  </small>
                </td>
                <td data-label="状态"><StatusTag :status="item.status" /></td>
                <td data-label="更新时间">{{ formatDate(item.updatedAt) }}</td>
                <td class="order-record-action">
                  <RouterLink
                    class="icon-button"
                    :to="`/projects/${item.projectId}`"
                    aria-label="查看项目"
                  >
                    <span class="mobile-action-label">查看项目</span>
                    <ExternalLink :size="17" />
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

  <section class="panel table-panel">
    <div class="panel-heading">
      <div>
        <span class="eyebrow">资金与调整</span>
        <h2>交易记录</h2>
        <p class="form-hint">仅显示当前四项服务中已登记凭据的收款、退款、账务调整与核销记录。</p>
      </div>
      <div class="inline-actions">
        <select
          v-model="transactionType"
          aria-label="按交易类型筛选"
          @change="applyTransactionFilters"
        >
          <option value="">全部类型</option>
          <option v-for="item in transactionTypes" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <select
          v-model="transactionStatus"
          aria-label="按交易状态筛选"
          @change="applyTransactionFilters"
        >
          <option value="">全部状态</option>
          <option value="CONFIRMED">有效</option>
          <option value="VOIDED">已作废</option>
        </select>
        <button class="button secondary" type="button" @click="applyTransactionFilters">
          查询
        </button>
      </div>
    </div>
    <div v-if="loadingTransactions" class="order-hub-empty">正在加载交易记录...</div>
    <p v-else-if="transactionError" class="form-error">{{ transactionError }}</p>
    <div v-else-if="transactionRecords.length" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>交易编号</th>
            <th>类型</th>
            <th>项目</th>
            <th>金额</th>
            <th>发生时间</th>
            <th>凭据 / 说明</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in transactionRecords" :key="item.transactionNo">
            <td>
              <strong>{{ item.transactionNo }}</strong>
              <small>{{ item.settlementNo }}</small>
            </td>
            <td>{{ item.transactionLabel }}</td>
            <td>
              <RouterLink :to="`/projects/${item.projectId}`">{{ item.projectName }}</RouterLink>
              <small>{{ item.serviceLabel }} · {{ item.projectNo }}</small>
            </td>
            <td>
              <strong>{{ money(item.amount, item.currency) }}</strong>
            </td>
            <td>{{ formatDate(item.occurredAt) }}</td>
            <td>
              {{ item.referenceNo || item.customerNote || '—' }}
              <small v-if="item.referenceNo && item.customerNote">{{ item.customerNote }}</small>
            </td>
            <td><StatusTag :status="item.status" /></td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="muted">暂无匹配的交易凭据。</p>
    <PaginationBar
      v-if="!loadingTransactions && transactionTotal > transactionPageSize"
      :page="transactionPage"
      :page-size="transactionPageSize"
      :total="transactionTotal"
      @change="changeTransactionPage"
    />
  </section>

  <section class="panel table-panel">
    <div class="panel-heading">
      <div>
        <span class="eyebrow">结算状态</span>
        <h2>结算记录</h2>
      </div>
      <div class="inline-actions">
        <select
          v-model="settlementStatus"
          aria-label="按结算状态筛选"
          @change="applySettlementFilters"
        >
          <option value="">全部状态</option>
          <option v-for="item in settlementStatuses" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <button class="button secondary" type="button" @click="applySettlementFilters">查询</button>
      </div>
    </div>
    <div v-if="loadingSettlements" class="order-hub-empty">正在加载账务记录...</div>
    <p v-else-if="settlementError" class="form-error">{{ settlementError }}</p>
    <div v-else-if="settlementRecords.length" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>结算编号</th>
            <th>项目</th>
            <th>应结金额</th>
            <th>账务调整</th>
            <th>已付金额</th>
            <th>待结金额</th>
            <th>到期时间</th>
            <th>最近收款</th>
            <th>发票信息</th>
            <th>状态</th>
            <th>更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in settlementRecords" :key="item.settlementNo">
            <td>
              <strong>{{ item.settlementNo }}</strong>
            </td>
            <td>
              <RouterLink :to="`/projects/${item.projectId}`">{{ item.projectName }}</RouterLink>
              <small>{{ item.serviceLabel }} · {{ item.projectNo }}</small>
            </td>
            <td>
              <strong>{{ money(item.amount, item.currency) }}</strong>
            </td>
            <td>{{ adjustmentMoney(item.adjustmentAmount, item.currency) }}</td>
            <td>{{ paidMoney(item.paidAmount, item.currency) }}</td>
            <td>
              <strong>{{ money(item.outstandingAmount, item.currency, '—') }}</strong>
            </td>
            <td>{{ formatDate(item.dueAt) }}</td>
            <td>{{ formatDate(item.paidAt) }}</td>
            <td>{{ item.invoiceNo || '—' }}</td>
            <td><StatusTag :status="item.status" /></td>
            <td>{{ formatDate(item.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="muted">暂无结算记录。项目范围或价格尚待确认时，不会在此生成结算信息。</p>
    <PaginationBar
      v-if="!loadingSettlements && settlementTotal > settlementPageSize"
      :page="settlementPage"
      :page-size="settlementPageSize"
      :total="settlementTotal"
      @change="changeSettlementPage"
    />
  </section>

  <section class="panel table-panel archive-panel">
    <div class="panel-heading">
      <div>
        <span class="eyebrow">历史记录</span>
        <h2>历史结算归档</h2>
        <p class="form-hint">
          旧版组合服务记录仅供查阅，不计入当前四项服务的待结金额，也不能在本系统继续调账。
        </p>
      </div>
      <Archive :size="21" aria-hidden="true" />
    </div>
    <div v-if="loadingArchivedSettlements" class="order-hub-empty">正在加载历史记录...</div>
    <p v-else-if="archivedSettlementError" class="form-error">
      {{ archivedSettlementError }}
    </p>
    <div v-else-if="archivedSettlementRecords.length" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>原结算编号</th>
            <th>项目</th>
            <th>原记录金额</th>
            <th>原记录已付</th>
            <th>原到期时间</th>
            <th>原记录状态</th>
            <th>归档说明</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in archivedSettlementRecords" :key="item.settlementNo">
            <td>
              <strong>{{ item.settlementNo }}</strong>
            </td>
            <td>
              <RouterLink :to="`/projects/${item.projectId}`">{{ item.projectName }}</RouterLink>
              <small>{{ item.serviceLabel }} · {{ item.projectNo }}</small>
            </td>
            <td>{{ money(item.amount, item.currency) }}</td>
            <td>{{ paidMoney(item.paidAmount, item.currency) }}</td>
            <td>{{ formatDate(item.dueAt) }}</td>
            <td><StatusTag :status="item.status" /></td>
            <td><span class="archive-badge">只读归档</span></td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="muted">暂无历史组合服务结算记录。</p>
    <PaginationBar
      v-if="!loadingArchivedSettlements && archivedSettlementTotal > archivedSettlementPageSize"
      :page="archivedSettlementPage"
      :page-size="archivedSettlementPageSize"
      :total="archivedSettlementTotal"
      @change="changeArchivedSettlementPage"
    />

    <div class="archive-segment">
      <div class="archive-segment-heading">
        <div>
          <h3>历史交易凭据</h3>
          <p>旧版组合服务原有的收款或调整记录只在此查阅，不计入当前四项服务的交易台账。</p>
        </div>
      </div>
      <div v-if="loadingArchivedTransactions" class="order-hub-empty">正在加载历史交易记录...</div>
      <p v-else-if="archivedTransactionError" class="form-error">
        {{ archivedTransactionError }}
      </p>
      <div v-else-if="archivedTransactionRecords.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>原交易编号</th>
              <th>类型</th>
              <th>项目</th>
              <th>原记录金额</th>
              <th>发生时间</th>
              <th>原凭据 / 说明</th>
              <th>原记录状态</th>
              <th>归档说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in archivedTransactionRecords" :key="item.transactionNo">
              <td>
                <strong>{{ item.transactionNo }}</strong>
                <small>{{ item.settlementNo }}</small>
              </td>
              <td>{{ item.transactionLabel }}</td>
              <td>
                <RouterLink :to="`/projects/${item.projectId}`">{{ item.projectName }}</RouterLink>
                <small>{{ item.serviceLabel }} · {{ item.projectNo }}</small>
              </td>
              <td>
                <strong>{{ money(item.amount, item.currency) }}</strong>
              </td>
              <td>{{ formatDate(item.occurredAt) }}</td>
              <td>
                {{ item.referenceNo || item.customerNote || '—' }}
                <small v-if="item.referenceNo && item.customerNote">{{ item.customerNote }}</small>
              </td>
              <td><StatusTag :status="item.status" /></td>
              <td><span class="archive-badge">只读归档</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="muted">暂无历史组合服务交易记录。</p>
      <PaginationBar
        v-if="
          !loadingArchivedTransactions && archivedTransactionTotal > archivedTransactionPageSize
        "
        :page="archivedTransactionPage"
        :page-size="archivedTransactionPageSize"
        :total="archivedTransactionTotal"
        @change="changeArchivedTransactionPage"
      />
    </div>
  </section>

  <section class="note-banner">
    <ArrowDownUp :size="18" />
    <p>
      交易记录与结算单相互独立：收款、退款或调整先登记凭据，再据此更新实收和待结金额；作废记录保留在台账中，但不再参与金额计算。
    </p>
  </section>
</template>

<style scoped>
.table-panel > .panel-heading {
  padding: 20px 16px 14px;
}

.table-panel > .muted,
.table-panel > .form-error,
.table-panel > .order-hub-empty {
  margin: 0;
  padding: 18px 16px 22px;
}

.inline-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.inline-actions select {
  min-width: 132px;
}

.archive-panel > .panel-heading {
  align-items: flex-start;
}

.archive-panel > .panel-heading > svg {
  flex: 0 0 auto;
  color: #667085;
}

.archive-segment {
  margin: 22px 16px 0;
  padding-top: 20px;
  border-top: 1px solid #e4e7ec;
}

.archive-segment-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.archive-segment-heading h3 {
  margin: 0;
  font-size: 16px;
  color: #344054;
}

.archive-segment-heading p {
  max-width: 720px;
  margin: 5px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.65;
}

.archive-segment > .muted,
.archive-segment > .form-error,
.archive-segment > .order-hub-empty {
  padding: 14px 0 20px;
}

.archive-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 3px 9px;
  border: 1px solid #d0d5dd;
  border-radius: 999px;
  background: #f2f4f7;
  color: #475467;
  font-size: 12px;
  font-weight: 650;
}

.order-record-table {
  min-width: 1020px;
  table-layout: fixed;
}

.order-record-table th:nth-child(1) {
  width: 192px;
}

.order-record-table th:nth-child(2) {
  width: 98px;
}

.order-record-table th:nth-child(3) {
  width: 210px;
}

.order-record-table th:nth-child(5) {
  width: 118px;
}

.order-record-table th:nth-child(6) {
  width: 96px;
}

.order-record-table th:nth-child(7) {
  width: 148px;
}

.order-record-table th:nth-child(8) {
  width: 52px;
}

.order-record-table td:nth-child(1),
.order-record-table td:nth-child(2),
.order-record-table td:nth-child(3),
.order-record-table td:nth-child(4) {
  line-height: 1.45;
  overflow-wrap: anywhere;
  white-space: normal;
}

.order-record-table td:nth-child(1) strong {
  font-size: 11px;
  white-space: nowrap;
}

.mobile-action-label {
  display: none;
}

@media (max-width: 640px) {
  .archive-panel > .panel-heading > svg {
    display: none;
  }

  .inline-actions {
    width: 100%;
    align-items: stretch;
  }

  .inline-actions select,
  .inline-actions .button {
    width: 100%;
  }

  .table-wrap:has(.order-record-table) {
    overflow: visible;
  }

  .order-record-table,
  .order-record-table tbody {
    display: block;
    width: 100%;
    min-width: 0;
  }

  .order-record-table thead {
    display: none;
  }

  .order-record-table tbody {
    padding: 0 14px 14px;
  }

  .order-record-table tr {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 14px 12px;
    margin-top: 12px;
    padding: 16px;
    border: 1px solid #e4e7ec;
    border-radius: 12px;
    background: #fff;
  }

  .order-record-table td,
  .order-record-table td:nth-child(1),
  .order-record-table td:nth-child(2),
  .order-record-table td:nth-child(3),
  .order-record-table td:nth-child(4) {
    display: flex;
    min-width: 0;
    padding: 0;
    border: 0;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    white-space: normal;
  }

  .order-record-table td::before {
    content: attr(data-label);
    color: #667085;
    font-size: 11px;
    font-weight: 650;
    line-height: 1.35;
  }

  .order-record-table td:nth-child(1),
  .order-record-table td:nth-child(3),
  .order-record-table td:nth-child(4),
  .order-record-action {
    grid-column: 1 / -1;
  }

  .order-record-table td:nth-child(1) strong {
    font-size: 12px;
    white-space: normal;
  }

  .order-record-action::before {
    display: none;
  }

  .order-record-action .icon-button {
    width: 100%;
    min-height: 42px;
    justify-content: center;
    gap: 7px;
    border: 1px solid #d0d5dd;
    border-radius: 9px;
  }

  .mobile-action-label {
    display: inline;
    font-size: 13px;
    font-weight: 650;
  }
}
</style>
