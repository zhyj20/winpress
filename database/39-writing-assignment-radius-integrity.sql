BEGIN;

-- This forward-only migration makes an existing writer service radius enforceable for
-- active onsite-writing seats. It never calculates a distance, changes a writer's
-- radius, or fills in missing legacy data. A record without an auditable manual
-- distance must be reviewed before a radius can be applied to that active seat.
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
    RAISE EXCEPTION 'writing assignment radius migration requires the verified schema-38 ledger record';
  END IF;

  IF EXISTS (
    SELECT 1 FROM writer_profile WHERE service_radius_km < 0
  ) THEN
    RAISE EXCEPTION 'writing assignment radius migration found a negative writer service radius';
  END IF;

  IF EXISTS (
    SELECT 1 FROM writing_assignment_member WHERE distance_km < 0
  ) THEN
    RAISE EXCEPTION 'writing assignment radius migration found a negative assignment distance';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM writing_assignment_member member
    JOIN writer_profile profile ON profile.id=member.writer_profile_id
    WHERE member.status IN ('OFFERED','ACCEPTED')
      AND profile.service_radius_km IS NOT NULL
      AND (member.distance_km IS NULL OR member.distance_km > profile.service_radius_km)
  ) THEN
    RAISE EXCEPTION 'writing assignment radius migration found an active seat outside its configured radius or without a verified distance';
  END IF;
END;
$$;

ALTER TABLE writer_profile
  DROP CONSTRAINT IF EXISTS ck_writer_profile_service_radius_nonnegative;
ALTER TABLE writer_profile
  ADD CONSTRAINT ck_writer_profile_service_radius_nonnegative
    CHECK (service_radius_km IS NULL OR service_radius_km >= 0);

ALTER TABLE writing_assignment_member
  DROP CONSTRAINT IF EXISTS ck_writing_assignment_member_distance_nonnegative;
ALTER TABLE writing_assignment_member
  ADD CONSTRAINT ck_writing_assignment_member_distance_nonnegative
    CHECK (distance_km IS NULL OR distance_km >= 0);

CREATE OR REPLACE FUNCTION enforce_writing_assignment_member_radius_integrity()
RETURNS TRIGGER AS $$
DECLARE
  configured_radius NUMERIC(10,2);
BEGIN
  IF NEW.status NOT IN ('OFFERED','ACCEPTED') THEN
    RETURN NEW;
  END IF;

  SELECT service_radius_km INTO configured_radius
  FROM writer_profile
  WHERE id=NEW.writer_profile_id;

  IF configured_radius IS NULL THEN
    RETURN NEW;
  END IF;
  IF NEW.distance_km IS NULL THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='writing assignment member distance is required when writer service radius is configured';
  END IF;
  IF NEW.distance_km > configured_radius THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='writing assignment member distance exceeds writer service radius';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_writing_assignment_member_radius_integrity
  ON writing_assignment_member;
CREATE TRIGGER trg_writing_assignment_member_radius_integrity
  BEFORE INSERT OR UPDATE OF writer_profile_id, distance_km, status
  ON writing_assignment_member
  FOR EACH ROW EXECUTE FUNCTION enforce_writing_assignment_member_radius_integrity();

CREATE OR REPLACE FUNCTION enforce_writer_profile_radius_integrity()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.service_radius_km IS NULL THEN
    RETURN NEW;
  END IF;
  IF EXISTS (
    SELECT 1
    FROM writing_assignment_member member
    WHERE member.writer_profile_id=NEW.id
      AND member.status IN ('OFFERED','ACCEPTED')
      AND (member.distance_km IS NULL OR member.distance_km > NEW.service_radius_km)
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='writer service radius cannot be reduced below an active assignment distance';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_writer_profile_radius_integrity ON writer_profile;
CREATE TRIGGER trg_writer_profile_radius_integrity
  BEFORE UPDATE OF service_radius_km ON writer_profile
  FOR EACH ROW EXECUTE FUNCTION enforce_writer_profile_radius_integrity();

INSERT INTO schema_migration_ledger (
  migration_version,
  script_name,
  release_contract,
  apply_mode,
  verification_reference
)
VALUES (
  39,
  '39-writing-assignment-radius-integrity.sql',
  'winpress-v4.2.28-20260731',
  'FORWARD',
  'WRITING_ASSIGNMENT_RADIUS_INTEGRITY_20260731'
)
ON CONFLICT (migration_version) DO NOTHING;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM schema_migration_ledger
    WHERE migration_version=39
      AND script_name='39-writing-assignment-radius-integrity.sql'
      AND release_contract='winpress-v4.2.28-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='WRITING_ASSIGNMENT_RADIUS_INTEGRITY_20260731'
  ) THEN
    RAISE EXCEPTION 'writing assignment radius migration conflicts with an existing version 39 record';
  END IF;
END;
$$;

COMMENT ON TABLE writing_assignment_member IS
  'Per-writer onsite-writing allocations. Active seats with a configured writer radius require a manually verified distance within that radius; no location is inferred by the platform.';

COMMIT;
