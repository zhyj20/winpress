BEGIN;

DO $$
BEGIN
  IF to_regclass('public.conference_project') IS NULL THEN
    RAISE EXCEPTION '请先执行 database/07-news-conference-replaces-owned-channel.sql';
  END IF;
END $$;

ALTER TABLE media_pr_invitation
  ALTER COLUMN journalist_name DROP NOT NULL;

ALTER TABLE media_pr_invitation
  ADD COLUMN IF NOT EXISTS external_media_id VARCHAR(120),
  ADD COLUMN IF NOT EXISTS media_attribute VARCHAR(80),
  ADD COLUMN IF NOT EXISTS media_province VARCHAR(80),
  ADD COLUMN IF NOT EXISTS media_city VARCHAR(80),
  ADD COLUMN IF NOT EXISTS media_channel_form VARCHAR(120),
  ADD COLUMN IF NOT EXISTS media_category VARCHAR(80),
  ADD COLUMN IF NOT EXISTS media_fit_score INTEGER;

CREATE TABLE IF NOT EXISTS conference_media_candidate (
  id BIGSERIAL PRIMARY KEY,
  candidate_no VARCHAR(40) NOT NULL UNIQUE,
  conference_project_id BIGINT NOT NULL REFERENCES conference_project(id) ON DELETE CASCADE,
  external_media_id VARCHAR(120) NOT NULL,
  media_name VARCHAR(180) NOT NULL,
  media_attribute VARCHAR(80),
  province VARCHAR(80),
  city VARCHAR(80),
  channel_form VARCHAR(120),
  category VARCHAR(80),
  coverage_tags TEXT,
  operation_note TEXT,
  fit_score INTEGER,
  selected_by BIGINT REFERENCES app_user(id),
  managed_operator_id BIGINT REFERENCES app_user(id),
  selected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  invited_at TIMESTAMPTZ,
  responded_at TIMESTAMPTZ,
  note TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'CANDIDATE' CHECK (status IN ('CANDIDATE','READY_TO_INVITE','INVITED','RESPONDED','DECLINED','ATTENDING','NOT_PROCEEDING')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(conference_project_id, external_media_id)
);

CREATE INDEX IF NOT EXISTS idx_conference_media_candidate_status
  ON conference_media_candidate(conference_project_id, status, fit_score DESC);

DROP TRIGGER IF EXISTS trg_conference_media_candidate_updated_at ON conference_media_candidate;
CREATE TRIGGER trg_conference_media_candidate_updated_at BEFORE UPDATE ON conference_media_candidate
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMIT;
