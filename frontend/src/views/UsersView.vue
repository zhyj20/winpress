<script setup lang="ts">
import { Save, UsersRound } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import http, { apiError } from '@/api/http'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import type { ApiResponse, PageResult, Role } from '@/types'

interface UserRow {
  id: number
  userNo: string
  username: string
  displayName: string
  mobile: string
  email: string
  organizationName: string
  role: Role
  status: string
  lastLoginAt?: string
  createdAt: string
}
const auth = useAuthStore()
const toast = useToastStore()
const items = ref<UserRow[]>([])
const total = ref(0)
const page = ref(1)
const role = ref('')
const status = ref('')
const loading = ref(true)
const error = ref('')
const saving = ref<number | null>(null)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get<ApiResponse<PageResult<UserRow>>>('/admin/users', {
      params: { role: role.value, status: status.value, page: page.value, pageSize: 20 },
    })
    items.value = data.data.items
    total.value = data.data.total
  } catch (e) {
    error.value = apiError(e)
  } finally {
    loading.value = false
  }
}
async function save(item: UserRow) {
  saving.value = item.id
  try {
    await http.patch(`/admin/users/${item.id}`, { role: item.role, status: item.status })
    toast.show('账号权限已更新，原登录会话已失效', 'success')
  } catch (e) {
    toast.show(apiError(e), 'error')
    await load()
  } finally {
    saving.value = null
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

onMounted(load)
</script>

<template>
  <PageHeader
    eyebrow="平台运营"
    title="账号与权限"
    description="管理客户、服务运营和平台运营账号。角色或状态变更后，原登录会话立即失效。"
  />
  <section class="panel filter-bar">
    <UsersRound :size="19" /><select
      v-model="role"
      aria-label="按账号角色筛选"
      @change="applyFilters"
    >
      <option value="">全部角色</option>
      <option value="CUSTOMER">客户</option>
      <option value="PUBLISH_OPERATOR">服务运营</option>
      <option value="PLATFORM_ADMIN">平台运营</option></select
    ><select v-model="status" aria-label="按账号状态筛选" @change="applyFilters">
      <option value="">全部状态</option>
      <option value="ACTIVE">正常</option>
      <option value="SUSPENDED">停用</option>
    </select>
  </section>
  <section class="panel table-panel">
    <DataState
      :loading="loading"
      :error="error"
      :empty="!items.length"
      empty-title="暂无账号"
      @retry="load"
      ><template #content
        ><div class="table-wrap">
          <table class="user-management-table">
            <thead>
              <tr>
                <th>用户</th>
                <th>单位</th>
                <th>联系方式</th>
                <th>角色</th>
                <th>状态</th>
                <th>最近登录</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td>
                  <strong>{{ item.displayName }}</strong
                  ><small>{{ item.username }}</small>
                </td>
                <td>{{ item.organizationName }}</td>
                <td>
                  {{ item.mobile }}<small>{{ item.email }}</small>
                </td>
                <td>
                  <select
                    v-model="item.role"
                    class="table-select"
                    :aria-label="`${item.displayName} 的账号角色`"
                    :disabled="item.id === auth.user?.id"
                  >
                    <option value="CUSTOMER">客户</option>
                    <option value="PUBLISH_OPERATOR">服务运营</option>
                    <option value="PLATFORM_ADMIN">平台运营</option>
                  </select>
                </td>
                <td>
                  <select
                    v-model="item.status"
                    class="table-select"
                    :aria-label="`${item.displayName} 的账号状态`"
                    :disabled="item.id === auth.user?.id"
                  >
                    <option value="ACTIVE">正常</option>
                    <option value="SUSPENDED">停用</option>
                  </select>
                </td>
                <td>
                  {{
                    item.lastLoginAt
                      ? new Date(item.lastLoginAt).toLocaleString('zh-CN')
                      : '尚未登录'
                  }}
                </td>
                <td>
                  <button
                    class="icon-button"
                    type="button"
                    title="保存权限"
                    :disabled="item.id === auth.user?.id || saving === item.id"
                    @click="save(item)"
                  >
                    <Save :size="17" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar :page="page" :page-size="20" :total="total" @change="changePage" /></template
    ></DataState>
  </section>
</template>

<style scoped>
.user-management-table {
  min-width: 940px;
  table-layout: fixed;
}
.user-management-table th:nth-child(1),
.user-management-table td:nth-child(1) {
  width: 18%;
}
.user-management-table th:nth-child(2),
.user-management-table td:nth-child(2) {
  width: 14%;
}
.user-management-table th:nth-child(3),
.user-management-table td:nth-child(3) {
  width: 18%;
}
.user-management-table th:nth-child(4),
.user-management-table td:nth-child(4) {
  width: 15%;
}
.user-management-table th:nth-child(5),
.user-management-table td:nth-child(5) {
  width: 12%;
}
.user-management-table th:nth-child(6),
.user-management-table td:nth-child(6) {
  width: 17%;
}
.user-management-table th:nth-child(7),
.user-management-table td:nth-child(7) {
  width: 52px;
}
.user-management-table small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-management-table td:nth-child(6) {
  color: #536175;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.user-management-table .table-select {
  min-width: 0;
  width: 100%;
}
</style>
