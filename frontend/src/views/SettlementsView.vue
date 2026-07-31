<script setup lang="ts">
import { Ban, CircleDollarSign, ReceiptText, Save, X } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useToastStore } from '@/stores/toast'
import type { ApiResponse, PageResult, SettlementTransactionType } from '@/types'

type SettlementStatus = 'PENDING' | 'CONFIRMED' | 'PAID' | 'CANCELLED'

interface Settlement {
  id: number
  settlementNo: string
  projectNo: string
  projectName: string
  organizationName: string
  serviceType: string
  serviceLabel: string
  archiveOnly: boolean
  amount: number
  paidAmount: number
  adjustmentAmount: number
  outstandingAmount: number
  currency: string
  dueAt?: string
  paidAt?: string
  invoiceNo?: string
  status: SettlementStatus
}

interface AdminSettlementTransaction {
  id: number
  transactionNo: string
  settlementId: number
  settlementNo: string
  projectNo: string
  projectName: string
  organizationName: string
  transactionType: SettlementTransactionType
  transactionLabel: string
  amount: number
  currency: string
  occurredAt: string
  referenceNo?: string
  customerNote?: string
  internalNote?: string
  status: 'CONFIRMED' | 'VOIDED'
  createdByName: string
  voidedByName?: string
  voidedAt?: string
  voidReason?: string
  createdAt: string
  updatedAt: string
}

const transactionTypes: {
  value: SettlementTransactionType
  label: string
  help: string
}[] = [
  { value: 'PAYMENT', label: '收款', help: '增加实收金额' },
  { value: 'REFUND', label: '退款', help: '冲减实收金额' },
  { value: 'CREDIT_ADJUSTMENT', label: '贷项调整', help: '减少客户应结金额' },
  { value: 'DEBIT_ADJUSTMENT', label: '借项调整', help: '增加客户应结金额' },
  { value: 'WRITE_OFF', label: '核销', help: '核销无需收取的余额' },
]

const toast = useToastStore()
const items = ref<Settlement[]>([])
const loading = ref(true)
const error = ref('')
const status = ref<SettlementStatus | ''>('')
const saving = ref<number | null>(null)
const selectedSettlement = ref<Settlement | null>(null)
const transactions = ref<AdminSettlementTransaction[]>([])
const loadingTransactions = ref(false)
const transactionError = ref('')
const submittingTransaction = ref(false)
const voidTarget = ref<AdminSettlementTransaction | null>(null)
const voidReason = ref('')
const voiding = ref(false)
const SETTLEMENT_TRANSACTION_SUBMISSION_STATE_KEY = 'winpress:settlement-transaction-submission:v1'
let transactionSubmissionFallback: { fingerprint: string; key: string } | null = null

const transactionForm = reactive<{
  transactionType: SettlementTransactionType
  amount: number | null
  occurredAt: string
  referenceNo: string
  customerNote: string
  internalNote: string
}>({
  transactionType: 'PAYMENT',
  amount: null,
  occurredAt: localDateTime(),
  referenceNo: '',
  customerNote: '',
  internalNote: '',
})

const availableTransactionTypes = computed(() =>
  selectedSettlement.value?.status === 'PAID'
    ? transactionTypes.filter((item) => item.value === 'REFUND')
    : transactionTypes,
)

const canCreateTransaction = computed(
  () =>
    !selectedSettlement.value?.archiveOnly &&
    ['CONFIRMED', 'PAID'].includes(selectedSettlement.value?.status || ''),
)

function localDateTime(value = new Date()) {
  const pad = (number: number) => String(number).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(
    value.getHours(),
  )}:${pad(value.getMinutes())}`
}

function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('zh-CN')
}

function money(value?: number, currency = 'CNY') {
  if (value == null) return '—'
  const prefix = currency === 'CNY' ? '¥' : `${currency} `
  return `${prefix}${Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function adjustmentMoney(value?: number, currency = 'CNY') {
  if (value == null || Number(value) === 0) return '—'
  return `${Number(value) > 0 ? '+' : ''}${money(value, currency)}`
}

function resetTransactionForm() {
  transactionForm.transactionType =
    selectedSettlement.value?.status === 'PAID' ? 'REFUND' : 'PAYMENT'
  transactionForm.amount = null
  transactionForm.occurredAt = localDateTime()
  transactionForm.referenceNo = ''
  transactionForm.customerNote = ''
  transactionForm.internalNote = ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<Settlement>>>('/admin/settlements', {
      params: { status: status.value || undefined, pageSize: 100 },
    })
    items.value = data.data.items
    if (selectedSettlement.value) {
      const refreshed = items.value.find((item) => item.id === selectedSettlement.value?.id)
      if (refreshed) selectedSettlement.value = refreshed
    }
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    loading.value = false
  }
}

async function save(item: Settlement) {
  if (item.archiveOnly) {
    toast.show('历史组合服务结算仅供查阅', 'error')
    return
  }
  saving.value = item.id
  try {
    await http.patch(`/admin/settlements/${item.id}`, {
      status: item.status,
      invoiceNo: item.invoiceNo || null,
    })
    toast.show('结算记录已更新', 'success')
    await load()
  } catch (requestError) {
    toast.show(apiError(requestError), 'error')
  } finally {
    saving.value = null
  }
}

async function loadTransactions() {
  if (!selectedSettlement.value) return
  loadingTransactions.value = true
  transactionError.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<AdminSettlementTransaction>>>(
      '/admin/settlement-transactions',
      {
        params: { settlementId: selectedSettlement.value.id, pageSize: 100 },
      },
    )
    transactions.value = data.data.items
  } catch (requestError) {
    transactionError.value = apiError(requestError)
  } finally {
    loadingTransactions.value = false
  }
}

function openLedger(item: Settlement) {
  selectedSettlement.value = item
  voidTarget.value = null
  voidReason.value = ''
  resetTransactionForm()
  void loadTransactions()
}

function closeLedger() {
  if (submittingTransaction.value || voiding.value) return
  selectedSettlement.value = null
  transactions.value = []
  voidTarget.value = null
  voidReason.value = ''
}

async function settlementTransactionFingerprint(settlementId: number, payload: object) {
  const bytes = new TextEncoder().encode(JSON.stringify({ settlementId, payload }))
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

function newSettlementTransactionIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `wp-trx-${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`
}

async function settlementTransactionIdempotencyKeyFor(settlementId: number, payload: object) {
  const fingerprint = await settlementTransactionFingerprint(settlementId, payload)
  try {
    const stored = JSON.parse(
      sessionStorage.getItem(SETTLEMENT_TRANSACTION_SUBMISSION_STATE_KEY) || 'null',
    ) as { fingerprint?: string; key?: string } | null
    if (stored?.fingerprint === fingerprint && stored.key) return stored.key
  } catch {
    // Restricted browser storage must not make the finance form unusable.
  }
  if (transactionSubmissionFallback?.fingerprint === fingerprint) {
    return transactionSubmissionFallback.key
  }
  const key = newSettlementTransactionIdempotencyKey()
  transactionSubmissionFallback = { fingerprint, key }
  try {
    sessionStorage.setItem(
      SETTLEMENT_TRANSACTION_SUBMISSION_STATE_KEY,
      JSON.stringify({ fingerprint, key }),
    )
  } catch {
    // The in-memory fallback still protects retries in this open page.
  }
  return key
}

function clearSettlementTransactionSubmissionState(key: string) {
  if (transactionSubmissionFallback?.key === key) transactionSubmissionFallback = null
  try {
    const stored = JSON.parse(
      sessionStorage.getItem(SETTLEMENT_TRANSACTION_SUBMISSION_STATE_KEY) || 'null',
    ) as { key?: string } | null
    if (stored?.key === key) {
      sessionStorage.removeItem(SETTLEMENT_TRANSACTION_SUBMISSION_STATE_KEY)
    }
  } catch {
    // Nothing else is required when browser storage is unavailable.
  }
}

async function submitTransaction() {
  if (!selectedSettlement.value || !canCreateTransaction.value) return
  if (!transactionForm.amount || transactionForm.amount <= 0) {
    toast.show('请输入有效的交易金额', 'error')
    return
  }
  if (!transactionForm.referenceNo.trim() && !transactionForm.customerNote.trim()) {
    toast.show('请填写凭据编号或客户可见说明', 'error')
    return
  }
  const occurredAt = new Date(transactionForm.occurredAt)
  if (Number.isNaN(occurredAt.getTime())) {
    toast.show('请选择有效的交易发生时间', 'error')
    return
  }
  submittingTransaction.value = true
  try {
    const payload = {
      transactionType: transactionForm.transactionType,
      amount: transactionForm.amount,
      occurredAt: occurredAt.toISOString(),
      referenceNo: transactionForm.referenceNo.trim() || null,
      customerNote: transactionForm.customerNote.trim() || null,
      internalNote: transactionForm.internalNote.trim() || null,
    }
    const idempotencyKey = await settlementTransactionIdempotencyKeyFor(
      selectedSettlement.value.id,
      payload,
    )
    await http.post(`/admin/settlements/${selectedSettlement.value.id}/transactions`, payload, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
    clearSettlementTransactionSubmissionState(idempotencyKey)
    toast.show('交易凭据已登记', 'success')
    resetTransactionForm()
    await Promise.all([loadTransactions(), load()])
  } catch (requestError) {
    toast.show(apiError(requestError), 'error')
  } finally {
    submittingTransaction.value = false
  }
}

function prepareVoid(item: AdminSettlementTransaction) {
  voidTarget.value = item
  voidReason.value = ''
}

async function confirmVoid() {
  if (!voidTarget.value || !voidReason.value.trim()) {
    toast.show('请填写作废原因', 'error')
    return
  }
  voiding.value = true
  try {
    await http.post(`/admin/settlement-transactions/${voidTarget.value.id}/void`, {
      reason: voidReason.value.trim(),
    })
    toast.show('交易记录已作废，原记录仍保留在台账中', 'success')
    voidTarget.value = null
    voidReason.value = ''
    await Promise.all([loadTransactions(), load()])
  } catch (requestError) {
    toast.show(apiError(requestError), 'error')
  } finally {
    voiding.value = false
  }
}

onMounted(load)
</script>

<template>
  <PageHeader
    eyebrow="平台运营"
    title="结算与交易"
    description="结算状态与交易凭据分开管理，收款、退款和调整均保留完整记录。"
  />

  <section class="panel filter-bar">
    <ReceiptText :size="19" />
    <select v-model="status" aria-label="按结算状态筛选" @change="load">
      <option value="">全部状态</option>
      <option value="PENDING">待确认</option>
      <option value="CONFIRMED">已确认</option>
      <option value="PAID">已结清</option>
      <option value="CANCELLED">已取消</option>
    </select>
  </section>

  <section class="panel table-panel">
    <DataState
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-title="暂无结算记录"
      @retry="load"
    >
      <template #content>
        <div class="table-wrap">
          <table class="settlement-table">
            <thead>
              <tr>
                <th>结算单</th>
                <th>客户 / 项目</th>
                <th>金额</th>
                <th>账务信息</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td>
                  <strong>{{ item.settlementNo }}</strong>
                </td>
                <td>
                  {{ item.organizationName }}
                  <small>{{ item.serviceLabel }} · {{ item.projectName }}</small>
                  <span v-if="item.archiveOnly" class="archive-badge">只读归档</span>
                </td>
                <td>
                  <dl class="settlement-amounts">
                    <div>
                      <dt>应结</dt>
                      <dd>{{ money(item.amount, item.currency) }}</dd>
                    </div>
                    <div>
                      <dt>调整</dt>
                      <dd>{{ adjustmentMoney(item.adjustmentAmount, item.currency) }}</dd>
                    </div>
                    <div>
                      <dt>实收</dt>
                      <dd>{{ money(item.paidAmount, item.currency) }}</dd>
                    </div>
                    <div>
                      <dt>待结</dt>
                      <dd>
                        <strong>{{ money(item.outstandingAmount, item.currency) }}</strong>
                      </dd>
                    </div>
                  </dl>
                </td>
                <td class="billing-cell">
                  <span>
                    到期：{{ item.dueAt ? new Date(item.dueAt).toLocaleDateString('zh-CN') : '—' }}
                  </span>
                  <input
                    v-model="item.invoiceNo"
                    class="table-input"
                    :aria-label="`${item.settlementNo} 发票号`"
                    placeholder="未开票"
                    :disabled="item.archiveOnly"
                  />
                </td>
                <td>
                  <select
                    v-model="item.status"
                    class="table-select"
                    :aria-label="`${item.settlementNo} 结算状态`"
                    :disabled="item.archiveOnly"
                  >
                    <option value="PENDING">待确认</option>
                    <option value="CONFIRMED">已确认</option>
                    <option value="PAID">已结清</option>
                    <option value="CANCELLED">已取消</option>
                  </select>
                  <StatusTag :status="item.status" />
                </td>
                <td>
                  <div class="settlement-actions">
                    <button
                      class="icon-button"
                      type="button"
                      aria-label="保存结算单"
                      title="保存结算单"
                      :disabled="item.archiveOnly || saving === item.id"
                      @click="save(item)"
                    >
                      <Save :size="17" />
                    </button>
                    <button
                      class="icon-button"
                      type="button"
                      :aria-label="item.archiveOnly ? '查看历史交易' : '查看并登记交易'"
                      :title="item.archiveOnly ? '查看历史交易' : '查看并登记交易'"
                      @click="openLedger(item)"
                    >
                      <CircleDollarSign :size="18" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </DataState>
  </section>

  <div v-if="selectedSettlement" class="modal-backdrop" @click.self="closeLedger">
    <section class="modal-panel settlement-ledger-dialog" role="dialog" aria-modal="true">
      <header>
        <div>
          <span class="eyebrow">交易台账</span>
          <h2>{{ selectedSettlement.settlementNo }}</h2>
          <p>{{ selectedSettlement.organizationName }} · {{ selectedSettlement.projectName }}</p>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="closeLedger">
          <X :size="19" />
        </button>
      </header>

      <div class="settlement-summary">
        <div>
          <span>应结</span>
          <strong>{{ money(selectedSettlement.amount, selectedSettlement.currency) }}</strong>
        </div>
        <div>
          <span>调整</span>
          <strong>{{
            adjustmentMoney(selectedSettlement.adjustmentAmount, selectedSettlement.currency)
          }}</strong>
        </div>
        <div>
          <span>实收</span>
          <strong>{{ money(selectedSettlement.paidAmount, selectedSettlement.currency) }}</strong>
        </div>
        <div>
          <span>待结</span>
          <strong>{{
            money(selectedSettlement.outstandingAmount, selectedSettlement.currency)
          }}</strong>
        </div>
      </div>

      <div class="settlement-ledger-grid">
        <form class="transaction-entry" @submit.prevent="submitTransaction">
          <div>
            <span class="eyebrow">登记凭据</span>
            <h3>新增交易</h3>
          </div>

          <p v-if="!canCreateTransaction" class="operation-notice">
            {{
              selectedSettlement.archiveOnly
                ? '历史组合服务结算仅供查阅，不能修改状态、发票或交易记录。'
                : selectedSettlement.status === 'PENDING'
                  ? '请先保存“已确认”状态，再登记交易。'
                  : '已取消的结算单不能登记交易。'
            }}
          </p>

          <fieldset :disabled="!canCreateTransaction || submittingTransaction">
            <div class="form-grid two-columns">
              <label>
                <span class="field-label">交易类型<span class="required">*</span></span>
                <select v-model="transactionForm.transactionType" required>
                  <option
                    v-for="item in availableTransactionTypes"
                    :key="item.value"
                    :value="item.value"
                  >
                    {{ item.label }} · {{ item.help }}
                  </option>
                </select>
              </label>
              <label>
                <span class="field-label">金额<span class="required">*</span></span>
                <input
                  v-model.number="transactionForm.amount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  inputmode="decimal"
                  required
                />
              </label>
              <label class="full">
                <span class="field-label">发生时间<span class="required">*</span></span>
                <input v-model="transactionForm.occurredAt" type="datetime-local" required />
              </label>
              <label class="full">
                凭据编号
                <input
                  v-model="transactionForm.referenceNo"
                  maxlength="120"
                  placeholder="银行回单号、退款单号或调整依据编号"
                />
              </label>
              <label class="full">
                客户可见说明
                <textarea
                  v-model="transactionForm.customerNote"
                  maxlength="500"
                  rows="3"
                  placeholder="凭据编号与本说明至少填写一项"
                />
              </label>
              <label class="full">
                内部备注
                <textarea
                  v-model="transactionForm.internalNote"
                  maxlength="1000"
                  rows="3"
                  placeholder="仅平台管理员可见"
                />
              </label>
            </div>
          </fieldset>

          <button
            class="button primary"
            type="submit"
            :disabled="!canCreateTransaction || submittingTransaction"
          >
            {{ submittingTransaction ? '正在登记...' : '登记交易' }}
          </button>
        </form>

        <section class="transaction-history">
          <div>
            <span class="eyebrow">凭据记录</span>
            <h3>交易明细</h3>
          </div>

          <form v-if="voidTarget" class="void-confirmation" @submit.prevent="confirmVoid">
            <div>
              <strong>作废 {{ voidTarget.transactionNo }}</strong>
              <p>原记录将继续保留，但不再参与金额计算。</p>
            </div>
            <label>
              作废原因<span class="required">*</span>
              <input v-model="voidReason" maxlength="500" required />
            </label>
            <div class="inline-actions">
              <button class="button danger" type="submit" :disabled="voiding">
                {{ voiding ? '正在作废...' : '确认作废' }}
              </button>
              <button
                class="button secondary"
                type="button"
                :disabled="voiding"
                @click="voidTarget = null"
              >
                取消
              </button>
            </div>
          </form>

          <div v-if="loadingTransactions" class="order-hub-empty">正在加载交易记录...</div>
          <p v-else-if="transactionError" class="form-error">{{ transactionError }}</p>
          <div v-else-if="transactions.length" class="transaction-list">
            <article v-for="item in transactions" :key="item.id">
              <div class="transaction-list-heading">
                <div>
                  <strong>{{ item.transactionLabel }}</strong>
                  <span>{{ item.transactionNo }}</span>
                </div>
                <strong>{{ money(item.amount, item.currency) }}</strong>
              </div>
              <dl>
                <div>
                  <dt>发生时间</dt>
                  <dd>{{ formatDate(item.occurredAt) }}</dd>
                </div>
                <div>
                  <dt>凭据编号</dt>
                  <dd>{{ item.referenceNo || '—' }}</dd>
                </div>
                <div>
                  <dt>客户说明</dt>
                  <dd>{{ item.customerNote || '—' }}</dd>
                </div>
                <div>
                  <dt>登记人员</dt>
                  <dd>{{ item.createdByName }}</dd>
                </div>
              </dl>
              <p v-if="item.internalNote" class="internal-note">
                内部备注：{{ item.internalNote }}
              </p>
              <p v-if="item.status === 'VOIDED'" class="void-note">
                已由 {{ item.voidedByName || '管理员' }} 于 {{ formatDate(item.voidedAt) }} 作废：{{
                  item.voidReason
                }}
              </p>
              <footer>
                <StatusTag :status="item.status" />
                <button
                  v-if="item.status === 'CONFIRMED'"
                  class="text-button danger-text"
                  type="button"
                  @click="prepareVoid(item)"
                >
                  <Ban :size="15" />作废
                </button>
              </footer>
            </article>
          </div>
          <p v-else class="muted">暂无交易凭据。</p>
        </section>
      </div>
    </section>
  </div>
</template>

<style scoped>
.settlement-actions {
  display: flex;
  gap: 8px;
}

.settlement-table {
  min-width: 980px;
  table-layout: fixed;
}

.settlement-table th:nth-child(1) {
  width: 16%;
}

.settlement-table th:nth-child(2) {
  width: 24%;
}

.settlement-table th:nth-child(3) {
  width: 21%;
}

.settlement-table th:nth-child(4) {
  width: 17%;
}

.settlement-table th:nth-child(5) {
  width: 13%;
}

.settlement-table th:nth-child(6) {
  width: 9%;
}

.settlement-table td {
  overflow-wrap: anywhere;
}

.settlement-table .table-select {
  width: 100%;
  min-width: 0;
}

.settlement-amounts {
  display: grid;
  grid-template-columns: repeat(2, minmax(88px, 1fr));
  gap: 7px 14px;
  min-width: 200px;
  margin: 0;
}

.settlement-amounts div {
  min-width: 0;
}

.settlement-amounts dt {
  color: #667085;
  font-size: 11px;
}

.settlement-amounts dd {
  margin: 2px 0 0;
  color: #1d2939;
  white-space: nowrap;
}

.billing-cell {
  min-width: 158px;
}

.billing-cell > span {
  display: block;
  margin-bottom: 7px;
  color: #667085;
  font-size: 12px;
}

.billing-cell .table-input {
  width: 100%;
}

.archive-badge {
  display: inline-flex;
  align-items: center;
  width: max-content;
  margin-top: 6px;
  padding: 3px 9px;
  border: 1px solid #d0d5dd;
  border-radius: 999px;
  background: #f2f4f7;
  color: #475467;
  font-size: 12px;
  font-weight: 650;
}

.settlement-ledger-dialog {
  width: min(1120px, 100%);
}

.settlement-ledger-dialog > header p {
  margin: 6px 0 0;
  color: #667085;
}

.settlement-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 22px;
}

.settlement-summary div {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid #e0e6ee;
  border-radius: 8px;
  background: #f8fafc;
}

.settlement-summary span {
  color: #667085;
  font-size: 12px;
}

.settlement-summary strong {
  font-size: 17px;
}

.settlement-ledger-grid {
  display: grid;
  grid-template-columns: minmax(300px, 0.85fr) minmax(420px, 1.15fr);
  gap: 24px;
}

.transaction-entry,
.transaction-history {
  display: grid;
  align-content: start;
  gap: 16px;
}

.transaction-entry h3,
.transaction-history h3 {
  margin: 4px 0 0;
}

.transaction-entry fieldset {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.operation-notice,
.void-confirmation {
  margin: 0;
  padding: 13px 14px;
  border: 1px solid #f0d5a8;
  border-radius: 8px;
  background: #fffaf0;
  color: #7a4b12;
  font-size: 13px;
}

.void-confirmation {
  display: grid;
  gap: 12px;
  border-color: #f0c5c5;
  background: #fff7f7;
  color: #7b2727;
}

.void-confirmation p {
  margin: 4px 0 0;
}

.transaction-list {
  display: grid;
  gap: 12px;
  max-height: 620px;
  overflow: auto;
  padding-right: 3px;
}

.transaction-list article {
  padding: 15px;
  border: 1px solid #e0e6ee;
  border-radius: 8px;
}

.transaction-list-heading,
.transaction-list article footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.transaction-list-heading > div {
  display: grid;
  gap: 3px;
}

.transaction-list-heading span {
  color: #667085;
  font-size: 12px;
}

.transaction-list dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
  margin: 14px 0;
}

.transaction-list dl div {
  min-width: 0;
}

.transaction-list dt {
  color: #667085;
  font-size: 12px;
}

.transaction-list dd {
  margin: 3px 0 0;
  overflow-wrap: anywhere;
  font-size: 13px;
}

.internal-note,
.void-note {
  margin: 0 0 12px;
  padding: 9px 10px;
  border-radius: 6px;
  background: #f4f6f8;
  color: #586474;
  font-size: 12px;
  line-height: 1.6;
}

.void-note {
  background: #fff1f1;
  color: #8a3030;
}

.danger-text {
  color: #b42318;
}

@media (max-width: 900px) {
  .settlement-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .settlement-ledger-grid {
    grid-template-columns: 1fr;
  }

  .transaction-list {
    max-height: none;
  }
}

@media (max-width: 560px) {
  .settlement-summary,
  .transaction-list dl {
    grid-template-columns: 1fr;
  }

  .settlement-actions .icon-button {
    min-width: 44px;
    min-height: 44px;
  }
}
</style>
