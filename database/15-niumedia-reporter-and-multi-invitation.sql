BEGIN;

ALTER TABLE publish_plan_item
  DROP CONSTRAINT IF EXISTS publish_plan_item_publish_plan_id_channel_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_publish_plan_item_direct_channel
  ON publish_plan_item(publish_plan_id, channel_id)
  WHERE channel_type='DIRECT_PUBLISHING';

CREATE INDEX IF NOT EXISTS idx_publish_plan_item_media_target
  ON publish_plan_item(publish_plan_id, channel_id)
  WHERE channel_type='MEDIA_PR';

ALTER TABLE media_pr_invitation
  ADD COLUMN IF NOT EXISTS candidate_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
  ADD COLUMN IF NOT EXISTS external_reporter_id VARCHAR(120);

ALTER TABLE media_pr_invitation
  DROP CONSTRAINT IF EXISTS media_pr_invitation_candidate_type_check;

ALTER TABLE media_pr_invitation
  ADD CONSTRAINT media_pr_invitation_candidate_type_check
  CHECK (candidate_type IN ('MEDIA','REPORTER','MANUAL'));

ALTER TABLE media_pr_invitation
  ALTER COLUMN media_fit_score TYPE NUMERIC(10,2)
  USING media_fit_score::NUMERIC(10,2);

ALTER TABLE conference_media_candidate
  ADD COLUMN IF NOT EXISTS candidate_key VARCHAR(260),
  ADD COLUMN IF NOT EXISTS candidate_type VARCHAR(20) NOT NULL DEFAULT 'MEDIA',
  ADD COLUMN IF NOT EXISTS external_reporter_id VARCHAR(120),
  ADD COLUMN IF NOT EXISTS reporter_name VARCHAR(80),
  ADD COLUMN IF NOT EXISTS reporter_news_count BIGINT,
  ADD COLUMN IF NOT EXISTS media_fans_count BIGINT,
  ADD COLUMN IF NOT EXISTS logo_url VARCHAR(800),
  ADD COLUMN IF NOT EXISTS reporter_avatar_url VARCHAR(800);

UPDATE conference_media_candidate
SET candidate_key = 'MEDIA:' || external_media_id
WHERE candidate_key IS NULL OR btrim(candidate_key) = '';

ALTER TABLE conference_media_candidate
  ALTER COLUMN candidate_key SET NOT NULL,
  ALTER COLUMN fit_score TYPE NUMERIC(10,2)
  USING fit_score::NUMERIC(10,2);

ALTER TABLE conference_media_candidate
  DROP CONSTRAINT IF EXISTS conference_media_candidate_conference_project_id_external_m_key,
  DROP CONSTRAINT IF EXISTS conference_media_candidate_conference_project_id_external_media_id_key,
  DROP CONSTRAINT IF EXISTS conference_media_candidate_candidate_type_check;

ALTER TABLE conference_media_candidate
  ADD CONSTRAINT conference_media_candidate_candidate_type_check
  CHECK (candidate_type IN ('MEDIA','REPORTER','MANUAL'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_conference_media_candidate_key
  ON conference_media_candidate(conference_project_id, candidate_key);

COMMIT;
