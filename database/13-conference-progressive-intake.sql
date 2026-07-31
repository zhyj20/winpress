BEGIN;

ALTER TABLE customer_requirement
  ALTER COLUMN facts DROP NOT NULL,
  ALTER COLUMN objective DROP NOT NULL;

ALTER TABLE conference_project
  ALTER COLUMN conference_type DROP NOT NULL,
  ALTER COLUMN conference_format DROP NOT NULL,
  ALTER COLUMN media_goal DROP NOT NULL;

COMMIT;
