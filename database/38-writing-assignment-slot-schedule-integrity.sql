BEGIN;

-- This is a forward-only staffing integrity migration. It preserves legacy parent
-- assignments and their recorded response notes, but it never invents extra writers,
-- schedules or accepted work. Any active legacy record that cannot be represented
-- truthfully must be reviewed before the migration can proceed.
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
    RAISE EXCEPTION 'writing assignment schedule migration requires the verified schema-37 ledger baseline';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM writing_assignment assignment_record
    JOIN editorial_task task ON task.id=assignment_record.editorial_task_id
    JOIN customer_requirement requirement_record ON requirement_record.id=task.requirement_id
    WHERE assignment_record.status IN ('OFFERED','ACCEPTED')
      AND (
        assignment_record.writer_profile_id IS NULL
        OR requirement_record.event_time IS NULL
      )
  ) THEN
    RAISE EXCEPTION 'writing assignment schedule migration requires an actual writer and service start time for every active offered or accepted legacy assignment';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM writing_assignment assignment_record
    WHERE assignment_record.status='ACCEPTED'
      AND assignment_record.writer_count<>1
  ) THEN
    RAISE EXCEPTION 'writing assignment schedule migration found an active multi-writer legacy acceptance; confirm the real writer roster before migration';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM writing_assignment first_assignment
    JOIN editorial_task first_task ON first_task.id=first_assignment.editorial_task_id
    JOIN customer_requirement first_requirement ON first_requirement.id=first_task.requirement_id
    JOIN writing_assignment second_assignment
      ON second_assignment.writer_profile_id=first_assignment.writer_profile_id
     AND second_assignment.id>first_assignment.id
     AND second_assignment.status='ACCEPTED'
    JOIN editorial_task second_task ON second_task.id=second_assignment.editorial_task_id
    JOIN customer_requirement second_requirement ON second_requirement.id=second_task.requirement_id
    WHERE first_assignment.status='ACCEPTED'
      AND first_assignment.writer_profile_id IS NOT NULL
      AND first_requirement.event_time IS NOT NULL
      AND second_requirement.event_time IS NOT NULL
      AND tstzrange(
        first_requirement.event_time,
        first_requirement.event_time + (first_assignment.service_days * INTERVAL '1 day'),
        '[)'
      ) && tstzrange(
        second_requirement.event_time,
        second_requirement.event_time + (second_assignment.service_days * INTERVAL '1 day'),
        '[)'
      )
  ) THEN
    RAISE EXCEPTION 'writing assignment schedule migration found overlapping confirmed legacy writer assignments; resolve actual fulfilment before migration';
  END IF;
END;
$$;

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE writing_assignment
  DROP CONSTRAINT IF EXISTS writing_assignment_status_check;
ALTER TABLE writing_assignment
  ADD CONSTRAINT writing_assignment_status_check
    CHECK (status IN ('WAITING_MATCH','OFFERED','PARTIALLY_ACCEPTED','ACCEPTED','DECLINED','CANCELLED','COMPLETED'));

CREATE TABLE IF NOT EXISTS writing_assignment_member (
  id BIGSERIAL PRIMARY KEY,
  member_no VARCHAR(40) NOT NULL UNIQUE,
  assignment_id BIGINT NOT NULL REFERENCES writing_assignment(id) ON DELETE CASCADE,
  writer_profile_id BIGINT NOT NULL REFERENCES writer_profile(id),
  service_window TSTZRANGE,
  distance_km NUMERIC(10,2),
  status VARCHAR(30) NOT NULL DEFAULT 'OFFERED'
    CHECK (status IN ('OFFERED','ACCEPTED','DECLINED','CANCELLED','COMPLETED')),
  offered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at TIMESTAMPTZ,
  response_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_writing_assignment_member_writer UNIQUE (assignment_id, writer_profile_id),
  CONSTRAINT ck_writing_assignment_member_window
    CHECK (status NOT IN ('OFFERED','ACCEPTED') OR service_window IS NOT NULL),
  CONSTRAINT ex_writing_assignment_member_no_overlap
    EXCLUDE USING GIST (
      writer_profile_id WITH =,
      service_window WITH &&
    ) WHERE (status='ACCEPTED' AND service_window IS NOT NULL)
);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname='ex_writing_assignment_member_no_overlap'
      AND conrelid='public.writing_assignment_member'::regclass
  ) THEN
    ALTER TABLE writing_assignment_member
      ADD CONSTRAINT ex_writing_assignment_member_no_overlap
      EXCLUDE USING GIST (
        writer_profile_id WITH =,
        service_window WITH &&
      ) WHERE (status='ACCEPTED' AND service_window IS NOT NULL);
  END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_writing_assignment_member_assignment
  ON writing_assignment_member(assignment_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_writing_assignment_member_writer
  ON writing_assignment_member(writer_profile_id, status, updated_at DESC);

DROP TRIGGER IF EXISTS trg_writing_assignment_member_updated_at
  ON writing_assignment_member;
CREATE TRIGGER trg_writing_assignment_member_updated_at
  BEFORE UPDATE ON writing_assignment_member
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- Backfill only an already identified legacy writer. The original parent fields remain
-- untouched for traceability; future runtime queries use the per-seat rows instead.
INSERT INTO writing_assignment_member (
  member_no,
  assignment_id,
  writer_profile_id,
  service_window,
  distance_km,
  status,
  offered_at,
  responded_at,
  response_note
)
SELECT
  'WRT-MBR-LEGACY-' || assignment_record.id,
  assignment_record.id,
  assignment_record.writer_profile_id,
  CASE
    WHEN requirement_record.event_time IS NULL THEN NULL
    ELSE tstzrange(
      requirement_record.event_time,
      requirement_record.event_time + (assignment_record.service_days * INTERVAL '1 day'),
      '[)'
    )
  END,
  assignment_record.distance_km,
  CASE assignment_record.status
    WHEN 'OFFERED' THEN 'OFFERED'
    WHEN 'ACCEPTED' THEN 'ACCEPTED'
    WHEN 'COMPLETED' THEN 'COMPLETED'
    WHEN 'DECLINED' THEN 'DECLINED'
    WHEN 'CANCELLED' THEN 'CANCELLED'
    ELSE 'CANCELLED'
  END,
  COALESCE(assignment_record.offered_at, assignment_record.created_at),
  assignment_record.responded_at,
  assignment_record.response_note
FROM writing_assignment assignment_record
JOIN editorial_task task ON task.id=assignment_record.editorial_task_id
JOIN customer_requirement requirement_record ON requirement_record.id=task.requirement_id
WHERE assignment_record.writer_profile_id IS NOT NULL
  AND assignment_record.status IN ('OFFERED','ACCEPTED','COMPLETED','DECLINED','CANCELLED')
ON CONFLICT (assignment_id, writer_profile_id) DO NOTHING;

-- A legacy decline remains present on the individual seat. The parent becomes ready to
-- match again, while a partially filled multi-writer order remains visibly incomplete.
UPDATE writing_assignment assignment_record
SET status = CASE
  WHEN assignment_record.status IN ('COMPLETED','CANCELLED') THEN assignment_record.status
  WHEN (
    SELECT count(*)
    FROM writing_assignment_member member
    WHERE member.assignment_id=assignment_record.id
      AND member.status='ACCEPTED'
  ) >= assignment_record.writer_count THEN 'ACCEPTED'
  WHEN EXISTS (
    SELECT 1
    FROM writing_assignment_member member
    WHERE member.assignment_id=assignment_record.id
      AND member.status='ACCEPTED'
  ) THEN 'PARTIALLY_ACCEPTED'
  WHEN EXISTS (
    SELECT 1
    FROM writing_assignment_member member
    WHERE member.assignment_id=assignment_record.id
      AND member.status='OFFERED'
  ) THEN 'OFFERED'
  ELSE 'WAITING_MATCH'
END
WHERE assignment_record.status NOT IN ('COMPLETED','CANCELLED');

INSERT INTO schema_migration_ledger (
  migration_version,
  script_name,
  release_contract,
  apply_mode,
  verification_reference
)
VALUES (
  38,
  '38-writing-assignment-slot-schedule-integrity.sql',
  'winpress-v4.2.27-20260731',
  'FORWARD',
  'WRITING_ASSIGNMENT_SLOT_AND_SCHEDULE_INTEGRITY_20260731'
)
ON CONFLICT (migration_version) DO NOTHING;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM schema_migration_ledger
    WHERE migration_version=38
      AND script_name='38-writing-assignment-slot-schedule-integrity.sql'
      AND release_contract='winpress-v4.2.27-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='WRITING_ASSIGNMENT_SLOT_AND_SCHEDULE_INTEGRITY_20260731'
  ) THEN
    RAISE EXCEPTION 'writing assignment schedule migration conflicts with an existing version 38 record';
  END IF;
END;
$$;

COMMENT ON TABLE writing_assignment_member IS
  'Per-writer onsite-writing allocations. Confirmed schedule windows have a database exclusion constraint and legacy parent records remain traceable.';

COMMIT;
