<script setup lang="ts">
import {
  ArrowRight,
  ExternalLink,
  FileCheck2,
  ListChecks,
  Newspaper,
  UsersRound,
} from 'lucide-vue-next'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import { insightArchive } from '@/marketing/content/home'
import '@/assets/marketing.css'

const reportingMaterials = [
  {
    index: '01',
    title: '事实底稿',
    text: '统一活动时间、地点、人物、产品、合作事项和客户已经确认的口径。',
  },
  {
    index: '02',
    title: '报道角度',
    text: '按行业变化、应用场景、区域特点和受众关切准备不同的报道切口。',
  },
  {
    index: '03',
    title: '采访准备',
    text: '整理发言人资料、采访提纲、背景材料和问答，供媒体进一步核实与采访。',
  },
]
</script>

<template>
  <div class="mkt-page insights-page">
    <SiteHeader />
    <main>
      <section class="insights-hero">
        <div class="mkt-shell">
          <span class="mkt-eyebrow">行业资讯</span>
          <h1>行业资讯</h1>
          <p>发布会实务、报道准备和旧站公开文章整理，供项目立项与执行参考。</p>
        </div>
      </section>

      <section class="insights-practice">
        <div class="mkt-shell">
          <div class="insights-practice-head">
            <span class="mkt-eyebrow">工作方法</span>
            <h2>传播准备</h2>
            <p>把新闻角度、媒体名单、现场安排和会后交付分别准备，执行时才不会互相挤压。</p>
          </div>
          <div class="insights-practice-grid">
            <article>
              <Newspaper :size="21" />
              <h3>新闻角度</h3>
              <p>先确定本次活动值得报道的事实，再按行业、地区和受众拆分选题。</p>
            </article>
            <article>
              <UsersRound :size="21" />
              <h3>媒体名单</h3>
              <p>先选媒体，再在选中的媒体内确认对应记者；名单需要逐项核验。</p>
            </article>
            <article>
              <ListChecks :size="21" />
              <h3>现场清单</h3>
              <p>动线、签到、采访、资料和备用方案分别指定负责人及完成时间。</p>
            </article>
            <article>
              <FileCheck2 :size="21" />
              <h3>会后交付</h3>
              <p>报道链接、稿件版本、沟通结果和项目记录分别归档。</p>
            </article>
          </div>
        </div>
      </section>

      <section class="insights-materials">
        <div class="mkt-shell">
          <div class="insights-materials-heading">
            <span class="mkt-eyebrow">稿件组合</span>
            <h2>报道材料</h2>
            <p>同一场活动不只准备一篇通稿。事实底稿、报道角度和采访资料分别服务不同的采编需要。</p>
          </div>
          <div class="insights-material-grid">
            <article v-for="item in reportingMaterials" :key="item.index">
              <span>{{ item.index }}</span>
              <h3>{{ item.title }}</h3>
              <p>{{ item.text }}</p>
            </article>
          </div>
          <a
            class="insights-source-note"
            href="https://old.winpress.cn/article/20"
            target="_blank"
            rel="noopener noreferrer"
          >
            旧站文章概括为：“帮助记者寻找不同的新闻角度”<ExternalLink :size="15" />
          </a>
        </div>
      </section>

      <section class="insights-archive">
        <div class="mkt-shell">
          <div class="insights-archive-heading">
            <div>
              <span class="mkt-eyebrow">旧站文章</span>
              <h2>项目资料选读</h2>
            </div>
            <p>
              以下短引文和方法摘要来自云发布旧站公开文章。旧文中的客户、数量和传播结果不作为当前服务承诺。
            </p>
          </div>
          <div class="insights-article-grid">
            <article v-for="article in insightArchive" :key="article.sourceUrl">
              <small>{{ article.category }}</small>
              <q>{{ article.quote }}</q>
              <h3>{{ article.title }}</h3>
              <p>{{ article.summary }}</p>
              <a :href="article.sourceUrl" target="_blank" rel="noopener noreferrer">
                {{ article.sourceLabel }}<ExternalLink :size="15" />
              </a>
            </article>
          </div>
        </div>
      </section>

      <section class="insights-quote">
        <div class="mkt-shell">
          <blockquote>
            <span>传播方法</span>
            <p>“同理不同样”</p>
            <small>战略口径保持一致，具体选题、材料和媒体沟通因城市与行业而调整。</small>
          </blockquote>
          <div>
            <span class="mkt-eyebrow">项目应用</span>
            <h2>执行方法</h2>
            <p>
              文章提供判断思路，项目工作台负责执行。创建项目后，议题、名单、时间和交付都进入可追踪任务。
            </p>
            <RouterLink class="mkt-button mkt-button-primary" to="/requirements/news-conference">
              举办新闻发布会<ArrowRight :size="17" />
            </RouterLink>
          </div>
        </div>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.insights-hero {
  padding: 128px 0 92px;
  color: #fff;
  background: #111a24;
}

.insights-hero .mkt-eyebrow {
  color: #78a7ee;
}

.insights-hero h1 {
  margin: 18px 0;
  color: #fff;
  font-size: clamp(48px, 6vw, 78px);
  line-height: 1.06;
  letter-spacing: -0.045em;
}

.insights-hero p {
  max-width: 720px;
  color: #d1dce8;
  font-size: 18px;
  line-height: 1.75;
}

.insights-practice,
.insights-materials,
.insights-archive,
.insights-quote {
  padding: 96px 0;
}

.insights-practice {
  background: #f3f0ea;
}

.insights-practice-head {
  max-width: 760px;
  margin-bottom: 44px;
}

.insights-practice-head h2,
.insights-materials-heading h2,
.insights-archive-heading h2,
.insights-quote h2 {
  margin: 12px 0;
  color: #111a24;
  font-size: clamp(34px, 4vw, 52px);
  line-height: 1.15;
  letter-spacing: -0.035em;
}

.insights-practice-head p,
.insights-materials-heading p,
.insights-archive-heading > p,
.insights-quote > .mkt-shell > div > p {
  color: #596778;
  line-height: 1.75;
}

.insights-practice-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid #c9c5bd;
  border-left: 1px solid #c9c5bd;
  background: #fff;
}

.insights-practice-grid article {
  min-height: 260px;
  padding: 28px;
  border-right: 1px solid #c9c5bd;
  border-bottom: 1px solid #c9c5bd;
}

.insights-practice-grid svg {
  color: #174ea6;
}

.insights-practice-grid h3 {
  margin: 58px 0 12px;
  color: #111a24;
  font-size: 20px;
}

.insights-practice-grid p,
.insights-article-grid p {
  color: #5d6a79;
  font-size: 13px;
  line-height: 1.75;
}

.insights-archive {
  background: #fff;
}

.insights-materials {
  background: #fff;
}

.insights-materials-heading {
  max-width: 760px;
  margin-bottom: 38px;
}

.insights-material-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid #d6dce3;
  border-left: 1px solid #d6dce3;
}

.insights-material-grid article {
  min-height: 230px;
  padding: 28px;
  border-right: 1px solid #d6dce3;
  border-bottom: 1px solid #d6dce3;
}

.insights-material-grid article > span {
  color: #174ea6;
  font-size: 12px;
  font-weight: 850;
}

.insights-material-grid h3 {
  margin: 52px 0 12px;
  color: #111a24;
  font-size: 21px;
}

.insights-material-grid p {
  color: #5d6a79;
  font-size: 13px;
  line-height: 1.75;
}

.insights-source-note {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: 22px;
  color: #174ea6;
  font-size: 13px;
  font-weight: 750;
}

.insights-archive-heading {
  display: grid;
  grid-template-columns: minmax(260px, 0.75fr) minmax(360px, 1fr);
  align-items: end;
  gap: 70px;
  margin-bottom: 44px;
}

.insights-article-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid #d6dce3;
  border-left: 1px solid #d6dce3;
}

.insights-article-grid article {
  min-height: 330px;
  display: flex;
  flex-direction: column;
  padding: 28px;
  border-right: 1px solid #d6dce3;
  border-bottom: 1px solid #d6dce3;
}

.insights-article-grid small {
  color: #174ea6;
  font-size: 11px;
  font-weight: 850;
}

.insights-article-grid q {
  min-height: 46px;
  margin: 40px 0 14px;
  color: #174ea6;
  font-size: 13px;
  font-weight: 750;
  line-height: 1.65;
  text-decoration: none;
}

.insights-article-grid h3 {
  margin: 0 0 14px;
  color: #111a24;
  font-size: 22px;
}

.insights-article-grid a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  color: #174ea6;
  font-size: 12px;
  font-weight: 800;
}

.insights-quote {
  color: #fff;
  background: #17355f;
}

.insights-quote .mkt-eyebrow {
  color: #a9c8ff;
}

.insights-quote > .mkt-shell {
  display: grid;
  grid-template-columns: minmax(260px, 0.85fr) minmax(420px, 1.15fr);
  align-items: center;
  gap: 88px;
}

.insights-quote blockquote {
  padding: 34px;
  margin: 0;
  border: 1px solid rgba(255, 255, 255, 0.24);
}

.insights-quote blockquote span,
.insights-quote blockquote small {
  color: #bdcce1;
}

.insights-quote blockquote p {
  margin: 44px 0 13px;
  color: #fff;
  font-size: clamp(38px, 5vw, 66px);
  font-weight: 800;
  letter-spacing: -0.04em;
}

.insights-quote blockquote small {
  line-height: 1.7;
}

.insights-quote h2 {
  color: #fff;
}

.insights-quote > .mkt-shell > div > p {
  margin-bottom: 24px;
  color: #c5d0df;
}

@media (max-width: 920px) {
  .insights-practice-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .insights-archive-heading,
  .insights-quote > .mkt-shell {
    grid-template-columns: 1fr;
    gap: 34px;
  }

  .insights-article-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .insights-material-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .insights-hero {
    padding: 102px 0 70px;
  }

  .insights-practice,
  .insights-materials,
  .insights-archive,
  .insights-quote {
    padding: 72px 0;
  }

  .insights-practice-grid,
  .insights-material-grid,
  .insights-article-grid {
    grid-template-columns: 1fr;
  }

  .insights-practice-grid article,
  .insights-material-grid article,
  .insights-article-grid article {
    min-height: 240px;
    padding: 23px;
  }
}
</style>
