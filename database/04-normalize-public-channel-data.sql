BEGIN;

UPDATE publish_channel
SET publish_form = '媒体直编', updated_at = CURRENT_TIMESTAMP
WHERE publish_form IN ('["direct_editorial"]', 'direct_editorial');

UPDATE publish_channel
SET channel_name = replace(channel_name, '包收录', '收录参考'),
    public_notes = regexp_replace(
      regexp_replace(replace(public_notes, '包收录', '收录参考'),
        '保证收录[^；。]*[；。]?', '收录情况以媒体实际反馈为准。', 'g'),
      '保证发布[^；。]*[；。]?', '发布安排以媒体审核结果为准。', 'g'),
    updated_at = CURRENT_TIMESTAMP
WHERE channel_name LIKE '%包收录%'
   OR public_notes LIKE '%包收录%'
   OR public_notes LIKE '%保证收录%'
   OR public_notes LIKE '%保证发布%';

INSERT INTO publish_offering (offering_no, channel_id, offering_name, status)
SELECT 'OFF-IMPORT-' || c.id, c.id, c.channel_name,
       CASE WHEN c.status IN ('ACTIVE','REVIEW_REQUIRED','INACTIVE') THEN c.status ELSE 'REVIEW_REQUIRED' END
FROM publish_channel c
WHERE c.channel_type='DIRECT_PUBLISHING'
ON CONFLICT (channel_id) DO UPDATE
SET offering_name=EXCLUDED.offering_name,
    status=EXCLUDED.status,
    updated_at=CURRENT_TIMESTAMP;

COMMIT;
