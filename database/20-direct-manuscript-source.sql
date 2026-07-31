BEGIN;

-- A direct-publishing project receives its own approved manuscript version. These nullable
-- references retain the customer-authorized source for audit without sharing one manuscript row
-- across two independently priced projects.
ALTER TABLE manuscript_version
  ADD COLUMN IF NOT EXISTS source_manuscript_id BIGINT,
  ADD COLUMN IF NOT EXISTS source_version_id BIGINT;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'manuscript_version'::regclass
      AND conname = 'fk_manuscript_version_source_manuscript'
  ) THEN
    ALTER TABLE manuscript_version
      ADD CONSTRAINT fk_manuscript_version_source_manuscript
      FOREIGN KEY (source_manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'manuscript_version'::regclass
      AND conname = 'fk_manuscript_version_source_version'
  ) THEN
    ALTER TABLE manuscript_version
      ADD CONSTRAINT fk_manuscript_version_source_version
      FOREIGN KEY (source_version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'manuscript_version'::regclass
      AND conname = 'ck_manuscript_version_source_pair'
  ) THEN
    ALTER TABLE manuscript_version
      ADD CONSTRAINT ck_manuscript_version_source_pair
      CHECK ((source_manuscript_id IS NULL) = (source_version_id IS NULL));
  END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_manuscript_version_source
  ON manuscript_version(source_manuscript_id, source_version_id)
  WHERE source_manuscript_id IS NOT NULL;

COMMIT;
