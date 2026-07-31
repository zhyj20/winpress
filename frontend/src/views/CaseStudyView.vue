<script setup lang="ts">
import { ArrowRight, CheckCircle2, ShieldCheck } from 'lucide-vue-next'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import { anonymizedForumCase } from '@/marketing/content/caseStudy'
import '@/assets/marketing.css'
import '@/assets/marketing-editorial.css'
</script>

<template>
  <div class="mkt-page editorial-page case-study-page">
    <SiteHeader />
    <main>
      <section class="case-study-hero">
        <div class="mkt-shell">
          <span class="mkt-eyebrow">脱敏案例资料</span>
          <h1>{{ anonymizedForumCase.title }}</h1>
          <p>{{ anonymizedForumCase.summary }}</p>
          <p class="case-study-hero-note">{{ anonymizedForumCase.sourceState }}</p>
        </div>
      </section>

      <section class="case-study-overview">
        <div class="mkt-shell">
          <div class="case-study-overview-copy">
            <span class="mkt-eyebrow">案例摘要</span>
            <h2>只保留公开必要信息</h2>
            <p>
              这不是项目复盘，也不提供媒体邀请的方法、过程或成果。它只作为服务边界示例，说明哪些信息可以被公开，哪些信息应保留在受权限控制的项目内。
            </p>
          </div>
          <dl class="case-study-facts">
            <div v-for="fact in anonymizedForumCase.facts" :key="fact.label">
              <dt>{{ fact.label }}</dt>
              <dd>{{ fact.value }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <section class="case-study-scope">
        <div class="mkt-shell case-study-scope-grid">
          <article>
            <span class="mkt-eyebrow">页面说明</span>
            <h2>本页说明什么</h2>
            <ul>
              <li v-for="item in anonymizedForumCase.shows" :key="item">
                <CheckCircle2 :size="17" />{{ item }}
              </li>
            </ul>
          </article>
          <article>
            <span class="mkt-eyebrow">保护边界</span>
            <h2>本页不说明什么</h2>
            <ul>
              <li v-for="item in anonymizedForumCase.doesNotShow" :key="item">
                <CheckCircle2 :size="17" />{{ item }}
              </li>
            </ul>
          </article>
        </div>
      </section>

      <section class="case-study-boundaries">
        <div class="mkt-shell">
          <ShieldCheck :size="25" />
          <div>
            <span class="mkt-eyebrow">公开边界</span>
            <h2>项目操作留在项目内</h2>
            <ul>
              <li v-for="boundary in anonymizedForumCase.boundaries" :key="boundary">
                <CheckCircle2 :size="17" />{{ boundary }}
              </li>
            </ul>
          </div>
        </div>
      </section>

      <section class="editorial-cta">
        <div class="mkt-shell">
          <div>
            <span>项目咨询</span>
            <h2>讨论媒体邀请服务</h2>
            <p>如需评估具体项目，请由项目负责人在受控空间内确认服务范围和资料边界。</p>
          </div>
          <RouterLink class="mkt-button mkt-button-light" to="/contact">
            联系服务顾问<ArrowRight :size="17" />
          </RouterLink>
        </div>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.case-study-hero {
  padding: 126px 0 86px;
  color: #fff;
  background: #111a24;
}

.case-study-hero .mkt-eyebrow {
  color: #8ab3fa;
}

.case-study-hero h1 {
  max-width: 780px;
  margin: 18px 0;
  color: #fff;
  font-size: clamp(45px, 5vw, 68px);
  line-height: 1.1;
  letter-spacing: -0.045em;
}

.case-study-hero > .mkt-shell > p:not(.case-study-hero-note) {
  max-width: 760px;
  color: #d2dce9;
  font-size: 18px;
  line-height: 1.8;
}

.case-study-hero-note {
  max-width: 760px;
  margin-top: 24px;
  color: #aebdd0;
  font-size: 13px;
  line-height: 1.7;
}

.case-study-overview,
.case-study-scope {
  padding: 92px 0;
}

.case-study-overview {
  background: #f3f0ea;
}

.case-study-overview > .mkt-shell {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(360px, 1.1fr);
  align-items: end;
  gap: 80px;
}

.case-study-overview h2,
.case-study-scope h2,
.case-study-boundaries h2 {
  margin: 14px 0;
  color: #111a24;
  font-size: clamp(34px, 4vw, 50px);
  letter-spacing: -0.035em;
}

.case-study-overview p {
  color: #5b6879;
  line-height: 1.8;
}

.case-study-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
  border-top: 1px solid #c8c5bd;
  border-left: 1px solid #c8c5bd;
}

.case-study-facts > div {
  min-height: 134px;
  padding: 22px;
  border-right: 1px solid #c8c5bd;
  border-bottom: 1px solid #c8c5bd;
}

.case-study-facts dt {
  color: #435365;
  font-size: 12px;
  font-weight: 750;
}

.case-study-facts dd {
  margin: 44px 0 0;
  color: #111a24;
  font-size: 19px;
  font-weight: 800;
}

.case-study-scope {
  background: #fff;
}

.case-study-scope-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.case-study-scope article {
  min-width: 0;
  padding: 34px;
  border: 1px solid #d8e1eb;
  background: #f8fafc;
}

.case-study-scope article:last-child {
  border-color: #c7d8ef;
  background: #f4f8ff;
}

.case-study-scope ul,
.case-study-boundaries ul {
  display: grid;
  gap: 12px;
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
}

.case-study-scope li,
.case-study-boundaries li {
  display: flex;
  gap: 9px;
  color: #536376;
  font-size: 14px;
  line-height: 1.72;
}

.case-study-scope li svg,
.case-study-boundaries li svg {
  flex: 0 0 auto;
  margin-top: 3px;
  color: #1f56d8;
}

.case-study-boundaries {
  padding: 0 0 92px;
  background: #f5f7fa;
}

.case-study-boundaries > .mkt-shell {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 22px;
  padding: 32px;
  border: 1px solid #c7d8ef;
  background: #f4f8ff;
}

.case-study-boundaries > .mkt-shell > svg {
  color: #1f56d8;
}

@media (max-width: 820px) {
  .case-study-hero {
    padding: 88px 0 66px;
  }

  .case-study-overview,
  .case-study-scope {
    padding: 72px 0;
  }

  .case-study-overview > .mkt-shell,
  .case-study-scope-grid {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .case-study-facts {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .case-study-boundaries {
    padding-bottom: 72px;
  }
}

@media (max-width: 520px) {
  .case-study-hero {
    padding: 68px 0 54px;
  }

  .case-study-hero h1 {
    font-size: 38px;
  }

  .case-study-hero > .mkt-shell > p:not(.case-study-hero-note) {
    font-size: 16px;
  }

  .case-study-facts,
  .case-study-scope-grid {
    grid-template-columns: 1fr;
  }

  .case-study-facts > div {
    min-height: auto;
  }

  .case-study-scope article,
  .case-study-boundaries > .mkt-shell {
    padding: 24px;
  }

  .case-study-boundaries > .mkt-shell {
    grid-template-columns: 1fr;
  }
}
</style>
