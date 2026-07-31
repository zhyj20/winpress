BEGIN;

-- This forward-only migration protects the release-conference checklist from
-- unverified reopenings. It does not infer a historical completion time or
-- change a historical project/item state. Existing contradictory records must
-- be reviewed and repaired by the accountable business owner before this
-- migration is applied.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM schema_migration_ledger
    WHERE migration_version=39
      AND script_name='39-writing-assignment-radius-integrity.sql'
      AND release_contract='winpress-v4.2.28-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='WRITING_ASSIGNMENT_RADIUS_INTEGRITY_20260731'
  ) THEN
    RAISE EXCEPTION 'conference work item integrity migration requires the verified schema-39 ledger record';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM conference_work_item
    WHERE (status='COMPLETED' AND completed_at IS NULL)
       OR (status<>'COMPLETED' AND completed_at IS NOT NULL)
  ) THEN
    RAISE EXCEPTION 'conference work item integrity migration found a status/completion-time contradiction; repair it with verified historical evidence before applying';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM conference_project cp
    WHERE cp.status='COMPLETED'
      AND EXISTS (
        SELECT 1
        FROM conference_work_item item
        WHERE item.conference_project_id=cp.id
          AND item.status<>'COMPLETED'
      )
  ) THEN
    RAISE EXCEPTION 'conference work item integrity migration found a completed conference project with unfinished work items';
  END IF;
END;
$$;

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

INSERT INTO schema_migration_ledger (
  migration_version,
  script_name,
  release_contract,
  apply_mode,
  verification_reference
)
VALUES (
  40,
  '40-conference-work-item-state-integrity.sql',
  'winpress-v4.2.29-20260731',
  'FORWARD',
  'CONFERENCE_WORK_ITEM_STATE_INTEGRITY_20260731'
)
ON CONFLICT (migration_version) DO NOTHING;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM schema_migration_ledger
    WHERE migration_version=40
      AND script_name='40-conference-work-item-state-integrity.sql'
      AND release_contract='winpress-v4.2.29-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='CONFERENCE_WORK_ITEM_STATE_INTEGRITY_20260731'
  ) THEN
    RAISE EXCEPTION 'conference work item integrity migration conflicts with an existing version 40 record';
  END IF;
END;
$$;

COMMENT ON TABLE conference_work_item IS
  'Release-conference execution checklist. Completed items are terminal and require a recorded completion time.';

COMMIT;
