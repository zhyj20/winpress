package com.winpress.commercial.service;

import com.winpress.commercial.dto.WorkflowDtos.AssignProjectRequest;
import com.winpress.commercial.dto.WorkflowDtos.AssignSupplierChannelRequest;
import com.winpress.commercial.dto.WorkflowDtos.BatchQuoteAdjustmentRequest;
import com.winpress.commercial.dto.WorkflowDtos.ChannelSelection;
import com.winpress.commercial.dto.WorkflowDtos.CreateBusinessInquiryRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateChannelRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreatePublishPlanRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateQuoteRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateRequirementRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateSettlementTransactionRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateSupplierRequest;
import com.winpress.commercial.dto.WorkflowDtos.OfferWritingAssignmentRequest;
import com.winpress.commercial.dto.WorkflowDtos.PageResult;
import com.winpress.commercial.dto.WorkflowDtos.RespondWritingAssignmentRequest;
import com.winpress.commercial.dto.WorkflowDtos.ReviewManuscriptRequest;
import com.winpress.commercial.dto.WorkflowDtos.SubmitManuscriptRequest;
import com.winpress.commercial.dto.WorkflowDtos.SubmitResultRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateBusinessInquiryRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateConferenceProjectRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateConferenceWorkItemRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateMediaInvitationRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateSupplierOrderRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateSupplierRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateTaskRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateChannelRequest;
import com.winpress.commercial.dto.WorkflowDtos.VoidSettlementTransactionRequest;
import com.winpress.commercial.dto.NiumediaDtos.BatchMediaCandidateRequest;
import com.winpress.commercial.dto.NiumediaDtos.DiscoveryTaxonomy;
import com.winpress.commercial.dto.NiumediaDtos.MediaCandidate;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchQuery;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchResult;
import com.winpress.commercial.dto.NiumediaDtos.UpdateConferenceMediaCandidateRequest;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.WorkflowRepository;
import com.winpress.commercial.repository.WorkflowRepository.WritingAssignmentOfferOutcome;
import com.winpress.commercial.repository.WorkflowRepository.RequirementCreation;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {
  private static final BigDecimal ONSITE_WRITING_DAILY_RATE = new BigDecimal("980.00");
  private static final Set<String> ONSITE_SERVICE_TYPES = Set.of("ONSITE_WRITING");
  private static final Set<String> SERVICE_TYPES = Set.of(
      "ONSITE_WRITING", "MEDIA_PR", "DIRECT_PUBLISHING", "NEWS_CONFERENCE");
  private static final Set<String> PROJECT_SCOPES = Set.of("active");
  private static final Set<String> TASK_SCOPES = Set.of("pending", "withResults", "awaitingAcceptance");
  private static final Set<String> CUSTOMER_WORK_ITEM_SCOPES = Set.of("planConfirmation");
  private static final Set<String> TASK_RECORD_SCOPES = Set.of("pendingExecution");
  private static final Set<String> CHANNEL_TYPES = Set.of("MEDIA_PR", "DIRECT_PUBLISHING");
  private static final Set<String> CHANNEL_STATES = Set.of("ACTIVE", "REVIEW_REQUIRED", "INACTIVE");
  private static final Set<String> CHANNEL_SORTS = Set.of("PRICE_ASC", "PRICE_DESC", "DELIVERY_ASC", "NAME_ASC");
  private static final Set<String> MEDIA_DISCOVERY_TARGETS = Set.of("MEDIA", "REPORTER");
  private static final Set<String> MEDIA_CANDIDATE_TYPES = Set.of("MEDIA", "REPORTER", "MANUAL");
  private static final Pattern IDEMPOTENCY_KEY_PATTERN =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{15,79}");
  private static final Set<String> QUOTE_STATES = Set.of("ACTIVE", "EXPIRING", "EXPIRED", "UNQUOTED");
  private static final Set<String> OPERATOR_TASK_STATES = Set.of("PENDING_EXECUTION", "IN_PROGRESS", "NEEDS_INFO", "EXCEPTION");
  private static final Set<String> CONFERENCE_TYPES = Set.of(
      "PRODUCT_RELEASE", "STRATEGIC_SIGNING", "INDUSTRY_FORUM", "CORPORATE_EVENT");
  private static final Set<String> CONFERENCE_FORMATS = Set.of("OFFLINE", "HYBRID", "ONLINE");
  private static final Set<String> CONFERENCE_AGENDA_STATES = Set.of("PREPARING", "CONFIRMED");
  private static final Set<String> CONFERENCE_VENUE_STATES = Set.of("PENDING", "CONFIRMED");
  private static final Set<String> CONFERENCE_WORK_ITEM_STATES = Set.of(
      "PENDING", "IN_PROGRESS", "NEEDS_INFO", "BLOCKED", "COMPLETED");
  private static final Set<String> CONFERENCE_MEDIA_CANDIDATE_STATES = Set.of(
      "CANDIDATE", "READY_TO_INVITE", "INVITED", "RESPONDED", "DECLINED", "ATTENDING", "NOT_PROCEEDING");
  private static final Set<String> MEDIA_INVITATION_PROGRESS_STATES = Set.of(
      "INVITED", "RESPONDED", "DECLINED", "ATTENDING", "NOT_PROCEEDING");
  private static final Set<String> MEDIA_INVITATION_RESULT_READY_STATES = Set.of(
      "INVITED", "RESPONDED", "ATTENDING");
  private static final Set<String> WRITING_ASSIGNMENT_STATES = Set.of(
      "WAITING_MATCH", "OFFERED", "PARTIALLY_ACCEPTED", "ACCEPTED", "DECLINED", "CANCELLED", "COMPLETED");
  private static final Set<String> SUPPLIER_TYPES = Set.of(
      "MEDIA_PR", "DIRECT_PUBLISHING", "WRITING", "EVENT_SERVICE", "MULTI_SERVICE");
  private static final Set<String> SUPPLIER_STATES = Set.of("ACTIVE", "INACTIVE");
  private static final Set<String> SUPPLIER_ORDER_STATES = Set.of(
      "PENDING_SUBMISSION", "SUBMITTED", "ACCEPTED", "IN_PROGRESS", "EXCEPTION", "COMPLETED", "CANCELLED");
  private static final Set<String> SUPPLIER_FULFILLMENT_MODES = Set.of(
      "UNCONFIRMED", "MANUAL", "API");
  private static final Set<String> SUPPLIER_EVIDENCE_REQUIRED_STATES = Set.of(
      "SUBMITTED", "ACCEPTED", "IN_PROGRESS", "COMPLETED");
  private static final Set<String> SETTLEMENT_STATES = Set.of("PENDING", "CONFIRMED", "PAID", "CANCELLED");
  private static final Set<String> SETTLEMENT_TRANSACTION_TYPES = Set.of(
      "PAYMENT", "REFUND", "CREDIT_ADJUSTMENT", "DEBIT_ADJUSTMENT", "WRITE_OFF");
  private static final Set<String> SETTLEMENT_TRANSACTION_STATES = Set.of("CONFIRMED", "VOIDED");
  private static final Set<String> INQUIRY_TYPES = Set.of(
      "API_INTEGRATION", "GENERAL_COOPERATION", "SERVICE_CONSULTATION", "MEDIA_PARTNERSHIP");
  private static final Set<String> INQUIRY_STATES = Set.of("NEW", "CONTACTED", "CLOSED");
  // These projections are intentionally positive lists. Customer-facing endpoints must not
  // begin exposing a new operational field merely because a repository query evolves.
  private static final List<String> CUSTOMER_WORK_ITEM_FIELDS = List.of(
      "recordNo", "projectId", "projectName", "title", "status", "updatedAt", "itemLabel");
  private static final List<String> CUSTOMER_TASK_RECORD_FIELDS = List.of(
      "recordNo", "projectId", "projectName", "title", "serviceType", "itemLabel",
      "status", "dueAt", "completedAt", "note", "updatedAt");
  private static final List<String> CUSTOMER_ORDER_RECORD_FIELDS = List.of(
      "recordNo", "projectId", "projectNo", "projectName", "serviceType",
      "serviceLabel", "amount", "currency", "status", "itemDetail", "createdAt", "updatedAt");
  private static final List<String> CUSTOMER_SETTLEMENT_RECORD_FIELDS = List.of(
      "settlementNo", "projectId", "projectNo", "projectName", "serviceType", "serviceLabel",
      "archiveOnly", "amount", "paidAmount", "currency", "adjustmentAmount", "outstandingAmount",
      "dueAt", "paidAt", "invoiceNo", "status", "updatedAt");
  private static final List<String> CUSTOMER_SETTLEMENT_TRANSACTION_FIELDS = List.of(
      "transactionNo", "settlementNo", "projectId", "projectNo", "projectName",
      "serviceType", "serviceLabel", "archiveOnly",
      "transactionType", "transactionLabel", "amount", "currency", "occurredAt",
      "referenceNo", "customerNote", "status", "createdAt", "updatedAt");
  private static final List<String> CUSTOMER_PUBLISH_PLAN_FIELDS = List.of(
      "planNo", "projectId", "planName", "estimatedAmount", "currency", "status",
      "confirmedAt", "createdAt", "itemCount");
  private static final List<String> CUSTOMER_CREATED_PUBLISH_PLAN_FIELDS = List.of(
      "planNo", "status", "itemCount", "estimatedAmount");
  private static final List<String> CUSTOMER_PROJECT_SUMMARY_FIELDS = List.of(
      "id", "projectNo", "projectName", "status", "customerName", "organizationName",
      "manuscriptStatus", "hasApprovedManuscript", "taskCount", "resultCount", "plannedEndAt", "createdAt");
  private static final List<String> CUSTOMER_PUBLISH_TASK_FIELDS = List.of(
      "taskNo", "projectId", "projectNo", "projectName", "manuscriptTitle", "channelType",
      "channelName", "plannedPublishAt", "actualPublishAt", "status", "updatedAt",
      "mediaInvitationStatus", "mediaInvitedAt", "mediaRespondedAt");
  private static final List<String> CUSTOMER_PROJECT_DETAIL_FIELDS = List.of(
      "projectNo", "projectName", "status", "customerName", "organizationName", "requirementNo",
      "facts", "objective", "requestedService", "eventTime", "eventLocation", "serviceDays",
      "writerCount", "unitPrice", "estimatedAmount", "onsiteContactName", "onsiteContactMobile",
      "deliverableRequirement", "matchingPreference");
  private static final List<String> CUSTOMER_CONFERENCE_FIELDS = List.of(
      "conferenceNo", "conferenceType", "conferenceFormat", "theme", "eventTime", "eventLocation",
      "conferenceScale", "mediaGoal", "guestPlan", "agendaPlan", "venuePlan", "mediaDirection",
      "communicationGoal", "agendaStatus", "venueStatus", "contactName", "contactMobile", "status");
  private static final List<String> CUSTOMER_CONFERENCE_WORK_ITEM_FIELDS = List.of(
      "itemNo", "sortOrder", "phase", "title", "detail", "dueAt", "status", "updatedAt");
  private static final List<String> CUSTOMER_CONFERENCE_CANDIDATE_FIELDS = List.of(
      "displayName", "reporterName", "attribute", "province", "city", "channelForm", "category",
      "coverageTags", "status", "selectedAt", "invitedAt", "respondedAt", "updatedAt");
  private static final List<String> CUSTOMER_SERVICE_INTAKE_TASK_FIELDS = List.of(
      "taskNo", "serviceType", "title", "customerVisibleNote", "status", "completedAt", "updatedAt");
  private static final List<String> CUSTOMER_MANUSCRIPT_FIELDS = List.of(
      "id", "manuscriptNo", "title", "status", "currentVersionNo", "approvedVersionId", "updatedAt");
  private static final List<String> CUSTOMER_MANUSCRIPT_VERSION_FIELDS = List.of(
      "id", "manuscriptId", "versionNo", "versionNumber", "title", "summary", "content", "changeNote",
      "reviewComment", "status", "createdAt", "reviewedAt", "sourceProjectName", "sourceManuscriptTitle");
  private static final List<String> CUSTOMER_RESULT_FIELDS = List.of(
      "resultNo", "channelName", "title", "url", "publishedAt", "verifiedAt", "status");
  private static final List<String> CUSTOMER_MONITORING_FIELDS = List.of(
      "monitoringNo", "monitoredAt", "metricName", "metricValue", "metricText", "sourceUrl", "status");
  private static final List<String> CUSTOMER_PROJECT_SETTLEMENT_FIELDS = List.of(
      "settlementNo", "amount", "paidAmount", "currency", "dueAt", "paidAt", "status");
  private static final List<String> CUSTOMER_FILE_FIELDS = List.of(
      "fileNo", "originalName", "contentType", "fileSize", "createdAt");
  private static final List<String> CUSTOMER_ACTIVITY_PROJECT_FIELDS = List.of(
      "projectId", "projectNo", "projectName", "requestedService", "status", "eventTime", "unitPrice",
      "estimatedAmount", "createdAt");
  private final WorkflowRepository repository;
  private final NiumediaMediaService niumediaMediaService;
  private final IntegrationAdminService integrationAdminService;

  public WorkflowService(
      WorkflowRepository repository,
      NiumediaMediaService niumediaMediaService,
      IntegrationAdminService integrationAdminService) {
    this.repository = repository;
    this.niumediaMediaService = niumediaMediaService;
    this.integrationAdminService = integrationAdminService;
  }

  public Map<String, Object> dashboard() { return repository.dashboard(CurrentUser.get()); }

  public PageResult<Map<String, Object>> workItems(int page, int pageSize) {
    AuthPrincipal user = CurrentUser.get();
    int[] p = page(page, pageSize);
    List<Map<String, Object>> items = repository.workItems(user, p[1], p[2]);
    if ("CUSTOMER".equals(user.role())) {
      items = customerFieldRows(items, CUSTOMER_WORK_ITEM_FIELDS);
    }
    return new PageResult<>(
        items, repository.workItemsCount(user), p[0], p[1]);
  }

  /**
   * A saved media invitation or direct-publishing plan needs a second, explicit customer action
   * before the system creates any execution task. Keep this filtered queue separate from the
   * general to-do list so the dashboard number and destination list share one query scope.
   */
  public PageResult<Map<String, Object>> workItems(String scope, int page, int pageSize) {
    if (blank(scope)) return workItems(page, pageSize);
    AuthPrincipal user = CurrentUser.get();
    if (!"CUSTOMER".equals(user.role()) || !CUSTOMER_WORK_ITEM_SCOPES.contains(scope)) {
      throw bad("INVALID_WORK_ITEM_SCOPE", "待办筛选范围不正确");
    }
    int[] p = page(page, pageSize);
    List<Map<String, Object>> items = customerFieldRows(
        repository.workItems(user, scope, p[1], p[2]), CUSTOMER_WORK_ITEM_FIELDS);
    return new PageResult<>(items, repository.workItemsCount(user, scope), p[0], p[1]);
  }

  public PageResult<Map<String, Object>> taskRecords(int page, int pageSize) {
    AuthPrincipal user = CurrentUser.get();
    int[] p = page(page, pageSize);
    List<Map<String, Object>> records = repository.taskRecords(user, p[1], p[2]);
    if ("CUSTOMER".equals(user.role())) {
      records = customerTaskRecords(records);
    }
    return new PageResult<>(records, repository.taskRecordsCount(user), p[0], p[1]);
  }

  public PageResult<Map<String, Object>> taskRecords(String scope, int page, int pageSize) {
    if (blank(scope)) return taskRecords(page, pageSize);
    if (!blank(scope) && !TASK_RECORD_SCOPES.contains(scope)) {
      throw bad("INVALID_TASK_RECORD_SCOPE", "任务记录筛选范围不正确");
    }
    AuthPrincipal user = CurrentUser.get();
    int[] p = page(page, pageSize);
    List<Map<String, Object>> records = repository.taskRecords(user, scope, p[1], p[2]);
    if ("CUSTOMER".equals(user.role())) {
      records = customerTaskRecords(records);
    }
    return new PageResult<>(records, repository.taskRecordsCount(user, scope), p[0], p[1]);
  }

  /**
   * Customer-facing order ledger. It intentionally projects only customer service records and
   * customer prices; supplier, cost and upstream fields stay in the platform-only supplier flow.
   */
  public PageResult<Map<String, Object>> orderRecords(
      String serviceType, String status, int page, int pageSize) {
    AuthPrincipal user = CurrentUser.get();
    if (!blank(serviceType) && !SERVICE_TYPES.contains(serviceType)) {
      throw bad("INVALID_SERVICE_TYPE", "服务类型不正确");
    }
    int[] p = page(page, pageSize);
    List<Map<String, Object>> records = repository.orderRecords(
        user, serviceType, status, p[1], p[2]);
    if ("CUSTOMER".equals(user.role())) {
      // Service, price and fulfilment state are customer-visible. Assignment, supplier and
      // procurement fields remain in the platform-only execution flow.
      records = customerFieldRows(records, CUSTOMER_ORDER_RECORD_FIELDS);
    }
    return new PageResult<>(
        records,
        repository.orderRecordsCount(user, serviceType, status), p[0], p[1]);
  }

  /**
   * Customer billing ledger. It exposes only existing, platform-confirmed settlement records in
   * the caller's project scope; it does not manufacture payment, refund, invoice or reconciliation
   * events that have not been independently confirmed.
   */
  public PageResult<Map<String, Object>> settlementRecords(String status, int page, int pageSize) {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    if (!blank(status) && !SETTLEMENT_STATES.contains(status)) {
      throw bad("INVALID_SETTLEMENT_STATUS", "结算状态不正确");
    }
    int[] p = page(page, pageSize);
    List<Map<String, Object>> records = repository.customerSettlementRecords(user, status, p[1], p[2]);
    return new PageResult<>(
        customerFieldRows(records, CUSTOMER_SETTLEMENT_RECORD_FIELDS),
        repository.customerSettlementRecordsCount(user, status), p[0], p[1]);
  }

  /**
   * Read-only customer archive for settlement rows created by retired combined-service models.
   * These records remain traceable but are not returned as current payables and cannot be mutated
   * through the current four-service settlement workflow.
   */
  public PageResult<Map<String, Object>> archivedSettlementRecords(
      String status, int page, int pageSize) {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    if (!blank(status) && !SETTLEMENT_STATES.contains(status)) {
      throw bad("INVALID_SETTLEMENT_STATUS", "结算状态不正确");
    }
    int[] p = page(page, pageSize);
    List<Map<String, Object>> records = repository.customerArchivedSettlementRecords(
        user, status, p[1], p[2]);
    return new PageResult<>(
        customerFieldRows(records, CUSTOMER_SETTLEMENT_RECORD_FIELDS),
        repository.customerArchivedSettlementRecordsCount(user, status), p[0], p[1]);
  }

  /**
   * Customer-visible payment and adjustment facts. Only the caller's project scope and a strict
   * public field list are returned; internal notes, actor identities and voiding reasons remain
   * platform-only.
   */
  public PageResult<Map<String, Object>> settlementTransactionRecords(
      String transactionType, String status, int page, int pageSize) {
    return settlementTransactionRecords(transactionType, status, false, page, pageSize);
  }

  /**
   * Read-only customer archive for transaction facts attached to retired combined-service
   * settlements. These facts remain traceable but never contribute to the current four-service
   * transaction ledger.
   */
  public PageResult<Map<String, Object>> archivedSettlementTransactionRecords(
      String transactionType, String status, int page, int pageSize) {
    return settlementTransactionRecords(transactionType, status, true, page, pageSize);
  }

  private PageResult<Map<String, Object>> settlementTransactionRecords(
      String transactionType, String status, boolean archiveOnly, int page, int pageSize) {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    String normalizedType = normalizeOptional(transactionType);
    String normalizedStatus = normalizeOptional(status);
    if (!blank(normalizedType) && !SETTLEMENT_TRANSACTION_TYPES.contains(normalizedType)) {
      throw bad("INVALID_SETTLEMENT_TRANSACTION_TYPE", "交易类型不正确");
    }
    if (!blank(normalizedStatus) && !SETTLEMENT_TRANSACTION_STATES.contains(normalizedStatus)) {
      throw bad("INVALID_SETTLEMENT_TRANSACTION_STATUS", "交易状态不正确");
    }
    int[] p = page(page, pageSize);
    List<Map<String, Object>> records = archiveOnly
        ? repository.customerArchivedSettlementTransactions(
            user, normalizedType, normalizedStatus, p[1], p[2])
        : repository.customerSettlementTransactions(
            user, normalizedType, normalizedStatus, p[1], p[2]);
    long total = archiveOnly
        ? repository.customerArchivedSettlementTransactionsCount(
            user, normalizedType, normalizedStatus)
        : repository.customerSettlementTransactionsCount(
            user, normalizedType, normalizedStatus);
    return new PageResult<>(
        customerFieldRows(records, CUSTOMER_SETTLEMENT_TRANSACTION_FIELDS),
        total, p[0], p[1]);
  }

  @Transactional
  public Map<String, Object> createRequirement(
      CreateRequirementRequest request, String idempotencyKey) {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    String normalizedIdempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
    if (normalizedIdempotencyKey.isEmpty()) {
      throw bad("IDEMPOTENCY_KEY_REQUIRED", "提交服务需求时缺少请求标识，请刷新页面后重试");
    }
    if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalizedIdempotencyKey).matches()) {
      throw bad("INVALID_IDEMPOTENCY_KEY", "请求标识无效，请刷新页面后重试");
    }
    String submissionHash = requirementRequestHash(request);
    RequirementCreation existing = repository.existingRequirement(
        user, normalizedIdempotencyKey, submissionHash);
    if (existing != null) {
      return requirementResponse(request, existing.projectId());
    }
    if (!SERVICE_TYPES.contains(request.requestedService())) {
      throw bad("INVALID_SERVICE_TYPE", "请选择有效的服务类型");
    }
    if (request.relatedProjectId() != null
        && repository.activityRootProjectId(user, request.relatedProjectId()) == null) {
      throw bad("RELATED_PROJECT_INVALID", "关联项目不存在，或不属于当前客户组织");
    }
    boolean hasSourceManuscriptId = request.sourceManuscriptId() != null;
    boolean hasSourceVersionId = request.sourceManuscriptVersionId() != null;
    if (hasSourceManuscriptId != hasSourceVersionId) {
      throw bad("SOURCE_MANUSCRIPT_REFERENCE_INCOMPLETE", "请选择完整的客户已确认稿件版本");
    }
    if (hasSourceManuscriptId && !"DIRECT_PUBLISHING".equals(request.requestedService())) {
      throw bad("SOURCE_MANUSCRIPT_NOT_APPLICABLE", "仅直编发稿可使用客户已确认稿件");
    }
    if (hasSourceManuscriptId && repository.approvedCustomerManuscriptSource(
        user, request.sourceManuscriptId(), request.sourceManuscriptVersionId()).isEmpty()) {
      throw bad("SOURCE_MANUSCRIPT_NOT_AVAILABLE", "所选客户已确认稿件不可用，请重新选择");
    }
    if (!"NEWS_CONFERENCE".equals(request.requestedService())) {
      if (blank(request.facts())) {
        throw bad("FACTS_REQUIRED", "请填写已确认的事实");
      }
    }
    if (ONSITE_SERVICE_TYPES.contains(request.requestedService())) {
      if (request.eventTime() == null) throw bad("EVENT_TIME_REQUIRED", "云采写订单须填写活动或服务开始时间");
      if (request.eventLocation() == null || request.eventLocation().isBlank()) throw bad("EVENT_LOCATION_REQUIRED", "云采写订单须填写服务地点");
      if (request.serviceDays() == null || request.serviceDays() < 1 || request.serviceDays() > 30) throw bad("INVALID_SERVICE_DAYS", "服务天数须为 1 至 30 天");
      if (request.writerCount() == null || request.writerCount() < 1 || request.writerCount() > 10) throw bad("INVALID_WRITER_COUNT", "写手人数须为 1 至 10 人");
      if (request.onsiteContactName() == null || request.onsiteContactName().isBlank()) throw bad("ONSITE_CONTACT_REQUIRED", "请填写现场联系人");
      if (request.onsiteContactMobile() == null || !request.onsiteContactMobile().matches("1[3-9]\\d{9}")) throw bad("ONSITE_MOBILE_INVALID", "请填写有效的现场联系人手机号");
    }
    if ("NEWS_CONFERENCE".equals(request.requestedService())) {
      if (!blank(request.conferenceType()) && !CONFERENCE_TYPES.contains(request.conferenceType())) {
        throw bad("INVALID_CONFERENCE_TYPE", "发布会类型不正确");
      }
      if (!blank(request.conferenceFormat()) && !CONFERENCE_FORMATS.contains(request.conferenceFormat())) {
        throw bad("INVALID_CONFERENCE_FORMAT", "举办形式不正确");
      }
      if (!blank(request.conferenceAgendaStatus())
          && !CONFERENCE_AGENDA_STATES.contains(request.conferenceAgendaStatus())) {
        throw bad("INVALID_AGENDA_STATUS", "议程准备情况不正确");
      }
      if (!blank(request.conferenceVenueStatus())
          && !CONFERENCE_VENUE_STATES.contains(request.conferenceVenueStatus())) {
        throw bad("INVALID_VENUE_STATUS", "场地确认情况不正确");
      }
      if (blank(request.conferenceContactName())) throw bad("CONFERENCE_CONTACT_REQUIRED", "请填写会务联系人");
      if (blank(request.conferenceContactMobile()) || !request.conferenceContactMobile().matches("1[3-9]\\d{9}")) {
        throw bad("CONFERENCE_MOBILE_INVALID", "请填写有效的会务联系人手机号");
      }
    }
    RequirementCreation creation = repository.createRequirement(
        user, request, normalizedIdempotencyKey, submissionHash);
    Long projectId = creation.projectId();
    if (creation.created() && hasSourceManuscriptId) {
      Long copiedManuscriptId = repository.copyApprovedManuscriptToDirectProject(
          user, projectId, request.sourceManuscriptId(), request.sourceManuscriptVersionId());
      if (copiedManuscriptId == null) {
        throw new BusinessException(
            "SOURCE_MANUSCRIPT_NOT_AVAILABLE",
            "所选客户已确认稿件不可用，请重新选择",
            HttpStatus.CONFLICT);
      }
    }
    return requirementResponse(request, projectId);
  }

  private Map<String, Object> requirementResponse(
      CreateRequirementRequest request, Long projectId) {
    boolean hasSourceManuscriptId = request.sourceManuscriptId() != null;
    if (ONSITE_SERVICE_TYPES.contains(request.requestedService())) {
      BigDecimal amount = ONSITE_WRITING_DAILY_RATE
          .multiply(BigDecimal.valueOf(request.serviceDays()))
          .multiply(BigDecimal.valueOf(request.writerCount()));
      return Map.of("projectId", projectId, "message", "云采写订单已提交", "unitPrice", ONSITE_WRITING_DAILY_RATE, "estimatedAmount", amount);
    }
    if ("NEWS_CONFERENCE".equals(request.requestedService())) {
      return Map.of("projectId", projectId, "message", "新闻发布会项目已创建");
    }
    return hasSourceManuscriptId
        ? Map.of("projectId", projectId, "message", "直编发稿项目已创建，客户定稿已复制")
        : Map.of("projectId", projectId, "message", "需求已提交");
  }

  private static String requirementRequestHash(CreateRequirementRequest request) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(request.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  /**
   * The response contains only customer-visible project and version descriptors. Raw manuscript
   * bodies and internal provenance identifiers stay in the server-side copy operation.
   */
  public List<Map<String, Object>> approvedCustomerManuscriptSources() {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    return repository.approvedCustomerManuscriptSources(user);
  }

  public PageResult<Map<String, Object>> requirements(String status, int page, int pageSize) {
    // The requirement ledger contains customer intake data. Operators work from the projects
    // assigned to them and must not use this endpoint to enumerate unrelated customer requests.
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER", "PLATFORM_ADMIN");
    int[] p = page(page, pageSize);
    return new PageResult<>(repository.requirements(user, status, p[1], p[2]),
        repository.requirementsCount(user, status), p[0], p[1]);
  }

  public PageResult<Map<String, Object>> projects(
      String status, String scope, String keyword, String serviceType, int page, int pageSize) {
    AuthPrincipal user = CurrentUser.get();
    if (!blank(scope) && !PROJECT_SCOPES.contains(scope)) {
      throw bad("INVALID_PROJECT_SCOPE", "项目筛选范围不正确");
    }
    if (!blank(serviceType) && !SERVICE_TYPES.contains(serviceType)) {
      throw bad("INVALID_SERVICE_TYPE", "服务类型不正确");
    }
    int[] p = page(page, pageSize);
    List<Map<String, Object>> projects = repository.projects(
        user, status, scope, keyword, serviceType, p[1], p[2]);
    if ("CUSTOMER".equals(user.role())) {
      projects = customerFieldRows(projects, CUSTOMER_PROJECT_SUMMARY_FIELDS);
    }
    return new PageResult<>(projects,
        repository.projectsCount(user, status, scope, keyword, serviceType), p[0], p[1]);
  }

  public Map<String, Object> project(Long projectId) {
    AuthPrincipal user = CurrentUser.get();
    requireProject(user, projectId);
    Map<String, Object> detail = repository.projectDetail(projectId);
    detail.put("activityProjects", repository.activityProjects(user, projectId));
    return "CUSTOMER".equals(user.role()) ? customerProjectDetail(detail) : detail;
  }

  @Transactional
  public Map<String, Object> submitManuscript(Long projectId, SubmitManuscriptRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PUBLISH_OPERATOR", "PLATFORM_ADMIN");
    requireProject(user, projectId);
    Map<String, Object> state = repository.lockProjectForManuscriptSubmission(projectId);
    if (state.isEmpty()) throw notFound("云采写项目不存在");
    if (!"ONSITE_WRITING".equals(string(state.get("requestedService")))) {
      throw bad("WRITING_PROJECT_REQUIRED", "请在独立的云采写项目中提交稿件");
    }
    String manuscriptStatus = string(state.get("manuscriptStatus"));
    if ("CLIENT_APPROVED".equals(manuscriptStatus)) {
      throw new BusinessException(
          "WRITING_ORDER_FINALIZED", "客户已确认定稿，不能再次提交新版本", HttpStatus.CONFLICT);
    }
    if ("CLIENT_REVIEW".equals(manuscriptStatus)) {
      throw new BusinessException(
          "WRITING_REVIEW_PENDING", "当前版本正在等待客户审核，不能重复提交", HttpStatus.CONFLICT);
    }
    Long manuscriptId = repository.submitManuscript(user, projectId, request);
    return Map.of("manuscriptId", manuscriptId, "message", "稿件已提交客户审核");
  }

  /**
   * Direct publishing accepts a customer-provided final manuscript without routing the customer
   * through Cloud Writing. The scope check and service-type guard prevent this endpoint from
   * becoming a general customer-side manuscript editor.
   */
  @Transactional
  public Map<String, Object> submitCustomerManuscript(
      Long projectId, SubmitManuscriptRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    requireProject(user, projectId);
    if (!"DIRECT_PUBLISHING".equals(repository.projectRequestedService(projectId))) {
      throw bad("CUSTOMER_MANUSCRIPT_NOT_ALLOWED", "仅直编发稿项目可提交客户已确认稿件");
    }
    Long manuscriptId = repository.submitCustomerApprovedManuscript(user, projectId, request);
    return Map.of(
        "manuscriptId", manuscriptId,
        "status", "CLIENT_APPROVED",
        "message", "客户定稿已保存，可继续筛选发稿渠道");
  }

  @Transactional
  public Map<String, Object> reviewManuscript(Long manuscriptId, ReviewManuscriptRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    if (!Set.of("APPROVE", "RETURN").contains(request.decision())) {
      throw bad("INVALID_REVIEW_DECISION", "请选择确认定稿或退回修改");
    }
    Map<String, Object> context = repository.manuscriptContext(manuscriptId, request.versionId());
    if (context.isEmpty()) throw notFound("稿件版本不存在");
    Long projectId = number(context.get("project_id"));
    requireProject(user, projectId);
    if (!"CLIENT_REVIEW".equals(string(context.get("version_status")))) {
      throw new BusinessException("VERSION_NOT_REVIEWABLE", "该版本当前不能审核", HttpStatus.CONFLICT);
    }
    repository.reviewManuscript(user, manuscriptId, request.versionId(), request.decision(), request.comment());
    return Map.of("manuscriptId", manuscriptId, "status", "APPROVE".equals(request.decision()) ? "CLIENT_APPROVED" : "CLIENT_RETURNED");
  }

  public PageResult<Map<String, Object>> channels(
      String type, String keyword, String region, String category, String publishForm,
      BigDecimal minPrice, BigDecimal maxPrice, Integer maxDays, Boolean linkSupport,
      String linkType, String newsSource, String entryLevel, String specialIndustry,
      String weekendPolicy,
      String sort, int page, int pageSize) {
    CurrentUser.get();
    return channelDirectory(
        type, keyword, region, category, publishForm, minPrice, maxPrice, maxDays,
        linkSupport, linkType, newsSource, entryLevel, specialIndustry, weekendPolicy,
        sort, page, pageSize);
  }

  /**
   * Open API callers are authenticated and scope-checked before reaching this method. Keeping
   * the sanitised projection here prevents the API adapter from growing a second channel query.
   */
  public PageResult<Map<String, Object>> openApiChannels(
      String type, String keyword, String region, String category, String publishForm,
      BigDecimal minPrice, BigDecimal maxPrice, Integer maxDays, Boolean linkSupport,
      String linkType, String newsSource, String entryLevel, String specialIndustry,
      String weekendPolicy,
      String sort, int page, int pageSize) {
    return channelDirectory(
        type, keyword, region, category, publishForm, minPrice, maxPrice, maxDays,
        linkSupport, linkType, newsSource, entryLevel, specialIndustry, weekendPolicy,
        sort, page, pageSize);
  }

  private PageResult<Map<String, Object>> channelDirectory(
      String type, String keyword, String region, String category, String publishForm,
      BigDecimal minPrice, BigDecimal maxPrice, Integer maxDays, Boolean linkSupport,
      String linkType, String newsSource, String entryLevel, String specialIndustry,
      String weekendPolicy,
      String sort, int page, int pageSize) {
    if (type != null && !type.isBlank() && !CHANNEL_TYPES.contains(type)) {
      throw bad("INVALID_CHANNEL_TYPE", "渠道类型不正确");
    }
    if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0) {
      throw bad("INVALID_PRICE_RANGE", "价格筛选不能小于零");
    }
    if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
      throw bad("INVALID_PRICE_RANGE", "最低价格不能高于最高价格");
    }
    if (maxDays != null && (maxDays < 1 || maxDays > 365)) {
      throw bad("INVALID_DELIVERY_DAYS", "时效筛选应在 1 到 365 个工作日之间");
    }
    String safeSort = sort == null || sort.isBlank() ? "PRICE_ASC" : sort;
    if (!CHANNEL_SORTS.contains(safeSort)) throw bad("INVALID_CHANNEL_SORT", "排序方式不正确");
    int[] p = page(page, pageSize);
    List<Map<String, Object>> rows = repository.channels(
        type, keyword, region, category, publishForm, minPrice, maxPrice, maxDays,
        linkSupport, linkType, newsSource, entryLevel, specialIndustry, weekendPolicy,
        safeSort, p[1], p[2]);
    return new PageResult<>(
        rows.stream().map(this::publicChannelRow).toList(),
        repository.channelsCount(
            type, keyword, region, category, publishForm, minPrice, maxPrice, maxDays,
            linkSupport, linkType, newsSource, entryLevel, specialIndustry, weekendPolicy),
        p[0], p[1]);
  }

  /**
   * The directory endpoint is shared by customer screens. Platform channel numbers and quote
   * primary keys are operational references, not customer-visible media data.
   */
  private Map<String, Object> publicChannelRow(Map<String, Object> row) {
    Map<String, Object> safe = new LinkedHashMap<>();
    for (String key : List.of(
        "id", "channelName", "channelType", "category", "region", "publishForm",
        "expectedDays", "linkSupport", "linkType", "newsSource", "entryLevel",
        "specialIndustry", "weekendPolicy", "publicNotes", "customerPrice", "currency",
        "validUntil", "publicTerms", "status")) {
      if (row.containsKey(key)) safe.put(key, row.get(key));
    }
    return safe;
  }

  public Map<String, Object> channelTaxonomy(String type) {
    CurrentUser.get();
    return channelTaxonomyDirectory(type);
  }

  /** See {@link #openApiChannels}; this method is not exposed without API-key scope checks. */
  public Map<String, Object> openApiChannelTaxonomy(String type) {
    return channelTaxonomyDirectory(type);
  }

  private Map<String, Object> channelTaxonomyDirectory(String type) {
    String safeType = blank(type) ? "DIRECT_PUBLISHING" : type.trim();
    if (!CHANNEL_TYPES.contains(safeType)) {
      throw bad("INVALID_CHANNEL_TYPE", "渠道类型不正确");
    }
    return repository.channelTaxonomy(safeType);
  }

  public Map<String, Object> mediaDiscoveryStatus() { return niumediaMediaService.status(); }

  public DiscoveryTaxonomy mediaDiscoveryTaxonomy() { return niumediaMediaService.taxonomy(); }

  public MediaSearchResult searchMediaDiscovery(
      String target, String keyword, String name, String province, String city,
      Integer mediumType, String mediaType, String mpTypes, String mpTypeGroup,
      String mediaRef, Integer reporterType, String platform, String sort, String field,
      String workflow, int page, int pageSize) {
    String safeTarget = blank(target) ? "MEDIA" : target.trim().toUpperCase();
    if (!MEDIA_DISCOVERY_TARGETS.contains(safeTarget)) {
      throw bad("INVALID_MEDIA_DISCOVERY_TARGET", "媒体检索类型不正确");
    }
    Long mediaId = null;
    if ("REPORTER".equals(safeTarget)) {
      if (blank(mediaRef)) {
        throw bad("MEDIA_ID_REQUIRED", "请先选择媒体后再筛选记者");
      }
      mediaId = niumediaMediaService.resolveMediaId(mediaRef);
    }
    int[] p = page(page, pageSize);
    return niumediaMediaService.search(new MediaSearchQuery(
        safeTarget, keyword, name, province, city, mediumType, mediaType, mpTypes,
        mpTypeGroup, mediaId, reporterType, platform, sort, field, workflow, p[0], p[1]));
  }

  @Transactional
  public Map<String, Object> addConferenceMediaCandidate(Long projectId, MediaCandidate candidate) {
    AuthPrincipal user = CurrentUser.get();
    requireProject(user, projectId);
    MediaCandidate resolved = resolveCandidateForPersistence(candidate);
    validateMediaCandidate(resolved);
    if (!repository.hasConferenceProject(projectId)) throw notFound("新闻发布会项目不存在");
    if (!repository.addConferenceMediaCandidate(user, projectId, resolved)) {
      throw new BusinessException("MEDIA_CANDIDATE_EXISTS", "该候选已在发布会拟邀名单中", HttpStatus.CONFLICT);
    }
    return Map.of("message", "已加入发布会拟邀名单", "displayName", resolved.displayName());
  }

  @Transactional
  public Map<String, Object> addConferenceMediaCandidates(
      Long projectId, BatchMediaCandidateRequest request) {
    AuthPrincipal user = CurrentUser.get();
    requireProject(user, projectId);
    if (!repository.hasConferenceProject(projectId)) throw notFound("新闻发布会项目不存在");
    int added = 0;
    int existing = 0;
    for (MediaCandidate candidate : request.candidates()) {
      MediaCandidate resolved = resolveCandidateForPersistence(candidate);
      validateMediaCandidate(resolved);
      if (repository.addConferenceMediaCandidate(user, projectId, resolved)) added++;
      else existing++;
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("added", added);
    result.put("existing", existing);
    result.put("message", added > 0 ? "候选名单已更新" : "所选候选已在名单中");
    return result;
  }

  @Transactional
  public Map<String, Object> createPublishPlan(
      Long projectId, CreatePublishPlanRequest request, String idempotencyKey) {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    requireProject(user, projectId);
    String normalizedIdempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
    if (normalizedIdempotencyKey.isEmpty()) {
      throw bad(
          "IDEMPOTENCY_KEY_REQUIRED",
          "保存发布计划时缺少请求标识，请刷新页面后重试");
    }
    if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalizedIdempotencyKey).matches()) {
      throw bad("INVALID_IDEMPOTENCY_KEY", "请求标识无效，请刷新页面后重试");
    }
    String submissionHash = publishPlanRequestHash(request);
    Map<String, Object> existingPlan = repository.existingPublishPlan(
        user, projectId, normalizedIdempotencyKey, submissionHash);
    if (!existingPlan.isEmpty()) {
      return publishPlanResponse(existingPlan);
    }
    if (request.selections() == null || request.selections().isEmpty()) {
      throw bad("PUBLISH_PLAN_SELECTION_REQUIRED", "请至少选择一个拟邀对象或直编渠道");
    }
    List<Map<String, Object>> channelRows = new ArrayList<>();
    List<ChannelSelection> normalizedSelections = new ArrayList<>();
    Set<String> types = new LinkedHashSet<>();
    Set<String> selectionKeys = new LinkedHashSet<>();
    for (ChannelSelection selection : request.selections()) {
      Map<String, Object> channel;
      String type;
      if (selection.channelId() == null) {
        // A customer may submit a manually supplemented invitation target while the external
        // media catalogue is unavailable or still awaiting acceptance.  It is deliberately not
        // represented by a fabricated execution channel, supplier, or customer price.
        if (selection.mediaCandidate() == null && blank(selection.mediaName())) {
          throw bad("INVITATION_TARGET_REQUIRED", "媒体邀请须填写拟邀媒体");
        }
        type = "MEDIA_PR";
        channel = new HashMap<>();
        channel.put("channel_type", type);
        channel.put("status", "MANUAL_REVIEW");
      } else {
        channel = repository.channel(selection.channelId());
        if (channel.isEmpty() || !"ACTIVE".equals(string(channel.get("status")))) {
          throw notFound("所选渠道不存在或已停用");
        }
        type = string(channel.get("channel_type"));
      }
      if (!Set.of("MEDIA_PR", "DIRECT_PUBLISHING").contains(type)) {
        throw bad("CHANNEL_NOT_SELECTABLE", "该渠道不能加入新的发布计划");
      }
      types.add(type);
      if (!"MEDIA_PR".equals(type) && selection.mediaCandidate() != null) {
        throw bad("MEDIA_CANDIDATE_NOT_APPLICABLE", "仅媒体邀请可选择媒体或记者候选");
      }
      MediaCandidate mediaCandidate = selection.mediaCandidate();
      if ("MEDIA_PR".equals(type) && mediaCandidate != null) {
        mediaCandidate = resolveCandidateForPersistence(mediaCandidate);
        validateMediaCandidate(mediaCandidate);
      }
      ChannelSelection normalized = new ChannelSelection(
          selection.channelId(), selection.plannedPublishAt(), selection.journalistName(),
          selection.mediaName(), selection.note(), mediaCandidate);
      if ("MEDIA_PR".equals(type) && blank(normalized.mediaName())) {
        throw bad("INVITATION_TARGET_REQUIRED", "媒体邀请须填写拟邀媒体");
      }
      if ("MEDIA_PR".equals(type) && normalized.mediaCandidate() != null &&
          !normalized.mediaCandidate().displayName().trim().equals(normalized.mediaName().trim())) {
        throw bad("MEDIA_CANDIDATE_MISMATCH", "已选择的媒体候选与拟邀媒体不一致，请重新选择");
      }
      String selectionKey = "DIRECT_PUBLISHING".equals(type)
          ? "CHANNEL:" + normalized.channelId()
          : "MEDIA_PR:" + (normalized.mediaCandidate() == null
              ? normalized.mediaName().trim() + "|" + string(normalized.journalistName())
              : normalized.mediaCandidate().candidateKey().trim());
      if (!selectionKeys.add(selectionKey.toLowerCase())) {
        throw bad("DUPLICATE_CHANNEL_SELECTION", "同一发布计划不能重复选择同一邀请对象或直编渠道");
      }
      if ("DIRECT_PUBLISHING".equals(type) && (channel.get("quote_id") == null || channel.get("customer_price") == null || channel.get("valid_until") == null)) {
        throw new BusinessException("PRICE_UNAVAILABLE", "所选媒体暂无有效报价", HttpStatus.CONFLICT);
      }
      Map<String, Object> copy = new HashMap<>(channel);
      channelRows.add(copy);
      normalizedSelections.add(normalized);
    }
    if (types.size() != 1) {
      throw bad("MIXED_SERVICE_PLAN_NOT_ALLOWED", "媒体邀请和直编发稿须分别建立独立计划");
    }
    String requestedService = repository.projectRequestedService(projectId);
    if (types.contains("DIRECT_PUBLISHING") && !"DIRECT_PUBLISHING".equals(requestedService)) {
      throw bad("DIRECT_PROJECT_REQUIRED", "请先创建直编发稿项目，再提交发稿计划");
    }
    if (types.contains("MEDIA_PR") && !"MEDIA_PR".equals(requestedService)) {
      throw bad("MEDIA_PR_PROJECT_REQUIRED", "请先创建媒体邀请项目，再提交邀请计划");
    }
    if (Boolean.TRUE.equals(request.exclusiveMediaPr()) || request.lockExpiresAt() != null) {
      throw bad("MEDIA_PR_EXCLUSIVE_NOT_AVAILABLE",
          "线上发布计划当前仅支持保存拟邀名单或发稿渠道；如需另行约定的排他安排，请联系项目负责人确认。");
    }
    boolean hasManuscriptId = request.manuscriptId() != null;
    boolean hasVersionId = request.manuscriptVersionId() != null;
    if (hasManuscriptId != hasVersionId) {
      throw bad("MANUSCRIPT_REFERENCE_INCOMPLETE", "稿件与稿件版本必须同时选择");
    }
    boolean approvedManuscriptRequired = types.contains("DIRECT_PUBLISHING");
    Map<String, Object> context = Map.of();
    if (hasManuscriptId) {
      context = repository.manuscriptContext(request.manuscriptId(), request.manuscriptVersionId());
      if (context.isEmpty() || !projectId.equals(number(context.get("project_id")))) throw notFound("稿件版本不存在");
      if (!request.manuscriptVersionId().equals(number(context.get("approved_version_id"))) ||
          !"APPROVED".equals(string(context.get("version_status")))) {
        throw new BusinessException("MANUSCRIPT_NOT_APPROVED", "请先确认稿件定稿版本", HttpStatus.CONFLICT);
      }
    } else if (approvedManuscriptRequired) {
      throw new BusinessException("MANUSCRIPT_NOT_APPROVED", "直编发稿必须选择已确认的定稿版本", HttpStatus.CONFLICT);
    }
    for (Map<String, Object> channel : channelRows) channel.put("title", context.get("title"));
    boolean activeLock = hasManuscriptId && repository.hasActiveLock(request.manuscriptId());
    if (activeLock && types.contains("DIRECT_PUBLISHING")) {
      throw new BusinessException("MANUSCRIPT_LOCKED",
          "当前定稿版本存在未完成的既有发布安排，暂不能提交直编发稿，请联系项目负责人确认。", HttpStatus.CONFLICT);
    }
    String planName = blank(request.planName())
        ? (hasManuscriptId ? string(context.get("title")) + "传播计划" : "媒体邀请计划")
        : request.planName().trim();
    Map<String, Object> plan = repository.createPublishPlan(
        user, projectId, request.manuscriptId(), request.manuscriptVersionId(), planName,
        request.objective(), false, null, channelRows, normalizedSelections,
        normalizedIdempotencyKey, submissionHash);
    return publishPlanResponse(plan);
  }

  private Map<String, Object> publishPlanResponse(Map<String, Object> source) {
    Map<String, Object> plan = customerFieldRow(source, CUSTOMER_CREATED_PUBLISH_PLAN_FIELDS);
    plan.put("message", "发布计划已保存，请核对后提交项目核验");
    return plan;
  }

  private static String publishPlanRequestHash(CreatePublishPlanRequest request) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(request.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private void validateMediaCandidate(MediaCandidate candidate) {
    if (!candidate.available()) {
      throw bad("MEDIA_CANDIDATE_UNAVAILABLE", "该候选当前不可加入名单，请重新筛选");
    }
    if (!MEDIA_CANDIDATE_TYPES.contains(candidate.candidateType().trim().toUpperCase())) {
      throw bad("INVALID_MEDIA_CANDIDATE_TYPE", "媒体候选类型不正确");
    }
    if (blank(candidate.candidateKey()) || blank(candidate.mediaId()) || blank(candidate.displayName())) {
      throw bad("INVALID_MEDIA_CANDIDATE", "媒体候选信息不完整");
    }
    if ("REPORTER".equalsIgnoreCase(candidate.candidateType()) &&
        (blank(candidate.reporterId()) || blank(candidate.reporterName()))) {
      throw bad("INVALID_REPORTER_CANDIDATE", "记者候选信息不完整");
    }
  }

  /**
   * Media-library candidates can only be persisted after their opaque, user-bound selection
   * reference is resolved on the server.  Manual entries are deliberately re-issued with server
   * identifiers and stay marked as pending verification; client-supplied provider IDs are ignored.
   */
  private MediaCandidate resolveCandidateForPersistence(MediaCandidate submitted) {
    if (submitted == null || blank(submitted.candidateType())) {
      throw bad("INVALID_MEDIA_CANDIDATE", "媒体候选信息不完整");
    }
    String type = submitted.candidateType().trim().toUpperCase();
    if ("MANUAL".equals(type)) return normalizeManualCandidate(submitted);
    if (!Set.of("MEDIA", "REPORTER").contains(type)) {
      throw bad("INVALID_MEDIA_CANDIDATE_TYPE", "媒体候选类型不正确");
    }
    MediaCandidate resolved = niumediaMediaService.resolveCandidate(submitted.candidateKey());
    if (resolved == null) {
      throw bad("MEDIA_SELECTION_EXPIRED", "媒体候选已失效，请重新筛选后再继续");
    }
    return resolved;
  }

  private MediaCandidate normalizeManualCandidate(MediaCandidate submitted) {
    if (blank(submitted.displayName())) {
      throw bad("INVALID_MEDIA_CANDIDATE", "请填写拟邀媒体名称");
    }
    String seed = UUID.randomUUID().toString().replace("-", "");
    String reporterName = blank(submitted.reporterName()) ? null : submitted.reporterName().trim();
    return new MediaCandidate(
        "MANUAL:" + seed,
        "MANUAL",
        "MANUAL-" + seed,
        submitted.displayName().trim(),
        reporterName == null ? null : "MANUAL-REPORTER-" + seed,
        reporterName,
        "人工补充（待核定）",
        blank(submitted.province()) ? null : submitted.province().trim(),
        blank(submitted.city()) ? null : submitted.city().trim(),
        null,
        null,
        List.of(),
        null,
        true,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public List<Map<String, Object>> publishPlans(Long projectId) {
    AuthPrincipal user = CurrentUser.get();
    requireProject(user, projectId);
    if ("CUSTOMER".equals(user.role())) {
      String requestedService = repository.projectRequestedService(projectId);
      if (!Set.of("MEDIA_PR", "DIRECT_PUBLISHING").contains(requestedService)) {
        return List.of();
      }
      return customerFieldRows(
          repository.publishPlansForService(projectId, requestedService),
          CUSTOMER_PUBLISH_PLAN_FIELDS);
    }
    List<Map<String, Object>> plans = repository.publishPlans(projectId);
    return plans;
  }

  @Transactional
  public Map<String, Object> confirmPublishPlan(String planNo) {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    if (blank(planNo)) throw bad("PUBLISH_PLAN_NO_REQUIRED", "请提供发布计划编号");
    Map<String, Object> plan = repository.lockPublishPlanForUpdateByNo(planNo.trim());
    if (plan.isEmpty()) throw notFound("发布计划不存在");
    Long planId = number(plan.get("id"));
    Long projectId = number(plan.get("projectId"));
    requireProject(user, projectId);
    String status = string(plan.get("status"));
    boolean alreadyConfirmed = Set.of("CONFIRMED", "EXECUTING", "COMPLETED").contains(status);
    if (!alreadyConfirmed && !"WAITING_CONFIRMATION".equals(status)) {
      throw new BusinessException("PLAN_NOT_CONFIRMABLE", "当前发布计划不能提交核验", HttpStatus.CONFLICT);
    }

    List<Map<String, Object>> items = repository.publishPlanItems(planId);
    if (items.isEmpty()) throw new BusinessException("EMPTY_PUBLISH_PLAN", "发布计划没有可执行项目", HttpStatus.CONFLICT);
    Set<String> types = new LinkedHashSet<>();
    for (Map<String, Object> item : items) {
      types.add(string(item.get("channelType")));
    }
    String requestedService = repository.projectRequestedService(projectId);
    if (types.size() != 1
        || !Set.of("MEDIA_PR", "DIRECT_PUBLISHING").contains(requestedService)
        || !types.contains(requestedService)) {
      throw new BusinessException(
          "PUBLISH_PLAN_SERVICE_MISMATCH",
          "该发布计划与项目服务不一致，不能提交核验，请重新建立对应服务项目",
          HttpStatus.CONFLICT);
    }
    if (alreadyConfirmed) {
      return Map.of("status", status, "taskNos", repository.publishPlanTaskNos(planId),
          "message", "发布计划已提交项目核验，无需重复提交");
    }
    for (Map<String, Object> item : items) {
      String channelType = string(item.get("channelType"));
      boolean manualMediaPr = "MEDIA_PR".equals(channelType) && item.get("channelId") == null;
      if (!manualMediaPr && !"ACTIVE".equals(string(item.get("channelStatus")))) {
        throw new BusinessException("CHANNEL_NOT_ACTIVE", "计划中有渠道已停用，请重新选择", HttpStatus.CONFLICT);
      }
      if ("DIRECT_PUBLISHING".equals(channelType) && !Boolean.TRUE.equals(item.get("quoteUsable"))) {
        throw new BusinessException("PLAN_PRICE_EXPIRED", "计划中有报价已失效，请刷新价格后重新创建计划", HttpStatus.CONFLICT);
      }
    }
    Long manuscriptId = plan.get("manuscriptId") == null ? null : number(plan.get("manuscriptId"));
    Long manuscriptVersionId = plan.get("manuscriptVersionId") == null ? null : number(plan.get("manuscriptVersionId"));
    boolean approvedManuscriptRequired = types.contains("DIRECT_PUBLISHING") || Boolean.TRUE.equals(plan.get("exclusiveMediaPr"));
    if (manuscriptId != null && manuscriptVersionId != null) {
      Map<String, Object> manuscript = repository.manuscriptContext(manuscriptId, manuscriptVersionId);
      if (manuscript.isEmpty() || !projectId.equals(number(manuscript.get("project_id")))) {
        throw new BusinessException("MANUSCRIPT_PROJECT_MISMATCH",
            "稿件定稿版本不属于当前项目，请重新建立发布计划", HttpStatus.CONFLICT);
      }
      if (!manuscriptVersionId.equals(number(manuscript.get("approved_version_id"))) ||
          !"APPROVED".equals(string(manuscript.get("version_status")))) {
        throw new BusinessException("MANUSCRIPT_NOT_APPROVED", "计划引用的定稿版本已失效，请重新创建计划", HttpStatus.CONFLICT);
      }
    } else if (approvedManuscriptRequired) {
      throw new BusinessException("MANUSCRIPT_NOT_APPROVED", "当前计划必须绑定已确认的定稿版本", HttpStatus.CONFLICT);
    }
    boolean activeLock = manuscriptId != null && repository.hasActiveLock(manuscriptId);
    if (activeLock && types.contains("DIRECT_PUBLISHING")) {
      throw new BusinessException("MANUSCRIPT_LOCKED",
          "当前定稿版本存在未完成的既有发布安排，暂不能确认直编发稿，请联系项目负责人确认。", HttpStatus.CONFLICT);
    }
    if (Boolean.TRUE.equals(plan.get("exclusiveMediaPr"))) {
      OffsetDateTime expiresAt = offsetDateTime(plan.get("lockExpiresAt"));
      if (expiresAt == null || !expiresAt.isAfter(OffsetDateTime.now())) {
        throw new BusinessException("LOCK_EXPIRED", "当前历史计划已超过可核验期限，请联系项目负责人重新确认。", HttpStatus.CONFLICT);
      }
      if (!activeLock) repository.createMediaPrLock(user, manuscriptId, manuscriptVersionId, expiresAt);
    }

    for (Map<String, Object> item : items) repository.createPublishTaskFromPlan(user, item);
    repository.markPublishPlanConfirmed(user, planId, projectId, manuscriptId);
    // A client confirmation only establishes the internal task and a reviewable plan.  It is not
    // evidence that a media invitation has been sent or a channel has accepted a submission.
    return Map.of("status", "CONFIRMED", "taskNos", repository.publishPlanTaskNos(planId),
        "message", "发布计划已提交项目核验，任务已建立");
  }

  public PageResult<Map<String, Object>> tasks(String status, String scope, String channelType, int page, int pageSize) {
    AuthPrincipal user = CurrentUser.get();
    if (!blank(scope) && !TASK_SCOPES.contains(scope)) {
      throw bad("INVALID_TASK_SCOPE", "任务筛选范围不正确");
    }
    int[] p = page(page, pageSize);
    List<Map<String, Object>> items = repository.tasks(user, status, scope, channelType, p[1], p[2]);
    if ("CUSTOMER".equals(user.role())) {
      items = customerFieldRows(items, CUSTOMER_PUBLISH_TASK_FIELDS);
    }
    return new PageResult<>(items, repository.tasksCount(user, status, scope, channelType), p[0], p[1]);
  }

  public Map<String, Object> task(Long taskId) {
    AuthPrincipal user = CurrentUser.requireRole("PUBLISH_OPERATOR", "PLATFORM_ADMIN");
    Map<String, Object> task = repository.task(taskId);
    if (task.isEmpty()) throw notFound("发布任务不存在");
    requireProject(user, number(task.get("projectId")));
    return task;
  }

  /**
   * Customers use the public task number rather than an internal task primary key.  This keeps
   * customer API links, confirmation responses, and client-side acceptance actions auditable
   * without disclosing the operational database identifier.
   */
  public Map<String, Object> customerTask(String taskNo) {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    Map<String, Object> task = repository.taskByNo(taskNo);
    if (task.isEmpty()) throw notFound("发布任务不存在");
    requireProject(user, number(task.get("projectId")));
    return customerFieldRow(task, CUSTOMER_PUBLISH_TASK_FIELDS);
  }

  @Transactional
  public Map<String, Object> updateTask(Long taskId, UpdateTaskRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PUBLISH_OPERATOR", "PLATFORM_ADMIN");
    if (!OPERATOR_TASK_STATES.contains(request.status())) {
      throw bad("INVALID_TASK_STATUS", "该状态不能从执行面板直接设置");
    }
    if ("EXCEPTION".equals(request.status()) && blank(request.exceptionReason())) {
      throw bad("EXCEPTION_REASON_REQUIRED", "请填写异常原因");
    }
    Map<String, Object> task = repository.lockTaskForUpdate(taskId);
    if (task.isEmpty()) throw notFound("发布任务不存在");
    if (!repository.canOperateTask(user, taskId)) throw forbidden();
    String currentStatus = string(task.get("status"));
    requireMutableTask(currentStatus);
    if (!repository.updateTask(
        user, taskId, currentStatus, request.status(),
        request.executionNote(), request.exceptionReason())) {
      throw taskStateChanged();
    }
    return Map.of("taskId", taskId, "status", request.status());
  }

  /**
   * A task being created only means that the customer confirmed a list.  The invitation timestamp
   * is written here, by an operator, when an invitation is actually sent; it is never inferred
   * from plan confirmation or task assignment.
   */
  @Transactional
  public Map<String, Object> updateMediaInvitation(
      Long taskId, UpdateMediaInvitationRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PUBLISH_OPERATOR", "PLATFORM_ADMIN");
    String nextStatus = request.status().trim().toUpperCase();
    if (!MEDIA_INVITATION_PROGRESS_STATES.contains(nextStatus)) {
      throw bad("INVALID_MEDIA_INVITATION_STATUS", "媒体沟通状态不正确");
    }
    Map<String, Object> task = repository.lockTaskForUpdate(taskId);
    if (task.isEmpty()) throw notFound("发布任务不存在");
    if (!repository.canOperateTask(user, taskId)) throw forbidden();
    Map<String, Object> invitation = repository.mediaInvitationForTask(taskId);
    if (invitation.isEmpty()) throw notFound("媒体邀请记录不存在");
    String currentStatus = string(invitation.get("status"));
    String taskStatus = mediaInvitationTaskStatus(nextStatus);
    if (currentStatus.equals(nextStatus)) {
      return Map.of(
          "taskId", taskId,
          "status", currentStatus,
          "taskStatus", taskStatus);
    }
    requireMutableTask(string(task.get("status")));
    if (!canAdvanceMediaInvitation(currentStatus, nextStatus)) {
      throw new BusinessException(
          "INVALID_MEDIA_INVITATION_TRANSITION",
          "请先登记已发出邀请，再记录媒体回复或到场情况",
          HttpStatus.CONFLICT);
    }
    if (blank(request.note())) {
      throw bad("MEDIA_INVITATION_NOTE_REQUIRED", "请填写本次媒体沟通的事实说明");
    }
    if (!repository.updateMediaInvitation(user, taskId, nextStatus, request.note())) {
      throw notFound("媒体邀请记录不存在");
    }
    return Map.of(
        "taskId", taskId,
        "status", nextStatus,
        "taskStatus", taskStatus);
  }

  private String mediaInvitationTaskStatus(String invitationStatus) {
    return Set.of("DECLINED", "NOT_PROCEEDING").contains(invitationStatus)
        ? "NOT_PROCEEDING" : "IN_PROGRESS";
  }

  private boolean canAdvanceMediaInvitation(String currentStatus, String nextStatus) {
    return switch (currentStatus) {
      case "PENDING" -> Set.of("INVITED", "NOT_PROCEEDING").contains(nextStatus);
      case "INVITED" -> Set.of("RESPONDED", "DECLINED", "ATTENDING", "NOT_PROCEEDING").contains(nextStatus);
      case "RESPONDED" -> Set.of("DECLINED", "ATTENDING", "NOT_PROCEEDING").contains(nextStatus);
      case "ATTENDING" -> "NOT_PROCEEDING".equals(nextStatus);
      default -> false;
    };
  }

  @Transactional
  public Map<String, Object> updateConferenceProject(
      Long projectId, UpdateConferenceProjectRequest request) {
    AuthPrincipal user = CurrentUser.get();
    requireProject(user, projectId);
    if (!repository.hasConferenceProject(projectId)) throw notFound("新闻发布会项目不存在");
    if (!blank(request.conferenceType()) && !CONFERENCE_TYPES.contains(request.conferenceType())) {
      throw bad("INVALID_CONFERENCE_TYPE", "发布会类型不正确");
    }
    if (!blank(request.conferenceFormat()) && !CONFERENCE_FORMATS.contains(request.conferenceFormat())) {
      throw bad("INVALID_CONFERENCE_FORMAT", "举办形式不正确");
    }
    if (!blank(request.agendaStatus()) && !CONFERENCE_AGENDA_STATES.contains(request.agendaStatus())) {
      throw bad("INVALID_AGENDA_STATUS", "议程准备情况不正确");
    }
    if (!blank(request.venueStatus()) && !CONFERENCE_VENUE_STATES.contains(request.venueStatus())) {
      throw bad("INVALID_VENUE_STATUS", "场地确认情况不正确");
    }
    if (blank(request.contactName())) throw bad("CONFERENCE_CONTACT_REQUIRED", "请填写会务联系人");
    if (blank(request.contactMobile()) || !request.contactMobile().matches("1[3-9]\\d{9}")) {
      throw bad("CONFERENCE_MOBILE_INVALID", "请填写有效的会务联系人手机号");
    }
    if (!repository.updateConferenceProject(user, projectId, request)) {
      throw notFound("新闻发布会项目不存在");
    }
    return Map.of("projectId", projectId, "message", "发布会资料已保存");
  }

  @Transactional
  public Map<String, Object> updateConferenceWorkItem(
      Long projectId, Long itemId, UpdateConferenceWorkItemRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PUBLISH_OPERATOR", "PLATFORM_ADMIN");
    if (!CONFERENCE_WORK_ITEM_STATES.contains(request.status())) {
      throw bad("INVALID_CONFERENCE_WORK_ITEM_STATUS", "统筹事项状态不正确");
    }
    if (!CONFERENCE_WORK_ITEM_STATES.contains(request.expectedStatus())) {
      throw bad("INVALID_CONFERENCE_WORK_ITEM_STATUS", "统筹事项原状态不正确");
    }
    if (Set.of("BLOCKED", "NEEDS_INFO").contains(request.status()) && blank(request.note())) {
      throw bad("CONFERENCE_WORK_ITEM_NOTE_REQUIRED", "标记受阻或需补充时请说明原因");
    }
    if (request.assignedOperatorId() != null) {
      if (!"PLATFORM_ADMIN".equals(user.role())) {
        throw forbidden();
      }
      if (!repository.operatorExists(request.assignedOperatorId())) {
        throw bad("INVALID_OPERATOR", "所选执行人员不存在或账号不可用");
      }
    }
    Map<String, Object> workItem = repository.lockConferenceWorkItemForUpdate(user, projectId, itemId);
    if (workItem.isEmpty()) throw forbidden();
    String currentStatus = string(workItem.get("status"));
    if (!request.expectedStatus().equals(currentStatus)) {
      throw conferenceWorkItemStateChanged();
    }
    if ("COMPLETED".equals(currentStatus)) {
      throw new BusinessException(
          "CONFERENCE_WORK_ITEM_FINALIZED", "该统筹事项已完成，不能重新打开或修改", HttpStatus.CONFLICT);
    }
    if (!canAdvanceConferenceWorkItem(currentStatus, request.status())) {
      throw new BusinessException(
          "INVALID_CONFERENCE_WORK_ITEM_TRANSITION",
          "请按事项当前进展更新状态，不能回退已登记的执行进度", HttpStatus.CONFLICT);
    }
    if (!repository.updateConferenceWorkItem(
        user, projectId, itemId, request.expectedStatus(), request.status(), request.note(),
        request.dueAt(), request.assignedOperatorId())) {
      throw conferenceWorkItemStateChanged();
    }
    return Map.of("projectId", projectId, "itemId", itemId, "status", request.status());
  }

  @Transactional
  public Map<String, Object> updateConferenceMediaCandidate(
      Long projectId, Long candidateId, UpdateConferenceMediaCandidateRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PUBLISH_OPERATOR", "PLATFORM_ADMIN");
    requireProject(user, projectId);
    if (!CONFERENCE_MEDIA_CANDIDATE_STATES.contains(request.status())) {
      throw bad("INVALID_MEDIA_CANDIDATE_STATUS", "媒体候选状态不正确");
    }
    if (!CONFERENCE_MEDIA_CANDIDATE_STATES.contains(request.expectedStatus())) {
      throw bad("INVALID_MEDIA_CANDIDATE_STATUS", "媒体候选原状态不正确");
    }
    Map<String, Object> candidate =
        repository.lockConferenceMediaCandidateForUpdate(user, projectId, candidateId);
    if (candidate.isEmpty()) throw forbidden();
    String currentStatus = string(candidate.get("status"));
    if (!request.expectedStatus().equals(currentStatus)) {
      throw conferenceMediaCandidateStateChanged();
    }
    if (Set.of("DECLINED", "ATTENDING", "NOT_PROCEEDING").contains(currentStatus)
        && !request.status().equals(currentStatus)) {
      throw new BusinessException(
          "CONFERENCE_MEDIA_CANDIDATE_FINALIZED",
          "该媒体候选的邀约结果已登记，不能重新修改状态",
          HttpStatus.CONFLICT);
    }
    if (!canAdvanceConferenceMediaCandidate(currentStatus, request.status())) {
      throw new BusinessException(
          "INVALID_CONFERENCE_MEDIA_CANDIDATE_TRANSITION",
          "请按候选、确认邀约、发出邀请和回复结果的顺序登记进度",
          HttpStatus.CONFLICT);
    }
    if (!request.status().equals(currentStatus)
        && Set.of("RESPONDED", "DECLINED", "ATTENDING", "NOT_PROCEEDING").contains(request.status())
        && blank(request.note())) {
      throw bad(
          "CONFERENCE_MEDIA_CANDIDATE_NOTE_REQUIRED",
          "登记回复、婉拒、确认到场或暂不推进时，请补充联系记录");
    }
    if (!repository.updateConferenceMediaCandidate(
        user, projectId, candidateId, request.expectedStatus(), request.status(), request.note())) {
      throw conferenceMediaCandidateStateChanged();
    }
    return Map.of("candidateId", candidateId, "status", request.status());
  }

  @Transactional
  public Map<String, Object> submitResult(Long taskId, SubmitResultRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PUBLISH_OPERATOR", "PLATFORM_ADMIN");
    String resultUrl = request.url().trim();
    if (!validResultUrl(resultUrl)) {
      throw bad("INVALID_RESULT_URL", "请填写可核验的 http 或 https 成果链接");
    }
    if (request.publishedAt() != null
        && request.publishedAt().isAfter(OffsetDateTime.now().plusMinutes(5))) {
      throw bad("RESULT_TIME_INVALID", "成果发布时间不能晚于当前时间");
    }
    Map<String, Object> task = repository.lockTaskForUpdate(taskId);
    if (task.isEmpty()) throw notFound("发布任务不存在");
    if (!repository.canOperateTask(user, taskId)) throw forbidden();
    String currentStatus = string(task.get("status"));
    if ("CLIENT_ACCEPTED".equals(currentStatus)) {
      throw new BusinessException(
          "TASK_ALREADY_ACCEPTED", "客户已验收，不能再次修改任务成果", HttpStatus.CONFLICT);
    }
    if ("NOT_PROCEEDING".equals(currentStatus)) {
      throw new BusinessException(
          "TASK_NOT_PROCEEDING", "该媒体邀请已结束，不能再补录发布成果", HttpStatus.CONFLICT);
    }
    if ("COMPLETED".equals(currentStatus) || repository.hasVerifiedResultForTask(taskId)) {
      throw new BusinessException(
          "TASK_RESULT_ALREADY_SUBMITTED", "任务已有已核验成果，不能重复提交", HttpStatus.CONFLICT);
    }
    requireMediaInvitationBeforeResult(taskId, task);
    requireSupplierFulfillmentBeforeResult(taskId, task);
    if (!repository.submitResult(
        user, taskId, currentStatus, request.title().trim(), resultUrl,
        request.publishedAt(), trimToNull(request.note()))) {
      throw taskStateChanged();
    }
    return Map.of("taskId", taskId, "status", "COMPLETED");
  }

  @Transactional
  public Map<String, Object> acceptTask(String taskNo) {
    AuthPrincipal user = CurrentUser.requireRole("CUSTOMER");
    Map<String, Object> task = repository.lockTaskByNoForUpdate(taskNo);
    if (task.isEmpty()) throw notFound("发布任务不存在");
    requireProject(user, number(task.get("projectId")));
    String currentStatus = string(task.get("status"));
    if ("CLIENT_ACCEPTED".equals(currentStatus)) {
      return Map.of("taskNo", string(task.get("taskNo")), "status", "CLIENT_ACCEPTED");
    }
    if (!"COMPLETED".equals(currentStatus)) {
      throw new BusinessException(
          "TASK_NOT_ACCEPTABLE", "任务尚未完成，暂不能验收", HttpStatus.CONFLICT);
    }
    Long taskId = number(task.get("id"));
    if (!repository.hasVerifiedResultForTask(taskId)) {
      throw new BusinessException(
          "TASK_RESULT_REQUIRED", "尚无已核验成果，暂不能验收", HttpStatus.CONFLICT);
    }
    if (!repository.acceptTask(user, taskId)) {
      throw taskStateChanged();
    }
    return Map.of("taskNo", string(task.get("taskNo")), "status", "CLIENT_ACCEPTED");
  }

  public List<Map<String, Object>> operators() {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    return repository.operators();
  }

  public List<Map<String, Object>> writerProfiles() {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    return repository.writerProfiles();
  }

  public List<Map<String, Object>> writingAssignments(String status) {
    AuthPrincipal user = CurrentUser.requireRole("PUBLISH_OPERATOR", "PLATFORM_ADMIN");
    if (status != null && !status.isBlank() && !WRITING_ASSIGNMENT_STATES.contains(status)) {
      throw bad("INVALID_WRITING_ASSIGNMENT_STATUS", "云采写派单状态不正确");
    }
    return repository.writingAssignments(user, status);
  }

  public Map<String, Object> offerWritingAssignment(
      Long assignmentId, OfferWritingAssignmentRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    WritingAssignmentOfferOutcome outcome;
    try {
      outcome = repository.offerWritingAssignment(
          user, assignmentId, request.writerProfileId(), request.distanceKm());
    } catch (DataIntegrityViolationException ex) {
      if (hasWritingAssignmentDatabaseMessage(
          ex, "writing assignment member distance is required when writer service radius is configured")) {
        throw new BusinessException("WRITING_ASSIGNMENT_DISTANCE_REQUIRED",
            "该写手已设置服务半径，请先填写经人工核验的服务距离", HttpStatus.CONFLICT);
      }
      if (hasWritingAssignmentDatabaseMessage(
          ex, "writing assignment member distance exceeds writer service radius")) {
        throw new BusinessException("WRITING_ASSIGNMENT_OUT_OF_SERVICE_RADIUS",
            "填写的服务距离超出该写手服务半径，请更换写手或调整安排", HttpStatus.CONFLICT);
      }
      throw ex;
    }
    if (outcome != WritingAssignmentOfferOutcome.OFFERED) {
      String code = switch (outcome) {
        case DISTANCE_REQUIRED -> "WRITING_ASSIGNMENT_DISTANCE_REQUIRED";
        case OUT_OF_SERVICE_RADIUS -> "WRITING_ASSIGNMENT_OUT_OF_SERVICE_RADIUS";
        default -> "WRITING_ASSIGNMENT_NOT_OFFERABLE";
      };
      String message = switch (outcome) {
        case NO_OPEN_SLOT -> "所需写手名额已满，无需继续派单";
        case WRITER_UNAVAILABLE -> "该写手当前不可接单，请选择可用写手";
        case DISTANCE_REQUIRED -> "该写手已设置服务半径，请先填写经人工核验的服务距离";
        case OUT_OF_SERVICE_RADIUS -> "填写的服务距离超出该写手服务半径，请更换写手或调整安排";
        case DUPLICATE_WRITER -> "该写手已在本任务名单中，请选择其他写手";
        case SCHEDULE_CONFLICT -> "该写手在本服务时段已有已确认任务，请选择其他写手或调整时间";
        case NOT_OFFERABLE -> "派单不存在、状态已变化或缺少可核验的服务时段";
        case OFFERED -> "";
      };
      throw new BusinessException(code, message, HttpStatus.CONFLICT);
    }
    return Map.of("assignmentId", assignmentId, "memberStatus", "OFFERED",
        "writerProfileId", request.writerProfileId());
  }

  public Map<String, Object> respondWritingAssignment(
      Long assignmentId, RespondWritingAssignmentRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PUBLISH_OPERATOR");
    if (!Set.of("ACCEPT", "DECLINE").contains(request.decision())) {
      throw bad("INVALID_WRITING_ASSIGNMENT_DECISION", "请选择接单或拒单");
    }
    if ("DECLINE".equals(request.decision()) && blank(request.note())) {
      throw bad("WRITING_ASSIGNMENT_NOTE_REQUIRED", "拒单时请说明原因，便于重新派单");
    }
    try {
      if (!repository.respondWritingAssignment(user, assignmentId, request.decision(), request.note())) {
        throw new BusinessException("WRITING_ASSIGNMENT_NOT_RESPONDABLE", "派单不存在、已处理或不属于当前账号", HttpStatus.CONFLICT);
      }
    } catch (DataIntegrityViolationException ex) {
      if (isWritingAssignmentScheduleConflict(ex)) {
        throw new BusinessException("WRITER_SCHEDULE_CONFLICT",
            "该时段已有已确认采写任务，请联系平台重新安排写手或服务时间", HttpStatus.CONFLICT);
      }
      throw ex;
    }
    return Map.of("assignmentId", assignmentId,
        "memberStatus", "ACCEPT".equals(request.decision()) ? "ACCEPTED" : "DECLINED");
  }

  private boolean isWritingAssignmentScheduleConflict(DataIntegrityViolationException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof java.sql.SQLException sqlException
          && "23P01".equals(sqlException.getSQLState())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private boolean hasWritingAssignmentDatabaseMessage(
      DataIntegrityViolationException exception, String expectedMessage) {
    Throwable cause = exception;
    while (cause != null) {
      String message = cause.getMessage();
      if (message != null && message.contains(expectedMessage)) return true;
      cause = cause.getCause();
    }
    return false;
  }

  public Map<String, Object> assignProject(Long projectId, AssignProjectRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!repository.assignProject(user, projectId, request.operatorId())) throw notFound("项目不存在");
    return Map.of("projectId", projectId, "operatorId", request.operatorId());
  }

  public Map<String, Object> createChannel(CreateChannelRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!CHANNEL_TYPES.contains(request.channelType())) throw bad("INVALID_CHANNEL_TYPE", "渠道类型不正确");
    if (request.costPrice() != null && request.costPrice().signum() < 0) {
      throw bad("INVALID_COST_PRICE", "内部成本价不能小于零");
    }
    if ("DIRECT_PUBLISHING".equals(request.channelType())) {
      if (request.customerPrice() == null || request.validUntil() == null) {
        throw bad("QUOTE_REQUIRED", "直编发稿渠道须填写客户价和报价有效期");
      }
      if (request.customerPrice().signum() <= 0) {
        throw bad("INVALID_QUOTE_PRICE", "客户服务价必须大于零");
      }
      if (request.costPrice() != null && request.customerPrice().compareTo(request.costPrice()) < 0) {
        throw bad("PRICE_BELOW_COST", "客户服务价不能低于成本价");
      }
      if (!request.validUntil().isAfter(OffsetDateTime.now())) {
        throw bad("INVALID_QUOTE_VALIDITY", "报价有效期必须晚于当前时间");
      }
    }
    return Map.of("channelId", repository.createChannel(user, request));
  }

  public PageResult<Map<String, Object>> adminChannels(
      String type, String status, String keyword, int page, int pageSize) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    if (type != null && !type.isBlank() && !CHANNEL_TYPES.contains(type)) {
      throw bad("INVALID_CHANNEL_TYPE", "渠道类型不正确");
    }
    if (status != null && !status.isBlank() && !CHANNEL_STATES.contains(status)) {
      throw bad("INVALID_CHANNEL_STATUS", "渠道状态不正确");
    }
    int[] p = page(page, pageSize);
    return new PageResult<>(repository.adminChannels(type, status, keyword, p[1], p[2]),
        repository.adminChannelsCount(type, status, keyword), p[0], p[1]);
  }

  public Map<String, Object> updateChannel(Long channelId, UpdateChannelRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!CHANNEL_TYPES.contains(request.channelType())) throw bad("INVALID_CHANNEL_TYPE", "渠道类型不正确");
    if (!CHANNEL_STATES.contains(request.status())) throw bad("INVALID_CHANNEL_STATUS", "渠道状态不正确");
    if (!repository.updateChannel(user, channelId, request)) throw notFound("渠道不存在");
    return Map.of("channelId", channelId, "status", request.status());
  }

  public PageResult<Map<String, Object>> pricingChannels(
      String keyword, String region, String category, String publishForm, String channelStatus,
      String quoteState, int page, int pageSize) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    if (channelStatus != null && !channelStatus.isBlank() && !CHANNEL_STATES.contains(channelStatus)) {
      throw bad("INVALID_CHANNEL_STATUS", "渠道状态不正确");
    }
    if (quoteState != null && !quoteState.isBlank() && !QUOTE_STATES.contains(quoteState)) {
      throw bad("INVALID_QUOTE_STATE", "报价状态不正确");
    }
    int[] p = page(page, pageSize);
    return new PageResult<>(
        repository.pricingChannels(keyword, region, category, publishForm, channelStatus, quoteState, p[1], p[2]),
        repository.pricingChannelsCount(keyword, region, category, publishForm, channelStatus, quoteState), p[0], p[1]);
  }

  public Map<String, Object> pricingSummary() {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    return repository.pricingSummary();
  }

  public List<Map<String, Object>> comparePricing(List<Long> channelIds) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    LinkedHashSet<Long> unique = new LinkedHashSet<>(channelIds == null ? List.of() : channelIds);
    if (unique.size() < 2 || unique.size() > 5 || unique.contains(null)) {
      throw bad("INVALID_COMPARISON_SELECTION", "请选择 2 至 5 个渠道进行比价");
    }
    List<Map<String, Object>> rows = repository.pricingComparison(new ArrayList<>(unique));
    if (rows.size() != unique.size()) throw bad("INVALID_COMPARISON_SELECTION", "所选渠道中存在不可比价的非直编渠道");
    return rows;
  }

  @Transactional
  public Map<String, Object> createQuote(CreateQuoteRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    validateQuoteInput(request.costPrice(), request.customerPrice(), request.validUntil(), request.reason());
    repository.lockPricingChannelForUpdate(request.channelId());
    validatePricingChannel(request.channelId());
    if (request.supplierId() != null
        && !repository.activeSupplierCanServeChannel(request.supplierId(), request.channelId())) {
      throw bad("INVALID_SUPPLIER_CHANNEL", "所选供应商不可用，或尚未关联该渠道");
    }
    BigDecimal costPrice = request.costPrice() == null ? null : request.costPrice().setScale(2, RoundingMode.HALF_UP);
    return repository.replaceDirectQuote(user, request.channelId(), request.supplierId(), costPrice,
        request.customerPrice().setScale(2, RoundingMode.HALF_UP),
        request.validUntil(), request.publicTerms(), request.reason(), "MANUAL");
  }

  @Transactional
  public Map<String, Object> batchAdjustQuotes(
      BatchQuoteAdjustmentRequest request, String idempotencyKey) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    String normalizedIdempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
    if (normalizedIdempotencyKey.isEmpty()) {
      throw bad(
          "IDEMPOTENCY_KEY_REQUIRED",
          "批量调价缺少请求标识，请刷新页面后重试");
    }
    if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalizedIdempotencyKey).matches()) {
      throw bad("INVALID_IDEMPOTENCY_KEY", "请求标识无效，请刷新页面后重试");
    }
    if (request.percentage().compareTo(new BigDecimal("-50")) < 0 || request.percentage().compareTo(new BigDecimal("50")) > 0) {
      throw bad("INVALID_ADJUSTMENT_PERCENTAGE", "批量调整幅度应在 -50% 到 50% 之间");
    }
    if (!request.validUntil().isAfter(OffsetDateTime.now())) throw bad("INVALID_QUOTE_VALIDITY", "报价有效期必须晚于当前时间");
    LinkedHashSet<Long> unique = new LinkedHashSet<>(request.channelIds());
    if (unique.contains(null)) throw bad("INVALID_CHANNEL_SELECTION", "批量调价包含无效渠道");
    if (unique.size() != request.channelIds().size()) throw bad("DUPLICATE_CHANNEL_SELECTION", "批量调价中不能重复选择渠道");
    List<Long> sortedChannelIds = unique.stream().sorted().toList();
    String submissionHash = batchQuoteAdjustmentRequestHash(request, sortedChannelIds);
    Map<String, Object> batch = repository.lockOrCreateQuoteAdjustmentBatch(
        user,
        normalizedIdempotencyKey,
        submissionHash,
        request.percentage().stripTrailingZeros(),
        request.validUntil(),
        trimToNull(request.publicTerms()),
        request.reason().trim(),
        sortedChannelIds.size());
    if (!submissionHash.equals(string(batch.get("submissionHash")))) {
      throw new BusinessException(
          "IDEMPOTENCY_KEY_REUSED",
          "该请求标识已用于另一批调价，请刷新页面后重试",
          HttpStatus.CONFLICT);
    }
    Long batchId = number(batch.get("id"));
    if ("COMPLETED".equals(string(batch.get("status")))) {
      List<Map<String, Object>> existingItems =
          repository.quoteAdjustmentBatchItems(batchId);
      if (existingItems.size() != sortedChannelIds.size()
          || number(batch.get("adjustedCount")) != sortedChannelIds.size()) {
        throw new BusinessException(
            "BATCH_ADJUSTMENT_INCOMPLETE",
            "该批调价记录不完整，请暂停继续调价并联系系统管理员",
            HttpStatus.CONFLICT);
      }
      return Map.of("adjustedCount", existingItems.size(), "items", existingItems);
    }
    if (!Boolean.TRUE.equals(batch.get("created"))) {
      throw new BusinessException(
          "BATCH_ADJUSTMENT_INCOMPLETE",
          "该批调价尚未形成完整记录，请暂停继续调价并联系系统管理员",
          HttpStatus.CONFLICT);
    }
    // Lock channel rows in a stable order before reading or replacing any quote.  This serializes
    // first-quote creation as well as later adjustments and avoids deadlocks between overlapping
    // batch requests.
    sortedChannelIds.forEach(repository::lockPricingChannelForUpdate);
    List<Map<String, Object>> changes = new ArrayList<>();
    for (Long channelId : sortedChannelIds) {
      Map<String, Object> channel = validatePricingChannel(channelId);
      Object value = channel.get("customerPrice");
      if (!(value instanceof BigDecimal price)) throw bad("PRICE_UNAVAILABLE", "所选渠道存在无可用基准报价的条目");
      BigDecimal next = price.multiply(BigDecimal.ONE.add(request.percentage().movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
      if (next.signum() <= 0) throw bad("INVALID_ADJUSTED_PRICE", "批量调整后客户服务价必须大于零");
      BigDecimal costPrice = channel.get("costPrice") instanceof BigDecimal cost ? cost : null;
      if (costPrice != null && next.compareTo(costPrice) < 0) {
        throw bad("PRICE_BELOW_COST", "批量调整后客户服务价不能低于成本价");
      }
      changes.add(repository.replaceDirectQuote(user, channelId, null, costPrice, next, request.validUntil(),
          request.publicTerms(), request.reason(), "BATCH_PERCENT", batchId));
    }
    repository.completeQuoteAdjustmentBatch(user, batchId, changes.size());
    return Map.of("adjustedCount", changes.size(), "items", changes);
  }

  private String batchQuoteAdjustmentRequestHash(
      BatchQuoteAdjustmentRequest request, List<Long> sortedChannelIds) {
    String canonical = sortedChannelIds.stream()
        .map(String::valueOf)
        .collect(java.util.stream.Collectors.joining(","))
        + "|" + request.percentage().stripTrailingZeros().toPlainString()
        + "|" + request.validUntil().toInstant()
        + "|" + (trimToNull(request.publicTerms()) == null
            ? "" : trimToNull(request.publicTerms()))
        + "|" + request.reason().trim();
    return sha256(canonical);
  }

  public List<Map<String, Object>> quoteAdjustments(Long channelId) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    findPricingChannel(channelId);
    return repository.quoteAdjustments(channelId, 30);
  }

  private Map<String, Object> validatePricingChannel(Long channelId) {
    Map<String, Object> channel = findPricingChannel(channelId);
    if (!"ACTIVE".equals(channel.get("channelStatus"))) {
      throw bad("CHANNEL_NOT_ACTIVE", "请先将渠道设为可用状态，再调整报价");
    }
    return channel;
  }

  private Map<String, Object> findPricingChannel(Long channelId) {
    Map<String, Object> channel = repository.pricingChannel(channelId);
    if (channel.isEmpty()) throw notFound("渠道不存在");
    if (!"DIRECT_PUBLISHING".equals(channel.get("channelType"))) {
      throw bad("DIRECT_PUBLISHING_ONLY", "仅直编发稿渠道可使用报价管理");
    }
    return channel;
  }

  private void validateQuoteInput(
      BigDecimal costPrice, BigDecimal customerPrice, OffsetDateTime validUntil, String reason) {
    if (costPrice != null && costPrice.signum() < 0) throw bad("INVALID_COST_PRICE", "成本价不能小于零");
    if (customerPrice == null || customerPrice.signum() <= 0) throw bad("INVALID_QUOTE_PRICE", "客户服务价必须大于零");
    if (costPrice != null && customerPrice.compareTo(costPrice) < 0) {
      throw bad("PRICE_BELOW_COST", "客户服务价不能低于成本价");
    }
    if (validUntil == null || !validUntil.isAfter(OffsetDateTime.now())) {
      throw bad("INVALID_QUOTE_VALIDITY", "报价有效期必须晚于当前时间");
    }
    if (blank(reason)) throw bad("QUOTE_REASON_REQUIRED", "请填写本次调价原因");
  }

  public PageResult<Map<String, Object>> suppliers(
      String type, String status, String keyword, int page, int pageSize) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!blank(type) && !SUPPLIER_TYPES.contains(type)) {
      throw bad("INVALID_SUPPLIER_TYPE", "供应商类型不正确");
    }
    if (!blank(status) && !SUPPLIER_STATES.contains(status)) {
      throw bad("INVALID_SUPPLIER_STATUS", "供应商状态不正确");
    }
    int[] p = page(page, pageSize);
    return new PageResult<>(
        repository.suppliers(type, status, keyword, p[1], p[2]),
        repository.suppliersCount(type, status, keyword), p[0], p[1]);
  }

  public List<Map<String, Object>> supplierOptions() {
    return supplierOptions(null);
  }

  public List<Map<String, Object>> supplierOptions(Long channelId) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    if (channelId != null && channelId <= 0) {
      throw bad("INVALID_CHANNEL", "渠道不正确");
    }
    return channelId == null
        ? repository.supplierOptions()
        : repository.supplierOptionsForChannel(channelId);
  }

  @Transactional
  public Map<String, Object> createSupplier(CreateSupplierRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    validateSupplier(request.supplierType(), request.contactPhone());
    Long supplierId = repository.createSupplier(user, request);
    return Map.of("supplierId", supplierId, "message", "供应商已创建");
  }

  @Transactional
  public Map<String, Object> updateSupplier(Long supplierId, UpdateSupplierRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    validateSupplier(request.supplierType(), request.contactPhone());
    if (!SUPPLIER_STATES.contains(request.status())) {
      throw bad("INVALID_SUPPLIER_STATUS", "供应商状态不正确");
    }
    if (!repository.updateSupplier(user, supplierId, request)) throw notFound("供应商不存在");
    return Map.of("supplierId", supplierId, "status", request.status());
  }

  public PageResult<Map<String, Object>> supplierChannels(
      Long supplierId, Long channelId, int page, int pageSize) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    int[] p = page(page, pageSize);
    return new PageResult<>(
        repository.supplierChannels(supplierId, channelId, p[1], p[2]),
        repository.supplierChannelsCount(supplierId, channelId), p[0], p[1]);
  }

  @Transactional
  public Map<String, Object> assignSupplierChannel(AssignSupplierChannelRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!repository.activeSupplierExists(request.supplierId())) {
      throw bad("INVALID_SUPPLIER", "供应商不存在或不可用");
    }
    if (!repository.channelExists(request.channelId())) throw notFound("渠道不存在");
    repository.assignSupplierChannel(user, request);
    return Map.of(
        "supplierId", request.supplierId(),
        "channelId", request.channelId(),
        "message", "供应商渠道关系已保存");
  }

  public PageResult<Map<String, Object>> supplierOrders(
      String status, Long supplierId, String keyword, int page, int pageSize) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!blank(status) && !SUPPLIER_ORDER_STATES.contains(status)) {
      throw bad("INVALID_SUPPLIER_ORDER_STATUS", "供应商订单状态不正确");
    }
    int[] p = page(page, pageSize);
    return new PageResult<>(
        repository.supplierOrders(status, supplierId, keyword, p[1], p[2]),
        repository.supplierOrdersCount(status, supplierId, keyword), p[0], p[1]);
  }

  public Map<String, Object> supplierOrderSummary() {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    return repository.supplierOrderSummary();
  }

  @Transactional
  public Map<String, Object> updateSupplierOrder(
      Long supplierOrderId, UpdateSupplierOrderRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!SUPPLIER_ORDER_STATES.contains(request.status())) {
      throw bad("INVALID_SUPPLIER_ORDER_STATUS", "供应商订单状态不正确");
    }
    if (!SUPPLIER_FULFILLMENT_MODES.contains(request.fulfillmentMode())) {
      throw bad("INVALID_SUPPLIER_FULFILLMENT_MODE", "供应商履约记录方式不正确");
    }
    Map<String, Object> order = repository.supplierOrder(supplierOrderId);
    if (order.isEmpty()) throw notFound("供应商订单不存在");
    String previousStatus = string(order.get("status"));
    if (!supplierOrderTransitionAllowed(previousStatus, request.status())) {
      throw new BusinessException(
          "INVALID_SUPPLIER_ORDER_TRANSITION",
          "供应商订单不能从当前状态变更为所选状态",
          HttpStatus.CONFLICT);
    }
    Long supplierId = request.supplierId() != null
        ? request.supplierId()
        : order.get("supplierId") == null ? null : number(order.get("supplierId"));
    if (supplierId != null
        && !repository.activeSupplierCanServeChannel(supplierId, number(order.get("channelId")))) {
      throw bad("INVALID_SUPPLIER_CHANNEL", "所选供应商不可用，或尚未关联该渠道");
    }
    if (!"PENDING_SUBMISSION".equals(request.status()) && supplierId == null) {
      throw bad("SUPPLIER_REQUIRED", "提交供应商订单前须先选择供应商");
    }
    if ("PENDING_SUBMISSION".equals(request.status())) {
      if (!"UNCONFIRMED".equals(request.fulfillmentMode())
          || !blank(request.externalOrderNo())
          || !blank(request.submissionEvidenceReference())) {
        throw bad(
            "SUPPLIER_ORDER_PENDING_CONTEXT_INVALID",
            "重新进入待提交时须清除上游订单号和履约凭据；既有记录会保留在状态轨迹中");
      }
    } else if (!"API".equals(request.fulfillmentMode()) && !blank(request.externalOrderNo())) {
      throw bad(
          "SUPPLIER_EXTERNAL_ORDER_MODE_INVALID",
          "上游订单号只适用于已验收的接口回执；人工提交请仅登记受控凭据位置");
    }
    if ("EXCEPTION".equals(request.status()) && blank(request.exceptionReason())) {
      throw bad("SUPPLIER_ORDER_EXCEPTION_REQUIRED", "请填写供应商订单异常原因");
    }
    if (SUPPLIER_EVIDENCE_REQUIRED_STATES.contains(request.status())) {
      if ("UNCONFIRMED".equals(request.fulfillmentMode())
          || blank(request.submissionEvidenceReference())) {
        throw bad(
            "SUPPLIER_ORDER_EVIDENCE_REQUIRED",
            "标记为已提交或执行中之前，必须选择人工凭据或受控接口回执并填写证据位置");
      }
      if ("API".equals(request.fulfillmentMode())) {
        if (blank(request.externalOrderNo())) {
          throw bad("SUPPLIER_EXTERNAL_ORDER_REQUIRED", "接口回执必须包含上游订单号");
        }
        if (!integrationAdminService.isSupplierFulfillmentRuntimeReady(supplierId)) {
          throw new BusinessException(
              "SUPPLIER_API_NOT_ACCEPTED",
              "该供应商的生产接口尚未完成授权与履约验收，不能登记为接口提交",
              HttpStatus.CONFLICT);
        }
      }
    }
    repository.updateSupplierOrder(user, supplierOrderId, supplierId, request);
    return Map.of("supplierOrderId", supplierOrderId, "status", request.status());
  }

  public List<Map<String, Object>> supplierOrderHistory(Long supplierOrderId) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!repository.supplierOrderExists(supplierOrderId)) throw notFound("供应商订单不存在");
    return repository.supplierOrderHistory(supplierOrderId);
  }

  @Transactional
  public Map<String, Object> createBusinessInquiry(CreateBusinessInquiryRequest request) {
    if (!INQUIRY_TYPES.contains(request.inquiryType())) {
      throw bad("INVALID_INQUIRY_TYPE", "咨询类型不正确");
    }
    if (!Boolean.TRUE.equals(request.privacyAccepted())) {
      throw bad("PRIVACY_CONSENT_REQUIRED", "请确认本次咨询信息的数据处理说明");
    }
    if (!request.mobile().matches("1[3-9]\\d{9}")) {
      throw bad("INQUIRY_MOBILE_INVALID", "请填写有效的联系人手机号");
    }
    if (repository.recentBusinessInquiryExists(
        request.inquiryType(), request.mobile(), request.companyName())) {
      throw new BusinessException(
          "DUPLICATE_INQUIRY",
          "相同咨询已提交，请勿重复操作",
          HttpStatus.CONFLICT);
    }
    Long inquiryId = repository.createBusinessInquiry(request);
    return Map.of("inquiryId", inquiryId, "message", "咨询已提交，我们会尽快与您联系");
  }

  public PageResult<Map<String, Object>> businessInquiries(
      String status, String type, int page, int pageSize) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!blank(status) && !INQUIRY_STATES.contains(status)) {
      throw bad("INVALID_INQUIRY_STATUS", "咨询状态不正确");
    }
    if (!blank(type) && !INQUIRY_TYPES.contains(type)) {
      throw bad("INVALID_INQUIRY_TYPE", "咨询类型不正确");
    }
    int[] p = page(page, pageSize);
    return new PageResult<>(
        repository.businessInquiries(status, type, p[1], p[2]),
        repository.businessInquiriesCount(status, type), p[0], p[1]);
  }

  @Transactional
  public Map<String, Object> updateBusinessInquiry(
      Long inquiryId, UpdateBusinessInquiryRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!INQUIRY_STATES.contains(request.status())) {
      throw bad("INVALID_INQUIRY_STATUS", "咨询状态不正确");
    }
    if (!repository.updateBusinessInquiry(user, inquiryId, request)) {
      throw notFound("咨询记录不存在");
    }
    return Map.of("inquiryId", inquiryId, "status", request.status());
  }

  private void validateSupplier(String supplierType, String contactPhone) {
    if (!SUPPLIER_TYPES.contains(supplierType)) {
      throw bad("INVALID_SUPPLIER_TYPE", "供应商类型不正确");
    }
    if (!blank(contactPhone)
        && !contactPhone.matches("(?:1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})")) {
      throw bad("INVALID_SUPPLIER_PHONE", "供应商联系电话格式不正确");
    }
  }

  private boolean supplierOrderTransitionAllowed(String from, String to) {
    if (from.equals(to)) return true;
    return switch (from) {
      case "PENDING_SUBMISSION" -> Set.of("SUBMITTED", "CANCELLED").contains(to);
      case "SUBMITTED" -> Set.of("ACCEPTED", "IN_PROGRESS", "EXCEPTION", "CANCELLED").contains(to);
      case "ACCEPTED" -> Set.of("IN_PROGRESS", "EXCEPTION", "CANCELLED").contains(to);
      case "IN_PROGRESS" -> Set.of("COMPLETED", "EXCEPTION", "CANCELLED").contains(to);
      case "EXCEPTION" -> Set.of("PENDING_SUBMISSION", "SUBMITTED", "CANCELLED").contains(to);
      default -> false;
    };
  }

  public PageResult<Map<String, Object>> settlements(String status, int page, int pageSize) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    String normalizedStatus = normalizeOptional(status);
    if (!blank(normalizedStatus) && !SETTLEMENT_STATES.contains(normalizedStatus)) {
      throw bad("INVALID_SETTLEMENT_STATUS", "结算状态不正确");
    }
    int[] p = page(page, pageSize);
    List<Map<String, Object>> items = repository.settlements(normalizedStatus, p[1], p[2]);
    return new PageResult<>(
        items, repository.settlementsCount(normalizedStatus), p[0], p[1]);
  }

  public PageResult<Map<String, Object>> settlementTransactions(
      Long settlementId, String transactionType, String status, int page, int pageSize) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    String normalizedType = normalizeOptional(transactionType);
    String normalizedStatus = normalizeOptional(status);
    if (!blank(normalizedType) && !SETTLEMENT_TRANSACTION_TYPES.contains(normalizedType)) {
      throw bad("INVALID_SETTLEMENT_TRANSACTION_TYPE", "交易类型不正确");
    }
    if (!blank(normalizedStatus) && !SETTLEMENT_TRANSACTION_STATES.contains(normalizedStatus)) {
      throw bad("INVALID_SETTLEMENT_TRANSACTION_STATUS", "交易状态不正确");
    }
    int[] p = page(page, pageSize);
    return new PageResult<>(
        repository.settlementTransactions(
            settlementId, normalizedType, normalizedStatus, p[1], p[2]),
        repository.settlementTransactionsCount(settlementId, normalizedType, normalizedStatus),
        p[0], p[1]);
  }

  @Transactional
  public Map<String, Object> createSettlementTransaction(
      Long settlementId,
      CreateSettlementTransactionRequest request,
      String idempotencyKey) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    String normalizedIdempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
    if (normalizedIdempotencyKey.isEmpty()) {
      throw bad(
          "IDEMPOTENCY_KEY_REQUIRED",
          "登记结算交易时缺少请求标识，请刷新页面后重试");
    }
    if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalizedIdempotencyKey).matches()) {
      throw bad("INVALID_IDEMPOTENCY_KEY", "请求标识无效，请刷新页面后重试");
    }
    String transactionType = request.transactionType().trim().toUpperCase();
    if (!SETTLEMENT_TRANSACTION_TYPES.contains(transactionType)) {
      throw bad("INVALID_SETTLEMENT_TRANSACTION_TYPE", "交易类型不正确");
    }
    BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
    CreateSettlementTransactionRequest normalized = new CreateSettlementTransactionRequest(
        transactionType,
        amount,
        request.occurredAt(),
        trimToNull(request.referenceNo()),
        trimToNull(request.customerNote()),
        trimToNull(request.internalNote()));
    String submissionHash = settlementTransactionRequestHash(normalized);
    if (blank(normalized.referenceNo()) && blank(normalized.customerNote())) {
      throw bad("SETTLEMENT_TRANSACTION_EVIDENCE_REQUIRED", "请填写凭据编号或客户可见说明");
    }
    if (normalized.occurredAt().isAfter(OffsetDateTime.now().plusMinutes(5))) {
      throw bad("SETTLEMENT_TRANSACTION_TIME_INVALID", "交易发生时间不能晚于当前时间");
    }
    Map<String, Object> settlement = repository.lockSettlementForUpdate(settlementId);
    if (settlement.isEmpty()) throw notFound("结算单不存在");
    requireCurrentSettlement(settlement);
    Map<String, Object> existingTransaction = repository.existingSettlementTransaction(
        settlementId, normalizedIdempotencyKey, submissionHash);
    if (!existingTransaction.isEmpty()) {
      return existingTransaction;
    }
    String settlementStatus = string(settlement.get("status"));
    if ("PENDING".equals(settlementStatus)) {
      throw new BusinessException(
          "SETTLEMENT_NOT_CONFIRMED", "请先确认结算单，再登记交易", HttpStatus.CONFLICT);
    }
    if ("CANCELLED".equals(settlementStatus)) {
      throw new BusinessException(
          "SETTLEMENT_CANCELLED", "已取消的结算单不能登记交易", HttpStatus.CONFLICT);
    }
    if ("PAID".equals(settlementStatus) && !"REFUND".equals(transactionType)) {
      throw new BusinessException(
          "SETTLEMENT_ALREADY_CLOSED", "已结清的结算单仅可登记退款", HttpStatus.CONFLICT);
    }

    BigDecimal paidAmount = decimal(settlement.get("paidAmount"));
    BigDecimal outstandingAmount = decimal(settlement.get("outstandingAmount"));
    if ("PAYMENT".equals(transactionType) && amount.compareTo(outstandingAmount) > 0) {
      throw bad("PAYMENT_EXCEEDS_OUTSTANDING", "收款金额不能超过当前待结金额");
    }
    if ("REFUND".equals(transactionType) && amount.compareTo(paidAmount) > 0) {
      throw bad("REFUND_EXCEEDS_PAID", "退款金额不能超过当前实收金额");
    }
    if (Set.of("CREDIT_ADJUSTMENT", "WRITE_OFF").contains(transactionType)
        && amount.compareTo(outstandingAmount) > 0) {
      throw bad("ADJUSTMENT_EXCEEDS_OUTSTANDING", "调整金额不能超过当前待结金额");
    }

    return repository.createSettlementTransaction(
        user,
        settlementId,
        normalized,
        normalizedIdempotencyKey,
        submissionHash);
  }

  private static String settlementTransactionRequestHash(
      CreateSettlementTransactionRequest request) {
    return sha256(request.toString());
  }

  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  @Transactional
  public Map<String, Object> voidSettlementTransaction(
      Long transactionId, VoidSettlementTransactionRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    Map<String, Object> transaction = repository.lockSettlementTransactionForUpdate(transactionId);
    if (transaction.isEmpty()) throw notFound("交易记录不存在");
    requireCurrentSettlement(transaction);
    if ("VOIDED".equals(transaction.get("status"))) {
      throw new BusinessException(
          "SETTLEMENT_TRANSACTION_ALREADY_VOIDED", "该交易记录已经作废", HttpStatus.CONFLICT);
    }
    Long settlementId = number(transaction.get("settlementId"));
    if (!repository.voidSettlementTransaction(
        user, transactionId, settlementId, request.reason().trim())) {
      throw notFound("交易记录不存在");
    }
    return Map.of("transactionId", transactionId, "status", "VOIDED");
  }

  @Transactional
  public Map<String, Object> updateSettlement(Long settlementId, String status, String invoiceNo) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    String normalizedStatus = status.trim().toUpperCase();
    if (!SETTLEMENT_STATES.contains(normalizedStatus)) {
      throw bad("INVALID_SETTLEMENT_STATUS", "结算状态不正确");
    }
    Map<String, Object> settlement = repository.lockSettlementForUpdate(settlementId);
    if (settlement.isEmpty()) throw notFound("结算单不存在");
    requireCurrentSettlement(settlement);
    long transactionCount = number(settlement.get("transactionCount"));
    if ("PAID".equals(normalizedStatus)
        && (transactionCount == 0
            || decimal(settlement.get("outstandingAmount")).compareTo(BigDecimal.ZERO) > 0)) {
      throw new BusinessException(
          "SETTLEMENT_NOT_BALANCED", "尚有待结金额，不能标记为已结清", HttpStatus.CONFLICT);
    }
    if ("CANCELLED".equals(normalizedStatus) && transactionCount > 0) {
      throw new BusinessException(
          "SETTLEMENT_HAS_TRANSACTIONS", "存在有效交易记录，须先作废交易后再取消结算单", HttpStatus.CONFLICT);
    }
    if ("PENDING".equals(normalizedStatus) && transactionCount > 0) {
      throw new BusinessException(
          "SETTLEMENT_HAS_TRANSACTIONS", "存在有效交易记录，结算单不能退回待确认", HttpStatus.CONFLICT);
    }
    if (!repository.updateSettlement(
        user, settlementId, normalizedStatus, trimToNull(invoiceNo))) {
      throw notFound("结算单不存在");
    }
    return Map.of("settlementId", settlementId, "status", normalizedStatus);
  }

  private void requireCurrentSettlement(Map<String, Object> settlement) {
    if (Boolean.TRUE.equals(settlement.get("archiveOnly"))) {
      throw new BusinessException(
          "ARCHIVED_SETTLEMENT_READ_ONLY",
          "历史组合服务结算仅供查阅，不能在当前系统中修改",
          HttpStatus.CONFLICT);
    }
  }

  public List<Map<String, Object>> logs(int page, int pageSize) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    int[] p = page(page, pageSize);
    return repository.logs(p[1], p[2]);
  }

  private void requireProject(AuthPrincipal user, Long projectId) {
    if (!repository.canViewProject(user, projectId)) throw forbidden();
  }

  private Map<String, Object> customerProjectDetail(Map<String, Object> detail) {
    Map<String, Object> safe = new LinkedHashMap<>(detail);
    safe.put("project", customerFieldRow(row(detail.get("project")), CUSTOMER_PROJECT_DETAIL_FIELDS));
    safe.put("conference", customerFieldRow(row(detail.get("conference")), CUSTOMER_CONFERENCE_FIELDS));
    safe.put("conferenceWorkItems", customerFieldRows(
        rows(detail.get("conferenceWorkItems")), CUSTOMER_CONFERENCE_WORK_ITEM_FIELDS));
    safe.put("conferenceMediaCandidates", customerFieldRows(
        rows(detail.get("conferenceMediaCandidates")), CUSTOMER_CONFERENCE_CANDIDATE_FIELDS));
    safe.put("serviceIntakeTasks", customerFieldRows(
        rows(detail.get("serviceIntakeTasks")), CUSTOMER_SERVICE_INTAKE_TASK_FIELDS));
    safe.put("manuscripts", customerFieldRows(rows(detail.get("manuscripts")), CUSTOMER_MANUSCRIPT_FIELDS));
    safe.put("versions", customerFieldRows(rows(detail.get("versions")), CUSTOMER_MANUSCRIPT_VERSION_FIELDS));
    safe.put("tasks", customerFieldRows(rows(detail.get("tasks")), CUSTOMER_PUBLISH_TASK_FIELDS));
    safe.put("results", customerFieldRows(rows(detail.get("results")), CUSTOMER_RESULT_FIELDS));
    safe.put("monitoring", customerFieldRows(rows(detail.get("monitoring")), CUSTOMER_MONITORING_FIELDS));
    safe.put("settlements", customerFieldRows(
        rows(detail.get("settlements")), CUSTOMER_PROJECT_SETTLEMENT_FIELDS));
    safe.put("files", customerFieldRows(rows(detail.get("files")), CUSTOMER_FILE_FIELDS));
    safe.put("activityProjects", customerFieldRows(
        rows(detail.get("activityProjects")), CUSTOMER_ACTIVITY_PROJECT_FIELDS));
    return safe;
  }

  private Map<String, Object> row(Object value) {
    if (!(value instanceof Map<?, ?> map)) return Map.of();
    Map<String, Object> row = new LinkedHashMap<>();
    map.forEach((key, itemValue) -> row.put(String.valueOf(key), itemValue));
    return row;
  }

  private List<Map<String, Object>> rows(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> row = new LinkedHashMap<>();
        map.forEach((key, itemValue) -> row.put(String.valueOf(key), itemValue));
        rows.add(row);
      }
    }
    return rows;
  }

  private List<Map<String, Object>> customerTaskRecords(List<Map<String, Object>> records) {
    List<Map<String, Object>> safe = new ArrayList<>();
    for (Map<String, Object> record : records) {
      boolean serviceIntake = "SERVICE_INTAKE".equals(String.valueOf(record.get("itemType")));
      Map<String, Object> row = customerFieldRow(record, CUSTOMER_TASK_RECORD_FIELDS);
      if (!serviceIntake) row.remove("note");
      safe.add(row);
    }
    return safe;
  }

  private List<Map<String, Object>> customerFieldRows(
      List<Map<String, Object>> rows, List<String> visibleFields) {
    List<Map<String, Object>> safe = new ArrayList<>();
    for (Map<String, Object> row : rows) safe.add(customerFieldRow(row, visibleFields));
    return safe;
  }

  private Map<String, Object> customerFieldRow(Map<String, Object> row, List<String> visibleFields) {
    Map<String, Object> safe = new LinkedHashMap<>();
    for (String field : visibleFields) {
      if (row.containsKey(field)) safe.put(field, row.get(field));
    }
    return safe;
  }

  private int[] page(int page, int pageSize) {
    int safePage = Math.max(1, page);
    int safeSize = Math.min(100, Math.max(1, pageSize));
    return new int[]{safePage, safeSize, (safePage - 1) * safeSize};
  }

  private Long number(Object value) {
    return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
  }

  private OffsetDateTime offsetDateTime(Object value) {
    if (value instanceof OffsetDateTime dateTime) return dateTime;
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
    if (value == null) return null;
    return OffsetDateTime.parse(String.valueOf(value));
  }

  private String normalizeOptional(String value) {
    return blank(value) ? null : value.trim().toUpperCase();
  }

  private String trimToNull(String value) {
    return blank(value) ? null : value.trim();
  }

  private BigDecimal decimal(Object value) {
    if (value instanceof BigDecimal decimal) return decimal;
    if (value instanceof Number number) return new BigDecimal(number.toString());
    return BigDecimal.ZERO;
  }

  private void requireMutableTask(String status) {
    if (Set.of("COMPLETED", "CLIENT_ACCEPTED", "NOT_PROCEEDING").contains(status)) {
      throw new BusinessException(
          "TASK_FINALIZED", "任务已完成、验收或结束，不能再修改执行状态", HttpStatus.CONFLICT);
    }
  }

  private boolean canAdvanceConferenceWorkItem(String currentStatus, String nextStatus) {
    if (currentStatus.equals(nextStatus)) return true;
    return switch (currentStatus) {
      case "PENDING" -> Set.of("IN_PROGRESS", "NEEDS_INFO", "BLOCKED", "COMPLETED").contains(nextStatus);
      case "IN_PROGRESS" -> Set.of("NEEDS_INFO", "BLOCKED", "COMPLETED").contains(nextStatus);
      case "NEEDS_INFO" -> Set.of("IN_PROGRESS", "BLOCKED", "COMPLETED").contains(nextStatus);
      case "BLOCKED" -> Set.of("IN_PROGRESS", "NEEDS_INFO", "COMPLETED").contains(nextStatus);
      default -> false;
    };
  }

  private boolean canAdvanceConferenceMediaCandidate(String currentStatus, String nextStatus) {
    if (currentStatus.equals(nextStatus)) return true;
    return switch (currentStatus) {
      case "CANDIDATE" -> Set.of("READY_TO_INVITE", "NOT_PROCEEDING").contains(nextStatus);
      case "READY_TO_INVITE" -> Set.of("INVITED", "NOT_PROCEEDING").contains(nextStatus);
      case "INVITED" -> Set.of("RESPONDED", "DECLINED", "ATTENDING", "NOT_PROCEEDING")
          .contains(nextStatus);
      case "RESPONDED" -> Set.of("DECLINED", "ATTENDING", "NOT_PROCEEDING").contains(nextStatus);
      default -> false;
    };
  }

  /**
   * A media report is only a possible outcome after the platform has recorded that an invitation
   * was actually sent. A confirmed list or an internally started task must never be treated as
   * outreach evidence.
   */
  private void requireMediaInvitationBeforeResult(Long taskId, Map<String, Object> task) {
    if (!"MEDIA_PR".equals(string(task.get("channelType")))) return;
    Map<String, Object> invitation = repository.mediaInvitationForTask(taskId);
    if (invitation == null || !MEDIA_INVITATION_RESULT_READY_STATES.contains(string(invitation.get("status")))) {
      throw new BusinessException(
          "MEDIA_INVITATION_REQUIRED", "尚未登记已发出的媒体邀请，不能补录报道成果", HttpStatus.CONFLICT);
    }
  }

  /**
   * A direct-publishing task can be fulfilled by the platform itself when no supplier is bound.
   * Once a supplier order has an assigned supplier, a customer-facing result must not bypass
   * that order's completed, evidence-backed fulfillment state.
   */
  private void requireSupplierFulfillmentBeforeResult(Long taskId, Map<String, Object> task) {
    if (!"DIRECT_PUBLISHING".equals(string(task.get("channelType")))) return;
    Map<String, Object> supplierOrder = repository.supplierOrderForPublishTask(taskId);
    if (supplierOrder == null || supplierOrder.isEmpty() || supplierOrder.get("supplierId") == null) return;
    boolean completed = "COMPLETED".equals(string(supplierOrder.get("status")));
    boolean hasEvidence = Set.of("MANUAL", "API")
        .contains(string(supplierOrder.get("fulfillmentMode")))
        && !blank(string(supplierOrder.get("submissionEvidenceReference")));
    if (!completed || !hasEvidence) {
      throw new BusinessException(
          "SUPPLIER_FULFILLMENT_REQUIRED",
          "该直编任务已关联供应商，请先完成供应商履约登记并保留可核验凭据，再提交发布成果",
          HttpStatus.CONFLICT);
    }
  }

  private BusinessException taskStateChanged() {
    return new BusinessException(
        "TASK_STATE_CHANGED", "任务状态已变化，请刷新后重试", HttpStatus.CONFLICT);
  }

  private BusinessException conferenceWorkItemStateChanged() {
    return new BusinessException(
        "CONFERENCE_WORK_ITEM_STATE_CHANGED", "统筹事项状态已变化，请刷新后重试", HttpStatus.CONFLICT);
  }

  private BusinessException conferenceMediaCandidateStateChanged() {
    return new BusinessException(
        "CONFERENCE_MEDIA_CANDIDATE_STATE_CHANGED",
        "媒体候选状态已变化，请刷新后重试",
        HttpStatus.CONFLICT);
  }

  private boolean validResultUrl(String value) {
    try {
      URI uri = URI.create(value);
      String scheme = uri.getScheme();
      return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
          && !blank(uri.getHost())
          && uri.getUserInfo() == null;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private String string(Object value) { return value == null ? "" : String.valueOf(value); }
  private boolean blank(String value) { return value == null || value.isBlank(); }
  private BusinessException bad(String code, String message) { return new BusinessException(code, message, HttpStatus.BAD_REQUEST); }
  private BusinessException notFound(String message) { return new BusinessException("NOT_FOUND", message, HttpStatus.NOT_FOUND); }
  private BusinessException forbidden() { return new BusinessException("FORBIDDEN", "当前账号无权访问该数据", HttpStatus.FORBIDDEN); }
}
