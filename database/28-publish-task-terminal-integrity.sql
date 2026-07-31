BEGIN;

-- A customer-accepted task is a final delivery fact. Refuse to install the
-- runtime guard if existing accepted rows do not have verified evidence.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM publish_task task
    WHERE task.status = 'CLIENT_ACCEPTED'
      AND NOT EXISTS (
        SELECT 1
        FROM result_link result
        WHERE result.publish_task_id = task.id
          AND result.status = 'VERIFIED'
      )
  ) THEN
    RAISE EXCEPTION
      'publish task terminal integrity preflight failed: accepted task without verified result';
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

COMMIT;
