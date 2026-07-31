BEGIN;

-- A manually supplemented MEDIA_PR list is a valid customer request even while an
-- external media catalogue or an execution supplier has not been accepted.  The list
-- may create a customer-visible invitation task, but must not require a placeholder
-- publish channel or create a supplier order before project verification.
ALTER TABLE publish_plan_item
  ALTER COLUMN channel_id DROP NOT NULL;

ALTER TABLE publish_task
  ALTER COLUMN channel_id DROP NOT NULL;

COMMIT;
