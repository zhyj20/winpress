BEGIN;

INSERT INTO organization (id, organization_no, name, organization_type, contact_name, contact_phone, contact_email, status) VALUES
  (1, 'ORG-WINPRESS', '云发布运营中心', 'PLATFORM', '平台管理员', '13800000001', 'admin@winpress.cn', 'ACTIVE'),
  (2, 'ORG-DEMO-CUSTOMER', '品牌工作台', 'CUSTOMER', '陈经理', '13800000003', 'client@demo.cn', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_role (id, role_code, role_name, status) VALUES
  (1, 'CUSTOMER', '客户', 'ACTIVE'),
  (2, 'PUBLISH_OPERATOR', '服务运营', 'ACTIVE'),
  (3, 'PLATFORM_ADMIN', '平台运营', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_permission (id, permission_code, permission_name, status) VALUES
  (1, 'requirement:create', '创建传播需求', 'ACTIVE'),
  (2, 'project:read_own', '查看本方项目', 'ACTIVE'),
  (3, 'manuscript:review', '审核稿件', 'ACTIVE'),
  (4, 'publish:submit', '提交发布计划', 'ACTIVE'),
  (5, 'task:execute', '执行发布任务', 'ACTIVE'),
  (6, 'result:submit', '回填发布成果', 'ACTIVE'),
  (7, 'project:dispatch', '调度项目', 'ACTIVE'),
  (8, 'channel:manage', '管理发布渠道', 'ACTIVE'),
  (9, 'settlement:manage', '管理结算', 'ACTIVE'),
  (10, 'audit:read', '查看操作日志', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_user (id, user_no, organization_id, username, password_hash, display_name, mobile, email, status) VALUES
  (1, 'USR-ADMIN-001', 1, 'admin@winpress.cn', '$2a$12$EB4OoSVKBpGvFsPLd8IlI.oso.L4LiCymapS821WeTpy4Yn.4LlWa', '平台管理员', '13800000001', 'admin@winpress.cn', 'ACTIVE'),
  (2, 'USR-OPERATOR-001', 1, 'operator@winpress.cn', '$2a$12$r2jjzHHuNPxzu1jhvYHll.kLOc.O.vaNBx3BSpZBvJwUUmlKLi8K2', '服务运营', '13800000002', 'operator@winpress.cn', 'ACTIVE'),
  (3, 'USR-CLIENT-001', 2, 'client@demo.cn', '$2a$12$ojrsvoMksV4cBxdcotBGFeX0iEkeON4zP4DhF7WS2ewFs2pwZqqBe', '陈经理', '13800000003', 'client@demo.cn', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_role (id, user_id, role_id, status) VALUES
  (1, 1, 3, 'ACTIVE'),
  (2, 2, 2, 'ACTIVE'),
  (3, 3, 1, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id, status)
SELECT 1, id, 'ACTIVE' FROM sys_permission WHERE permission_code IN ('requirement:create','project:read_own','manuscript:review','publish:submit')
ON CONFLICT (role_id, permission_id) DO NOTHING;
INSERT INTO role_permission (role_id, permission_id, status)
SELECT 2, id, 'ACTIVE' FROM sys_permission WHERE permission_code IN ('project:read_own','task:execute','result:submit')
ON CONFLICT (role_id, permission_id) DO NOTHING;
INSERT INTO role_permission (role_id, permission_id, status)
SELECT 3, id, 'ACTIVE' FROM sys_permission
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO customer_requirement (
  id, requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, due_at, status
) VALUES (
  1, 'REQ-DEMO-001', 3, 2, '新品战略合作直编发稿', CURRENT_TIMESTAMP + INTERVAL '10 days', '深圳',
  '客户已提供并确认新品战略合作通稿，现选择直编渠道提交发布。',
  '按客户选定的财经与行业媒体渠道完成稿件送审、发布跟进和结果归档。',
  '企业管理者、行业合作伙伴与财经媒体读者', 'DIRECT_PUBLISHING', CURRENT_TIMESTAMP + INTERVAL '14 days', 'IN_PROGRESS'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO project (
  id, project_no, requirement_id, organization_id, customer_id, project_name, owner_operator_id,
  budget, planned_start_at, planned_end_at, status
) VALUES (
  1, 'PRJ-DEMO-001', 1, 2, 3, '新品战略合作直编发稿', 2, 1080.00,
  CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '20 days', 'PUBLISHING'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_requirement (
  id, requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, due_at, status
) VALUES (
  3, 'REQ-DEMO-003', 3, 2, '2026 产品发布会项目', CURRENT_TIMESTAMP + INTERVAL '30 days', '广州市天河区示例发布中心',
  '客户将发布新一代企业服务产品，已确认活动主题、发布嘉宾、基础议程和可公开引用的产品信息。',
  '统筹发布会会务信息、媒体方向、现场素材与会后传播安排。', '行业客户、合作伙伴与财经科技媒体读者',
  'NEWS_CONFERENCE', CURRENT_TIMESTAMP + INTERVAL '35 days', 'SUBMITTED'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO project (
  id, project_no, requirement_id, organization_id, customer_id, project_name,
  budget, planned_start_at, planned_end_at, status
) VALUES (
  3, 'PRJ-DEMO-003', 3, 2, 3, '2026 产品发布会项目',
  NULL, CURRENT_TIMESTAMP + INTERVAL '30 days', CURRENT_TIMESTAMP + INTERVAL '35 days', 'PLANNING'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO conference_project (
  id, conference_no, project_id, conference_type, conference_format, theme, event_time,
  event_location, attendee_scale, media_goal, guest_plan, agenda_plan, venue_plan,
  media_direction, communication_goal, agenda_status, venue_status, contact_name, contact_mobile, status
) VALUES (
  1, 'CNF-DEMO-001', 3, 'PRODUCT_RELEASE', 'OFFLINE', '2026 产品发布会',
  CURRENT_TIMESTAMP + INTERVAL '30 days', '广州市天河区示例发布中心', '100–200 人',
  '邀请财经、产业与科技媒体关注新品发布，并在会后确认稿件与传播安排。',
  '产品负责人、合作伙伴代表和主持人名单待最终确认。',
  '主题发布、产品演示、合作伙伴发言、媒体问答。',
  '主会场、签到区和媒体采访区已初步确认。',
  '财经、企业服务与科技产业线口。',
  '清楚说明产品更新和行业合作进展。',
  'CONFIRMED', 'CONFIRMED', '陈经理', '13800000003', 'PLANNING'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO conference_work_item (
  id, item_no, conference_project_id, sort_order, phase, title, detail, due_at, assigned_operator_id, status, note, completed_at
) VALUES
  (1, 'CNF-ITM-DEMO-001', 1, 1, 'PRE_EVENT', '确认活动信息', '核对活动主题、时间、地点、议程和现场联系人。', CURRENT_TIMESTAMP + INTERVAL '27 days', 2, 'COMPLETED', '活动信息已确认。', CURRENT_TIMESTAMP - INTERVAL '1 day'),
  (2, 'CNF-ITM-DEMO-002', 1, 2, 'PRE_EVENT', '确定媒体方向', '确认邀请范围、媒体重点和对外沟通材料。', CURRENT_TIMESTAMP + INTERVAL '26 days', 2, 'IN_PROGRESS', '正在确认媒体方向。', NULL),
  (3, 'CNF-ITM-DEMO-003', 1, 3, 'PRE_EVENT', '准备现场素材', '整理新闻资料、嘉宾信息、图片和可公开引用的内容。', CURRENT_TIMESTAMP + INTERVAL '24 days', 2, 'PENDING', NULL, NULL),
  (4, 'CNF-ITM-DEMO-004', 1, 4, 'ONSITE', '现场执行协调', '确认签到、采访、采写和现场对接安排。', CURRENT_TIMESTAMP + INTERVAL '30 days', 2, 'PENDING', NULL, NULL),
  (5, 'CNF-ITM-DEMO-005', 1, 5, 'POST_EVENT', '安排会后传播', '确认稿件、直编发布和成果归档的后续安排。', CURRENT_TIMESTAMP + INTERVAL '31 days', 2, 'PENDING', NULL, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_requirement (
  id, requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
  objective, target_audience, requested_service, service_days, writer_count, unit_price,
  estimated_amount, onsite_contact_name, onsite_contact_mobile, deliverable_requirement,
  matching_preference, due_at, status
) VALUES (
  2, 'REQ-DEMO-002', 3, 2, '产业论坛现场采写', CURRENT_TIMESTAMP + INTERVAL '20 days', '上海市浦东新区示例会议中心',
  '客户将举办产业论坛，已确认主办单位、议程、演讲嘉宾和可公开引用的活动资料。',
  '安排一名专业写手到场记录并完成一篇论坛新闻稿。', '行业客户、合作伙伴与企业管理者',
  'ONSITE_WRITING', 1, 1, 980.00, 980.00, '陈经理', '13800000003',
  '活动结束后 6 小时内提交初稿，约定范围内修改一次。', 'NEAREST_AVAILABLE',
  CURRENT_TIMESTAMP + INTERVAL '21 days', 'SUBMITTED'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO project (
  id, project_no, requirement_id, organization_id, customer_id, project_name, owner_operator_id,
  budget, planned_start_at, planned_end_at, status
) VALUES (
  2, 'PRJ-DEMO-002', 2, 2, 3, '产业论坛现场采写', NULL, 980.00,
  CURRENT_TIMESTAMP + INTERVAL '20 days', CURRENT_TIMESTAMP + INTERVAL '21 days', 'PLANNING'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO editorial_task (
  id, task_no, project_id, requirement_id, assigned_operator_id, writer_name, writing_brief, due_at, status
) VALUES (
  2, 'EDT-DEMO-002', 2, 2, NULL, NULL,
  '会议活动现场采写，平台按活动所在地及周边优先匹配专业写手。',
  CURRENT_TIMESTAMP + INTERVAL '21 days', 'PENDING_ASSIGNMENT'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO manuscript (id, manuscript_no, project_id, editorial_task_id, title, current_version_no, status) VALUES
  (1, 'MAN-DEMO-001', 1, NULL, '示例品牌发布新一代企业服务产品并启动行业合作', 2, 'MONITORING')
ON CONFLICT (id) DO NOTHING;

INSERT INTO manuscript_version (
  id, version_no, manuscript_id, version_number, title, summary, content, change_note, submitted_by,
  reviewed_by, reviewed_at, review_comment, status
) VALUES
  (1, 'VER-DEMO-001', 1, 1, '示例品牌发布新一代企业服务产品', '首稿用于客户核验事实与引语。',
   '示例品牌今日发布新一代企业服务产品。产品面向企业管理场景，首批合作机构将参与联合测试。',
   '客户上传待确认版本', 3, 3, CURRENT_TIMESTAMP - INTERVAL '2 days', '请补充合作机构名称。', 'CLIENT_RETURNED'),
  (2, 'VER-DEMO-002', 1, 2, '示例品牌发布新一代企业服务产品并启动行业合作', '已补充合作范围、发布日期和负责人引语。',
   '示例品牌今日发布新一代企业服务产品，并与两家行业机构启动联合测试。企业负责人表示，首期合作将围绕数据治理和服务效率展开。',
   '客户上传已确认版本', 3, 3, CURRENT_TIMESTAMP - INTERVAL '1 day', '事实与表述确认，可进入发布。', 'APPROVED')
ON CONFLICT (id) DO NOTHING;

UPDATE manuscript SET approved_version_id = 2 WHERE id = 1;

INSERT INTO publish_channel (
  id, channel_no, channel_name, channel_type, category, region, publish_form, expected_days, link_support, public_notes, status
) VALUES
  (1, 'CH-DEMO-PR-001', '财经记者邀约', 'MEDIA_PR', '财经商业', '全国', '记者自主采访报道', 7, TRUE, '由记者依据选题价值和采访情况独立判断是否报道。', 'ACTIVE'),
  (2, 'CH-DEMO-DP-001', '中央级财经客户端', 'DIRECT_PUBLISHING', '财经商业', '全国', '客户端图文', 2, TRUE, '栏目、标题和发布时间以媒体审核结果为准。', 'ACTIVE'),
  (3, 'CH-DEMO-DP-002', '区域主流新闻门户', 'DIRECT_PUBLISHING', '综合新闻', '广东', '网站图文', 2, TRUE, '支持带图发布，链接形式以媒体实际页面为准。', 'ACTIVE'),
  (6, 'CH-DEMO-DP-003', '企业服务行业媒体', 'DIRECT_PUBLISHING', '企业服务', '全国', '网站图文', 3, TRUE, '适合产品、案例与行业观察类稿件。', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO supplier (
  id, supplier_no, supplier_name, supplier_type, service_scope, internal_note, status
) VALUES (
  1, 'SUP-DEMO-DIRECT-001', '本机演示直编服务商', 'DIRECT_PUBLISHING',
  '仅用于本机演示渠道的直编发稿订单流转',
  '演示数据，不代表真实合作供应商或对外合作关系。', 'ACTIVE'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO channel_quote (
  id, quote_no, channel_id, supplier_id, customer_tier, cost_price, customer_price, currency, valid_from, valid_until, public_terms, status
) VALUES
  (1, 'QUO-DEMO-001', 2, 1, 'STANDARD', 800.00, 1080.00, 'CNY', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '30 days', '客户服务价含媒体沟通与发布跟进，提交后复核稿件和排期。', 'ACTIVE'),
  (2, 'QUO-DEMO-002', 3, 1, 'STANDARD', 240.00, 360.00, 'CNY', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '30 days', '客户服务价含发布跟进，特殊行业和周末排期需另行确认。', 'ACTIVE'),
  (3, 'QUO-DEMO-003', 6, 1, 'STANDARD', 360.00, 520.00, 'CNY', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '30 days', '客户服务价含基础审稿与发布跟进。', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO supplier_channel (
  id, mapping_no, supplier_id, channel_id, external_product_code, service_scope, priority, status
) VALUES
  (1, 'SUPMAP-DEMO-002', 1, 2, 'CH-DEMO-DP-001', '本机演示直编发稿', 10, 'ACTIVE'),
  (2, 'SUPMAP-DEMO-003', 1, 3, 'CH-DEMO-DP-002', '本机演示直编发稿', 10, 'ACTIVE'),
  (3, 'SUPMAP-DEMO-006', 1, 6, 'CH-DEMO-DP-003', '本机演示直编发稿', 10, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO publish_task (
  id, task_no, project_id, manuscript_id, manuscript_version_id, channel_id, channel_type,
  assigned_operator_id, planned_publish_at, actual_publish_at, execution_note, status
) VALUES
  (1, 'PUB-DEMO-001', 1, 1, 2, 2, 'DIRECT_PUBLISHING', 2, CURRENT_TIMESTAMP - INTERVAL '12 hours', CURRENT_TIMESTAMP - INTERVAL '8 hours', '媒体审核通过并完成发布。', 'COMPLETED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO direct_publish_order (
  id, order_no, publish_task_id, channel_quote_id, article_title, amount, price_valid_until, requirement_note, status
) VALUES (
  1, 'ORD-DEMO-001', 1, 1, '示例品牌发布新一代企业服务产品并启动行业合作', 1080.00,
  CURRENT_TIMESTAMP + INTERVAL '29 days', '使用客户确认的第二版稿件，保留来源署名。', 'COMPLETED'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO direct_publish_order_item (
  id, item_no, order_id, channel_id, channel_quote_id, unit_price, publish_url, status
) VALUES (
  1, 'ITEM-DEMO-001', 1, 2, 1, 1080.00, 'https://example.com/demo-published-result', 'PUBLISHED'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO result_link (
  id, result_no, project_id, publish_task_id, channel_name, title, url, published_at, verified_by, verified_at, status
) VALUES (
  1, 'RES-DEMO-001', 1, 1, '中央级财经客户端', '示例品牌发布新一代企业服务产品并启动行业合作',
  'https://example.com/demo-published-result', CURRENT_TIMESTAMP - INTERVAL '8 hours', 2, CURRENT_TIMESTAMP - INTERVAL '7 hours', 'VERIFIED'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO monitoring_record (
  id, monitoring_no, project_id, publish_task_id, monitored_at, metric_name, metric_value, metric_text, source_url, status
) VALUES
  (1, 'MON-DEMO-001', 1, 1, CURRENT_TIMESTAMP - INTERVAL '4 hours', 'LINK_AVAILABLE', 1, '链接访问正常', 'https://example.com/demo-published-result', 'VERIFIED'),
  (2, 'MON-DEMO-002', 1, 1, CURRENT_TIMESTAMP - INTERVAL '3 hours', 'SEARCH_VISIBILITY', 1, '标题关键词可检索', 'https://example.com/demo-published-result', 'VERIFIED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO settlement_order (
  id, settlement_no, project_id, organization_id, amount, paid_amount, currency, due_at, status
) VALUES (
  1, 'SET-DEMO-001', 1, 2, 1080.00, 0, 'CNY', CURRENT_TIMESTAMP + INTERVAL '15 days', 'PENDING'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO operation_log (
  id, log_no, actor_id, actor_role, action, target_type, target_id, detail_json, status
) VALUES (
  1, 'LOG-DEMO-001', 3, 'CUSTOMER', 'APPROVE_MANUSCRIPT', 'MANUSCRIPT', 'MAN-DEMO-001', '{"versionNumber":2}'::jsonb, 'SUCCESS'),
  (2, 'LOG-DEMO-002', 2, 'PUBLISH_OPERATOR', 'COMPLETE_PUBLISH_TASK', 'PUBLISH_TASK', 'PUB-DEMO-001', '{"result":"published"}'::jsonb, 'SUCCESS')
ON CONFLICT (id) DO NOTHING;

INSERT INTO service_price_book (
  id, price_no, service_code, service_name, billing_unit, list_price, currency, version_no, status
) VALUES (
  1, 'PRICE-ONSITE-WRITING-V1', 'ONSITE_WRITING', '云采写现场服务', 'PERSON_DAY', 980.00, 'CNY', 1, 'ACTIVE'
) ON CONFLICT (service_code, version_no) DO NOTHING;

INSERT INTO writer_profile (
  id, writer_no, user_id, province, city, service_radius_km, expertise_tags, availability_status, status
) VALUES (
  1, 'WRT-DEMO-001', 2, '广东', '广州', 120.00, '商业新闻、会议活动、品牌内容', 'AVAILABLE', 'ACTIVE'
) ON CONFLICT (user_id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('organization','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM organization), 1));
SELECT setval(pg_get_serial_sequence('app_user','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM app_user), 1));
SELECT setval(pg_get_serial_sequence('sys_role','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM sys_role), 1));
SELECT setval(pg_get_serial_sequence('sys_permission','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM sys_permission), 1));
SELECT setval(pg_get_serial_sequence('publish_channel','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM publish_channel), 1));
SELECT setval(pg_get_serial_sequence('channel_quote','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM channel_quote), 1));
SELECT setval(pg_get_serial_sequence('supplier','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM supplier), 1));
SELECT setval(pg_get_serial_sequence('supplier_channel','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM supplier_channel), 1));
SELECT setval(pg_get_serial_sequence('user_role','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM user_role), 1));
SELECT setval(pg_get_serial_sequence('role_permission','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM role_permission), 1));
SELECT setval(pg_get_serial_sequence('customer_requirement','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM customer_requirement), 1));
SELECT setval(pg_get_serial_sequence('project','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM project), 1));
SELECT setval(pg_get_serial_sequence('conference_project','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM conference_project), 1));
SELECT setval(pg_get_serial_sequence('conference_work_item','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM conference_work_item), 1));
SELECT setval(pg_get_serial_sequence('editorial_task','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM editorial_task), 1));
SELECT setval(pg_get_serial_sequence('service_price_book','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM service_price_book), 1));
SELECT setval(pg_get_serial_sequence('writer_profile','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM writer_profile), 1));
SELECT setval(pg_get_serial_sequence('writing_assignment','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM writing_assignment), 1));
SELECT setval(pg_get_serial_sequence('manuscript','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM manuscript), 1));
SELECT setval(pg_get_serial_sequence('manuscript_version','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM manuscript_version), 1));
SELECT setval(pg_get_serial_sequence('manuscript_lock','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM manuscript_lock), 1));
SELECT setval(pg_get_serial_sequence('media_outlet','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM media_outlet), 1));
SELECT setval(pg_get_serial_sequence('media_contact','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM media_contact), 1));
SELECT setval(pg_get_serial_sequence('publish_offering','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM publish_offering), 1));
SELECT setval(pg_get_serial_sequence('publish_plan','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM publish_plan), 1));
SELECT setval(pg_get_serial_sequence('publish_plan_item','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM publish_plan_item), 1));
SELECT setval(pg_get_serial_sequence('publish_task','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM publish_task), 1));
SELECT setval(pg_get_serial_sequence('media_pr_invitation','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM media_pr_invitation), 1));
SELECT setval(pg_get_serial_sequence('direct_publish_order','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM direct_publish_order), 1));
SELECT setval(pg_get_serial_sequence('direct_publish_order_item','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM direct_publish_order_item), 1));
SELECT setval(pg_get_serial_sequence('monitoring_record','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM monitoring_record), 1));
SELECT setval(pg_get_serial_sequence('result_link','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM result_link), 1));
SELECT setval(pg_get_serial_sequence('settlement_order','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM settlement_order), 1));
SELECT setval(pg_get_serial_sequence('file_asset','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM file_asset), 1));
SELECT setval(pg_get_serial_sequence('operation_log','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM operation_log), 1));

COMMIT;
