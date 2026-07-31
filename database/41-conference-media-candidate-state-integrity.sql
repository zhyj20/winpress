BEGIN;

-- This forward-only migration makes the conference invite list an auditable contact timeline.
-- It does not create invitation, reply, attendance or decline facts for historical candidates.
-- Any incompatible historical record stops the migration for manual, evidence-based review.
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
    RAISE EXCEPTION 'conference media candidate integrity migration requires the verified schema-40 ledger record';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM conference_media_candidate
    WHERE (status IN ('CANDIDATE','READY_TO_INVITE')
        AND (invited_at IS NOT NULL OR responded_at IS NOT NULL))
       OR (status='INVITED' AND (invited_at IS NULL OR responded_at IS NOT NULL))
       OR (status IN ('RESPONDED','DECLINED','ATTENDING')
        AND (invited_at IS NULL OR responded_at IS NULL))
       OR (responded_at IS NOT NULL AND invited_at IS NULL)
       OR (invited_at IS NOT NULL AND invited_at < selected_at)
       OR (responded_at IS NOT NULL AND responded_at < invited_at)
       OR (status IN ('RESPONDED','DECLINED','ATTENDING','NOT_PROCEEDING')
        AND btrim(COALESCE(note, '')) = '')
  ) THEN
    RAISE EXCEPTION 'conference media candidate integrity migration found an unverifiable status, timestamp or contact-note record; repair it from verified business evidence before applying';
  END IF;
END;
$$;

ALTER TABLE conference_media_candidate
  DROP CONSTRAINT IF EXISTS ck_conference_media_candidate_status_timeline;
ALTER TABLE conference_media_candidate
  ADD CONSTRAINT ck_conference_media_candidate_status_timeline
    CHECK (
      (status IN ('CANDIDATE','READY_TO_INVITE')
        AND invited_at IS NULL AND responded_at IS NULL)
      OR (status='INVITED' AND invited_at IS NOT NULL AND responded_at IS NULL)
      OR (status IN ('RESPONDED','DECLINED','ATTENDING')
        AND invited_at IS NOT NULL AND responded_at IS NOT NULL)
      OR (status='NOT_PROCEEDING'
        AND (responded_at IS NULL OR invited_at IS NOT NULL))
    );

ALTER TABLE conference_media_candidate
  DROP CONSTRAINT IF EXISTS ck_conference_media_candidate_contact_time_order;
ALTER TABLE conference_media_candidate
  ADD CONSTRAINT ck_conference_media_candidate_contact_time_order
    CHECK (
      (invited_at IS NULL OR invited_at >= selected_at)
      AND (responded_at IS NULL OR (invited_at IS NOT NULL AND responded_at >= invited_at))
    );

ALTER TABLE conference_media_candidate
  DROP CONSTRAINT IF EXISTS ck_conference_media_candidate_outcome_note;
ALTER TABLE conference_media_candidate
  ADD CONSTRAINT ck_conference_media_candidate_outcome_note
    CHECK (
      status NOT IN ('RESPONDED','DECLINED','ATTENDING','NOT_PROCEEDING')
      OR btrim(COALESCE(note, '')) <> ''
    );

CREATE OR REPLACE FUNCTION enforce_conference_media_candidate_state_integrity()
RETURNS TRIGGER AS $$
BEGIN
  IF OLD.invited_at IS NOT NULL AND NEW.invited_at IS DISTINCT FROM OLD.invited_at THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='conference media candidate invitation time cannot be changed';
  END IF;
  IF OLD.responded_at IS NOT NULL AND NEW.responded_at IS DISTINCT FROM OLD.responded_at THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='conference media candidate response time cannot be changed';
  END IF;
  IF OLD.status IS DISTINCT FROM NEW.status
    AND NOT (
      (OLD.status='CANDIDATE' AND NEW.status IN ('READY_TO_INVITE','NOT_PROCEEDING'))
      OR (OLD.status='READY_TO_INVITE' AND NEW.status IN ('INVITED','NOT_PROCEEDING'))
      OR (OLD.status='INVITED' AND NEW.status IN ('RESPONDED','DECLINED','ATTENDING','NOT_PROCEEDING'))
      OR (OLD.status='RESPONDED' AND NEW.status IN ('DECLINED','ATTENDING','NOT_PROCEEDING'))
    ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='conference media candidate status transition is not allowed';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_conference_media_candidate_state_integrity
  ON conference_media_candidate;
CREATE TRIGGER trg_conference_media_candidate_state_integrity
  BEFORE UPDATE OF status, invited_at, responded_at ON conference_media_candidate
  FOR EACH ROW EXECUTE FUNCTION enforce_conference_media_candidate_state_integrity();

INSERT INTO schema_migration_ledger (
  migration_version,
  script_name,
  release_contract,
  apply_mode,
  verification_reference
)
VALUES (
  41,
  '41-conference-media-candidate-state-integrity.sql',
  'winpress-v4.2.30-20260731',
  'FORWARD',
  'CONFERENCE_MEDIA_CANDIDATE_STATE_INTEGRITY_20260731'
)
ON CONFLICT (migration_version) DO NOTHING;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM schema_migration_ledger
    WHERE migration_version=41
      AND script_name='41-conference-media-candidate-state-integrity.sql'
      AND release_contract='winpress-v4.2.30-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='CONFERENCE_MEDIA_CANDIDATE_STATE_INTEGRITY_20260731'
  ) THEN
    RAISE EXCEPTION 'conference media candidate integrity migration conflicts with an existing version 41 record';
  END IF;
END;
$$;

COMMENT ON TABLE conference_media_candidate IS
  'Conference invite candidates. Contact milestones are ordered, timestamped and non-reopenable after an outcome is recorded.';

COMMIT;
