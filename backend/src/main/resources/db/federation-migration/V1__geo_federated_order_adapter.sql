CREATE TABLE IF NOT EXISTS federated_identity_map (
  id BIGSERIAL PRIMARY KEY,
  tenant_id VARCHAR(128) NOT NULL,
  source_instance_id VARCHAR(128) NOT NULL DEFAULT 'default',
  object_type VARCHAR(64) NOT NULL,
  platform_id VARCHAR(128) NOT NULL,
  winpress_id BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (tenant_id, source_instance_id, object_type, platform_id),
  UNIQUE (tenant_id, source_instance_id, object_type, winpress_id)
);

CREATE TABLE IF NOT EXISTS federation_inbox_jti (
  id BIGSERIAL PRIMARY KEY,
  direction VARCHAR(80) NOT NULL,
  assertion_jti VARCHAR(128) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (direction, assertion_jti)
);

CREATE TABLE IF NOT EXISTS federated_order_receipt (
  id BIGSERIAL PRIMARY KEY,
  platform_order_id VARCHAR(128) NOT NULL UNIQUE,
  tenant_id VARCHAR(128) NOT NULL,
  source_instance_id VARCHAR(128) NOT NULL DEFAULT 'default',
  platform_event_id VARCHAR(128) NOT NULL,
  platform_organization_id VARCHAR(128) NOT NULL,
  platform_brand_id VARCHAR(128) NOT NULL,
  platform_project_id VARCHAR(128) NOT NULL,
  service_type VARCHAR(40) NOT NULL,
  snapshot_hash VARCHAR(64) NOT NULL,
  winpress_organization_id BIGINT NOT NULL,
  winpress_customer_id BIGINT NOT NULL,
  winpress_requirement_id BIGINT NOT NULL,
  winpress_project_id BIGINT NOT NULL,
  winpress_editorial_task_id BIGINT,
  winpress_publish_plan_id BIGINT,
  winpress_status VARCHAR(64) NOT NULL,
  next_action TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (service_type IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')),
  CHECK (snapshot_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_federated_order_receipt_tenant_project
  ON federated_order_receipt (tenant_id, platform_project_id, created_at DESC);

CREATE TABLE IF NOT EXISTS federation_event_outbox (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(128) NOT NULL UNIQUE,
  platform_order_id VARCHAR(128) NOT NULL REFERENCES federated_order_receipt(platform_order_id),
  tenant_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  payload JSONB NOT NULL,
  snapshot_hash VARCHAR(64),
  idempotency_key VARCHAR(256) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  attempt_count INTEGER NOT NULL DEFAULT 0,
  max_attempts INTEGER NOT NULL DEFAULT 5,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  locked_by VARCHAR(128),
  locked_until TIMESTAMPTZ,
  last_error_code VARCHAR(128),
  last_error_message TEXT,
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (tenant_id, idempotency_key),
  CHECK (status IN ('pending','processing','retry_wait','published','dead_letter')),
  CHECK (attempt_count >= 0 AND max_attempts > 0)
);

CREATE INDEX IF NOT EXISTS idx_federation_event_outbox_dispatch
  ON federation_event_outbox (status, next_attempt_at, created_at);

CREATE TABLE IF NOT EXISTS federation_callback_delivery_receipt (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(128) NOT NULL UNIQUE,
  callback_jti VARCHAR(128),
  delivered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  response_code INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
