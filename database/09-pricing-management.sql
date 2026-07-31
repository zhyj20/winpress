BEGIN;

CREATE TABLE IF NOT EXISTS quote_adjustment (
  id BIGSERIAL PRIMARY KEY,
  adjustment_no VARCHAR(40) NOT NULL UNIQUE,
  channel_id BIGINT NOT NULL REFERENCES publish_channel(id),
  previous_quote_id BIGINT REFERENCES channel_quote(id),
  current_quote_id BIGINT NOT NULL REFERENCES channel_quote(id),
  previous_customer_price NUMERIC(14,2),
  current_customer_price NUMERIC(14,2) NOT NULL,
  adjustment_mode VARCHAR(30) NOT NULL CHECK (adjustment_mode IN ('MANUAL','BATCH_PERCENT')),
  reason VARCHAR(300) NOT NULL,
  adjusted_by BIGINT NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quote_active_price
  ON channel_quote(channel_id, status, customer_price, valid_until);
CREATE INDEX IF NOT EXISTS idx_channel_direct_filter
  ON publish_channel(channel_type, status, category, region, publish_form, expected_days);
CREATE INDEX IF NOT EXISTS idx_quote_adjustment_channel_time
  ON quote_adjustment(channel_id, created_at DESC);

DROP TRIGGER IF EXISTS trg_quote_adjustment_updated_at ON quote_adjustment;
CREATE TRIGGER trg_quote_adjustment_updated_at BEFORE UPDATE ON quote_adjustment
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMIT;
