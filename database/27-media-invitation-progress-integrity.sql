BEGIN;

-- A declined or explicitly closed media target is a terminal invitation outcome, not a
-- publication result. Keep that state separate from COMPLETED so customers are never asked to
-- accept a result that does not exist.
ALTER TABLE publish_task
  DROP CONSTRAINT IF EXISTS ck_publish_task_status;
ALTER TABLE publish_task
  ADD CONSTRAINT ck_publish_task_status CHECK (
    status IN (
      'PENDING_ASSIGNMENT','PENDING_EXECUTION','IN_PROGRESS','NEEDS_INFO',
      'EXCEPTION','COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING'
    )
  );

ALTER TABLE publish_task
  DROP CONSTRAINT IF EXISTS ck_publish_task_not_proceeding_channel;
ALTER TABLE publish_task
  ADD CONSTRAINT ck_publish_task_not_proceeding_channel
  CHECK (status<>'NOT_PROCEEDING' OR channel_type='MEDIA_PR') NOT VALID;

-- Reconcile only facts that are already present. This does not create an invitation, response,
-- attendance or report; it aligns the generic task state with the dedicated invitation record.
UPDATE publish_task task
SET status='IN_PROGRESS', exception_reason=NULL, updated_at=CURRENT_TIMESTAMP
FROM media_pr_invitation invitation
WHERE invitation.publish_task_id=task.id
  AND invitation.status IN ('INVITED','RESPONDED','ATTENDING')
  AND task.status IN ('PENDING_ASSIGNMENT','PENDING_EXECUTION','IN_PROGRESS','NEEDS_INFO');

UPDATE publish_task task
SET status='NOT_PROCEEDING', exception_reason=NULL, updated_at=CURRENT_TIMESTAMP
FROM media_pr_invitation invitation
WHERE invitation.publish_task_id=task.id
  AND invitation.status IN ('DECLINED','NOT_PROCEEDING')
  AND task.status NOT IN ('COMPLETED','CLIENT_ACCEPTED');

UPDATE publish_task task
SET status='COMPLETED', updated_at=CURRENT_TIMESTAMP
FROM media_pr_invitation invitation
WHERE invitation.publish_task_id=task.id
  AND invitation.status='REPORTED'
  AND task.status NOT IN ('COMPLETED','CLIENT_ACCEPTED')
  AND EXISTS (
    SELECT 1 FROM result_link result
    WHERE result.publish_task_id=task.id AND result.status='VERIFIED'
  );

ALTER TABLE publish_task
  VALIDATE CONSTRAINT ck_publish_task_not_proceeding_channel;

-- A confirmed plan proves that the intake stage has finished. It does not prove that a media
-- invitation was sent or that a channel accepted a submission.
UPDATE service_intake_task intake
SET status='COMPLETED',
    completed_at=COALESCE(completed_at, CURRENT_TIMESTAMP),
    customer_visible_note='服务范围已确认，执行任务已建立',
    updated_at=CURRENT_TIMESTAMP
WHERE intake.status NOT IN ('COMPLETED','CANCELLED')
  AND EXISTS (
    SELECT 1 FROM publish_plan plan
    WHERE plan.project_id=intake.project_id
      AND plan.status IN ('CONFIRMED','EXECUTING','COMPLETED')
  );

UPDATE publish_plan plan
SET status=CASE
      WHEN EXISTS (
        SELECT 1
        FROM publish_plan_item item
        JOIN publish_task task ON task.publish_plan_item_id=item.id
        WHERE item.publish_plan_id=plan.id
      ) AND NOT EXISTS (
        SELECT 1
        FROM publish_plan_item item
        JOIN publish_task task ON task.publish_plan_item_id=item.id
        WHERE item.publish_plan_id=plan.id
          AND task.status NOT IN ('COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING')
      ) THEN 'COMPLETED'
      WHEN EXISTS (
        SELECT 1
        FROM publish_plan_item item
        JOIN publish_task task ON task.publish_plan_item_id=item.id
        WHERE item.publish_plan_id=plan.id
          AND task.status IN (
            'IN_PROGRESS','NEEDS_INFO','EXCEPTION',
            'COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING'
          )
      ) THEN 'EXECUTING'
      ELSE 'CONFIRMED'
    END,
    updated_at=CURRENT_TIMESTAMP
WHERE plan.status IN ('CONFIRMED','EXECUTING','COMPLETED')
  AND EXISTS (
    SELECT 1
    FROM publish_plan_item item
    JOIN publish_task task ON task.publish_plan_item_id=item.id
    WHERE item.publish_plan_id=plan.id
  );

UPDATE project project_row
SET status=CASE
      WHEN project_row.status='CANCELLED' THEN 'CANCELLED'
      WHEN EXISTS (
        SELECT 1 FROM publish_plan waiting
        WHERE waiting.project_id=project_row.id AND waiting.status='WAITING_CONFIRMATION'
      ) THEN CASE
        WHEN EXISTS (
          SELECT 1 FROM publish_task task
          WHERE task.project_id=project_row.id
            AND task.status IN ('IN_PROGRESS','NEEDS_INFO','EXCEPTION')
        ) THEN 'IN_PROGRESS'
        ELSE 'PLANNING'
      END
      WHEN EXISTS (
        SELECT 1 FROM publish_task task WHERE task.project_id=project_row.id
      ) AND NOT EXISTS (
        SELECT 1 FROM publish_task task
        WHERE task.project_id=project_row.id
          AND task.status NOT IN ('COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING')
      ) THEN 'COMPLETED'
      WHEN EXISTS (
        SELECT 1 FROM publish_task task
        WHERE task.project_id=project_row.id
          AND task.status IN ('IN_PROGRESS','NEEDS_INFO','EXCEPTION')
      ) THEN 'IN_PROGRESS'
      ELSE 'PLANNING'
    END,
    updated_at=CURRENT_TIMESTAMP
FROM customer_requirement requirement
WHERE requirement.id=project_row.requirement_id
  AND requirement.requested_service IN ('MEDIA_PR','DIRECT_PUBLISHING')
  AND EXISTS (
    SELECT 1 FROM publish_task task WHERE task.project_id=project_row.id
  );

UPDATE direct_publish_order direct_order
SET status=CASE task.status
      WHEN 'COMPLETED' THEN 'COMPLETED'
      WHEN 'CLIENT_ACCEPTED' THEN 'COMPLETED'
      WHEN 'EXCEPTION' THEN 'EXCEPTION'
      WHEN 'IN_PROGRESS' THEN 'IN_PROGRESS'
      WHEN 'NEEDS_INFO' THEN 'IN_PROGRESS'
      ELSE 'SUBMITTED'
    END,
    updated_at=CURRENT_TIMESTAMP
FROM publish_task task
WHERE direct_order.publish_task_id=task.id;

COMMIT;
