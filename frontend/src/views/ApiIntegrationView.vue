<script setup lang="ts">
import { ArrowLeft, CheckCircle2, FileJson, Send, ShieldCheck } from 'lucide-vue-next'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import '@/assets/marketing.css'

const apiScope = [
  'CRM、内容系统、官网或 App 与传播项目的协同需求',
  '创建需求、查询项目状态、接收结果的接口范围评估',
  '账号、联系人与项目字段的受控映射',
  '回调、日志、灰度与异常处理要求的确认',
]

const needPrepare = [
  '拟采用的鉴权方式与访问来源控制要求',
  '字段标准：企业名、联系人、联系人电话、标题、正文/素材、发布时间',
  '目标动作：是否触发“现场采写”“媒体邀请”“直编发稿”“发布会传播”',
  '需要回传的项目状态、结果信息与异常处理规则',
]
</script>

<template>
  <div class="mkt-page api-page">
    <SiteHeader />
    <main>
      <section class="mkt-section mkt-section-white">
        <div class="mkt-shell api-hero">
          <p class="mkt-eyebrow">企业系统接入</p>
          <h1>API 接入</h1>
          <p>
            将服务需求、项目状态和直编渠道目录接入 CRM、内容系统或业务中台。
            正式密钥仅在应用归属、访问范围、沙箱联调和上线验收完成后签发；接口能力以双方确认的接入方案为准。
          </p>
          <div class="mkt-actions">
            <RouterLink class="mkt-button mkt-button-primary" to="/requirements/new">
              查看服务范围
            </RouterLink>
            <RouterLink class="mkt-button mkt-button-secondary" to="/contact?type=API_INTEGRATION">
              申请接口接入
            </RouterLink>
          </div>
        </div>
      </section>

      <section class="mkt-section mkt-section-soft">
        <div class="mkt-shell api-grid">
          <article class="api-card">
            <h2>评估场景</h2>
            <ul>
              <li v-for="item in apiScope" :key="item"><CheckCircle2 :size="16" />{{ item }}</li>
            </ul>
          </article>
          <article class="api-card">
            <h2>评估重点</h2>
            <div class="api-stat">
              <p><strong>对象</strong>：哪些系统和业务动作需要衔接</p>
              <p><strong>字段</strong>：哪些资料可传递、由谁维护、如何留痕</p>
              <p><strong>验收</strong>：状态查询、异常处理和上线边界如何核对</p>
            </div>
          </article>
        </div>
      </section>

      <section class="mkt-section mkt-section-white">
        <div class="mkt-shell">
          <div class="mkt-section-head">
            <span class="mkt-eyebrow">接入清单</span>
            <h2>接入准备</h2>
            <p class="mkt-match-caption">确定范围后再进入联调。</p>
            <p>提交咨询后，平台与客户共同确认业务场景、字段边界和验收方式。</p>
          </div>
          <div class="api-checklist">
            <article v-for="item in needPrepare" :key="item">
              <FileJson :size="18" />
              <p>{{ item }}</p>
            </article>
          </div>
          <div class="api-note">
            <ShieldCheck :size="19" />
            <p>
              生产环境前需确认访问控制、重试、错误处理与日志留存；涉及合规审计时，双方确认白名单和传输策略。
            </p>
          </div>
        </div>
      </section>

      <section class="mkt-section mkt-section-soft">
        <div class="mkt-shell">
          <span class="mkt-match-caption">接入流程</span>
          <div class="api-cta">
            <div>
              <h2>接口接入</h2>
              <p class="mkt-match-caption">需要与现有系统协同？</p>
              <p>
                请说明现有系统、预计调用量和上线时间。确认后，再安排接口清单、联调环境与验收标准。
              </p>
            </div>
            <div class="mkt-actions">
              <RouterLink class="mkt-button mkt-button-primary" to="/contact?type=API_INTEGRATION">
                <Send :size="16" /> 申请接口接入
              </RouterLink>
              <RouterLink class="mkt-button mkt-button-light" to="/">
                <ArrowLeft :size="16" /> 返回首页
              </RouterLink>
            </div>
          </div>
        </div>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.api-hero {
  padding-top: 60px;
  padding-bottom: 60px;
}

.api-hero h1 {
  max-width: 780px;
  margin: 12px 0 14px;
  font-size: clamp(36px, 3.4vw, 48px);
  line-height: 1.22;
}

.api-hero p {
  max-width: 820px;
  color: #536173;
  line-height: 1.75;
}

.api-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.api-card {
  min-height: 260px;
  padding: 30px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #fff;
  display: grid;
  gap: 16px;
}

.api-card h2 {
  margin: 0;
  font-size: 24px;
}

.api-card ul {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 10px;
}

.api-card li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: #455468;
  font-size: 15px;
  line-height: 1.65;
}

.api-stat p {
  margin: 0;
  color: #455468;
  font-size: 15px;
  line-height: 1.65;
}

.api-stat strong {
  color: #22395a;
}

.api-checklist {
  display: grid;
  gap: 12px;
  margin-top: 20px;
}

.api-checklist article {
  min-height: 72px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: #fff;
}

.api-checklist article svg {
  color: #0d56de;
  margin-top: 2px;
}

.api-checklist p {
  margin: 0;
  color: #455468;
  line-height: 1.65;
}

.api-note {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 8px;
  background: #f1f6ff;
  border: 1px solid #cdd8ee;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: #3f5979;
  font-size: 13px;
  line-height: 1.7;
}

.api-note svg {
  color: #1c7a43;
  margin-top: 2px;
}

.api-cta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 34px;
  border: 1px solid #dce4ef;
  border-radius: 10px;
  background: #fff;
}

.api-cta h2 {
  margin: 0 0 10px;
  font-size: 30px;
  line-height: 1.24;
}

.api-cta p {
  margin: 0;
  color: #526173;
  line-height: 1.75;
  max-width: 780px;
}

@media (max-width: 920px) {
  .api-grid,
  .api-cta {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .api-cta {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
