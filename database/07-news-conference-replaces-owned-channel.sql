BEGIN;

-- 云发布不再配置或执行客户自有渠道。保留历史数据用于审计，但不再对客展示或允许新建。
ALTER TABLE publish_channel DROP CONSTRAINT IF EXISTS publish_channel_channel_type_check;
ALTER TABLE publish_task DROP CONSTRAINT IF EXISTS publish_task_channel_type_check;

UPDATE publish_channel
SET channel_type = 'LEGACY_OWNED_CHANNEL',
    status = 'INACTIVE',
    public_notes = '历史记录已归档；该类渠道不再由云发布配置或执行。',
    updated_at = CURRENT_TIMESTAMP
WHERE channel_type = 'OWNED_CHANNEL';

ALTER TABLE publish_channel ADD CONSTRAINT publish_channel_channel_type_check
  CHECK (channel_type IN ('MEDIA_PR','DIRECT_PUBLISHING','LEGACY_OWNED_CHANNEL'));

UPDATE publish_task
SET channel_type = 'LEGACY_OWNED_CHANNEL',
    status = CASE WHEN status IN ('COMPLETED','CLIENT_ACCEPTED') THEN status ELSE 'EXCEPTION' END,
    execution_note = COALESCE(execution_note, '历史自有渠道记录已归档，不再继续执行。'),
    updated_at = CURRENT_TIMESTAMP
WHERE channel_type = 'OWNED_CHANNEL';

ALTER TABLE publish_task ADD CONSTRAINT publish_task_channel_type_check
  CHECK (channel_type IN ('MEDIA_PR','DIRECT_PUBLISHING','LEGACY_OWNED_CHANNEL'));

DO $$
BEGIN
  IF to_regclass('public.owned_channel_record') IS NOT NULL
     AND to_regclass('public.legacy_owned_channel_record') IS NULL THEN
    ALTER TABLE owned_channel_record RENAME TO legacy_owned_channel_record;
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS conference_project (
  id BIGSERIAL PRIMARY KEY,
  conference_no VARCHAR(40) NOT NULL UNIQUE,
  project_id BIGINT NOT NULL UNIQUE REFERENCES project(id) ON DELETE CASCADE,
  conference_type VARCHAR(40) NOT NULL CHECK (conference_type IN ('PRODUCT_RELEASE','STRATEGIC_SIGNING','INDUSTRY_FORUM','CORPORATE_EVENT')),
  conference_format VARCHAR(40) NOT NULL CHECK (conference_format IN ('OFFLINE','HYBRID','ONLINE')),
  attendee_scale VARCHAR(40),
  media_goal TEXT NOT NULL,
  agenda_status VARCHAR(30) NOT NULL DEFAULT 'PREPARING' CHECK (agenda_status IN ('PREPARING','CONFIRMED')),
  venue_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (venue_status IN ('PENDING','CONFIRMED')),
  contact_name VARCHAR(80) NOT NULL,
  contact_mobile VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING_SCOPE' CHECK (status IN ('PENDING_SCOPE','PLANNING','EXECUTING','COMPLETED','CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conference_work_item (
  id BIGSERIAL PRIMARY KEY,
  item_no VARCHAR(40) NOT NULL UNIQUE,
  conference_project_id BIGINT NOT NULL REFERENCES conference_project(id) ON DELETE CASCADE,
  sort_order SMALLINT NOT NULL CHECK (sort_order BETWEEN 1 AND 20),
  title VARCHAR(120) NOT NULL,
  detail TEXT,
  due_at TIMESTAMPTZ,
  assigned_operator_id BIGINT REFERENCES app_user(id),
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','IN_PROGRESS','NEEDS_INFO','BLOCKED','COMPLETED')),
  note TEXT,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(conference_project_id, sort_order)
);

CREATE INDEX IF NOT EXISTS idx_conference_project_status ON conference_project(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_conference_work_item_status ON conference_work_item(conference_project_id, status, sort_order);

DROP TRIGGER IF EXISTS trg_conference_project_updated_at ON conference_project;
CREATE TRIGGER trg_conference_project_updated_at BEFORE UPDATE ON conference_project
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

DROP TRIGGER IF EXISTS trg_conference_work_item_updated_at ON conference_work_item;
CREATE TRIGGER trg_conference_work_item_updated_at BEFORE UPDATE ON conference_work_item
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMIT;
