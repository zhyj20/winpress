BEGIN;

-- A customer-accepted result is a terminal, auditable fact. Keep task and evidence states
-- constrained even when data is written outside the application service.
ALTER TABLE publish_task
  DROP CONSTRAINT IF EXISTS ck_publish_task_status;
ALTER TABLE publish_task
  ADD CONSTRAINT ck_publish_task_status CHECK (
    status IN (
      'PENDING_ASSIGNMENT','PENDING_EXECUTION','IN_PROGRESS','NEEDS_INFO',
      'EXCEPTION','COMPLETED','CLIENT_ACCEPTED'
    )
  );

ALTER TABLE media_pr_invitation
  DROP CONSTRAINT IF EXISTS ck_media_pr_invitation_status;
ALTER TABLE media_pr_invitation
  ADD CONSTRAINT ck_media_pr_invitation_status CHECK (
    status IN (
      'PENDING','INVITED','RESPONDED','DECLINED','ATTENDING','REPORTED','NOT_PROCEEDING'
    )
  );

ALTER TABLE result_link
  DROP CONSTRAINT IF EXISTS ck_result_link_status;
ALTER TABLE result_link
  ADD CONSTRAINT ck_result_link_status CHECK (
    status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED')
  );

CREATE UNIQUE INDEX IF NOT EXISTS uq_result_link_task_url
  ON result_link(publish_task_id, url);

COMMIT;
