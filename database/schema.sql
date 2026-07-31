BEGIN;

-- A time-range exclusion constraint below prevents one confirmed onsite writer from
-- being booked for overlapping services. The extension is part of the schema, not a
-- frontend convention, so a database without it cannot be reported as ready.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS organization (
  id BIGSERIAL PRIMARY KEY,
  organization_no VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  organization_type VARCHAR(30) NOT NULL,
  contact_name VARCHAR(80),
  contact_phone VARCHAR(30),
  contact_email VARCHAR(160),
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_user (
  id BIGSERIAL PRIMARY KEY,
  user_no VARCHAR(40) NOT NULL UNIQUE,
  organization_id BIGINT REFERENCES organization(id),
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  mobile VARCHAR(30) NOT NULL,
  email VARCHAR(160) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  last_login_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS business_inquiry (
  id BIGSERIAL PRIMARY KEY,
  inquiry_no VARCHAR(40) NOT NULL UNIQUE,
  inquiry_type VARCHAR(40) NOT NULL
    CHECK (inquiry_type IN ('API_INTEGRATION','GENERAL_COOPERATION','SERVICE_CONSULTATION','MEDIA_PARTNERSHIP')),
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

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGSERIAL PRIMARY KEY,
  role_code VARCHAR(40) NOT NULL UNIQUE,
  role_name VARCHAR(80) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGSERIAL PRIMARY KEY,
  permission_code VARCHAR(80) NOT NULL UNIQUE,
  permission_name VARCHAR(120) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_role (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permission (
  id BIGSERIAL PRIMARY KEY,
  role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
  permission_id BIGINT NOT NULL REFERENCES sys_permission(id) ON DELETE CASCADE,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS customer_requirement (
  id BIGSERIAL PRIMARY KEY,
  requirement_no VARCHAR(40) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL REFERENCES app_user(id),
  organization_id BIGINT NOT NULL REFERENCES organization(id),
  title VARCHAR(200) NOT NULL,
  event_time TIMESTAMPTZ,
  event_location VARCHAR(200),
  facts TEXT,
  objective TEXT,
  target_audience VARCHAR(300),
  requested_service VARCHAR(40) NOT NULL,
  service_days SMALLINT NOT NULL DEFAULT 1 CHECK (service_days BETWEEN 1 AND 30),
  writer_count SMALLINT NOT NULL DEFAULT 1 CHECK (writer_count BETWEEN 1 AND 10),
  unit_price NUMERIC(14,2) CHECK (unit_price IS NULL OR unit_price >= 0),
  estimated_amount NUMERIC(14,2) CHECK (estimated_amount IS NULL OR estimated_amount >= 0),
  onsite_contact_name VARCHAR(80),
  onsite_contact_mobile VARCHAR(30),
  deliverable_requirement TEXT,
  matching_preference VARCHAR(40) NOT NULL DEFAULT 'EXPERIENCE_FIRST',
  due_at TIMESTAMPTZ,
  submission_key VARCHAR(80),
  submission_hash CHAR(64),
  status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_customer_requirement_submission_pair
    CHECK ((submission_key IS NULL) = (submission_hash IS NULL))
);

CREATE TABLE IF NOT EXISTS project (
  id BIGSERIAL PRIMARY KEY,
  project_no VARCHAR(40) NOT NULL UNIQUE,
  requirement_id BIGINT NOT NULL REFERENCES customer_requirement(id),
  organization_id BIGINT NOT NULL REFERENCES organization(id),
  customer_id BIGINT NOT NULL REFERENCES app_user(id),
  activity_root_project_id BIGINT,
  project_name VARCHAR(200) NOT NULL,
  owner_operator_id BIGINT REFERENCES app_user(id),
  budget NUMERIC(14,2),
  planned_start_at TIMESTAMPTZ,
  planned_end_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'PLANNING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_project_activity_root
    FOREIGN KEY (activity_root_project_id) REFERENCES project(id) ON DELETE RESTRICT,
  CONSTRAINT ck_project_activity_root_not_self
    CHECK (activity_root_project_id IS NULL OR activity_root_project_id <> id)
);

CREATE TABLE IF NOT EXISTS conference_project (
  id BIGSERIAL PRIMARY KEY,
  conference_no VARCHAR(40) NOT NULL UNIQUE,
  project_id BIGINT NOT NULL UNIQUE REFERENCES project(id) ON DELETE CASCADE,
  conference_type VARCHAR(40) CHECK (conference_type IN ('PRODUCT_RELEASE','STRATEGIC_SIGNING','INDUSTRY_FORUM','CORPORATE_EVENT')),
  conference_format VARCHAR(40) CHECK (conference_format IN ('OFFLINE','HYBRID','ONLINE')),
  theme VARCHAR(240),
  event_time TIMESTAMPTZ,
  event_location VARCHAR(240),
  attendee_scale VARCHAR(40),
  media_goal TEXT,
  guest_plan TEXT,
  agenda_plan TEXT,
  venue_plan TEXT,
  media_direction TEXT,
  communication_goal TEXT,
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
  phase VARCHAR(30) NOT NULL DEFAULT 'PRE_EVENT'
    CHECK (phase IN ('PRE_EVENT','ONSITE','POST_EVENT')),
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

CREATE TABLE IF NOT EXISTS conference_media_candidate (
  id BIGSERIAL PRIMARY KEY,
  candidate_no VARCHAR(40) NOT NULL UNIQUE,
  conference_project_id BIGINT NOT NULL REFERENCES conference_project(id) ON DELETE CASCADE,
  candidate_key VARCHAR(260) NOT NULL,
  candidate_type VARCHAR(20) NOT NULL DEFAULT 'MEDIA'
    CHECK (candidate_type IN ('MEDIA','REPORTER','MANUAL')),
  external_media_id VARCHAR(120) NOT NULL,
  media_name VARCHAR(180) NOT NULL,
  external_reporter_id VARCHAR(120),
  reporter_name VARCHAR(80),
  media_attribute VARCHAR(80),
  province VARCHAR(80),
  city VARCHAR(80),
  channel_form VARCHAR(120),
  category VARCHAR(80),
  coverage_tags TEXT,
  operation_note TEXT,
  fit_score NUMERIC(10,2),
  reporter_news_count BIGINT,
  media_fans_count BIGINT,
  logo_url VARCHAR(800),
  reporter_avatar_url VARCHAR(800),
  selected_by BIGINT REFERENCES app_user(id),
  managed_operator_id BIGINT REFERENCES app_user(id),
  selected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  invited_at TIMESTAMPTZ,
  responded_at TIMESTAMPTZ,
  note TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'CANDIDATE' CHECK (status IN ('CANDIDATE','READY_TO_INVITE','INVITED','RESPONDED','DECLINED','ATTENDING','NOT_PROCEEDING')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(conference_project_id, candidate_key)
);

CREATE TABLE IF NOT EXISTS editorial_task (
  id BIGSERIAL PRIMARY KEY,
  task_no VARCHAR(40) NOT NULL UNIQUE,
  project_id BIGINT NOT NULL REFERENCES project(id),
  requirement_id BIGINT NOT NULL REFERENCES customer_requirement(id),
  assigned_operator_id BIGINT REFERENCES app_user(id),
  writer_name VARCHAR(80),
  writing_brief TEXT,
  due_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING_ASSIGNMENT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The acceptance task is separate from channel execution. It keeps a new media-invitation or
-- direct-publishing order visible before a specific media/channel is chosen.
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
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_writer_profile_service_radius_nonnegative
    CHECK (service_radius_km IS NULL OR service_radius_km >= 0)
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
    CHECK (status IN ('WAITING_MATCH','OFFERED','PARTIALLY_ACCEPTED','ACCEPTED','DECLINED','CANCELLED','COMPLETED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A writing assignment is the customer-facing service configuration. Each fulfilled
-- writer seat is recorded separately so that a two-person order cannot silently turn
-- into a one-person assignment. A confirmed seat holds its actual service window;
-- PostgreSQL rejects overlapping confirmations for the same writer.
CREATE TABLE IF NOT EXISTS writing_assignment_member (
  id BIGSERIAL PRIMARY KEY,
  member_no VARCHAR(40) NOT NULL UNIQUE,
  assignment_id BIGINT NOT NULL REFERENCES writing_assignment(id) ON DELETE CASCADE,
  writer_profile_id BIGINT NOT NULL REFERENCES writer_profile(id),
  service_window TSTZRANGE,
  distance_km NUMERIC(10,2),
  status VARCHAR(30) NOT NULL DEFAULT 'OFFERED'
    CHECK (status IN ('OFFERED','ACCEPTED','DECLINED','CANCELLED','COMPLETED')),
  offered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at TIMESTAMPTZ,
  response_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_writing_assignment_member_writer UNIQUE (assignment_id, writer_profile_id),
  CONSTRAINT ck_writing_assignment_member_window
    CHECK (status NOT IN ('OFFERED','ACCEPTED') OR service_window IS NOT NULL),
  CONSTRAINT ck_writing_assignment_member_distance_nonnegative
    CHECK (distance_km IS NULL OR distance_km >= 0),
  CONSTRAINT ex_writing_assignment_member_no_overlap
    EXCLUDE USING GIST (
      writer_profile_id WITH =,
      service_window WITH &&
    ) WHERE (status='ACCEPTED' AND service_window IS NOT NULL)
);

COMMENT ON TABLE writing_assignment_member IS
  'One real writer seat for an onsite-writing order. Confirmed seats use a database-enforced time window and never reveal writer details to customers.';

CREATE OR REPLACE FUNCTION enforce_writing_assignment_member_radius_integrity()
RETURNS TRIGGER AS $$
DECLARE
  configured_radius NUMERIC(10,2);
BEGIN
  IF NEW.status NOT IN ('OFFERED','ACCEPTED') THEN
    RETURN NEW;
  END IF;

  SELECT service_radius_km INTO configured_radius
  FROM writer_profile
  WHERE id=NEW.writer_profile_id;

  IF configured_radius IS NULL THEN
    RETURN NEW;
  END IF;
  IF NEW.distance_km IS NULL THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='writing assignment member distance is required when writer service radius is configured';
  END IF;
  IF NEW.distance_km > configured_radius THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='writing assignment member distance exceeds writer service radius';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_writing_assignment_member_radius_integrity
  ON writing_assignment_member;
CREATE TRIGGER trg_writing_assignment_member_radius_integrity
  BEFORE INSERT OR UPDATE OF writer_profile_id, distance_km, status
  ON writing_assignment_member
  FOR EACH ROW EXECUTE FUNCTION enforce_writing_assignment_member_radius_integrity();

CREATE OR REPLACE FUNCTION enforce_writer_profile_radius_integrity()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.service_radius_km IS NULL THEN
    RETURN NEW;
  END IF;
  IF EXISTS (
    SELECT 1
    FROM writing_assignment_member member
    WHERE member.writer_profile_id=NEW.id
      AND member.status IN ('OFFERED','ACCEPTED')
      AND (member.distance_km IS NULL OR member.distance_km > NEW.service_radius_km)
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='writer service radius cannot be reduced below an active assignment distance';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_writer_profile_radius_integrity ON writer_profile;
CREATE TRIGGER trg_writer_profile_radius_integrity
  BEFORE UPDATE OF service_radius_km ON writer_profile
  FOR EACH ROW EXECUTE FUNCTION enforce_writer_profile_radius_integrity();

CREATE TABLE IF NOT EXISTS manuscript (
  id BIGSERIAL PRIMARY KEY,
  manuscript_no VARCHAR(40) NOT NULL UNIQUE,
  project_id BIGINT NOT NULL REFERENCES project(id),
  editorial_task_id BIGINT REFERENCES editorial_task(id),
  title VARCHAR(240) NOT NULL,
  current_version_no INT NOT NULL DEFAULT 1,
  approved_version_id BIGINT,
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS manuscript_version (
  id BIGSERIAL PRIMARY KEY,
  version_no VARCHAR(40) NOT NULL UNIQUE,
  manuscript_id BIGINT NOT NULL REFERENCES manuscript(id) ON DELETE CASCADE,
  version_number INT NOT NULL,
  title VARCHAR(240) NOT NULL,
  summary TEXT,
  content TEXT NOT NULL,
  change_note VARCHAR(500),
  submitted_by BIGINT NOT NULL REFERENCES app_user(id),
  reviewed_by BIGINT REFERENCES app_user(id),
  reviewed_at TIMESTAMPTZ,
  review_comment TEXT,
  source_manuscript_id BIGINT,
  source_version_id BIGINT,
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_manuscript_version_source_pair
    CHECK ((source_manuscript_id IS NULL) = (source_version_id IS NULL)),
  CONSTRAINT fk_manuscript_version_source_manuscript
    FOREIGN KEY (source_manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  CONSTRAINT fk_manuscript_version_source_version
    FOREIGN KEY (source_version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT,
  UNIQUE(manuscript_id, version_number)
);

ALTER TABLE manuscript DROP CONSTRAINT IF EXISTS fk_manuscript_approved_version;
ALTER TABLE manuscript ADD CONSTRAINT fk_manuscript_approved_version
  FOREIGN KEY (approved_version_id) REFERENCES manuscript_version(id);

CREATE TABLE IF NOT EXISTS publish_channel (
  id BIGSERIAL PRIMARY KEY,
  channel_no VARCHAR(40) NOT NULL UNIQUE,
  channel_name VARCHAR(180) NOT NULL,
  channel_type VARCHAR(30) NOT NULL CHECK (channel_type IN ('MEDIA_PR','DIRECT_PUBLISHING','LEGACY_OWNED_CHANNEL')),
  category VARCHAR(80),
  region VARCHAR(80),
  publish_form VARCHAR(120),
  expected_days INT,
  link_support BOOLEAN NOT NULL DEFAULT TRUE,
  public_notes TEXT,
  source_type VARCHAR(40) NOT NULL DEFAULT 'INTERNAL',
  source_ref VARCHAR(120),
  last_verified_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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

-- Supplier API connections are platform-only operational records. Credentials stay in the
-- deployment secret store; this table retains only the environment-variable reference and
-- acceptance evidence needed to decide whether an adapter may be enabled.
CREATE TABLE IF NOT EXISTS supplier_api_connection (
  id BIGSERIAL PRIMARY KEY,
  connection_no VARCHAR(40) NOT NULL UNIQUE,
  supplier_id BIGINT REFERENCES supplier(id) ON DELETE RESTRICT,
  connection_name VARCHAR(180) NOT NULL,
  provider_code VARCHAR(80) NOT NULL,
  connection_kind VARCHAR(40) NOT NULL
    CHECK (connection_kind IN ('MEDIA_DATA','ORDER_FULFILLMENT','QUOTE_SYNC','GEO_FEDERATION')),
  environment VARCHAR(20) NOT NULL
    CHECK (environment IN ('SANDBOX','PRODUCTION')),
  base_url VARCHAR(500) NOT NULL,
  auth_type VARCHAR(30) NOT NULL
    CHECK (auth_type IN ('NONE','BEARER','API_KEY_HEADER','HMAC_SHA256')),
  auth_header_name VARCHAR(100),
  credential_env_key VARCHAR(160),
  capability_scope TEXT,
  media_search_path VARCHAR(300),
  reporter_search_path VARCHAR(300),
  quote_path VARCHAR(300),
  order_path VARCHAR(300),
  order_status_path VARCHAR(300),
  callback_path VARCHAR(300),
  reconciliation_path VARCHAR(300),
  sla_reference VARCHAR(300),
  rate_limit_per_minute INTEGER NOT NULL DEFAULT 60
    CHECK (rate_limit_per_minute BETWEEN 1 AND 10000),
  timeout_seconds INTEGER NOT NULL DEFAULT 15
    CHECK (timeout_seconds BETWEEN 1 AND 120),
  max_retries INTEGER NOT NULL DEFAULT 2
    CHECK (max_retries BETWEEN 0 AND 10),
  data_scope TEXT,
  contract_reference VARCHAR(300),
  authorization_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SUBMITTED'
    CHECK (authorization_status IN ('NOT_SUBMITTED','PENDING','VERIFIED','REJECTED')),
  authorization_evidence_ref VARCHAR(500),
  sandbox_status VARCHAR(30) NOT NULL DEFAULT 'NOT_TESTED'
    CHECK (sandbox_status IN ('NOT_TESTED','PENDING','PASSED','FAILED')),
  sandbox_evidence_ref VARCHAR(500),
  production_status VARCHAR(30) NOT NULL DEFAULT 'NOT_APPROVED'
    CHECK (production_status IN ('NOT_APPROVED','PENDING','APPROVED','REVOKED')),
  production_evidence_ref VARCHAR(500),
  internal_note TEXT,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  last_config_checked_at TIMESTAMPTZ,
  last_config_check_status VARCHAR(30)
    CHECK (last_config_check_status IS NULL OR last_config_check_status IN ('READY','BLOCKED')),
  last_config_check_detail TEXT,
  created_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
  updated_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_supplier_api_provider_environment
    UNIQUE (provider_code, connection_kind, environment),
  CONSTRAINT ck_supplier_api_auth_reference
    CHECK (
      (auth_type='NONE' AND credential_env_key IS NULL)
      OR
      (auth_type<>'NONE' AND credential_env_key IS NOT NULL)
    ),
  CONSTRAINT ck_supplier_api_authorization_evidence
    CHECK (
      authorization_status<>'VERIFIED'
      OR (
        contract_reference IS NOT NULL
        AND authorization_evidence_ref IS NOT NULL
      )
    ),
  CONSTRAINT ck_supplier_api_sandbox_evidence
    CHECK (sandbox_status<>'PASSED' OR sandbox_evidence_ref IS NOT NULL),
  CONSTRAINT ck_supplier_api_production_evidence
    CHECK (production_status<>'APPROVED' OR production_evidence_ref IS NOT NULL),
  CONSTRAINT ck_supplier_api_enablement
    CHECK (
      NOT enabled
      OR (
        authorization_status='VERIFIED'
        AND sandbox_status='PASSED'
        AND production_status='APPROVED'
        AND (auth_type='NONE' OR credential_env_key IS NOT NULL)
      )
  )
);

CREATE TABLE IF NOT EXISTS platform_acceptance_gate (
  id BIGSERIAL PRIMARY KEY,
  gate_code VARCHAR(60) NOT NULL UNIQUE
    CHECK (gate_code IN (
      'EXTERNAL_MEDIA_DATA',
      'SUPPLIER_FULFILLMENT',
      'LEGAL_TRUST',
      'PRODUCTION_OPERATIONS',
      'LEGACY_COMBINATION_REVIEW'
    )),
  gate_name VARCHAR(180) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','IN_REVIEW','PASSED','BLOCKED')),
  evidence_reference VARCHAR(500),
  review_note TEXT,
  reviewed_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_platform_acceptance_gate_evidence
    CHECK (status<>'PASSED' OR evidence_reference IS NOT NULL)
);

INSERT INTO platform_acceptance_gate (gate_code, gate_name, status)
VALUES
  ('EXTERNAL_MEDIA_DATA', '外部媒体数据授权与联调', 'PENDING'),
  ('SUPPLIER_FULFILLMENT', '供应商真实履约联调', 'PENDING'),
  ('LEGAL_TRUST', '法律与商用信任信息', 'PENDING'),
  ('PRODUCTION_OPERATIONS', '生产运维与灾备演练', 'PENDING'),
  ('LEGACY_COMBINATION_REVIEW', '历史组合服务审核', 'PENDING')
ON CONFLICT (gate_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS platform_acceptance_evidence_item (
  id BIGSERIAL PRIMARY KEY,
  gate_code VARCHAR(60) NOT NULL
    REFERENCES platform_acceptance_gate(gate_code) ON DELETE CASCADE,
  item_code VARCHAR(80) NOT NULL,
  item_name VARCHAR(240) NOT NULL,
  required BOOLEAN NOT NULL DEFAULT TRUE,
  item_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
    CHECK (item_status IN ('PENDING','IN_REVIEW','VERIFIED','REJECTED','NOT_APPLICABLE')),
  evidence_reference VARCHAR(500),
  review_note TEXT,
  reviewed_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_platform_acceptance_evidence_item UNIQUE (gate_code, item_code),
  CONSTRAINT ck_platform_acceptance_evidence_reference
    CHECK (
      item_status<>'VERIFIED'
      OR NULLIF(btrim(evidence_reference), '') IS NOT NULL
    ),
  CONSTRAINT ck_platform_acceptance_required_applicability
    CHECK (NOT required OR item_status<>'NOT_APPLICABLE')
);

INSERT INTO platform_acceptance_evidence_item
  (gate_code, item_code, item_name, required)
VALUES
  ('EXTERNAL_MEDIA_DATA', 'WRITTEN_AUTHORIZATION', '书面授权及使用期限', TRUE),
  ('EXTERNAL_MEDIA_DATA', 'DATA_SCOPE', '媒体与记者数据范围及允许字段', TRUE),
  ('EXTERNAL_MEDIA_DATA', 'PRODUCTION_CREDENTIAL', '生产凭据已进入受控密钥环境', TRUE),
  ('EXTERNAL_MEDIA_DATA', 'RATE_LIMIT_POLICY', '限流、超时与异常重试策略', TRUE),
  ('EXTERNAL_MEDIA_DATA', 'SANDBOX_ACCEPTANCE', '沙箱检索与异常场景验收记录', TRUE),
  ('SUPPLIER_FULFILLMENT', 'CONTRACT_AND_SLA', '供应商合同、责任边界与服务等级', TRUE),
  ('SUPPLIER_FULFILLMENT', 'FIELD_MAPPING', '报价、下单、回执与状态字段映射', TRUE),
  ('SUPPLIER_FULFILLMENT', 'SANDBOX_ORDER', '沙箱下单与撤销验收记录', TRUE),
  ('SUPPLIER_FULFILLMENT', 'QUOTE_AND_RECEIPT', '报价及受理回执核对记录', TRUE),
  ('SUPPLIER_FULFILLMENT', 'CALLBACK_AND_RETRY', '回调、超时与异常重试验收记录', TRUE),
  ('SUPPLIER_FULFILLMENT', 'RECONCILIATION', '对账口径与差异处理验收记录', TRUE),
  ('LEGAL_TRUST', 'LEGAL_ENTITY', '经营主体及统一社会信用代码', TRUE),
  ('LEGAL_TRUST', 'FILING_AND_LICENSE', '备案与适用许可证信息', TRUE),
  ('LEGAL_TRUST', 'ADDRESS_AND_SERVICE', '经营地址及客服联系方式', TRUE),
  ('LEGAL_TRUST', 'PRIVACY_AND_TERMS', '隐私与服务条款法务定稿', TRUE),
  ('LEGAL_TRUST', 'CASE_AUTHORIZATION', '案例、商标与素材授权', TRUE),
  ('LEGAL_TRUST', 'PRODUCTION_DOMAINS', '生产域名权属及披露口径', TRUE),
  ('PRODUCTION_OPERATIONS', 'PREPROD_MIGRATION', '隔离预发布环境迁移记录', TRUE),
  ('PRODUCTION_OPERATIONS', 'BACKUP_RESTORE', '备份恢复演练记录', TRUE),
  ('PRODUCTION_OPERATIONS', 'LEAST_PRIVILEGE_NETWORK', '最小权限账号与网络边界核验', TRUE),
  ('PRODUCTION_OPERATIONS', 'TLS_AND_DOMAIN', 'TLS 证书与域名切换验证', TRUE),
  ('PRODUCTION_OPERATIONS', 'MONITORING_ALERTS', '监控、告警与值守链路验证', TRUE),
  ('PRODUCTION_OPERATIONS', 'PRODUCTION_DB_REHEARSAL', '生产数据库迁移回滚演练', TRUE),
  ('PRODUCTION_OPERATIONS', 'NON_PRODUCTION_DATA_PURGE', '测试账号、演示与种子数据清除', TRUE),
  ('LEGACY_COMBINATION_REVIEW', 'INVENTORY', '历史组合记录完整清单', TRUE),
  ('LEGACY_COMBINATION_REVIEW', 'BUSINESS_CONFIRMATION', '逐条业务确认与处理决定', TRUE),
  ('LEGACY_COMBINATION_REVIEW', 'MIGRATION_PLAN', '映射、迁移或归档实施方案', TRUE),
  ('LEGACY_COMBINATION_REVIEW', 'BACKUP_AND_ROLLBACK', '迁移前备份与回滚方案', TRUE)
ON CONFLICT (gate_code, item_code) DO UPDATE
SET item_name=EXCLUDED.item_name, required=EXCLUDED.required;

CREATE TABLE IF NOT EXISTS legacy_service_review (
  id BIGSERIAL PRIMARY KEY,
  review_no VARCHAR(40) NOT NULL UNIQUE,
  requirement_id BIGINT NOT NULL UNIQUE REFERENCES customer_requirement(id) ON DELETE RESTRICT,
  original_service_type VARCHAR(40) NOT NULL DEFAULT 'WRITING_AND_PUBLISHING'
    CHECK (original_service_type='WRITING_AND_PUBLISHING'),
  review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
    CHECK (review_status IN ('PENDING','IN_REVIEW','APPROVED','REJECTED')),
  approved_action VARCHAR(40)
    CHECK (
      approved_action IS NULL
      OR approved_action IN (
        'ARCHIVE_ONLY',
        'MAP_TO_ONSITE_WRITING',
        'MAP_TO_MEDIA_PR',
        'MAP_TO_DIRECT_PUBLISHING',
        'MAP_TO_NEWS_CONFERENCE',
        'MANUAL_RECONSTRUCTION'
      )
    ),
  evidence_reference VARCHAR(500),
  business_note TEXT,
  reviewed_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_legacy_service_review_approval
    CHECK (
      review_status<>'APPROVED'
      OR (
        approved_action IS NOT NULL
        AND evidence_reference IS NOT NULL
      )
    )
);

INSERT INTO legacy_service_review (review_no, requirement_id)
SELECT
  'LEGACY-' || lpad(requirement.id::text, 12, '0'),
  requirement.id
FROM customer_requirement requirement
WHERE requirement.requested_service='WRITING_AND_PUBLISHING'
ON CONFLICT (requirement_id) DO NOTHING;

-- Starts with migration 36.  Historical scripts are not backfilled because their
-- execution record was not captured by the earlier schema.
CREATE TABLE IF NOT EXISTS schema_migration_ledger (
  migration_version INTEGER PRIMARY KEY
    CHECK (migration_version > 0),
  script_name VARCHAR(180) NOT NULL,
  release_contract VARCHAR(100) NOT NULL,
  apply_mode VARCHAR(30) NOT NULL
    CHECK (apply_mode IN ('BASELINE','FORWARD')),
  verification_reference VARCHAR(500) NOT NULL,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  applied_by VARCHAR(120) NOT NULL DEFAULT CURRENT_USER,
  CONSTRAINT uq_schema_migration_ledger_script_name UNIQUE (script_name),
  CONSTRAINT ck_schema_migration_ledger_script_name
    CHECK (script_name ~ '^[0-9]{2,4}-[a-z0-9][a-z0-9-]*\.sql$'),
  CONSTRAINT ck_schema_migration_ledger_contract
    CHECK (char_length(btrim(release_contract)) >= 8),
  CONSTRAINT ck_schema_migration_ledger_reference
    CHECK (char_length(btrim(verification_reference)) >= 12)
);

CREATE TABLE IF NOT EXISTS channel_quote (
  id BIGSERIAL PRIMARY KEY,
  quote_no VARCHAR(40) NOT NULL UNIQUE,
  channel_id BIGINT NOT NULL REFERENCES publish_channel(id),
  supplier_id BIGINT REFERENCES supplier(id),
  customer_tier VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
  cost_price NUMERIC(14,2),
  customer_price NUMERIC(14,2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
  valid_from TIMESTAMPTZ NOT NULL,
  valid_until TIMESTAMPTZ NOT NULL,
  public_terms TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_channel_quote_price_integrity
    CHECK (
      customer_price > 0
      AND (cost_price IS NULL OR (cost_price >= 0 AND customer_price >= cost_price))
    ),
  CONSTRAINT ck_channel_quote_validity CHECK (valid_until > valid_from),
  CONSTRAINT ck_channel_quote_status
    CHECK (status IN ('ACTIVE','REVIEW_REQUIRED','INACTIVE','SUPERSEDED'))
);

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

-- Immutable pricing change ledger.  The order table keeps the quote it used;
-- this table explains why a later customer-facing quote was changed.
CREATE TABLE IF NOT EXISTS quote_adjustment_batch (
  id BIGSERIAL PRIMARY KEY,
  batch_no VARCHAR(40) NOT NULL UNIQUE,
  adjusted_by BIGINT NOT NULL REFERENCES app_user(id),
  submission_key VARCHAR(80) NOT NULL,
  submission_hash VARCHAR(64) NOT NULL,
  percentage NUMERIC(7,4) NOT NULL,
  valid_until TIMESTAMPTZ NOT NULL,
  public_terms VARCHAR(1000),
  reason VARCHAR(300) NOT NULL,
  channel_count INTEGER NOT NULL,
  adjusted_count INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_quote_adjustment_batch_submission UNIQUE (adjusted_by, submission_key),
  CONSTRAINT ck_quote_adjustment_batch_key CHECK (char_length(submission_key) BETWEEN 16 AND 80),
  CONSTRAINT ck_quote_adjustment_batch_hash CHECK (submission_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT ck_quote_adjustment_batch_percentage CHECK (percentage BETWEEN -50 AND 50),
  CONSTRAINT ck_quote_adjustment_batch_counts CHECK (
    channel_count BETWEEN 1 AND 200
    AND adjusted_count BETWEEN 0 AND channel_count
  ),
  CONSTRAINT ck_quote_adjustment_batch_status CHECK (status IN ('PROCESSING','COMPLETED'))
);

CREATE TABLE IF NOT EXISTS quote_adjustment (
  id BIGSERIAL PRIMARY KEY,
  adjustment_no VARCHAR(40) NOT NULL UNIQUE,
  batch_id BIGINT REFERENCES quote_adjustment_batch(id),
  channel_id BIGINT NOT NULL REFERENCES publish_channel(id),
  previous_quote_id BIGINT REFERENCES channel_quote(id),
  current_quote_id BIGINT NOT NULL REFERENCES channel_quote(id),
  previous_cost_price NUMERIC(14,2),
  current_cost_price NUMERIC(14,2),
  previous_customer_price NUMERIC(14,2),
  current_customer_price NUMERIC(14,2) NOT NULL,
  adjustment_mode VARCHAR(30) NOT NULL CHECK (adjustment_mode IN ('MANUAL','BATCH_PERCENT')),
  reason VARCHAR(300) NOT NULL,
  adjusted_by BIGINT NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS manuscript_lock (
  id BIGSERIAL PRIMARY KEY,
  lock_no VARCHAR(40) NOT NULL UNIQUE,
  manuscript_id BIGINT NOT NULL REFERENCES manuscript(id),
  manuscript_version_id BIGINT REFERENCES manuscript_version(id),
  lock_type VARCHAR(30) NOT NULL DEFAULT 'MEDIA_PR',
  locked_by BIGINT NOT NULL REFERENCES app_user(id),
  reason VARCHAR(300) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  locked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMPTZ,
  released_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS media_outlet (
  id BIGSERIAL PRIMARY KEY,
  outlet_no VARCHAR(40) NOT NULL UNIQUE,
  external_media_id VARCHAR(120),
  outlet_name VARCHAR(180) NOT NULL,
  media_attribute VARCHAR(80),
  province VARCHAR(80),
  city VARCHAR(80),
  channel_form VARCHAR(120),
  category VARCHAR(80),
  last_verified_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVIEW_REQUIRED','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(external_media_id)
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
  submission_key VARCHAR(80),
  submission_hash VARCHAR(64),
  status VARCHAR(30) NOT NULL DEFAULT 'WAITING_CONFIRMATION'
    CHECK (status IN ('DRAFT','WAITING_CONFIRMATION','CONFIRMED','EXECUTING','COMPLETED','CANCELLED')),
  CONSTRAINT ck_publish_plan_submission_pair
    CHECK ((submission_key IS NULL) = (submission_hash IS NULL)),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_publish_plan_submission_key
  ON publish_plan(project_id, created_by, submission_key)
  WHERE submission_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS publish_plan_item (
  id BIGSERIAL PRIMARY KEY,
  item_no VARCHAR(40) NOT NULL UNIQUE,
  publish_plan_id BIGINT NOT NULL REFERENCES publish_plan(id) ON DELETE CASCADE,
  -- Manual MEDIA_PR candidates are deliberately saved without a fabricated execution channel.
  -- A channel becomes mandatory only when a verified execution route is actually bound.
  channel_id BIGINT REFERENCES publish_channel(id),
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
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_publish_plan_item_direct_channel
  ON publish_plan_item(publish_plan_id, channel_id)
  WHERE channel_type='DIRECT_PUBLISHING';

CREATE INDEX IF NOT EXISTS idx_publish_plan_item_media_target
  ON publish_plan_item(publish_plan_id, channel_id)
  WHERE channel_type='MEDIA_PR';

CREATE TABLE IF NOT EXISTS publish_task (
  id BIGSERIAL PRIMARY KEY,
  task_no VARCHAR(40) NOT NULL UNIQUE,
  publish_plan_item_id BIGINT UNIQUE REFERENCES publish_plan_item(id),
  project_id BIGINT NOT NULL REFERENCES project(id),
  manuscript_id BIGINT REFERENCES manuscript(id),
  manuscript_version_id BIGINT REFERENCES manuscript_version(id),
  -- A manually supplemented MEDIA_PR task has no channel or supplier until project verification.
  channel_id BIGINT REFERENCES publish_channel(id),
  channel_type VARCHAR(30) NOT NULL CHECK (channel_type IN ('MEDIA_PR','DIRECT_PUBLISHING','LEGACY_OWNED_CHANNEL')),
  assigned_operator_id BIGINT REFERENCES app_user(id),
  planned_publish_at TIMESTAMPTZ,
  actual_publish_at TIMESTAMPTZ,
  execution_note TEXT,
  exception_reason TEXT,
  client_accepted_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING_ASSIGNMENT'
    CONSTRAINT ck_publish_task_status CHECK (
      status IN (
        'PENDING_ASSIGNMENT','PENDING_EXECUTION','IN_PROGRESS','NEEDS_INFO',
        'EXCEPTION','COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING'
      )
    ),
  CONSTRAINT ck_publish_task_not_proceeding_channel
    CHECK (status<>'NOT_PROCEEDING' OR channel_type='MEDIA_PR'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS media_pr_invitation (
  id BIGSERIAL PRIMARY KEY,
  invitation_no VARCHAR(40) NOT NULL UNIQUE,
  publish_task_id BIGINT NOT NULL REFERENCES publish_task(id),
  candidate_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
    CHECK (candidate_type IN ('MEDIA','REPORTER','MANUAL')),
  journalist_name VARCHAR(80),
  media_name VARCHAR(180) NOT NULL,
  external_media_id VARCHAR(120),
  external_reporter_id VARCHAR(120),
  media_attribute VARCHAR(80),
  media_province VARCHAR(80),
  media_city VARCHAR(80),
  media_channel_form VARCHAR(120),
  media_category VARCHAR(80),
  media_fit_score NUMERIC(10,2),
  beat VARCHAR(120),
  invited_at TIMESTAMPTZ,
  response_at TIMESTAMPTZ,
  response_note TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
    CONSTRAINT ck_media_pr_invitation_status CHECK (
      status IN (
        'PENDING','INVITED','RESPONDED','DECLINED','ATTENDING','REPORTED','NOT_PROCEEDING'
      )
    ),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS direct_publish_order (
  id BIGSERIAL PRIMARY KEY,
  order_no VARCHAR(40) NOT NULL UNIQUE,
  publish_task_id BIGINT NOT NULL REFERENCES publish_task(id),
  channel_quote_id BIGINT NOT NULL REFERENCES channel_quote(id),
  article_title VARCHAR(240) NOT NULL,
  amount NUMERIC(14,2) NOT NULL,
  price_valid_until TIMESTAMPTZ NOT NULL,
  requirement_note TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS direct_publish_order_item (
  id BIGSERIAL PRIMARY KEY,
  item_no VARCHAR(40) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL REFERENCES direct_publish_order(id) ON DELETE CASCADE,
  channel_id BIGINT NOT NULL REFERENCES publish_channel(id),
  channel_quote_id BIGINT NOT NULL REFERENCES channel_quote(id),
  unit_price NUMERIC(14,2) NOT NULL,
  publish_url VARCHAR(800),
  rejection_reason TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
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
  fulfillment_mode VARCHAR(20) NOT NULL DEFAULT 'UNCONFIRMED'
    CONSTRAINT ck_supplier_order_fulfillment_mode
      CHECK (fulfillment_mode IN ('UNCONFIRMED','MANUAL','API')),
  submission_evidence_ref VARCHAR(500),
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

CREATE TABLE IF NOT EXISTS monitoring_record (
  id BIGSERIAL PRIMARY KEY,
  monitoring_no VARCHAR(40) NOT NULL UNIQUE,
  project_id BIGINT NOT NULL REFERENCES project(id),
  publish_task_id BIGINT REFERENCES publish_task(id),
  monitored_at TIMESTAMPTZ NOT NULL,
  metric_name VARCHAR(80) NOT NULL,
  metric_value NUMERIC(18,2),
  metric_text VARCHAR(300),
  source_url VARCHAR(800),
  status VARCHAR(30) NOT NULL DEFAULT 'VERIFIED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS result_link (
  id BIGSERIAL PRIMARY KEY,
  result_no VARCHAR(40) NOT NULL UNIQUE,
  project_id BIGINT NOT NULL REFERENCES project(id),
  publish_task_id BIGINT NOT NULL REFERENCES publish_task(id),
  channel_name VARCHAR(180) NOT NULL,
  title VARCHAR(240) NOT NULL,
  url VARCHAR(800) NOT NULL,
  screenshot_path VARCHAR(500),
  published_at TIMESTAMPTZ,
  verified_by BIGINT REFERENCES app_user(id),
  verified_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION'
    CONSTRAINT ck_result_link_status CHECK (
      status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED')
    ),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_result_link_task_url
  ON result_link(publish_task_id, url);

CREATE TABLE IF NOT EXISTS settlement_order (
  id BIGSERIAL PRIMARY KEY,
  settlement_no VARCHAR(40) NOT NULL UNIQUE,
  project_id BIGINT NOT NULL REFERENCES project(id),
  organization_id BIGINT NOT NULL REFERENCES organization(id),
  amount NUMERIC(14,2) NOT NULL,
  paid_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
  due_at TIMESTAMPTZ,
  paid_at TIMESTAMPTZ,
  invoice_no VARCHAR(80),
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS settlement_transaction (
  id BIGSERIAL PRIMARY KEY,
  transaction_no VARCHAR(40) NOT NULL UNIQUE,
  settlement_order_id BIGINT NOT NULL REFERENCES settlement_order(id),
  transaction_type VARCHAR(30) NOT NULL,
  amount NUMERIC(14,2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
  occurred_at TIMESTAMPTZ NOT NULL,
  reference_no VARCHAR(120),
  customer_note VARCHAR(500),
  internal_note VARCHAR(1000),
  status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
  created_by BIGINT NOT NULL REFERENCES app_user(id),
  voided_by BIGINT REFERENCES app_user(id),
  voided_at TIMESTAMPTZ,
  void_reason VARCHAR(500),
  submission_key VARCHAR(80),
  submission_hash VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_settlement_transaction_type CHECK (
    transaction_type IN (
      'PAYMENT',
      'REFUND',
      'CREDIT_ADJUSTMENT',
      'DEBIT_ADJUSTMENT',
      'WRITE_OFF'
    )
  ),
  CONSTRAINT ck_settlement_transaction_status CHECK (status IN ('CONFIRMED', 'VOIDED')),
  CONSTRAINT ck_settlement_transaction_amount CHECK (amount > 0),
  CONSTRAINT ck_settlement_transaction_evidence CHECK (
    NULLIF(btrim(reference_no), '') IS NOT NULL
    OR NULLIF(btrim(customer_note), '') IS NOT NULL
  ),
  CONSTRAINT ck_settlement_transaction_submission_pair CHECK (
    (submission_key IS NULL) = (submission_hash IS NULL)
  ),
  CONSTRAINT ck_settlement_transaction_void_state CHECK (
    (status = 'CONFIRMED' AND voided_by IS NULL AND voided_at IS NULL AND void_reason IS NULL)
    OR
    (
      status = 'VOIDED'
      AND voided_by IS NOT NULL
      AND voided_at IS NOT NULL
      AND NULLIF(btrim(void_reason), '') IS NOT NULL
    )
  )
);

CREATE TABLE IF NOT EXISTS file_asset (
  id BIGSERIAL PRIMARY KEY,
  file_no VARCHAR(40) NOT NULL UNIQUE,
  project_id BIGINT REFERENCES project(id),
  uploader_id BIGINT NOT NULL REFERENCES app_user(id),
  original_name VARCHAR(240) NOT NULL,
  storage_key VARCHAR(500) NOT NULL UNIQUE,
  content_type VARCHAR(120),
  file_size BIGINT NOT NULL,
  checksum_sha256 VARCHAR(64) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS operation_log (
  id BIGSERIAL PRIMARY KEY,
  log_no VARCHAR(40) NOT NULL UNIQUE,
  actor_id BIGINT REFERENCES app_user(id),
  actor_role VARCHAR(40),
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(80) NOT NULL,
  target_id VARCHAR(80) NOT NULL,
  detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  ip_address VARCHAR(80),
  status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_user_org ON app_user(organization_id);
CREATE INDEX IF NOT EXISTS idx_requirement_customer_status ON customer_requirement(customer_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_requirement_submission_key
  ON customer_requirement(customer_id, organization_id, submission_key)
  WHERE submission_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_requirement_service_event ON customer_requirement(requested_service, event_time, status);
CREATE INDEX IF NOT EXISTS idx_project_customer_status ON project(customer_id, status);
CREATE INDEX IF NOT EXISTS idx_project_operator_status ON project(owner_operator_id, status);
CREATE INDEX IF NOT EXISTS idx_project_activity_root ON project(activity_root_project_id)
  WHERE activity_root_project_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_conference_project_status ON conference_project(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_conference_work_item_status ON conference_work_item(conference_project_id, phase, status, sort_order);
CREATE INDEX IF NOT EXISTS idx_conference_media_candidate_status ON conference_media_candidate(conference_project_id, status, fit_score DESC);
CREATE INDEX IF NOT EXISTS idx_editorial_project_status ON editorial_task(project_id, status);
CREATE INDEX IF NOT EXISTS idx_service_price_active ON service_price_book(service_code, status, effective_from, effective_until);
CREATE INDEX IF NOT EXISTS idx_writer_profile_location ON writer_profile(status, availability_status, province, city);
CREATE INDEX IF NOT EXISTS idx_writing_assignment_status ON writing_assignment(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_writing_assignment_member_assignment ON writing_assignment_member(assignment_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_writing_assignment_member_writer ON writing_assignment_member(writer_profile_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_service_intake_task_status ON service_intake_task(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_manuscript_project_status ON manuscript(project_id, status);
CREATE INDEX IF NOT EXISTS idx_version_manuscript_status ON manuscript_version(manuscript_id, status);
CREATE INDEX IF NOT EXISTS idx_manuscript_version_source ON manuscript_version(source_manuscript_id, source_version_id)
  WHERE source_manuscript_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_channel_type_status ON publish_channel(channel_type, status);
CREATE INDEX IF NOT EXISTS idx_channel_source_ref ON publish_channel(source_type, source_ref);
CREATE INDEX IF NOT EXISTS idx_supplier_type_status ON supplier(supplier_type, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_api_connection_supplier
  ON supplier_api_connection(supplier_id, enabled, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_api_connection_readiness
  ON supplier_api_connection(
    authorization_status, sandbox_status, production_status, enabled, updated_at DESC
  );
CREATE INDEX IF NOT EXISTS idx_platform_acceptance_gate_status
  ON platform_acceptance_gate(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_platform_acceptance_evidence_gate
  ON platform_acceptance_evidence_item(gate_code, required, item_status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_legacy_service_review_status
  ON legacy_service_review(review_status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_channel_channel ON supplier_channel(channel_id, status, priority);
CREATE INDEX IF NOT EXISTS idx_quote_channel_validity ON channel_quote(channel_id, status, valid_until);
CREATE INDEX IF NOT EXISTS idx_quote_supplier_validity ON channel_quote(supplier_id, status, valid_until);
CREATE INDEX IF NOT EXISTS idx_quote_active_price ON channel_quote(channel_id, status, customer_price, valid_until);
CREATE UNIQUE INDEX IF NOT EXISTS uq_channel_quote_one_active_per_channel
  ON channel_quote(channel_id) WHERE status='ACTIVE';
CREATE INDEX IF NOT EXISTS idx_channel_direct_filter ON publish_channel(channel_type, status, category, region, publish_form, expected_days);
CREATE INDEX IF NOT EXISTS idx_quote_adjustment_channel_time ON quote_adjustment(channel_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_quote_adjustment_batch ON quote_adjustment(batch_id, channel_id)
  WHERE batch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_lock_manuscript_active ON manuscript_lock(manuscript_id, active);
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_media_pr_lock ON manuscript_lock(manuscript_id) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_media_outlet_filter ON media_outlet(status, media_attribute, province, city, category);
CREATE INDEX IF NOT EXISTS idx_media_contact_outlet ON media_contact(media_outlet_id, status);
CREATE INDEX IF NOT EXISTS idx_publish_offering_outlet ON publish_offering(media_outlet_id, status);
CREATE INDEX IF NOT EXISTS idx_publish_plan_project_status ON publish_plan(project_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_publish_plan_item_plan ON publish_plan_item(publish_plan_id, status);
CREATE INDEX IF NOT EXISTS idx_publish_project_status ON publish_task(project_id, status);
CREATE INDEX IF NOT EXISTS idx_publish_operator_status ON publish_task(assigned_operator_id, status);
CREATE INDEX IF NOT EXISTS idx_supplier_order_status ON supplier_order(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_order_supplier ON supplier_order(supplier_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_order_plan ON supplier_order(publish_plan_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_order_history ON supplier_order_status_history(supplier_order_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_monitoring_project_time ON monitoring_record(project_id, monitored_at DESC);
CREATE INDEX IF NOT EXISTS idx_result_project_status ON result_link(project_id, status);
CREATE INDEX IF NOT EXISTS idx_settlement_org_status ON settlement_order(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_settlement_transaction_settlement_time
  ON settlement_transaction(settlement_order_id, occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_settlement_transaction_type_status
  ON settlement_transaction(transaction_type, status, occurred_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_settlement_transaction_submission_key
  ON settlement_transaction(settlement_order_id, submission_key)
  WHERE submission_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_operation_actor_time ON operation_log(actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_business_inquiry_status ON business_inquiry(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_business_inquiry_mobile_time ON business_inquiry(mobile, created_at DESC);

CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION enforce_publish_task_terminal_integrity() RETURNS TRIGGER AS $$
BEGIN
  IF OLD.status = 'CLIENT_ACCEPTED' AND NEW.status IS DISTINCT FROM OLD.status THEN
    RAISE EXCEPTION 'customer-accepted publish tasks are immutable';
  END IF;

  IF OLD.status = 'COMPLETED'
     AND NEW.status NOT IN ('COMPLETED', 'CLIENT_ACCEPTED') THEN
    RAISE EXCEPTION 'completed publish tasks cannot return to execution';
  END IF;

  IF OLD.status = 'NOT_PROCEEDING' AND NEW.status IS DISTINCT FROM OLD.status THEN
    RAISE EXCEPTION 'closed media invitation tasks are immutable';
  END IF;

  IF NEW.status = 'COMPLETED' AND OLD.status IS DISTINCT FROM 'COMPLETED' THEN
    IF NOT EXISTS (
      SELECT 1
      FROM result_link result
      WHERE result.publish_task_id=OLD.id
        AND result.status='VERIFIED'
    ) THEN
      RAISE EXCEPTION 'completed publish tasks require a verified result';
    END IF;

    IF NEW.channel_type='MEDIA_PR' AND NOT EXISTS (
      SELECT 1
      FROM media_pr_invitation invitation
      WHERE invitation.publish_task_id=OLD.id
        AND invitation.status='REPORTED'
        AND invitation.invited_at IS NOT NULL
    ) THEN
      RAISE EXCEPTION 'completed media tasks require a recorded invitation and reported outcome';
    END IF;
  END IF;

  IF NEW.status = 'CLIENT_ACCEPTED'
     AND OLD.status IS DISTINCT FROM 'CLIENT_ACCEPTED' THEN
    IF OLD.status <> 'COMPLETED' OR NOT EXISTS (
      SELECT 1
      FROM result_link result
      WHERE result.publish_task_id = OLD.id
        AND result.status = 'VERIFIED'
    ) THEN
      RAISE EXCEPTION 'customer acceptance requires a completed task and verified result';
    END IF;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_publish_task_terminal_integrity ON publish_task;
CREATE TRIGGER trg_publish_task_terminal_integrity
  BEFORE UPDATE OF status ON publish_task
  FOR EACH ROW EXECUTE FUNCTION enforce_publish_task_terminal_integrity();

-- A publish plan is an execution record for exactly one independent service. Historical
-- cross-service rows are preserved for audit, while every new insert or relationship change is
-- rejected unless MEDIA_PR/DIRECT_PUBLISHING plan items match the owning project service.
CREATE OR REPLACE FUNCTION enforce_platform_acceptance_gate_readiness() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.status<>'PASSED' THEN
    RETURN NEW;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM platform_acceptance_evidence_item item
    WHERE item.gate_code=NEW.gate_code
      AND item.required
      AND item.item_status<>'VERIFIED'
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='required acceptance evidence remains incomplete';
  END IF;

  IF NEW.gate_code='EXTERNAL_MEDIA_DATA'
     AND NOT EXISTS (
       SELECT 1
       FROM supplier_api_connection connection
       WHERE connection.connection_kind='MEDIA_DATA'
         AND connection.environment='PRODUCTION'
         AND connection.enabled
         AND connection.authorization_status='VERIFIED'
         AND connection.sandbox_status='PASSED'
         AND connection.production_status='APPROVED'
         AND NULLIF(btrim(connection.data_scope), '') IS NOT NULL
         AND (
           NULLIF(btrim(connection.media_search_path), '') IS NOT NULL
           OR NULLIF(btrim(connection.reporter_search_path), '') IS NOT NULL
         )
     ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='accepted production media-data connection is required';
  END IF;

  IF NEW.gate_code='SUPPLIER_FULFILLMENT'
     AND NOT EXISTS (
       SELECT 1
       FROM supplier_api_connection connection
       WHERE connection.connection_kind='ORDER_FULFILLMENT'
         AND connection.environment='PRODUCTION'
         AND connection.enabled
         AND connection.supplier_id IS NOT NULL
         AND connection.authorization_status='VERIFIED'
         AND connection.sandbox_status='PASSED'
         AND connection.production_status='APPROVED'
         AND NULLIF(btrim(connection.order_path), '') IS NOT NULL
         AND NULLIF(btrim(connection.order_status_path), '') IS NOT NULL
         AND NULLIF(btrim(connection.callback_path), '') IS NOT NULL
         AND NULLIF(btrim(connection.reconciliation_path), '') IS NOT NULL
         AND NULLIF(btrim(connection.sla_reference), '') IS NOT NULL
     ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='accepted production fulfillment connection is required';
  END IF;

  IF NEW.gate_code='LEGACY_COMBINATION_REVIEW'
     AND EXISTS (
       SELECT 1
       FROM legacy_service_review review
       WHERE review.review_status NOT IN ('APPROVED','REJECTED')
     ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='legacy combination reviews remain incomplete';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_platform_acceptance_gate_readiness ON platform_acceptance_gate;
CREATE TRIGGER trg_platform_acceptance_gate_readiness
  BEFORE INSERT OR UPDATE OF status ON platform_acceptance_gate
  FOR EACH ROW EXECUTE FUNCTION enforce_platform_acceptance_gate_readiness();

CREATE OR REPLACE FUNCTION reopen_acceptance_gate_on_evidence_change() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.required AND NEW.item_status<>'VERIFIED' THEN
    UPDATE platform_acceptance_gate
    SET status='IN_REVIEW',
        reviewed_at=NULL,
        review_note=concat_ws(
          E'\n',
          NULLIF(review_note, ''),
          '必备验收项重新打开：' || NEW.item_name
        )
    WHERE gate_code=NEW.gate_code
      AND status='PASSED';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_acceptance_evidence_reopens_gate
  ON platform_acceptance_evidence_item;
CREATE TRIGGER trg_acceptance_evidence_reopens_gate
  AFTER INSERT OR UPDATE OF item_status, required ON platform_acceptance_evidence_item
  FOR EACH ROW EXECUTE FUNCTION reopen_acceptance_gate_on_evidence_change();

CREATE OR REPLACE FUNCTION reopen_acceptance_gate_on_connection_change() RETURNS TRIGGER AS $$
DECLARE
  affected_kind VARCHAR(40);
BEGIN
  affected_kind := CASE WHEN TG_OP='DELETE' THEN OLD.connection_kind ELSE NEW.connection_kind END;

  IF affected_kind='MEDIA_DATA'
     AND NOT EXISTS (
       SELECT 1
       FROM supplier_api_connection connection
       WHERE connection.connection_kind='MEDIA_DATA'
         AND connection.environment='PRODUCTION'
         AND connection.enabled
         AND connection.authorization_status='VERIFIED'
         AND connection.sandbox_status='PASSED'
         AND connection.production_status='APPROVED'
         AND NULLIF(btrim(connection.data_scope), '') IS NOT NULL
         AND (
           NULLIF(btrim(connection.media_search_path), '') IS NOT NULL
           OR NULLIF(btrim(connection.reporter_search_path), '') IS NOT NULL
         )
     ) THEN
    UPDATE platform_acceptance_gate
    SET status='IN_REVIEW', reviewed_at=NULL
    WHERE gate_code='EXTERNAL_MEDIA_DATA' AND status='PASSED';
  END IF;

  IF affected_kind='ORDER_FULFILLMENT'
     AND NOT EXISTS (
       SELECT 1
       FROM supplier_api_connection connection
       WHERE connection.connection_kind='ORDER_FULFILLMENT'
         AND connection.environment='PRODUCTION'
         AND connection.enabled
         AND connection.supplier_id IS NOT NULL
         AND connection.authorization_status='VERIFIED'
         AND connection.sandbox_status='PASSED'
         AND connection.production_status='APPROVED'
         AND NULLIF(btrim(connection.order_path), '') IS NOT NULL
         AND NULLIF(btrim(connection.order_status_path), '') IS NOT NULL
         AND NULLIF(btrim(connection.callback_path), '') IS NOT NULL
         AND NULLIF(btrim(connection.reconciliation_path), '') IS NOT NULL
         AND NULLIF(btrim(connection.sla_reference), '') IS NOT NULL
     ) THEN
    UPDATE platform_acceptance_gate
    SET status='IN_REVIEW', reviewed_at=NULL
    WHERE gate_code='SUPPLIER_FULFILLMENT' AND status='PASSED';
  END IF;

  IF TG_OP='DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_integration_connection_reopens_gate ON supplier_api_connection;
CREATE TRIGGER trg_integration_connection_reopens_gate
  AFTER INSERT OR UPDATE OR DELETE ON supplier_api_connection
  FOR EACH ROW EXECUTE FUNCTION reopen_acceptance_gate_on_connection_change();

CREATE OR REPLACE FUNCTION enforce_supplier_order_fulfillment_evidence() RETURNS TRIGGER AS $$
DECLARE
  must_check BOOLEAN;
BEGIN
  must_check := TG_OP='INSERT';
  IF TG_OP='UPDATE' THEN
    must_check :=
      NEW.status IS DISTINCT FROM OLD.status
      OR NEW.fulfillment_mode IS DISTINCT FROM OLD.fulfillment_mode
      OR NEW.submission_evidence_ref IS DISTINCT FROM OLD.submission_evidence_ref
      OR NEW.external_order_no IS DISTINCT FROM OLD.external_order_no;
  END IF;

  IF must_check
     AND NEW.status IN ('SUBMITTED','ACCEPTED','IN_PROGRESS','COMPLETED') THEN
    IF NEW.fulfillment_mode NOT IN ('MANUAL','API')
       OR NULLIF(btrim(NEW.submission_evidence_ref), '') IS NULL THEN
      RAISE EXCEPTION USING
        ERRCODE='23514',
        MESSAGE='supplier order fulfillment evidence is required';
    END IF;
    IF NEW.fulfillment_mode='API'
       AND NULLIF(btrim(NEW.external_order_no), '') IS NULL THEN
      RAISE EXCEPTION USING
        ERRCODE='23514',
        MESSAGE='API supplier order requires an external order number';
    END IF;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_supplier_order_fulfillment_evidence ON supplier_order;
CREATE TRIGGER trg_supplier_order_fulfillment_evidence
  BEFORE INSERT OR UPDATE ON supplier_order
  FOR EACH ROW EXECUTE FUNCTION enforce_supplier_order_fulfillment_evidence();

CREATE OR REPLACE FUNCTION enforce_legacy_combination_service_boundary() RETURNS TRIGGER AS $$
DECLARE
  approved_action VARCHAR(40);
  expected_action VARCHAR(40);
BEGIN
  IF TG_OP='INSERT' AND NEW.requested_service='WRITING_AND_PUBLISHING' THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='new combination service records are not allowed';
  END IF;

  IF TG_OP='UPDATE'
     AND OLD.requested_service<>'WRITING_AND_PUBLISHING'
     AND NEW.requested_service='WRITING_AND_PUBLISHING' THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='combination service cannot be restored';
  END IF;

  IF TG_OP='UPDATE'
     AND OLD.requested_service='WRITING_AND_PUBLISHING'
     AND NEW.requested_service IS DISTINCT FROM OLD.requested_service THEN
    expected_action := CASE NEW.requested_service
      WHEN 'ONSITE_WRITING' THEN 'MAP_TO_ONSITE_WRITING'
      WHEN 'MEDIA_PR' THEN 'MAP_TO_MEDIA_PR'
      WHEN 'DIRECT_PUBLISHING' THEN 'MAP_TO_DIRECT_PUBLISHING'
      WHEN 'NEWS_CONFERENCE' THEN 'MAP_TO_NEWS_CONFERENCE'
      ELSE NULL
    END;

    SELECT review.approved_action
    INTO approved_action
    FROM legacy_service_review review
    WHERE review.requirement_id=OLD.id
      AND review.review_status='APPROVED';

    IF expected_action IS NULL OR approved_action IS DISTINCT FROM expected_action THEN
      RAISE EXCEPTION USING
        ERRCODE='23514',
        MESSAGE='approved legacy service mapping is required';
    END IF;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_legacy_combination_service_boundary ON customer_requirement;
CREATE TRIGGER trg_legacy_combination_service_boundary
  BEFORE INSERT OR UPDATE OF requested_service ON customer_requirement
  FOR EACH ROW EXECUTE FUNCTION enforce_legacy_combination_service_boundary();

CREATE OR REPLACE FUNCTION enforce_publish_plan_item_service_integrity() RETURNS TRIGGER AS $$
DECLARE
  project_service VARCHAR(40);
BEGIN
  SELECT requirement.requested_service
  INTO project_service
  FROM publish_plan plan
  JOIN project project_record ON project_record.id=plan.project_id
  JOIN customer_requirement requirement ON requirement.id=project_record.requirement_id
  WHERE plan.id=NEW.publish_plan_id;

  IF FOUND
     AND (
       project_service NOT IN ('MEDIA_PR','DIRECT_PUBLISHING')
       OR NEW.channel_type IS DISTINCT FROM project_service
     ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='publish plan item service must match the project service';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_publish_plan_item_service_integrity ON publish_plan_item;
CREATE TRIGGER trg_publish_plan_item_service_integrity
  BEFORE INSERT OR UPDATE OF publish_plan_id, channel_type ON publish_plan_item
  FOR EACH ROW EXECUTE FUNCTION enforce_publish_plan_item_service_integrity();

CREATE OR REPLACE FUNCTION enforce_publish_plan_project_service_integrity() RETURNS TRIGGER AS $$
DECLARE
  project_service VARCHAR(40);
BEGIN
  IF NEW.project_id IS NOT DISTINCT FROM OLD.project_id THEN
    RETURN NEW;
  END IF;

  SELECT requirement.requested_service
  INTO project_service
  FROM project project_record
  JOIN customer_requirement requirement ON requirement.id=project_record.requirement_id
  WHERE project_record.id=NEW.project_id;

  IF FOUND AND EXISTS (
    SELECT 1
    FROM publish_plan_item item
    WHERE item.publish_plan_id=OLD.id
      AND (
        project_service NOT IN ('MEDIA_PR','DIRECT_PUBLISHING')
        OR item.channel_type<>project_service
      )
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='publish plan service must match the destination project service';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_publish_plan_project_service_integrity ON publish_plan;
CREATE TRIGGER trg_publish_plan_project_service_integrity
  BEFORE UPDATE OF project_id ON publish_plan
  FOR EACH ROW EXECUTE FUNCTION enforce_publish_plan_project_service_integrity();

CREATE OR REPLACE FUNCTION enforce_project_plan_service_integrity() RETURNS TRIGGER AS $$
DECLARE
  project_service VARCHAR(40);
BEGIN
  IF NEW.requirement_id IS NOT DISTINCT FROM OLD.requirement_id THEN
    RETURN NEW;
  END IF;

  SELECT requested_service
  INTO project_service
  FROM customer_requirement
  WHERE id=NEW.requirement_id;

  IF FOUND AND EXISTS (
    SELECT 1
    FROM publish_plan plan
    JOIN publish_plan_item item ON item.publish_plan_id=plan.id
    WHERE plan.project_id=OLD.id
      AND (
        project_service NOT IN ('MEDIA_PR','DIRECT_PUBLISHING')
        OR item.channel_type<>project_service
      )
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='project service must match its existing publish plans';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_project_plan_service_integrity ON project;
CREATE TRIGGER trg_project_plan_service_integrity
  BEFORE UPDATE OF requirement_id ON project
  FOR EACH ROW EXECUTE FUNCTION enforce_project_plan_service_integrity();

CREATE OR REPLACE FUNCTION enforce_requirement_plan_service_integrity() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.requested_service IS NOT DISTINCT FROM OLD.requested_service THEN
    RETURN NEW;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM project project_record
    JOIN publish_plan plan ON plan.project_id=project_record.id
    JOIN publish_plan_item item ON item.publish_plan_id=plan.id
    WHERE project_record.requirement_id=OLD.id
      AND (
        NEW.requested_service NOT IN ('MEDIA_PR','DIRECT_PUBLISHING')
        OR item.channel_type<>NEW.requested_service
      )
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='requirement service must match its existing publish plans';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_requirement_plan_service_integrity ON customer_requirement;
CREATE TRIGGER trg_requirement_plan_service_integrity
  BEFORE UPDATE OF requested_service ON customer_requirement
  FOR EACH ROW EXECUTE FUNCTION enforce_requirement_plan_service_integrity();

CREATE OR REPLACE FUNCTION enforce_schema_migration_ledger_append_only()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION USING
    ERRCODE='55000',
    MESSAGE='schema migration ledger is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_schema_migration_ledger_append_only
  ON schema_migration_ledger;
CREATE TRIGGER trg_schema_migration_ledger_append_only
  BEFORE UPDATE OR DELETE ON schema_migration_ledger
  FOR EACH ROW EXECUTE FUNCTION enforce_schema_migration_ledger_append_only();

ALTER TABLE conference_work_item
  DROP CONSTRAINT IF EXISTS ck_conference_work_item_completion_time;
ALTER TABLE conference_work_item
  ADD CONSTRAINT ck_conference_work_item_completion_time
    CHECK (
      (status = 'COMPLETED' AND completed_at IS NOT NULL)
      OR (status <> 'COMPLETED' AND completed_at IS NULL)
    );

CREATE OR REPLACE FUNCTION enforce_conference_work_item_terminal_integrity()
RETURNS TRIGGER AS $$
BEGIN
  IF OLD.status = 'COMPLETED' AND NEW.status IS DISTINCT FROM OLD.status THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='completed conference work item cannot be reopened';
  END IF;
  IF OLD.status IS DISTINCT FROM NEW.status
    AND NOT (
      (OLD.status='PENDING' AND NEW.status IN ('IN_PROGRESS','NEEDS_INFO','BLOCKED','COMPLETED'))
      OR (OLD.status='IN_PROGRESS' AND NEW.status IN ('NEEDS_INFO','BLOCKED','COMPLETED'))
      OR (OLD.status='NEEDS_INFO' AND NEW.status IN ('IN_PROGRESS','BLOCKED','COMPLETED'))
      OR (OLD.status='BLOCKED' AND NEW.status IN ('IN_PROGRESS','NEEDS_INFO','COMPLETED'))
    ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='conference work item status transition is not allowed';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_conference_work_item_terminal_integrity
  ON conference_work_item;
CREATE TRIGGER trg_conference_work_item_terminal_integrity
  BEFORE UPDATE OF status ON conference_work_item
  FOR EACH ROW EXECUTE FUNCTION enforce_conference_work_item_terminal_integrity();

ALTER TABLE conference_media_candidate
  DROP CONSTRAINT IF EXISTS ck_conference_media_candidate_status_timeline;
ALTER TABLE conference_media_candidate
  ADD CONSTRAINT ck_conference_media_candidate_status_timeline
    CHECK (
      (status IN ('CANDIDATE','READY_TO_INVITE')
        AND invited_at IS NULL AND responded_at IS NULL)
      OR (status='INVITED' AND invited_at IS NOT NULL AND responded_at IS NULL)
      OR (status IN ('RESPONDED','DECLINED','ATTENDING')
        AND invited_at IS NOT NULL AND responded_at IS NOT NULL)
      OR (status='NOT_PROCEEDING'
        AND (responded_at IS NULL OR invited_at IS NOT NULL))
    );

ALTER TABLE conference_media_candidate
  DROP CONSTRAINT IF EXISTS ck_conference_media_candidate_contact_time_order;
ALTER TABLE conference_media_candidate
  ADD CONSTRAINT ck_conference_media_candidate_contact_time_order
    CHECK (
      (invited_at IS NULL OR invited_at >= selected_at)
      AND (responded_at IS NULL OR (invited_at IS NOT NULL AND responded_at >= invited_at))
    );

ALTER TABLE conference_media_candidate
  DROP CONSTRAINT IF EXISTS ck_conference_media_candidate_outcome_note;
ALTER TABLE conference_media_candidate
  ADD CONSTRAINT ck_conference_media_candidate_outcome_note
    CHECK (
      status NOT IN ('RESPONDED','DECLINED','ATTENDING','NOT_PROCEEDING')
      OR btrim(COALESCE(note, '')) <> ''
    );

CREATE OR REPLACE FUNCTION enforce_conference_media_candidate_state_integrity()
RETURNS TRIGGER AS $$
BEGIN
  IF OLD.invited_at IS NOT NULL AND NEW.invited_at IS DISTINCT FROM OLD.invited_at THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='conference media candidate invitation time cannot be changed';
  END IF;
  IF OLD.responded_at IS NOT NULL AND NEW.responded_at IS DISTINCT FROM OLD.responded_at THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='conference media candidate response time cannot be changed';
  END IF;
  IF OLD.status IS DISTINCT FROM NEW.status
    AND NOT (
      (OLD.status='CANDIDATE' AND NEW.status IN ('READY_TO_INVITE','NOT_PROCEEDING'))
      OR (OLD.status='READY_TO_INVITE' AND NEW.status IN ('INVITED','NOT_PROCEEDING'))
      OR (OLD.status='INVITED' AND NEW.status IN ('RESPONDED','DECLINED','ATTENDING','NOT_PROCEEDING'))
      OR (OLD.status='RESPONDED' AND NEW.status IN ('DECLINED','ATTENDING','NOT_PROCEEDING'))
    ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='conference media candidate status transition is not allowed';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_conference_media_candidate_state_integrity
  ON conference_media_candidate;
CREATE TRIGGER trg_conference_media_candidate_state_integrity
  BEFORE UPDATE OF status, invited_at, responded_at ON conference_media_candidate
  FOR EACH ROW EXECUTE FUNCTION enforce_conference_media_candidate_state_integrity();

CREATE OR REPLACE FUNCTION enforce_conference_project_completion_integrity()
RETURNS TRIGGER AS $$
BEGIN
  IF OLD.status = 'COMPLETED' AND NEW.status IS DISTINCT FROM OLD.status THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='completed conference project cannot be reopened';
  END IF;
  IF NEW.status = 'COMPLETED' AND EXISTS (
    SELECT 1
    FROM conference_work_item item
    WHERE item.conference_project_id=NEW.id
      AND item.status<>'COMPLETED'
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='completed conference project requires every work item to be completed';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_conference_project_completion_integrity
  ON conference_project;
CREATE TRIGGER trg_conference_project_completion_integrity
  BEFORE UPDATE OF status ON conference_project
  FOR EACH ROW EXECUTE FUNCTION enforce_conference_project_completion_integrity();

DO $$
DECLARE
  table_name TEXT;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'organization','app_user','business_inquiry','sys_role','sys_permission','user_role','role_permission',
    'customer_requirement','project','conference_project','conference_work_item','conference_media_candidate','editorial_task','service_intake_task',
    'service_price_book','writer_profile','writing_assignment','writing_assignment_member','manuscript','manuscript_version',
    'publish_channel','supplier','supplier_api_connection','platform_acceptance_gate','platform_acceptance_evidence_item','legacy_service_review','channel_quote','supplier_channel','quote_adjustment_batch','quote_adjustment','manuscript_lock','media_outlet','media_contact','publish_offering',
    'publish_plan','publish_plan_item','publish_task','media_pr_invitation',
    'direct_publish_order','direct_publish_order_item','supplier_order','monitoring_record',
    'result_link','settlement_order','settlement_transaction','file_asset','operation_log'
  ]
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS trg_%I_updated_at ON %I', table_name, table_name);
    EXECUTE format('CREATE TRIGGER trg_%I_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION touch_updated_at()', table_name, table_name);
  END LOOP;
END;
$$;

COMMIT;
