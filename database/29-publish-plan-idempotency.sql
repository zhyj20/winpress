BEGIN;

-- A network retry of the same customer action must return the original plan instead of
-- creating another set of execution tasks and internal orders. Historical plans keep NULL
-- values and are not reinterpreted or merged.
ALTER TABLE publish_plan
  ADD COLUMN IF NOT EXISTS submission_key VARCHAR(80),
  ADD COLUMN IF NOT EXISTS submission_hash VARCHAR(64);

ALTER TABLE publish_plan
  DROP CONSTRAINT IF EXISTS ck_publish_plan_submission_pair;

ALTER TABLE publish_plan
  ADD CONSTRAINT ck_publish_plan_submission_pair
    CHECK ((submission_key IS NULL) = (submission_hash IS NULL));

DROP INDEX IF EXISTS uq_publish_plan_submission_key;

CREATE UNIQUE INDEX uq_publish_plan_submission_key
  ON publish_plan(project_id, created_by, submission_key)
  WHERE submission_key IS NOT NULL;

COMMIT;
