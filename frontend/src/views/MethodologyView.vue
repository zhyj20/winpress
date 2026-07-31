<script setup lang="ts">
import {
  ArrowRight,
  CalendarCheck2,
  ClipboardCheck,
  ExternalLink,
  FilePenLine,
  ListChecks,
  Newspaper,
  Send,
  UsersRound,
} from 'lucide-vue-next'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import '@/assets/marketing.css'
import '@/assets/marketing-editorial.css'

const phases = [
  {
    index: '01',
    title: '会前准备',
    text: '确认新闻点、活动资料、媒体方向、会场动线与负责人。',
    icon: ClipboardCheck,
  },
  {
    index: '02',
    title: '现场执行',
    text: '记录签到、采访、采写、临时调整和待补资料。',
    icon: UsersRound,
  },
  {
    index: '03',
    title: '会后归档',
    text: '核对稿件版本、邀请反馈、发布链接与未结事项。',
    icon: CalendarCheck2,
  },
]

const serviceBoundaries = [
  {
    title: '现场采写',
    text: '写手完成资料梳理、现场采写、撰写与修改。',
    note: '不承担媒体邀请，不决定媒体是否报道。',
    icon: FilePenLine,
  },
  {
    title: '媒体邀请',
    text: '从媒体方向到具体记者逐项筛选，实际沟通后记录反馈。',
    note: '媒体是否到场、采访或报道由媒体自行决定。',
    icon: Newspaper,
  },
  {
    title: '直编发稿',
    text: '使用客户定稿筛选渠道、保存计划并提交项目核验。',
    note: '渠道、价格、稿件要求和排期以核验结果为准。',
    icon: Send,
  },
  {
    title: '新闻发布会',
    text: '管理发布会资料、媒体名单和会前、现场、会后清单。',
    note: '需要其他服务时分别立项、分别计价、关联查看。',
    icon: ListChecks,
  },
]

const contentMaterials = [
  {
    title: '事实底稿',
    text: '活动信息、人物身份、产品数据和合作事项先由客户确认，后续稿件共用同一事实版本。',
  },
  {
    title: '报道角度',
    text: '按行业、地区和媒体线口拆分选题，让不同媒体找到与其读者相关的切入点。',
  },
  {
    title: '采访准备',
    text: '发言人资料、采访提纲、背景文件和问答提前整理，现场更新另留版本记录。',
  },
]
</script>

<template>
  <div class="mkt-page editorial-page methodology-page">
    <SiteHeader />
    <main>
      <section class="editorial-hero">
        <div class="mkt-shell">
          <span class="mkt-eyebrow">方法论</span>
          <h1>发布会方法论</h1>
          <p>把议题、资料、媒体名单和执行节点放进同一张项目清单，明确负责人和执行顺序。</p>
        </div>
      </section>

      <section class="editorial-section">
        <div class="mkt-shell">
          <div class="editorial-section-heading editorial-heading-row">
            <div>
              <span class="mkt-eyebrow">内容准备</span>
              <h2>稿件组合</h2>
            </div>
            <p>同一场活动需要统一事实，也需要为不同媒体准备不同层次的材料。</p>
          </div>
          <div class="method-content-grid">
            <article v-for="item in contentMaterials" :key="item.title">
              <h3>{{ item.title }}</h3>
              <p>{{ item.text }}</p>
            </article>
          </div>
          <a
            class="method-source-link"
            href="https://old.winpress.cn/article/20"
            target="_blank"
            rel="noopener noreferrer"
          >
            旧站文章：“帮助记者寻找不同的新闻角度”<ExternalLink :size="15" />
          </a>
        </div>
      </section>

      <section class="editorial-section">
        <div class="mkt-shell editorial-two-column">
          <div class="editorial-section-heading">
            <span class="mkt-eyebrow">立项标准</span>
            <h2>三项基本资料</h2>
            <p>
              发布会标题、联系人和联系电话用于建立项目。时间、地点、议程和媒体方向可在后续补充。
            </p>
          </div>
          <div class="method-principles">
            <article>
              <strong>01</strong>
              <div>
                <h3>先建项目</h3>
                <p>资料不齐时也能开始，缺项进入待办，不阻断立项。</p>
              </div>
            </article>
            <article>
              <strong>02</strong>
              <div>
                <h3>再核名单</h3>
                <p>按议题、行业与地区筛选媒体，再到具体媒体内确认记者。</p>
              </div>
            </article>
            <article>
              <strong>03</strong>
              <div>
                <h3>逐项执行</h3>
                <p>每项工作保留负责人、截止时间、状态和项目记录。</p>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section class="editorial-section editorial-section-soft">
        <div class="mkt-shell">
          <div class="editorial-section-heading editorial-heading-row">
            <div>
              <span class="mkt-eyebrow">执行清单</span>
              <h2>三个阶段</h2>
            </div>
            <p>项目日期可导出为日历文件；没有确认的日期不会被系统自行补写。</p>
          </div>
          <div class="method-phase-grid">
            <article v-for="phase in phases" :key="phase.index">
              <div>
                <component :is="phase.icon" :size="22" /><span>{{ phase.index }}</span>
              </div>
              <h3>{{ phase.title }}</h3>
              <p>{{ phase.text }}</p>
            </article>
          </div>
        </div>
      </section>

      <section class="editorial-section">
        <div class="mkt-shell">
          <div class="editorial-section-heading editorial-heading-row">
            <div>
              <span class="mkt-eyebrow">服务边界</span>
              <h2>四项独立服务</h2>
            </div>
            <p>一场活动可关联多个项目，但需求、任务、费用和交付始终分别确认。</p>
          </div>
          <div class="method-service-list">
            <article v-for="service in serviceBoundaries" :key="service.title">
              <component :is="service.icon" :size="22" />
              <div>
                <h3>{{ service.title }}</h3>
                <p>{{ service.text }}</p>
                <small>{{ service.note }}</small>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section class="editorial-cta">
        <div class="mkt-shell">
          <div>
            <span>项目入口</span>
            <h2>举办新闻发布会</h2>
            <p>先提交三项基本资料，随后在项目工作台补充媒体名单和执行安排。</p>
          </div>
          <RouterLink class="mkt-button mkt-button-light" to="/requirements/news-conference">
            创建发布会项目<ArrowRight :size="17" />
          </RouterLink>
        </div>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>
