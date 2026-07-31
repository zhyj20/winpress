<script setup lang="ts">
import { ArrowRight, CheckCircle2, FileWarning, ShieldCheck } from 'lucide-vue-next'
import { computed } from 'vue'
import SiteFooter from '@/components/marketing/SiteFooter.vue'
import SiteHeader from '@/components/marketing/SiteHeader.vue'
import '@/assets/marketing.css'

interface Section {
  title: string
  paragraphs?: string[]
  bullets?: string[]
}

const props = defineProps<{ page: 'legal' | 'privacy' | 'terms' | 'boundaries' | 'cases' }>()

const pages: Record<
  typeof props.page,
  {
    eyebrow: string
    title: string
    lead: string
    leadRemark?: string
    notice?: string
    sections: Section[]
  }
> = {
  legal: {
    eyebrow: '法律与经营信息',
    title: '主体核验与信息披露边界',
    lead: '本页用于披露网站经营主体、备案与联系信息。',
    leadRemark: '运营主体与备案资料正在核验，核验完成后统一披露。',
    notice:
      '正式上线前待提供并核验：运营主体全称、统一社会信用代码、注册地址、客服联系方式、ICP备案号及适用许可证信息。',
    sections: [
      {
        title: '当前可确认的信息',
        bullets: [
          '产品名称：云发布（WinPress）',
          '规划域名：winpress.cn、winpress.waykey.net',
          '服务范围：现场采写、媒体邀请、直编发稿、新闻发布会项目管理及经评估后的 API 接入',
        ],
      },
      {
        title: '信息更新原则',
        paragraphs: [
          '经营主体与备案信息只有在收到可核验材料后才会发布。更新时应同时核对网站页脚、隐私政策、服务条款、合同模板与开票信息，避免不同页面互相矛盾。',
        ],
      },
      {
        title: '联系渠道',
        paragraphs: [
          '您可以通过站内咨询表单联系云发布。公开电话、邮箱与办公地址将在主体材料核验完成后补充。',
        ],
      },
    ],
  },
  privacy: {
    eyebrow: '数据处理说明',
    title: '隐私与数据处理说明（待确认）',
    lead: '本页说明云发布在账号注册、项目提交、商务咨询与服务执行中拟采用的个人信息处理边界。',
    leadRemark: '状态：待运营主体与法务确认；本页不是已生效的隐私政策。',
    notice:
      '本页为商用前的隐私与数据处理说明草案。运营主体、适用法律、保存期限、第三方处理范围、跨境安排和生产安全措施均待书面核验；正式上线前以主体方公布的版本为准。',
    sections: [
      {
        title: '我们可能收集的信息',
        bullets: [
          '账号信息：姓名、手机号、工作邮箱、所属组织与登录凭证',
          '项目资料：活动信息、联系人、稿件、文件、媒体与渠道选择、项目沟通记录',
          '咨询信息：公司名称、联系人、手机号、邮箱与咨询内容',
          '运行信息：请求时间、操作记录、错误日志及用于保障账号安全的必要技术信息',
        ],
      },
      {
        title: '使用目的',
        bullets: [
          '创建和管理项目、分配执行人员、提供客户支持',
          '核对稿件版本、报价、订单、任务状态与成果链接',
          '处理商务咨询、API 接入评估与售后问题',
          '排查故障、防止滥用并履行适用的法律义务',
        ],
      },
      {
        title: '共享与权限',
        paragraphs: [
          '拟定规则为：客户资料只在完成项目所需范围内由获授权人员处理；如需向履约方提供必要资料，须由运营主体在正式协议中明确范围、目的、期限与责任。客户界面不展示上游供应商身份、联系方式或成本价。',
          '不将项目材料用于与本次服务无关的营销；法律要求、授权范围和具体处理方式以正式政策与项目协议为准。',
        ],
      },
      {
        title: '保存、安全与权利',
        paragraphs: [
          '当前页面仅说明拟采用的数据保护原则，不代表生产安全措施已经验收。正式上线前需由运营主体确定保存期限、访问控制、传输与备份措施，并经过安全和法务核验后公布。用户的查阅、更正、删除等请求渠道，也须随正式政策一并明确。',
        ],
      },
    ],
  },
  terms: {
    eyebrow: '条款草案',
    title: '服务条款（待确认）',
    lead: '本页列出拟用于云发布网站与操作台的基本使用规则。',
    leadRemark: '状态：待运营主体与法务确认；具体项目以双方确认的订单、报价、排期与合同为准。',
    notice:
      '本页为商用前条款草案，不构成已生效的合同、退款规则或争议处理依据。主体、适用法律、收费与退款、违约及争议解决条款须经运营主体和法律顾问核验后公布。',
    sections: [
      {
        title: '账号与材料',
        bullets: [
          '用户应提供真实、合法且有权使用的活动信息、稿件、图片与联系资料',
          '账号仅供授权人员使用，不得转让、共享凭证或绕过权限控制',
          '平台可对明显违法、侵权、虚假或无法核验的内容拒绝受理',
        ],
      },
      {
        title: '下单与变更',
        paragraphs: [
          '四项常规服务分别下单。新闻发布会项目用于统筹会前、现场和会后工作，不自动把采写、媒体邀请或直编发稿合并计价。新增服务、范围变化和加急安排须另行确认。',
        ],
      },
      {
        title: '媒体与发布结果',
        paragraphs: [
          '媒体邀请是联系与协调服务。媒体是否到场、采访、采用或报道，由媒体独立决定。直编发稿受稿件、栏目、审核规则、报价有效期和排期影响；平台只把已经核验的发布链接作为完成依据。',
        ],
      },
      {
        title: '费用、取消与争议',
        paragraphs: [
          '费用以提交前展示或另行确认的报价为准。订单取消、改期、第三方已发生费用、退款和违约责任应在具体合同或订单规则中约定。正式商用前须由运营主体的法律顾问复核本条款。',
        ],
      },
    ],
  },
  boundaries: {
    eyebrow: '服务边界',
    title: '服务边界',
    lead: '把不同工作拆开，是为了让客户知道买了什么、谁负责、如何验收。',
    leadRemark: '同时避免把媒体邀请、采写与付费发布混为一谈。',
    notice:
      '媒体目录、服务价格、排期和实际履约均以项目核验为准。外部数据、价格和服务能力未完成验收前，不代表实时媒体资源、可约名单或实际履约。',
    sections: [
      {
        title: '现场采写（云采写）',
        bullets: [
          '按 980 元/人/天计基础服务费，活动所在地及周边写手优先',
          '写手负责资料梳理、现场采集、稿件撰写和约定次数的修改',
          '写手不以媒体记者身份承接，不负责媒体邀请，也不决定发布结果',
        ],
      },
      {
        title: '媒体邀请',
        bullets: [
          '按议题、行业线口、城市和媒体属性形成候选名单；名单经项目核验后再安排实际邀请',
          '候选、已邀、回复、到场等状态分别记录',
          '媒体保持编辑独立，邀请不构成采访、到场或报道承诺',
        ],
      },
      {
        title: '直编发稿',
        bullets: [
          '客户从经项目核验的可用渠道中查看栏目、价格、时效和公开规则',
          '必须绑定客户已确认的稿件版本；报价失效后须重新选择',
          '供应商、成本价和上游订单只供平台内部管理，客户按云发布订单验收',
        ],
      },
      {
        title: '新闻发布会',
        bullets: [
          '项目工作台分为会前、现场和会后三个阶段',
          '创建项目只强制填写标题、会务联系人和手机号，其他资料可逐步补充',
          '现场采写、媒体邀请和直编发稿仍分别选择、确认和计价',
        ],
      },
      {
        title: 'API 接入',
        paragraphs: [
          'API 接入不是普通自助服务项目。企业须先提交咨询，由商务与技术人员确认系统边界、字段、鉴权、回调、日志和验收方案后再实施。',
        ],
      },
    ],
  },
  cases: {
    eyebrow: '案例与证据',
    title: '案例发布标准',
    lead: '案例仅在取得公开授权并完成证据核验后发布；未经授权的客户名称、商标和项目结果不作为宣传背书。',
    sections: [
      {
        title: '可公开案例的最低材料',
        bullets: [
          '客户或项目名称的公开授权范围',
          '服务时间、服务范围、稿件或活动事实来源',
          '可核验的发布链接、媒体名单或项目验收记录',
          '结果口径、统计时间与必要的限制说明',
        ],
      },
      {
        title: '页面标注规则',
        bullets: [
          '真实案例：明确客户授权、项目范围与核验日期',
          '匿名案例：隐去身份，但保留真实流程与可核验的项目记录',
          '演示数据：直接标注“演示”或“示例”，不得写成已发生业绩',
        ],
      },
      {
        title: '当前状态',
        paragraphs: [
          '现有客户名称与项目材料尚未完成逐项授权和证据核验，因此不发布实名品牌案例。网站可展示匿名、脱敏的资料样本，但不展示原件、联系人、名单、报价或历史结果；材料齐备后，可由平台管理员建立证据清单，再更新对客页面。',
        ],
      },
    ],
  },
}

const content = computed(() => pages[props.page])
</script>

<template>
  <div class="mkt-page trust-page">
    <SiteHeader />
    <main>
      <section class="trust-hero">
        <div class="mkt-shell">
          <span class="mkt-eyebrow">{{ content.eyebrow }}</span>
          <h1>{{ content.title }}</h1>
          <p>{{ content.lead }}</p>
          <p v-if="content.leadRemark" class="trust-hero-remark">{{ content.leadRemark }}</p>
        </div>
      </section>
      <section class="trust-body">
        <div class="mkt-shell trust-layout">
          <aside>
            <ShieldCheck :size="22" />
            <strong>商用信息原则</strong>
            <p>可核验再公开；对客信息与平台内部资料分开；演示数据必须明确标注。</p>
            <RouterLink to="/contact">联系平台<ArrowRight :size="15" /></RouterLink>
          </aside>
          <div class="trust-content">
            <div v-if="content.notice" class="trust-notice">
              <FileWarning :size="20" />
              <p>{{ content.notice }}</p>
            </div>
            <article v-for="section in content.sections" :key="section.title">
              <h2>{{ section.title }}</h2>
              <p v-for="paragraph in section.paragraphs" :key="paragraph">{{ paragraph }}</p>
              <ul v-if="section.bullets">
                <li v-for="bullet in section.bullets" :key="bullet">
                  <CheckCircle2 :size="17" />{{ bullet }}
                </li>
              </ul>
            </article>
          </div>
        </div>
      </section>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.trust-hero {
  padding: 96px 0 78px;
  color: #fff;
  background: #141d28;
}

.trust-hero .mkt-eyebrow {
  color: #a9c8ff;
}

.trust-hero h1 {
  max-width: 880px;
  margin: 16px 0 20px;
  color: #fff;
  font-size: clamp(40px, 5vw, 68px);
  line-height: 1.08;
}

.trust-hero p {
  max-width: 860px;
  margin: 0;
  color: #d4deea;
  font-size: 17px;
  line-height: 1.85;
}

.trust-hero-remark {
  margin: 10px 0 0;
  color: #d4deea;
  max-width: 860px;
  font-size: 12px;
  line-height: 1.72;
}

.trust-body {
  padding: 76px 0 96px;
  background: #f7f8fa;
}

.trust-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 760px);
  justify-content: space-between;
  gap: 70px;
}

.trust-layout aside {
  position: sticky;
  top: 96px;
  align-self: start;
  display: grid;
  gap: 10px;
  padding: 22px;
  border: 1px solid #dce2e9;
  background: #fff;
}

.trust-layout aside svg {
  color: #245ba7;
}

.trust-layout aside p {
  margin: 0;
  color: #687586;
  font-size: 13px;
  line-height: 1.7;
}

.trust-layout aside a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 44px;
  color: #245ba7;
  font-weight: 750;
}

.trust-content {
  display: grid;
  gap: 18px;
}

.trust-content article,
.trust-notice {
  padding: 30px 32px;
  border: 1px solid #dce2e9;
  background: #fff;
}

.trust-content h2 {
  margin: 0 0 16px;
  color: #172334;
  font-size: 24px;
}

.trust-content p {
  color: #566476;
  line-height: 1.82;
}

.trust-content ul {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.trust-content li {
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: 9px;
  color: #455467;
  line-height: 1.7;
}

.trust-content li svg {
  margin-top: 5px;
  color: #28724e;
}

.trust-notice {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 12px;
  border-color: #dfcda8;
  background: #fffaf0;
}

.trust-notice svg {
  color: #9b681b;
}

.trust-notice p {
  margin: 0;
  color: #725124;
}

@media (max-width: 820px) {
  .trust-layout {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .trust-layout aside {
    position: static;
  }
}
</style>
