BEGIN;

CREATE TEMP TABLE media_channel_import (
  channel_no VARCHAR(40), channel_name VARCHAR(180), channel_type VARCHAR(30), category VARCHAR(80),
  region VARCHAR(80), publish_form VARCHAR(120), expected_days INT, link_support BOOLEAN,
  public_notes TEXT, source_type VARCHAR(40), source_ref VARCHAR(120), last_verified_at TIMESTAMPTZ,
  status VARCHAR(30)
);

\copy media_channel_import FROM '/docker-entrypoint-initdb.d/media_channels.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8');

INSERT INTO publish_channel
  (channel_no, channel_name, channel_type, category, region, publish_form, expected_days,
   link_support, public_notes, source_type, source_ref, last_verified_at, status)
SELECT channel_no, replace(channel_name, '包收录', '收录参考'), channel_type,
       COALESCE(NULLIF(category,''),'综合'), COALESCE(NULLIF(region,''),'全国'),
       CASE
         WHEN publish_form IN ('["direct_editorial"]', 'direct_editorial') THEN '媒体直编'
         WHEN publish_form IS NULL OR publish_form = '' THEN '网站图文'
         ELSE publish_form
       END,
       expected_days, link_support,
       regexp_replace(
         regexp_replace(
           replace(COALESCE(NULLIF(public_notes,''),'栏目、链接形式和发布时间以媒体审核结果为准。'),
             '包收录', '收录参考'),
           '保证收录[^；。]*[；。]?', '收录情况以媒体实际反馈为准。', 'g'),
         '保证发布[^；。]*[；。]?', '发布安排以媒体审核结果为准。', 'g'),
       source_type, source_ref, last_verified_at, status
FROM media_channel_import
ON CONFLICT (channel_no) DO UPDATE SET
  channel_name=EXCLUDED.channel_name, category=EXCLUDED.category, region=EXCLUDED.region,
  publish_form=EXCLUDED.publish_form, expected_days=EXCLUDED.expected_days,
  public_notes=EXCLUDED.public_notes, last_verified_at=EXCLUDED.last_verified_at,
  status=EXCLUDED.status, updated_at=CURRENT_TIMESTAMP;

CREATE TEMP TABLE media_quote_import (
  channel_no VARCHAR(40),
  quote_no VARCHAR(40),
  cost_price NUMERIC(14,2),
  customer_price NUMERIC(14,2),
  currency VARCHAR(10),
  valid_from TIMESTAMPTZ,
  valid_until TIMESTAMPTZ,
  public_terms TEXT,
  status VARCHAR(30)
);

\copy media_quote_import FROM '/docker-entrypoint-initdb.d/media_quotes.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8');

INSERT INTO channel_quote
  (quote_no, channel_id, customer_tier, cost_price, customer_price, currency, valid_from, valid_until, public_terms, status)
SELECT q.quote_no, c.id, 'STANDARD', q.cost_price, q.customer_price, q.currency,
       q.valid_from, q.valid_until,
       COALESCE(NULLIF(q.public_terms,''),'客户服务价含媒体沟通与发布跟进，提交后复核稿件、栏目和排期。'),
       q.status
FROM media_quote_import q
JOIN publish_channel c ON c.channel_no=q.channel_no
ON CONFLICT (quote_no) DO NOTHING;

SELECT setval(pg_get_serial_sequence('publish_channel','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM publish_channel), 1));
SELECT setval(pg_get_serial_sequence('channel_quote','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM channel_quote), 1));

COMMIT;
