BEGIN;

-- Supplier integrations are operational configuration, not customer-visible capabilities.
-- Credentials remain in the deployment secret store.  This table stores only the name of the
-- environment variable that supplies a credential to a reviewed adapter.
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

CREATE INDEX IF NOT EXISTS idx_supplier_api_connection_supplier
  ON supplier_api_connection(supplier_id, enabled, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_supplier_api_connection_readiness
  ON supplier_api_connection(
    authorization_status, sandbox_status, production_status, enabled, updated_at DESC
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

CREATE INDEX IF NOT EXISTS idx_platform_acceptance_gate_status
  ON platform_acceptance_gate(status, updated_at DESC);

-- Historical combination records are registered for human review only.  The migration does not
-- change, split, archive, delete, price, or fulfill any customer record.
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

CREATE INDEX IF NOT EXISTS idx_legacy_service_review_status
  ON legacy_service_review(review_status, updated_at DESC);

DROP TRIGGER IF EXISTS trg_supplier_api_connection_updated_at ON supplier_api_connection;
CREATE TRIGGER trg_supplier_api_connection_updated_at
  BEFORE UPDATE ON supplier_api_connection
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

DROP TRIGGER IF EXISTS trg_platform_acceptance_gate_updated_at ON platform_acceptance_gate;
CREATE TRIGGER trg_platform_acceptance_gate_updated_at
  BEFORE UPDATE ON platform_acceptance_gate
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

DROP TRIGGER IF EXISTS trg_legacy_service_review_updated_at ON legacy_service_review;
CREATE TRIGGER trg_legacy_service_review_updated_at
  BEFORE UPDATE ON legacy_service_review
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMIT;
