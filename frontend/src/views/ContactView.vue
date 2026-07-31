<script setup lang="ts">
import { CheckCircle2, Send } from 'lucide-vue-next'
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import http, { apiError } from '@/api/http'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import '@/assets/marketing.css'

const route = useRoute()
const submitted = ref(false)
const submitting = ref(false)
const error = ref('')

const requestedType = String(route.query.type || 'SERVICE_CONSULTATION')
const validTypes = [
  'API_INTEGRATION',
  'GENERAL_COOPERATION',
  'SERVICE_CONSULTATION',
  'MEDIA_PARTNERSHIP',
]
const form = reactive({
  inquiryType: validTypes.includes(requestedType) ? requestedType : 'SERVICE_CONSULTATION',
  companyName: '',
  contactName: '',
  mobile: '',
  email: '',
  message: '',
  privacyAccepted: false,
})

const intro = computed(
  () =>
    ({
      API_INTEGRATION: {
        title: '申请 API 接入评估',
        description: '请说明业务系统、预计调用场景与上线计划。技术评估完成前，不会开通生产接口。',
      },
      GENERAL_COOPERATION: {
        title: '洽谈商务合作',
        description: '请说明合作方向、现有资源与希望对接的业务范围。',
      },
      MEDIA_PARTNERSHIP: {
        title: '媒体合作申请',
        description: '请说明媒体类型、覆盖领域和可提供的合作方式。平台审核后再安排对接。',
      },
      SERVICE_CONSULTATION: {
        title: '咨询传播服务',
        description: '请简要说明活动、稿件或传播计划，商务人员会根据情况联系您。',
      },
    })[form.inquiryType],
)

async function submit() {
  submitting.value = true
  error.value = ''
  try {
    await http.post('/public/inquiries', {
      ...form,
      email: form.email || null,
    })
    submitted.value = true
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mkt-page contact-page">
    <SiteHeader />
    <main class="contact-main">
      <section class="mkt-shell contact-layout">
        <div class="contact-copy">
          <span class="mkt-eyebrow">联系云发布</span>
          <h1>{{ intro?.title }}</h1>
          <p>{{ intro?.description }}</p>
          <ul>
            <li><CheckCircle2 :size="17" />常规服务请直接创建需求，便于保留项目记录</li>
            <li><CheckCircle2 :size="17" />API 接入须先完成业务与技术评估</li>
            <li><CheckCircle2 :size="17" />媒体合作申请通过审核后才会进入合作洽谈</li>
            <li><CheckCircle2 :size="17" />媒体采访与报道由媒体自主决定</li>
          </ul>
          <aside>
            <strong>公开联系方式说明</strong>
            <p>公开电话、邮箱与办公地址正在核验，确认后统一公布。您可以先通过本表单提交咨询。</p>
          </aside>
        </div>

        <section class="contact-form-card">
          <div v-if="submitted" class="contact-success">
            <CheckCircle2 :size="44" />
            <h2>提交成功</h2>
            <p>我们已收到您的信息。请勿重复提交相同咨询。</p>
            <RouterLink class="mkt-button mkt-button-primary" to="/">返回首页</RouterLink>
          </div>
          <form v-else @submit.prevent="submit">
            <label
              >咨询类型<span>*</span
              ><select v-model="form.inquiryType" required>
                <option value="SERVICE_CONSULTATION">服务咨询</option>
                <option value="API_INTEGRATION">API 接入</option>
                <option value="GENERAL_COOPERATION">商务合作</option>
                <option value="MEDIA_PARTNERSHIP">媒体合作申请</option>
              </select></label
            >
            <label
              >公司名称<span>*</span
              ><input
                v-model="form.companyName"
                required
                maxlength="160"
                autocomplete="organization"
            /></label>
            <div class="contact-form-row">
              <label
                >联系人<span>*</span
                ><input v-model="form.contactName" required maxlength="80" autocomplete="name"
              /></label>
              <label
                >手机号码<span>*</span
                ><input
                  v-model="form.mobile"
                  required
                  maxlength="11"
                  pattern="1[3-9][0-9]{9}"
                  inputmode="numeric"
                  autocomplete="tel"
              /></label>
            </div>
            <label
              >工作邮箱<input
                v-model="form.email"
                type="email"
                maxlength="160"
                autocomplete="email"
            /></label>
            <label
              >咨询内容<span>*</span
              ><textarea
                v-model="form.message"
                required
                rows="6"
                maxlength="3000"
                placeholder="请说明业务场景、时间计划以及希望解决的问题"
              />
            </label>
            <label class="contact-consent">
              <input v-model="form.privacyAccepted" required type="checkbox" />
              <span
                >我已阅读<RouterLink to="/privacy">隐私与数据处理说明（待主体确认）</RouterLink
                >，同意平台仅为处理本次咨询使用上述信息。</span
              >
            </label>
            <p v-if="error" class="form-error">{{ error }}</p>
            <button
              class="mkt-button mkt-button-primary contact-submit"
              type="submit"
              :disabled="submitting"
            >
              <Send :size="17" />{{ submitting ? '正在提交' : '提交咨询' }}
            </button>
          </form>
        </section>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.contact-main {
  min-height: calc(100vh - 420px);
  padding: 86px 0 96px;
  background: #f5f7fa;
}

.contact-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(460px, 0.72fr);
  gap: 72px;
  align-items: start;
}

.contact-copy h1 {
  max-width: 620px;
  margin: 16px 0 20px;
  color: #111a24;
  font-size: clamp(40px, 4.8vw, 64px);
  line-height: 1.1;
}

.contact-copy > p {
  max-width: 680px;
  color: #586678;
  font-size: 17px;
  line-height: 1.8;
}

.contact-copy ul {
  display: grid;
  gap: 12px;
  margin: 30px 0;
  padding: 0;
  list-style: none;
}

.contact-copy li {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  color: #34465d;
  line-height: 1.6;
}

.contact-copy li svg {
  flex: 0 0 auto;
  margin-top: 4px;
  color: #1d6b45;
}

.contact-copy aside {
  max-width: 680px;
  padding: 18px 20px;
  border-left: 3px solid #2c5fa7;
  background: #fff;
}

.contact-copy aside p {
  margin: 7px 0 0;
  color: #667486;
  font-size: 13px;
  line-height: 1.75;
}

.contact-form-card {
  padding: 32px;
  border: 1px solid #dbe1e9;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 45px rgb(34 48 68 / 8%);
}

.contact-form-card form {
  display: grid;
  gap: 18px;
}

.contact-form-card label {
  display: grid;
  gap: 7px;
  color: #28394f;
  font-size: 13px;
  font-weight: 750;
}

.contact-form-card label > span {
  color: #c94848;
}

.contact-form-card input,
.contact-form-card select,
.contact-form-card textarea {
  width: 100%;
  border: 1px solid #cfd7e1;
  border-radius: 7px;
  padding: 11px 12px;
  color: #1e2b3c;
  background: #fff;
  font: inherit;
  font-weight: 500;
}

.contact-form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.contact-consent {
  grid-template-columns: 18px 1fr !important;
  align-items: start;
  gap: 9px !important;
  font-weight: 500 !important;
  line-height: 1.6;
}

.contact-consent input {
  width: 16px;
  margin-top: 3px;
}

.contact-consent span {
  color: #637083 !important;
}

.contact-consent a {
  color: #245fae;
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 2px;
}

.contact-submit {
  width: 100%;
  justify-content: center;
}

.contact-success {
  min-height: 400px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 14px;
  text-align: center;
}

.contact-success svg {
  color: #258054;
}

.contact-success h2,
.contact-success p {
  margin: 0;
}

.contact-success p {
  color: #667486;
}

@media (max-width: 900px) {
  .contact-layout {
    grid-template-columns: 1fr;
    gap: 36px;
  }
}

@media (max-width: 560px) {
  .contact-main {
    padding: 56px 0 70px;
  }

  .contact-form-card {
    padding: 22px;
  }

  .contact-form-row {
    grid-template-columns: 1fr;
  }
}
</style>
