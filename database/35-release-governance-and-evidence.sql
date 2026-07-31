BEGIN;

-- Release gates are only meaningful when every required item has its own evidence.
-- This table stores evidence references and review decisions, never credentials or secret values.
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

CREATE INDEX IF NOT EXISTS idx_platform_acceptance_evidence_gate
  ON platform_acceptance_evidence_item(gate_code, required, item_status, updated_at DESC);

ALTER TABLE supplier_api_connection
  ADD COLUMN IF NOT EXISTS reconciliation_path VARCHAR(300),
  ADD COLUMN IF NOT EXISTS sla_reference VARCHAR(300);

ALTER TABLE supplier_order
  ADD COLUMN IF NOT EXISTS fulfillment_mode VARCHAR(20) NOT NULL DEFAULT 'UNCONFIRMED',
  ADD COLUMN IF NOT EXISTS submission_evidence_ref VARCHAR(500);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname='ck_supplier_order_fulfillment_mode'
      AND conrelid='public.supplier_order'::regclass
  ) THEN
    ALTER TABLE supplier_order
      ADD CONSTRAINT ck_supplier_order_fulfillment_mode
      CHECK (fulfillment_mode IN ('UNCONFIRMED','MANUAL','API'));
  END IF;
END;
$$;

-- A gate cannot be promoted with a single summary path while required evidence remains open.
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

-- Reopening a required evidence item must also reopen a previously passed release gate.
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

-- If the final accepted production connector is disabled or loses approval, its gate is reopened.
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

-- Internal supplier records may not be presented as submitted or executing without a traceable
-- manual handoff or an accepted production API receipt.
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

-- Existing combination records remain readable and reviewable. New combination records are
-- forbidden, and a historical record may only be remapped after a matching business approval.
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

DROP TRIGGER IF EXISTS trg_platform_acceptance_evidence_item_updated_at
  ON platform_acceptance_evidence_item;
CREATE TRIGGER trg_platform_acceptance_evidence_item_updated_at
  BEFORE UPDATE ON platform_acceptance_evidence_item
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- A previous single-reference approval is not enough under the itemized evidence model.
UPDATE platform_acceptance_gate gate
SET status='IN_REVIEW',
    reviewed_at=NULL,
    review_note=concat_ws(
      E'\n',
      NULLIF(gate.review_note, ''),
      '已启用逐项验收证据清单，需重新完成必备项目复核。'
    )
WHERE gate.status='PASSED'
  AND EXISTS (
    SELECT 1
    FROM platform_acceptance_evidence_item item
    WHERE item.gate_code=gate.gate_code
      AND item.required
      AND item.item_status<>'VERIFIED'
  );

COMMIT;
