<script setup lang="ts">
import { Check, MapPin, PenTool, Send, X } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import type { ApiResponse, WriterProfile, WritingAssignment } from '@/types'

const auth = useAuthStore()
const toast = useToastStore()
const items = ref<WritingAssignment[]>([])
const writers = ref<WriterProfile[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const submitting = ref(false)
const selectedWriter = reactive<Record<number, number>>({})
const distanceKm = reactive<Record<number, number | undefined>>({})
const declineTarget = ref<WritingAssignment | null>(null)
const declineNote = ref('')
const page = ref(1)
const pageSize = 10
const pagedItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize),
)

const isAdmin = computed(() => auth.user?.role === 'PLATFORM_ADMIN')
const isWriter = computed(() => auth.user?.role === 'PUBLISH_OPERATOR')
const pageMeta = computed(() =>
  isAdmin.value
    ? {
        eyebrow: '云采写',
        title: '采写派单',
        description: '按服务地点和时段补齐每个写手名额；同一写手的已确认档期不会重复安排。',
      }
    : {
        eyebrow: '云采写',
        title: '云采写任务',
        description: '查看发给你的现场采写任务；确认接单后，按项目时间完成采集、撰写与修改。',
      },
)
const emptyState = computed(() =>
  isAdmin.value
    ? {
        text: '暂无待匹配的采写任务。新建云采写项目后，可在这里安排写手。',
        to: '/projects',
        action: '查看项目',
      }
    : {
        text: '暂无分配给你的采写任务。新的派单会在这里显示。',
        to: '/work-items',
        action: '查看当前待办',
      },
)

function selectedWriterProfile(item: WritingAssignment) {
  const writerProfileId = selectedWriter[item.id]
  return writerProfileId ? writers.value.find((writer) => writer.id === writerProfileId) : undefined
}

function selectedWriterRadiusKm(item: WritingAssignment) {
  const radius = selectedWriterProfile(item)?.serviceRadiusKm
  return typeof radius === 'number' && Number.isFinite(radius) ? radius : null
}

function requiresManualDistance(item: WritingAssignment) {
  return selectedWriterRadiusKm(item) !== null
}

function money(value: number) {
  return `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [assignmentResponse, writerResponse] = await Promise.all([
      http.get<ApiResponse<WritingAssignment[]>>('/writing-assignments'),
      isAdmin.value
        ? http.get<ApiResponse<WriterProfile[]>>('/admin/writers')
        : Promise.resolve(null),
    ])
    items.value = assignmentResponse.data.data
    const lastPage = Math.max(1, Math.ceil(items.value.length / pageSize))
    page.value = Math.min(page.value, lastPage)
    writers.value = writerResponse?.data.data || []
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    loading.value = false
  }
}

async function offer(item: WritingAssignment) {
  const writerProfileId = selectedWriter[item.id]
  if (!writerProfileId) {
    actionError.value = '请先选择写手。'
    return
  }
  const radiusKm = selectedWriterRadiusKm(item)
  const enteredDistanceKm = distanceKm[item.id]
  if (
    radiusKm !== null &&
    (typeof enteredDistanceKm !== 'number' || !Number.isFinite(enteredDistanceKm))
  ) {
    actionError.value = '该写手已设置服务半径，请填写经人工核验的服务距离。'
    return
  }
  if (radiusKm !== null && enteredDistanceKm! > radiusKm) {
    actionError.value = `填写的服务距离超出该写手 ${radiusKm} km 的服务半径。`
    return
  }
  submitting.value = true
  actionError.value = ''
  try {
    await http.post(`/admin/writing-assignments/${item.id}/offer`, {
      writerProfileId,
      distanceKm: distanceKm[item.id] ?? null,
    })
    toast.show('派单已发送，等待写手接单。', 'success')
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    submitting.value = false
  }
}

async function respond(item: WritingAssignment, decision: 'ACCEPT' | 'DECLINE', note = '') {
  submitting.value = true
  actionError.value = ''
  try {
    await http.post(`/writing-assignments/${item.id}/respond`, { decision, note: note || null })
    toast.show(
      decision === 'ACCEPT' ? '已接单，请按项目时间推进。' : '已拒单，平台将重新安排。',
      'success',
    )
    declineTarget.value = null
    declineNote.value = ''
    await load()
  } catch (requestError) {
    actionError.value = apiError(requestError)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <PageHeader
    :eyebrow="pageMeta.eyebrow"
    :title="pageMeta.title"
    :description="pageMeta.description"
  />

  <p v-if="actionError" class="form-error assignment-global-error">{{ actionError }}</p>
  <DataState
    :loading="loading"
    :error="error"
    :empty="!items.length"
    empty-title="暂无云采写派单"
    :empty-text="emptyState.text"
    @retry="load"
  >
    <RouterLink class="button secondary" :to="emptyState.to">{{ emptyState.action }}</RouterLink>
    <template #content>
      <section class="assignment-grid">
        <article v-for="item in pagedItems" :key="item.id" class="assignment-card">
          <header>
            <div>
              <small>{{ item.assignmentNo }}</small>
              <h2>{{ item.projectName }}</h2>
            </div>
            <StatusTag :status="item.status" />
          </header>
          <dl>
            <div>
              <dt>服务地点</dt>
              <dd><MapPin :size="15" />{{ item.serviceLocation || '远程交付' }}</dd>
            </div>
            <div>
              <dt>服务配置</dt>
              <dd>{{ item.writerCount }} 人 × {{ item.serviceDays }} 天</dd>
            </div>
            <div>
              <dt>写手安排</dt>
              <dd>
                已确认 {{ item.acceptedWriterCount }}/{{ item.writerCount }} 人
                <template v-if="item.offeredWriterCount">
                  · 待接单 {{ item.offeredWriterCount }} 人</template
                >
                <template v-if="item.openWriterSlots">
                  · 待派 {{ item.openWriterSlots }} 人</template
                >
              </dd>
            </div>
            <div>
              <dt>服务单价</dt>
              <dd>{{ money(item.unitPrice) }} / 人 / 天</dd>
            </div>
            <div>
              <dt>预计金额</dt>
              <dd>{{ money(item.estimatedAmount) }}</dd>
            </div>
            <div v-if="item.writerNames">
              <dt>已安排写手</dt>
              <dd>{{ item.writerNames }}</dd>
            </div>
            <div v-if="isWriter && item.memberDistanceKm != null">
              <dt>参考距离</dt>
              <dd>{{ item.memberDistanceKm }} km</dd>
            </div>
          </dl>

          <div v-if="isAdmin && item.openWriterSlots > 0" class="assignment-offer">
            <label>
              选择写手
              <select v-model.number="selectedWriter[item.id]">
                <option :value="0">请选择</option>
                <option
                  v-for="writer in writers"
                  :key="writer.id"
                  :value="writer.id"
                  :disabled="writer.availabilityStatus !== 'AVAILABLE'"
                >
                  {{ writer.displayName }} · {{ writer.city || '城市未设置' }} ·
                  {{ writer.availabilityStatus === 'AVAILABLE' ? '可接单' : '不可接单' }}
                  <template v-if="writer.serviceRadiusKm != null">
                    · 服务半径 {{ writer.serviceRadiusKm }} km
                  </template>
                </option>
              </select>
            </label>
            <label>
              参考距离（km）<span v-if="requiresManualDistance(item)" class="required">*</span>
              <input
                v-model.number="distanceKm[item.id]"
                type="number"
                min="0"
                :max="selectedWriterRadiusKm(item) ?? undefined"
                step="0.1"
                :required="requiresManualDistance(item)"
              />
            </label>
            <button
              class="button primary"
              type="button"
              :disabled="submitting"
              @click="offer(item)"
            >
              <Send :size="16" />{{
                item.acceptedWriterCount || item.offeredWriterCount ? '补充派单' : '发送派单'
              }}
            </button>
            <small class="assignment-hint">
              <template v-if="requiresManualDistance(item)">
                该写手服务半径为
                {{ selectedWriterRadiusKm(item) }}
                km；请填写经人工核验的服务距离，超过半径不能派单。
              </template>
              <template v-else>
                发送前会校验写手总体可用状态与已确认档期；服务距离不会由系统自动推算。
              </template>
            </small>
          </div>

          <div v-if="isWriter && item.memberStatus === 'OFFERED'" class="assignment-actions">
            <button
              class="button primary"
              type="button"
              :disabled="submitting"
              @click="respond(item, 'ACCEPT')"
            >
              <Check :size="16" />接单
            </button>
            <button
              class="button secondary"
              type="button"
              :disabled="submitting"
              @click="declineTarget = item"
            >
              拒单
            </button>
          </div>
          <RouterLink
            class="text-button assignment-project-link"
            :to="`/projects/${item.projectId}`"
          >
            <PenTool :size="15" />查看项目
          </RouterLink>
        </article>
      </section>
      <PaginationBar
        v-if="items.length > pageSize"
        :page="page"
        :page-size="pageSize"
        :total="items.length"
        @change="page = $event"
      />
    </template>
  </DataState>

  <div v-if="declineTarget" class="modal-backdrop" @click.self="declineTarget = null">
    <form
      class="modal-panel compact-form"
      @submit.prevent="respond(declineTarget, 'DECLINE', declineNote)"
    >
      <header>
        <div>
          <span class="eyebrow">拒绝派单</span>
          <h2>{{ declineTarget.projectName }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="declineTarget = null">
          <X :size="19" />
        </button>
      </header>
      <label
        >拒单原因<span class="required">*</span
        ><textarea v-model="declineNote" required rows="4" maxlength="1000" />
      </label>
      <div class="form-actions">
        <button class="button secondary" type="button" @click="declineTarget = null">取消</button>
        <button class="button primary" type="submit" :disabled="submitting">确认拒单</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.assignment-global-error {
  margin-bottom: 16px;
}
.assignment-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}
.assignment-card {
  padding: 24px;
  border: 1px solid #e1e6ed;
  border-radius: 12px;
  background: #fff;
}
.assignment-card > header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 18px;
}
.assignment-card h2 {
  margin: 5px 0 0;
  color: #172033;
  font-size: 19px;
}
.assignment-card header small {
  color: #7b8797;
  font-size: 11px;
}
.assignment-card dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 18px 0;
  margin: 18px 0;
  border-top: 1px solid #e7ebf0;
  border-bottom: 1px solid #e7ebf0;
}
.assignment-card dt {
  color: #7b8797;
  font-size: 11px;
}
.assignment-card dd {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 5px 0 0;
  color: #273449;
  font-size: 13px;
  font-weight: 700;
}
.assignment-offer {
  display: grid;
  grid-template-columns: 1fr 150px auto;
  gap: 12px;
  align-items: end;
}
.assignment-offer label {
  color: #536175;
  font-size: 12px;
}
.assignment-offer select,
.assignment-offer input {
  width: 100%;
  margin-top: 6px;
}
.assignment-hint {
  grid-column: 1 / -1;
  color: #718096;
  font-size: 12px;
  line-height: 1.5;
}
.assignment-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.assignment-project-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
@media (max-width: 900px) {
  .assignment-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 620px) {
  .assignment-card dl,
  .assignment-offer {
    grid-template-columns: 1fr;
  }
}
</style>
