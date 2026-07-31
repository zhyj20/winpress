BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_channel_quote_price_integrity'
      AND conrelid = 'public.channel_quote'::regclass
  ) THEN
    ALTER TABLE channel_quote
      ADD CONSTRAINT ck_channel_quote_price_integrity
      CHECK (
        customer_price > 0
        AND (cost_price IS NULL OR (cost_price >= 0 AND customer_price >= cost_price))
      ) NOT VALID;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_channel_quote_validity'
      AND conrelid = 'public.channel_quote'::regclass
  ) THEN
    ALTER TABLE channel_quote
      ADD CONSTRAINT ck_channel_quote_validity
      CHECK (valid_until > valid_from) NOT VALID;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_channel_quote_status'
      AND conrelid = 'public.channel_quote'::regclass
  ) THEN
    ALTER TABLE channel_quote
      ADD CONSTRAINT ck_channel_quote_status
      CHECK (status IN ('ACTIVE','REVIEW_REQUIRED','INACTIVE','SUPERSEDED')) NOT VALID;
  END IF;
END $$;

ALTER TABLE channel_quote VALIDATE CONSTRAINT ck_channel_quote_price_integrity;
ALTER TABLE channel_quote VALIDATE CONSTRAINT ck_channel_quote_validity;
ALTER TABLE channel_quote VALIDATE CONSTRAINT ck_channel_quote_status;

CREATE UNIQUE INDEX IF NOT EXISTS uq_channel_quote_one_active_per_channel
  ON channel_quote(channel_id)
  WHERE status='ACTIVE';

COMMIT;
