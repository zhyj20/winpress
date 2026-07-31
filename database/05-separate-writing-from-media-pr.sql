BEGIN;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'editorial_task'
      AND column_name = 'interview_plan'
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'editorial_task'
      AND column_name = 'writing_brief'
  ) THEN
    ALTER TABLE editorial_task RENAME COLUMN interview_plan TO writing_brief;
  END IF;
END $$;

UPDATE editorial_task
SET writing_brief = '根据客户提供的事实材料、写作目的和交付要求完成稿件撰写，初稿提交客户审核。'
WHERE task_no = 'EDT-DEMO-001';

COMMIT;
