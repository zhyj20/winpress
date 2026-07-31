export type HistoricalReportCategory = 'EVENT_CAMPAIGN' | 'DIRECT_PUBLISHING'

export interface HistoricalReportGroup {
  key: HistoricalReportCategory
  label: string
  title: string
  description: string
}

export interface HistoricalReportRecord {
  id: string
  category: HistoricalReportCategory
  title: string
  period: string
  format: string
  sourceMaterial: string
  recordCount?: number
  scope: string
}

/**
 * This is a client-safe catalogue, not an export of historical workbooks.
 * It deliberately omits source paths, media lists, URLs, prices, contacts,
 * quotations and every original attachment. Individual public use still needs
 * a written authorization check before a record is promoted to a named case.
 */
export const historicalReportGroups: HistoricalReportGroup[] = [
  {
    key: 'EVENT_CAMPAIGN',
    label: '活动传播',
    title: '新闻发布会与活动传播',
    description: '签约、发布会、行业大会、体验日和展会的传播汇报，按活动项目归档。',
  },
  {
    key: 'DIRECT_PUBLISHING',
    label: '直编发稿',
    title: '直编发稿传播',
    description: '以定稿为基础，按媒体、频道、发布时间和发布记录归档。',
  },
]

export const historicalReportRecords: HistoricalReportRecord[] = [
  {
    id: 'golden-crane-signing-2024',
    category: 'EVENT_CAMPAIGN',
    title: '金鹤玉粟鲜食米饭项目签约仪式',
    period: '2024年1月',
    format: '媒体发布汇报',
    sourceMaterial: '活动传播记录',
    recordCount: 72,
    scope: '签约仪式期间的媒体发布记录',
  },
  {
    id: 'specialized-new-products-first-2021',
    category: 'EVENT_CAMPAIGN',
    title: '专精特新新品会·第一场',
    period: '2021年',
    format: '传播汇报案',
    sourceMaterial: '项目汇报文件',
    scope: '新品会传播过程与复盘材料',
  },
  {
    id: 'specialized-new-products-second',
    category: 'EVENT_CAMPAIGN',
    title: '专精特新新品会·第二场',
    period: '项目汇报期',
    format: '发布汇报案',
    sourceMaterial: '项目汇报文件',
    scope: '新品会活动传播复盘材料',
  },
  {
    id: 'specialized-new-products-third',
    category: 'EVENT_CAMPAIGN',
    title: '专精特新新品会·第三场',
    period: '2023年',
    format: '发布汇报案',
    sourceMaterial: '项目汇报文件',
    scope: '新品会活动传播复盘材料',
  },
  {
    id: 'juguang-global-experience-day-2026',
    category: 'EVENT_CAMPAIGN',
    title: '聚光科技全球体验日',
    period: '2026年4月',
    format: '媒体发布汇报',
    sourceMaterial: '活动传播记录',
    recordCount: 10,
    scope: '全球客户体验日相关报道记录',
  },
  {
    id: 'juguang-environment-expo-2026',
    category: 'EVENT_CAMPAIGN',
    title: '聚光科技北京环保展',
    period: '2026年6月',
    format: '媒体发布汇报',
    sourceMaterial: '展会传播记录',
    recordCount: 8,
    scope: '北京环保展相关报道记录',
  },
  {
    id: 'ai-digital-economy-conference-2021',
    category: 'EVENT_CAMPAIGN',
    title: '人工智能与数字经济融合大会',
    period: '2021年4月',
    format: '网络媒体传播汇报',
    sourceMaterial: '大会传播记录',
    recordCount: 13,
    scope: '会前预热与大会新闻发布记录',
  },
  {
    id: 'overseas-chinese-vietnam-expo-2023',
    category: 'EVENT_CAMPAIGN',
    title: '侨交会越南展',
    period: '2023年9月',
    format: '媒体发布汇报',
    sourceMaterial: '展会传播记录',
    recordCount: 19,
    scope: '越南智能科技展相关传播记录',
  },
  {
    id: 'carpoly-art-coating-launch-2024',
    category: 'EVENT_CAMPAIGN',
    title: '嘉宝莉艺术涂料品牌新形象发布会',
    period: '2024年3月',
    format: '媒体发布汇报',
    sourceMaterial: '发布会传播记录',
    recordCount: 128,
    scope: '发布会预热与活动期传播记录',
  },
  {
    id: 'guangmingyuan-uv-2022',
    category: 'DIRECT_PUBLISHING',
    title: '广明源防疫光源产品传播',
    period: '2022年1月',
    format: '新闻稿件媒体传播汇报',
    sourceMaterial: '直编发稿记录',
    recordCount: 72,
    scope: '防疫光源产品稿件发布记录',
  },
  {
    id: 'guangmingyuan-plant-lighting-2022',
    category: 'DIRECT_PUBLISHING',
    title: '广明源植物照明传播',
    period: '2022年10月',
    format: '新闻稿件媒体传播汇报',
    sourceMaterial: '直编发稿记录',
    recordCount: 16,
    scope: '植物照明主题稿件发布记录',
  },
  {
    id: 'guoyao-electromagnetic-2022',
    category: 'DIRECT_PUBLISHING',
    title: '广州国曜科技电磁技术传播',
    period: '2022年10月',
    format: '新闻稿件媒体传播汇报',
    sourceMaterial: '直编发稿记录',
    recordCount: 9,
    scope: '电磁技术产品主题稿件发布记录',
  },
  {
    id: 'kingdee-organization-growth-2023',
    category: 'DIRECT_PUBLISHING',
    title: '金蝶组织生长力主题传播',
    period: '2023年11月',
    format: '新闻稿件媒体发布汇报',
    sourceMaterial: '直编发稿记录',
    recordCount: 11,
    scope: '组织管理主题稿件发布记录',
  },
  {
    id: 'carpoly-antiviral-coating-2021',
    category: 'DIRECT_PUBLISHING',
    title: '嘉宝莉抗病毒涂料传播',
    period: '2021年2月',
    format: '新闻发布汇报',
    sourceMaterial: '直编发稿记录',
    recordCount: 79,
    scope: '产品检测与应用主题稿件发布记录',
  },
  {
    id: 'guirenniao-finance-pr-2023',
    category: 'DIRECT_PUBLISHING',
    title: '贵人鸟财经公关传播',
    period: '2023年10月',
    format: '财经公关发布汇报',
    sourceMaterial: '定向发布记录',
    recordCount: 29,
    scope: '财经主题稿件发布记录',
  },
  {
    id: 'kingdee-award-2024',
    category: 'DIRECT_PUBLISHING',
    title: '金蝶徐少春获奖传播',
    period: '2024年12月',
    format: '新闻稿件媒体发布汇报',
    sourceMaterial: '直编发稿记录',
    recordCount: 13,
    scope: '获奖新闻稿件发布记录',
  },
  {
    id: 'sanming-fire-service-2026',
    category: 'DIRECT_PUBLISHING',
    title: '福建三明消防人物报道',
    period: '2026年3—4月',
    format: '媒体发布汇报',
    sourceMaterial: '人物报道记录',
    recordCount: 3,
    scope: '消防人物故事发布记录',
  },
  {
    id: 'utigen-car-t-2023',
    category: 'DIRECT_PUBLISHING',
    title: '上海优替济生 CAR-T 主题传播',
    period: '2023年6月',
    format: '媒体传播汇报',
    sourceMaterial: '直编发稿记录',
    recordCount: 40,
    scope: '医疗科技主题稿件发布记录',
  },
  {
    id: 'carpoly-easy-breathe-2021',
    category: 'DIRECT_PUBLISHING',
    title: '嘉宝莉易呼吸产品传播',
    period: '2021年6—7月',
    format: '新闻发布汇报',
    sourceMaterial: '直编发稿记录',
    recordCount: 21,
    scope: '产品发布主题稿件记录',
  },
  {
    id: 'alienware-bag-2024',
    category: 'DIRECT_PUBLISHING',
    title: 'Alienware 品牌产品传播',
    period: '2024年11月',
    format: '品牌传播汇报',
    sourceMaterial: '传播汇报演示文件',
    recordCount: 39,
    scope: '产品主题媒体发布记录',
  },
  {
    id: 'yunshuiji-iot-water-2023',
    category: 'DIRECT_PUBLISHING',
    title: '云水纪智能净水主题传播',
    period: '2023年5月',
    format: '新闻稿件媒体发布汇报',
    sourceMaterial: '直编发稿记录',
    recordCount: 40,
    scope: '品牌与产品主题稿件记录',
  },
  {
    id: 'guangmingyuan-expo-2023',
    category: 'DIRECT_PUBLISHING',
    title: '广明源展会水处理主题传播',
    period: '2023年8月',
    format: '新闻稿件媒体传播汇报',
    sourceMaterial: '直编发稿记录',
    recordCount: 49,
    scope: '展会主题稿件发布记录',
  },
]

export function reportsForCategory(category: HistoricalReportCategory) {
  return historicalReportRecords.filter((record) => record.category === category)
}
