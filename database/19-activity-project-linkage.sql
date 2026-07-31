BEGIN;

-- An activity may contain several independently ordered services.  The root reference is
-- deliberately optional: existing standalone projects stay standalone, and a linked service
-- keeps its own requirement, project, task flow and price records.
ALTER TABLE project
  ADD COLUMN IF NOT EXISTS activity_root_project_id BIGINT;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'project'::regclass
      AND conname = 'fk_project_activity_root'
  ) THEN
    ALTER TABLE project
      ADD CONSTRAINT fk_project_activity_root
      FOREIGN KEY (activity_root_project_id) REFERENCES project(id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'project'::regclass
      AND conname = 'ck_project_activity_root_not_self'
  ) THEN
    ALTER TABLE project
      ADD CONSTRAINT ck_project_activity_root_not_self
      CHECK (activity_root_project_id IS NULL OR activity_root_project_id <> id);
  END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_project_activity_root
  ON project(activity_root_project_id)
  WHERE activity_root_project_id IS NOT NULL;

COMMIT;
