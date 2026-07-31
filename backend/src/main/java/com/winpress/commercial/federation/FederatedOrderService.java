package com.winpress.commercial.federation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** WinPress server-side acceptance point for signed GEO fulfillment orders. */
@Service
public class FederatedOrderService {
  private static final Set<String> SERVICE_TYPES = Set.of(
      "ONSITE_WRITING", "MEDIA_PR", "DIRECT_PUBLISHING", "NEWS_CONFERENCE");
  private static final Set<String> FORBIDDEN_FIELD_TOKENS = Set.of(
      "supplier", "cost", "margin", "internal_note", "upstream", "secret", "token", "api_key", "apikey");

  private final FederatedOrderRepository repository;
  private final FederationTokenService tokens;
  private final FederationSnapshotIntegrity integrity;
  private final ObjectMapper objectMapper;
  private final WinPressProperties properties;
  private final FederationSourceIdentity sourceIdentity;

  public FederatedOrderService(
      FederatedOrderRepository repository,
      FederationTokenService tokens,
      FederationSnapshotIntegrity integrity,
      ObjectMapper objectMapper,
      WinPressProperties properties,
      FederationSourceIdentity sourceIdentity
  ) {
    this.repository = repository;
    this.tokens = tokens;
    this.integrity = integrity;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.sourceIdentity = sourceIdentity;
  }

  @Transactional
  public Map<String, Object> accept(JsonNode body) {
    if (!tokens.isConfigured()) throw unavailable("联邦服务暂不可用");
    String assertion = required(body, "assertion");
    JsonNode claims = tokens.verifyGeoOrderAssertion(assertion);
    ObjectNode snapshot = requiredObject(body, "snapshot");
    assertNoInternalFields(snapshot);
    String snapshotHash = integrity.hash(snapshot);
    assertIdentity(claims, snapshot, snapshotHash);
    String orderId = required(snapshot, "order_id");
    Map<String, Object> existing = repository.findReceipt(orderId);
    if (!existing.isEmpty()) {
      if (!snapshotHash.equals(String.valueOf(existing.get("snapshotHash")))) {
        throw conflict("同一 GEO 订单不能使用不同快照重复投递");
      }
      return receiptResponse(existing, true);
    }
    String eventId = required(snapshot, "event_id");
    String assertionJti = required(claims, "jti");
    OffsetDateTime expiresAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(claims.path("exp").asLong()), ZoneOffset.UTC);
    if (!repository.insertInboxJti(eventId, assertionJti, expiresAt)) {
      Map<String, Object> afterReplay = repository.findReceipt(orderId);
      if (!afterReplay.isEmpty() && snapshotHash.equals(String.valueOf(afterReplay.get("snapshotHash")))) {
        return receiptResponse(afterReplay, true);
      }
      throw unauthorized("联邦请求已被使用或无法验证其幂等上下文");
    }
    String sourceInstanceId = sourceIdentity.current();
    FederatedOrderRepository.MaterializedOrder materialized;
    try {
      materialized = repository.materialize(snapshot, snapshotHash, sourceInstanceId);
    } catch (BusinessException error) {
      throw error;
    } catch (Exception error) {
      throw conflict("云发布无法创建履约对象：" + safeMessage(error));
    }
    ObjectNode accepted = eventClaims(snapshot, materialized, "ExternalOrderAccepted");
    repository.appendOutbox(
        "wp-external-order-accepted-" + orderId,
        orderId,
        materialized.tenantId(),
        "ExternalOrderAccepted",
        accepted,
        snapshotHash,
        "ExternalOrderAccepted:" + orderId
    );
    ObjectNode changed = eventClaims(snapshot, materialized, "ExternalOrderStatusChanged");
    repository.appendOutbox(
        "wp-external-order-status-accepted-" + orderId,
        orderId,
        materialized.tenantId(),
        "ExternalOrderStatusChanged",
        changed,
        snapshotHash,
        "ExternalOrderStatusChanged:" + orderId + ":" + materialized.status()
    );
    return receiptResponse(repository.findReceipt(orderId), false);
  }

  public Map<String, Object> status() {
    return Map.of(
        "enabled", properties.getFederation().isEnabled(),
        "configured", tokens.isConfigured(),
        "migrate_on_start", properties.getFederation().isMigrateOnStart(),
        "callback_configured", !blank(properties.getFederation().getGeoCallbackUrl())
    );
  }

  private Map<String, Object> receiptResponse(Map<String, Object> receipt, boolean idempotent) {
    if (receipt == null || receipt.isEmpty()) throw conflict("联邦订单受理回执缺失");
    ObjectNode claims = objectMapper.createObjectNode();
    claims.put("event_id", String.valueOf(receipt.get("platformEventId")));
    claims.put("order_id", String.valueOf(receipt.get("platformOrderId")));
    claims.put("tenant_id", String.valueOf(receipt.get("tenantId")));
    claims.put("organization_id", String.valueOf(receipt.get("platformOrganizationId")));
    claims.put("brand_id", String.valueOf(receipt.get("platformBrandId")));
    claims.put("project_id", String.valueOf(receipt.get("platformProjectId")));
    claims.put("service_type", String.valueOf(receipt.get("serviceType")));
    claims.put("snapshot_hash", String.valueOf(receipt.get("snapshotHash")));
    claims.put("source_instance_id", sourceIdentity.requireStored(receipt.get("sourceInstanceId")));
    claims.put("winpress_requirement_id", String.valueOf(receipt.get("requirementId")));
    claims.put("winpress_project_id", String.valueOf(receipt.get("projectId")));
    putOptionalId(claims, "winpress_editorial_task_id", receipt.get("editorialTaskId"));
    putOptionalId(claims, "winpress_publish_plan_id", receipt.get("publishPlanId"));
    claims.put("winpress_status", String.valueOf(receipt.get("status")));
    claims.put("next_action", String.valueOf(receipt.get("nextAction")));
    return Map.of(
        "receipt", tokens.issueGeoOrderReceipt(claims),
        "idempotent", idempotent,
        "order_id", String.valueOf(receipt.get("platformOrderId")),
        "winpress_requirement_id", String.valueOf(receipt.get("requirementId")),
        "winpress_project_id", String.valueOf(receipt.get("projectId"))
    );
  }

  private ObjectNode eventClaims(
      ObjectNode snapshot,
      FederatedOrderRepository.MaterializedOrder materialized,
      String eventType
  ) {
    ObjectNode event = objectMapper.createObjectNode();
    event.put("event_id", "wp-" + eventType.toLowerCase() + "-" + materialized.platformOrderId());
    event.put("event_type", eventType);
    event.put("order_id", materialized.platformOrderId());
    event.put("tenant_id", materialized.tenantId());
    event.put("organization_id", required(snapshot, "organization_id"));
    event.put("brand_id", required(snapshot, "brand_id"));
    event.put("project_id", required(snapshot, "project_id"));
    event.put("service_type", required(snapshot, "service_type"));
    event.put("snapshot_hash", integrity.hash(snapshot));
    event.put("source_instance_id", sourceIdentity.current());
    event.put("winpress_requirement_id", materialized.requirementId());
    event.put("winpress_project_id", materialized.projectId());
    if (materialized.editorialTaskId() != null) event.put("winpress_editorial_task_id", materialized.editorialTaskId());
    if (materialized.publishPlanId() != null) event.put("winpress_publish_plan_id", materialized.publishPlanId());
    event.put("winpress_status", materialized.status());
    event.put("next_action", materialized.nextAction());
    return event;
  }

  private void assertIdentity(JsonNode claims, ObjectNode snapshot, String snapshotHash) {
    for (String field : new String[]{"tenant_id", "organization_id", "brand_id", "project_id", "order_id", "service_type", "event_id"}) {
      if (!required(claims, field).equals(required(snapshot, field))) {
        throw unauthorized("联邦签名与快照身份不一致：" + field);
      }
    }
    if (!SERVICE_TYPES.contains(required(snapshot, "service_type"))) throw bad("联邦订单服务类型无效");
    if ("DIRECT_PUBLISHING".equals(required(snapshot, "service_type"))
        && required(snapshot, "authorization_reference").isBlank()) {
      throw bad("直编发布订单缺少费用授权编号");
    }
    if (!snapshotHash.equals(required(claims, "snapshot_hash"))) throw unauthorized("联邦快照哈希不匹配");
    if (!"1.0".equals(required(snapshot, "contract_version"))) throw bad("不支持的联邦订单契约版本");
    if (!"WinpressFederatedOrderRequested".equals(required(snapshot, "event_type"))) throw bad("联邦订单事件类型无效");
  }

  private ObjectNode requiredObject(JsonNode body, String field) {
    JsonNode node = body == null ? null : body.get(field);
    if (!(node instanceof ObjectNode object)) throw bad(field + " 为必填对象");
    return integrity.object(object);
  }

  private void assertNoInternalFields(JsonNode node) {
    if (node == null || node.isNull()) return;
    if (node.isObject()) {
      node.fields().forEachRemaining(entry -> {
        String normalized = entry.getKey().toLowerCase();
        if (FORBIDDEN_FIELD_TOKENS.stream().anyMatch(normalized::contains)) {
          throw bad("联邦订单不接受供应商、成本、凭据或毛利字段");
        }
        assertNoInternalFields(entry.getValue());
      });
    } else if (node.isArray()) {
      node.forEach(this::assertNoInternalFields);
    }
  }

  private String required(JsonNode node, String field) {
    String value = node == null ? "" : node.path(field).asText("").trim();
    if (value.isBlank()) throw bad(field + " 为必填项");
    return value;
  }

  private void putOptionalId(ObjectNode node, String field, Object value) {
    if (value != null) node.put(field, String.valueOf(value));
  }

  private String safeMessage(Exception error) {
    String text = error == null ? "unknown" : String.valueOf(error.getMessage());
    return text.length() <= 300 ? text : text.substring(0, 300);
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
  private BusinessException bad(String message) { return new BusinessException("FEDERATION_ORDER_INVALID", message, HttpStatus.BAD_REQUEST); }
  private BusinessException unauthorized(String message) { return new BusinessException("FEDERATION_ORDER_UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED); }
  private BusinessException conflict(String message) { return new BusinessException("FEDERATION_ORDER_CONFLICT", message, HttpStatus.CONFLICT); }
  private BusinessException unavailable(String message) { return new BusinessException("FEDERATION_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE); }
}
