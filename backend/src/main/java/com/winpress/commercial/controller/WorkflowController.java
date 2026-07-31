package com.winpress.commercial.controller;

import com.winpress.commercial.config.ApiResponse;
import com.winpress.commercial.dto.WorkflowDtos.CreatePublishPlanRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateRequirementRequest;
import com.winpress.commercial.dto.WorkflowDtos.PageResult;
import com.winpress.commercial.dto.WorkflowDtos.ReviewManuscriptRequest;
import com.winpress.commercial.dto.WorkflowDtos.RespondWritingAssignmentRequest;
import com.winpress.commercial.dto.WorkflowDtos.SubmitManuscriptRequest;
import com.winpress.commercial.dto.NiumediaDtos.BatchMediaCandidateRequest;
import com.winpress.commercial.dto.NiumediaDtos.DiscoveryTaxonomy;
import com.winpress.commercial.dto.NiumediaDtos.MediaCandidate;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchResult;
import com.winpress.commercial.dto.WorkflowDtos.UpdateConferenceProjectRequest;
import com.winpress.commercial.service.WorkflowService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {
  private final WorkflowService service;

  public WorkflowController(WorkflowService service) { this.service = service; }

  @GetMapping("/dashboard")
  public ApiResponse<Map<String, Object>> dashboard() { return ApiResponse.ok(service.dashboard()); }

  @GetMapping("/work-items")
  public ApiResponse<PageResult<Map<String, Object>>> workItems(
      @RequestParam(required = false) String scope,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.workItems(scope, page, pageSize));
  }

  @GetMapping("/task-records")
  public ApiResponse<PageResult<Map<String, Object>>> taskRecords(
      @RequestParam(required = false) String scope,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.taskRecords(scope, page, pageSize));
  }

  @GetMapping("/order-records")
  public ApiResponse<PageResult<Map<String, Object>>> orderRecords(
      @RequestParam(required = false) String serviceType,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.orderRecords(serviceType, status, page, pageSize));
  }

  @GetMapping("/settlement-records")
  public ApiResponse<PageResult<Map<String, Object>>> settlementRecords(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.settlementRecords(status, page, pageSize));
  }

  @GetMapping("/settlement-archive-records")
  public ApiResponse<PageResult<Map<String, Object>>> archivedSettlementRecords(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.archivedSettlementRecords(status, page, pageSize));
  }

  @GetMapping("/transaction-records")
  public ApiResponse<PageResult<Map<String, Object>>> settlementTransactionRecords(
      @RequestParam(required = false) String transactionType,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(
        service.settlementTransactionRecords(transactionType, status, page, pageSize));
  }

  @GetMapping("/transaction-archive-records")
  public ApiResponse<PageResult<Map<String, Object>>> archivedSettlementTransactionRecords(
      @RequestParam(required = false) String transactionType,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(
        service.archivedSettlementTransactionRecords(
            transactionType, status, page, pageSize));
  }

  @PostMapping("/requirements")
  public ApiResponse<Map<String, Object>> createRequirement(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateRequirementRequest request) {
    return ApiResponse.ok(service.createRequirement(request, idempotencyKey));
  }

  @GetMapping("/requirements")
  public ApiResponse<PageResult<Map<String, Object>>> requirements(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.requirements(status, page, pageSize));
  }

  @GetMapping("/projects")
  public ApiResponse<PageResult<Map<String, Object>>> projects(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String serviceType,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.projects(status, scope, keyword, serviceType, page, pageSize));
  }

  /**
   * Lists only the caller's already approved versions that may be copied into a new
   * independently priced direct-publishing project.
   */
  @GetMapping("/customer/approved-manuscripts")
  public ApiResponse<List<Map<String, Object>>> approvedCustomerManuscripts() {
    return ApiResponse.ok(service.approvedCustomerManuscriptSources());
  }

  @GetMapping("/projects/{projectId}")
  public ApiResponse<Map<String, Object>> project(@PathVariable Long projectId) {
    return ApiResponse.ok(service.project(projectId));
  }

  @PatchMapping("/projects/{projectId}/conference")
  public ApiResponse<Map<String, Object>> updateConferenceProject(
      @PathVariable Long projectId,
      @Valid @RequestBody UpdateConferenceProjectRequest request) {
    return ApiResponse.ok(service.updateConferenceProject(projectId, request));
  }

  @PostMapping("/manuscripts/{manuscriptId}/review")
  public ApiResponse<Map<String, Object>> reviewManuscript(
      @PathVariable Long manuscriptId, @Valid @RequestBody ReviewManuscriptRequest request) {
    return ApiResponse.ok(service.reviewManuscript(manuscriptId, request));
  }

  /**
   * A customer may provide an already approved manuscript for its own direct-publishing project.
   * This deliberately does not open manuscript submission for other service types.
   */
  @PostMapping("/projects/{projectId}/customer-manuscripts")
  public ApiResponse<Map<String, Object>> submitCustomerManuscript(
      @PathVariable Long projectId, @Valid @RequestBody SubmitManuscriptRequest request) {
    return ApiResponse.ok(service.submitCustomerManuscript(projectId, request));
  }

  @GetMapping("/channels")
  public ApiResponse<PageResult<Map<String, Object>>> channels(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String category,
      @RequestParam(required = false, name = "publish_form") String publishForm,
      @RequestParam(required = false, name = "min_price") BigDecimal minPrice,
      @RequestParam(required = false, name = "max_price") BigDecimal maxPrice,
      @RequestParam(required = false, name = "max_days") Integer maxDays,
      @RequestParam(required = false, name = "link_support") Boolean linkSupport,
      @RequestParam(required = false, name = "link_type") String linkType,
      @RequestParam(required = false, name = "news_source") String newsSource,
      @RequestParam(required = false, name = "entry_level") String entryLevel,
      @RequestParam(required = false, name = "special_industry") String specialIndustry,
      @RequestParam(required = false, name = "weekend_policy") String weekendPolicy,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.channels(
        type, keyword, region, category, publishForm, minPrice, maxPrice, maxDays,
        linkSupport, linkType, newsSource, entryLevel, specialIndustry, weekendPolicy,
        sort, page, pageSize));
  }

  @GetMapping("/channels/taxonomy")
  public ApiResponse<Map<String, Object>> channelTaxonomy(
      @RequestParam(defaultValue = "DIRECT_PUBLISHING") String type) {
    return ApiResponse.ok(service.channelTaxonomy(type));
  }

  @GetMapping("/media-discovery/status")
  public ApiResponse<Map<String, Object>> mediaDiscoveryStatus() {
    return ApiResponse.ok(service.mediaDiscoveryStatus());
  }

  @GetMapping("/media-discovery/taxonomy")
  public ApiResponse<DiscoveryTaxonomy> mediaDiscoveryTaxonomy() {
    return ApiResponse.ok(service.mediaDiscoveryTaxonomy());
  }

  @GetMapping("/media-discovery")
  public ApiResponse<MediaSearchResult> mediaDiscovery(
      @RequestParam(defaultValue = "MEDIA") String target,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String province,
      @RequestParam(required = false) String city,
      @RequestParam(required = false, name = "medium_type") Integer mediumType,
      @RequestParam(required = false, name = "media_type") String mediaType,
      @RequestParam(required = false, name = "mp_types") String mpTypes,
      @RequestParam(required = false, name = "mp_type_group") String mpTypeGroup,
      @RequestParam(required = false, name = "media_ref") String mediaRef,
      @RequestParam(required = false, name = "reporter_type") Integer reporterType,
      @RequestParam(required = false) String platform,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String field,
      @RequestParam(required = false) String workflow,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.searchMediaDiscovery(
        target, keyword, name, province, city, mediumType, mediaType, mpTypes, mpTypeGroup,
        mediaRef, reporterType, platform, sort, field, workflow, page, pageSize));
  }

  @PostMapping("/projects/{projectId}/conference-media-candidates")
  public ApiResponse<Map<String, Object>> addConferenceMediaCandidate(
      @PathVariable Long projectId, @Valid @RequestBody MediaCandidate candidate) {
    return ApiResponse.ok(service.addConferenceMediaCandidate(projectId, candidate));
  }

  @PostMapping("/projects/{projectId}/conference-media-candidates/batch")
  public ApiResponse<Map<String, Object>> addConferenceMediaCandidates(
      @PathVariable Long projectId, @Valid @RequestBody BatchMediaCandidateRequest request) {
    return ApiResponse.ok(service.addConferenceMediaCandidates(projectId, request));
  }

  @PostMapping("/projects/{projectId}/publish-plan")
  public ApiResponse<Map<String, Object>> createPublishPlan(
      @PathVariable Long projectId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreatePublishPlanRequest request) {
    return ApiResponse.ok(service.createPublishPlan(projectId, request, idempotencyKey));
  }

  @PostMapping("/projects/{projectId}/publish-plans")
  public ApiResponse<Map<String, Object>> createPublishPlanV41(
      @PathVariable Long projectId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreatePublishPlanRequest request) {
    return ApiResponse.ok(service.createPublishPlan(projectId, request, idempotencyKey));
  }

  @GetMapping("/projects/{projectId}/publish-plans")
  public ApiResponse<List<Map<String, Object>>> publishPlans(@PathVariable Long projectId) {
    return ApiResponse.ok(service.publishPlans(projectId));
  }

  @PostMapping("/publish-plans/{planNo}/confirm")
  public ApiResponse<Map<String, Object>> confirmPublishPlan(@PathVariable String planNo) {
    return ApiResponse.ok(service.confirmPublishPlan(planNo));
  }

  @GetMapping("/writing-assignments")
  public ApiResponse<List<Map<String, Object>>> writingAssignments(
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(service.writingAssignments(status));
  }

  @PostMapping("/writing-assignments/{assignmentId}/respond")
  public ApiResponse<Map<String, Object>> respondWritingAssignment(
      @PathVariable Long assignmentId, @Valid @RequestBody RespondWritingAssignmentRequest request) {
    return ApiResponse.ok(service.respondWritingAssignment(assignmentId, request));
  }

  @GetMapping("/publish-tasks")
  public ApiResponse<PageResult<Map<String, Object>>> tasks(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String channelType,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.tasks(status, scope, channelType, page, pageSize));
  }

  @GetMapping("/publish-tasks/{taskId}")
  public ApiResponse<Map<String, Object>> task(@PathVariable Long taskId) {
    return ApiResponse.ok(service.task(taskId));
  }

  @GetMapping("/customer/publish-tasks/{taskNo}")
  public ApiResponse<Map<String, Object>> customerTask(@PathVariable String taskNo) {
    return ApiResponse.ok(service.customerTask(taskNo));
  }

  @PostMapping("/publish-tasks/{taskNo}/accept")
  public ApiResponse<Map<String, Object>> accept(@PathVariable String taskNo) {
    return ApiResponse.ok(service.acceptTask(taskNo));
  }
}
