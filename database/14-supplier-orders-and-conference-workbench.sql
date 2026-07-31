BEGIN;

ALTER TABLE conference_project
  ADD COLUMN IF NOT EXISTS theme VARCHAR(240),
  ADD COLUMN IF NOT EXISTS event_time TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS event_location VARCHAR(240),
  ADD COLUMN IF NOT EXISTS guest_plan TEXT,
  ADD COLUMN IF NOT EXISTS agenda_plan TEXT,
  ADD COLUMN IF NOT EXISTS venue_plan TEXT,
  ADD COLUMN IF NOT EXISTS media_direction TEXT,
  ADD COLUMN IF NOT EXISTS communication_goal TEXT;

UPDATE conference_project cp
SET theme = COALESCE(cp.theme, p.project_name),
    event_time = COALESCE(cp.event_time, r.event_time),
    event_location = COALESCE(cp.event_location, r.event_location),
    communication_goal = COALESCE(cp.communication_goal, r.objective)
FROM project p
JOIN customer_requirement r ON r.id = p.requirement_id
WHERE cp.project_id = p.id;

ALTER TABLE conference_work_item
  ADD COLUMN IF NOT EXISTS phase VARCHAR(30) NOT NULL DEFAULT 'PRE_EVENT';

UPDATE conference_work_item
SET phase = CASE
  WHEN item_no = 'CNF-ITM-DEMO-004' THEN 'ONSITE'
  WHEN item_no = 'CNF-ITM-DEMO-005' THEN 'POST_EVENT'
  WHEN sort_order <= 6 THEN 'PRE_EVENT'
  WHEN sort_order = 7 THEN 'ONSITE'
  ELSE 'POST_EVENT'
END;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'conference_work_item_phase_check'
      AND conrelid = 'conference_work_item'::regclass
  ) THEN
    ALTER TABLE conference_work_item
      ADD CONSTRAINT conference_work_item_phase_check
      CHECK (phase IN ('PRE_EVENT','ONSITE','POST_EVENT'));
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS supplier (
  id BIGSERIAL PRIMARY KEY,
  supplier_no VARCHAR(40) NOT NULL UNIQUE,
  supplier_name VARCHAR(180) NOT NULL,
  supplier_type VARCHAR(40) NOT NULL
    CHECK (supplier_type IN ('MEDIA_PR','DIRECT_PUBLISHING','WRITING','EVENT_SERVICE','MULTI_SERVICE')),
  contact_name VARCHAR(80),
  contact_phone VARCHAR(30),
  contact_email VARCHAR(160),
  service_scope TEXT,
  internal_note TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE channel_quote
  ADD COLUMN IF NOT EXISTS supplier_id BIGINT REFERENCES supplier(id);

CREATE TABLE IF NOT EXISTS supplier_channel (
  id BIGSERIAL PRIMARY KEY,
  mapping_no VARCHAR(40) NOT NULL UNIQUE,
  supplier_id BIGINT NOT NULL REFERENCES supplier(id),
  channel_id BIGINT NOT NULL REFERENCES publish_channel(id),
  external_product_code VARCHAR(120),
  service_scope TEXT,
  priority SMALLINT NOT NULL DEFAULT 100 CHECK (priority BETWEEN 1 AND 999),
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(supplier_id, channel_id)
);

CREATE TABLE IF NOT EXISTS supplier_order (
  id BIGSERIAL PRIMARY KEY,
  supplier_order_no VARCHAR(40) NOT NULL UNIQUE,
  supplier_id BIGINT REFERENCES supplier(id),
  publish_plan_id BIGINT NOT NULL REFERENCES publish_plan(id),
  publish_task_id BIGINT NOT NULL UNIQUE REFERENCES publish_task(id) ON DELETE CASCADE,
  channel_id BIGINT NOT NULL REFERENCES publish_channel(id),
  channel_quote_id BIGINT REFERENCES channel_quote(id),
  customer_price_snapshot NUMERIC(14,2),
  cost_price_snapshot NUMERIC(14,2),
  currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
  article_title VARCHAR(240),
  planned_publish_at TIMESTAMPTZ,
  external_order_no VARCHAR(120),
  submission_note TEXT,
  exception_reason TEXT,
  assigned_operator_id BIGINT REFERENCES app_user(id),
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING_SUBMISSION'
    CHECK (status IN ('PENDING_SUBMISSION','SUBMITTED','ACCEPTED','IN_PROGRESS','EXCEPTION','COMPLETED','CANCELLED')),
  submitted_at TIMESTAMPTZ,
  accepted_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS supplier_order_status_history (
  id BIGSERIAL PRIMARY KEY,
  history_no VARCHAR(40) NOT NULL UNIQUE,
  supplier_order_id BIGINT NOT NULL REFERENCES supplier_order(id) ON DELETE CASCADE,
  previous_status VARCHAR(30),
  current_status VARCHAR(30) NOT NULL,
  note TEXT,
  changed_by BIGINT NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS business_inquiry (
  id BIGSERIAL PRIMARY KEY,
  inquiry_no VARCHAR(40) NOT NULL UNIQUE,
  inquiry_type VARCHAR(40) NOT NULL
    CHECK (inquiry_type IN ('API_INTEGRATION','GENERAL_COOPERATION','SERVICE_CONSULTATION')),
  company_name VARCHAR(160) NOT NULL,
  contact_name VARCHAR(80) NOT NULL,
  mobile VARCHAR(30) NOT NULL,
  email VARCHAR(160),
  message TEXT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'NEW'
    CHECK (status IN ('NEW','CONTACTED','CLOSED')),
  handled_by BIGINT REFERENCES app_user(id),
  handled_at TIMESTAMPTZ,
  handling_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_supplier_type_status
  ON supplier(supplier_type, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_channel_channel
  ON supplier_channel(channel_id, status, priority);
CREATE INDEX IF NOT EXISTS idx_quote_supplier_validity
  ON channel_quote(supplier_id, status, valid_until);
CREATE INDEX IF NOT EXISTS idx_supplier_order_status
  ON supplier_order(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_order_supplier
  ON supplier_order(supplier_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_order_plan
  ON supplier_order(publish_plan_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_order_history
  ON supplier_order_status_history(supplier_order_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_business_inquiry_status
  ON business_inquiry(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_business_inquiry_mobile_time
  ON business_inquiry(mobile, created_at DESC);

DROP TRIGGER IF EXISTS trg_supplier_updated_at ON supplier;
CREATE TRIGGER trg_supplier_updated_at
  BEFORE UPDATE ON supplier
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

DROP TRIGGER IF EXISTS trg_supplier_channel_updated_at ON supplier_channel;
CREATE TRIGGER trg_supplier_channel_updated_at
  BEFORE UPDATE ON supplier_channel
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

DROP TRIGGER IF EXISTS trg_supplier_order_updated_at ON supplier_order;
CREATE TRIGGER trg_supplier_order_updated_at
  BEFORE UPDATE ON supplier_order
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

DROP TRIGGER IF EXISTS trg_business_inquiry_updated_at ON business_inquiry;
CREATE TRIGGER trg_business_inquiry_updated_at
  BEFORE UPDATE ON business_inquiry
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMIT;
