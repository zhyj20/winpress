package com.winpress.commercial.federation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winpress.commercial.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

/**
 * The only WinPress component allowed to materialize a signed GEO order into WinPress business
 * objects.  GEO never accesses these tables directly.
 */
@Repository
public class FederatedOrderRepository {
  private static final String SCHEMA = "winpress_federation";
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public FederatedOrderRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> findReceipt(String platformOrderId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT platform_order_id AS "platformOrderId", tenant_id AS "tenantId",
               source_instance_id AS "sourceInstanceId", platform_event_id AS "platformEventId",
               platform_organization_id AS "platformOrganizationId", platform_brand_id AS "platformBrandId",
               platform_project_id AS "platformProjectId", service_type AS "serviceType",
               snapshot_hash AS "snapshotHash", winpress_organization_id AS "organizationId",
               winpress_customer_id AS "customerId", winpress_requirement_id AS "requirementId",
               winpress_project_id AS "projectId", winpress_editorial_task_id AS "editorialTaskId",
               winpress_publish_plan_id AS "publishPlanId", winpress_status AS "status",
               next_action AS "nextAction", created_at AS "createdAt", updated_at AS "updatedAt"
          FROM winpress_federation.federated_order_receipt
         WHERE platform_order_id=?
         LIMIT 1
        """, platformOrderId);
    return rows.isEmpty() ? Map.of() : rows.get(0);
  }

  public boolean insertInboxJti(String eventId, String jti, OffsetDateTime expiresAt) {
    return jdbc.update("""
        INSERT INTO winpress_federation.federation_inbox_jti
        (direction, assertion_jti, event_id, expires_at)
        VALUES ('geo_to_winpress_order', ?, ?, ?)
        ON CONFLICT (direction, assertion_jti) DO NOTHING
        """, jti, eventId, expiresAt) == 1;
  }

  public MaterializedOrder materialize(ObjectNode snapshot, String snapshotHash, String sourceInstanceId) {
    String tenantId = required(snapshot, "tenant_id");
    String organizationId = required(snapshot, "organization_id");
    String orderId = required(snapshot, "order_id");
    String serviceType = required(snapshot, "service_type");
    long localOrganizationId = ensureOrganization(tenantId, sourceInstanceId, organizationId,
        first(snapshot, "organization_name", "brand_name", "project_name"));
    // WinPress users belong to exactly one organisation.  A GEO identity may legitimately
    // participate in multiple organisations within a tenant, so the inbound identity map
    // must be scoped by the *platform* organisation before it becomes a WinPress customer.
    String organizationScopedCustomerId = organizationId + ":" + required(snapshot, "confirmed_by");
    long localCustomerId = ensureCustomer(tenantId, sourceInstanceId, organizationScopedCustomerId, localOrganizationId,
        first(snapshot, "organization_name", "brand_name"));
    long requirementId = createRequirement(snapshot, localOrganizationId, localCustomerId);
    long projectId = createProject(snapshot, requirementId, localOrganizationId, localCustomerId);
    Long editorialTaskId = null;
    Long publishPlanId = null;
    String status;
    String nextAction;
    switch (serviceType) {
      case "ONSITE_WRITING" -> {
        editorialTaskId = createOnsiteWriting(snapshot, projectId, requirementId);
        status = "PENDING_ASSIGNMENT";
        nextAction = "云采写任务已创建，云发布将进行写手匹配与指派。";
      }
      case "DIRECT_PUBLISHING" -> {
        publishPlanId = createDirectPublishing(snapshot, projectId, localCustomerId);
        status = "PENDING_ASSIGNMENT";
        nextAction = "直编发稿计划已确认，云发布将进入渠道履约与结果回执。";
      }
      case "MEDIA_PR" -> {
        status = "PENDING_MEDIA_SCOPE";
        nextAction = "媒体邀请需求已创建，等待项目负责人核对活动信息和拟邀范围。";
      }
      case "NEWS_CONFERENCE" -> {
        createConference(snapshot, projectId);
        status = "PENDING_SCOPE";
        nextAction = "新闻发布会项目已创建，云发布将按九项履约清单推进。";
      }
      default -> throw bad("FEDERATION_SERVICE_TYPE_INVALID", "联邦订单服务类型无效");
    }
    insertReceipt(snapshot, snapshotHash, sourceInstanceId, localOrganizationId, localCustomerId,
        requirementId, projectId, editorialTaskId, publishPlanId, status, nextAction);
    return new MaterializedOrder(orderId, tenantId, localOrganizationId, localCustomerId, requirementId,
        projectId, editorialTaskId, publishPlanId, status, nextAction);
  }

  public void appendOutbox(
      String eventId, String platformOrderId, String tenantId, String eventType,
      JsonNode payload, String snapshotHash, String idempotencyKey
  ) {
    jdbc.update("""
        INSERT INTO winpress_federation.federation_event_outbox
        (event_id, platform_order_id, tenant_id, event_type, payload, snapshot_hash, idempotency_key)
        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
        ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
        """, eventId, platformOrderId, tenantId, eventType, json(payload), blankToNull(snapshotHash), idempotencyKey);
  }

  public List<Map<String, Object>> leaseOutbox(String workerId, int limit, int leaseSeconds) {
    return jdbc.queryForList("""
        WITH candidate AS (
          SELECT id FROM winpress_federation.federation_event_outbox
           WHERE attempt_count < max_attempts
             AND next_attempt_at <= CURRENT_TIMESTAMP
             AND (status IN ('pending','retry_wait') OR (status='processing' AND locked_until<=CURRENT_TIMESTAMP))
           ORDER BY created_at
           FOR UPDATE SKIP LOCKED
           LIMIT ?
        )
        UPDATE winpress_federation.federation_event_outbox e
           SET status='processing', locked_by=?, locked_until=CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
               attempt_count=e.attempt_count+1, last_error_code=NULL, last_error_message=NULL, updated_at=CURRENT_TIMESTAMP
          FROM candidate
         WHERE e.id=candidate.id
        RETURNING e.id, e.event_id AS "eventId", e.platform_order_id AS "platformOrderId",
                  e.tenant_id AS "tenantId", e.event_type AS "eventType", e.payload::text AS payload,
                  e.snapshot_hash AS "snapshotHash", e.status, e.attempt_count AS "attemptCount",
                  e.max_attempts AS "maxAttempts", e.locked_by AS "lockedBy"
        """, limit, workerId, leaseSeconds);
  }

  public boolean markOutboxPublished(long id, String workerId) {
    return jdbc.update("""
        UPDATE winpress_federation.federation_event_outbox
           SET status='published', published_at=CURRENT_TIMESTAMP, locked_until=NULL, updated_at=CURRENT_TIMESTAMP
         WHERE id=? AND status='processing' AND locked_by=?
        """, id, workerId) == 1;
  }

  public boolean failOutbox(long id, String workerId, int retrySeconds, String code, String message) {
    return jdbc.update("""
        UPDATE winpress_federation.federation_event_outbox
           SET status=CASE WHEN attempt_count>=max_attempts THEN 'dead_letter' ELSE 'retry_wait' END,
               next_attempt_at=CASE WHEN attempt_count>=max_attempts THEN next_attempt_at
                    ELSE CURRENT_TIMESTAMP + (? * INTERVAL '1 second') END,
               locked_until=NULL, last_error_code=?, last_error_message=?, updated_at=CURRENT_TIMESTAMP
         WHERE id=? AND status='processing' AND locked_by=?
        """, retrySeconds, code, message, id, workerId) == 1;
  }

  public void recordCallbackDelivery(String eventId, String callbackJti, int responseCode) {
    jdbc.update("""
        INSERT INTO winpress_federation.federation_callback_delivery_receipt
        (event_id, callback_jti, response_code)
        VALUES (?, ?, ?)
        ON CONFLICT (event_id) DO NOTHING
        """, eventId, callbackJti, responseCode);
  }

  /** Safe operational projection for the callback reconciler; it deliberately excludes supplier and cost tables. */
  public List<Map<String, Object>> reconciliationReceipts(int limit) {
    return jdbc.queryForList("""
        SELECT platform_order_id AS "platformOrderId", tenant_id AS "tenantId",
               source_instance_id AS "sourceInstanceId", platform_event_id AS "platformEventId",
               platform_organization_id AS "platformOrganizationId",
               platform_brand_id AS "platformBrandId", platform_project_id AS "platformProjectId",
               service_type AS "serviceType", snapshot_hash AS "snapshotHash",
               winpress_requirement_id AS "requirementId", winpress_project_id AS "projectId",
               winpress_editorial_task_id AS "editorialTaskId", winpress_publish_plan_id AS "publishPlanId",
               winpress_status AS "status", next_action AS "nextAction"
          FROM winpress_federation.federated_order_receipt
         ORDER BY updated_at ASC
         LIMIT ?
        """, Math.max(1, Math.min(limit, 200)));
  }

  public String editorialTaskStatus(long taskId) {
    List<String> rows = jdbc.query("SELECT status FROM editorial_task WHERE id=?", (rs, rowNum) -> rs.getString(1), taskId);
    return rows.isEmpty() ? "" : rows.get(0);
  }

  public String projectStatus(long projectId) {
    List<String> rows = jdbc.query("SELECT status FROM project WHERE id=?", (rs, rowNum) -> rs.getString(1), projectId);
    return rows.isEmpty() ? "" : rows.get(0);
  }

  /**
   * A federated media-PR requirement is not an invitation.  Only an actual
   * publish task with a persisted invitation lets fulfillment move beyond the
   * scope-confirmation stage.
   */
  public int mediaInvitationCount(long projectId) {
    Integer count = jdbc.queryForObject("""
        SELECT COUNT(*)::int
          FROM media_pr_invitation invitation
          JOIN publish_task task ON task.id=invitation.publish_task_id
         WHERE task.project_id=? AND task.channel_type='MEDIA_PR'
        """, Integer.class, projectId);
    return count == null ? 0 : count;
  }

  public String conferenceStatus(long projectId) {
    List<String> rows = jdbc.query("SELECT status FROM conference_project WHERE project_id=? ORDER BY id DESC LIMIT 1", (rs, rowNum) -> rs.getString(1), projectId);
    return rows.isEmpty() ? "" : rows.get(0);
  }

  public Map<String, Integer> directTaskCounts(long projectId) {
    return jdbc.queryForObject("""
        SELECT COUNT(*)::int AS "total",
               COUNT(*) FILTER (WHERE status IN ('COMPLETED','CLIENT_ACCEPTED'))::int AS "completed",
               COUNT(*) FILTER (WHERE status IN ('EXCEPTION','CANCELLED'))::int AS "exception"
          FROM publish_task
         WHERE project_id=? AND channel_type='DIRECT_PUBLISHING'
        """, (rs, rowNum) -> Map.of(
            "total", rs.getInt("total"), "completed", rs.getInt("completed"), "exception", rs.getInt("exception")
        ), projectId);
  }

  public List<Map<String, Object>> verifiedResultUrls(long projectId) {
    return jdbc.queryForList("""
        SELECT url, title, published_at AS "publishedAt"
          FROM result_link
         WHERE project_id=? AND status='VERIFIED' AND url IS NOT NULL AND url<>''
         ORDER BY published_at NULLS LAST, id
         LIMIT 100
        """, projectId);
  }

  public void updateReceiptStatus(String platformOrderId, String status, String nextAction) {
    jdbc.update("""
        UPDATE winpress_federation.federated_order_receipt
           SET winpress_status=?, next_action=?, updated_at=CURRENT_TIMESTAMP
         WHERE platform_order_id=?
        """, status, nextAction, platformOrderId);
  }

  private long ensureOrganization(String tenantId, String sourceInstanceId, String platformId, String name) {
    Long mapped = mappedId(tenantId, sourceInstanceId, "organization", platformId);
    if (mapped != null) return mapped;
    long organizationId = jdbc.queryForObject("""
        INSERT INTO organization (organization_no, name, organization_type, status)
        VALUES (?, ?, 'CUSTOMER', 'ACTIVE') RETURNING id
        """, Long.class, no("GEOORG"), defaultText(name, "GEO 联邦客户"));
    insertMapping(tenantId, sourceInstanceId, "organization", platformId, organizationId);
    return organizationId;
  }

  private long ensureCustomer(
      String tenantId, String sourceInstanceId, String platformId, long organizationId, String displayName
  ) {
    Long mapped = mappedId(tenantId, sourceInstanceId, "user", platformId);
    if (mapped != null) return mapped;
    String suffix = UUID.nameUUIDFromBytes((tenantId + ":" + platformId).getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .toString().replace("-", "").substring(0, 20);
    String username = "geo-" + suffix;
    List<Map<String, Object>> existing = jdbc.queryForList("""
        SELECT id, organization_id AS "organizationId"
          FROM app_user
         WHERE username=?
         LIMIT 1
        """, username);
    long userId;
    if (!existing.isEmpty()) {
      // A prior, otherwise rolled-back materialisation can leave this deterministic
      // technical identity behind in a legacy deployment.  Reuse it only when it
      // belongs to the same materialized organisation; never cross-link tenants.
      Map<String, Object> row = existing.get(0);
      long existingOrganizationId = number(row.get("organizationId"));
      if (existingOrganizationId != organizationId) {
        throw bad("FEDERATION_IDENTITY_CONFLICT", "云发布联邦客户身份映射冲突");
      }
      userId = number(row.get("id"));
    } else {
      userId = jdbc.queryForObject("""
          INSERT INTO app_user
          (user_no, organization_id, username, password_hash, display_name, mobile, email, status)
          VALUES (?, ?, ?, ?, ?, '00000000000', ?, 'ACTIVE') RETURNING id
          """, Long.class,
          no("GEOUSR"), organizationId, username,
          encoder.encode(UUID.randomUUID().toString()), defaultText(displayName, "GEO 联邦客户"),
          username + "@invalid.local");
    }
    insertMapping(tenantId, sourceInstanceId, "user", platformId, userId);
    return userId;
  }

  private long createRequirement(ObjectNode snapshot, long organizationId, long customerId) {
    ObjectNode details = object(snapshot, "service_details");
    String serviceType = required(snapshot, "service_type");
    int serviceDays = "ONSITE_WRITING".equals(serviceType) ? details.path("service_days").asInt(1) : 1;
    int writerCount = "ONSITE_WRITING".equals(serviceType) ? details.path("writer_count").asInt(1) : 1;
    BigDecimal amount = "ONSITE_WRITING".equals(serviceType) ? onsitePrice().multiply(BigDecimal.valueOf(serviceDays)).multiply(BigDecimal.valueOf(writerCount)) : null;
    return jdbc.queryForObject("""
        INSERT INTO customer_requirement
        (requirement_no, customer_id, organization_id, title, event_time, event_location, facts,
         objective, target_audience, requested_service, service_days, writer_count, unit_price,
         estimated_amount, onsite_contact_name, onsite_contact_mobile, deliverable_requirement,
         matching_preference, due_at, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED') RETURNING id
        """, Long.class,
        no("REQ"), customerId, organizationId, required(snapshot, "title"),
        timestamp(details.path("event_time").asText("")), blankToNull(details.path("event_location").asText("")),
        blankToNull(snapshot.path("facts").asText("")), blankToNull(snapshot.path("objective").asText("")),
        blankToNull(snapshot.path("target_audience").asText("")), serviceType, serviceDays, writerCount,
        "ONSITE_WRITING".equals(serviceType) ? onsitePrice() : null, amount,
        blankToNull(details.path("contact_name").asText("")), blankToNull(details.path("contact_mobile").asText("")),
        blankToNull(details.path("deliverable_requirement").asText("")),
        "ONSITE_WRITING".equals(serviceType) ? "NEAREST_AVAILABLE" : "EXPERIENCE_FIRST",
        timestamp(snapshot.path("due_at").asText("")));
  }

  private long createProject(ObjectNode snapshot, long requirementId, long organizationId, long customerId) {
    return jdbc.queryForObject("""
        INSERT INTO project
        (project_no, requirement_id, organization_id, customer_id, project_name, planned_start_at, planned_end_at, status)
        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, 'PLANNING') RETURNING id
        """, Long.class, no("PRJ"), requirementId, organizationId, customerId,
        required(snapshot, "title"), timestamp(snapshot.path("due_at").asText("")));
  }

  private long createOnsiteWriting(ObjectNode snapshot, long projectId, long requirementId) {
    ObjectNode details = object(snapshot, "service_details");
    long taskId = jdbc.queryForObject("""
        INSERT INTO editorial_task (task_no, project_id, requirement_id, due_at, writing_brief, status)
        VALUES (?, ?, ?, ?, ?, 'PENDING_ASSIGNMENT') RETURNING id
        """, Long.class, no("EDT"), projectId, requirementId,
        timestamp(snapshot.path("due_at").asText("")), bounded(snapshot.path("objective").asText(""), 4000));
    int serviceDays = details.path("service_days").asInt(1);
    int writerCount = details.path("writer_count").asInt(1);
    BigDecimal price = onsitePrice();
    jdbc.update("""
        INSERT INTO writing_assignment
        (assignment_no, editorial_task_id, matching_mode, service_location, service_days, writer_count,
         unit_price_snapshot, estimated_amount_snapshot, status)
        VALUES (?, ?, 'NEAREST_AVAILABLE', ?, ?, ?, ?, ?, 'WAITING_MATCH')
        """, no("WAS"), taskId, details.path("event_location").asText(""), serviceDays, writerCount,
        price, price.multiply(BigDecimal.valueOf(serviceDays)).multiply(BigDecimal.valueOf(writerCount)));
    return taskId;
  }

  private long createDirectPublishing(ObjectNode snapshot, long projectId, long customerId) {
    ObjectNode packageRef = object(object(snapshot, "service_details"), "delivery_package");
    ObjectNode immutable = object(packageRef, "immutable_snapshot");
    ObjectNode approved = object(immutable, "approved_content");
    String title = defaultText(approved.path("title").asText(""), required(snapshot, "title"));
    String content = required(approved, "content");
    long manuscriptId = jdbc.queryForObject("""
        INSERT INTO manuscript (manuscript_no, project_id, title, current_version_no, status)
        VALUES (?, ?, ?, 1, 'DRAFT') RETURNING id
        """, Long.class, no("MAN"), projectId, title);
    long versionId = jdbc.queryForObject("""
        INSERT INTO manuscript_version
        (version_no, manuscript_id, version_number, title, summary, content, change_note, submitted_by, reviewed_by, reviewed_at, status)
        VALUES (?, ?, 1, ?, ?, ?, 'Imported from GEO immutable delivery package', ?, ?, CURRENT_TIMESTAMP, 'APPROVED') RETURNING id
        """, Long.class, no("MANV"), manuscriptId, title, blankToNull(approved.path("summary").asText("")), content, customerId, customerId);
    jdbc.update("UPDATE manuscript SET approved_version_id=?, status='APPROVED', updated_at=CURRENT_TIMESTAMP WHERE id=?", versionId, manuscriptId);
    long planId = jdbc.queryForObject("""
        INSERT INTO publish_plan
        (plan_no, project_id, manuscript_id, manuscript_version_id, plan_name, objective, estimated_amount,
         currency, created_by, confirmed_by, confirmed_at, status)
        VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, CURRENT_TIMESTAMP, 'CONFIRMED') RETURNING id
        """, Long.class, no("PLAN"), projectId, manuscriptId, versionId, title + "传播计划",
        blankToNull(snapshot.path("objective").asText("")), defaultText(snapshot.path("currency").asText(""), "CNY"), customerId, customerId);
    JsonNode selections = object(snapshot, "service_details").path("channel_selections");
    BigDecimal total = BigDecimal.ZERO;
    for (JsonNode selection : selections) {
      long channelId = Long.parseLong(required(selection, "channel_id"));
      Map<String, Object> quote = activeDirectQuote(channelId);
      long itemId = jdbc.queryForObject("""
          INSERT INTO publish_plan_item
          (item_no, publish_plan_id, channel_id, channel_type, planned_publish_at, note,
           quote_id, unit_price_snapshot, price_valid_until, status)
          VALUES (?, ?, ?, 'DIRECT_PUBLISHING', ?, ?, ?, ?, ?, 'TASK_CREATED') RETURNING id
          """, Long.class, no("PLANITM"), planId, channelId,
          timestamp(selection.path("planned_publish_at").asText("")), blankToNull(selection.path("note").asText("")),
          number(quote.get("quoteId")), decimal(quote.get("customerPrice")), timestamp(String.valueOf(quote.get("validUntil"))));
      jdbc.update("""
          INSERT INTO publish_task
          (task_no, publish_plan_item_id, project_id, manuscript_id, manuscript_version_id, channel_id,
           channel_type, planned_publish_at, status)
          VALUES (?, ?, ?, ?, ?, ?, 'DIRECT_PUBLISHING', ?, 'PENDING_ASSIGNMENT')
          """, no("PUB"), itemId, projectId, manuscriptId, versionId, channelId,
          timestamp(selection.path("planned_publish_at").asText("")));
      total = total.add(decimal(quote.get("customerPrice")));
    }
    jdbc.update("UPDATE publish_plan SET estimated_amount=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", total, planId);
    return planId;
  }

  private void createConference(ObjectNode snapshot, long projectId) {
    ObjectNode details = object(snapshot, "service_details");
    long conferenceId = jdbc.queryForObject("""
        INSERT INTO conference_project
        (conference_no, project_id, conference_type, conference_format, theme, event_time,
         event_location, communication_goal, contact_name, contact_mobile, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_SCOPE') RETURNING id
        """, Long.class, no("CNF"), projectId, blankToNull(details.path("conference_type").asText("")),
        blankToNull(details.path("conference_format").asText("")), required(snapshot, "title"),
        timestamp(details.path("event_time").asText("")), blankToNull(details.path("event_location").asText("")),
        blankToNull(snapshot.path("objective").asText("")), required(details, "contact_name"), required(details, "contact_mobile"));
    String[][] work = {
        {"PRE_EVENT", "确认发布目标与项目范围"}, {"PRE_EVENT", "确定议程、嘉宾与发言分工"},
        {"PRE_EVENT", "落实场地、舞台与现场动线"}, {"PRE_EVENT", "准备新闻材料与问答口径"},
        {"PRE_EVENT", "建立拟邀媒体清单"}, {"PRE_EVENT", "执行媒体邀请与到场确认"},
        {"ONSITE", "统筹现场接待、采访与采写"}, {"POST_EVENT", "安排会后发稿与渠道发布"},
        {"POST_EVENT", "核验成果并完成项目复盘"}
    };
    for (int index = 0; index < work.length; index += 1) {
      jdbc.update("""
          INSERT INTO conference_work_item
          (item_no, conference_project_id, sort_order, phase, title, status)
          VALUES (?, ?, ?, ?, ?, 'PENDING')
          """, no("CNFITM"), conferenceId, index + 1, work[index][0], work[index][1]);
    }
  }

  private void insertReceipt(
      ObjectNode snapshot, String snapshotHash, String sourceInstanceId, long organizationId, long customerId,
      long requirementId, long projectId, Long editorialTaskId, Long publishPlanId, String status, String nextAction
  ) {
    jdbc.update("""
        INSERT INTO winpress_federation.federated_order_receipt
        (platform_order_id, tenant_id, source_instance_id, platform_event_id, platform_organization_id,
         platform_brand_id, platform_project_id, service_type, snapshot_hash, winpress_organization_id,
         winpress_customer_id, winpress_requirement_id, winpress_project_id, winpress_editorial_task_id,
         winpress_publish_plan_id, winpress_status, next_action)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, required(snapshot, "order_id"), required(snapshot, "tenant_id"), sourceInstanceId,
        required(snapshot, "event_id"), required(snapshot, "organization_id"), required(snapshot, "brand_id"),
        required(snapshot, "project_id"), required(snapshot, "service_type"), snapshotHash, organizationId,
        customerId, requirementId, projectId, editorialTaskId, publishPlanId, status, nextAction);
  }

  private Map<String, Object> activeDirectQuote(long channelId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT c.id AS "channelId", c.channel_name AS "channelName", q.id AS "quoteId", q.customer_price AS "customerPrice",
               q.currency, q.valid_until AS "validUntil"
          FROM publish_channel c
          JOIN channel_quote q ON q.channel_id=c.id
         WHERE c.id=? AND c.channel_type='DIRECT_PUBLISHING' AND c.status='ACTIVE'
           AND q.status='ACTIVE' AND q.valid_from<=CURRENT_TIMESTAMP AND q.valid_until>CURRENT_TIMESTAMP
         ORDER BY q.valid_until ASC, q.id DESC
         LIMIT 1
        """, channelId);
    if (rows.isEmpty()) throw bad("FEDERATION_QUOTE_UNAVAILABLE", "所选直编渠道没有当前有效的客户报价");
    return rows.get(0);
  }

  /** Customer-facing direct-publishing quote only.  Supplier and cost columns are never selected. */
  public Map<String, Object> currentDirectCustomerQuote(long channelId) {
    return activeDirectQuote(channelId);
  }

  /**
   * Lists only channels that are currently selectable and have a current customer quote.  This is
   * intentionally a catalogue projection: it never selects supplier, upstream-order, cost or
   * margin fields, so it is safe to return through the GEO customer BFF.
   */
  public List<Map<String, Object>> currentDirectCustomerOffers(int limit) {
    int boundedLimit = Math.max(1, Math.min(limit, 100));
    return jdbc.queryForList("""
        SELECT DISTINCT ON (c.id)
               c.id AS "channelId", c.channel_name AS "channelName",
               q.customer_price AS "customerPrice", q.currency, q.valid_until AS "validUntil"
          FROM publish_channel c
          JOIN channel_quote q ON q.channel_id=c.id
         WHERE c.channel_type='DIRECT_PUBLISHING' AND c.status='ACTIVE'
           AND q.status='ACTIVE' AND q.valid_from<=CURRENT_TIMESTAMP AND q.valid_until>CURRENT_TIMESTAMP
         ORDER BY c.id, q.valid_until ASC, q.id DESC
         LIMIT ?
        """, boundedLimit);
  }

  /** Customer-facing onsite-writing price only.  The effective end is used by the quote service. */
  public Map<String, Object> currentOnsiteCustomerPrice() {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT list_price AS "customerPrice", currency, effective_until AS "effectiveUntil"
          FROM service_price_book
         WHERE service_code='ONSITE_WRITING' AND status='ACTIVE'
           AND effective_from<=CURRENT_TIMESTAMP
           AND (effective_until IS NULL OR effective_until>CURRENT_TIMESTAMP)
         ORDER BY version_no DESC, id DESC LIMIT 1
        """);
    if (rows.isEmpty()) throw bad("FEDERATION_PRICE_UNAVAILABLE", "云采写尚无当前有效的服务价目，不能创建履约任务");
    return rows.get(0);
  }

  private BigDecimal onsitePrice() {
    List<BigDecimal> rows = jdbc.query("""
        SELECT list_price FROM service_price_book
         WHERE service_code='ONSITE_WRITING' AND status='ACTIVE'
           AND effective_from<=CURRENT_TIMESTAMP
           AND (effective_until IS NULL OR effective_until>CURRENT_TIMESTAMP)
         ORDER BY version_no DESC, id DESC LIMIT 1
        """, (rs, rowNum) -> rs.getBigDecimal(1));
    if (rows.isEmpty() || rows.get(0) == null || rows.get(0).signum() < 0) {
      throw bad("FEDERATION_PRICE_UNAVAILABLE", "云采写尚无当前有效的服务价目，不能创建履约任务");
    }
    return rows.get(0);
  }

  private Long mappedId(String tenantId, String sourceInstanceId, String type, String platformId) {
    List<Long> rows = jdbc.query("""
        SELECT winpress_id FROM winpress_federation.federated_identity_map
         WHERE tenant_id=? AND source_instance_id=? AND object_type=? AND platform_id=? LIMIT 1
        """, (rs, rowNum) -> rs.getLong(1), tenantId, sourceInstanceId, type, platformId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private void insertMapping(String tenantId, String sourceInstanceId, String type, String platformId, long winpressId) {
    try {
      jdbc.update("""
          INSERT INTO winpress_federation.federated_identity_map
          (tenant_id, source_instance_id, object_type, platform_id, winpress_id)
          VALUES (?, ?, ?, ?, ?)
          ON CONFLICT (tenant_id, source_instance_id, object_type, platform_id) DO NOTHING
          """, tenantId, sourceInstanceId, type, platformId, winpressId);
    } catch (DuplicateKeyException ignored) {
      // A concurrent signed request can reuse the same identity only; receipt idempotency still guards materialization.
    }
  }

  private ObjectNode object(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (!(value instanceof ObjectNode object)) throw bad("FEDERATION_SNAPSHOT_INVALID", field + " 缺失或格式错误");
    return object;
  }

  private String required(JsonNode node, String field) {
    String value = node == null ? "" : node.path(field).asText("").trim();
    if (value.isBlank()) throw bad("FEDERATION_SNAPSHOT_INVALID", field + " 为必填项");
    return value;
  }

  private String first(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private OffsetDateTime timestamp(String value) {
    if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) return null;
    String normalized = value.trim();
    try {
      return OffsetDateTime.parse(normalized);
    } catch (Exception ignored) {
      // PostgreSQL's default text representation uses a space between date and time.
      // Quotes and order snapshots are persisted independently, so normalize it here
      // rather than rejecting a valid customer quote during downstream materialization.
      String isoLike = normalized.replace(' ', 'T');
      try {
        return OffsetDateTime.parse(isoLike);
      } catch (Exception ignoredAgain) {
        try {
          // Legacy rows without an explicit offset are normalised to UTC.  New GEO
          // snapshots continue to transmit offset-aware ISO-8601 timestamps.
          return LocalDateTime.parse(isoLike).atOffset(ZoneOffset.UTC);
        } catch (Exception error) {
          throw bad("FEDERATION_SNAPSHOT_INVALID", "时间格式错误");
        }
      }
    }
  }

  private long number(Object value) {
    if (value instanceof Number number) return number.longValue();
    return Long.parseLong(String.valueOf(value));
  }

  private BigDecimal decimal(Object value) {
    if (value instanceof BigDecimal decimal) return decimal;
    return new BigDecimal(String.valueOf(value));
  }

  private String json(Object value) {
    try { return objectMapper.writeValueAsString(value); }
    catch (JsonProcessingException error) { throw new IllegalStateException("cannot serialize federation event", error); }
  }

  private String defaultText(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
  private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private String bounded(String value, int max) { String safe = value == null ? "" : value.trim(); return safe.length() <= max ? safe : safe.substring(0, max); }
  private String no(String prefix) { return prefix + "-" + java.time.LocalDate.now().toString().replace("-", "") + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(); }
  private BusinessException bad(String code, String message) { return new BusinessException(code, message, HttpStatus.CONFLICT); }

  public record MaterializedOrder(
      String platformOrderId, String tenantId, long organizationId, long customerId, long requirementId,
      long projectId, Long editorialTaskId, Long publishPlanId, String status, String nextAction
  ) {}
}
