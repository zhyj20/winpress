export interface PublicCaseFact {
  label: string
  value: string
}

/**
 * This public page is intentionally narrower than the internal project record.
 * It demonstrates only that media invitation is managed as an independent service;
 * it must not disclose a client, media list, selection method, outreach activity,
 * delivery evidence or any materials from the underlying project.
 */
export const anonymizedForumCase = {
  title: '媒体邀请项目（脱敏样本）',
  sourceState: '项目资料仅供内部核验；本页不公开项目名称、执行过程、人员、名单或原始资料。',
  summary:
    '本页只说明媒体邀请可以作为独立服务纳入项目记录，不展示任何具体项目的操作细节或传播结果。',
  facts: [
    { label: '服务类别', value: '媒体邀请' },
    { label: '展示范围', value: '项目摘要' },
    { label: '原始资料', value: '不公开' },
  ] satisfies PublicCaseFact[],
  shows: [
    '媒体邀请可作为一项独立服务建立项目记录。',
    '公开页面只保留服务类别、展示目的和资料边界。',
  ],
  doesNotShow: [
    '不展示媒体筛选条件、候选名单、联系人、邀约节奏或沟通记录。',
    '不展示活动名称、地点、时间、参与者、报价、附件或交付数据。',
    '不据此承诺媒体参与、采访、报道、发布或任何传播效果。',
  ],
  boundaries: [
    '原始方案、采访提纲、主持材料、稿件、汇报文件和现场图片均不进入公开前端目录。',
    '客户、嘉宾、机构、媒体、人员、联系方式、费用与项目结果不进入公开页面。',
    '页面不提供原件、展示图、媒体名单或任何项目附件下载。',
    '如需对外发布实名案例或成果，须先取得项目主体、肖像及相关权利方的书面确认。',
  ],
}
