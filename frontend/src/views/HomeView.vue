<script setup lang="ts">
import {
  ArrowRight,
  CheckCircle2,
  CircleCheck,
  ExternalLink,
  Presentation,
  ShieldCheck,
} from 'lucide-vue-next'
import CaseCard from '@/components/marketing/CaseCard.vue'
import ChannelCard from '@/components/marketing/ChannelCard.vue'
import MediaDiscoveryCard from '@/components/marketing/MediaDiscoveryCard.vue'
import NewsFlowVisual from '@/components/marketing/NewsFlowVisual.vue'
import ProjectDashboardPreview from '@/components/marketing/ProjectDashboardPreview.vue'
import SectionHeading from '@/components/marketing/SectionHeading.vue'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import { channelPaths, insightArticles, taskCases, taskEntries } from '@/marketing/content/home'
import '@/assets/marketing.css'
import '@/assets/marketing-home.css'
</script>

<template>
  <div class="mkt-page home-page">
    <SiteHeader />
    <main>
      <section class="home-hero">
        <div class="mkt-shell home-hero-inner">
          <div class="home-hero-copy">
            <span class="mkt-eyebrow">企业新闻传播</span>
            <h1><span>让品牌的重要声音，</span><span>被专业媒体报道</span></h1>
            <p>
              围绕发布会、产品发布和企业重大事件，完成现场采写、媒体邀请、渠道发布与会后归档；每项工作单独确认，全程可查。
            </p>
            <div class="mkt-actions">
              <RouterLink class="mkt-button mkt-button-primary" to="/requirements/new">
                创建传播项目<ArrowRight :size="17" />
              </RouterLink>
              <a class="mkt-button mkt-button-secondary" href="#services">查看服务能力</a>
            </div>
            <ul class="home-hero-trust" aria-label="项目管理说明">
              <li><CheckCircle2 :size="16" />发布前核对议题、材料与媒体名单。</li>
              <li><CheckCircle2 :size="16" />执行中记录沟通、排期与项目进度。</li>
              <li><CheckCircle2 :size="16" />交付以定稿、确认名单和核验链接为准。</li>
            </ul>
          </div>
          <NewsFlowVisual />
        </div>
      </section>

      <section id="solutions" class="home-section home-section-white home-entry-section">
        <div class="mkt-shell">
          <SectionHeading
            eyebrow="服务入口"
            title="四项传播服务"
            description="按项目需要选择服务。一次活动可以建立多个关联项目，但任务、费用和交付分别确认。"
          />
          <div class="home-entry-grid">
            <article v-for="item in taskEntries" :key="item.index" class="home-entry-card">
              <div class="home-entry-card-head">
                <span>{{ item.index }}</span>
                <component :is="item.icon" :size="21" />
              </div>
              <small>{{ item.label }}</small>
              <h3>{{ item.title }}</h3>
              <p>{{ item.description }}</p>
              <RouterLink :to="item.to">{{ item.action }}<ArrowRight :size="16" /></RouterLink>
            </article>
          </div>
        </div>
      </section>

      <section id="workbench" class="home-section home-section-ink">
        <div class="mkt-shell home-conference-layout home-workbench-layout">
          <div class="home-conference-copy">
            <span class="mkt-eyebrow">项目中枢</span>
            <h2>传播项目管理</h2>
            <p>
              把活动拆成可执行项目。稿件、名单、排期和结果都在操作台确认，团队不用在聊天记录和表格之间反复对账。
            </p>
            <ul class="home-workbench-points">
              <li><CircleCheck :size="17" />关键动作先确认再执行。</li>
              <li><CircleCheck :size="17" />每次修改保留版本和责任人。</li>
              <li><CircleCheck :size="17" />待确认、执行中和已完成一目了然。</li>
            </ul>
            <RouterLink class="mkt-text-link" to="/login">
              进入传播看板<ArrowRight :size="16" />
            </RouterLink>
          </div>
          <div class="home-conference-preview">
            <span class="home-preview-kicker"><Presentation :size="16" />项目工作台</span>
            <ProjectDashboardPreview />
          </div>
        </div>
      </section>

      <section id="services" class="home-section home-section-white">
        <div class="mkt-shell">
          <SectionHeading
            eyebrow="服务能力"
            title="专业传播服务"
            description="内容、媒体、渠道和活动执行各有明确边界；需要组合使用时，在同一活动下关联查看。"
          />
          <div class="home-channel-grid home-service-grid">
            <ChannelCard v-for="item in channelPaths" :key="item.label" v-bind="item" />
          </div>
        </div>
      </section>

      <section id="media-insights" class="home-section home-section-paper home-insights-section">
        <div class="mkt-shell">
          <SectionHeading
            eyebrow="行业资讯"
            title="发布会文章选读"
            description="从旧站公开文章中选取仍适用的内容策划、媒体沟通与现场执行经验。"
          />
          <div class="home-insight-layout">
            <blockquote>
              <span>连续发布</span>
              <p>“同理不同样”</p>
              <small>核心口径保持一致，具体选题、案例与媒体沟通按城市、行业和媒体线口调整。</small>
              <a
                href="https://old.winpress.cn/article/23"
                target="_blank"
                rel="noopener noreferrer"
              >
                阅读原文<ExternalLink :size="15" />
              </a>
            </blockquote>
            <div class="home-insight-grid">
              <article v-for="article in insightArticles" :key="article.sourceUrl">
                <small>{{ article.category }}</small>
                <q>{{ article.quote }}</q>
                <h3>{{ article.title }}</h3>
                <p>{{ article.summary }}</p>
                <a :href="article.sourceUrl" target="_blank" rel="noopener noreferrer">
                  {{ article.sourceLabel }}<ExternalLink :size="14" />
                </a>
              </article>
            </div>
          </div>
          <div class="home-insight-foot">
            <small
              >短引文来自云发布旧站公开文章。旧文中的客户、数量与传播结果不作为当前服务承诺，仍以原始项目资料和授权为准。</small
            >
            <RouterLink class="mkt-text-link" to="/insights">
              查看全部文章<ArrowRight :size="16" />
            </RouterLink>
          </div>
        </div>
      </section>

      <section id="media-resources" class="home-section home-section-soft">
        <span
          id="media"
          aria-hidden="true"
          style="position: absolute; left: 0; top: 0; width: 0; height: 0; overflow: hidden"
        ></span>
        <div class="mkt-shell home-media-layout">
          <span class="mkt-eyebrow">媒体筛选</span>
          <h2>媒体筛选</h2>
          <p class="mkt-match-caption">要更多媒体，更要对线口。</p>
          <p>
            按行业、领域、媒体属性、地区和预算形成待核验的传播方向；实际可用名单、报价与排期以项目确认结果为准。
          </p>
          <ul>
            <li><CircleCheck :size="17" />先按行业和议题整理候选方向，再过滤地区与标签。</li>
            <li><CircleCheck :size="17" />媒体类别与发布形式可作为项目筛选条件。</li>
            <li><CircleCheck :size="17" />未接入实时媒体数据时，平台会先核验后再执行。</li>
          </ul>
          <RouterLink class="mkt-button mkt-button-secondary" to="/direct-publishing">
            查看媒体筛选<ArrowRight :size="17" />
          </RouterLink>
        </div>
        <MediaDiscoveryCard />
      </section>

      <section id="methodology" class="home-section home-section-white">
        <span
          id="conference"
          aria-hidden="true"
          style="position: absolute; left: 0; top: 0; width: 0; height: 0; overflow: hidden"
        ></span>
        <div class="mkt-shell home-conference-layout">
          <div class="home-conference-preview home-conference-project-card">
            <span class="home-preview-kicker"><Presentation :size="16" />方法论</span>
            <div class="home-conference-facts">
              <div><strong>前置</strong><span>议题确认、素材准备、任务拆分。</span></div>
              <div><strong>执行</strong><span>媒体邀请、稿件提交、现场对接、反馈收口。</span></div>
              <div><strong>结果</strong><span>核验发布链接与客户可查的项目记录。</span></div>
            </div>
          </div>
          <div class="home-conference-copy">
            <span class="mkt-eyebrow">方法论</span>
            <h2>发布会项目管理</h2>
            <p>
              从议题、资料、媒体动线到会后链接，按会前、现场和会后清单推进。需要采写、媒体邀请或直编发稿时，再分别建立服务项目。
            </p>
            <RouterLink class="mkt-text-link" to="/requirements/news-conference">
              立即发起发布会项目<ArrowRight :size="16" />
            </RouterLink>
          </div>
        </div>
      </section>

      <section id="cases" class="home-section home-section-soft">
        <div class="mkt-shell">
          <SectionHeading
            eyebrow="案例展示"
            title="项目示例"
            description="以下为服务流程示例，用于说明项目怎样推进，不作为未经授权的客户业绩。"
          />
          <div class="home-case-grid">
            <CaseCard v-for="item in taskCases" :key="item.category" v-bind="item" />
          </div>
        </div>
      </section>

      <section class="home-final-cta">
        <div class="mkt-shell home-final-cta-inner">
          <div>
            <span>传播推进</span>
            <h2 class="home-final-cta-title">重要活动宣传，就找云发布</h2>
            <p>提交标题和联系方式即可建立项目，其他资料可在项目推进中补充。</p>
          </div>
          <div class="mkt-actions">
            <RouterLink class="mkt-button mkt-button-light" to="/requirements/new">
              创建传播项目<ArrowRight :size="17" />
            </RouterLink>
            <RouterLink class="mkt-button home-button-dark" to="/direct-publishing"
              >查看媒体筛选</RouterLink
            >
          </div>
          <small><ShieldCheck :size="15" />内容采写、媒体邀请和渠道发布分别立项、分别执行。</small>
        </div>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>
