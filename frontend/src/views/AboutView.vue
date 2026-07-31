<script setup lang="ts">
import { ArrowRight, CheckCircle2 } from 'lucide-vue-next'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import '@/assets/marketing.css'
import '@/assets/marketing-home.css'

const milestones = [
  {
    year: '2010',
    title: '财经报道',
    description: '从新闻采访与调查报道出发，把信息来源、事实核对和采访对象放在内容工作之前。',
  },
  {
    year: '2014',
    title: '内容生产',
    description:
      '从自媒体内容聚合与写作服务切入，在企业资料、行业信息和内容表达之间建立更稳定的工作方式。',
  },
  {
    year: '2016',
    title: '媒体数据',
    description: '围绕主流新闻媒体、记者与内容信息，开始以数据辅助媒体关系管理和候选筛选。',
  },
  {
    year: '2021',
    title: '新闻手册',
    description:
      '把新闻价值判断、结构化采访和新闻手册用于发布会项目，为采访与写作准备可核验的基础材料。',
  },
  {
    year: '现在',
    title: '云发布',
    description:
      '现场采写、媒体邀请、直编发稿和新闻发布会作为四项独立服务，在同一活动下形成可关联查看的项目记录。',
  },
]

const principles = [
  {
    number: '01',
    title: '事实核验',
    description: '先核对事实材料、来源和可公开信息，再讨论稿件与传播安排。',
  },
  {
    number: '02',
    title: '材料准备',
    description: '将活动背景、新闻点与采访要点整理成适合不同角色使用的沟通材料。',
  },
  {
    number: '03',
    title: '媒体独立',
    description: '写手与项目团队负责准备和沟通；媒体是否采访或报道，始终由媒体独立判断。',
  },
]
</script>

<template>
  <div class="mkt-page about-page">
    <SiteHeader />
    <main>
      <section class="about-hero">
        <div class="mkt-shell">
          <span class="mkt-eyebrow">关于云发布</span>
          <h1>从事实，到报道</h1>
          <p>
            云发布源于一条从新闻采访走向企业传播的业务线：从事实材料开始，经过内容生产、媒体沟通和项目执行，让一项传播工作有据可查。
          </p>
        </div>
      </section>

      <section class="home-section home-section-white about-history-section">
        <div class="mkt-shell">
          <div class="about-section-heading">
            <div>
              <span class="mkt-eyebrow">发展脉络</span>
              <h2>业务沿革</h2>
            </div>
            <p>
              麦媒的实践从内容生产切入，随后延伸至媒体数据、发布会组织与项目化交付。云发布把这些方法放进一项项可以查询和协同的传播项目。
            </p>
          </div>

          <ol class="about-timeline">
            <li v-for="milestone in milestones" :key="milestone.year">
              <span class="about-timeline-year">{{ milestone.year }}</span>
              <div>
                <h3>{{ milestone.title }}</h3>
                <p>{{ milestone.description }}</p>
              </div>
            </li>
          </ol>
        </div>
      </section>

      <section class="home-section home-section-soft">
        <div class="mkt-shell about-grid">
          <article class="about-positioning">
            <span class="mkt-eyebrow">今天的云发布</span>
            <h2>企业新闻传播项目操作台</h2>
            <p>
              面向企业市场与品牌团队，云发布用于管理现场采写、媒体邀请、直编发稿和新闻发布会。四项服务分别建单、分别履约；同一活动下的关联任务可以一起查看。
            </p>
            <RouterLink class="about-inline-link" to="/#solutions">
              查看服务方案 <ArrowRight :size="16" />
            </RouterLink>
          </article>

          <article class="about-work-list">
            <small>项目记录</small>
            <ul>
              <li><CheckCircle2 :size="18" />稿件版本、修改记录与最终定稿。</li>
              <li><CheckCircle2 :size="18" />拟邀媒体、联系人与邀请推进记录。</li>
              <li><CheckCircle2 :size="18" />渠道计划、提交状态与可核验的发布结果。</li>
              <li><CheckCircle2 :size="18" />会前、现场、会后的任务安排与确认记录。</li>
            </ul>
          </article>
        </div>
      </section>

      <section class="home-section home-section-white about-principles-section">
        <div class="mkt-shell">
          <div class="about-section-heading about-section-heading-compact">
            <div>
              <span class="mkt-eyebrow">工作原则</span>
              <h2>工作方法</h2>
            </div>
          </div>

          <div class="about-principles">
            <article v-for="principle in principles" :key="principle.number">
              <span>{{ principle.number }}</span>
              <h3>{{ principle.title }}</h3>
              <p>{{ principle.description }}</p>
            </article>
          </div>
        </div>
      </section>

      <section class="about-cta">
        <div class="mkt-shell">
          <div>
            <small>传播项目</small>
            <h2>发起项目</h2>
            <p>先选择需要的服务并留下联系人，其余资料可以随着项目推进逐步补齐。</p>
          </div>
          <RouterLink class="mkt-button mkt-button-light" to="/requirements/new">
            提交服务需求 <ArrowRight :size="17" />
          </RouterLink>
        </div>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.about-hero {
  padding: 144px 0 108px;
  color: #fff;
  background:
    radial-gradient(circle at 82% 28%, rgba(65, 129, 220, 0.27), transparent 28rem),
    linear-gradient(120deg, #111923 0%, #172b43 100%);
}

.about-hero .mkt-shell {
  max-width: 960px;
  margin-left: max(24px, calc((100vw - 1240px) / 2));
}

.about-hero h1 {
  max-width: 840px;
  margin: 22px 0;
  color: #f8fbff;
  font-size: clamp(44px, 5.5vw, 76px);
  line-height: 1.08;
  letter-spacing: -0.055em;
}

.about-hero p {
  max-width: 760px;
  color: #d7e1ec;
  font-size: 18px;
  line-height: 1.8;
}

.about-hero .mkt-eyebrow {
  color: #93beff;
}

.about-section-heading {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.72fr);
  align-items: end;
  gap: 56px;
  margin-bottom: 52px;
}

.about-section-heading h2,
.about-grid h2,
.about-cta h2 {
  margin: 16px 0 0;
  color: #111a24;
  font-size: clamp(32px, 3.4vw, 48px);
  line-height: 1.2;
  letter-spacing: -0.045em;
}

.about-section-heading > p {
  margin: 0;
  color: #647186;
  font-size: 15px;
  line-height: 1.85;
}

.about-timeline {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  padding: 0;
  margin: 0;
  list-style: none;
  border-top: 1px solid #ccd5df;
}

.about-timeline li {
  position: relative;
  min-height: 268px;
  padding: 30px 30px 0 0;
  border-right: 1px solid #e2e7ed;
}

.about-timeline li:not(:first-child) {
  padding-left: 30px;
}

.about-timeline li:last-child {
  border-right: 0;
}

.about-timeline li::before {
  position: absolute;
  top: -5px;
  width: 9px;
  height: 9px;
  content: '';
  background: #2761b5;
  border-radius: 50%;
}

.about-timeline-year {
  display: block;
  margin-bottom: 52px;
  color: #2761b5;
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.06em;
}

.about-timeline h3,
.about-principles h3 {
  margin: 0 0 13px;
  color: #172030;
  font-size: 21px;
  letter-spacing: -0.025em;
}

.about-timeline p,
.about-grid p,
.about-grid li,
.about-principles p {
  color: #536175;
  font-size: 15px;
  line-height: 1.8;
}

.about-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(340px, 0.92fr);
  gap: 90px;
  align-items: center;
}

.about-positioning > p {
  max-width: 650px;
  margin: 24px 0;
}

.about-inline-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #1d55a9;
  font-size: 14px;
  font-weight: 750;
  text-decoration: none;
}

.about-inline-link:hover {
  color: #133f80;
}

.about-work-list {
  padding: 32px 34px;
  background: #fff;
  border: 1px solid #e0e6ee;
  border-radius: 4px;
  box-shadow: 0 18px 40px rgba(24, 44, 69, 0.06);
}

.about-work-list small,
.about-cta small {
  color: #2759a5;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.about-work-list ul {
  display: grid;
  gap: 17px;
  padding: 0;
  margin: 24px 0 0;
  list-style: none;
}

.about-work-list li {
  display: grid;
  grid-template-columns: 22px 1fr;
  gap: 10px;
}

.about-principles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  background: #d8dee6;
}

.about-principles article {
  min-height: 240px;
  padding: 34px;
  background: #f7f9fb;
}

.about-principles span {
  color: #2759a5;
  font-size: 12px;
  font-weight: 850;
}

.about-principles h3 {
  margin-top: 50px;
}

.about-cta {
  padding: 84px 0;
  color: #fff;
  background: #2359a8;
}

.about-cta .mkt-shell {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 40px;
}

.about-cta small,
.about-cta h2 {
  color: #fff;
}

.about-cta h2 {
  max-width: 670px;
}

.about-cta p {
  margin-bottom: 0;
  color: #dfe9f7;
}

@media (max-width: 1180px) and (min-width: 981px) {
  .about-timeline {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .about-timeline li:nth-child(3) {
    border-right: 0;
  }

  .about-timeline li:nth-child(n + 4) {
    padding-top: 44px;
    border-top: 1px solid #e2e7ed;
  }

  .about-timeline li:nth-child(4) {
    padding-left: 0;
  }
}

@media (max-width: 980px) {
  .about-timeline {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .about-timeline li:nth-child(even) {
    border-right: 0;
  }

  .about-timeline li:nth-child(n + 3) {
    padding-top: 44px;
    border-top: 1px solid #e2e7ed;
  }

  .about-timeline li:nth-child(odd) {
    padding-left: 0;
  }
}

@media (max-width: 780px) {
  .about-hero {
    padding: 112px 0 76px;
  }

  .about-hero .mkt-shell {
    margin: 0 auto;
  }

  .about-hero p {
    font-size: 16px;
  }

  .about-section-heading,
  .about-grid,
  .about-principles {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .about-section-heading {
    margin-bottom: 36px;
  }

  .about-timeline {
    grid-template-columns: 1fr;
  }

  .about-timeline li,
  .about-timeline li:not(:first-child),
  .about-timeline li:nth-child(n + 3) {
    min-height: 0;
    padding: 28px 0 32px;
    border-top: 1px solid #e2e7ed;
    border-right: 0;
  }

  .about-timeline li:first-child {
    border-top: 0;
  }

  .about-timeline-year {
    margin-bottom: 22px;
  }

  .about-work-list,
  .about-principles article {
    padding: 28px 24px;
  }

  .about-principles h3 {
    margin-top: 38px;
  }

  .about-cta .mkt-shell {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
