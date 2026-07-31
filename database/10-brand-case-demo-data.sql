BEGIN;

-- Brand names below are used as local demonstration data at the user's request.
-- Project details, budgets and delivery records are illustrative and must not be
-- presented as public claims about the named brands without checking source files.

INSERT INTO organization (
  organization_no, name, organization_type, contact_name, contact_phone, contact_email, status
) VALUES
  ('ORG-CASE-CHIPSEA', '芯海科技（案例演示）', 'CUSTOMER', '案例联系人', '13800001001', 'chipsea.case@demo.winpress.cn', 'ACTIVE'),
  ('ORG-CASE-KINGDEE', '金蝶集团（案例演示）', 'CUSTOMER', '案例联系人', '13800001002', 'kingdee.case@demo.winpress.cn', 'ACTIVE'),
  ('ORG-CASE-SUNON', '圣奥集团（案例演示）', 'CUSTOMER', '案例联系人', '13800001003', 'sunon.case@demo.winpress.cn', 'ACTIVE'),
  ('ORG-CASE-GMY', '广明源（案例演示）', 'CUSTOMER', '案例联系人', '13800001004', 'gmy.case@demo.winpress.cn', 'ACTIVE')
ON CONFLICT (organization_no) DO UPDATE SET
  name = EXCLUDED.name,
  contact_name = EXCLUDED.contact_name,
  contact_phone = EXCLUDED.contact_phone,
  contact_email = EXCLUDED.contact_email,
  status = EXCLUDED.status;

INSERT INTO app_user (
  user_no, organization_id, username, password_hash, display_name, mobile, email, status
)
SELECT data.user_no, organization.id, data.username, data.password_hash,
       data.display_name, data.mobile, data.email, 'ACTIVE'
FROM (
  VALUES
    ('USR-CASE-CHIPSEA', 'ORG-CASE-CHIPSEA', 'chipsea.case@demo.winpress.cn', '$2a$12$ojrsvoMksV4cBxdcotBGFeX0iEkeON4zP4DhF7WS2ewFs2pwZqqBe', '芯海科技案例联系人', '13800001001', 'chipsea.case@demo.winpress.cn'),
    ('USR-CASE-KINGDEE', 'ORG-CASE-KINGDEE', 'kingdee.case@demo.winpress.cn', '$2a$12$ojrsvoMksV4cBxdcotBGFeX0iEkeON4zP4DhF7WS2ewFs2pwZqqBe', '金蝶集团案例联系人', '13800001002', 'kingdee.case@demo.winpress.cn'),
    ('USR-CASE-SUNON', 'ORG-CASE-SUNON', 'sunon.case@demo.winpress.cn', '$2a$12$ojrsvoMksV4cBxdcotBGFeX0iEkeON4zP4DhF7WS2ewFs2pwZqqBe', '圣奥集团案例联系人', '13800001003', 'sunon.case@demo.winpress.cn'),
    ('USR-CASE-GMY', 'ORG-CASE-GMY', 'gmy.case@demo.winpress.cn', '$2a$12$ojrsvoMksV4cBxdcotBGFeX0iEkeON4zP4DhF7WS2ewFs2pwZqqBe', '广明源案例联系人', '13800001004', 'gmy.case@demo.winpress.cn')
) AS data(user_no, organization_no, username, password_hash, display_name, mobile, email)
JOIN organization ON organization.organization_no = data.organization_no
ON CONFLICT (username) DO UPDATE SET
  organization_id = EXCLUDED.organization_id,
  display_name = EXCLUDED.display_name,
  mobile = EXCLUDED.mobile,
  email = EXCLUDED.email,
  status = EXCLUDED.status;

INSERT INTO user_role (user_id, role_id, status)
SELECT app_user.id, sys_role.id, 'ACTIVE'
FROM app_user
CROSS JOIN sys_role
WHERE app_user.username IN (
  'chipsea.case@demo.winpress.cn',
  'kingdee.case@demo.winpress.cn',
  'sunon.case@demo.winpress.cn',
  'gmy.case@demo.winpress.cn'
)
AND sys_role.role_code = 'CUSTOMER'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- 芯海科技：现场采写；媒体邀请和直编发布按独立项目处理。
INSERT INTO customer_requirement (
  requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, service_days, writer_count, unit_price,
  estimated_amount, onsite_contact_name, onsite_contact_mobile, deliverable_requirement,
  matching_preference, due_at, status
)
SELECT 'REQ-CASE-CHIPSEA-001', app_user.id, organization.id,
  '技术交流活动现场采写（案例演示）',
  TIMESTAMPTZ '2026-09-10 09:30:00+08', '深圳市南山区案例会场',
  '活动资料包含技术说明、应用场景、演讲提纲和经客户确认的公开信息。',
  '完成现场事实采集和一篇供客户审核的活动稿件。',
  '科技产业从业者、合作伙伴与企业客户', 'ONSITE_WRITING',
  1, 1, 980.00, 980.00, '案例联系人', '13800001001',
  '活动结束后提交一篇初稿，按约定完成修改和定稿。',
  'NEAREST_AVAILABLE', TIMESTAMPTZ '2026-09-11 18:00:00+08', 'IN_PROGRESS'
FROM app_user
JOIN organization ON organization.id = app_user.organization_id
WHERE app_user.username = 'chipsea.case@demo.winpress.cn'
ON CONFLICT (requirement_no) DO NOTHING;

INSERT INTO project (
  project_no, requirement_id, organization_id, customer_id, project_name, owner_operator_id,
  budget, planned_start_at, planned_end_at, status
)
SELECT 'PRJ-CASE-CHIPSEA-001', requirement.id, requirement.organization_id, requirement.customer_id,
  '芯海科技技术交流活动传播（案例演示）', operator.id,
  12800.00, TIMESTAMPTZ '2026-09-08 09:00:00+08', TIMESTAMPTZ '2026-09-18 18:00:00+08', 'CLIENT_REVIEW'
FROM customer_requirement requirement
CROSS JOIN app_user operator
WHERE requirement.requirement_no = 'REQ-CASE-CHIPSEA-001'
AND operator.username = 'operator@winpress.cn'
ON CONFLICT (project_no) DO NOTHING;

INSERT INTO customer_requirement (
  requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, due_at, status
)
SELECT 'REQ-CASE-CHIPSEA-002', app_user.id, organization.id,
  '技术专题稿件直编发布（案例演示）', TIMESTAMPTZ '2026-06-18 10:00:00+08', '线上项目',
  '客户已提供经内部确认的技术专题稿件、配图和对外署名信息。',
  '按科技、产业和企业服务方向选择渠道，确认价格、排期和发布结果。',
  '科技产业读者、企业客户与合作伙伴', 'DIRECT_PUBLISHING',
  TIMESTAMPTZ '2026-06-26 18:00:00+08', 'COMPLETED'
FROM app_user
JOIN organization ON organization.id = app_user.organization_id
WHERE app_user.username = 'chipsea.case@demo.winpress.cn'
ON CONFLICT (requirement_no) DO NOTHING;

INSERT INTO project (
  project_no, requirement_id, organization_id, customer_id, project_name, owner_operator_id,
  budget, planned_start_at, planned_end_at, status
)
SELECT 'PRJ-CASE-CHIPSEA-002', requirement.id, requirement.organization_id, requirement.customer_id,
  '芯海科技技术专题直编发布（案例演示）', operator.id,
  1600.00, TIMESTAMPTZ '2026-06-18 10:00:00+08', TIMESTAMPTZ '2026-06-26 18:00:00+08', 'MONITORING'
FROM customer_requirement requirement
CROSS JOIN app_user operator
WHERE requirement.requirement_no = 'REQ-CASE-CHIPSEA-002'
AND operator.username = 'operator@winpress.cn'
ON CONFLICT (project_no) DO NOTHING;

-- 金蝶集团：大会媒体邀请与研究内容直编发布。
INSERT INTO customer_requirement (
  requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, due_at, status
)
SELECT 'REQ-CASE-KINGDEE-001', app_user.id, organization.id,
  '企业管理主题大会媒体邀请（案例演示）',
  TIMESTAMPTZ '2026-08-20 09:00:00+08', '深圳市案例会议中心',
  '客户已确认大会主题、议程、可公开嘉宾信息和媒体沟通材料。',
  '围绕企业管理与数字化议题筛选拟邀媒体，记录邀请、回复和到场安排。',
  '企业管理者、产业伙伴与财经科技媒体', 'MEDIA_PR',
  TIMESTAMPTZ '2026-08-24 18:00:00+08', 'IN_PROGRESS'
FROM app_user
JOIN organization ON organization.id = app_user.organization_id
WHERE app_user.username = 'kingdee.case@demo.winpress.cn'
ON CONFLICT (requirement_no) DO NOTHING;

INSERT INTO project (
  project_no, requirement_id, organization_id, customer_id, project_name, owner_operator_id,
  budget, planned_start_at, planned_end_at, status
)
SELECT 'PRJ-CASE-KINGDEE-001', requirement.id, requirement.organization_id, requirement.customer_id,
  '金蝶集团大会媒体邀请（案例演示）', operator.id,
  26000.00, TIMESTAMPTZ '2026-08-08 09:00:00+08', TIMESTAMPTZ '2026-08-25 18:00:00+08', 'PUBLISHING'
FROM customer_requirement requirement
CROSS JOIN app_user operator
WHERE requirement.requirement_no = 'REQ-CASE-KINGDEE-001'
AND operator.username = 'operator@winpress.cn'
ON CONFLICT (project_no) DO NOTHING;

INSERT INTO customer_requirement (
  requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, due_at, status
)
SELECT 'REQ-CASE-KINGDEE-002', app_user.id, organization.id,
  '企业管理研究内容直编发稿（案例演示）',
  TIMESTAMPTZ '2026-05-12 10:00:00+08', '线上项目',
  '客户已提供研究文章定稿、摘要、配图和发布署名。',
  '按财经、企业服务和科技方向完成渠道复核、下单与结果归档。',
  '企业管理者、专业服务机构和行业读者', 'DIRECT_PUBLISHING',
  TIMESTAMPTZ '2026-05-20 18:00:00+08', 'COMPLETED'
FROM app_user
JOIN organization ON organization.id = app_user.organization_id
WHERE app_user.username = 'kingdee.case@demo.winpress.cn'
ON CONFLICT (requirement_no) DO NOTHING;

INSERT INTO project (
  project_no, requirement_id, organization_id, customer_id, project_name, owner_operator_id,
  budget, planned_start_at, planned_end_at, status
)
SELECT 'PRJ-CASE-KINGDEE-002', requirement.id, requirement.organization_id, requirement.customer_id,
  '金蝶集团研究内容发布（案例演示）', operator.id,
  1600.00, TIMESTAMPTZ '2026-05-12 10:00:00+08', TIMESTAMPTZ '2026-05-20 18:00:00+08', 'COMPLETED'
FROM customer_requirement requirement
CROSS JOIN app_user operator
WHERE requirement.requirement_no = 'REQ-CASE-KINGDEE-002'
AND operator.username = 'operator@winpress.cn'
ON CONFLICT (project_no) DO NOTHING;

-- 圣奥集团：新闻发布会与展会现场采写。
INSERT INTO customer_requirement (
  requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, due_at, status
)
SELECT 'REQ-CASE-SUNON-001', app_user.id, organization.id,
  '办公空间主题发布会（案例演示）',
  TIMESTAMPTZ '2026-09-22 14:00:00+08', '杭州市案例发布中心',
  '客户已确认活动主题、基础议程、展示内容、嘉宾范围和现场联系人。',
  '统筹发布会信息、媒体方向、现场素材、采写安排和会后传播。',
  '企业客户、设计与办公空间行业从业者、财经和产业媒体', 'NEWS_CONFERENCE',
  TIMESTAMPTZ '2026-09-28 18:00:00+08', 'IN_PROGRESS'
FROM app_user
JOIN organization ON organization.id = app_user.organization_id
WHERE app_user.username = 'sunon.case@demo.winpress.cn'
ON CONFLICT (requirement_no) DO NOTHING;

INSERT INTO project (
  project_no, requirement_id, organization_id, customer_id, project_name, owner_operator_id,
  budget, planned_start_at, planned_end_at, status
)
SELECT 'PRJ-CASE-SUNON-001', requirement.id, requirement.organization_id, requirement.customer_id,
  '圣奥集团办公空间主题发布会（案例演示）', operator.id,
  32800.00, TIMESTAMPTZ '2026-09-05 09:00:00+08', TIMESTAMPTZ '2026-09-30 18:00:00+08', 'IN_PROGRESS'
FROM customer_requirement requirement
CROSS JOIN app_user operator
WHERE requirement.requirement_no = 'REQ-CASE-SUNON-001'
AND operator.username = 'operator@winpress.cn'
ON CONFLICT (project_no) DO NOTHING;

INSERT INTO customer_requirement (
  requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, service_days, writer_count, unit_price,
  estimated_amount, onsite_contact_name, onsite_contact_mobile, deliverable_requirement,
  matching_preference, due_at, status
)
SELECT 'REQ-CASE-SUNON-002', app_user.id, organization.id,
  '行业展会现场采写（案例演示）',
  TIMESTAMPTZ '2026-10-15 09:00:00+08', '上海市青浦区案例展馆',
  '展会资料包含展示主题、产品信息、现场议程和可公开采访对象。',
  '安排写手完成现场事实采集、重点观点整理和一篇活动稿件。',
  '办公空间行业客户、合作伙伴与专业读者', 'ONSITE_WRITING',
  2, 1, 980.00, 1960.00, '案例联系人', '13800001003',
  '首日整理现场要点，第二日完成稿件；客户确认后归档。',
  'NEAREST_AVAILABLE', TIMESTAMPTZ '2026-10-17 18:00:00+08', 'SUBMITTED'
FROM app_user
JOIN organization ON organization.id = app_user.organization_id
WHERE app_user.username = 'sunon.case@demo.winpress.cn'
ON CONFLICT (requirement_no) DO NOTHING;

INSERT INTO project (
  project_no, requirement_id, organization_id, customer_id, project_name, owner_operator_id,
  budget, planned_start_at, planned_end_at, status
)
SELECT 'PRJ-CASE-SUNON-002', requirement.id, requirement.organization_id, requirement.customer_id,
  '圣奥集团行业展会现场采写（案例演示）', NULL,
  1960.00, TIMESTAMPTZ '2026-10-15 09:00:00+08', TIMESTAMPTZ '2026-10-17 18:00:00+08', 'PLANNING'
FROM customer_requirement requirement
WHERE requirement.requirement_no = 'REQ-CASE-SUNON-002'
ON CONFLICT (project_no) DO NOTHING;

-- 广明源：照明技术交流现场采写；后续传播另行下单。
INSERT INTO customer_requirement (
  requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, service_days, writer_count, unit_price,
  estimated_amount, onsite_contact_name, onsite_contact_mobile, deliverable_requirement,
  matching_preference, due_at, status
)
SELECT 'REQ-CASE-GMY-001', app_user.id, organization.id,
  '照明技术交流活动现场采写（案例演示）',
  TIMESTAMPTZ '2026-08-28 14:00:00+08', '佛山市案例活动中心',
  '客户已准备活动流程、技术资料、公开演讲提纲和现场联系人信息。',
  '现场完成事实和观点采集，形成一篇供客户审核的活动稿件。',
  '照明行业客户、渠道伙伴与制造业读者', 'ONSITE_WRITING',
  1, 2, 980.00, 1960.00, '案例联系人', '13800001004',
  '两名写手分别负责现场记录与技术资料整理，提交一篇客户审核稿。',
  'NEAREST_AVAILABLE', TIMESTAMPTZ '2026-08-30 18:00:00+08', 'IN_PROGRESS'
FROM app_user
JOIN organization ON organization.id = app_user.organization_id
WHERE app_user.username = 'gmy.case@demo.winpress.cn'
ON CONFLICT (requirement_no) DO NOTHING;

INSERT INTO project (
  project_no, requirement_id, organization_id, customer_id, project_name, owner_operator_id,
  budget, planned_start_at, planned_end_at, status
)
SELECT 'PRJ-CASE-GMY-001', requirement.id, requirement.organization_id, requirement.customer_id,
  '广明源照明技术交流传播（案例演示）', operator.id,
  16800.00, TIMESTAMPTZ '2026-08-22 09:00:00+08', TIMESTAMPTZ '2026-09-05 18:00:00+08', 'PUBLISHING'
FROM customer_requirement requirement
CROSS JOIN app_user operator
WHERE requirement.requirement_no = 'REQ-CASE-GMY-001'
AND operator.username = 'operator@winpress.cn'
ON CONFLICT (project_no) DO NOTHING;

INSERT INTO customer_requirement (
  requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, due_at, status
)
SELECT 'REQ-CASE-GMY-002', app_user.id, organization.id,
  '照明技术专题稿件直编发布（案例演示）',
  TIMESTAMPTZ '2026-04-16 10:00:00+08', '线上项目',
  '客户已提供技术专题定稿、应用场景图片和对外署名。',
  '按照明、制造、科技和区域新闻方向选择直编渠道并核验结果。',
  '照明产业客户、制造业读者与渠道伙伴', 'DIRECT_PUBLISHING',
  TIMESTAMPTZ '2026-04-24 18:00:00+08', 'COMPLETED'
FROM app_user
JOIN organization ON organization.id = app_user.organization_id
WHERE app_user.username = 'gmy.case@demo.winpress.cn'
ON CONFLICT (requirement_no) DO NOTHING;

INSERT INTO project (
  project_no, requirement_id, organization_id, customer_id, project_name, owner_operator_id,
  budget, planned_start_at, planned_end_at, status
)
SELECT 'PRJ-CASE-GMY-002', requirement.id, requirement.organization_id, requirement.customer_id,
  '广明源照明技术专题发布（案例演示）', operator.id,
  880.00, TIMESTAMPTZ '2026-04-16 10:00:00+08', TIMESTAMPTZ '2026-04-24 18:00:00+08', 'COMPLETED'
FROM customer_requirement requirement
CROSS JOIN app_user operator
WHERE requirement.requirement_no = 'REQ-CASE-GMY-002'
AND operator.username = 'operator@winpress.cn'
ON CONFLICT (project_no) DO NOTHING;

-- Writing tasks and manuscripts.
INSERT INTO editorial_task (
  task_no, project_id, requirement_id, assigned_operator_id, writer_name, writing_brief, due_at, status
)
SELECT data.task_no, project.id, requirement.id, operator.id, data.writer_name,
       data.writing_brief, data.due_at, data.status
FROM (
  VALUES
    ('EDT-CASE-CHIPSEA-001', 'PRJ-CASE-CHIPSEA-001', 'REQ-CASE-CHIPSEA-001', '深圳科技内容写手', '现场记录活动事实、技术要点和经确认的发言内容，先提交客户审核稿。', TIMESTAMPTZ '2026-09-11 12:00:00+08', 'COMPLETED'),
    ('EDT-CASE-KINGDEE-001', 'PRJ-CASE-KINGDEE-001', 'REQ-CASE-KINGDEE-001', '企业服务内容编辑', '根据大会资料整理媒体沟通稿，保持企业管理术语和嘉宾表述准确。', TIMESTAMPTZ '2026-08-15 18:00:00+08', 'COMPLETED'),
    ('EDT-CASE-SUNON-002', 'PRJ-CASE-SUNON-002', 'REQ-CASE-SUNON-002', NULL, '展会现场采写，优先匹配上海及周边且熟悉办公空间行业的写手。', TIMESTAMPTZ '2026-10-17 18:00:00+08', 'PENDING_ASSIGNMENT'),
    ('EDT-CASE-GMY-001', 'PRJ-CASE-GMY-001', 'REQ-CASE-GMY-001', '制造业内容写手组', '现场记录与技术资料整理分工完成，稿件须经客户确认后进入媒体邀请。', TIMESTAMPTZ '2026-08-30 12:00:00+08', 'COMPLETED')
) AS data(task_no, project_no, requirement_no, writer_name, writing_brief, due_at, status)
JOIN project ON project.project_no = data.project_no
JOIN customer_requirement requirement ON requirement.requirement_no = data.requirement_no
LEFT JOIN app_user operator ON operator.username = 'operator@winpress.cn'
ON CONFLICT (task_no) DO NOTHING;

INSERT INTO manuscript (
  manuscript_no, project_id, editorial_task_id, title, current_version_no, status
)
SELECT data.manuscript_no, project.id, editorial_task.id, data.title, data.current_version_no, data.status
FROM (
  VALUES
    ('MAN-CASE-CHIPSEA-001', 'PRJ-CASE-CHIPSEA-001', 'EDT-CASE-CHIPSEA-001', '技术交流活动新闻稿（案例演示）', 1, 'CLIENT_REVIEW'),
    ('MAN-CASE-CHIPSEA-002', 'PRJ-CASE-CHIPSEA-002', NULL, '技术专题稿件（案例演示）', 1, 'MONITORING'),
    ('MAN-CASE-KINGDEE-001', 'PRJ-CASE-KINGDEE-001', 'EDT-CASE-KINGDEE-001', '企业管理主题大会媒体沟通稿（案例演示）', 2, 'PUBLISHING'),
    ('MAN-CASE-KINGDEE-002', 'PRJ-CASE-KINGDEE-002', NULL, '企业管理研究文章（案例演示）', 1, 'PUBLISHED'),
    ('MAN-CASE-GMY-001', 'PRJ-CASE-GMY-001', 'EDT-CASE-GMY-001', '照明技术交流活动新闻稿（案例演示）', 2, 'PUBLISHING'),
    ('MAN-CASE-GMY-002', 'PRJ-CASE-GMY-002', NULL, '照明技术专题文章（案例演示）', 1, 'PUBLISHED')
) AS data(manuscript_no, project_no, editorial_task_no, title, current_version_no, status)
JOIN project ON project.project_no = data.project_no
LEFT JOIN editorial_task ON editorial_task.task_no = data.editorial_task_no
ON CONFLICT (manuscript_no) DO NOTHING;

INSERT INTO manuscript_version (
  version_no, manuscript_id, version_number, title, summary, content, change_note,
  submitted_by, reviewed_by, reviewed_at, review_comment, status
)
SELECT data.version_no, manuscript.id, data.version_number, data.title, data.summary, data.content,
       data.change_note, operator.id, customer.id, data.reviewed_at, data.review_comment, data.status
FROM (
  VALUES
    ('VER-CASE-CHIPSEA-001', 'MAN-CASE-CHIPSEA-001', 1, '技术交流活动新闻稿（案例演示）', '等待客户确认技术表述和现场引语。', '案例稿件正文用于展示客户审核、修改和定稿流程；不作为品牌公开新闻材料。', '现场资料整理后提交首稿', NULL::timestamptz, NULL, 'CLIENT_REVIEW'),
    ('VER-CASE-CHIPSEA-002', 'MAN-CASE-CHIPSEA-002', 1, '技术专题稿件（案例演示）', '客户定稿已进入直编发布和链接核验。', '案例稿件正文用于展示技术专题直编发稿、渠道下单和结果归档。', '客户提供定稿', TIMESTAMPTZ '2026-06-17 16:00:00+08', '稿件和配图已确认。', 'APPROVED'),
    ('VER-CASE-KINGDEE-001', 'MAN-CASE-KINGDEE-001', 1, '企业管理主题大会媒体沟通稿初稿（案例演示）', '首稿用于核对议题、议程和嘉宾表述。', '案例稿件正文用于展示大会媒体沟通稿的事实核验过程。', '根据大会材料提交首稿', TIMESTAMPTZ '2026-08-13 16:00:00+08', '请调整议题顺序。', 'CLIENT_RETURNED'),
    ('VER-CASE-KINGDEE-002', 'MAN-CASE-KINGDEE-001', 2, '企业管理主题大会媒体沟通稿（案例演示）', '已按客户意见调整议题顺序和媒体沟通要点。', '案例稿件正文用于展示媒体邀请前的定稿、媒体方向和邀请记录。', '根据客户意见完成第二版', TIMESTAMPTZ '2026-08-15 16:00:00+08', '内容确认，可进入媒体邀请。', 'APPROVED'),
    ('VER-CASE-KINGDEE-003', 'MAN-CASE-KINGDEE-002', 1, '企业管理研究文章（案例演示）', '客户定稿用于展示多渠道直编发布。', '案例稿件正文用于展示客户定稿的渠道选择、下单和结果核验。', '客户提供定稿', TIMESTAMPTZ '2026-05-11 15:00:00+08', '内容与署名确认。', 'APPROVED'),
    ('VER-CASE-GMY-001', 'MAN-CASE-GMY-001', 1, '照明技术交流活动新闻稿初稿（案例演示）', '首稿用于核对技术术语和现场观点。', '案例稿件正文用于展示现场采写后的客户审核与修改。', '现场采写后提交首稿', TIMESTAMPTZ '2026-08-29 10:00:00+08', '请补充应用场景说明。', 'CLIENT_RETURNED'),
    ('VER-CASE-GMY-002', 'MAN-CASE-GMY-001', 2, '照明技术交流活动新闻稿（案例演示）', '已补充应用场景并完成客户确认。', '案例稿件正文用于展示采写定稿、媒体邀请和执行记录。', '根据客户意见补充应用场景', TIMESTAMPTZ '2026-08-30 10:00:00+08', '事实和表述确认。', 'APPROVED'),
    ('VER-CASE-GMY-003', 'MAN-CASE-GMY-002', 1, '照明技术专题文章（案例演示）', '客户定稿用于展示行业与区域媒体直编发布。', '案例稿件正文用于展示媒体筛选、报价确认、发布和链接归档。', '客户提供定稿', TIMESTAMPTZ '2026-04-15 16:00:00+08', '稿件与图片确认。', 'APPROVED')
) AS data(version_no, manuscript_no, version_number, title, summary, content, change_note, reviewed_at, review_comment, status)
JOIN manuscript ON manuscript.manuscript_no = data.manuscript_no
JOIN project ON project.id = manuscript.project_id
JOIN app_user customer ON customer.id = project.customer_id
CROSS JOIN app_user operator
WHERE operator.username = 'operator@winpress.cn'
ON CONFLICT (version_no) DO NOTHING;

UPDATE manuscript SET approved_version_id = version.id
FROM manuscript_version version
WHERE version.manuscript_id = manuscript.id
AND version.status = 'APPROVED'
AND manuscript.manuscript_no IN (
  'MAN-CASE-CHIPSEA-002', 'MAN-CASE-KINGDEE-001', 'MAN-CASE-KINGDEE-002',
  'MAN-CASE-GMY-001', 'MAN-CASE-GMY-002'
);

-- 圣奥发布会统筹清单与拟邀媒体候选。
INSERT INTO conference_project (
  conference_no, project_id, conference_type, conference_format, attendee_scale, media_goal,
  agenda_status, venue_status, contact_name, contact_mobile, status
)
SELECT 'CNF-CASE-SUNON-001', project.id, 'PRODUCT_RELEASE', 'OFFLINE', '150–200 人',
  '围绕办公空间、制造与设计议题确认媒体方向，并安排现场内容与会后传播。',
  'CONFIRMED', 'CONFIRMED', '案例联系人', '13800001003', 'EXECUTING'
FROM project
WHERE project.project_no = 'PRJ-CASE-SUNON-001'
ON CONFLICT (conference_no) DO NOTHING;

INSERT INTO conference_work_item (
  item_no, conference_project_id, sort_order, title, detail, due_at,
  assigned_operator_id, status, note, completed_at
)
SELECT data.item_no, conference_project.id, data.sort_order, data.title, data.detail,
       data.due_at, operator.id, data.status, data.note, data.completed_at
FROM (
  VALUES
    ('CNF-CASE-SUNON-001-01', 1::smallint, '确认活动信息', '核对主题、时间、地点、议程、展示内容和现场联系人。', TIMESTAMPTZ '2026-09-12 18:00:00+08', 'COMPLETED', '活动基础信息已确认。', TIMESTAMPTZ '2026-09-12 15:00:00+08'),
    ('CNF-CASE-SUNON-001-02', 2::smallint, '确定媒体方向', '按财经、产业、设计和办公空间方向整理拟邀范围。', TIMESTAMPTZ '2026-09-14 18:00:00+08', 'IN_PROGRESS', '首轮候选已进入确认。', NULL::timestamptz),
    ('CNF-CASE-SUNON-001-03', 3::smallint, '准备现场素材', '整理新闻资料、展示图片、嘉宾信息和可公开引用内容。', TIMESTAMPTZ '2026-09-18 18:00:00+08', 'IN_PROGRESS', '正在核对图片和嘉宾介绍。', NULL::timestamptz),
    ('CNF-CASE-SUNON-001-04', 4::smallint, '现场执行协调', '确认签到、媒体接待、采访区域、采写与摄影衔接。', TIMESTAMPTZ '2026-09-22 12:00:00+08', 'PENDING', NULL, NULL::timestamptz),
    ('CNF-CASE-SUNON-001-05', 5::smallint, '安排会后传播', '确认稿件审核、直编渠道、媒体反馈和成果归档安排。', TIMESTAMPTZ '2026-09-25 18:00:00+08', 'PENDING', NULL, NULL::timestamptz)
) AS data(item_no, sort_order, title, detail, due_at, status, note, completed_at)
CROSS JOIN conference_project
CROSS JOIN app_user operator
WHERE conference_project.conference_no = 'CNF-CASE-SUNON-001'
AND operator.username = 'operator@winpress.cn'
ON CONFLICT (item_no) DO NOTHING;

INSERT INTO conference_media_candidate (
  candidate_no, conference_project_id, candidate_key, candidate_type,
  external_media_id, media_name, media_attribute,
  province, city, channel_form, category, coverage_tags, operation_note, fit_score,
  selected_by, managed_operator_id, selected_at, invited_at, responded_at, note, status
)
SELECT data.candidate_no, conference_project.id, 'MEDIA:' || data.external_media_id, 'MEDIA',
       data.external_media_id, data.media_name,
       data.media_attribute, data.province, data.city, data.channel_form, data.category,
       data.coverage_tags, data.operation_note, data.fit_score, customer.id, operator.id,
       data.selected_at, data.invited_at, data.responded_at, data.note, data.status
FROM (
  VALUES
    ('CMC-CASE-SUNON-001', 'CASE-SUNON-FINANCE', '财经商业媒体候选', '财经商业', '浙江', '杭州', '客户端与网站', '财经商业', '企业经营、产业合作', '项目负责人确认联系人后发出邀请。', 88, TIMESTAMPTZ '2026-09-10 10:00:00+08', TIMESTAMPTZ '2026-09-15 10:00:00+08', TIMESTAMPTZ '2026-09-16 15:00:00+08', '已回复，等待确认到场安排。', 'RESPONDED'),
    ('CMC-CASE-SUNON-002', 'CASE-SUNON-DESIGN', '设计与办公空间媒体候选', '行业媒体', '上海', '上海', '网站与新媒体', '办公空间', '空间设计、办公方式', '适合展示内容和空间应用议题。', 94, TIMESTAMPTZ '2026-09-10 10:10:00+08', TIMESTAMPTZ '2026-09-15 10:10:00+08', TIMESTAMPTZ '2026-09-17 11:00:00+08', '已确认到场，具体采访由媒体自主决定。', 'ATTENDING'),
    ('CMC-CASE-SUNON-003', 'CASE-SUNON-MANUFACTURING', '制造产业媒体候选', '产业媒体', '浙江', '杭州', '网站图文', '制造产业', '制造、供应链、绿色生产', '适合制造与产业协同议题。', 86, TIMESTAMPTZ '2026-09-10 10:20:00+08', NULL::timestamptz, NULL::timestamptz, '待项目负责人确认是否发出邀请。', 'READY_TO_INVITE'),
    ('CMC-CASE-SUNON-004', 'CASE-SUNON-LOCAL', '杭州本地新闻媒体候选', '地方媒体', '浙江', '杭州', '网站与客户端', '综合新闻', '本地企业、城市产业', '适合活动信息和本地产业议题。', 82, TIMESTAMPTZ '2026-09-10 10:30:00+08', TIMESTAMPTZ '2026-09-15 10:30:00+08', NULL::timestamptz, '邀请已发出，等待回复。', 'INVITED')
) AS data(candidate_no, external_media_id, media_name, media_attribute, province, city, channel_form, category, coverage_tags, operation_note, fit_score, selected_at, invited_at, responded_at, note, status)
JOIN conference_project ON conference_project.conference_no = 'CNF-CASE-SUNON-001'
JOIN project ON project.id = conference_project.project_id
JOIN app_user customer ON customer.id = project.customer_id
CROSS JOIN app_user operator
WHERE operator.username = 'operator@winpress.cn'
ON CONFLICT (candidate_no) DO NOTHING;

-- Publish tasks: media invitation and direct publishing.
INSERT INTO publish_task (
  task_no, project_id, manuscript_id, manuscript_version_id, channel_id, channel_type,
  assigned_operator_id, planned_publish_at, actual_publish_at, execution_note, status
)
SELECT data.task_no, project.id, manuscript.id, version.id, channel.id, data.channel_type,
       operator.id, data.planned_at, data.actual_at, data.execution_note, data.status
FROM (
  VALUES
    ('PUB-CASE-CHIPSEA-001', 'PRJ-CASE-CHIPSEA-002', 'MAN-CASE-CHIPSEA-002', 'VER-CASE-CHIPSEA-002', 'CH-DEMO-DP-001', 'DIRECT_PUBLISHING', TIMESTAMPTZ '2026-06-20 10:00:00+08', TIMESTAMPTZ '2026-06-21 15:00:00+08', '客户定稿已通过渠道审核并完成案例发布记录。', 'COMPLETED'),
    ('PUB-CASE-CHIPSEA-002', 'PRJ-CASE-CHIPSEA-002', 'MAN-CASE-CHIPSEA-002', 'VER-CASE-CHIPSEA-002', 'CH-DEMO-DP-003', 'DIRECT_PUBLISHING', TIMESTAMPTZ '2026-06-21 10:00:00+08', TIMESTAMPTZ '2026-06-23 11:00:00+08', '行业渠道案例记录已完成。', 'CLIENT_ACCEPTED'),
    ('PUB-CASE-KINGDEE-001', 'PRJ-CASE-KINGDEE-001', 'MAN-CASE-KINGDEE-001', 'VER-CASE-KINGDEE-002', 'CH-DEMO-PR-001', 'MEDIA_PR', TIMESTAMPTZ '2026-08-16 10:00:00+08', NULL::timestamptz, '已发出第一批邀请，正在跟进回复。', 'IN_PROGRESS'),
    ('PUB-CASE-KINGDEE-002', 'PRJ-CASE-KINGDEE-001', 'MAN-CASE-KINGDEE-001', 'VER-CASE-KINGDEE-002', 'CH-DEMO-PR-001', 'MEDIA_PR', TIMESTAMPTZ '2026-08-17 10:00:00+08', NULL::timestamptz, '第二批行业方向候选等待确认。', 'PENDING_EXECUTION'),
    ('PUB-CASE-KINGDEE-003', 'PRJ-CASE-KINGDEE-002', 'MAN-CASE-KINGDEE-002', 'VER-CASE-KINGDEE-003', 'CH-DEMO-DP-001', 'DIRECT_PUBLISHING', TIMESTAMPTZ '2026-05-14 10:00:00+08', TIMESTAMPTZ '2026-05-15 15:00:00+08', '财经客户端案例发布记录已完成。', 'CLIENT_ACCEPTED'),
    ('PUB-CASE-KINGDEE-004', 'PRJ-CASE-KINGDEE-002', 'MAN-CASE-KINGDEE-002', 'VER-CASE-KINGDEE-003', 'CH-DEMO-DP-003', 'DIRECT_PUBLISHING', TIMESTAMPTZ '2026-05-15 10:00:00+08', TIMESTAMPTZ '2026-05-17 14:00:00+08', '企业服务行业媒体案例发布记录已完成。', 'CLIENT_ACCEPTED'),
    ('PUB-CASE-GMY-001', 'PRJ-CASE-GMY-001', 'MAN-CASE-GMY-001', 'VER-CASE-GMY-002', 'CH-DEMO-PR-001', 'MEDIA_PR', TIMESTAMPTZ '2026-08-31 10:00:00+08', NULL::timestamptz, '制造与科技方向媒体邀请已发出。', 'IN_PROGRESS'),
    ('PUB-CASE-GMY-002', 'PRJ-CASE-GMY-002', 'MAN-CASE-GMY-002', 'VER-CASE-GMY-003', 'CH-DEMO-DP-002', 'DIRECT_PUBLISHING', TIMESTAMPTZ '2026-04-18 10:00:00+08', TIMESTAMPTZ '2026-04-19 11:00:00+08', '区域新闻门户案例记录已完成。', 'CLIENT_ACCEPTED'),
    ('PUB-CASE-GMY-003', 'PRJ-CASE-GMY-002', 'MAN-CASE-GMY-002', 'VER-CASE-GMY-003', 'CH-DEMO-DP-003', 'DIRECT_PUBLISHING', TIMESTAMPTZ '2026-04-18 10:30:00+08', TIMESTAMPTZ '2026-04-21 12:00:00+08', '企业服务行业媒体案例记录已完成。', 'CLIENT_ACCEPTED')
) AS data(task_no, project_no, manuscript_no, version_no, channel_no, channel_type, planned_at, actual_at, execution_note, status)
JOIN project ON project.project_no = data.project_no
JOIN manuscript ON manuscript.manuscript_no = data.manuscript_no
JOIN manuscript_version version ON version.version_no = data.version_no
JOIN publish_channel channel ON channel.channel_no = data.channel_no
CROSS JOIN app_user operator
WHERE operator.username = 'operator@winpress.cn'
ON CONFLICT (task_no) DO NOTHING;

INSERT INTO media_pr_invitation (
  invitation_no, publish_task_id, journalist_name, media_name, media_attribute, media_province,
  media_city, media_channel_form, media_category, media_fit_score, beat,
  invited_at, response_at, response_note, status
)
SELECT data.invitation_no, publish_task.id, data.journalist_name, data.media_name,
       data.media_attribute, data.province, data.city, data.channel_form, data.category,
       data.fit_score, data.beat, data.invited_at, data.response_at, data.response_note, data.status
FROM (
  VALUES
    ('INV-CASE-KINGDEE-001', 'PUB-CASE-KINGDEE-001', '待媒体确认', '财经商业媒体候选', '财经商业', '广东', '深圳', '客户端与网站', '财经商业', 91, '企业管理与数字化', TIMESTAMPTZ '2026-08-16 10:00:00+08', TIMESTAMPTZ '2026-08-17 14:00:00+08', '媒体已回复，正在确认活动安排；是否采访或报道由媒体自主决定。', 'RESPONDED'),
    ('INV-CASE-KINGDEE-002', 'PUB-CASE-KINGDEE-002', NULL, '企业服务行业媒体候选', '行业媒体', '北京', '北京', '网站与新媒体', '企业服务', 87, '企业管理与服务生态', NULL::timestamptz, NULL::timestamptz, '等待项目负责人确认联系人和邀请时间。', 'PENDING'),
    ('INV-CASE-GMY-001', 'PUB-CASE-GMY-001', '待媒体确认', '制造与照明行业媒体候选', '产业媒体', '广东', '广州', '网站与新媒体', '制造产业', 89, '照明技术与制造', TIMESTAMPTZ '2026-08-31 10:00:00+08', NULL::timestamptz, '邀请已发出，等待媒体回复。', 'INVITED')
) AS data(invitation_no, task_no, journalist_name, media_name, media_attribute, province, city, channel_form, category, fit_score, beat, invited_at, response_at, response_note, status)
JOIN publish_task ON publish_task.task_no = data.task_no
ON CONFLICT (invitation_no) DO NOTHING;

-- Direct publishing orders reuse the current demo quotes and preserve the quoted price.
INSERT INTO direct_publish_order (
  order_no, publish_task_id, channel_quote_id, article_title, amount,
  price_valid_until, requirement_note, status
)
SELECT data.order_no, publish_task.id, quote.id, manuscript.title, quote.customer_price,
       quote.valid_until, '案例演示订单：使用客户确认稿件，发布要求以渠道审核为准。', data.status
FROM (
  VALUES
    ('ORD-CASE-CHIPSEA-001', 'PUB-CASE-CHIPSEA-001', 'COMPLETED'),
    ('ORD-CASE-CHIPSEA-002', 'PUB-CASE-CHIPSEA-002', 'COMPLETED'),
    ('ORD-CASE-KINGDEE-001', 'PUB-CASE-KINGDEE-003', 'COMPLETED'),
    ('ORD-CASE-KINGDEE-002', 'PUB-CASE-KINGDEE-004', 'COMPLETED'),
    ('ORD-CASE-GMY-001', 'PUB-CASE-GMY-002', 'COMPLETED'),
    ('ORD-CASE-GMY-002', 'PUB-CASE-GMY-003', 'COMPLETED')
) AS data(order_no, task_no, status)
JOIN publish_task ON publish_task.task_no = data.task_no
JOIN manuscript ON manuscript.id = publish_task.manuscript_id
JOIN LATERAL (
  SELECT channel_quote.*
  FROM channel_quote
  WHERE channel_quote.channel_id = publish_task.channel_id
  ORDER BY channel_quote.valid_until DESC, channel_quote.id DESC
  LIMIT 1
) quote ON TRUE
ON CONFLICT (order_no) DO NOTHING;

INSERT INTO direct_publish_order_item (
  item_no, order_id, channel_id, channel_quote_id, unit_price, publish_url, status
)
SELECT 'ITEM-' || SUBSTRING(direct_publish_order.order_no FROM 5), direct_publish_order.id,
       publish_task.channel_id, direct_publish_order.channel_quote_id, direct_publish_order.amount,
       'https://example.com/winpress/' || LOWER(REPLACE(direct_publish_order.order_no, 'ORD-', '')),
       'PUBLISHED'
FROM direct_publish_order
JOIN publish_task ON publish_task.id = direct_publish_order.publish_task_id
WHERE direct_publish_order.order_no LIKE 'ORD-CASE-%'
ON CONFLICT (item_no) DO NOTHING;

INSERT INTO result_link (
  result_no, project_id, publish_task_id, channel_name, title, url,
  published_at, verified_by, verified_at, status
)
SELECT 'RES-' || SUBSTRING(publish_task.task_no FROM 5), project.id, publish_task.id,
       publish_channel.channel_name || '（案例记录）', manuscript.title,
       'https://example.com/winpress/' || LOWER(REPLACE(publish_task.task_no, 'PUB-', '')),
       publish_task.actual_publish_at, operator.id,
       publish_task.actual_publish_at + INTERVAL '1 hour', 'VERIFIED'
FROM publish_task
JOIN project ON project.id = publish_task.project_id
JOIN manuscript ON manuscript.id = publish_task.manuscript_id
JOIN publish_channel ON publish_channel.id = publish_task.channel_id
CROSS JOIN app_user operator
WHERE publish_task.task_no IN (
  'PUB-CASE-CHIPSEA-001', 'PUB-CASE-CHIPSEA-002',
  'PUB-CASE-KINGDEE-003', 'PUB-CASE-KINGDEE-004',
  'PUB-CASE-GMY-002', 'PUB-CASE-GMY-003'
)
AND operator.username = 'operator@winpress.cn'
ON CONFLICT (result_no) DO NOTHING;

INSERT INTO monitoring_record (
  monitoring_no, project_id, publish_task_id, monitored_at,
  metric_name, metric_value, metric_text, source_url, status
)
SELECT 'MON-' || SUBSTRING(publish_task.task_no FROM 5), project.id, publish_task.id,
       publish_task.actual_publish_at + INTERVAL '2 hours',
       'LINK_AVAILABLE', 1, '案例链接已记录并完成访问检查。',
       'https://example.com/winpress/' || LOWER(REPLACE(publish_task.task_no, 'PUB-', '')),
       'VERIFIED'
FROM publish_task
JOIN project ON project.id = publish_task.project_id
WHERE publish_task.task_no IN (
  'PUB-CASE-CHIPSEA-001', 'PUB-CASE-CHIPSEA-002',
  'PUB-CASE-KINGDEE-003', 'PUB-CASE-KINGDEE-004',
  'PUB-CASE-GMY-002', 'PUB-CASE-GMY-003'
)
ON CONFLICT (monitoring_no) DO NOTHING;

INSERT INTO settlement_order (
  settlement_no, project_id, organization_id, amount, paid_amount,
  currency, due_at, paid_at, invoice_no, status
)
SELECT data.settlement_no, project.id, project.organization_id, data.amount, data.paid_amount,
       'CNY', data.due_at, data.paid_at, data.invoice_no, data.status
FROM (
  VALUES
    ('SET-CASE-CHIPSEA-001', 'PRJ-CASE-CHIPSEA-002', 1600.00, 1600.00, TIMESTAMPTZ '2026-07-10 18:00:00+08', TIMESTAMPTZ '2026-06-28 10:00:00+08', 'INV-CASE-CHIPSEA-001', 'PAID'),
    ('SET-CASE-KINGDEE-001', 'PRJ-CASE-KINGDEE-001', 26000.00, 0.00, TIMESTAMPTZ '2026-09-05 18:00:00+08', NULL::timestamptz, NULL, 'PENDING'),
    ('SET-CASE-KINGDEE-002', 'PRJ-CASE-KINGDEE-002', 1600.00, 1600.00, TIMESTAMPTZ '2026-06-05 18:00:00+08', TIMESTAMPTZ '2026-05-25 10:00:00+08', 'INV-CASE-KINGDEE-001', 'PAID'),
    ('SET-CASE-SUNON-001', 'PRJ-CASE-SUNON-001', 32800.00, 0.00, TIMESTAMPTZ '2026-10-10 18:00:00+08', NULL::timestamptz, NULL, 'PENDING'),
    ('SET-CASE-GMY-001', 'PRJ-CASE-GMY-002', 880.00, 880.00, TIMESTAMPTZ '2026-05-10 18:00:00+08', TIMESTAMPTZ '2026-04-28 10:00:00+08', 'INV-CASE-GMY-001', 'PAID')
) AS data(settlement_no, project_no, amount, paid_amount, due_at, paid_at, invoice_no, status)
JOIN project ON project.project_no = data.project_no
ON CONFLICT (settlement_no) DO NOTHING;

INSERT INTO operation_log (
  log_no, actor_id, actor_role, action, target_type, target_id, detail_json, status
)
SELECT data.log_no, actor.id, data.actor_role, data.action, data.target_type,
       data.target_id, data.detail_json::jsonb, 'SUCCESS'
FROM (
  VALUES
    ('LOG-CASE-CHIPSEA-001', 'chipsea.case@demo.winpress.cn', 'CUSTOMER', 'CREATE_CASE_DATA', 'PROJECT', 'PRJ-CASE-CHIPSEA-001', '{"caseData":true,"brand":"芯海科技","stage":"client_review"}'),
    ('LOG-CASE-CHIPSEA-002', 'operator@winpress.cn', 'PUBLISH_OPERATOR', 'VERIFY_RESULT', 'PROJECT', 'PRJ-CASE-CHIPSEA-002', '{"caseData":true,"links":2}'),
    ('LOG-CASE-KINGDEE-001', 'operator@winpress.cn', 'PUBLISH_OPERATOR', 'FOLLOW_MEDIA_INVITATION', 'PROJECT', 'PRJ-CASE-KINGDEE-001', '{"caseData":true,"invitationStatus":"RESPONDED"}'),
    ('LOG-CASE-KINGDEE-002', 'operator@winpress.cn', 'PUBLISH_OPERATOR', 'COMPLETE_PUBLISH_TASK', 'PROJECT', 'PRJ-CASE-KINGDEE-002', '{"caseData":true,"links":2}'),
    ('LOG-CASE-SUNON-001', 'operator@winpress.cn', 'PUBLISH_OPERATOR', 'UPDATE_CONFERENCE_WORK_ITEM', 'PROJECT', 'PRJ-CASE-SUNON-001', '{"caseData":true,"completedItems":1}'),
    ('LOG-CASE-SUNON-002', 'operator@winpress.cn', 'PUBLISH_OPERATOR', 'SELECT_CONFERENCE_MEDIA', 'PROJECT', 'PRJ-CASE-SUNON-001', '{"caseData":true,"candidates":4}'),
    ('LOG-CASE-GMY-001', 'operator@winpress.cn', 'PUBLISH_OPERATOR', 'FOLLOW_MEDIA_INVITATION', 'PROJECT', 'PRJ-CASE-GMY-001', '{"caseData":true,"invitationStatus":"INVITED"}'),
    ('LOG-CASE-GMY-002', 'operator@winpress.cn', 'PUBLISH_OPERATOR', 'COMPLETE_PUBLISH_TASK', 'PROJECT', 'PRJ-CASE-GMY-002', '{"caseData":true,"links":2}')
) AS data(log_no, actor_username, actor_role, action, target_type, target_id, detail_json)
JOIN app_user actor ON actor.username = data.actor_username
ON CONFLICT (log_no) DO NOTHING;

COMMIT;
