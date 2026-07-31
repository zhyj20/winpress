-- Local demonstration coverage only.
-- The guard is the stable demo editorial-task number, so this script cannot create an
-- assignment for an arbitrary customer order in a reused database.
BEGIN;

INSERT INTO writing_assignment (
  assignment_no, editorial_task_id, matching_mode, service_location,
  service_days, writer_count, unit_price_snapshot, estimated_amount_snapshot, status
)
SELECT
  'WRT-ASG-DEMO-002',
  editorial_task.id,
  'NEAREST_AVAILABLE',
  requirement.event_location,
  requirement.service_days,
  requirement.writer_count,
  requirement.unit_price,
  requirement.estimated_amount,
  'WAITING_MATCH'
FROM editorial_task
JOIN customer_requirement requirement ON requirement.id = editorial_task.requirement_id
WHERE editorial_task.task_no = 'EDT-DEMO-002'
  AND requirement.requirement_no = 'REQ-DEMO-002'
  AND requirement.requested_service = 'ONSITE_WRITING'
ON CONFLICT (assignment_no) DO NOTHING;

COMMIT;
