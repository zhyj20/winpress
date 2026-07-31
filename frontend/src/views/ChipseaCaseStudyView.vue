<script setup lang="ts">
import { nextTick, onMounted } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  ExternalLink,
  FileCheck2,
  Search,
  ShieldCheck,
} from 'lucide-vue-next'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import { chipseaComputexCase } from '@/marketing/content/chipseaCase'
import '@/assets/marketing.css'
import '@/assets/marketing-editorial.css'

const coverageSummary = {
  total: chipseaComputexCase.coverage.length,
  taiwan: chipseaComputexCase.coverage.filter((report) => report.market === '台湾').length,
  publicPage: chipseaComputexCase.coverage.filter((report) => report.verification === 'PUBLIC_PAGE')
    .length,
  archiveEvidence: chipseaComputexCase.coverage.filter(
    (report) => report.verification === 'ARCHIVE_EVIDENCE',
  ).length,
}

const coverageEvidenceSummary = [
  `${coverageSummary.publicPage} 条公开页面`,
  coverageSummary.archiveEvidence ? `${coverageSummary.archiveEvidence} 条归档留存` : '',
]
  .filter(Boolean)
  .join('、')

function alignInitialCaseAnchor() {
  const anchorId = window.location.hash.replace(/^#/, '')
  const target = anchorId ? document.getElementById(anchorId) : null

  target?.scrollIntoView({ block: 'start' })
}

onMounted(async () => {
  await nextTick()
  window.requestAnimationFrame(alignInitialCaseAnchor)
})
</script>

<template>
  <div class="mkt-page editorial-page chipsea-case-page">
    <SiteHeader />
    <main>
      <section class="chipsea-hero">
        <div class="mkt-shell chipsea-hero__layout">
          <div class="chipsea-hero__copy">
            <RouterLink class="chipsea-back-link" to="/cases">
              <ArrowLeft :size="16" />返回案例展示
            </RouterLink>
            <span class="mkt-eyebrow">新闻发布会案例</span>
            <h1>{{ chipseaComputexCase.title }}</h1>
            <p>{{ chipseaComputexCase.summary }}</p>
            <p class="chipsea-source-note">{{ chipseaComputexCase.sourceState }}</p>
          </div>
          <figure class="chipsea-hero__media">
            <img
              src="/case-media/chipsea-computex-booth.webp"
              alt="COMPUTEX 2026 芯海科技展位现场"
              decoding="async"
            />
            <figcaption>
              <span>活动现场</span>
              <strong>COMPUTEX 2026 · 台北</strong>
            </figcaption>
          </figure>
        </div>
      </section>

      <nav class="case-story-nav" aria-label="案例章节">
        <div class="mkt-shell">
          <a href="#launch">项目启动</a>
          <a href="#execution">执行过程</a>
          <a href="#event-day">活动当天</a>
          <a href="#results">报道成果</a>
        </div>
      </nav>

      <section class="chipsea-overview">
        <div class="mkt-shell chipsea-overview__layout">
          <div>
            <span class="mkt-eyebrow">项目概况</span>
            <h2>展会传播项目</h2>
            <p>
              本案不是单次发稿。项目从展前资料和采访准备开始，现场推进媒体沟通，展后再逐条核验报道。采写、媒体邀请与项目管理是同一活动下的关联任务，仍分别记录与交付。
            </p>
          </div>
          <dl class="chipsea-facts">
            <div v-for="fact in chipseaComputexCase.facts" :key="fact.label">
              <dt>{{ fact.label }}</dt>
              <dd>{{ fact.value }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <section id="launch" class="chipsea-section chipsea-launch">
        <div class="mkt-shell chipsea-split">
          <figure class="chipsea-launch__photo">
            <img
              :src="chipseaComputexCase.launchPhoto.src"
              :alt="chipseaComputexCase.launchPhoto.alt"
              loading="lazy"
              decoding="async"
            />
            <figcaption>{{ chipseaComputexCase.launchPhoto.caption }}</figcaption>
          </figure>
          <div class="chipsea-section-copy">
            <span class="mkt-eyebrow">项目启动</span>
            <h2>立项与资料确认</h2>
            <p>
              启动阶段先解决边界问题：什么可以对外说、由谁审核、记者需要哪些事实、现场素材由谁回传。没有确认的经营信息不进入媒体材料。
            </p>
            <ul class="chipsea-check-list">
              <li v-for="point in chipseaComputexCase.launchPoints" :key="point">
                <CheckCircle2 :size="18" />{{ point }}
              </li>
            </ul>
          </div>
        </div>
      </section>

      <section id="execution" class="chipsea-section chipsea-execution">
        <div class="mkt-shell">
          <div class="editorial-section-heading editorial-heading-row">
            <div>
              <span class="mkt-eyebrow">执行过程</span>
              <h2>项目时间线</h2>
            </div>
            <p>项目 WBS 定义推进节点；成果区只收录可以公开说明的页面和留存。</p>
          </div>
          <ol class="chipsea-timeline" aria-label="芯海科技 COMPUTEX 项目时间表">
            <li v-for="(milestone, index) in chipseaComputexCase.milestones" :key="milestone.title">
              <div class="chipsea-timeline__index">{{ String(index + 1).padStart(2, '0') }}</div>
              <div class="chipsea-timeline__body">
                <span>{{ milestone.period }}</span>
                <h3>{{ milestone.title }}</h3>
                <p>{{ milestone.description }}</p>
                <ul>
                  <li v-for="item in milestone.deliverables" :key="item">{{ item }}</li>
                </ul>
              </div>
            </li>
          </ol>

          <div class="chipsea-work-grid">
            <article v-for="item in chipseaComputexCase.executionPoints" :key="item.title">
              <FileCheck2 :size="21" />
              <h3>{{ item.title }}</h3>
              <p>{{ item.description }}</p>
            </article>
          </div>
        </div>
      </section>

      <section id="event-day" class="chipsea-section chipsea-event">
        <div class="mkt-shell">
          <div class="editorial-section-heading editorial-heading-row">
            <div>
              <span class="mkt-eyebrow">活动当天</span>
              <h2>采访与产品展示</h2>
            </div>
            <p>展位沟通、产品演示和记者采访在同一执行窗口推进，现场素材当天归集。</p>
          </div>
          <div class="chipsea-event-gallery">
            <figure v-for="photo in chipseaComputexCase.eventPhotos" :key="photo.src">
              <img :src="photo.src" :alt="photo.alt" loading="lazy" decoding="async" />
              <figcaption>{{ photo.caption }}</figcaption>
            </figure>
          </div>
        </div>
      </section>

      <section id="results" class="chipsea-section chipsea-results">
        <div class="mkt-shell">
          <div class="editorial-section-heading editorial-heading-row">
            <div>
              <span class="mkt-eyebrow">报道成果</span>
              <h2>公开报道核验</h2>
            </div>
            <p>
              检索覆盖 2026 年 6 月 2 日至 30 日。展期报道集中在 6 月 2 日至 5 日，6 月 11
              日另保留一条展后行业观察。
            </p>
          </div>

          <dl class="chipsea-result-stats">
            <div>
              <dt>台湾关联页面</dt>
              <dd>
                <span>{{ coverageSummary.taiwan }} 个</span>
                <small
                  >含
                  {{ coverageSummary.archiveEvidence }} 条归档留存，不作为独立媒体数量承诺</small
                >
              </dd>
            </div>
            <div>
              <dt>关联报道页面</dt>
              <dd>
                <span>{{ coverageSummary.total }} 个</span>
                <small>{{ coverageEvidenceSummary }}；不把同源转载等同于独立原创</small>
              </dd>
            </div>
            <div>
              <dt>核验日期</dt>
              <dd>
                <span>2026-07-31</span>
                <small>链接状态可能随媒体站点调整</small>
              </dd>
            </div>
          </dl>

          <div class="chipsea-coverage-list">
            <article v-for="report in chipseaComputexCase.coverage" :key="report.url">
              <div class="chipsea-coverage-meta">
                <time :datetime="report.date">{{ report.date }}</time>
                <span>{{ report.market }}</span>
              </div>
              <div class="chipsea-coverage-copy">
                <small>{{ report.outlet }} · {{ report.kind }}</small>
                <h3>{{ report.title }}</h3>
                <p>{{ report.angle }}</p>
                <span
                  :class="[
                    'chipsea-evidence-label',
                    `chipsea-evidence-label--${report.verification.toLowerCase()}`,
                  ]"
                >
                  <CheckCircle2 v-if="report.verification === 'PUBLIC_PAGE'" :size="14" />
                  <FileCheck2 v-else-if="report.verification === 'ARCHIVE_EVIDENCE'" :size="14" />
                  <Search v-else :size="14" />
                  {{ report.evidence }}
                </span>
              </div>
              <a :href="report.url" target="_blank" rel="noopener noreferrer">
                查看报道<ExternalLink :size="16" />
              </a>
            </article>
          </div>

          <div class="chipsea-evidence-gallery">
            <figure v-for="item in chipseaComputexCase.evidenceImages" :key="item.src">
              <img :src="item.src" :alt="item.alt" loading="lazy" decoding="async" />
              <figcaption>{{ item.caption }}</figcaption>
            </figure>
          </div>
        </div>
      </section>

      <section class="chipsea-boundary">
        <div class="mkt-shell">
          <ShieldCheck :size="26" />
          <div>
            <span class="mkt-eyebrow">证据说明</span>
            <h2>展示边界</h2>
            <ul>
              <li v-for="note in chipseaComputexCase.resultNotes" :key="note">{{ note }}</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="editorial-cta">
        <div class="mkt-shell">
          <div>
            <span>新闻发布会</span>
            <h2>创建活动传播项目</h2>
            <p>先提交活动名称、联系人和联系电话，其他资料可在项目启动后继续补充。</p>
          </div>
          <RouterLink class="mkt-button mkt-button-light" to="/requirements/news-conference">
            举办新闻发布会<ArrowRight :size="17" />
          </RouterLink>
        </div>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.chipsea-hero {
  padding: 118px 0 78px;
  color: #fff;
  background: #111a24;
}

.chipsea-hero__layout {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(480px, 1.08fr);
  align-items: center;
  gap: 70px;
}

.chipsea-back-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 45px;
  color: #c7d4e5;
  font-size: 13px;
  font-weight: 750;
  text-decoration: none;
}

.chipsea-hero .mkt-eyebrow {
  color: #8ab3fa;
}

.chipsea-hero h1 {
  max-width: 700px;
  margin: 18px 0;
  color: #fff;
  font-size: clamp(43px, 5.1vw, 70px);
  line-height: 1.08;
  letter-spacing: -0.045em;
}

.chipsea-hero__copy > p:not(.chipsea-source-note) {
  max-width: 680px;
  color: #d3deeb;
  font-size: 18px;
  line-height: 1.78;
}

.chipsea-source-note {
  max-width: 680px;
  margin-top: 24px;
  color: #aebdd0;
  font-size: 12px;
  line-height: 1.75;
}

.chipsea-hero__media {
  position: relative;
  min-height: 560px;
  margin: 0;
  overflow: hidden;
  background: #243244;
}

.chipsea-hero__media img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.chipsea-hero__media::after {
  position: absolute;
  inset: 0;
  content: '';
  background: linear-gradient(180deg, transparent 55%, rgba(7, 13, 20, 0.82));
}

.chipsea-hero__media figcaption {
  position: absolute;
  z-index: 1;
  right: 28px;
  bottom: 25px;
  left: 28px;
  display: flex;
  justify-content: space-between;
  gap: 20px;
}

.chipsea-hero__media figcaption span {
  color: #c9d5e5;
  font-size: 12px;
  font-weight: 750;
}

.chipsea-hero__media figcaption strong {
  color: #fff;
  font-size: 14px;
}

.case-story-nav {
  position: sticky;
  z-index: 20;
  top: 72px;
  border-bottom: 1px solid #dce3ec;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(14px);
}

.case-story-nav > .mkt-shell {
  display: flex;
  gap: 36px;
  overflow-x: auto;
}

.case-story-nav a {
  flex: 0 0 auto;
  padding: 18px 0 16px;
  color: #344256;
  border-bottom: 2px solid transparent;
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
}

.case-story-nav a:hover {
  color: #1f56d8;
  border-bottom-color: #1f56d8;
}

.chipsea-overview,
.chipsea-section {
  scroll-margin-top: 140px;
}

.chipsea-overview {
  padding: 88px 0;
  background: #f3f0ea;
}

.chipsea-overview__layout {
  display: grid;
  grid-template-columns: minmax(300px, 0.86fr) minmax(560px, 1.14fr);
  align-items: end;
  gap: 74px;
}

.chipsea-overview h2,
.chipsea-section h2,
.chipsea-boundary h2 {
  margin: 14px 0;
  color: #111a24;
  font-size: clamp(34px, 4vw, 52px);
  line-height: 1.14;
  letter-spacing: -0.038em;
}

.chipsea-overview p,
.chipsea-section-copy > p {
  color: #556577;
  line-height: 1.82;
}

.chipsea-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  border-top: 1px solid #c8c5bd;
  border-left: 1px solid #c8c5bd;
}

.chipsea-facts > div {
  min-height: 124px;
  padding: 22px;
  border-right: 1px solid #c8c5bd;
  border-bottom: 1px solid #c8c5bd;
}

.chipsea-facts dt,
.chipsea-result-stats dt {
  color: #526173;
  font-size: 11px;
  font-weight: 800;
}

.chipsea-facts dd {
  margin: 34px 0 0;
  color: #111a24;
  font-size: 18px;
  font-weight: 800;
}

.chipsea-section {
  padding: 96px 0;
}

.chipsea-launch {
  background: #fff;
}

.chipsea-split {
  display: grid;
  grid-template-columns: minmax(480px, 1.08fr) minmax(360px, 0.92fr);
  align-items: center;
  gap: 78px;
}

.chipsea-launch__photo {
  margin: 0;
}

.chipsea-launch__photo img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
}

.chipsea-launch__photo figcaption {
  padding-top: 12px;
  color: #6a7686;
  font-size: 12px;
}

.chipsea-check-list {
  display: grid;
  gap: 15px;
  margin: 28px 0 0;
  padding: 0;
  list-style: none;
}

.chipsea-check-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: #405065;
  font-size: 14px;
  line-height: 1.72;
}

.chipsea-check-list svg {
  flex: 0 0 auto;
  margin-top: 3px;
  color: #1f56d8;
}

.chipsea-execution {
  background: #f5f7fa;
}

.chipsea-timeline {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid #d7e0ea;
  border-left: 1px solid #d7e0ea;
  background: #fff;
}

.chipsea-timeline > li {
  min-width: 0;
  min-height: 324px;
  padding: 24px 20px;
  border-right: 1px solid #d7e0ea;
  border-bottom: 1px solid #d7e0ea;
}

.chipsea-timeline__index {
  color: #1f56d8;
  font-size: 12px;
  font-weight: 850;
}

.chipsea-timeline__body > span {
  display: block;
  margin-top: 44px;
  color: #66768a;
  font-size: 11px;
  font-weight: 750;
}

.chipsea-timeline h3 {
  min-height: 48px;
  margin: 10px 0 12px;
  color: #172334;
  font-size: 20px;
}

.chipsea-timeline p {
  min-height: 84px;
  margin: 0;
  color: #5b6979;
  font-size: 13px;
  line-height: 1.7;
}

.chipsea-timeline ul {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}

.chipsea-timeline ul li {
  padding: 4px 7px;
  color: #344b6b;
  border: 1px solid #cfd9e6;
  background: #f5f8fc;
  font-size: 10px;
  font-weight: 750;
}

.chipsea-work-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 32px;
}

.chipsea-work-grid article {
  min-height: 210px;
  padding: 25px;
  border: 1px solid #d7e0ea;
  background: #fff;
}

.chipsea-work-grid svg {
  color: #1f56d8;
}

.chipsea-work-grid h3 {
  margin: 32px 0 10px;
  color: #182536;
  font-size: 18px;
}

.chipsea-work-grid p {
  margin: 0;
  color: #5b697a;
  font-size: 13px;
  line-height: 1.72;
}

.chipsea-event {
  color: #fff;
  background: #111a24;
}

.chipsea-event .mkt-eyebrow {
  color: #8ab3fa;
}

.chipsea-event h2 {
  color: #fff;
}

.chipsea-event .editorial-section-heading > p {
  color: #c6d1df;
}

.chipsea-event-gallery {
  display: grid;
  grid-template-columns: 1.35fr 0.65fr;
  gap: 14px;
}

.chipsea-event-gallery figure {
  position: relative;
  min-width: 0;
  min-height: 286px;
  margin: 0;
  overflow: hidden;
  background: #253447;
}

.chipsea-event-gallery figure:first-child {
  grid-row: span 2;
  min-height: 586px;
}

.chipsea-event-gallery img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 240ms ease;
}

.chipsea-event-gallery figure:hover img {
  transform: scale(1.02);
}

.chipsea-event-gallery figcaption {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 48px 18px 17px;
  color: #fff;
  background: linear-gradient(transparent, rgba(8, 14, 21, 0.88));
  font-size: 13px;
  font-weight: 800;
}

.chipsea-results {
  background: #fff;
}

.chipsea-result-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0 0 42px;
  border-top: 1px solid #d8e0ea;
  border-left: 1px solid #d8e0ea;
}

.chipsea-result-stats > div {
  min-height: 166px;
  padding: 24px;
  border-right: 1px solid #d8e0ea;
  border-bottom: 1px solid #d8e0ea;
}

.chipsea-result-stats dd {
  margin: 28px 0 0;
  color: #111a24;
  font-size: 30px;
  font-weight: 850;
  letter-spacing: -0.025em;
}

.chipsea-result-stats dd > span {
  display: block;
  margin-bottom: 8px;
}

.chipsea-result-stats small {
  display: block;
  color: #667587;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.6;
}

.chipsea-coverage-list {
  border-top: 1px solid #dce3ec;
}

.chipsea-coverage-list article {
  display: grid;
  grid-template-columns: 138px minmax(0, 1fr) 112px;
  align-items: start;
  gap: 30px;
  padding: 25px 0;
  border-bottom: 1px solid #dce3ec;
}

.chipsea-coverage-meta {
  display: grid;
  gap: 9px;
}

.chipsea-coverage-meta time {
  color: #172538;
  font-size: 12px;
  font-weight: 800;
}

.chipsea-coverage-meta span {
  width: fit-content;
  padding: 4px 8px;
  color: #42618e;
  border: 1px solid #ced9e8;
  font-size: 10px;
  font-weight: 750;
}

.chipsea-coverage-copy > small {
  color: #1f56d8;
  font-size: 11px;
  font-weight: 800;
}

.chipsea-coverage-copy h3 {
  margin: 8px 0 9px;
  color: #172334;
  font-size: 19px;
  line-height: 1.45;
}

.chipsea-coverage-copy p {
  margin: 0;
  color: #5c6a7a;
  font-size: 13px;
  line-height: 1.68;
}

.chipsea-evidence-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  color: #426148;
  font-size: 10px;
  font-weight: 750;
}

.chipsea-evidence-label--archive_evidence {
  color: #735d32;
}

.chipsea-evidence-label--metadata {
  color: #536376;
}

.chipsea-coverage-list article > a {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
  padding-top: 25px;
  color: #1f56d8;
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
}

.chipsea-evidence-gallery {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 52px;
}

.chipsea-evidence-gallery figure {
  min-width: 0;
  margin: 0;
  border: 1px solid #d9e1eb;
  background: #f5f7fa;
}

.chipsea-evidence-gallery img {
  display: block;
  width: 100%;
  height: 310px;
  object-fit: cover;
  object-position: top;
}

.chipsea-evidence-gallery figcaption {
  padding: 14px;
  color: #4e5f72;
  font-size: 11px;
  font-weight: 750;
}

.chipsea-boundary {
  padding: 0 0 94px;
  background: #fff;
}

.chipsea-boundary > .mkt-shell {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 22px;
  padding: 34px;
  border: 1px solid #c7d8ef;
  background: #f4f8ff;
}

.chipsea-boundary > .mkt-shell > svg {
  color: #1f56d8;
}

.chipsea-boundary h2 {
  font-size: 28px;
}

.chipsea-boundary ul {
  display: grid;
  gap: 10px;
  margin: 22px 0 0;
  padding-left: 18px;
  color: #536376;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 1120px) {
  .chipsea-hero__layout {
    grid-template-columns: minmax(0, 0.92fr) minmax(380px, 1.08fr);
    gap: 42px;
  }

  .chipsea-hero__media {
    min-height: 480px;
  }

  .chipsea-timeline {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .chipsea-work-grid,
  .chipsea-evidence-gallery {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .chipsea-hero {
    padding: 88px 0 60px;
  }

  .chipsea-hero__layout,
  .chipsea-overview__layout,
  .chipsea-split {
    grid-template-columns: 1fr;
    gap: 46px;
  }

  .chipsea-back-link {
    margin-bottom: 34px;
  }

  .chipsea-hero__media {
    min-height: 420px;
  }

  .case-story-nav {
    top: 64px;
  }

  .chipsea-overview,
  .chipsea-section {
    padding: 72px 0;
  }

  .chipsea-event-gallery {
    grid-template-columns: 1fr;
  }

  .chipsea-event-gallery figure,
  .chipsea-event-gallery figure:first-child {
    min-height: 330px;
    grid-row: auto;
  }

  .chipsea-coverage-list article {
    grid-template-columns: 112px minmax(0, 1fr);
  }

  .chipsea-coverage-list article > a {
    grid-column: 2;
    justify-content: flex-start;
    padding-top: 0;
  }
}

@media (max-width: 560px) {
  .chipsea-hero {
    padding: 68px 0 48px;
  }

  .chipsea-hero h1 {
    font-size: 38px;
  }

  .chipsea-hero__copy > p:not(.chipsea-source-note) {
    font-size: 16px;
  }

  .chipsea-hero__media {
    min-height: 270px;
  }

  .chipsea-hero__media figcaption {
    right: 18px;
    bottom: 16px;
    left: 18px;
  }

  .case-story-nav > .mkt-shell {
    gap: 25px;
  }

  .chipsea-facts,
  .chipsea-timeline,
  .chipsea-work-grid,
  .chipsea-result-stats,
  .chipsea-evidence-gallery {
    grid-template-columns: 1fr;
  }

  .chipsea-timeline > li,
  .chipsea-work-grid article,
  .chipsea-result-stats > div {
    min-height: auto;
  }

  .chipsea-timeline__body > span {
    margin-top: 28px;
  }

  .chipsea-timeline h3,
  .chipsea-timeline p {
    min-height: auto;
  }

  .chipsea-event-gallery figure,
  .chipsea-event-gallery figure:first-child {
    min-height: 0;
    aspect-ratio: 4 / 3;
  }

  .chipsea-coverage-list article {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .chipsea-coverage-list article > a {
    grid-column: 1;
  }

  .chipsea-evidence-gallery img {
    height: auto;
    max-height: 430px;
  }

  .chipsea-boundary > .mkt-shell {
    grid-template-columns: 1fr;
    padding: 24px;
  }
}
</style>
