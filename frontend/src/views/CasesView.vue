<script setup lang="ts">
import { ArrowRight, ExternalLink, FileText, Newspaper, ShieldCheck } from 'lucide-vue-next'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import { insightArticles, taskCases } from '@/marketing/content/home'
import { historicalReportGroups, reportsForCategory } from '@/marketing/content/historicalReports'
import '@/assets/marketing.css'
import '@/assets/marketing-editorial.css'
</script>

<template>
  <div class="mkt-page editorial-page cases-page">
    <SiteHeader />
    <main>
      <section class="editorial-hero">
        <div class="mkt-shell">
          <span class="mkt-eyebrow">案例展示</span>
          <h1>项目示例</h1>
          <p>案例按授权范围展示；项目成果以可追溯证据为准。</p>
        </div>
      </section>

      <section class="editorial-section">
        <div class="mkt-shell">
          <div class="case-project-grid">
            <article class="case-project-card case-project-card--named">
              <figure>
                <img
                  src="/case-media/chipsea-computex-booth.webp"
                  alt="COMPUTEX 2026 芯海科技展位现场"
                  loading="eager"
                  decoding="async"
                />
              </figure>
              <div class="case-project-card__body">
                <span class="mkt-eyebrow">新闻发布会案例</span>
                <h2>芯海科技 COMPUTEX 2026</h2>
                <p>项目启动、内容准备、展会采访与公开报道核验，按一条时间轴分别呈现。</p>
                <dl>
                  <div>
                    <dt>项目周期</dt>
                    <dd>2026年5—6月</dd>
                  </div>
                  <div>
                    <dt>成果证据</dt>
                    <dd>13个关联页面</dd>
                  </div>
                </dl>
                <RouterLink class="mkt-text-link" to="/cases/chipsea-computex-2026">
                  <Newspaper :size="16" />查看完整案例<ArrowRight :size="16" />
                </RouterLink>
              </div>
            </article>

            <article class="case-project-card">
              <figure class="case-project-card__anonymized-visual" aria-label="媒体邀请脱敏案例">
                <ShieldCheck :size="38" />
                <span>脱敏项目摘要</span>
              </figure>
              <div class="case-project-card__body">
                <span class="mkt-eyebrow">脱敏资料样本</span>
                <h2>媒体邀请项目</h2>
                <p>只保留服务类别和公开边界，不展示项目名称、操作过程、媒体名单或现场资料。</p>
                <dl>
                  <div>
                    <dt>展示内容</dt>
                    <dd>项目摘要</dd>
                  </div>
                  <div>
                    <dt>原始文件</dt>
                    <dd>不公开</dd>
                  </div>
                </dl>
                <RouterLink class="mkt-text-link" to="/cases/industry-forum">
                  <FileText :size="16" />查看公开边界<ArrowRight :size="16" />
                </RouterLink>
              </div>
            </article>
          </div>
          <div class="editorial-section-heading editorial-heading-row case-archive-heading">
            <div>
              <span class="mkt-eyebrow">历史传播资料</span>
              <h2>两类汇报</h2>
            </div>
            <p>活动项目与直编发稿分别归集；原始汇报、渠道链接、价格与名单不对外公开。</p>
          </div>
          <div class="case-archive-grid">
            <article
              v-for="group in historicalReportGroups"
              :key="group.key"
              class="case-archive-card"
            >
              <div class="case-archive-card__head">
                <span>{{ group.label }}</span>
                <FileText :size="19" />
              </div>
              <h3>{{ group.title }}</h3>
              <p>{{ group.description }}</p>
              <strong>{{ reportsForCategory(group.key).length }} 份已归集资料</strong>
              <RouterLink
                class="mkt-text-link"
                :to="{ path: '/case-evidence/historical-reports', query: { type: group.key } }"
              >
                查看归集目录<ArrowRight :size="16" />
              </RouterLink>
            </article>
          </div>
          <div class="editorial-section-heading editorial-heading-row">
            <div>
              <span class="mkt-eyebrow">服务流程</span>
              <h2>四类项目</h2>
            </div>
            <p>以下内容是流程示例，不代表未经授权的客户名称、媒体结果或传播业绩。</p>
          </div>
          <div class="case-example-grid">
            <article v-for="item in taskCases" :key="item.category">
              <div class="case-example-head">
                <component :is="item.icon" :size="21" />
                <span>{{ item.category }}</span>
              </div>
              <h3>{{ item.title }}</h3>
              <dl>
                <div>
                  <dt>常见问题</dt>
                  <dd>{{ item.challenge }}</dd>
                </div>
                <div>
                  <dt>执行安排</dt>
                  <dd>{{ item.response }}</dd>
                </div>
                <div>
                  <dt>可查交付</dt>
                  <dd>{{ item.result }}</dd>
                </div>
              </dl>
            </article>
          </div>
        </div>
      </section>

      <section class="editorial-section editorial-section-soft">
        <div class="mkt-shell">
          <div class="editorial-section-heading editorial-heading-row">
            <div>
              <span class="mkt-eyebrow">历史文章</span>
              <h2>项目资料选读</h2>
            </div>
            <p>
              以下内容来自旧站公开文章，保留项目方法，不把历史数量、客户或传播结果改写成当前承诺。
            </p>
          </div>
          <div class="case-reading-grid">
            <article v-for="article in insightArticles" :key="article.sourceUrl">
              <small>{{ article.category }}</small>
              <q>{{ article.quote }}</q>
              <h3>{{ article.title }}</h3>
              <p>{{ article.summary }}</p>
              <a :href="article.sourceUrl" target="_blank" rel="noopener noreferrer">
                阅读旧站原文<ExternalLink :size="15" />
              </a>
            </article>
          </div>
        </div>
      </section>

      <section class="case-evidence-note">
        <div class="mkt-shell">
          <ShieldCheck :size="24" />
          <div>
            <span class="mkt-eyebrow">证据规则</span>
            <h2>案例发布条件</h2>
            <p>
              脱敏资料样本只展示工作结构；客户名称、项目数量、媒体名单和传播结果只有在授权与原始证据齐备后才能对外发布。
            </p>
          </div>
          <RouterLink class="mkt-text-link" to="/case-evidence">
            查看案例与证据说明<ArrowRight :size="16" />
          </RouterLink>
        </div>
      </section>

      <section class="editorial-cta">
        <div class="mkt-shell">
          <div>
            <span>项目入口</span>
            <h2>创建传播项目</h2>
            <p>选择一项服务独立下单；同一活动下的关联项目可以集中查看。</p>
          </div>
          <RouterLink class="mkt-button mkt-button-light" to="/requirements/new">
            选择服务<ArrowRight :size="17" />
          </RouterLink>
        </div>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.case-project-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 88px;
}

.case-project-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #d8e1ec;
  border-radius: 10px;
  background: #fff;
}

.case-project-card--named {
  border-color: #bfd0eb;
}

.case-project-card figure {
  height: 310px;
  margin: 0;
  overflow: hidden;
  background: #223247;
}

.case-project-card__anonymized-visual {
  display: grid;
  place-content: center;
  gap: 13px;
  color: #d7e5f6;
  background: linear-gradient(145deg, rgba(74, 131, 204, 0.16), transparent 58%), #182638;
  text-align: center;
}

.case-project-card__anonymized-visual svg {
  justify-self: center;
  color: #84ade5;
}

.case-project-card__anonymized-visual span {
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.case-project-card img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 240ms ease;
}

.case-project-card:hover img {
  transform: scale(1.02);
}

.case-project-card__body {
  padding: 30px;
}

.case-project-card h2 {
  margin: 12px 0;
  color: #111a24;
  font-size: clamp(28px, 3.3vw, 42px);
  line-height: 1.18;
  letter-spacing: -0.03em;
}

.case-project-card p {
  min-height: 50px;
  color: #556577;
  font-size: 14px;
  line-height: 1.76;
}

.case-project-card dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 26px 0;
}

.case-project-card dl > div {
  padding: 15px;
  border: 1px solid #d8e1ec;
  background: #f8fafc;
}

.case-project-card dt {
  color: #667487;
  font-size: 11px;
  font-weight: 750;
}

.case-project-card dd {
  margin: 24px 0 0;
  color: #17335f;
  font-size: 15px;
  font-weight: 800;
}

.case-project-card .mkt-text-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.case-archive-heading {
  margin-top: 0;
}

.case-archive-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin: 26px 0 88px;
}

.case-archive-card {
  display: flex;
  min-width: 0;
  min-height: 258px;
  flex-direction: column;
  padding: 28px 30px;
  border: 1px solid #d8e1ec;
  border-radius: 10px;
  background: linear-gradient(145deg, #fff 0%, #f6f9fc 100%);
}

.case-archive-card:hover {
  border-color: #abc0d9;
  box-shadow: 0 12px 28px rgba(28, 66, 112, 0.08);
}

.case-archive-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #2c6096;
}

.case-archive-card__head span {
  font-size: 12px;
  font-weight: 800;
}

.case-archive-card h3 {
  margin: 24px 0 10px;
  color: #14243a;
  font-size: clamp(24px, 2.6vw, 33px);
  letter-spacing: -0.035em;
}

.case-archive-card p {
  margin: 0;
  color: #5c6c80;
  font-size: 14px;
  line-height: 1.75;
}

.case-archive-card strong {
  display: block;
  margin: 22px 0 18px;
  color: #294c77;
  font-size: 14px;
}

.case-archive-card .mkt-text-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: auto;
}

@media (max-width: 820px) {
  .case-project-grid {
    grid-template-columns: 1fr;
    margin-bottom: 68px;
  }

  .case-archive-grid {
    grid-template-columns: 1fr;
    margin-bottom: 68px;
  }
}

@media (max-width: 520px) {
  .case-project-card figure {
    height: 230px;
  }

  .case-project-card__body {
    padding: 24px;
  }

  .case-project-card p {
    min-height: auto;
  }

  .case-project-card dl {
    grid-template-columns: 1fr;
  }
}
</style>
