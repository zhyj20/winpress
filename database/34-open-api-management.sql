BEGIN;

-- Open API is a customer-system ingress, distinct from supplier API configuration.
-- Secret material is never stored here: access keys are generated once and only their SHA-256
-- digest is retained. Raw request bodies are also deliberately excluded from the audit ledger.
CREATE TABLE IF NOT EXISTS open_api_application (
  id BIGSERIAL PRIMARY KEY,
  application_no VARCHAR(40) NOT NULL UNIQUE,
  application_name VARCHAR(160) NOT NULL,
  client_code VARCHAR(80) NOT NULL UNIQUE
    CHECK (client_code ~ '^[A-Z0-9][A-Z0-9._-]{1,79}$'),
  customer_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
  environment VARCHAR(20) NOT NULL
    CHECK (environment IN ('SANDBOX','PRODUCTION')),
  service_scopes TEXT NOT NULL,
  rate_limit_per_minute INTEGER NOT NULL DEFAULT 60
    CHECK (rate_limit_per_minute BETWEEN 1 AND 10000),
  authorization_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SUBMITTED'
    CHECK (authorization_status IN ('NOT_SUBMITTED','PENDING','VERIFIED','REJECTED')),
  authorization_evidence_ref VARCHAR(500),
  sandbox_status VARCHAR(30) NOT NULL DEFAULT 'NOT_TESTED'
    CHECK (sandbox_status IN ('NOT_TESTED','PENDING','PASSED','FAILED')),
  sandbox_evidence_ref VARCHAR(500),
  production_status VARCHAR(30) NOT NULL DEFAULT 'NOT_APPROVED'
    CHECK (production_status IN ('NOT_APPROVED','PENDING','APPROVED','REVOKED')),
  production_evidence_ref VARCHAR(500),
  contract_reference VARCHAR(300),
  internal_note TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','REVOKED')),
  created_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
  updated_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_open_api_application_authorization_evidence
    CHECK (
      authorization_status<>'VERIFIED'
      OR (contract_reference IS NOT NULL AND authorization_evidence_ref IS NOT NULL)
    ),
  CONSTRAINT ck_open_api_application_sandbox_evidence
    CHECK (sandbox_status<>'PASSED' OR sandbox_evidence_ref IS NOT NULL),
  CONSTRAINT ck_open_api_application_production_evidence
    CHECK (production_status<>'APPROVED' OR production_evidence_ref IS NOT NULL),
  CONSTRAINT ck_open_api_application_activation
    CHECK (
      status<>'ACTIVE'
      OR (
        authorization_status='VERIFIED'
        AND sandbox_status='PASSED'
        AND (environment='SANDBOX' OR production_status='APPROVED')
      )
    )
);

CREATE INDEX IF NOT EXISTS idx_open_api_application_customer
  ON open_api_application(customer_user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_open_api_application_status
  ON open_api_application(status, environment, updated_at DESC);

CREATE TABLE IF NOT EXISTS open_api_access_key (
  id BIGSERIAL PRIMARY KEY,
  key_no VARCHAR(40) NOT NULL UNIQUE,
  application_id BIGINT NOT NULL REFERENCES open_api_application(id) ON DELETE RESTRICT,
  key_label VARCHAR(120) NOT NULL,
  key_prefix VARCHAR(40) NOT NULL UNIQUE,
  key_hash CHAR(64) NOT NULL UNIQUE,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
  expires_at TIMESTAMPTZ,
  last_used_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  revoked_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
  created_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_open_api_access_key_revocation
    CHECK ((status='REVOKED') = (revoked_at IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_open_api_access_key_application
  ON open_api_access_key(application_id, status, expires_at, updated_at DESC);

CREATE TABLE IF NOT EXISTS open_api_request_receipt (
  id BIGSERIAL PRIMARY KEY,
  application_id BIGINT NOT NULL REFERENCES open_api_application(id) ON DELETE RESTRICT,
  external_request_id VARCHAR(80) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  requirement_id BIGINT NOT NULL REFERENCES customer_requirement(id) ON DELETE RESTRICT,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
  service_type VARCHAR(40) NOT NULL
    CHECK (service_type IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')),
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(application_id, external_request_id)
);

CREATE INDEX IF NOT EXISTS idx_open_api_receipt_application
  ON open_api_request_receipt(application_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_open_api_receipt_project
  ON open_api_request_receipt(project_id);

CREATE TABLE IF NOT EXISTS open_api_access_log (
  id BIGSERIAL PRIMARY KEY,
  application_id BIGINT REFERENCES open_api_application(id) ON DELETE SET NULL,
  access_key_id BIGINT REFERENCES open_api_access_key(id) ON DELETE SET NULL,
  external_request_id VARCHAR(80),
  operation_code VARCHAR(80) NOT NULL,
  request_hash CHAR(64),
  response_status INTEGER NOT NULL CHECK (response_status BETWEEN 100 AND 599),
  outcome_code VARCHAR(80) NOT NULL,
  duration_millis INTEGER CHECK (duration_millis IS NULL OR duration_millis >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_open_api_access_log_application
  ON open_api_access_log(application_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_open_api_access_log_operation
  ON open_api_access_log(operation_code, created_at DESC);

DROP TRIGGER IF EXISTS trg_open_api_application_updated_at ON open_api_application;
CREATE TRIGGER trg_open_api_application_updated_at
  BEFORE UPDATE ON open_api_application
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

DROP TRIGGER IF EXISTS trg_open_api_access_key_updated_at ON open_api_access_key;
CREATE TRIGGER trg_open_api_access_key_updated_at
  BEFORE UPDATE ON open_api_access_key
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

DROP TRIGGER IF EXISTS trg_open_api_request_receipt_updated_at ON open_api_request_receipt;
CREATE TRIGGER trg_open_api_request_receipt_updated_at
  BEFORE UPDATE ON open_api_request_receipt
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMIT;
