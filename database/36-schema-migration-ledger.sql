BEGIN;

-- This ledger starts at the verified schema-35 baseline.  It deliberately does not
-- manufacture entries for older scripts whose execution history was not recorded.
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

DO $$
BEGIN
  IF
    to_regclass('public.customer_requirement') IS NULL OR
    to_regclass('public.supplier_api_connection') IS NULL OR
    to_regclass('public.platform_acceptance_evidence_item') IS NULL OR
    to_regclass('public.legacy_service_review') IS NULL OR
    to_regclass('public.open_api_application') IS NULL
  THEN
    RAISE EXCEPTION 'schema migration ledger baseline requires the verified schema-35 structures';
  END IF;

  IF (SELECT count(*) FROM platform_acceptance_evidence_item WHERE required) <> 28 THEN
    RAISE EXCEPTION 'schema migration ledger baseline requires all 28 release-evidence items';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgname='trg_platform_acceptance_gate_readiness'
      AND tgrelid='public.platform_acceptance_gate'::regclass
      AND NOT tgisinternal
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgname='trg_supplier_order_fulfillment_evidence'
      AND tgrelid='public.supplier_order'::regclass
      AND NOT tgisinternal
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgname='trg_legacy_combination_service_boundary'
      AND tgrelid='public.customer_requirement'::regclass
      AND NOT tgisinternal
  ) THEN
    RAISE EXCEPTION 'schema migration ledger baseline requires the verified schema-35 governance guards';
  END IF;
END;
$$;

INSERT INTO schema_migration_ledger (
  migration_version,
  script_name,
  release_contract,
  apply_mode,
  verification_reference
)
VALUES (
  36,
  '36-schema-migration-ledger.sql',
  'winpress-v4.2.25-20260731',
  'BASELINE',
  'SCHEMA35_STRUCTURAL_BASELINE_20260731'
)
ON CONFLICT (migration_version) DO NOTHING;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM schema_migration_ledger
    WHERE migration_version=36
      AND script_name='36-schema-migration-ledger.sql'
      AND release_contract='winpress-v4.2.25-20260731'
      AND apply_mode='BASELINE'
      AND verification_reference='SCHEMA35_STRUCTURAL_BASELINE_20260731'
  ) THEN
    RAISE EXCEPTION 'schema migration ledger baseline conflicts with an existing version 36 record';
  END IF;
END;
$$;

COMMENT ON TABLE schema_migration_ledger IS
  'Append-only structural migration baseline and forward migration ledger; never stores credentials or business facts.';

COMMIT;
