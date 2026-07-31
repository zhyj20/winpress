import {
  BookOpenCheck,
  FilePenLine,
  Gauge,
  Megaphone,
  Newspaper,
  Send,
  ShieldCheck,
} from 'lucide-vue-next'

export const taskEntries = [
  {
    index: '01',
    label: '云采写',
    title: '现场采写',
    description: '提交活动基本信息与素材，按城市、行业和时段匹配写手并启动稿件流程。',
    action: '预约现场采写',
    to: '/requirements/cloud-writing',
    icon: FilePenLine,
  },
  {
    index: '02',
    label: '媒体邀请',
    title: '媒体邀请',
    description: '提交活动简报与议题，整理拟邀名单并记录项目沟通。媒体是否到场由媒体侧自行确认。',
    action: '创建媒体邀请',
    to: '/media-invitation',
    icon: Newspaper,
  },
  {
    index: '03',
    label: '直编发稿',
    title: '媒体筛选与发稿',
    description: '按行业、地区、形式与时间窗口整理渠道计划，项目核验后再进入执行环节。',
    action: '筛选发稿媒体',
    to: '/direct-publishing',
    icon: Send,
  },
  {
    index: '04',
    label: '举办新闻发布会',
    title: '举办新闻发布会',
    description: '基本信息、拟邀名单、现场安排和会后清单在发布会项目中持续更新。',
    action: '发起发布会项目',
    to: '/requirements/news-conference',
    icon: Megaphone,
  },
]

export const channelPaths = [
  {
    label: '云采写',
    title: '现场采写',
    value: '现场采写 980 元 / 人 / 天。',
    description: '按城市、档期、行业经验匹配写手；完成资料梳理、现场采写、撰写与修订。',
    note: '写手不承担媒体邀请与报道决策。',
    action: '预约云采写',
    to: '/requirements/cloud-writing',
    tone: 'purple' as const,
    icon: FilePenLine,
  },
  {
    label: '媒体邀请',
    title: '媒体邀请',
    value: '适配活动传播、品牌故事与热点叙事。',
    description: '围绕新闻点和传播目标筛选候选媒体，整理拟邀名单并记录沟通进度。',
    note: '是否到场、是否采访或报道由媒体侧确认。',
    action: '提交媒体邀请',
    to: '/media-invitation',
    tone: 'amber' as const,
    icon: Newspaper,
  },
  {
    label: '直编发稿',
    title: '直编发稿',
    value: '适配已有定稿的内容发布任务。',
    description: '按媒体类型、行业、地区、发布日期与价格整理渠道计划，支持批量提报。',
    note: '目录不是实时媒体库存；最终可用性、报价和发布以项目核验结果为准。',
    action: '进入频道库',
    to: '/direct-publishing',
    tone: 'blue' as const,
    icon: Send,
  },
  {
    label: '举办新闻发布会',
    title: '举办新闻发布会',
    value: '活动议题 + 媒体名单 + 场内执行清单。',
    description: '管理议题、现场素材、拟邀名单和会后清单。',
    note: '需要采写、媒体邀请或直编发稿时，分别建立独立服务项目。',
    action: '发起新闻发布会',
    to: '/requirements/news-conference',
    tone: 'green' as const,
    icon: Megaphone,
  },
]

export const mediaRecommendations = [
  {
    group: '汽车产业媒体',
    fields: ['汽车产业', '北京/全国', '新能源/智能驾驶'],
    reason: '适配新品发布、供应链与行业合作内容。',
  },
  {
    group: '工业制造媒体',
    fields: ['制造业', '深圳/全国', '产业案例/工艺解读'],
    reason: '适配产品发布、工艺改造与行业趋势。',
  },
  {
    group: '科技创新媒体',
    fields: ['科技行业', '上海/全国', '技术路线/应用场景'],
    reason: '适配技术落地、研发进展与市场表达。',
  },
]

export const taskCases = [
  {
    category: '云采写',
    title: '活动稿件生产',
    challenge: '多场景信息分散：活动口径、采访要点、行业素材不一致，导致发布节奏受阻。',
    response: '建立统一口径库，统一采写、修订和版本管理，并与执行端对齐交付。',
    result: '形成可复用稿件、修改记录与定稿版本，供后续发布另行使用。',
    icon: Megaphone,
  },
  {
    category: '媒体邀请',
    title: '发布会媒体邀请',
    challenge: '同一活动涉及不同媒体属性，缺少层级化邀约清单，沟通效率低。',
    response: '按行业与地区整理候选名单，项目专员在实际沟通后记录进度。',
    result: '客户可查看拟邀名单、已记录的沟通状态、现场安排与项目进度。',
    icon: ShieldCheck,
  },
  {
    category: '直编发稿',
    title: '多渠道发布',
    challenge: '内容发布在多个渠道提交，人工拆单易漏项，难以核对投放状态。',
    response: '统一建单、统一提报、统一记录，按渠道归档发布结果。',
    result: '客户可查已确认渠道的计划、时间窗口与已记录的执行结果。',
    icon: BookOpenCheck,
  },
  {
    category: '新闻发布会',
    title: '发布会项目管理',
    challenge: '会前准备、现场执行和会后交付分散，项目进度不易核对。',
    response:
      '用发布会清单管理会前、现场和会后事项；如需采写、媒体邀请或直编发稿，分别建立服务项目后关联查看。',
    result: '客户可查看发布会清单、完成记录和关联服务项目的进度。',
    icon: Gauge,
  },
]

export const insightArticles = [
  {
    category: '内容策划',
    title: '连续发布的本地化选题',
    summary:
      '连续多场活动不能机械复制同一篇稿件。品牌主张保持一致，选题、案例与媒体沟通要贴近当地产业和读者。',
    quote: '“同理不同样”',
    sourceLabel: '旧站公开文章 · 2024-11-28',
    sourceUrl: 'https://old.winpress.cn/article/23',
  },
  {
    category: '报道准备',
    title: '一场发布会，多套报道材料',
    summary:
      '通稿交代事实，深度稿展开议题，采访提纲和嘉宾资料服务不同报道角度，避免所有媒体只拿到同一份材料。',
    quote: '“帮助记者寻找不同的新闻角度”',
    sourceLabel: '旧站公开文章 · 2024-11-28',
    sourceUrl: 'https://old.winpress.cn/article/20',
  },
  {
    category: '现场执行',
    title: '发布会执行清单',
    summary:
      '会场动线、记者联络、采访安排和备用方案相互关联。关键动作进入清单，现场才有负责人、时点和处理顺序。',
    quote: '“一个地方错了后面可能就会引发连锁反应”',
    sourceLabel: '旧站公开文章 · 2024-11-28',
    sourceUrl: 'https://old.winpress.cn/article/18',
  },
]

export const insightArchive = [
  ...insightArticles,
  {
    category: '异地邀约',
    title: '异地媒体参会',
    summary:
      '记者无法到场时，远程入会只是起点；议程同步、新闻手册、采访素材和提问安排决定线上参会质量。',
    quote: '“线上线下同步，为记者参会破除时空限制”',
    sourceLabel: '旧站公开文章 · 2024-11-28',
    sourceUrl: 'https://old.winpress.cn/article/22',
  },
]
