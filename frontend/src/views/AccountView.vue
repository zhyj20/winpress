<script setup lang="ts">
import { Building2, Mail, Phone, ShieldCheck, UserRound } from 'lucide-vue-next'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const roleLabel = { CUSTOMER: '客户', PUBLISH_OPERATOR: '服务运营', PLATFORM_ADMIN: '平台运营' }
</script>

<template>
  <PageHeader
    eyebrow="账号与权限"
    title="账号信息"
    description="账号资料用于项目沟通、材料确认和权限识别。"
  />
  <section class="account-grid">
    <article class="panel profile-card">
      <span class="profile-avatar">{{ auth.user?.displayName.slice(0, 1) }}</span>
      <h2>{{ auth.user?.displayName }}</h2>
      <p>{{ roleLabel[auth.user?.role || 'CUSTOMER'] }}</p>
      <span class="status-tag status-success">账号正常</span>
    </article>
    <article class="panel detail-list">
      <div>
        <span><Building2 :size="18" />单位</span><strong>{{ auth.user?.organizationName }}</strong>
      </div>
      <div>
        <span><UserRound :size="18" />登录账号</span><strong>{{ auth.user?.username }}</strong>
      </div>
      <div>
        <span><Phone :size="18" />手机号</span><strong>{{ auth.user?.mobile }}</strong>
      </div>
      <div>
        <span><Mail :size="18" />邮箱</span><strong>{{ auth.user?.email }}</strong>
      </div>
      <div>
        <span><ShieldCheck :size="18" />权限</span
        ><strong>{{ auth.user?.permissions.length }} 项</strong>
      </div>
    </article>
  </section>
</template>
