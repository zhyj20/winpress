BEGIN;

CREATE TABLE IF NOT EXISTS service_price_book (
  id BIGSERIAL PRIMARY KEY,
  price_no VARCHAR(40) NOT NULL UNIQUE,
  service_code VARCHAR(40) NOT NULL,
  service_name VARCHAR(120) NOT NULL,
  billing_unit VARCHAR(30) NOT NULL,
  list_price NUMERIC(14,2) NOT NULL CHECK (list_price >= 0),
  currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
  version_no INT NOT NULL DEFAULT 1,
  effective_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  effective_until TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','SUPERSEDED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(service_code, version_no)
);

INSERT INTO service_price_book
  (price_no, service_code, service_name, billing_unit, list_price, currency, version_no, status)
VALUES
  ('PRICE-ONSITE-WRITING-V1', 'ONSITE_WRITING', '云采写现场服务', 'PERSON_DAY', 980.00, 'CNY', 1, 'ACTIVE')
ON CONFLICT (service_code, version_no) DO NOTHING;

CREATE TABLE IF NOT EXISTS writer_profile (
  id BIGSERIAL PRIMARY KEY,
  writer_no VARCHAR(40) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id),
  province VARCHAR(80),
  city VARCHAR(80),
  service_radius_km NUMERIC(10,2),
  expertise_tags TEXT,
  availability_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE'
    CHECK (availability_status IN ('AVAILABLE','BUSY','OFFLINE')),
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS writing_assignment (
  id BIGSERIAL PRIMARY KEY,
  assignment_no VARCHAR(40) NOT NULL UNIQUE,
  editorial_task_id BIGINT NOT NULL UNIQUE REFERENCES editorial_task(id) ON DELETE CASCADE,
  writer_profile_id BIGINT REFERENCES writer_profile(id),
  matching_mode VARCHAR(40) NOT NULL DEFAULT 'NEAREST_AVAILABLE',
  service_location VARCHAR(200),
  distance_km NUMERIC(10,2),
  service_days SMALLINT NOT NULL DEFAULT 1 CHECK (service_days BETWEEN 1 AND 30),
  writer_count SMALLINT NOT NULL DEFAULT 1 CHECK (writer_count BETWEEN 1 AND 10),
  unit_price_snapshot NUMERIC(14,2) NOT NULL CHECK (unit_price_snapshot >= 0),
  estimated_amount_snapshot NUMERIC(14,2) NOT NULL CHECK (estimated_amount_snapshot >= 0),
  offered_at TIMESTAMPTZ,
  responded_at TIMESTAMPTZ,
  response_note TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'WAITING_MATCH'
    CHECK (status IN ('WAITING_MATCH','OFFERED','ACCEPTED','DECLINED','CANCELLED','COMPLETED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO writing_assignment
  (assignment_no, editorial_task_id, matching_mode, service_location, service_days, writer_count,
   unit_price_snapshot, estimated_amount_snapshot, status)
SELECT 'WA-MIG-' || et.id, et.id, 'NEAREST_AVAILABLE', r.event_location,
       r.service_days, r.writer_count, COALESCE(r.unit_price, 980.00),
       COALESCE(r.estimated_amount, COALESCE(r.unit_price, 980.00) * r.service_days * r.writer_count),
       CASE WHEN et.assigned_operator_id IS NULL THEN 'WAITING_MATCH' ELSE 'ACCEPTED' END
FROM editorial_task et
JOIN customer_requirement r ON r.id=et.requirement_id
WHERE r.requested_service IN ('ONSITE_WRITING','ONSITE_WRITING_AND_MEDIA_PR')
ON CONFLICT (editorial_task_id) DO NOTHING;

ALTER TABLE quote_adjustment ADD COLUMN IF NOT EXISTS previous_cost_price NUMERIC(14,2);
ALTER TABLE quote_adjustment ADD COLUMN IF NOT EXISTS current_cost_price NUMERIC(14,2);

ALTER TABLE manuscript_lock ADD COLUMN IF NOT EXISTS manuscript_version_id BIGINT REFERENCES manuscript_version(id);
ALTER TABLE manuscript_lock ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS media_outlet (
  id BIGSERIAL PRIMARY KEY,
  outlet_no VARCHAR(40) NOT NULL UNIQUE,
  external_media_id VARCHAR(120) UNIQUE,
  outlet_name VARCHAR(180) NOT NULL,
  media_attribute VARCHAR(80),
  province VARCHAR(80),
  city VARCHAR(80),
  channel_form VARCHAR(120),
  category VARCHAR(80),
  last_verified_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVIEW_REQUIRED','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS media_contact (
  id BIGSERIAL PRIMARY KEY,
  contact_no VARCHAR(40) NOT NULL UNIQUE,
  media_outlet_id BIGINT NOT NULL REFERENCES media_outlet(id) ON DELETE CASCADE,
  display_name VARCHAR(80) NOT NULL,
  beat VARCHAR(120),
  city VARCHAR(80),
  internal_note TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS publish_offering (
  id BIGSERIAL PRIMARY KEY,
  offering_no VARCHAR(40) NOT NULL UNIQUE,
  channel_id BIGINT NOT NULL UNIQUE REFERENCES publish_channel(id) ON DELETE CASCADE,
  media_outlet_id BIGINT REFERENCES media_outlet(id),
  offering_name VARCHAR(180) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVIEW_REQUIRED','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO publish_offering (offering_no, channel_id, offering_name, status)
SELECT 'OFF-MIG-' || c.id, c.id, c.channel_name,
       CASE WHEN c.status IN ('ACTIVE','REVIEW_REQUIRED','INACTIVE') THEN c.status ELSE 'REVIEW_REQUIRED' END
FROM publish_channel c
WHERE c.channel_type='DIRECT_PUBLISHING'
ON CONFLICT (channel_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS publish_plan (
  id BIGSERIAL PRIMARY KEY,
  plan_no VARCHAR(40) NOT NULL UNIQUE,
  project_id BIGINT NOT NULL REFERENCES project(id),
  manuscript_id BIGINT REFERENCES manuscript(id),
  manuscript_version_id BIGINT REFERENCES manuscript_version(id),
  plan_name VARCHAR(160) NOT NULL,
  objective VARCHAR(500),
  estimated_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (estimated_amount >= 0),
  currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
  exclusive_media_pr BOOLEAN NOT NULL DEFAULT FALSE,
  lock_expires_at TIMESTAMPTZ,
  created_by BIGINT NOT NULL REFERENCES app_user(id),
  confirmed_by BIGINT REFERENCES app_user(id),
  confirmed_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'WAITING_CONFIRMATION'
    CHECK (status IN ('DRAFT','WAITING_CONFIRMATION','CONFIRMED','EXECUTING','COMPLETED','CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS publish_plan_item (
  id BIGSERIAL PRIMARY KEY,
  item_no VARCHAR(40) NOT NULL UNIQUE,
  publish_plan_id BIGINT NOT NULL REFERENCES publish_plan(id) ON DELETE CASCADE,
  channel_id BIGINT NOT NULL REFERENCES publish_channel(id),
  channel_type VARCHAR(30) NOT NULL CHECK (channel_type IN ('MEDIA_PR','DIRECT_PUBLISHING')),
  planned_publish_at TIMESTAMPTZ,
  journalist_name VARCHAR(80),
  media_name VARCHAR(180),
  note VARCHAR(500),
  media_candidate_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  quote_id BIGINT REFERENCES channel_quote(id),
  unit_price_snapshot NUMERIC(14,2),
  price_valid_until TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','CONFIRMED','TASK_CREATED','CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(publish_plan_id, channel_id)
);

ALTER TABLE publish_task ADD COLUMN IF NOT EXISTS publish_plan_item_id BIGINT REFERENCES publish_plan_item(id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_publish_task_plan_item ON publish_task(publish_plan_item_id)
  WHERE publish_plan_item_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_service_price_active ON service_price_book(service_code, status, effective_from, effective_until);
CREATE INDEX IF NOT EXISTS idx_writer_profile_location ON writer_profile(status, availability_status, province, city);
CREATE INDEX IF NOT EXISTS idx_writing_assignment_status ON writing_assignment(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_media_outlet_filter ON media_outlet(status, media_attribute, province, city, category);
CREATE INDEX IF NOT EXISTS idx_media_contact_outlet ON media_contact(media_outlet_id, status);
CREATE INDEX IF NOT EXISTS idx_publish_offering_outlet ON publish_offering(media_outlet_id, status);
CREATE INDEX IF NOT EXISTS idx_publish_plan_project_status ON publish_plan(project_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_publish_plan_item_plan ON publish_plan_item(publish_plan_id, status);

DROP INDEX IF EXISTS uq_active_media_pr_lock;
CREATE UNIQUE INDEX uq_active_media_pr_lock ON manuscript_lock(manuscript_id)
  WHERE active=TRUE;

DO $$
DECLARE
  table_name TEXT;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'service_price_book','writer_profile','writing_assignment','media_outlet','media_contact',
    'publish_offering','publish_plan','publish_plan_item'
  ]
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS trg_%I_updated_at ON %I', table_name, table_name);
    EXECUTE format(
      'CREATE TRIGGER trg_%I_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION touch_updated_at()',
      table_name, table_name
    );
  END LOOP;
END;
$$;

COMMIT;
