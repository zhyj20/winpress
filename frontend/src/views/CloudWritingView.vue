<script setup lang="ts">
import {
  ArrowRight,
  CalendarDays,
  Check,
  CircleDollarSign,
  Clock3,
  FileCheck2,
  MapPin,
  Newspaper,
  PenTool,
  ShieldCheck,
  UserRoundCheck,
  UsersRound,
} from 'lucide-vue-next'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import '@/assets/marketing.css'

const steps = [
  ['1', '填写订单', '提交活动日期、地点、议程、事实材料和稿件要求。'],
  ['2', '就近匹配', '平台优先联系活动所在地及周边、档期合适的专业写手。'],
  ['3', '确认接单', '接单前确认到场时间、联系人、交付内容和可能产生的增项。'],
  ['4', '现场采写', '写手到场记录活动进程，采集经客户确认的事实与引语。'],
  ['5', '稿件交付', '按订单约定提交初稿，并依据客户意见完成约定范围内的修改。'],
]

const suitable = [
  '新闻发布会与新品发布',
  '行业论坛、峰会与年会',
  '展会、路演与招商活动',
  '签约、开工与企业重要活动',
]
const included = [
  '专业写手一个工作日的现场服务',
  '活动流程记录与事实素材整理',
  '一篇订单约定稿件的撰写',
  '约定范围内的稿件修改与定稿',
]
const excluded = [
  '跨城交通与住宿',
  '超时、加急或多篇稿件',
  '摄影、摄像与直播',
  '媒体邀请、媒体采访和媒体发布',
]
</script>

<template>
  <div class="mkt-page cw-page">
    <SiteHeader />

    <main>
      <section class="cw-hero">
        <div class="mkt-shell cw-hero-inner">
          <div class="cw-hero-copy">
            <span class="mkt-eyebrow">云采写 · 会议活动现场采写</span>
            <h1>云采写 · 现场采写</h1>
            <p>
              专业写手到场完成事实采集与稿件交付。客户在线下单后，平台依据活动地点、档期、行业经验与稿件要求匹配写手。
            </p>
            <div class="cw-price">
              <small>基础服务价</small><strong><span>¥</span>980</strong><em>/ 人 / 天</em>
            </div>
            <div class="mkt-actions">
              <RouterLink class="mkt-button mkt-button-primary" to="/requirements/cloud-writing"
                >填写采写订单<ArrowRight :size="17" /></RouterLink
              ><RouterLink class="mkt-button mkt-button-secondary" to="/media-invitation"
                >媒体邀请另行提交</RouterLink
              >
            </div>
            <div class="cw-note">
              <ShieldCheck
                :size="18"
              />写手受客户委托完成内容服务，不以媒体记者身份到场，也不决定媒体是否报道或刊发。
            </div>
          </div>
          <div class="cw-order-card">
            <div class="cw-order-head">
              <span><PenTool :size="22" /></span>
              <div><small>下单信息</small><strong>会议活动现场采写</strong></div>
            </div>
            <dl>
              <div>
                <dt><MapPin :size="16" />服务地点</dt>
                <dd>活动所在城市</dd>
              </div>
              <div>
                <dt><CalendarDays :size="16" />服务时间</dt>
                <dd>1 天</dd>
              </div>
              <div>
                <dt><UsersRound :size="16" />写手人数</dt>
                <dd>1 人</dd>
              </div>
              <div>
                <dt><CircleDollarSign :size="16" />基础金额</dt>
                <dd>¥980</dd>
              </div>
            </dl>
            <div class="cw-order-total">
              <span>计价方式</span><strong>¥980 × 人数 × 天数</strong>
            </div>
            <small>媒体邀请和其他增项单独确认，不计入上述基础金额。</small>
          </div>
        </div>
      </section>

      <section class="mkt-section mkt-section-white">
        <div class="mkt-shell">
          <div class="mkt-section-head">
            <span class="mkt-eyebrow">适用场景</span>
            <h2>适用活动</h2>
            <p>
              面向需要现场形成文字内容的会议与企业活动，适合当天记录关键信息并形成新闻稿、活动综述或约定文稿。
            </p>
          </div>
          <div class="cw-scene-grid">
            <article v-for="item in suitable" :key="item">
              <CalendarDays :size="20" /><strong>{{ item }}</strong>
            </article>
          </div>
        </div>
      </section>

      <section class="mkt-section mkt-section-soft">
        <div class="mkt-shell">
          <div class="mkt-section-head mkt-section-head-center">
            <span class="mkt-eyebrow">下单到交付</span>
            <h2>采写流程</h2>
            <p>活动信息越完整，写手匹配、到场安排和稿件交付越准确。</p>
          </div>
          <div class="cw-step-grid">
            <article v-for="step in steps" :key="step[0]">
              <span>{{ step[0] }}</span
              ><strong>{{ step[1] }}</strong>
              <p>{{ step[2] }}</p>
            </article>
          </div>
        </div>
      </section>

      <section class="mkt-section mkt-section-white">
        <div class="mkt-shell cw-scope-grid">
          <article>
            <div class="cw-scope-title is-included">
              <FileCheck2 :size="21" />
              <h2>基础服务</h2>
            </div>
            <ul>
              <li v-for="item in included" :key="item"><Check :size="17" />{{ item }}</li>
            </ul>
          </article>
          <article>
            <div class="cw-scope-title is-excluded">
              <Clock3 :size="21" />
              <h2>增项与边界</h2>
            </div>
            <ul>
              <li v-for="item in excluded" :key="item"><ArrowRight :size="17" />{{ item }}</li>
            </ul>
          </article>
        </div>
      </section>

      <section class="mkt-section mkt-section-navy">
        <div class="mkt-shell cw-quality">
          <div>
            <span class="mkt-eyebrow">匹配与履约</span>
            <h2>写手匹配</h2>
            <p>
              平台就近联系写手，同时核对可用档期、行业经验和文体要求。最终人员与到场安排以接单确认结果为准。
            </p>
          </div>
          <div class="cw-quality-list">
            <article>
              <MapPin :size="20" /><span
                ><strong>地域</strong><small>活动所在地及周边优先</small></span
              >
            </article>
            <article>
              <UserRoundCheck :size="20" /><span
                ><strong>经验</strong><small>按行业与文体核对适配度</small></span
              >
            </article>
            <article>
              <Clock3 :size="20" /><span
                ><strong>档期</strong><small>接单前确认到场与交付时间</small></span
              >
            </article>
            <article>
              <Newspaper :size="20" /><span
                ><strong>边界</strong><small>媒体邀请与发布单独下单</small></span
              >
            </article>
          </div>
        </div>
      </section>

      <section class="mkt-final-cta">
        <div class="mkt-shell mkt-final-cta-inner">
          <div>
            <h2>提交云采写订单</h2>
            <p>
              确定活动日期和地点后即可下单；填写人数、天数、联系人与稿件要求，系统自动显示基础金额。
            </p>
          </div>
          <div class="mkt-actions">
            <RouterLink class="mkt-button mkt-button-primary" to="/requirements/cloud-writing"
              >开始下单<ArrowRight :size="17" /></RouterLink
            ><RouterLink class="mkt-button mkt-button-light" to="/media-invitation"
              >提交媒体邀请</RouterLink
            >
          </div>
        </div>
      </section>
    </main>

    <SiteFooter />
  </div>
</template>
