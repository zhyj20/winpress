BEGIN;

-- A normal media invitation can start from an event brief. Direct publishing
-- and exclusive media arrangements continue to require an approved version in
-- the service layer.
ALTER TABLE publish_plan ALTER COLUMN manuscript_id DROP NOT NULL;
ALTER TABLE publish_plan ALTER COLUMN manuscript_version_id DROP NOT NULL;
ALTER TABLE publish_task ALTER COLUMN manuscript_id DROP NOT NULL;
ALTER TABLE publish_task ALTER COLUMN manuscript_version_id DROP NOT NULL;

COMMIT;
