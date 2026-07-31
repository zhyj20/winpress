<script setup lang="ts">
import { ArrowLeft, Building2, Mail, Phone, UserRound, UserRoundPlus } from 'lucide-vue-next'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiError } from '@/api/http'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import '@/assets/marketing.css'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const error = ref('')
const form = reactive({
  username: '',
  organizationName: '',
  displayName: '',
  mobile: '',
  email: '',
  password: '',
  confirmPassword: '',
})

async function submit() {
  error.value = ''
  if (form.password !== form.confirmPassword) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await auth.register({
      username: form.username,
      organizationName: form.organizationName,
      displayName: form.displayName,
      mobile: form.mobile,
      email: form.email,
      password: form.password,
    })
    router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard')
  } catch (e) {
    error.value = apiError(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <SiteHeader />
  <main class="register-page">
    <form class="register-form" @submit.prevent="submit">
      <RouterLink
        class="back-link"
        :to="{
          path: '/login',
          query: route.query.redirect ? { redirect: route.query.redirect } : {},
        }"
        ><ArrowLeft :size="17" />返回登录</RouterLink
      >
      <img src="/winpress-logo.png" alt="WinPress 云发布" />
      <div>
        <span class="eyebrow">企业账号</span>
        <h1>创建云发布账号</h1>
        <p>账号用于提交需求、确认稿件和查看项目进度，请填写负责本项目的联系人信息。</p>
      </div>
      <div class="form-grid two-columns">
        <label
          >用户名<span class="required">*</span>
          <div class="input-icon">
            <UserRound :size="18" /><input
              v-model="form.username"
              required
              maxlength="80"
              autocomplete="username"
              placeholder="用于登录"
            /></div
        ></label>
        <label
          >单位<span class="required">*</span>
          <div class="input-icon">
            <Building2 :size="18" /><input
              v-model="form.organizationName"
              required
              maxlength="160"
              placeholder="公司、机构或项目单位"
            /></div
        ></label>
        <label
          >联系人<span class="required">*</span>
          <div class="input-icon">
            <UserRound :size="18" /><input
              v-model="form.displayName"
              required
              maxlength="80"
              placeholder="真实姓名"
            /></div
        ></label>
        <label
          >手机号<span class="required">*</span>
          <div class="input-icon">
            <Phone :size="18" /><input
              v-model="form.mobile"
              required
              pattern="1[3-9]\d{9}"
              inputmode="tel"
              placeholder="11 位手机号"
            /></div
        ></label>
        <label
          >邮箱<span class="required">*</span>
          <div class="input-icon">
            <Mail :size="18" /><input
              v-model="form.email"
              type="email"
              required
              placeholder="用于接收确认材料"
            /></div
        ></label>
        <label
          >密码<span class="required">*</span
          ><input
            v-model="form.password"
            type="password"
            required
            minlength="8"
            maxlength="64"
            autocomplete="new-password"
            placeholder="至少 8 位"
        /></label>
        <label
          >确认密码<span class="required">*</span
          ><input
            v-model="form.confirmPassword"
            type="password"
            required
            minlength="8"
            maxlength="64"
            autocomplete="new-password"
            placeholder="再次输入密码"
        /></label>
      </div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="button primary wide" type="submit" :disabled="loading">
        <UserRoundPlus :size="18" />{{ loading ? '正在创建账号' : '完成注册' }}
      </button>
    </form>
  </main>
</template>
