-- P1: keep a media-partnership application separate from customer service orders.
-- This is intentionally an enquiry workflow: submitting it never creates a supplier account,
-- grants portal access, or implies an approved commercial relationship.
DO $$
BEGIN
  IF to_regclass('public.business_inquiry') IS NOT NULL THEN
    ALTER TABLE business_inquiry
      DROP CONSTRAINT IF EXISTS business_inquiry_inquiry_type_check;
    ALTER TABLE business_inquiry
      ADD CONSTRAINT business_inquiry_inquiry_type_check
      CHECK (inquiry_type IN (
        'API_INTEGRATION', 'GENERAL_COOPERATION', 'SERVICE_CONSULTATION', 'MEDIA_PARTNERSHIP'
      ));
  END IF;
END $$;
