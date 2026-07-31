BEGIN;

-- Preserve existing historical rows exactly as recorded. These triggers only reject new writes
-- that would attach MEDIA_PR or DIRECT_PUBLISHING plan items to another service project.
CREATE OR REPLACE FUNCTION enforce_publish_plan_item_service_integrity() RETURNS TRIGGER AS $$
DECLARE
  project_service VARCHAR(40);
BEGIN
  SELECT requirement.requested_service
  INTO project_service
  FROM publish_plan plan
  JOIN project project_record ON project_record.id=plan.project_id
  JOIN customer_requirement requirement ON requirement.id=project_record.requirement_id
  WHERE plan.id=NEW.publish_plan_id;

  IF FOUND
     AND (
       project_service NOT IN ('MEDIA_PR','DIRECT_PUBLISHING')
       OR NEW.channel_type IS DISTINCT FROM project_service
     ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='publish plan item service must match the project service';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_publish_plan_item_service_integrity ON publish_plan_item;
CREATE TRIGGER trg_publish_plan_item_service_integrity
  BEFORE INSERT OR UPDATE OF publish_plan_id, channel_type ON publish_plan_item
  FOR EACH ROW EXECUTE FUNCTION enforce_publish_plan_item_service_integrity();

CREATE OR REPLACE FUNCTION enforce_publish_plan_project_service_integrity() RETURNS TRIGGER AS $$
DECLARE
  project_service VARCHAR(40);
BEGIN
  IF NEW.project_id IS NOT DISTINCT FROM OLD.project_id THEN
    RETURN NEW;
  END IF;

  SELECT requirement.requested_service
  INTO project_service
  FROM project project_record
  JOIN customer_requirement requirement ON requirement.id=project_record.requirement_id
  WHERE project_record.id=NEW.project_id;

  IF FOUND AND EXISTS (
    SELECT 1
    FROM publish_plan_item item
    WHERE item.publish_plan_id=OLD.id
      AND (
        project_service NOT IN ('MEDIA_PR','DIRECT_PUBLISHING')
        OR item.channel_type<>project_service
      )
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='publish plan service must match the destination project service';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_publish_plan_project_service_integrity ON publish_plan;
CREATE TRIGGER trg_publish_plan_project_service_integrity
  BEFORE UPDATE OF project_id ON publish_plan
  FOR EACH ROW EXECUTE FUNCTION enforce_publish_plan_project_service_integrity();

CREATE OR REPLACE FUNCTION enforce_project_plan_service_integrity() RETURNS TRIGGER AS $$
DECLARE
  project_service VARCHAR(40);
BEGIN
  IF NEW.requirement_id IS NOT DISTINCT FROM OLD.requirement_id THEN
    RETURN NEW;
  END IF;

  SELECT requested_service
  INTO project_service
  FROM customer_requirement
  WHERE id=NEW.requirement_id;

  IF FOUND AND EXISTS (
    SELECT 1
    FROM publish_plan plan
    JOIN publish_plan_item item ON item.publish_plan_id=plan.id
    WHERE plan.project_id=OLD.id
      AND (
        project_service NOT IN ('MEDIA_PR','DIRECT_PUBLISHING')
        OR item.channel_type<>project_service
      )
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='project service must match its existing publish plans';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_project_plan_service_integrity ON project;
CREATE TRIGGER trg_project_plan_service_integrity
  BEFORE UPDATE OF requirement_id ON project
  FOR EACH ROW EXECUTE FUNCTION enforce_project_plan_service_integrity();

CREATE OR REPLACE FUNCTION enforce_requirement_plan_service_integrity() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.requested_service IS NOT DISTINCT FROM OLD.requested_service THEN
    RETURN NEW;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM project project_record
    JOIN publish_plan plan ON plan.project_id=project_record.id
    JOIN publish_plan_item item ON item.publish_plan_id=plan.id
    WHERE project_record.requirement_id=OLD.id
      AND (
        NEW.requested_service NOT IN ('MEDIA_PR','DIRECT_PUBLISHING')
        OR item.channel_type<>NEW.requested_service
      )
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE='23514',
      MESSAGE='requirement service must match its existing publish plans';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_requirement_plan_service_integrity ON customer_requirement;
CREATE TRIGGER trg_requirement_plan_service_integrity
  BEFORE UPDATE OF requested_service ON customer_requirement
  FOR EACH ROW EXECUTE FUNCTION enforce_requirement_plan_service_integrity();

COMMIT;
