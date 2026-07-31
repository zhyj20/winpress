BEGIN;

-- Older local data imported through a non-UTF-8 path could leave customer-visible intake
-- titles as question marks. The original wording cannot be recovered, so restore the concise
-- service-stage title from the stored service type rather than inventing a channel or supplier.
UPDATE service_intake_task
SET title = CASE
      WHEN service_type = 'MEDIA_PR' THEN '确认媒体邀请范围'
      WHEN service_type = 'DIRECT_PUBLISHING' THEN '确认稿件与发稿范围'
      ELSE title
    END,
    customer_visible_note = COALESCE(
      NULLIF(customer_visible_note, ''),
      '需求已提交，等待项目负责人确认服务范围。'
    )
WHERE btrim(title) ~ '^\?+$';

-- Prevent an encoding fallback from reaching customer, operator or administrator work queues.
ALTER TABLE service_intake_task
  DROP CONSTRAINT IF EXISTS ck_service_intake_task_title_not_placeholder;

ALTER TABLE service_intake_task
  ADD CONSTRAINT ck_service_intake_task_title_not_placeholder
  CHECK (btrim(title) <> '' AND btrim(title) !~ '^\?+$');

COMMIT;
