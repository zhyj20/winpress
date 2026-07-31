package com.winpress.commercial.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Converts mature WinPress fulfillment facts into signed GEO callbacks.  The reconciler reads
 * only customer-safe operational statuses and verified public URLs; no supplier/cost tables are
 * queried or included in the callback payload.
 */
@Component
public class FederationFulfillmentReconciler {
  private static final Logger log = LoggerFactory.getLogger(FederationFulfillmentReconciler.class);
  private final FederatedOrderRepository repository;
  private final FederationSnapshotIntegrity integrity;
  private final ObjectMapper objectMapper;
  private final FederationTokenService tokens;
  private final FederationSourceIdentity sourceIdentity;

  public FederationFulfillmentReconciler(
      FederatedOrderRepository repository,
      FederationSnapshotIntegrity integrity,
      ObjectMapper objectMapper,
      FederationTokenService tokens,
      FederationSourceIdentity sourceIdentity
  ) {
    this.repository = repository;
    this.integrity = integrity;
    this.objectMapper = objectMapper;
    this.tokens = tokens;
    this.sourceIdentity = sourceIdentity;
  }

  @Scheduled(fixedDelayString = "${winpress.federation.reconcile-delay-ms:15000}")
  public void reconcile() {
    if (!tokens.isConfigured()) return;
    for (Map<String, Object> receipt : repository.reconciliationReceipts(100)) {
      try { reconcileOne(receipt); }
      catch (Exception ignored) {
        // Do not expose downstream internals in logs.  The next scheduled pass is idempotent.
        log.warn("federated fulfillment reconciliation deferred for order {}", value(receipt, "platformOrderId"));
      }
    }
  }

  private void reconcileOne(Map<String, Object> receipt) {
    String serviceType = value(receipt, "serviceType");
    long projectId = number(receipt.get("projectId"));
    switch (serviceType) {
      case "ONSITE_WRITING" -> reconcileOnsite(receipt);
      case "DIRECT_PUBLISHING" -> reconcileDirect(receipt, projectId);
      case "MEDIA_PR" -> reconcileMediaPr(receipt, projectId);
      case "NEWS_CONFERENCE" -> reconcileConference(receipt, projectId);
      default -> emitException(receipt, "UNSUPPORTED_SERVICE_TYPE", "云发布订单类型无法继续履约。");
    }
  }

  private void reconcileOnsite(Map<String, Object> receipt) {
    String status = repository.editorialTaskStatus(number(receipt.get("editorialTaskId")));
    if (status.isBlank()) { emitException(receipt, "EDITORIAL_TASK_MISSING", "云采写履约对象不存在，需要运营处理。"); return; }
    if ("COMPLETED".equals(status)) {
      emit(receipt, "CloudInterviewEditorialCompleted", "COMPLETED",
          "云采写已完成，GEO 可进入交付核验与后续治理。", null);
      return;
    }
    emitStatusIfChanged(receipt, status, "云采写正在由云发布履约团队处理。");
  }

  private void reconcileDirect(Map<String, Object> receipt, long projectId) {
    Map<String, Integer> counts = repository.directTaskCounts(projectId);
    int total = counts.getOrDefault("total", 0);
    int completed = counts.getOrDefault("completed", 0);
    int exceptions = counts.getOrDefault("exception", 0);
    if (total == 0) { emitException(receipt, "PUBLISH_TASK_MISSING", "直编发布履约任务不存在，需要运营处理。"); return; }
    if (exceptions > 0) { emitException(receipt, "PUBLISH_TASK_EXCEPTION", "云发布存在异常履约任务，需要运营处理。"); return; }
    if (completed < total) {
      emitStatusIfChanged(receipt, "EXECUTING", "云发布正在执行渠道发布与结果回执。");
      return;
    }
    List<Map<String, Object>> urls = repository.verifiedResultUrls(projectId);
    if (urls.size() < total) {
      emitStatusIfChanged(receipt, "COMPLETED_PENDING_EVIDENCE", "云发布已完成渠道执行，等待可核验的结果链接。 ");
      return;
    }
    ObjectNode extra = objectMapper.createObjectNode();
    ArrayNode resultUrls = extra.putArray("result_urls");
    for (Map<String, Object> row : urls) {
      ObjectNode item = resultUrls.addObject();
      item.put("url", value(row, "url"));
      String title = value(row, "title");
      if (!title.isBlank()) item.put("title", title);
      String publishedAt = value(row, "publishedAt");
      if (!publishedAt.isBlank()) item.put("published_at", publishedAt);
      item.put("verified", true);
    }
    emit(receipt, "PublishResultCompleted", "COMPLETED",
        "云发布已返回可核验结果链接；GEO 可启动核验与同题复测。", extra);
  }

  private void reconcileProjectProgress(Map<String, Object> receipt, String label) {
    String status = repository.projectStatus(number(receipt.get("projectId")));
    if (status.isBlank()) { emitException(receipt, "PROJECT_MISSING", label + "履约项目不存在，需要运营处理。"); return; }
    if ("ARCHIVED".equals(status)) { emitException(receipt, "PROJECT_ARCHIVED", label + "履约项目已归档，需要运营处理。"); return; }
    emitStatusIfChanged(receipt, status, label + "正在由云发布履约团队处理。");
  }

  private void reconcileMediaPr(Map<String, Object> receipt, long projectId) {
    // A MEDIA_PR requirement may include target guidance, but it is not a
    // journalistic invitation until a WinPress operator creates one.  Do not
    // expose generic project planning as an invitation-progress state.
    if (repository.mediaInvitationCount(projectId) == 0) {
      emitStatusIfChanged(receipt, "PENDING_MEDIA_SCOPE",
          "媒体邀请需求已受理，等待云发布确认范围并选择实际邀请对象。");
      return;
    }
    reconcileProjectProgress(receipt, "媒体邀请");
  }

  private void reconcileConference(Map<String, Object> receipt, long projectId) {
    String status = repository.conferenceStatus(projectId);
    if (status.isBlank()) { emitException(receipt, "CONFERENCE_MISSING", "新闻发布会履约对象不存在，需要运营处理。"); return; }
    emitStatusIfChanged(receipt, status, "新闻发布会正在由云发布履约团队处理。");
  }

  private void emitStatusIfChanged(Map<String, Object> receipt, String status, String nextAction) {
    if (status.equals(value(receipt, "status"))) return;
    emit(receipt, "ExternalOrderStatusChanged", status, nextAction, null);
  }

  private void emitException(Map<String, Object> receipt, String code, String nextAction) {
    ObjectNode extra = objectMapper.createObjectNode().put("error_code", code);
    emit(receipt, "ExternalOrderException", "EXCEPTION", nextAction, extra);
  }

  private void emit(
      Map<String, Object> receipt, String eventType, String status, String nextAction, ObjectNode extra
  ) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("event_type", eventType);
    payload.put("order_id", value(receipt, "platformOrderId"));
    payload.put("tenant_id", value(receipt, "tenantId"));
    payload.put("organization_id", value(receipt, "platformOrganizationId"));
    payload.put("brand_id", value(receipt, "platformBrandId"));
    payload.put("project_id", value(receipt, "platformProjectId"));
    payload.put("service_type", value(receipt, "serviceType"));
    payload.put("snapshot_hash", value(receipt, "snapshotHash"));
    payload.put("source_instance_id", sourceIdentity.requireStored(receipt.get("sourceInstanceId")));
    payload.put("winpress_requirement_id", value(receipt, "requirementId"));
    payload.put("winpress_project_id", value(receipt, "projectId"));
    putOptional(payload, "winpress_editorial_task_id", receipt.get("editorialTaskId"));
    putOptional(payload, "winpress_publish_plan_id", receipt.get("publishPlanId"));
    payload.put("winpress_status", status);
    payload.put("next_action", nextAction.trim());
    if (extra != null) extra.fields().forEachRemaining(entry -> payload.set(entry.getKey(), entry.getValue()));
    String contentHash = integrity.hash(payload);
    String idempotencyKey = eventType + ":" + value(receipt, "platformOrderId") + ":" + status + ":" + contentHash;
    String eventId = "wp-" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
    payload.put("event_id", eventId);
    repository.appendOutbox(eventId, value(receipt, "platformOrderId"), value(receipt, "tenantId"), eventType,
        payload, value(receipt, "snapshotHash"), idempotencyKey);
    repository.updateReceiptStatus(value(receipt, "platformOrderId"), status, nextAction.trim());
  }

  private void putOptional(ObjectNode target, String field, Object value) {
    if (value != null && !String.valueOf(value).isBlank()) target.put(field, String.valueOf(value));
  }
  private long number(Object value) { try { return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); } catch (Exception error) { return 0L; } }
  private String value(Map<String, Object> row, String field) { Object value = row.get(field); return value == null ? "" : String.valueOf(value).trim(); }
}
