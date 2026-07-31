<script setup lang="ts">
import { ExternalLink, Pencil, Plus, Search, X } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useToastStore } from '@/stores/toast'
import type { ApiResponse, Channel, PageResult } from '@/types'

type AdminChannel = Channel

const toast = useToastStore()
const items = ref<AdminChannel[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const showForm = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const keyword = ref('')
const type = ref('')
const status = ref('ACTIVE')

const defaults = () => ({
  channelName: '',
  channelType: 'DIRECT_PUBLISHING',
  category: '',
  region: '全国',
  publishForm: '网站图文',
  expectedDays: 2,
  linkSupport: true,
  publicNotes: '',
  customerPrice: undefined as number | undefined,
  validUntil: '',
  status: 'ACTIVE',
})
const form = reactive(defaults())

function localDateTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

function openCreate() {
  editingId.value = null
  Object.assign(form, defaults())
  actionError.value = ''
  showForm.value = true
}

function openEdit(item: AdminChannel) {
  editingId.value = item.id
  Object.assign(form, {
    channelName: item.channelName,
    channelType: item.channelType,
    category: item.category || '',
    region: item.region || '',
    publishForm: item.publishForm || '',
    expectedDays: item.expectedDays || 2,
    linkSupport: item.linkSupport,
    publicNotes: item.publicNotes || '',
    customerPrice: item.customerPrice == null ? undefined : Number(item.customerPrice),
    validUntil: localDateTime(item.validUntil),
    status: item.status,
  })
  actionError.value = ''
  showForm.value = true
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<AdminChannel>>>('/admin/channels', {
      params: {
        type: type.value,
        status: status.value,
        keyword: keyword.value,
        page: page.value,
        pageSize: 15,
      },
    })
    items.value = data.data.items
    total.value = data.data.total
  } catch (e) {
    error.value = apiError(e)
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

async function save() {
  submitting.value = true
  actionError.value = ''
  const payload = {
    ...form,
    validUntil: form.channelType === 'DIRECT_PUBLISHING' ? form.validUntil || null : null,
    customerPrice:
      !editingId.value && form.channelType === 'DIRECT_PUBLISHING' ? form.customerPrice : null,
    costPrice: null,
  }
  try {
    if (editingId.value) await http.put(`/admin/channels/${editingId.value}`, payload)
    else await http.post('/admin/channels', payload)
    toast.show(
      editingId.value ? '渠道资料已更新；报价请在“报价与比价”中调整' : '渠道已创建',
      'success',
    )
    showForm.value = false
    await load()
  } catch (e) {
    actionError.value = apiError(e)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <PageHeader
    eyebrow="平台运营"
    title="渠道管理"
    description="维护发布渠道资料、状态和基础报价。批量调价与渠道比价请在“定价与比价”处理。"
  >
    <RouterLink class="button secondary" to="/admin/pricing">
      报价与比价 <ExternalLink :size="16" />
    </RouterLink>
    <button class="button primary" type="button" @click="openCreate">
      <Plus :size="17" />新增渠道
    </button>
  </PageHeader>

  <section class="panel filter-bar">
    <div class="input-icon search-input">
      <Search :size="17" /><input
        v-model="keyword"
        aria-label="搜索渠道资料"
        placeholder="搜索渠道名称或编号"
        @keyup.enter="applyFilters"
      />
    </div>
    <select v-model="type" aria-label="按渠道类型筛选" @change="applyFilters">
      <option value="">全部渠道</option>
      <option value="MEDIA_PR">媒体邀请</option>
      <option value="DIRECT_PUBLISHING">直编发稿</option>
    </select>
    <select v-model="status" aria-label="按渠道状态筛选" @change="applyFilters">
      <option value="">全部状态</option>
      <option value="ACTIVE">可用</option>
      <option value="REVIEW_REQUIRED">待复核</option>
      <option value="INACTIVE">停用</option>
    </select>
    <button class="button secondary" type="button" @click="applyFilters">查询</button>
  </section>

  <section class="panel table-panel">
    <DataState
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-title="暂无渠道"
      @retry="load"
    >
      <template #content>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>渠道</th>
                <th>类型</th>
                <th>分类 / 地区</th>
                <th>时效</th>
                <th>客户服务价</th>
                <th>有效期</th>
                <th>状态</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td>
                  <strong>{{ item.channelName }}</strong
                  ><small>{{ item.channelNo }}</small>
                </td>
                <td>
                  {{
                    item.channelType === 'MEDIA_PR'
                      ? '媒体邀请'
                      : item.channelType === 'DIRECT_PUBLISHING'
                        ? '直编发稿'
                        : '历史渠道记录'
                  }}
                </td>
                <td>{{ item.category || '未分类' }} / {{ item.region || '未设置' }}</td>
                <td>{{ item.expectedDays ? `${item.expectedDays} 天` : '另行确认' }}</td>
                <td>
                  {{
                    item.customerPrice == null
                      ? '不按单项报价'
                      : `¥${Number(item.customerPrice).toLocaleString('zh-CN')}`
                  }}
                </td>
                <td>
                  {{
                    item.validUntil ? new Date(item.validUntil).toLocaleDateString('zh-CN') : '—'
                  }}
                </td>
                <td><StatusTag :status="item.status" /></td>
                <td>
                  <span v-if="item.channelType === 'LEGACY_OWNED_CHANNEL'" class="muted">
                    仅供查看
                  </span>
                  <button
                    v-else
                    class="icon-button"
                    type="button"
                    title="调整渠道与报价"
                    aria-label="调整渠道与报价"
                    @click="openEdit(item)"
                  >
                    <Pencil :size="17" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar :page="page" :page-size="15" :total="total" @change="changePage" />
      </template>
    </DataState>
  </section>

  <div v-if="showForm" class="modal-backdrop" @click.self="showForm = false">
    <form class="modal-panel form-modal" @submit.prevent="save">
      <header>
        <div>
          <span class="eyebrow">渠道资料</span>
          <h2>{{ editingId ? '调整渠道与报价' : '新增发布渠道' }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="showForm = false">
          <X :size="19" />
        </button>
      </header>
      <div class="form-grid two-columns">
        <label
          >渠道名称<span class="required">*</span
          ><input v-model="form.channelName" required maxlength="180"
        /></label>
        <label
          >渠道类型<span class="required">*</span
          ><select v-model="form.channelType">
            <option value="MEDIA_PR">媒体邀请</option>
            <option value="DIRECT_PUBLISHING">直编发稿</option>
          </select></label
        >
        <label>分类<input v-model="form.category" maxlength="80" /></label>
        <label>地区<input v-model="form.region" maxlength="80" /></label>
        <label>发布形式<input v-model="form.publishForm" maxlength="120" /></label>
        <label>预计工作日<input v-model.number="form.expectedDays" type="number" min="1" /></label>
        <template v-if="form.channelType === 'DIRECT_PUBLISHING' && !editingId">
          <label
            >客户服务价<span class="required">*</span
            ><input
              v-model.number="form.customerPrice"
              required
              type="number"
              min="0.01"
              step="0.01"
          /></label>
          <label
            >报价有效期<span class="required">*</span
            ><input v-model="form.validUntil" required type="datetime-local"
          /></label>
        </template>
        <label v-if="editingId"
          >可用状态<span class="required">*</span
          ><select v-model="form.status">
            <option value="ACTIVE">可用</option>
            <option value="REVIEW_REQUIRED">待复核</option>
            <option value="INACTIVE">停用</option>
          </select></label
        >
        <label class="full"
          >对客说明<textarea v-model="form.publicNotes" rows="3" maxlength="1000"></textarea>
        </label>
        <label class="full inline-check"
          ><input v-model="form.linkSupport" type="checkbox" />发布结果可回填链接</label
        >
      </div>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>
      <div class="form-actions">
        <button class="button secondary" type="button" @click="showForm = false">取消</button
        ><button class="button primary" type="submit" :disabled="submitting">
          {{ submitting ? '正在保存' : editingId ? '保存调整' : '保存渠道' }}
        </button>
      </div>
    </form>
  </div>
</template>
