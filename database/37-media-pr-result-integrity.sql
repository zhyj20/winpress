BEGIN;

-- This is a forward-only guard. Do not repair historical tasks here: if a terminal media
-- task lacks both a verified result and an invitation fact, the migration must stop for a
-- business review instead of inventing contact or publication evidence.
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
    RAISE EXCEPTION 'media result integrity migration requires the verified schema-36 ledger baseline';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM publish_task task
    WHERE task.status IN ('COMPLETED','CLIENT_ACCEPTED')
      AND NOT EXISTS (
        SELECT 1
        FROM result_link result
        WHERE result.publish_task_id=task.id
          AND result.status='VERIFIED'
      )
  ) THEN
    RAISE EXCEPTION 'media result integrity preflight failed: terminal publish task without a verified result';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM publish_task task
    LEFT JOIN media_pr_invitation invitation ON invitation.publish_task_id=task.id
    WHERE task.channel_type='MEDIA_PR'
      AND task.status IN ('COMPLETED','CLIENT_ACCEPTED')
      AND (
        invitation.status IS DISTINCT FROM 'REPORTED'
        OR invitation.invited_at IS NULL
      )
  ) THEN
    RAISE EXCEPTION 'media result integrity preflight failed: terminal media task without a reported invitation fact';
  END IF;
END;
$$;

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

INSERT INTO schema_migration_ledger (
  migration_version,
  script_name,
  release_contract,
  apply_mode,
  verification_reference
)
VALUES (
  37,
  '37-media-pr-result-integrity.sql',
  'winpress-v4.2.26-20260731',
  'FORWARD',
  'MEDIA_PR_RESULT_CHAIN_INTEGRITY_20260731'
)
ON CONFLICT (migration_version) DO NOTHING;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM schema_migration_ledger
    WHERE migration_version=37
      AND script_name='37-media-pr-result-integrity.sql'
      AND release_contract='winpress-v4.2.26-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='MEDIA_PR_RESULT_CHAIN_INTEGRITY_20260731'
  ) THEN
    RAISE EXCEPTION 'media result integrity migration conflicts with an existing version 37 record';
  END IF;
END;
$$;

COMMENT ON FUNCTION enforce_publish_task_terminal_integrity() IS
  'Protects publish-task terminal states: verified result required for completion, and media tasks also require a recorded invitation before a reported outcome.';

COMMIT;
