BEGIN;

-- Production bootstrap intentionally creates only role/permission metadata and the public
-- service price. It creates no account, password, supplier, customer project, or demo case.
INSERT INTO sys_role (role_code, role_name, status) VALUES
  ('CUSTOMER', '客户', 'ACTIVE'),
  ('PUBLISH_OPERATOR', '服务运营', 'ACTIVE'),
  ('PLATFORM_ADMIN', '平台运营', 'ACTIVE')
ON CONFLICT (role_code) DO UPDATE SET role_name=EXCLUDED.role_name, status=EXCLUDED.status;

INSERT INTO sys_permission (permission_code, permission_name, status) VALUES
  ('requirement:create', '创建传播需求', 'ACTIVE'),
  ('project:read_own', '查看本方项目', 'ACTIVE'),
  ('manuscript:review', '审核稿件', 'ACTIVE'),
  ('publish:submit', '提交发布计划', 'ACTIVE'),
  ('task:execute', '执行发布任务', 'ACTIVE'),
  ('result:submit', '回填发布成果', 'ACTIVE'),
  ('project:dispatch', '调度项目', 'ACTIVE'),
  ('channel:manage', '管理发布渠道', 'ACTIVE'),
  ('settlement:manage', '管理结算', 'ACTIVE'),
  ('audit:read', '查看操作日志', 'ACTIVE')
ON CONFLICT (permission_code) DO UPDATE SET permission_name=EXCLUDED.permission_name, status=EXCLUDED.status;

INSERT INTO role_permission (role_id, permission_id, status)
SELECT r.id, p.id, 'ACTIVE'
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('requirement:create','project:read_own','manuscript:review','publish:submit')
WHERE r.role_code='CUSTOMER'
ON CONFLICT (role_id, permission_id) DO UPDATE SET status=EXCLUDED.status;

INSERT INTO role_permission (role_id, permission_id, status)
SELECT r.id, p.id, 'ACTIVE'
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('project:read_own','task:execute','result:submit')
WHERE r.role_code='PUBLISH_OPERATOR'
ON CONFLICT (role_id, permission_id) DO UPDATE SET status=EXCLUDED.status;

INSERT INTO role_permission (role_id, permission_id, status)
SELECT r.id, p.id, 'ACTIVE'
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='PLATFORM_ADMIN'
ON CONFLICT (role_id, permission_id) DO UPDATE SET status=EXCLUDED.status;

INSERT INTO service_price_book
  (price_no, service_code, service_name, billing_unit, list_price, currency, version_no, status)
VALUES
  ('PRICE-ONSITE-WRITING-V1', 'ONSITE_WRITING', '云采写现场服务', 'PERSON_DAY', 980.00, 'CNY', 1, 'ACTIVE')
ON CONFLICT (service_code, version_no) DO UPDATE
  SET service_name=EXCLUDED.service_name, billing_unit=EXCLUDED.billing_unit,
      list_price=EXCLUDED.list_price, currency=EXCLUDED.currency, status=EXCLUDED.status;

COMMIT;
