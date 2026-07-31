BEGIN;

-- Payment, refund and adjustment facts must be recorded independently from the settlement
-- status. A settlement can no longer become "paid" merely because an operator changed a
-- dropdown value.
CREATE TABLE IF NOT EXISTS settlement_transaction (
  id BIGSERIAL PRIMARY KEY,
  transaction_no VARCHAR(40) NOT NULL UNIQUE,
  settlement_order_id BIGINT NOT NULL REFERENCES settlement_order(id),
  transaction_type VARCHAR(30) NOT NULL,
  amount NUMERIC(14,2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
  occurred_at TIMESTAMPTZ NOT NULL,
  reference_no VARCHAR(120),
  customer_note VARCHAR(500),
  internal_note VARCHAR(1000),
  status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
  created_by BIGINT NOT NULL REFERENCES app_user(id),
  voided_by BIGINT REFERENCES app_user(id),
  voided_at TIMESTAMPTZ,
  void_reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_settlement_transaction_type CHECK (
    transaction_type IN (
      'PAYMENT',
      'REFUND',
      'CREDIT_ADJUSTMENT',
      'DEBIT_ADJUSTMENT',
      'WRITE_OFF'
    )
  ),
  CONSTRAINT ck_settlement_transaction_status CHECK (status IN ('CONFIRMED', 'VOIDED')),
  CONSTRAINT ck_settlement_transaction_amount CHECK (amount > 0),
  CONSTRAINT ck_settlement_transaction_evidence CHECK (
    NULLIF(btrim(reference_no), '') IS NOT NULL
    OR NULLIF(btrim(customer_note), '') IS NOT NULL
  ),
  CONSTRAINT ck_settlement_transaction_void_state CHECK (
    (status = 'CONFIRMED' AND voided_by IS NULL AND voided_at IS NULL AND void_reason IS NULL)
    OR
    (
      status = 'VOIDED'
      AND voided_by IS NOT NULL
      AND voided_at IS NOT NULL
      AND NULLIF(btrim(void_reason), '') IS NOT NULL
    )
  )
);

CREATE INDEX IF NOT EXISTS idx_settlement_transaction_settlement_time
  ON settlement_transaction(settlement_order_id, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_settlement_transaction_type_status
  ON settlement_transaction(transaction_type, status, occurred_at DESC);

DROP TRIGGER IF EXISTS trg_settlement_transaction_updated_at ON settlement_transaction;
CREATE TRIGGER trg_settlement_transaction_updated_at
  BEFORE UPDATE ON settlement_transaction
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMIT;
