export interface ChipseaCaseMilestone {
  period: string
  title: string
  description: string
  deliverables: string[]
}

export interface ChipseaCasePhoto {
  src: string
  caption: string
  alt: string
}

export interface ChipseaCaseCoverage {
  date: string
  outlet: string
  market: string
  kind: string
  title: string
  angle: string
  url: string
  verification: 'PUBLIC_PAGE' | 'ARCHIVE_EVIDENCE' | 'METADATA'
  evidence: string
}

export const chipseaComputexCase = {
  client: '芯海科技',
  title: '芯海科技 COMPUTEX 2026 媒体云发布会',
  summary:
    '围绕台北电脑展建立项目时间表，前置完成新闻手册、采访提纲和媒体素材，展期推进现场采访与报道，展后核验公开链接并归档。',
  sourceState:
    '本页依据用户提供的项目资料、活动照片与可追溯公开页面整理。项目计划和公开成果分开呈现；预算、联系人、媒体沟通记录、采访原文及未公开经营信息不进入页面，对外公开权利仍由项目主体确认。',
  facts: [
    { label: 'WBS 节奏', value: '2026-05-09 至 07-05' },
    { label: '活动现场', value: 'COMPUTEX 2026' },
    { label: '执行地点', value: '台北南港展览馆' },
    { label: '关联任务', value: '采写、媒体邀请、项目管理' },
  ],
  milestones: [
    {
      period: '5月9—12日',
      title: '项目启动',
      description: '确认传播边界、审核路径、项目联系人和可公开资料，建立推进清单。',
      deliverables: ['项目 WBS', '资料清单', '审核机制'],
    },
    {
      period: '5月13—20日',
      title: '内容准备',
      description: '完成预采访，整理新闻手册、选题方向、采访提纲和基础稿件。',
      deliverables: ['新闻手册', '采访提纲', '选题建议'],
    },
    {
      period: '5月21日—6月1日',
      title: '媒体准备',
      description: '按产业线口与地区确认媒体，补齐事实卡、问答口径和现场素材。',
      deliverables: ['媒体名单', '媒体 Q&A', '素材包'],
    },
    {
      period: '6月2—5日',
      title: '展会执行',
      description: '推进展位沟通、现场采访、产品演示、图片回传与报道跟进。',
      deliverables: ['现场采访', '展会图片', '报道链接'],
    },
    {
      period: '6月6—20日',
      title: '跟进与补采',
      description:
        '按项目 WBS 跟进重点媒体补采、深度稿策动与报道链接；可确认的信息再进入项目归档。',
      deliverables: ['重点报道链接', '阶段成果表', '深度稿跟进'],
    },
    {
      period: '6月21日—7月5日',
      title: '归档与复盘',
      description: '按项目 WBS 汇总链接、截图和传播记录；本页公开报道检索范围截至 6 月 30 日。',
      deliverables: ['传播报告', '成果包', '复盘建议'],
    },
  ] satisfies ChipseaCaseMilestone[],
  launchPoints: [
    '先确定技术信息的公开边界，再进入采访和稿件生产。',
    '将品牌、产品技术、市场与供应链三类受访对象拆成独立提纲。',
    '同一活动下的采写、媒体邀请和项目管理分别留痕，不创建组合订单。',
  ],
  executionPoints: [
    {
      title: '采前访谈',
      description: '按战略、产品技术、市场与供应链三条线整理问题，先补事实，再定选题。',
    },
    {
      title: '新闻手册',
      description: '汇总展会背景、产品信息、可引用事实、问答边界和图片说明，供记者快速查阅。',
    },
    {
      title: '稿件版本',
      description: '分别准备大陆媒体、台湾媒体和深度报道版本，避免把同一篇通稿机械复制到所有渠道。',
    },
    {
      title: '媒体沟通',
      description: '台湾媒体以展位采访和现场观察为主，大陆媒体按产业、科技和财经线口补充采写。',
    },
  ],
  launchPhoto: {
    src: '/case-media/chipsea-computex-launch.webp',
    caption: '项目启动阶段的内部资料沟通与采访准备',
    alt: '芯海科技项目启动阶段的会议现场',
  } satisfies ChipseaCasePhoto,
  eventPhotos: [
    {
      src: '/case-media/chipsea-computex-booth.webp',
      caption: 'COMPUTEX 2026 芯海科技展位',
      alt: 'COMPUTEX 2026 芯海科技展位及现场参观者',
    },
    {
      src: '/case-media/chipsea-computex-product.webp',
      caption: '计算外围芯片应用展示',
      alt: '芯海科技工作人员在展位展示笔记本电脑产品',
    },
    {
      src: '/case-media/chipsea-computex-interview.webp',
      caption: '展位现场采访',
      alt: '媒体记者在芯海科技展位进行现场采访',
    },
  ] satisfies ChipseaCasePhoto[],
  evidenceImages: [
    {
      src: '/case-media/chipsea-result-digitimes-article.webp',
      caption: 'DIGITIMES 报道页面',
      alt: 'DIGITIMES 芯海科技 COMPUTEX 2026 报道页面截图',
    },
    {
      src: '/case-media/chipsea-result-dute.webp',
      caption: '深圳商报·读创报道留存',
      alt: '深圳商报读创芯海科技报道分享图',
    },
    {
      src: '/case-media/chipsea-result-southcn.webp',
      caption: '南方日报报道留存',
      alt: '南方日报芯海科技相关报道页面截图',
    },
    {
      src: '/case-media/chipsea-result-digitimes-index.webp',
      caption: 'DIGITIMES 专题列表留存',
      alt: 'DIGITIMES COMPUTEX 专题列表页面截图',
    },
  ] satisfies ChipseaCasePhoto[],
  coverage: [
    {
      date: '2026-06-02',
      outlet: '深圳商报·读创',
      market: '大陆',
      kind: '现场产业报道',
      title: '科创001｜全球掀起AI PC热潮，国产自研计算外围芯片酝酿新爆点',
      angle: '从 AI PC 的系统管理需求切入计算外围芯片。',
      url: 'https://www.dutenews.com/n/article/10675451',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-02',
      outlet: '联合报',
      market: '台湾',
      kind: '现场采访报道',
      title: '獲AMD intel 全球雙認證 芯海科技積極布局AI PC產業鏈',
      angle: '双平台认证、AI PC 产业链与海外市场。',
      url: 'https://money.udn.com/money/story/5603/9541484',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-02',
      outlet: '经济日报',
      market: '台湾',
      kind: '现场采访报道',
      title: 'AI PC產業鏈走向混合架構 芯海科技：3至5年關鍵在離線推理能力提升',
      angle: 'AI PC 混合架构、主板控制与离线推理。',
      url: 'https://udn.com/news/story/7333/9541623',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-02',
      outlet: '中国时报',
      market: '台湾',
      kind: '展会产业报道',
      title: '219家陸企參展COMPUTEX 從製造商轉型AI供應鏈夥伴',
      angle: '大陆企业参展与 AI 供应链角色变化。',
      url: 'https://www.chinatimes.com/realtimenews/20260602004024-260409?chdtv',
      verification: 'ARCHIVE_EVIDENCE',
      evidence: '项目归档链接与截图留存；站点抓取受限',
    },
    {
      date: '2026-06-03',
      outlet: '深圳新闻网',
      market: '大陆',
      kind: '产业报道',
      title: 'AI PC风口上的全球突围 芯海科技带计算“第二大脑”登上台北电脑展',
      angle: 'EC 芯片、计算外围产品矩阵与全球供应链。',
      url: 'https://www.sznews.com/news/content/2026-06/03/content_32076830.htm',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-03',
      outlet: '爱集微',
      market: '大陆',
      kind: '行业解读',
      title: 'EC芯片背后的万亿AI PC变局：芯海科技站上新风口',
      angle: 'EC 商业化进程、平台认证与计算外围生态。',
      url: 'https://www.ijiwei.com/n/1044772',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-04',
      outlet: 'DIGITIMES',
      market: '台湾',
      kind: '供应链报道',
      title: 'Intel、AMD雙認證加持 芯海科技叩關全球PC供應鏈',
      angle: '认证资质、联想供应链与台湾品牌、ODM 市场。',
      url: 'https://www.digitimes.com.tw/tech/dt/n/shwnws.asp?id=0000757577_X7G404HG8MHU6C6W1BVU2',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-04',
      outlet: '南方日报',
      market: '大陆',
      kind: '产业报道',
      title: '巨头扎堆AI PC 广东如何卡位？',
      angle: 'AI PC 产业变革中的广东制造与计算外围芯片。',
      url: 'https://news.southcn.com/node_ac2b0b62a4/3beb0529db.shtml',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-04',
      outlet: '电子发烧友网 / 新浪财经',
      market: '大陆',
      kind: '行业报道留存',
      title: '亮剑台北电脑展 芯海科技COMPUTEX解锁AI PC“芯”实力',
      angle: '展位产品、AI PC 计算生态与海外供应链。',
      url: 'https://finance.sina.com.cn/wm/2026-06-04/doc-iniaeyvy0625084.shtml',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-05',
      outlet: '中新网广东',
      market: '大陆',
      kind: '产业报道',
      title: '全球掀起AI PC热潮 深圳科企聚力发展',
      angle: 'EC 芯片的系统价值与深圳科技企业布局。',
      url: 'https://www.gd.chinanews.com.cn/2026/2026-06-05/448079.shtml',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-05',
      outlet: '证券时报·证券时报网',
      market: '大陆',
      kind: '展会采访报道',
      title: '全球AIPC热潮爆发 国内全产业链乘势迈入黄金发展期',
      angle: '记录芯海科技在 COMPUTEX 的现场展示及对 AI PC 计算外围芯片的采访。',
      url: 'https://www.stcn.com/article/detail/3946386.html',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-05',
      outlet: '深圳商报·读创',
      market: '大陆',
      kind: '产业延展报道',
      title: '个人电脑大变革，黄仁勋点燃AI PC',
      angle: '在 AI PC 产业背景中记录芯海科技的计算外围芯片展示。',
      url: 'https://www.dutenews.com/n/article/10680149',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
    {
      date: '2026-06-11',
      outlet: '深圳新闻网',
      market: '大陆',
      kind: '展后行业观察',
      title: '消费观察｜一颗EC的全球突围：AI PC风口下，大湾区芯片的“竞争力替代”之路',
      angle: '围绕 AI PC、EC 与计算外围芯片，延展展会后的产业观察。',
      url: 'https://www.sznews.com/news/content/2026-06/11/content_32085930.htm',
      verification: 'PUBLIC_PAGE',
      evidence: '公开页面已核验',
    },
  ] satisfies ChipseaCaseCoverage[],
  resultNotes: [
    '检索范围为 2026 年 6 月 2 日至 6 月 30 日；展期关联报道集中在 6 月 2 日至 5 日，6 月 11 日另保留一条围绕同一技术议题的展后行业观察。',
    '本页列出 13 个关联报道页面；同源转载、专题聚合与独立原创不混作同一统计口径。',
    '公开页面与本项目的关联不等于项目归因或合同履约结论，正式对外使用须由项目主体确认。',
    '页面核验状态分为“公开页面”和“归档留存”；归档留存用于保留受限页面的标题、日期和项目材料证据，不表述为可实时访问的原始页面。',
    '部分媒体页面存在会员墙、地区限制或后续改版，页面标题、日期和链接均按 2026 年 7 月 31 日核验结果记录。',
    '原始方案、新闻手册、采访提纲、内部稿件、预算与联系人信息不提供下载。',
  ],
}
