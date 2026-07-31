<script setup lang="ts">
import { ArrowLeft, CalendarDays, FileArchive, Link2, ShieldCheck } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import {
  historicalReportGroups,
  historicalReportRecords,
  reportsForCategory,
  type HistoricalReportCategory,
} from '@/marketing/content/historicalReports'
import '@/assets/marketing.css'
import '@/assets/marketing-editorial.css'

type FilterKey = 'ALL' | HistoricalReportCategory

const route = useRoute()
const requestedType = route.query.type
const selected = ref<FilterKey>(
  requestedType === 'EVENT_CAMPAIGN' || requestedType === 'DIRECT_PUBLISHING'
    ? requestedType
    : 'ALL',
)

const visibleGroups = computed(() =>
  historicalReportGroups.filter(
    (group) => selected.value === 'ALL' || group.key === selected.value,
  ),
)

function recordCount(category: HistoricalReportCategory) {
  return reportsForCategory(category).length
}
</script>

<template>
  <div class="mkt-page editorial-page historical-reports-page">
    <SiteHeader />
    <main>
      <section class="historical-reports-hero">
        <div class="mkt-shell">
          <RouterLink class="historical-back" to="/cases">
            <ArrowLeft :size="16" />返回案例展示
          </RouterLink>
          <span class="mkt-eyebrow">历史传播资料</span>
          <h1>传播汇报归集</h1>
          <p>按活动传播和直编发稿两条路径整理历史汇报，便于回看项目类型与交付结构。</p>
          <div class="historical-reports-summary" aria-label="资料归集概览">
            <div>
              <strong>{{ historicalReportRecords.length }}</strong>
              <span>已归集资料</span>
            </div>
            <div>
              <strong>{{ recordCount('EVENT_CAMPAIGN') }}</strong>
              <span>活动传播</span>
            </div>
            <div>
              <strong>{{ recordCount('DIRECT_PUBLISHING') }}</strong>
              <span>直编发稿</span>
            </div>
            <div>
              <strong>2021—2026</strong>
              <span>资料覆盖期</span>
            </div>
          </div>
        </div>
      </section>

      <section class="historical-reports-body">
        <div class="mkt-shell">
          <div class="historical-reports-toolbar">
            <div role="group" aria-label="传播类型筛选" class="historical-report-filters">
              <button
                type="button"
                :class="{ active: selected === 'ALL' }"
                :aria-pressed="selected === 'ALL'"
                @click="selected = 'ALL'"
              >
                全部 {{ historicalReportRecords.length }}
              </button>
              <button
                v-for="group in historicalReportGroups"
                :key="group.key"
                type="button"
                :class="{ active: selected === group.key }"
                :aria-pressed="selected === group.key"
                @click="selected = group.key"
              >
                {{ group.label }} {{ recordCount(group.key) }}
              </button>
            </div>
            <p>原始汇报只作为核验依据保留，不在网站公开下载。</p>
          </div>

          <section v-for="group in visibleGroups" :key="group.key" class="historical-report-group">
            <div class="historical-report-group-heading">
              <div>
                <span class="mkt-eyebrow">{{ group.label }}</span>
                <h2>{{ group.title }}</h2>
              </div>
              <p>{{ group.description }}</p>
            </div>

            <div class="historical-report-grid">
              <article
                v-for="record in reportsForCategory(group.key)"
                :key="record.id"
                class="historical-report-card"
              >
                <div class="historical-report-card__top">
                  <span>{{ record.sourceMaterial }}</span>
                  <FileArchive :size="18" aria-hidden="true" />
                </div>
                <h3>{{ record.title }}</h3>
                <p>{{ record.scope }}</p>
                <dl>
                  <div>
                    <dt><CalendarDays :size="14" />归档时间</dt>
                    <dd>{{ record.period }}</dd>
                  </div>
                  <div>
                    <dt><FileArchive :size="14" />材料类型</dt>
                    <dd>{{ record.format }}</dd>
                  </div>
                  <div v-if="record.recordCount">
                    <dt><Link2 :size="14" />汇报记录</dt>
                    <dd>{{ record.recordCount }} 条链接记录</dd>
                  </div>
                </dl>
                <small>资料已归集，实名公开范围与结果口径待逐项核验。</small>
              </article>
            </div>
          </section>
        </div>
      </section>

      <section class="historical-report-boundary">
        <div class="mkt-shell">
          <ShieldCheck :size="24" />
          <div>
            <span class="mkt-eyebrow">展示边界</span>
            <h2>看项目结构，不公开项目底稿</h2>
            <p>
              页面不提供原始表格、汇报案、媒体链接、渠道价格、联系人、媒体名单或供应商信息。后续如取得书面授权，再按单个项目补充实名案例与核验说明。
            </p>
          </div>
        </div>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.historical-reports-hero {
  padding: 72px 0 56px;
  background: linear-gradient(135deg, #f3f7fc 0%, #fff 58%, #eef4fa 100%);
  border-bottom: 1px solid #dae4ef;
}

.historical-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 34px;
  color: #365a7e;
  font-size: 14px;
  font-weight: 700;
}

.historical-back:hover {
  color: #1258b5;
}

.historical-reports-hero h1 {
  margin: 12px 0 14px;
  color: #13233a;
  font-size: clamp(38px, 5.3vw, 68px);
  letter-spacing: -0.055em;
  line-height: 1.05;
}

.historical-reports-hero > .mkt-shell > p {
  max-width: 620px;
  margin: 0;
  color: #52657a;
  font-size: 17px;
  line-height: 1.8;
}

.historical-reports-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  margin-top: 44px;
  overflow: hidden;
  border: 1px solid #d6e0eb;
  border-radius: 10px;
  background: #d6e0eb;
}

.historical-reports-summary > div {
  min-width: 0;
  padding: 19px 22px;
  background: rgba(255, 255, 255, 0.86);
}

.historical-reports-summary strong,
.historical-reports-summary span {
  display: block;
}

.historical-reports-summary strong {
  color: #17365f;
  font-size: 24px;
  letter-spacing: -0.04em;
}

.historical-reports-summary span {
  margin-top: 5px;
  color: #64758a;
  font-size: 12px;
}

.historical-reports-body {
  padding: 72px 0 96px;
}

.historical-reports-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 54px;
  padding-bottom: 18px;
  border-bottom: 1px solid #dce5ef;
}

.historical-reports-toolbar p {
  max-width: 410px;
  margin: 0;
  color: #617184;
  font-size: 13px;
  line-height: 1.65;
  text-align: right;
}

.historical-report-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.historical-report-filters button {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid #cbd7e4;
  border-radius: 6px;
  background: #fff;
  color: #42536a;
  font: inherit;
  font-size: 13px;
  font-weight: 750;
  cursor: pointer;
}

.historical-report-filters button:hover,
.historical-report-filters button.active {
  border-color: #1f5cae;
  background: #1f5cae;
  color: #fff;
}

.historical-report-group + .historical-report-group {
  margin-top: 74px;
}

.historical-report-group-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 30px;
  margin-bottom: 24px;
}

.historical-report-group-heading h2 {
  margin: 9px 0 0;
  color: #18283d;
  font-size: clamp(26px, 3.1vw, 38px);
  letter-spacing: -0.04em;
}

.historical-report-group-heading p {
  max-width: 410px;
  margin: 0;
  color: #637388;
  font-size: 14px;
  line-height: 1.7;
}

.historical-report-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.historical-report-card {
  display: flex;
  min-width: 0;
  min-height: 286px;
  flex-direction: column;
  padding: 24px;
  border: 1px solid #d9e2ec;
  border-radius: 10px;
  background: #fff;
}

.historical-report-card:hover {
  border-color: #a9bfdb;
  box-shadow: 0 12px 28px rgba(28, 66, 112, 0.08);
}

.historical-report-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: #356795;
}

.historical-report-card__top span {
  min-width: 0;
  overflow: hidden;
  color: #356795;
  font-size: 12px;
  font-weight: 780;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.historical-report-card h3 {
  margin: 26px 0 11px;
  color: #18273a;
  font-size: 21px;
  line-height: 1.36;
  letter-spacing: -0.025em;
}

.historical-report-card > p {
  min-height: 47px;
  margin: 0;
  color: #617186;
  font-size: 13px;
  line-height: 1.68;
}

.historical-report-card dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px 16px;
  margin: 24px 0 18px;
}

.historical-report-card dl > div:last-child:nth-child(odd) {
  grid-column: 1 / -1;
}

.historical-report-card dt {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #78879a;
  font-size: 11px;
  font-weight: 750;
}

.historical-report-card dd {
  margin: 5px 0 0;
  color: #263c59;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.45;
}

.historical-report-card small {
  margin-top: auto;
  padding-top: 14px;
  border-top: 1px solid #e4eaf1;
  color: #738195;
  font-size: 11px;
  line-height: 1.65;
}

.historical-report-boundary {
  padding: 42px 0;
  border-top: 1px solid #d9e2eb;
  background: #f5f8fb;
}

.historical-report-boundary > .mkt-shell {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.historical-report-boundary svg {
  flex: 0 0 auto;
  margin-top: 3px;
  color: #23578d;
}

.historical-report-boundary h2 {
  margin: 8px 0;
  color: #1b2d45;
  font-size: 23px;
}

.historical-report-boundary p {
  max-width: 850px;
  margin: 0;
  color: #5d6e82;
  font-size: 14px;
  line-height: 1.75;
}

@media (max-width: 980px) {
  .historical-report-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .historical-reports-hero,
  .historical-reports-body {
    padding-top: 48px;
  }

  .historical-reports-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-top: 32px;
  }

  .historical-reports-toolbar,
  .historical-report-group-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .historical-reports-toolbar p {
    text-align: left;
  }
}

@media (max-width: 520px) {
  .historical-reports-summary > div {
    padding: 16px;
  }

  .historical-report-grid {
    grid-template-columns: 1fr;
  }

  .historical-report-card {
    min-height: 0;
    padding: 21px;
  }

  .historical-report-card h3 {
    margin-top: 22px;
  }
}
</style>
