BEGIN;

ALTER TABLE customer_requirement
  ADD COLUMN IF NOT EXISTS service_days SMALLINT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS writer_count SMALLINT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS unit_price NUMERIC(14,2),
  ADD COLUMN IF NOT EXISTS estimated_amount NUMERIC(14,2),
  ADD COLUMN IF NOT EXISTS onsite_contact_name VARCHAR(80),
  ADD COLUMN IF NOT EXISTS onsite_contact_mobile VARCHAR(30),
  ADD COLUMN IF NOT EXISTS deliverable_requirement TEXT,
  ADD COLUMN IF NOT EXISTS matching_preference VARCHAR(40) NOT NULL DEFAULT 'EXPERIENCE_FIRST';

ALTER TABLE customer_requirement DROP CONSTRAINT IF EXISTS ck_requirement_service_days;
ALTER TABLE customer_requirement ADD CONSTRAINT ck_requirement_service_days CHECK (service_days BETWEEN 1 AND 30);
ALTER TABLE customer_requirement DROP CONSTRAINT IF EXISTS ck_requirement_writer_count;
ALTER TABLE customer_requirement ADD CONSTRAINT ck_requirement_writer_count CHECK (writer_count BETWEEN 1 AND 10);
ALTER TABLE customer_requirement DROP CONSTRAINT IF EXISTS ck_requirement_unit_price;
ALTER TABLE customer_requirement ADD CONSTRAINT ck_requirement_unit_price CHECK (unit_price IS NULL OR unit_price >= 0);
ALTER TABLE customer_requirement DROP CONSTRAINT IF EXISTS ck_requirement_estimated_amount;
ALTER TABLE customer_requirement ADD CONSTRAINT ck_requirement_estimated_amount CHECK (estimated_amount IS NULL OR estimated_amount >= 0);

CREATE INDEX IF NOT EXISTS idx_requirement_service_event
  ON customer_requirement(requested_service, event_time, status);

UPDATE customer_requirement
SET service_days = 1,
    writer_count = 1,
    unit_price = 980.00,
    estimated_amount = 980.00,
    matching_preference = 'NEAREST_AVAILABLE'
WHERE requested_service IN ('ONSITE_WRITING', 'ONSITE_WRITING_AND_MEDIA_PR')
  AND unit_price IS NULL;

COMMIT;
