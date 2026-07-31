BEGIN;

CREATE TABLE IF NOT EXISTS service_intake_task (
  id BIGSERIAL PRIMARY KEY,
  intake_task_no VARCHAR(40) NOT NULL UNIQUE,
  project_id BIGINT NOT NULL UNIQUE REFERENCES project(id) ON DELETE CASCADE,
  service_type VARCHAR(40) NOT NULL CHECK (service_type IN ('MEDIA_PR','DIRECT_PUBLISHING')),
  title VARCHAR(160) NOT NULL,
  customer_visible_note VARCHAR(500),
  assigned_operator_id BIGINT REFERENCES app_user(id),
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING_ACCEPTANCE'
    CHECK (status IN ('PENDING_ACCEPTANCE','PENDING_INFO','IN_PROGRESS','COMPLETED','CANCELLED')),
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_service_intake_task_status
  ON service_intake_task(status, updated_at DESC);

-- Existing open media/direct requirements receive a traceable intake task. This migration does
-- not change their price, supplier mapping, or historical execution records.
INSERT INTO service_intake_task
  (intake_task_no, project_id, service_type, title, customer_visible_note, assigned_operator_id, status)
SELECT
  'INTAKE-MIG-' || p.id,
  p.id,
  r.requested_service,
  CASE WHEN r.requested_service='MEDIA_PR' THEN '确认媒体邀请范围' ELSE '确认稿件与发稿范围' END,
  '需求已提交，等待项目负责人确认服务范围。',
  p.owner_operator_id,
  CASE WHEN p.owner_operator_id IS NULL THEN 'PENDING_ACCEPTANCE' ELSE 'IN_PROGRESS' END
FROM project p
JOIN customer_requirement r ON r.id=p.requirement_id
WHERE r.requested_service IN ('MEDIA_PR','DIRECT_PUBLISHING')
  AND NOT EXISTS (SELECT 1 FROM service_intake_task sit WHERE sit.project_id=p.id);

COMMIT;
