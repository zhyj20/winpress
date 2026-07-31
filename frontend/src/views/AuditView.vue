<script setup lang="ts">
import { ShieldCheck } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import PageHeader from '@/components/PageHeader.vue'
import type { ApiResponse } from '@/types'

interface Log {
  logNo: string
  actorName?: string
  actorRole: string
  action: string
  targetType: string
  targetId: string
  detail: Record<string, unknown>
  createdAt: string
}
const items = ref<Log[]>([])
const loading = ref(true)
const error = ref('')
const page = ref(1)
const pageSize = 20
const pagedItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize),
)
const actionLabels: Record<string, string> = {
  CREATE_REQUIREMENT: '创建需求',
  SUBMIT_MANUSCRIPT: '提交稿件',
  APPROVE_MANUSCRIPT: '确认稿件',
  RETURN_MANUSCRIPT: '退回稿件',
  CREATE_PUBLISH_TASK: '创建发布任务',
  UPDATE_PUBLISH_TASK: '更新任务',
  CONFIRM_PUBLISH_PLAN: '确认发布计划',
  CREATE_PUBLISH_PLAN: '创建发布计划',
  COPY_APPROVED_MANUSCRIPT_TO_DIRECT_PROJECT: '复制已确认稿件',
  SUBMIT_PUBLISH_RESULT: '提交成果',
  ACCEPT_PUBLISH_RESULT: '验收成果',
  ASSIGN_PROJECT: '分配项目',
  CREATE_CHANNEL: '创建渠道',
  UPDATE_SETTLEMENT: '更新结算',
  UPLOAD_FILE: '上传文件',
  CREATE_NEWS_CONFERENCE: '创建新闻发布会',
  ADD_CONFERENCE_MEDIA_CANDIDATE: '补充拟邀名单',
  UPDATE_BUSINESS_INQUIRY: '更新商务咨询',
  UPDATE_CONFERENCE_WORK_ITEM: '更新发布会事项',
  UPDATE_SUPPLIER_ORDER: '更新供应商订单',
  UPDATE_NEWS_CONFERENCE: '更新发布会资料',
  SELECT_CONFERENCE_MEDIA: '保存媒体名单',
  FOLLOW_MEDIA_INVITATION: '更新媒体沟通',
  COMPLETE_PUBLISH_TASK: '完成发布任务',
  CREATE_CASE_DATA: '导入案例数据',
  VERIFY_RESULT: '核验发布成果',
}
const targetLabels: Record<string, string> = {
  REQUIREMENT: '服务需求',
  PUBLISH_PLAN: '发布计划',
  PUBLISH_TASK: '发布任务',
  MANUSCRIPT: '稿件',
  PROJECT: '项目',
  CONFERENCE_PROJECT: '新闻发布会',
  CONFERENCE_MEDIA_CANDIDATE: '拟邀名单',
  BUSINESS_INQUIRY: '商务咨询',
  CONFERENCE_WORK_ITEM: '发布会事项',
  SUPPLIER_ORDER: '供应商订单',
}

function actionLabel(action: string) {
  return actionLabels[action] || '系统操作'
}

function targetLabel(targetType: string) {
  return targetLabels[targetType] || '业务对象'
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get<ApiResponse<Log[]>>('/admin/operation-logs')
    items.value = data.data
    const lastPage = Math.max(1, Math.ceil(items.value.length / pageSize))
    page.value = Math.min(page.value, lastPage)
  } catch (e) {
    error.value = apiError(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <PageHeader
    eyebrow="安全审计"
    title="操作日志"
    description="记录关键业务操作、执行人和目标对象，便于追溯项目变更。"
  />
  <section class="panel table-panel">
    <DataState
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-title="暂无操作记录"
      @retry="load"
      ><template #content
        ><div class="audit-list">
          <article v-for="item in pagedItems" :key="item.logNo">
            <span class="audit-icon"><ShieldCheck :size="18" /></span>
            <div>
              <strong>{{ actionLabel(item.action) }}</strong>
              <p>
                {{ item.actorName || '系统' }} · {{ targetLabel(item.targetType) }}
                {{ item.targetId }}
              </p>
              <small>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</small>
            </div>
            <code>{{ item.logNo }}</code>
          </article>
        </div>
        <PaginationBar
          v-if="items.length > pageSize"
          :page="page"
          :page-size="pageSize"
          :total="items.length"
          @change="page = $event" /></template
    ></DataState>
  </section>
</template>
