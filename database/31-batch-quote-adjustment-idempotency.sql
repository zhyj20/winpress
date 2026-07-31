BEGIN;

CREATE TABLE IF NOT EXISTS quote_adjustment_batch (
  id BIGSERIAL PRIMARY KEY,
  batch_no VARCHAR(40) NOT NULL UNIQUE,
  adjusted_by BIGINT NOT NULL REFERENCES app_user(id),
  submission_key VARCHAR(80) NOT NULL,
  submission_hash VARCHAR(64) NOT NULL,
  percentage NUMERIC(7,4) NOT NULL,
  valid_until TIMESTAMPTZ NOT NULL,
  public_terms VARCHAR(1000),
  reason VARCHAR(300) NOT NULL,
  channel_count INTEGER NOT NULL,
  adjusted_count INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_quote_adjustment_batch_submission UNIQUE (adjusted_by, submission_key),
  CONSTRAINT ck_quote_adjustment_batch_key CHECK (char_length(submission_key) BETWEEN 16 AND 80),
  CONSTRAINT ck_quote_adjustment_batch_hash CHECK (submission_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT ck_quote_adjustment_batch_percentage CHECK (percentage BETWEEN -50 AND 50),
  CONSTRAINT ck_quote_adjustment_batch_counts CHECK (
    channel_count BETWEEN 1 AND 200
    AND adjusted_count BETWEEN 0 AND channel_count
  ),
  CONSTRAINT ck_quote_adjustment_batch_status CHECK (status IN ('PROCESSING','COMPLETED'))
);

ALTER TABLE quote_adjustment
  ADD COLUMN IF NOT EXISTS batch_id BIGINT;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_quote_adjustment_batch'
      AND conrelid = 'public.quote_adjustment'::regclass
  ) THEN
    ALTER TABLE quote_adjustment
      ADD CONSTRAINT fk_quote_adjustment_batch
      FOREIGN KEY (batch_id) REFERENCES quote_adjustment_batch(id);
  END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_quote_adjustment_batch
  ON quote_adjustment(batch_id, channel_id)
  WHERE batch_id IS NOT NULL;

DROP TRIGGER IF EXISTS trg_quote_adjustment_batch_updated_at ON quote_adjustment_batch;
CREATE TRIGGER trg_quote_adjustment_batch_updated_at
BEFORE UPDATE ON quote_adjustment_batch
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMIT;
