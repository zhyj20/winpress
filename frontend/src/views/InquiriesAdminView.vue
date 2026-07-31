<script setup lang="ts">
import { CheckCircle2, MessageSquareText, PhoneCall, Search } from 'lucide-vue-next'
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useToastStore } from '@/stores/toast'
import type { ApiResponse, BusinessInquiry, PageResult } from '@/types'

const toast = useToastStore()
const route = useRoute()
const items = ref<BusinessInquiry[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const savingId = ref<number | null>(null)
const filters = reactive({ status: '', type: '' })
const notes = reactive<Record<number, string>>({})
const inquiryStatuses = ['NEW', 'CONTACTED', 'CLOSED']
const inquiryTypes = [
  'API_INTEGRATION',
  'GENERAL_COOPERATION',
  'SERVICE_CONSULTATION',
  'MEDIA_PARTNERSHIP',
]

function routeText(value: unknown) {
  if (Array.isArray(value)) return value[0] || ''
  return typeof value === 'string' ? value : ''
}

function applyRouteFilters() {
  const requestedStatus = routeText(route.query.status)
  const requestedType = routeText(route.query.type)
  const requestedPage = Number(routeText(route.query.page))
  filters.status = inquiryStatuses.includes(requestedStatus) ? requestedStatus : ''
  filters.type = inquiryTypes.includes(requestedType) ? requestedType : ''
  page.value = Number.isSafeInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1
}

function typeLabel(value: string) {
  return (
    {
      API_INTEGRATION: 'API 接入',
      GENERAL_COOPERATION: '商务合作',
      SERVICE_CONSULTATION: '服务咨询',
      MEDIA_PARTNERSHIP: '媒体合作申请',
    }[value] || value
  )
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<BusinessInquiry>>>('/admin/inquiries', {
      params: { ...filters, page: page.value, pageSize: 20 },
    })
    items.value = data.data.items
    total.value = data.data.total
    for (const item of items.value) notes[item.id] = item.handlingNote || ''
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

async function update(item: BusinessInquiry, status: 'CONTACTED' | 'CLOSED') {
  savingId.value = item.id
  actionError.value = ''
  try {
    await http.patch(`/admin/inquiries/${item.id}`, {
      status,
      handlingNote: notes[item.id] || null,
    })
    toast.show(status === 'CONTACTED' ? '已记录联系情况' : '咨询已关闭', 'success')
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    savingId.value = null
  }
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
  <PageHeader
    eyebrow="平台运营"
    title="商务咨询"
    description="处理 API 接入、媒体合作、商务合作与服务咨询；联系人信息仅限负责人员查看。"
  />

  <section class="panel inquiry-filters">
    <span class="filter-title"><Search :size="17" />咨询筛选</span>
    <select v-model="filters.type" aria-label="咨询类型">
      <option value="">全部类型</option>
      <option value="API_INTEGRATION">API 接入</option>
      <option value="GENERAL_COOPERATION">商务合作</option>
      <option value="SERVICE_CONSULTATION">服务咨询</option>
      <option value="MEDIA_PARTNERSHIP">媒体合作申请</option>
    </select>
    <select v-model="filters.status" aria-label="咨询状态">
      <option value="">全部状态</option>
      <option value="NEW">待联系</option>
      <option value="CONTACTED">已联系</option>
      <option value="CLOSED">已关闭</option>
    </select>
    <button class="button secondary" type="button" @click="applyFilters">筛选</button>
  </section>

  <section class="panel inquiry-list-panel">
    <DataState
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-title="暂无咨询记录"
      @retry="load"
    >
      <template #content>
        <div class="inquiry-list">
          <article v-for="item in items" :key="item.id">
            <header>
              <div>
                <span>{{ typeLabel(item.inquiryType) }}</span>
                <h2>{{ item.companyName }}</h2>
                <small
                  >{{ item.inquiryNo }} ·
                  {{ new Date(item.createdAt).toLocaleString('zh-CN') }}</small
                >
              </div>
              <StatusTag :status="item.status" />
            </header>
            <div class="inquiry-contact">
              <strong>{{ item.contactName }}</strong>
              <a :href="`tel:${item.mobile}`"><PhoneCall :size="15" />{{ item.mobile }}</a>
              <a v-if="item.email" :href="`mailto:${item.email}`">{{ item.email }}</a>
            </div>
            <p>{{ item.message }}</p>
            <label
              >处理记录<textarea
                v-model="notes[item.id]"
                rows="2"
                maxlength="2000"
                placeholder="例如：已电话沟通，等待接口清单"
              />
            </label>
            <footer>
              <span v-if="item.handlerName"
                >最近处理：{{ item.handlerName
                }}<template v-if="item.handledAt">
                  · {{ new Date(item.handledAt).toLocaleString('zh-CN') }}</template
                ></span
              >
              <span v-else>尚未分配处理人</span>
              <div>
                <button
                  class="button secondary"
                  type="button"
                  :disabled="savingId === item.id"
                  @click="update(item, 'CONTACTED')"
                >
                  <MessageSquareText :size="16" />记录已联系
                </button>
                <button
                  class="button ghost"
                  type="button"
                  :disabled="savingId === item.id"
                  @click="update(item, 'CLOSED')"
                >
                  <CheckCircle2 :size="16" />关闭
                </button>
              </div>
            </footer>
          </article>
        </div>
        <p v-if="actionError" class="form-error">{{ actionError }}</p>
        <PaginationBar :page="page" :page-size="20" :total="total" @change="changePage" />
      </template>
    </DataState>
  </section>
</template>

<style scoped>
.inquiry-filters {
  display: grid;
  grid-template-columns: 1fr minmax(160px, 220px) minmax(160px, 220px) auto;
  gap: 10px;
  padding: 16px;
  margin-bottom: 16px;
}

.inquiry-filters .filter-title {
  color: var(--muted);
  font-weight: 750;
}

.inquiry-list-panel {
  padding: 0;
}

.inquiry-list {
  display: grid;
}

.inquiry-list article {
  display: grid;
  gap: 14px;
  padding: 24px;
  border-bottom: 1px solid var(--border);
}

.inquiry-list article > header,
.inquiry-list article > footer,
.inquiry-contact {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.inquiry-list h2 {
  margin: 5px 0;
  font-size: 20px;
}

.inquiry-list header span,
.inquiry-list small,
.inquiry-list footer > span {
  color: var(--muted);
  font-size: 12px;
}

.inquiry-contact {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.inquiry-contact a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #1e5aaa;
}

.inquiry-list p {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.inquiry-list footer > div {
  display: flex;
  gap: 8px;
}

@media (max-width: 760px) {
  .inquiry-filters {
    grid-template-columns: 1fr;
  }

  .inquiry-list article > header,
  .inquiry-list article > footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
