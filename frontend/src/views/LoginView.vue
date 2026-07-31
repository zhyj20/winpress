<script setup lang="ts">
import { ArrowLeft, LockKeyhole, LogIn, Mail, ShieldCheck } from 'lucide-vue-next'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiError } from '@/api/http'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import '@/assets/marketing.css'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const isLocalDemo = import.meta.env.VITE_LOCAL_DEMO === 'true'
const localTestAccounts = [
  { role: '客户', username: 'client@demo.cn', note: '下单、审核与验收' },
  { role: '服务运营', username: 'operator@winpress.cn', note: '采写、媒体与发布执行' },
  { role: '平台运营', username: 'admin@winpress.cn', note: '项目、渠道、交易与权限管理' },
]

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard')
  } catch (e) {
    error.value = apiError(e)
  } finally {
    loading.value = false
  }
}

function goHome() {
  const target = '/'
  if (loading.value) return
  router.replace(target).catch(() => {
    window.location.href = target
  })
}

function selectTestAccount(account: (typeof localTestAccounts)[number]) {
  username.value = account.username
  password.value = ''
  error.value = ''
}
</script>

<template>
  <SiteHeader />
  <main class="auth-page">
    <button class="auth-back-button button secondary" type="button" @click="goHome">
      <ArrowLeft :size="16" />
      返回首页
    </button>

    <section class="auth-context">
      <img src="/winpress-logo.png" alt="WinPress Logo" />
      <div>
        <span class="eyebrow">云发布运营中心</span>
        <h1>登录操作台</h1>
      </div>
      <p>登录后按权限处理云采写、媒体邀请、直编发稿和新闻发布会项目。</p>
      <ul>
        <li><ShieldCheck :size="18" />项目进度与待办事项</li>
        <li><ShieldCheck :size="18" />稿件版本与媒体反馈</li>
        <li><ShieldCheck :size="18" />订单记录与核验成果</li>
      </ul>
    </section>

    <section class="auth-panel">
      <form class="auth-form" @submit.prevent="submit">
        <div>
          <span class="eyebrow">企业账号</span>
          <h2>进入云发布</h2>
          <p>使用企业账号登录后，可直接开始提交传播需求并跟进项目执行。</p>
        </div>

        <section v-if="isLocalDemo" class="local-test-accounts" aria-label="本机测试身份">
          <div class="local-test-heading">
            <strong>选择测试身份</strong>
            <span>仅填写账号</span>
          </div>
          <div class="local-test-list">
            <button
              v-for="account in localTestAccounts"
              :key="account.username"
              class="local-test-account"
              type="button"
              :disabled="loading"
              @click="selectTestAccount(account)"
            >
              <span>
                <strong>{{ account.role }}</strong>
                <small>{{ account.username }} · {{ account.note }}</small>
              </span>
              <span class="local-test-enter">填写账号</span>
            </button>
          </div>
          <p>密码请使用本机测试账号文档中的对应密码；是否自动填充由浏览器密码管理设置决定。</p>
        </section>

        <label>
          账号<span class="required">*</span>
          <div class="input-icon">
            <Mail :size="18" />
            <input
              v-model="username"
              type="text"
              autocomplete="username"
              required
              placeholder="请输入注册邮箱"
            />
          </div>
        </label>

        <label>
          密码<span class="required">*</span>
          <div class="input-icon">
            <LockKeyhole :size="18" />
            <input
              v-model="password"
              type="password"
              autocomplete="current-password"
              required
              minlength="8"
              placeholder="请输入密码"
            />
          </div>
        </label>

        <p v-if="error" class="form-error" role="alert">{{ error }}</p>

        <button class="button primary wide" type="submit" :disabled="loading">
          <LogIn :size="18" />{{ loading ? '登录中…' : '登录' }}
        </button>

        <p class="auth-switch">
          还未注册，
          <RouterLink
            :to="{
              path: '/register',
              query: route.query.redirect ? { redirect: route.query.redirect } : {},
            }"
          >
            创建企业账号
          </RouterLink>
        </p>
      </form>
    </section>
  </main>
</template>
