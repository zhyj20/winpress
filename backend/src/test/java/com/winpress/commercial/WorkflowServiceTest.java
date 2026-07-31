package com.winpress.commercial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winpress.commercial.dto.WorkflowDtos.ChannelSelection;
import com.winpress.commercial.dto.WorkflowDtos.BatchQuoteAdjustmentRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateChannelRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreatePublishPlanRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateQuoteRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateRequirementRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateSettlementTransactionRequest;
import com.winpress.commercial.dto.WorkflowDtos.OfferWritingAssignmentRequest;
import com.winpress.commercial.dto.WorkflowDtos.RespondWritingAssignmentRequest;
import com.winpress.commercial.dto.WorkflowDtos.SubmitManuscriptRequest;
import com.winpress.commercial.dto.WorkflowDtos.SubmitResultRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateConferenceWorkItemRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateTaskRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateMediaInvitationRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateSupplierOrderRequest;
import com.winpress.commercial.dto.WorkflowDtos.VoidSettlementTransactionRequest;
import com.winpress.commercial.dto.NiumediaDtos.MediaCandidate;
import com.winpress.commercial.dto.NiumediaDtos.UpdateConferenceMediaCandidateRequest;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.WorkflowRepository;
import com.winpress.commercial.repository.WorkflowRepository.RequirementCreation;
import com.winpress.commercial.repository.WorkflowRepository.WritingAssignmentOfferOutcome;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import com.winpress.commercial.service.NiumediaMediaService;
import com.winpress.commercial.service.IntegrationAdminService;
import com.winpress.commercial.service.WorkflowService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowServiceTest {
  private static final String PUBLISH_PLAN_KEY = "test-publish-plan-key-0001";
  private static final String SETTLEMENT_TRANSACTION_KEY =
      "test-settlement-transaction-key-0001";
  private static final String BATCH_QUOTE_KEY =
      "test-batch-quote-key-0001";

  private WorkflowRepository repository;
  private IntegrationAdminService integrationAdminService;
  private NiumediaMediaService niumediaMediaService;
  private WorkflowService service;

  @BeforeEach
  void setUp() {
    repository = mock(WorkflowRepository.class);
    integrationAdminService = mock(IntegrationAdminService.class);
    niumediaMediaService = mock(NiumediaMediaService.class);
    service = new WorkflowService(repository, niumediaMediaService, integrationAdminService);
    CurrentUser.set(new AuthPrincipal(3L, "USR-3", 2L, "客户", "client", "陈经理",
        "13800000003", "client@example.com", "CUSTOMER", List.of("publish:submit")));
    when(repository.canViewProject(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
    when(repository.projectRequestedService(1L)).thenReturn("DIRECT_PUBLISHING");
    when(repository.manuscriptContext(10L, 20L)).thenReturn(Map.of(
        "project_id", 1L, "approved_version_id", 20L, "version_status", "APPROVED", "title", "已确认稿件"));
    when(repository.lockTaskForUpdate(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "IN_PROGRESS",
        "channelType", "MEDIA_PR"));
  }

  @AfterEach
  void tearDown() { CurrentUser.clear(); }

  @Test
  void directPublishingIsBlockedWhileMediaPrLockIsActive() {
    when(repository.channel(30L)).thenReturn(channel(30L, "DIRECT_PUBLISHING"));
    when(repository.hasActiveLock(10L)).thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class, () -> createPublishPlan(
        new CreatePublishPlanRequest(10L, 20L, null, null, false, null,
            List.of(new ChannelSelection(30L, null, null, null, null, null)))));

    assertEquals("MANUSCRIPT_LOCKED", ex.getCode());
  }

  @Test
  void onlineExclusiveMediaPrIsNotAvailableForNewPlans() {
    when(repository.projectRequestedService(1L)).thenReturn("MEDIA_PR");

    BusinessException ex = assertThrows(BusinessException.class, () -> createPublishPlan(
        new CreatePublishPlanRequest(null, null, "重点媒体邀约", null, true,
            OffsetDateTime.now().plusDays(2), List.of(
            new ChannelSelection(null, null, "记者", "财经媒体", null, null)))));

    assertEquals("MEDIA_PR_EXCLUSIVE_NOT_AVAILABLE", ex.getCode());
    verify(repository, never()).createPublishPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void creatingAPublishPlanDoesNotCreateTasksBeforeConfirmation() {
    when(repository.channel(30L)).thenReturn(channel(30L, "DIRECT_PUBLISHING"));
    when(repository.hasActiveLock(10L)).thenReturn(false);
    when(repository.createPublishPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(20L),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Map.of("planNo", "PLAN-77", "status", "WAITING_CONFIRMATION", "itemCount", 1,
            "estimatedAmount", new BigDecimal("100.00")));

    Map<String, Object> result = createPublishPlan(
        new CreatePublishPlanRequest(10L, 20L, null, null, false, null,
            List.of(new ChannelSelection(30L, null, null, null, null, null))));

    assertEquals("PLAN-77", result.get("planNo"));
    assertEquals("WAITING_CONFIRMATION", result.get("status"));
    assertFalse(result.containsKey("planId"));
    verify(repository, never()).createPublishTaskFromPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
  }

  @Test
  void publishPlanRequiresAnIdempotencyKey() {
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.createPublishPlan(
            1L,
            new CreatePublishPlanRequest(
                null, null, "媒体邀请计划", null, false, null,
                List.of(new ChannelSelection(null, null, null, "产业媒体", null, null))),
            null));

    assertEquals("IDEMPOTENCY_KEY_REQUIRED", exception.getCode());
    verify(repository, never()).existingPublishPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void publishPlanRetryReturnsTheOriginalPlanWithoutCreatingAnotherOne() {
    CreatePublishPlanRequest request = new CreatePublishPlanRequest(
        null, null, "媒体邀请计划", null, false, null,
        List.of(new ChannelSelection(null, null, null, "产业媒体", null, null)));
    when(repository.existingPublishPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(PUBLISH_PLAN_KEY),
        org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Map.of(
            "planNo", "PLAN-EXISTING", "status", "WAITING_CONFIRMATION",
            "itemCount", 1, "estimatedAmount", BigDecimal.ZERO));

    Map<String, Object> result =
        service.createPublishPlan(1L, request, PUBLISH_PLAN_KEY);

    assertEquals("PLAN-EXISTING", result.get("planNo"));
    verify(repository, never()).createPublishPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void publicChannelDirectoryStripsOperationalIdentifiers() {
    Map<String, Object> raw = new HashMap<>(Map.of(
        "id", 30L,
        "channelNo", "UPSTREAM-CHANNEL-30",
        "quoteId", 88L,
        "channelName", "测试媒体",
        "channelType", "DIRECT_PUBLISHING",
        "customerPrice", new BigDecimal("128.50"),
        "status", "ACTIVE"));
    when(repository.channels(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(List.of(raw));
    when(repository.channelsCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(1L);

    var page = service.channels(
        "DIRECT_PUBLISHING", null, null, null, null, null, null, null, null,
        null, null, null, null, null, "PRICE_ASC", 1, 20);

    Map<String, Object> item = page.items().get(0);
    assertEquals("测试媒体", item.get("channelName"));
    assertEquals(new BigDecimal("128.50"), item.get("customerPrice"));
    assertFalse(item.containsKey("channelNo"));
    assertFalse(item.containsKey("quoteId"));
  }

  @Test
  void mediaInvitationCanStartWithoutAnApprovedManuscript() {
    when(repository.channel(31L)).thenReturn(channel(31L, "MEDIA_PR"));
    when(repository.projectRequestedService(1L)).thenReturn("MEDIA_PR");
    when(repository.createPublishPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Map.of("planNo", "PLAN-78", "status", "WAITING_CONFIRMATION", "itemCount", 1,
            "estimatedAmount", BigDecimal.ZERO));

    Map<String, Object> result = createPublishPlan(
        new CreatePublishPlanRequest(null, null, "活动媒体邀请", null, false, null,
            List.of(new ChannelSelection(31L, null, "联系人", "财经媒体", null, null))));

    assertEquals("PLAN-78", result.get("planNo"));
    assertFalse(result.containsKey("planId"));
    verify(repository, never()).manuscriptContext(
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void manualMediaInvitationCanStartWithoutAnExecutionChannel() {
    when(repository.projectRequestedService(1L)).thenReturn("MEDIA_PR");
    when(repository.createPublishPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Map.of("planNo", "PLAN-80", "status", "WAITING_CONFIRMATION", "itemCount", 1,
            "estimatedAmount", BigDecimal.ZERO));

    Map<String, Object> result = createPublishPlan(
        new CreatePublishPlanRequest(null, null, "人工补充媒体邀请", null, false, null,
            List.of(new ChannelSelection(null, null, "李编辑", "华南产业观察", null,
                candidate("MANUAL:browser-value", "MANUAL", null, "华南产业观察", null, "李编辑")))));

    assertEquals("PLAN-80", result.get("planNo"));
    assertFalse(result.containsKey("planId"));
    verify(repository, never()).channel(org.mockito.ArgumentMatchers.anyLong());
    verify(niumediaMediaService, never()).resolveCandidate(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void confirmingManualMediaInvitationDoesNotRequireAnExecutionChannel() {
    when(repository.projectRequestedService(1L)).thenReturn("MEDIA_PR");
    when(repository.lockPublishPlanForUpdateByNo("PLAN-80")).thenReturn(Map.of(
        "id", 80L, "projectId", 1L, "status", "WAITING_CONFIRMATION", "exclusiveMediaPr", false));
    Map<String, Object> manualItem = new HashMap<>(Map.of(
        "planItemId", 801L, "publishPlanId", 80L, "projectId", 1L,
        "channelType", "MEDIA_PR", "channelStatus", "MANUAL_REVIEW",
        "mediaName", "华南产业观察", "candidateType", "MANUAL"));
    when(repository.publishPlanItems(80L)).thenReturn(List.of(manualItem));
    when(repository.createPublishTaskFromPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.same(manualItem))).thenReturn(901L);
    when(repository.publishPlanTaskNos(80L)).thenReturn(List.of("PUB-901"));

    Map<String, Object> result = service.confirmPublishPlan("PLAN-80");

    assertEquals("CONFIRMED", result.get("status"));
    assertEquals(List.of("PUB-901"), result.get("taskNos"));
    assertFalse(result.containsKey("taskIds"));
    assertFalse(result.containsKey("planId"));
    verify(repository).markPublishPlanConfirmed(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(80L),
        org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.isNull());
  }

  @Test
  void confirmingPlanRejectsProjectServiceMismatchBeforeCreatingTasks() {
    when(repository.projectRequestedService(1L)).thenReturn("DIRECT_PUBLISHING");
    when(repository.lockPublishPlanForUpdateByNo("PLAN-81")).thenReturn(Map.of(
        "id", 81L, "projectId", 1L, "status", "WAITING_CONFIRMATION", "exclusiveMediaPr", false));
    Map<String, Object> mismatchedItem = new HashMap<>(Map.of(
        "planItemId", 811L, "publishPlanId", 81L, "projectId", 1L,
        "channelType", "MEDIA_PR", "channelStatus", "MANUAL_REVIEW",
        "mediaName", "历史错配媒体", "candidateType", "MANUAL"));
    when(repository.publishPlanItems(81L)).thenReturn(List.of(mismatchedItem));

    BusinessException exception = assertThrows(
        BusinessException.class, () -> service.confirmPublishPlan("PLAN-81"));

    assertEquals("PUBLISH_PLAN_SERVICE_MISMATCH", exception.getCode());
    assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
    verify(repository, never()).createPublishTaskFromPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
    verify(repository, never()).markPublishPlanConfirmed(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void confirmingDirectPlanRejectsManuscriptFromAnotherProject() {
    when(repository.lockPublishPlanForUpdateByNo("PLAN-77")).thenReturn(Map.of(
        "id", 77L, "projectId", 1L, "status", "WAITING_CONFIRMATION", "exclusiveMediaPr", false,
        "manuscriptId", 10L, "manuscriptVersionId", 20L));
    Map<String, Object> directItem = new HashMap<>(Map.of(
        "planItemId", 771L, "publishPlanId", 77L, "projectId", 1L,
        "channelType", "DIRECT_PUBLISHING", "channelStatus", "ACTIVE", "quoteUsable", true));
    when(repository.publishPlanItems(77L)).thenReturn(List.of(directItem));
    when(repository.manuscriptContext(10L, 20L)).thenReturn(Map.of(
        "project_id", 2L, "approved_version_id", 20L, "version_status", "APPROVED"));

    BusinessException exception = assertThrows(
        BusinessException.class, () -> service.confirmPublishPlan("PLAN-77"));

    assertEquals("MANUSCRIPT_PROJECT_MISMATCH", exception.getCode());
    verify(repository, never()).createPublishTaskFromPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
    verify(repository, never()).markPublishPlanConfirmed(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void customerCanSubmitAnExistingFinalManuscriptForDirectPublishing() {
    when(repository.projectRequestedService(1L)).thenReturn("DIRECT_PUBLISHING");
    when(repository.submitCustomerApprovedManuscript(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.any())).thenReturn(66L);

    Map<String, Object> result = service.submitCustomerManuscript(1L,
        new SubmitManuscriptRequest("客户确认稿", "摘要", "客户提供并确认的稿件正文", "首次提交"));

    assertEquals(66L, result.get("manuscriptId"));
    assertEquals("CLIENT_APPROVED", result.get("status"));
    verify(repository).submitCustomerApprovedManuscript(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void customerCannotUseExistingManuscriptEndpointOutsideDirectPublishing() {
    when(repository.projectRequestedService(1L)).thenReturn("MEDIA_PR");

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.submitCustomerManuscript(1L,
            new SubmitManuscriptRequest("客户确认稿", null, "客户提供并确认的稿件正文", null)));

    assertEquals("CUSTOMER_MANUSCRIPT_NOT_ALLOWED", exception.getCode());
    verify(repository, never()).submitCustomerApprovedManuscript(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void operatorManuscriptEndpointRequiresAnIndependentWritingProject() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockProjectForManuscriptSubmission(1L))
        .thenReturn(Map.of("requestedService", "MEDIA_PR"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.submitManuscript(1L,
            new SubmitManuscriptRequest("媒体邀请稿件", null, "不应通过云采写端点提交", null)));

    assertEquals("WRITING_PROJECT_REQUIRED", exception.getCode());
    verify(repository, never()).submitManuscript(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void writingOrderCannotBeReopenedAfterClientApproval() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockProjectForManuscriptSubmission(1L)).thenReturn(Map.of(
        "requestedService", "ONSITE_WRITING", "manuscriptStatus", "CLIENT_APPROVED"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.submitManuscript(1L,
            new SubmitManuscriptRequest("重复稿件", null, "客户定稿后不得重开", null)));

    assertEquals("WRITING_ORDER_FINALIZED", exception.getCode());
    verify(repository, never()).submitManuscript(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void writingOrderWaitsForReviewBeforeAnotherVersion() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockProjectForManuscriptSubmission(1L)).thenReturn(Map.of(
        "requestedService", "ONSITE_WRITING", "manuscriptStatus", "CLIENT_REVIEW"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.submitManuscript(1L,
            new SubmitManuscriptRequest("重复待审稿", null, "等待客户审核时不得重复提交", null)));

    assertEquals("WRITING_REVIEW_PENDING", exception.getCode());
    verify(repository, never()).submitManuscript(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void returnedWritingOrderAcceptsARevision() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockProjectForManuscriptSubmission(1L)).thenReturn(Map.of(
        "requestedService", "ONSITE_WRITING", "manuscriptStatus", "CLIENT_RETURNED"));
    when(repository.submitManuscript(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.any())).thenReturn(77L);

    Map<String, Object> result = service.submitManuscript(1L,
        new SubmitManuscriptRequest("修订稿", "摘要", "根据客户意见完成修订", "已修订"));

    assertEquals(77L, result.get("manuscriptId"));
    verify(repository).submitManuscript(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void directPublishingPlanRequiresAnIndependentDirectPublishingProject() {
    when(repository.channel(30L)).thenReturn(channel(30L, "DIRECT_PUBLISHING"));
    when(repository.projectRequestedService(1L)).thenReturn("ONSITE_WRITING");

    BusinessException exception = assertThrows(BusinessException.class,
        () -> createPublishPlan(
            new CreatePublishPlanRequest(10L, 20L, null, null, false, null,
                List.of(new ChannelSelection(30L, null, null, null, null, null)))));

    assertEquals("DIRECT_PROJECT_REQUIRED", exception.getCode());
  }

  @Test
  void mediaInvitationPlanRequiresAnIndependentMediaInvitationProject() {
    when(repository.channel(31L)).thenReturn(channel(31L, "MEDIA_PR"));
    when(repository.projectRequestedService(1L)).thenReturn("NEWS_CONFERENCE");

    BusinessException exception = assertThrows(BusinessException.class,
        () -> createPublishPlan(
            new CreatePublishPlanRequest(null, null, null, null, false, null,
                List.of(new ChannelSelection(31L, null, null, "产业媒体", null, null)))));

    assertEquals("MEDIA_PR_PROJECT_REQUIRED", exception.getCode());
  }

  @Test
  void mixedServicePlanIsRejectedWithoutAnExclusiveLock() {
    when(repository.channel(31L)).thenReturn(channel(31L, "MEDIA_PR"));
    when(repository.channel(30L)).thenReturn(channel(30L, "DIRECT_PUBLISHING"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> createPublishPlan(
            new CreatePublishPlanRequest(null, null, null, null, false, null,
                List.of(
                    new ChannelSelection(31L, null, null, "产业媒体", null, null),
                    new ChannelSelection(30L, null, null, null, null, null)))));

    assertEquals("MIXED_SERVICE_PLAN_NOT_ALLOWED", exception.getCode());
  }

  @Test
  void publishPlanRequiresAtLeastOneSelection() {
    BusinessException exception = assertThrows(BusinessException.class,
        () -> createPublishPlan(
            new CreatePublishPlanRequest(null, null, null, null, false, null, List.of())));

    assertEquals("PUBLISH_PLAN_SELECTION_REQUIRED", exception.getCode());
  }

  @Test
  void directRequirementCopiesOnlyTheCustomersApprovedSourceVersion() {
    CreateRequirementRequest request = directRequirementWithSource(10L, 20L);
    when(repository.approvedCustomerManuscriptSource(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(10L),
        org.mockito.ArgumentMatchers.eq(20L))).thenReturn(Map.of("projectId", 7L));
    when(repository.createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(request),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(new RequirementCreation(92L, true));
    when(repository.copyApprovedManuscriptToDirectProject(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(92L),
        org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(20L))).thenReturn(66L);

    Map<String, Object> result = service.createRequirement(request, "test-requirement-key-0001");

    assertEquals(92L, result.get("projectId"));
    assertEquals("直编发稿项目已创建，客户定稿已复制", result.get("message"));
    verify(repository).copyApprovedManuscriptToDirectProject(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(92L),
        org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(20L));
  }

  @Test
  void requirementSubmissionRequiresAValidIdempotencyKey() {
    CreateRequirementRequest request = new CreateRequirementRequest(
        "媒体邀请", null, null, "邀请媒体参加沟通会", null, null,
        "MEDIA_PR", null, null, null, null, null,
        null, null, null, null, null, null, null, null, null);

    BusinessException missing = assertThrows(
        BusinessException.class, () -> service.createRequirement(request, null));
    BusinessException malformed = assertThrows(
        BusinessException.class, () -> service.createRequirement(request, "short"));

    assertEquals("IDEMPOTENCY_KEY_REQUIRED", missing.getCode());
    assertEquals("INVALID_IDEMPOTENCY_KEY", malformed.getCode());
    verify(repository, never()).createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void idempotentReplayReturnsTheOriginalProjectBeforeMutableSourceValidation() {
    CreateRequirementRequest request = directRequirementWithSource(10L, 20L);
    when(repository.existingRequirement(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.eq("test-requirement-key-0001"),
        org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(new RequirementCreation(92L, false));

    Map<String, Object> result = service.createRequirement(
        request, "test-requirement-key-0001");

    assertEquals(92L, result.get("projectId"));
    verify(repository, never()).approvedCustomerManuscriptSource(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong());
    verify(repository, never()).createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    verify(repository, never()).copyApprovedManuscriptToDirectProject(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void sourceManuscriptMustBeVisibleAndApprovedForTheCurrentCustomer() {
    CreateRequirementRequest request = directRequirementWithSource(10L, 20L);
    when(repository.approvedCustomerManuscriptSource(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(10L),
        org.mockito.ArgumentMatchers.eq(20L))).thenReturn(Map.of());

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.createRequirement(request, "test-requirement-key-0001"));

    assertEquals("SOURCE_MANUSCRIPT_NOT_AVAILABLE", exception.getCode());
    verify(repository, never()).createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void sourceManuscriptReferenceMustBeCompleteAndOnlyForDirectPublishing() {
    CreateRequirementRequest incomplete = directRequirementWithSource(10L, null);
    BusinessException incompleteError = assertThrows(
        BusinessException.class,
        () -> service.createRequirement(incomplete, "test-requirement-key-0001"));
    assertEquals("SOURCE_MANUSCRIPT_REFERENCE_INCOMPLETE", incompleteError.getCode());

    CreateRequirementRequest mediaPr = new CreateRequirementRequest(
        "媒体邀请", null, null, "邀请媒体参加沟通会", null, null,
        "MEDIA_PR", null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, 10L, 20L);
    BusinessException wrongServiceError = assertThrows(
        BusinessException.class,
        () -> service.createRequirement(mediaPr, "test-requirement-key-0001"));
    assertEquals("SOURCE_MANUSCRIPT_NOT_APPLICABLE", wrongServiceError.getCode());
  }

  @Test
  void approvedManuscriptSourceListUsesCustomerScope() {
    when(repository.approvedCustomerManuscriptSources(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(Map.of("title", "客户已确认稿件")));

    List<Map<String, Object>> sources = service.approvedCustomerManuscriptSources();

    assertEquals(1, sources.size());
    verify(repository).approvedCustomerManuscriptSources(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void approvedManuscriptSourceListIsNotAvailableToOperators() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));

    BusinessException exception = assertThrows(
        BusinessException.class, () -> service.approvedCustomerManuscriptSources());

    assertEquals("FORBIDDEN", exception.getCode());
    verify(repository, never()).approvedCustomerManuscriptSources(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void oneMediaInvitationPlanCanContainMultipleDistinctTargets() {
    when(repository.channel(31L)).thenReturn(channel(31L, "MEDIA_PR"));
    when(repository.projectRequestedService(1L)).thenReturn("MEDIA_PR");
    when(repository.createPublishPlan(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Map.of("planNo", "PLAN-79", "status", "WAITING_CONFIRMATION", "itemCount", 2,
            "estimatedAmount", BigDecimal.ZERO));

    MediaCandidate media = candidate("MEDIA:9", "MEDIA", "9", "产业观察", null, null);
    MediaCandidate reporter = candidate(
        "REPORTER:88", "REPORTER", "9", "产业观察", "88", "林记者");
    when(niumediaMediaService.resolveCandidate("SEL-MEDIA")).thenReturn(media);
    when(niumediaMediaService.resolveCandidate("SEL-REPORTER")).thenReturn(reporter);
    Map<String, Object> result = createPublishPlan(
        new CreatePublishPlanRequest(null, null, "活动媒体邀请", null, false, null,
            List.of(
                new ChannelSelection(31L, null, null, "产业观察", null,
                    candidate("SEL-MEDIA", "MEDIA", null, "产业观察", null, null)),
                new ChannelSelection(31L, null, "林记者", "产业观察", null,
                    candidate("SEL-REPORTER", "REPORTER", null, "产业观察", null, "林记者")))));

    assertEquals(2, result.get("itemCount"));
  }

  @Test
  void manualConferenceCandidateIsReissuedWithServerSideIdentifiers() {
    when(repository.hasConferenceProject(1L)).thenReturn(true);
    when(repository.addConferenceMediaCandidate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.any())).thenReturn(true);
    MediaCandidate manual = candidate("MANUAL:browser-provided", "MANUAL", null,
        "华南产业观察", null, "李编辑");

    service.addConferenceMediaCandidate(1L, manual);

    ArgumentCaptor<MediaCandidate> captor = ArgumentCaptor.forClass(MediaCandidate.class);
    verify(repository).addConferenceMediaCandidate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L), captor.capture());
    MediaCandidate stored = captor.getValue();
    assertEquals("MANUAL", stored.candidateType());
    org.junit.jupiter.api.Assertions.assertTrue(stored.candidateKey().startsWith("MANUAL:"));
    org.junit.jupiter.api.Assertions.assertTrue(stored.mediaId().startsWith("MANUAL-"));
    org.junit.jupiter.api.Assertions.assertTrue(stored.reporterId().startsWith("MANUAL-REPORTER-"));
    verify(niumediaMediaService, never()).resolveCandidate(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void conferenceMediaCandidateCannotSkipConfirmationAndInvitation() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockConferenceMediaCandidateForUpdate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(61L))).thenReturn(Map.of("id", 61L, "status", "CANDIDATE"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateConferenceMediaCandidate(1L, 61L,
            new UpdateConferenceMediaCandidateRequest("ATTENDING", "CANDIDATE", "媒体确认到场")));

    assertEquals("INVALID_CONFERENCE_MEDIA_CANDIDATE_TRANSITION", exception.getCode());
    verify(repository, never()).updateConferenceMediaCandidate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void conferenceMediaCandidateRejectsAStaleClientState() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockConferenceMediaCandidateForUpdate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(61L))).thenReturn(Map.of("id", 61L, "status", "INVITED"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateConferenceMediaCandidate(1L, 61L,
            new UpdateConferenceMediaCandidateRequest("INVITED", "READY_TO_INVITE", null)));

    assertEquals("CONFERENCE_MEDIA_CANDIDATE_STATE_CHANGED", exception.getCode());
    verify(repository, never()).updateConferenceMediaCandidate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void conferenceMediaCandidateOutcomeRequiresAContactRecord() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockConferenceMediaCandidateForUpdate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(61L))).thenReturn(Map.of("id", 61L, "status", "INVITED"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateConferenceMediaCandidate(1L, 61L,
            new UpdateConferenceMediaCandidateRequest("DECLINED", "INVITED", "")));

    assertEquals("CONFERENCE_MEDIA_CANDIDATE_NOTE_REQUIRED", exception.getCode());
    verify(repository, never()).updateConferenceMediaCandidate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void conferenceMediaCandidateMovesFromConfirmedToInvitedWithExpectedState() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockConferenceMediaCandidateForUpdate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(61L))).thenReturn(Map.of("id", 61L, "status", "READY_TO_INVITE"));
    when(repository.updateConferenceMediaCandidate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(61L), org.mockito.ArgumentMatchers.eq("READY_TO_INVITE"),
        org.mockito.ArgumentMatchers.eq("INVITED"), org.mockito.ArgumentMatchers.eq("已发送邀请")))
        .thenReturn(true);

    Map<String, Object> result = service.updateConferenceMediaCandidate(1L, 61L,
        new UpdateConferenceMediaCandidateRequest("INVITED", "READY_TO_INVITE", "已发送邀请"));

    assertEquals("INVITED", result.get("status"));
    verify(repository).updateConferenceMediaCandidate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(61L), org.mockito.ArgumentMatchers.eq("READY_TO_INVITE"),
        org.mockito.ArgumentMatchers.eq("INVITED"), org.mockito.ArgumentMatchers.eq("已发送邀请"));
  }

  @Test
  void finalizedConferenceMediaCandidateCannotBeReopened() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockConferenceMediaCandidateForUpdate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(61L))).thenReturn(Map.of("id", 61L, "status", "ATTENDING"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateConferenceMediaCandidate(1L, 61L,
            new UpdateConferenceMediaCandidateRequest("INVITED", "ATTENDING", "重新联系")));

    assertEquals("CONFERENCE_MEDIA_CANDIDATE_FINALIZED", exception.getCode());
    verify(repository, never()).updateConferenceMediaCandidate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void completedConferenceWorkItemCannotBeReopened() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockConferenceWorkItemForUpdate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(51L))).thenReturn(Map.of("id", 51L, "status", "COMPLETED"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateConferenceWorkItem(1L, 51L,
            new UpdateConferenceWorkItemRequest(
                "PENDING", "COMPLETED", "尝试重新打开已完成事项", null, null)));

    assertEquals("CONFERENCE_WORK_ITEM_FINALIZED", exception.getCode());
    verify(repository, never()).updateConferenceWorkItem(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void conferenceWorkItemRejectsAStaleClientState() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockConferenceWorkItemForUpdate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(51L))).thenReturn(Map.of("id", 51L, "status", "BLOCKED"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateConferenceWorkItem(1L, 51L,
            new UpdateConferenceWorkItemRequest("IN_PROGRESS", "PENDING", "已补充资料", null, null)));

    assertEquals("CONFERENCE_WORK_ITEM_STATE_CHANGED", exception.getCode());
    verify(repository, never()).updateConferenceWorkItem(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void conferenceWorkItemNeedsInfoRequiresAnExplanation() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateConferenceWorkItem(1L, 51L,
            new UpdateConferenceWorkItemRequest("NEEDS_INFO", "PENDING", "", null, null)));

    assertEquals("CONFERENCE_WORK_ITEM_NOTE_REQUIRED", exception.getCode());
    verify(repository, never()).lockConferenceWorkItemForUpdate(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void completedStatusMustBeProducedByResultSubmission() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L))).thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.updateTask(9L, new UpdateTaskRequest("COMPLETED", "", "")));

    assertEquals("INVALID_TASK_STATUS", ex.getCode());
  }

  @Test
  void nonCustomerCannotAcceptACompletedPublishTask() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));

    BusinessException ex = assertThrows(BusinessException.class, () -> service.acceptTask("PUB-9"));

    assertEquals("FORBIDDEN", ex.getCode());
    verify(repository, never()).taskByNo("PUB-9");
    verify(repository, never()).acceptTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void acceptedTaskCannotReturnToExecution() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockTaskForUpdate(9L)).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "CLIENT_ACCEPTED"));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
        .thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.updateTask(9L, new UpdateTaskRequest("IN_PROGRESS", "继续执行", null)));

    assertEquals("TASK_FINALIZED", ex.getCode());
    verify(repository, never()).updateTask(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void acceptedTaskCannotReceiveAnotherResult() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockTaskForUpdate(9L)).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "CLIENT_ACCEPTED"));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
        .thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.submitResult(9L, new SubmitResultRequest(
            "已核验报道", "https://example.com/result", OffsetDateTime.now(), null)));

    assertEquals("TASK_ALREADY_ACCEPTED", ex.getCode());
    verify(repository, never()).submitResult(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void mediaTaskClosedWithoutAResultCannotReceiveAFabricatedPublicationResult() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockTaskForUpdate(9L)).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "NOT_PROCEEDING",
        "channelType", "MEDIA_PR"));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
        .thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.submitResult(9L, new SubmitResultRequest(
            "不存在的报道", "https://example.com/not-a-result", OffsetDateTime.now(), null)));

    assertEquals("TASK_NOT_PROCEEDING", ex.getCode());
    verify(repository, never()).submitResult(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void mediaResultRequiresARecordedInvitationBeforeItCanBeSubmitted() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockTaskForUpdate(9L)).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "IN_PROGRESS",
        "channelType", "MEDIA_PR"));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
        .thenReturn(true);
    when(repository.mediaInvitationForTask(9L)).thenReturn(Map.of("status", "PENDING"));
    when(repository.submitResult(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any())).thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.submitResult(9L, new SubmitResultRequest(
            "不应跳过邀约的报道", "https://example.com/uninvited-media-result", OffsetDateTime.now(), null)));

    assertEquals("MEDIA_INVITATION_REQUIRED", ex.getCode());
    verify(repository, never()).submitResult(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void invitedMediaTaskCanSubmitAReportedResult() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockTaskForUpdate(9L)).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "IN_PROGRESS",
        "channelType", "MEDIA_PR"));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
        .thenReturn(true);
    when(repository.mediaInvitationForTask(9L)).thenReturn(Map.of("status", "INVITED"));
    when(repository.submitResult(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any())).thenReturn(true);

    Map<String, Object> result = service.submitResult(9L, new SubmitResultRequest(
        "已形成报道", "https://example.com/invited-media-result", OffsetDateTime.now(), null));

    assertEquals("COMPLETED", result.get("status"));
    verify(repository).submitResult(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L),
        org.mockito.ArgumentMatchers.eq("IN_PROGRESS"), org.mockito.ArgumentMatchers.eq("已形成报道"),
        org.mockito.ArgumentMatchers.eq("https://example.com/invited-media-result"),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull());
  }

  @Test
  void completedTaskCannotReceiveDuplicateResult() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockTaskForUpdate(9L)).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "COMPLETED"));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
        .thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.submitResult(9L, new SubmitResultRequest(
            "已核验报道", "https://example.com/result", OffsetDateTime.now(), null)));

    assertEquals("TASK_RESULT_ALREADY_SUBMITTED", ex.getCode());
  }

  @Test
  void customerAcceptanceRequiresAVerifiedResult() {
    when(repository.lockTaskByNoForUpdate("PUB-9")).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "COMPLETED"));
    when(repository.hasVerifiedResultForTask(9L)).thenReturn(false);

    BusinessException ex = assertThrows(
        BusinessException.class, () -> service.acceptTask("PUB-9"));

    assertEquals("TASK_RESULT_REQUIRED", ex.getCode());
    verify(repository, never()).acceptTask(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void repeatedCustomerAcceptanceIsIdempotent() {
    when(repository.lockTaskByNoForUpdate("PUB-9")).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "CLIENT_ACCEPTED"));

    Map<String, Object> result = service.acceptTask("PUB-9");

    assertEquals("CLIENT_ACCEPTED", result.get("status"));
    verify(repository, never()).acceptTask(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void customerCanAcceptACompletedTaskWithVerifiedEvidence() {
    when(repository.lockTaskByNoForUpdate("PUB-9")).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "COMPLETED"));
    when(repository.hasVerifiedResultForTask(9L)).thenReturn(true);
    when(repository.acceptTask(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L))).thenReturn(true);

    Map<String, Object> result = service.acceptTask("PUB-9");

    assertEquals("CLIENT_ACCEPTED", result.get("status"));
  }

  @Test
  void operatorRecordsInvitationOnlyAfterItIsActuallySent() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L))).thenReturn(true);
    when(repository.mediaInvitationForTask(9L)).thenReturn(Map.of("status", "PENDING"));
    when(repository.updateMediaInvitation(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L),
        org.mockito.ArgumentMatchers.eq("INVITED"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(true);

    Map<String, Object> result = service.updateMediaInvitation(
        9L, new UpdateMediaInvitationRequest("INVITED", "已按项目安排发出邀请"));

    assertEquals("INVITED", result.get("status"));
    assertEquals("IN_PROGRESS", result.get("taskStatus"));
    verify(repository).updateMediaInvitation(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L),
        org.mockito.ArgumentMatchers.eq("INVITED"), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void declinedMediaTargetClosesThatTaskWithoutFabricatingAResult() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
        .thenReturn(true);
    when(repository.mediaInvitationForTask(9L)).thenReturn(Map.of("status", "RESPONDED"));
    when(repository.updateMediaInvitation(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L),
        org.mockito.ArgumentMatchers.eq("DECLINED"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(true);

    Map<String, Object> result = service.updateMediaInvitation(
        9L, new UpdateMediaInvitationRequest("DECLINED", "媒体已明确婉拒本次邀请"));

    assertEquals("DECLINED", result.get("status"));
    assertEquals("NOT_PROCEEDING", result.get("taskStatus"));
  }

  @Test
  void responseCannotBeRecordedBeforeAnInvitationWasSent() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L))).thenReturn(true);
    when(repository.mediaInvitationForTask(9L)).thenReturn(Map.of("status", "PENDING"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateMediaInvitation(9L, new UpdateMediaInvitationRequest("RESPONDED", null)));

    assertEquals("INVALID_MEDIA_INVITATION_TRANSITION", exception.getCode());
    verify(repository, never()).updateMediaInvitation(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void mediaInvitationProgressRequiresAnActualContactNote() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L))).thenReturn(true);
    when(repository.mediaInvitationForTask(9L)).thenReturn(Map.of("status", "PENDING"));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateMediaInvitation(9L, new UpdateMediaInvitationRequest("INVITED", " ")));

    assertEquals("MEDIA_INVITATION_NOTE_REQUIRED", exception.getCode());
    verify(repository, never()).updateMediaInvitation(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void onsiteWritingRequiresScheduleAndLocation() {
    CreateRequirementRequest request = new CreateRequirementRequest(
        "论坛现场采写", null, "", "已确认议程", "完成活动稿", "行业客户",
        "ONSITE_WRITING", 1, 1, "陈经理", "13800000003", "一篇新闻稿",
        null, null, null, null, null, null, null, null, OffsetDateTime.now().plusDays(1));

    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.createRequirement(request, "test-requirement-key-0001"));

    assertEquals("EVENT_TIME_REQUIRED", ex.getCode());
  }

  @Test
  void onsiteWritingUsesFixedDailyRate() {
    when(repository.createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(new RequirementCreation(88L, true));
    CreateRequirementRequest request = new CreateRequirementRequest(
        "论坛现场采写", OffsetDateTime.now().plusDays(2), "上海", "已确认议程", "完成活动稿", "行业客户",
        "ONSITE_WRITING", 2, 1, "陈经理", "13800000003", "一篇新闻稿",
        null, null, null, null, null, null, null, null, OffsetDateTime.now().plusDays(3));

    Map<String, Object> result = service.createRequirement(request, "test-requirement-key-0001");

    assertEquals(88L, result.get("projectId"));
    assertEquals(new BigDecimal("1960.00"), result.get("estimatedAmount"));
  }

  @Test
  void newsConferenceCanStartWithTitleContactAndPhoneOnly() {
    when(repository.createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(new RequirementCreation(89L, true));
    CreateRequirementRequest request = new CreateRequirementRequest(
        "新品发布会", null, null, null, null, null,
        "NEWS_CONFERENCE", null, null, null, null, null,
        null, null, null, null, null, null, "陈经理", "13800000003", null);

    Map<String, Object> result = service.createRequirement(request, "test-requirement-key-0001");

    assertEquals(89L, result.get("projectId"));
    verify(repository).createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(request),
        org.mockito.ArgumentMatchers.eq("test-requirement-key-0001"),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void mediaPrCanStartWithoutObjective() {
    when(repository.createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(new RequirementCreation(90L, true));
    CreateRequirementRequest request = new CreateRequirementRequest(
        "媒体邀请", null, null, "邀请媒体参加新品沟通会", null, null,
        "MEDIA_PR", null, null, null, null, null,
        null, null, null, null, null, null, null, null, null);

    Map<String, Object> result = service.createRequirement(request, "test-requirement-key-0001");

    assertEquals(90L, result.get("projectId"));
    verify(repository).createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(request),
        org.mockito.ArgumentMatchers.eq("test-requirement-key-0001"),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void linkedServiceRequiresAnAccessibleProjectInTheSameCustomerOrganization() {
    CreateRequirementRequest request = new CreateRequirementRequest(
        "同一活动的媒体邀请", null, null, "邀请媒体参加沟通会", null, null,
        "MEDIA_PR", null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, 42L);
    when(repository.activityRootProjectId(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(42L))).thenReturn(null);

    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.createRequirement(request, "test-requirement-key-0001"));

    assertEquals("RELATED_PROJECT_INVALID", ex.getCode());
    verify(repository, never()).createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void linkedServiceStillCreatesItsOwnRequirementAndProject() {
    CreateRequirementRequest request = new CreateRequirementRequest(
        "同一活动的媒体邀请", null, null, "邀请媒体参加沟通会", null, null,
        "MEDIA_PR", null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, 42L);
    when(repository.activityRootProjectId(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(42L))).thenReturn(12L);
    when(repository.createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(request),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(new RequirementCreation(91L, true));

    Map<String, Object> result = service.createRequirement(request, "test-requirement-key-0001");

    assertEquals(91L, result.get("projectId"));
    verify(repository).activityRootProjectId(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(42L));
    verify(repository).createRequirement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(request),
        org.mockito.ArgumentMatchers.eq("test-requirement-key-0001"),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void combinedAndApiServiceTypesCannotBeCreatedAsRegularOrders() {
    for (String serviceType : List.of("ONSITE_WRITING_AND_MEDIA_PR", "INTEGRATED_PROJECT")) {
      CreateRequirementRequest request = new CreateRequirementRequest(
          "不支持的普通订单", null, null, null, null, null,
          serviceType, null, null, null, null, null,
          null, null, null, null, null, null, "陈经理", "13800000003", null);

      BusinessException ex = assertThrows(
          BusinessException.class,
          () -> service.createRequirement(request, "test-requirement-key-0001"));

      assertEquals("INVALID_SERVICE_TYPE", ex.getCode());
    }
  }

  @Test
  void orderLedgerRejectsApiIntegrationAsAServiceType() {
    BusinessException ex = assertThrows(
        BusinessException.class, () -> service.orderRecords("INTEGRATED_PROJECT", null, 1, 20));

    assertEquals("INVALID_SERVICE_TYPE", ex.getCode());
    verify(repository, never()).orderRecords(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void reporterDiscoveryRequiresASelectedMediaContext() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.searchMediaDiscovery(
            "REPORTER", null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, 1, 20));

    assertEquals("MEDIA_ID_REQUIRED", ex.getCode());
    verify(niumediaMediaService, never()).search(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void projectsRejectLegacyOrUnknownServiceFilters() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.projects(null, null, null, "INTEGRATED_PROJECT", 1, 20));

    assertEquals("INVALID_SERVICE_TYPE", ex.getCode());
    verify(repository, never()).projects(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void orderLedgerKeepsOnsiteWritingWithinTheCustomerOrderScope() {
    when(repository.orderRecords(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ONSITE_WRITING"),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(20),
        org.mockito.ArgumentMatchers.eq(0))).thenReturn(List.of(Map.of("recordNo", "WRT-ASG-1")));
    when(repository.orderRecordsCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ONSITE_WRITING"),
        org.mockito.ArgumentMatchers.isNull())).thenReturn(1L);

    var result = service.orderRecords("ONSITE_WRITING", null, 1, 20);

    assertEquals(1L, result.total());
    assertEquals("WRT-ASG-1", result.items().get(0).get("recordNo"));
  }

  @Test
  void customerOrderLedgerOmitsInternalAssignee() {
    Map<String, Object> raw = new HashMap<>(Map.of(
        "recordNo", "PUB-1", "itemType", "PUBLISH_TASK", "itemId", 8L,
        "ownerName", "内部执行人员", "amount", new BigDecimal("980.00"),
        "supplierId", 91L, "costPrice", new BigDecimal("600.00"), "quoteId", 44L,
        "upstreamReference", "provider-only"));
    when(repository.orderRecords(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("MEDIA_PR"),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(20),
        org.mockito.ArgumentMatchers.eq(0))).thenReturn(List.of(raw));
    when(repository.orderRecordsCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("MEDIA_PR"),
        org.mockito.ArgumentMatchers.isNull())).thenReturn(1L);

    var result = service.orderRecords("MEDIA_PR", null, 1, 20);

    assertFalse(result.items().get(0).containsKey("ownerName"));
    assertFalse(result.items().get(0).containsKey("itemType"));
    assertFalse(result.items().get(0).containsKey("itemId"));
    assertFalse(result.items().get(0).containsKey("supplierId"));
    assertFalse(result.items().get(0).containsKey("costPrice"));
    assertFalse(result.items().get(0).containsKey("quoteId"));
    assertFalse(result.items().get(0).containsKey("upstreamReference"));
    assertEquals(new BigDecimal("980.00"), result.items().get(0).get("amount"));
  }

  @Test
  void customerSettlementLedgerUsesPositiveFieldProjection() {
    Map<String, Object> raw = new HashMap<>(Map.ofEntries(
        Map.entry("settlementNo", "SET-1"), Map.entry("projectId", 1L),
        Map.entry("projectNo", "PRJ-1"), Map.entry("projectName", "客户项目"),
        Map.entry("amount", new BigDecimal("980.00")), Map.entry("paidAmount", BigDecimal.ZERO),
        Map.entry("currency", "CNY"), Map.entry("status", "PENDING"),
        Map.entry("updatedAt", "2026-07-29T00:00:00Z"),
        Map.entry("supplierId", 91L), Map.entry("costPrice", new BigDecimal("600.00")),
        Map.entry("upstreamReference", "provider-only"), Map.entry("operatorName", "内部执行人员")));
    when(repository.customerSettlementRecords(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("PENDING"),
        org.mockito.ArgumentMatchers.eq(20), org.mockito.ArgumentMatchers.eq(0)))
        .thenReturn(List.of(raw));
    when(repository.customerSettlementRecordsCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("PENDING")))
        .thenReturn(1L);

    var result = service.settlementRecords("PENDING", 1, 20);

    assertEquals(1L, result.total());
    assertEquals("SET-1", result.items().get(0).get("settlementNo"));
    assertEquals(new BigDecimal("980.00"), result.items().get(0).get("amount"));
    assertFalse(result.items().get(0).containsKey("supplierId"));
    assertFalse(result.items().get(0).containsKey("costPrice"));
    assertFalse(result.items().get(0).containsKey("upstreamReference"));
    assertFalse(result.items().get(0).containsKey("operatorName"));
  }

  @Test
  void retiredCombinedSettlementIsReturnedOnlyThroughTheCustomerArchive() {
    Map<String, Object> raw = new HashMap<>(Map.ofEntries(
        Map.entry("settlementNo", "SET-LEGACY-1"), Map.entry("projectId", 1L),
        Map.entry("projectNo", "PRJ-LEGACY-1"), Map.entry("projectName", "历史组合项目"),
        Map.entry("serviceType", "WRITING_AND_PUBLISHING"),
        Map.entry("serviceLabel", "历史组合记录"), Map.entry("archiveOnly", true),
        Map.entry("amount", new BigDecimal("1080.00")), Map.entry("paidAmount", BigDecimal.ZERO),
        Map.entry("currency", "CNY"), Map.entry("status", "PENDING"),
        Map.entry("updatedAt", "2026-07-29T00:00:00Z"),
        Map.entry("supplierId", 91L), Map.entry("costPrice", new BigDecimal("600.00"))));
    when(repository.customerArchivedSettlementRecords(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.eq(20), org.mockito.ArgumentMatchers.eq(0)))
        .thenReturn(List.of(raw));
    when(repository.customerArchivedSettlementRecordsCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(1L);

    var result = service.archivedSettlementRecords(null, 1, 20);

    assertEquals(1L, result.total());
    assertEquals(true, result.items().get(0).get("archiveOnly"));
    assertEquals("历史组合记录", result.items().get(0).get("serviceLabel"));
    assertFalse(result.items().get(0).containsKey("supplierId"));
    assertFalse(result.items().get(0).containsKey("costPrice"));
  }

  @Test
  void customerQueuesAndPublishPlansUsePositiveFieldProjections() {
    Map<String, Object> workItem = new HashMap<>(Map.ofEntries(
        Map.entry("itemType", "PUBLISH_TASK"), Map.entry("itemId", 8L), Map.entry("recordNo", "PUB-1"), Map.entry("projectId", 1L),
        Map.entry("projectName", "客户项目"), Map.entry("title", "行业媒体"), Map.entry("status", "PENDING"),
        Map.entry("updatedAt", "2026-07-29T00:00:00Z"), Map.entry("itemLabel", "直编发稿"),
        Map.entry("operatorName", "内部执行人员"), Map.entry("supplierId", 91L),
        Map.entry("costPrice", new BigDecimal("600.00"))));
    when(repository.workItems(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(20),
        org.mockito.ArgumentMatchers.eq(0))).thenReturn(List.of(workItem));
    when(repository.workItemsCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

    Map<String, Object> taskRecord = new HashMap<>(Map.ofEntries(
        Map.entry("itemType", "PUBLISH_TASK"), Map.entry("itemId", 8L), Map.entry("recordNo", "PUB-1"),
        Map.entry("projectId", 1L), Map.entry("projectName", "客户项目"), Map.entry("title", "行业媒体"),
        Map.entry("itemLabel", "直编发稿"), Map.entry("status", "PENDING"), Map.entry("note", "内部执行备注"),
        Map.entry("updatedAt", "2026-07-29T00:00:00Z"), Map.entry("supplierName", "内部供应商"),
        Map.entry("costPrice", new BigDecimal("600.00")), Map.entry("quoteId", 44L)));
    when(repository.taskRecords(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(20),
        org.mockito.ArgumentMatchers.eq(0))).thenReturn(List.of(taskRecord));
    when(repository.taskRecordsCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

    Map<String, Object> plan = new HashMap<>(Map.ofEntries(
        Map.entry("id", 77L), Map.entry("planNo", "PLAN-1"), Map.entry("projectId", 1L),
        Map.entry("planName", "客户计划"), Map.entry("estimatedAmount", new BigDecimal("980.00")),
        Map.entry("currency", "CNY"), Map.entry("exclusiveMediaPr", true),
        Map.entry("lockExpiresAt", "2026-07-30T00:00:00Z"), Map.entry("status", "WAITING_CONFIRMATION"),
        Map.entry("createdAt", "2026-07-29T00:00:00Z"), Map.entry("itemCount", 1),
        Map.entry("supplierId", 91L), Map.entry("costPrice", new BigDecimal("600.00")), Map.entry("quoteId", 44L)));
    when(repository.publishPlansForService(1L, "DIRECT_PUBLISHING")).thenReturn(List.of(plan));

    var workItems = service.workItems(1, 20);
    var taskRecords = service.taskRecords(1, 20);
    var plans = service.publishPlans(1L);

    assertEquals("行业媒体", workItems.items().get(0).get("title"));
    assertEquals("PUB-1", workItems.items().get(0).get("recordNo"));
    assertFalse(workItems.items().get(0).containsKey("itemType"));
    assertFalse(workItems.items().get(0).containsKey("itemId"));
    assertFalse(workItems.items().get(0).containsKey("operatorName"));
    assertFalse(workItems.items().get(0).containsKey("supplierId"));
    assertFalse(workItems.items().get(0).containsKey("costPrice"));
    assertFalse(taskRecords.items().get(0).containsKey("note"));
    assertFalse(taskRecords.items().get(0).containsKey("itemType"));
    assertFalse(taskRecords.items().get(0).containsKey("itemId"));
    assertFalse(taskRecords.items().get(0).containsKey("supplierName"));
    assertFalse(taskRecords.items().get(0).containsKey("costPrice"));
    assertFalse(taskRecords.items().get(0).containsKey("quoteId"));
    assertEquals(new BigDecimal("980.00"), plans.get(0).get("estimatedAmount"));
    assertFalse(plans.get(0).containsKey("id"));
    assertFalse(plans.get(0).containsKey("supplierId"));
    assertFalse(plans.get(0).containsKey("costPrice"));
    assertFalse(plans.get(0).containsKey("quoteId"));
    assertFalse(plans.get(0).containsKey("exclusiveMediaPr"));
    assertFalse(plans.get(0).containsKey("lockExpiresAt"));
  }

  @Test
  void customerConferenceProjectDoesNotExposeHistoricalCrossServicePlans() {
    when(repository.projectRequestedService(1L)).thenReturn("NEWS_CONFERENCE");

    List<Map<String, Object>> plans = service.publishPlans(1L);

    assertEquals(List.of(), plans);
    verify(repository, never()).publishPlans(1L);
    verify(repository, never()).publishPlansForService(
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void customerCanOpenOnlyPendingPublishPlanConfirmationsWithoutInternalFields() {
    Map<String, Object> planConfirmation = new HashMap<>(Map.ofEntries(
        Map.entry("itemType", "PUBLISH_PLAN_CONFIRMATION"), Map.entry("itemId", 77L),
        Map.entry("recordNo", "PLAN-77"), Map.entry("projectId", 1L),
        Map.entry("projectName", "客户项目"), Map.entry("title", "本季度传播计划"),
        Map.entry("status", "WAITING_CONFIRMATION"),
        Map.entry("updatedAt", "2026-07-29T00:00:00Z"),
        Map.entry("itemLabel", "发布计划确认"), Map.entry("supplierId", 91L),
        Map.entry("costPrice", new BigDecimal("600.00")),
        Map.entry("operatorName", "内部执行人员")));
    when(repository.workItems(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("planConfirmation"),
        org.mockito.ArgumentMatchers.eq(20), org.mockito.ArgumentMatchers.eq(0)))
        .thenReturn(List.of(planConfirmation));
    when(repository.workItemsCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("planConfirmation")))
        .thenReturn(1L);

    var page = service.workItems("planConfirmation", 1, 20);

    assertEquals(1L, page.total());
    assertEquals("PLAN-77", page.items().get(0).get("recordNo"));
    assertEquals("发布计划确认", page.items().get(0).get("itemLabel"));
    assertFalse(page.items().get(0).containsKey("itemType"));
    assertFalse(page.items().get(0).containsKey("itemId"));
    assertFalse(page.items().get(0).containsKey("supplierId"));
    assertFalse(page.items().get(0).containsKey("costPrice"));
    assertFalse(page.items().get(0).containsKey("operatorName"));
  }

  @Test
  void customerPendingPlatformExecutionQueueKeepsFourServiceProjectionSafe() {
    Map<String, Object> execution = new HashMap<>(Map.ofEntries(
        Map.entry("itemType", "WRITING_ASSIGNMENT"), Map.entry("itemId", 88L),
        Map.entry("recordNo", "WRA-88"), Map.entry("projectId", 1L),
        Map.entry("projectName", "客户项目"), Map.entry("title", "活动现场采写"),
        Map.entry("serviceType", "ONSITE_WRITING"),
        Map.entry("itemLabel", "云采写"), Map.entry("status", "WAITING_MATCH"),
        Map.entry("note", "内部匹配规则"),
        Map.entry("updatedAt", "2026-07-29T00:00:00Z"),
        Map.entry("ownerName", "内部执行人员"), Map.entry("supplierId", 91L),
        Map.entry("costPrice", new BigDecimal("600.00"))));
    when(repository.taskRecords(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("pendingExecution"),
        org.mockito.ArgumentMatchers.eq(20), org.mockito.ArgumentMatchers.eq(0)))
        .thenReturn(List.of(execution));
    when(repository.taskRecordsCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("pendingExecution")))
        .thenReturn(1L);

    var page = service.taskRecords("pendingExecution", 1, 20);

    assertEquals(1L, page.total());
    assertEquals("WRA-88", page.items().get(0).get("recordNo"));
    assertEquals("ONSITE_WRITING", page.items().get(0).get("serviceType"));
    assertEquals("云采写", page.items().get(0).get("itemLabel"));
    assertFalse(page.items().get(0).containsKey("itemType"));
    assertFalse(page.items().get(0).containsKey("itemId"));
    assertFalse(page.items().get(0).containsKey("note"));
    assertFalse(page.items().get(0).containsKey("ownerName"));
    assertFalse(page.items().get(0).containsKey("supplierId"));
    assertFalse(page.items().get(0).containsKey("costPrice"));
  }

  @Test
  void taskRecordScopeRejectsUnknownFilters() {
    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.taskRecords("supplierExecution", 1, 20));

    assertEquals("INVALID_TASK_RECORD_SCOPE", ex.getCode());
  }

  @Test
  void customerMediaTaskIncludesVerifiableInvitationMilestonesButNotContactNotes() {
    Map<String, Object> task = new HashMap<>(Map.ofEntries(
        Map.entry("id", 81L), Map.entry("taskNo", "PUB-MEDIA-1"), Map.entry("projectId", 1L),
        Map.entry("projectNo", "PRJ-1"), Map.entry("projectName", "客户媒体邀请项目"),
        Map.entry("channelType", "MEDIA_PR"), Map.entry("channelName", "产业观察"),
        Map.entry("status", "IN_PROGRESS"), Map.entry("mediaInvitationStatus", "RESPONDED"),
        Map.entry("mediaInvitedAt", "2026-07-30T01:00:00+08:00"),
        Map.entry("mediaRespondedAt", "2026-07-30T02:00:00+08:00"),
        Map.entry("updatedAt", "2026-07-30T02:00:00+08:00"),
        Map.entry("executionNote", "内部沟通说明"), Map.entry("operatorName", "内部执行人员"),
        Map.entry("externalReporterId", "provider-reporter-id"), Map.entry("supplierId", 91L)));
    when(repository.taskByNo("PUB-MEDIA-1")).thenReturn(task);

    Map<String, Object> result = service.customerTask("PUB-MEDIA-1");

    assertEquals("RESPONDED", result.get("mediaInvitationStatus"));
    assertEquals("2026-07-30T01:00:00+08:00", result.get("mediaInvitedAt"));
    assertEquals("2026-07-30T02:00:00+08:00", result.get("mediaRespondedAt"));
    assertFalse(result.containsKey("id"));
    assertFalse(result.containsKey("executionNote"));
    assertFalse(result.containsKey("operatorName"));
    assertFalse(result.containsKey("externalReporterId"));
    assertFalse(result.containsKey("supplierId"));
  }

  @Test
  void customerProjectAndTaskViewsUsePositiveFieldProjections() {
    Map<String, Object> projectSummary = new HashMap<>(Map.ofEntries(
        Map.entry("id", 1L), Map.entry("projectNo", "PRJ-1"), Map.entry("projectName", "客户项目"),
        Map.entry("status", "PLANNING"), Map.entry("organizationName", "客户组织"),
        Map.entry("taskCount", 1L), Map.entry("resultCount", 0L),
        Map.entry("operatorName", "内部执行人员"), Map.entry("budget", new BigDecimal("980.00")),
        Map.entry("supplierId", 91L), Map.entry("costPrice", new BigDecimal("600.00"))));
    when(repository.projects(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(20),
        org.mockito.ArgumentMatchers.eq(0))).thenReturn(List.of(projectSummary));
    when(repository.projectsCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull())).thenReturn(1L);

    Map<String, Object> task = new HashMap<>(Map.ofEntries(
        Map.entry("id", 81L), Map.entry("taskNo", "PUB-1"), Map.entry("projectId", 1L),
        Map.entry("projectNo", "PRJ-1"), Map.entry("projectName", "客户项目"),
        Map.entry("manuscriptId", 31L), Map.entry("manuscriptTitle", "客户定稿"),
        Map.entry("channelType", "DIRECT_PUBLISHING"), Map.entry("channelName", "产业观察"),
        Map.entry("status", "PENDING_EXECUTION"), Map.entry("updatedAt", "2026-07-29T00:00:00Z"),
        Map.entry("executionNote", "内部执行备注"), Map.entry("exceptionReason", "内部异常"),
        Map.entry("operatorName", "内部执行人员"), Map.entry("supplierId", 91L),
        Map.entry("costPrice", new BigDecimal("600.00")), Map.entry("upstreamReference", "provider-only")));
    when(repository.tasks(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.eq(20), org.mockito.ArgumentMatchers.eq(0))).thenReturn(List.of(task));
    when(repository.tasksCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull())).thenReturn(1L);
    when(repository.taskByNo("PUB-1")).thenReturn(task);

    Map<String, Object> projectDetail = new HashMap<>();
    projectDetail.put("project", new HashMap<>(Map.ofEntries(
        Map.entry("id", 1L), Map.entry("projectNo", "PRJ-1"), Map.entry("projectName", "客户项目"),
        Map.entry("status", "PLANNING"), Map.entry("operatorName", "内部执行人员"),
        Map.entry("budget", new BigDecimal("980.00")), Map.entry("supplierId", 91L))));
    projectDetail.put("conference", new HashMap<>(Map.ofEntries(
        Map.entry("id", 11L), Map.entry("conferenceNo", "CONF-1"), Map.entry("theme", "客户活动"),
        Map.entry("status", "PLANNING"), Map.entry("operatorName", "内部执行人员"))));
    projectDetail.put("conferenceWorkItems", List.of(new HashMap<>(Map.ofEntries(
        Map.entry("id", 51L), Map.entry("itemNo", "CWI-1"), Map.entry("sortOrder", 1),
        Map.entry("phase", "PRE_EVENT"), Map.entry("title", "确认议程"), Map.entry("detail", "客户可核对的事项"),
        Map.entry("status", "PENDING"), Map.entry("assignedOperatorId", 2L),
        Map.entry("operatorName", "内部执行人员"), Map.entry("note", "内部跟进记录")))));
    projectDetail.put("conferenceMediaCandidates", List.of(new HashMap<>(Map.ofEntries(
        Map.entry("id", 61L), Map.entry("displayName", "产业观察"), Map.entry("candidateKey", "SEL-provider-ref"),
        Map.entry("mediaId", "provider-media-id"), Map.entry("reporterId", "provider-reporter-id"),
        Map.entry("operationNote", "internal note"), Map.entry("operatorName", "内部执行人员"),
        Map.entry("score", 88.0), Map.entry("newsCount", 120L), Map.entry("status", "CANDIDATE")))));
    projectDetail.put("versions", List.of(new HashMap<>(Map.ofEntries(
        Map.entry("id", 91L), Map.entry("manuscriptId", 31L), Map.entry("versionNo", "MS-V-1"),
        Map.entry("versionNumber", 1), Map.entry("title", "客户定稿"), Map.entry("status", "APPROVED"),
        Map.entry("sourceProjectName", "客户原项目"), Map.entry("sourceManuscriptTitle", "原始客户定稿"),
        Map.entry("sourceProjectId", 6L), Map.entry("sourceManuscriptId", 18L),
        Map.entry("supplierId", 91L), Map.entry("costPrice", new BigDecimal("600.00"))))));
    projectDetail.put("tasks", List.of(task));
    when(repository.projectDetail(1L)).thenReturn(projectDetail);
    when(repository.activityProjects(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L)))
        .thenReturn(List.of());

    var list = service.projects(null, null, null, null, 1, 20);
    var taskList = service.tasks(null, null, null, 1, 20);
    var taskDetail = service.customerTask("PUB-1");
    var detail = service.project(1L);

    assertFalse(list.items().get(0).containsKey("operatorName"));
    assertFalse(list.items().get(0).containsKey("budget"));
    assertFalse(list.items().get(0).containsKey("supplierId"));
    assertFalse(list.items().get(0).containsKey("costPrice"));
    assertEquals("PUB-1", taskList.items().get(0).get("taskNo"));
    assertFalse(taskList.items().get(0).containsKey("id"));
    assertFalse(taskList.items().get(0).containsKey("manuscriptId"));
    assertFalse(taskList.items().get(0).containsKey("executionNote"));
    assertFalse(taskList.items().get(0).containsKey("exceptionReason"));
    assertFalse(taskList.items().get(0).containsKey("operatorName"));
    assertFalse(taskList.items().get(0).containsKey("supplierId"));
    assertFalse(taskList.items().get(0).containsKey("costPrice"));
    assertFalse(taskDetail.containsKey("id"));
    assertFalse(taskDetail.containsKey("upstreamReference"));
    @SuppressWarnings("unchecked")
    Map<String, Object> project = (Map<String, Object>) detail.get("project");
    assertFalse(project.containsKey("operatorName"));
    assertFalse(project.containsKey("budget"));
    assertFalse(project.containsKey("supplierId"));
    assertEquals("客户项目", project.get("projectName"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> workItems = (List<Map<String, Object>>) detail.get("conferenceWorkItems");
    assertEquals("确认议程", workItems.get(0).get("title"));
    assertFalse(workItems.get(0).containsKey("id"));
    assertFalse(workItems.get(0).containsKey("assignedOperatorId"));
    assertFalse(workItems.get(0).containsKey("operatorName"));
    assertFalse(workItems.get(0).containsKey("note"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> candidates = (List<Map<String, Object>>) detail.get("conferenceMediaCandidates");
    assertEquals("产业观察", candidates.get(0).get("displayName"));
    assertFalse(candidates.get(0).containsKey("id"));
    assertFalse(candidates.get(0).containsKey("candidateKey"));
    assertFalse(candidates.get(0).containsKey("mediaId"));
    assertFalse(candidates.get(0).containsKey("reporterId"));
    assertFalse(candidates.get(0).containsKey("operationNote"));
    assertFalse(candidates.get(0).containsKey("operatorName"));
    assertFalse(candidates.get(0).containsKey("score"));
    assertFalse(candidates.get(0).containsKey("newsCount"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> versions = (List<Map<String, Object>>) detail.get("versions");
    assertEquals("客户原项目", versions.get(0).get("sourceProjectName"));
    assertEquals("原始客户定稿", versions.get(0).get("sourceManuscriptTitle"));
    assertFalse(versions.get(0).containsKey("sourceProjectId"));
    assertFalse(versions.get(0).containsKey("sourceManuscriptId"));
    assertFalse(versions.get(0).containsKey("supplierId"));
    assertFalse(versions.get(0).containsKey("costPrice"));
  }

  @Test
  void operatorCannotEnumerateCustomerRequirementLedger() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));

    BusinessException exception = assertThrows(
        BusinessException.class, () -> service.requirements(null, 1, 20));

    assertEquals("FORBIDDEN", exception.getCode());
    verify(repository, never()).requirements(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void manualQuoteCreatesANewVersionForAnActiveDirectPublishingChannel() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("channel:manage")));
    when(repository.pricingChannel(30L)).thenReturn(Map.of(
        "id", 30L, "channelType", "DIRECT_PUBLISHING", "channelStatus", "ACTIVE",
        "customerPrice", new BigDecimal("100.00")));
    when(repository.replaceDirectQuote(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(30L),
        org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.any(BigDecimal.class), org.mockito.ArgumentMatchers.any(BigDecimal.class),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Map.of("channelId", 30L, "quoteId", 88L));

    Map<String, Object> result = service.createQuote(new CreateQuoteRequest(
        30L, null, new BigDecimal("80"), new BigDecimal("128.5"), OffsetDateTime.now().plusDays(30),
        "栏目及排期以复核结果为准。", "年度价格调整"));

    assertEquals(88L, result.get("quoteId"));
    verify(repository).lockPricingChannelForUpdate(30L);
    verify(repository).replaceDirectQuote(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(30L),
        org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.eq(new BigDecimal("80.00")),
        org.mockito.ArgumentMatchers.eq(new BigDecimal("128.50")), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("年度价格调整"),
        org.mockito.ArgumentMatchers.eq("MANUAL"));
  }

  @Test
  void batchQuoteAdjustmentRequiresAnIdempotencyKey() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("channel:manage")));

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.batchAdjustQuotes(new BatchQuoteAdjustmentRequest(
            List.of(20L), new BigDecimal("10"), OffsetDateTime.now().plusDays(30),
            "提交后复核稿件、栏目和排期。", "批量年度调价"), null));

    assertEquals("IDEMPOTENCY_KEY_REQUIRED", exception.getCode());
    verify(repository, never()).lockOrCreateQuoteAdjustmentBatch(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void batchQuoteAdjustmentLocksChannelsInStableOrderBeforeReplacingPrices() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("channel:manage")));
    when(repository.pricingChannel(20L)).thenReturn(Map.of(
        "id", 20L, "channelType", "DIRECT_PUBLISHING", "channelStatus", "ACTIVE",
        "costPrice", new BigDecimal("60.00"), "customerPrice", new BigDecimal("100.00")));
    when(repository.pricingChannel(30L)).thenReturn(Map.of(
        "id", 30L, "channelType", "DIRECT_PUBLISHING", "channelStatus", "ACTIVE",
        "costPrice", new BigDecimal("80.00"), "customerPrice", new BigDecimal("120.00")));
    when(repository.lockOrCreateQuoteAdjustmentBatch(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(BATCH_QUOTE_KEY),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(2)))
        .thenAnswer(invocation -> Map.of(
            "id", 77L,
            "submissionHash", invocation.getArgument(2, String.class),
            "channelCount", 2L,
            "adjustedCount", 0L,
            "status", "PROCESSING",
            "created", true));

    Map<String, Object> result = service.batchAdjustQuotes(new BatchQuoteAdjustmentRequest(
        List.of(30L, 20L), new BigDecimal("10"), OffsetDateTime.now().plusDays(30),
        "提交后复核稿件、栏目和排期。", "批量年度调价"), BATCH_QUOTE_KEY);

    assertEquals(2, result.get("adjustedCount"));
    var order = org.mockito.Mockito.inOrder(repository);
    order.verify(repository).lockOrCreateQuoteAdjustmentBatch(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(BATCH_QUOTE_KEY),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(2));
    order.verify(repository).lockPricingChannelForUpdate(20L);
    order.verify(repository).lockPricingChannelForUpdate(30L);
    verify(repository).completeQuoteAdjustmentBatch(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(77L),
        org.mockito.ArgumentMatchers.eq(2));
  }

  @Test
  void batchQuoteAdjustmentRetryReturnsTheOriginalBatchWithoutCompoundingPrices() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("channel:manage")));
    when(repository.lockOrCreateQuoteAdjustmentBatch(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(BATCH_QUOTE_KEY),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(2)))
        .thenAnswer(invocation -> Map.of(
            "id", 77L,
            "submissionHash", invocation.getArgument(2, String.class),
            "channelCount", 2L,
            "adjustedCount", 2L,
            "status", "COMPLETED",
            "created", false));
    when(repository.quoteAdjustmentBatchItems(77L)).thenReturn(List.of(
        Map.of("channelId", 20L, "quoteId", 201L, "customerPrice", new BigDecimal("110.00")),
        Map.of("channelId", 30L, "quoteId", 301L, "customerPrice", new BigDecimal("132.00"))));

    Map<String, Object> result = service.batchAdjustQuotes(new BatchQuoteAdjustmentRequest(
        List.of(30L, 20L), new BigDecimal("10.0"), OffsetDateTime.now().plusDays(30),
        "提交后复核稿件、栏目和排期。", "批量年度调价"), BATCH_QUOTE_KEY);

    assertEquals(2, result.get("adjustedCount"));
    verify(repository, never()).lockPricingChannelForUpdate(
        org.mockito.ArgumentMatchers.anyLong());
    verify(repository, never()).completeQuoteAdjustmentBatch(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void batchQuoteAdjustmentRejectsAReusedKeyForDifferentContent() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("channel:manage")));
    when(repository.lockOrCreateQuoteAdjustmentBatch(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(BATCH_QUOTE_KEY),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1)))
        .thenReturn(Map.of(
            "id", 77L,
            "submissionHash", "different-request-hash",
            "channelCount", 1L,
            "adjustedCount", 1L,
            "status", "COMPLETED",
            "created", false));

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.batchAdjustQuotes(new BatchQuoteAdjustmentRequest(
            List.of(20L), new BigDecimal("10"), OffsetDateTime.now().plusDays(30),
            "提交后复核稿件、栏目和排期。", "另一批调价"), BATCH_QUOTE_KEY));

    assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getCode());
    verify(repository, never()).lockPricingChannelForUpdate(
        org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void directPublishingChannelRejectsAnExpiredOrNonPositiveQuote() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("channel:manage")));
    CreateChannelRequest invalidPrice = new CreateChannelRequest(
        "测试渠道", "DIRECT_PUBLISHING", "科技", "全国", "网站图文", 2, true, null,
        BigDecimal.ZERO, null, OffsetDateTime.now().plusDays(1));
    BusinessException priceException = assertThrows(BusinessException.class, () -> service.createChannel(invalidPrice));
    assertEquals("INVALID_QUOTE_PRICE", priceException.getCode());

    CreateChannelRequest expiredQuote = new CreateChannelRequest(
        "测试渠道", "DIRECT_PUBLISHING", "科技", "全国", "网站图文", 2, true, null,
        new BigDecimal("100"), null, OffsetDateTime.now().minusMinutes(1));
    BusinessException validityException = assertThrows(BusinessException.class, () -> service.createChannel(expiredQuote));
    assertEquals("INVALID_QUOTE_VALIDITY", validityException.getCode());
  }

  @Test
  void customerTransactionLedgerExcludesInternalAuditFields() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("transactionNo", "TRX-1");
    raw.put("settlementNo", "SET-1");
    raw.put("projectId", 1L);
    raw.put("projectNo", "PRJ-1");
    raw.put("projectName", "客户项目");
    raw.put("serviceType", "DIRECT_PUBLISHING");
    raw.put("serviceLabel", "直编发稿");
    raw.put("archiveOnly", false);
    raw.put("transactionType", "PAYMENT");
    raw.put("transactionLabel", "收款");
    raw.put("amount", new BigDecimal("100.00"));
    raw.put("currency", "CNY");
    raw.put("occurredAt", OffsetDateTime.now());
    raw.put("status", "CONFIRMED");
    raw.put("createdAt", OffsetDateTime.now());
    raw.put("updatedAt", OffsetDateTime.now());
    raw.put("internalNote", "仅平台可见");
    raw.put("createdByName", "财务管理员");
    raw.put("voidReason", "内部原因");
    when(repository.customerSettlementTransactions(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(raw));
    when(repository.customerSettlementTransactionsCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull())).thenReturn(1L);

    var result = service.settlementTransactionRecords(null, null, 1, 20);

    Map<String, Object> item = result.items().get(0);
    assertEquals("TRX-1", item.get("transactionNo"));
    assertEquals("DIRECT_PUBLISHING", item.get("serviceType"));
    assertEquals(false, item.get("archiveOnly"));
    assertFalse(item.containsKey("internalNote"));
    assertFalse(item.containsKey("createdByName"));
    assertFalse(item.containsKey("voidReason"));
  }

  @Test
  void archivedTransactionLedgerUsesASeparateReadOnlyCustomerProjection() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("transactionNo", "TRX-LEGACY-1");
    raw.put("settlementNo", "SET-LEGACY-1");
    raw.put("projectId", 1L);
    raw.put("projectNo", "PRJ-LEGACY-1");
    raw.put("projectName", "历史组合项目");
    raw.put("serviceType", "WRITING_AND_PUBLISHING");
    raw.put("serviceLabel", "历史组合记录");
    raw.put("archiveOnly", true);
    raw.put("transactionType", "PAYMENT");
    raw.put("transactionLabel", "收款");
    raw.put("amount", new BigDecimal("100.00"));
    raw.put("currency", "CNY");
    raw.put("occurredAt", OffsetDateTime.now());
    raw.put("status", "CONFIRMED");
    raw.put("createdAt", OffsetDateTime.now());
    raw.put("updatedAt", OffsetDateTime.now());
    raw.put("internalNote", "仅平台可见");
    raw.put("createdByName", "财务管理员");
    raw.put("voidReason", "内部原因");
    when(repository.customerArchivedSettlementTransactions(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(raw));
    when(repository.customerArchivedSettlementTransactionsCount(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull())).thenReturn(1L);

    var result = service.archivedSettlementTransactionRecords(null, null, 1, 20);

    Map<String, Object> item = result.items().get(0);
    assertEquals("TRX-LEGACY-1", item.get("transactionNo"));
    assertEquals("WRITING_AND_PUBLISHING", item.get("serviceType"));
    assertEquals("历史组合记录", item.get("serviceLabel"));
    assertEquals(true, item.get("archiveOnly"));
    assertFalse(item.containsKey("internalNote"));
    assertFalse(item.containsKey("createdByName"));
    assertFalse(item.containsKey("voidReason"));
    verify(repository, never()).customerSettlementTransactions(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void settlementCannotBeMarkedPaidWithoutRecordedEvidence() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("settlement:manage")));
    when(repository.lockSettlementForUpdate(9L)).thenReturn(Map.of(
        "id", 9L,
        "status", "CONFIRMED",
        "paidAmount", BigDecimal.ZERO,
        "outstandingAmount", new BigDecimal("100.00"),
        "transactionCount", 0L));

    BusinessException exception = assertThrows(
        BusinessException.class, () -> service.updateSettlement(9L, "PAID", null));

    assertEquals("SETTLEMENT_NOT_BALANCED", exception.getCode());
    verify(repository, never()).updateSettlement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void archivedCombinedSettlementCannotBeChangedOrReceiveNewTransactions() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("settlement:manage")));
    when(repository.lockSettlementForUpdate(9L)).thenReturn(Map.of(
        "id", 9L,
        "status", "PENDING",
        "archiveOnly", true,
        "paidAmount", BigDecimal.ZERO,
        "outstandingAmount", new BigDecimal("1080.00"),
        "transactionCount", 0L));

    BusinessException updateException = assertThrows(
        BusinessException.class, () -> service.updateSettlement(9L, "CONFIRMED", null));
    BusinessException transactionException = assertThrows(
        BusinessException.class, () -> service.createSettlementTransaction(
            9L, new CreateSettlementTransactionRequest(
                "PAYMENT", new BigDecimal("100"), OffsetDateTime.now(),
                "BANK-LEGACY-1", "历史记录", null),
            SETTLEMENT_TRANSACTION_KEY));

    assertEquals("ARCHIVED_SETTLEMENT_READ_ONLY", updateException.getCode());
    assertEquals("ARCHIVED_SETTLEMENT_READ_ONLY", transactionException.getCode());
    verify(repository, never()).updateSettlement(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    verify(repository, never()).createSettlementTransaction(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void archivedCombinedSettlementTransactionCannotBeVoided() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("settlement:manage")));
    when(repository.lockSettlementTransactionForUpdate(19L)).thenReturn(Map.of(
        "id", 19L,
        "settlementId", 9L,
        "status", "CONFIRMED",
        "archiveOnly", true));

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.voidSettlementTransaction(
            19L, new VoidSettlementTransactionRequest("历史记录保持只读")));

    assertEquals("ARCHIVED_SETTLEMENT_READ_ONLY", exception.getCode());
    verify(repository, never()).voidSettlementTransaction(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void paymentRequiresCustomerVisibleEvidenceOrAReferenceNumber() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("settlement:manage")));
    CreateSettlementTransactionRequest request = new CreateSettlementTransactionRequest(
        "PAYMENT", new BigDecimal("100"), OffsetDateTime.now(), " ", " ", "内部备注");

    BusinessException exception = assertThrows(
        BusinessException.class, () -> service.createSettlementTransaction(
            9L, request, SETTLEMENT_TRANSACTION_KEY));

    assertEquals("SETTLEMENT_TRANSACTION_EVIDENCE_REQUIRED", exception.getCode());
    verify(repository, never()).lockSettlementForUpdate(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void confirmedSettlementAcceptsAnEvidenceBackedPartialPayment() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("settlement:manage")));
    when(repository.lockSettlementForUpdate(9L)).thenReturn(Map.of(
        "id", 9L,
        "status", "CONFIRMED",
        "paidAmount", BigDecimal.ZERO,
        "outstandingAmount", new BigDecimal("100.00"),
        "transactionCount", 0L));
    when(repository.createSettlementTransaction(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString())).thenReturn(Map.of(
        "transactionNo", "TRX-9", "status", "CONFIRMED"));

    Map<String, Object> result = service.createSettlementTransaction(
        9L, new CreateSettlementTransactionRequest(
            "payment", new BigDecimal("30"), OffsetDateTime.now(),
            "BANK-20260729", "首笔收款", "已复核"),
        SETTLEMENT_TRANSACTION_KEY);

    assertEquals("TRX-9", result.get("transactionNo"));
    ArgumentCaptor<CreateSettlementTransactionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateSettlementTransactionRequest.class);
    verify(repository).createSettlementTransaction(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L),
        requestCaptor.capture(), org.mockito.ArgumentMatchers.eq(SETTLEMENT_TRANSACTION_KEY),
        org.mockito.ArgumentMatchers.anyString());
    assertEquals("PAYMENT", requestCaptor.getValue().transactionType());
    assertEquals(new BigDecimal("30.00"), requestCaptor.getValue().amount());
  }

  @Test
  void settlementTransactionRequiresAnIdempotencyKey() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("settlement:manage")));
    CreateSettlementTransactionRequest request = new CreateSettlementTransactionRequest(
        "PAYMENT", new BigDecimal("30"), OffsetDateTime.now(),
        "BANK-20260730", "首笔收款", null);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.createSettlementTransaction(9L, request, null));

    assertEquals("IDEMPOTENCY_KEY_REQUIRED", exception.getCode());
    verify(repository, never()).lockSettlementForUpdate(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void settlementTransactionRetryReturnsTheOriginalFinancialFact() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("settlement:manage")));
    CreateSettlementTransactionRequest request = new CreateSettlementTransactionRequest(
        "PAYMENT", new BigDecimal("100"), OffsetDateTime.now(),
        "BANK-RETRY-20260730", "已到账", null);
    when(repository.lockSettlementForUpdate(9L)).thenReturn(Map.of(
        "id", 9L,
        "status", "PAID",
        "archiveOnly", false,
        "paidAmount", new BigDecimal("100.00"),
        "outstandingAmount", BigDecimal.ZERO,
        "transactionCount", 1L));
    when(repository.existingSettlementTransaction(
        org.mockito.ArgumentMatchers.eq(9L),
        org.mockito.ArgumentMatchers.eq(SETTLEMENT_TRANSACTION_KEY),
        org.mockito.ArgumentMatchers.anyString())).thenReturn(Map.of(
        "id", 19L,
        "transactionNo", "TRX-19",
        "transactionType", "PAYMENT",
        "amount", new BigDecimal("100.00"),
        "status", "CONFIRMED"));

    Map<String, Object> result = service.createSettlementTransaction(
        9L, request, SETTLEMENT_TRANSACTION_KEY);

    assertEquals("TRX-19", result.get("transactionNo"));
    verify(repository, never()).createSettlementTransaction(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void missingSettlementDoesNotReturnASuccessResponse() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("settlement:manage")));
    when(repository.updateSettlement(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(99L),
        org.mockito.ArgumentMatchers.eq("PAID"), org.mockito.ArgumentMatchers.any())).thenReturn(false);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateSettlement(99L, "PAID", null));

    assertEquals("NOT_FOUND", exception.getCode());
  }

  @Test
  void supplierOrderAssignmentOptionsAreLimitedToTheCurrentChannel() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of()));
    List<Map<String, Object>> expected = List.of(Map.of(
        "id", 5L,
        "supplierNo", "SUP-5",
        "supplierName", "已关联供应商",
        "supplierType", "DIRECT_PUBLISHING"));
    when(repository.supplierOptionsForChannel(30L)).thenReturn(expected);

    List<Map<String, Object>> result = service.supplierOptions(30L);

    assertEquals(expected, result);
    verify(repository).supplierOptionsForChannel(30L);
    verify(repository, never()).supplierOptions();
  }

  @Test
  void supplierOrderCannotBePresentedAsSubmittedWithoutFulfillmentEvidence() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of()));
    when(repository.supplierOrder(19L)).thenReturn(Map.of(
        "id", 19L,
        "status", "PENDING_SUBMISSION",
        "supplierId", 5L,
        "channelId", 30L));
    when(repository.activeSupplierCanServeChannel(5L, 30L)).thenReturn(true);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.updateSupplierOrder(
            19L,
            new UpdateSupplierOrderRequest(
                5L, "SUBMITTED", "UNCONFIRMED", null, null, null, null)));

    assertEquals("SUPPLIER_ORDER_EVIDENCE_REQUIRED", exception.getCode());
    verify(repository, never()).updateSupplierOrder(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void supplierOrderRetryMustReturnToACleanPendingState() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of()));
    when(repository.supplierOrder(19L)).thenReturn(Map.of(
        "id", 19L,
        "status", "EXCEPTION",
        "supplierId", 5L,
        "channelId", 30L));
    when(repository.activeSupplierCanServeChannel(5L, 30L)).thenReturn(true);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.updateSupplierOrder(
            19L,
            new UpdateSupplierOrderRequest(
                5L, "PENDING_SUBMISSION", "MANUAL", null,
                "controlled/receipt/previous-attempt", null, null)));

    assertEquals("SUPPLIER_ORDER_PENDING_CONTEXT_INVALID", exception.getCode());
    verify(repository, never()).updateSupplierOrder(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void manualSupplierFulfillmentCannotCarryAnApiUpstreamOrderNumber() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of()));
    when(repository.supplierOrder(19L)).thenReturn(Map.of(
        "id", 19L,
        "status", "PENDING_SUBMISSION",
        "supplierId", 5L,
        "channelId", 30L));
    when(repository.activeSupplierCanServeChannel(5L, 30L)).thenReturn(true);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.updateSupplierOrder(
            19L,
            new UpdateSupplierOrderRequest(
                5L, "SUBMITTED", "MANUAL", "UPSTREAM-STALE-19",
                "controlled/manual-handoff-19", null, null)));

    assertEquals("SUPPLIER_EXTERNAL_ORDER_MODE_INVALID", exception.getCode());
    verify(repository, never()).updateSupplierOrder(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void directPublishingResultRequiresCompletedSupplierFulfillment() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "运营",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.lockTaskForUpdate(9L)).thenReturn(Map.of(
        "id", 9L, "taskNo", "PUB-9", "projectId", 1L, "status", "IN_PROGRESS",
        "channelType", "DIRECT_PUBLISHING"));
    when(repository.canOperateTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
        .thenReturn(true);
    when(repository.supplierOrderForPublishTask(9L)).thenReturn(Map.of(
        "supplierId", 5L,
        "status", "IN_PROGRESS",
        "fulfillmentMode", "MANUAL",
        "submissionEvidenceReference", "controlled/manual-handoff-9"));

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.submitResult(9L, new SubmitResultRequest(
            "不应跳过履约的结果", "https://example.com/pending-supplier", OffsetDateTime.now(), null)));

    assertEquals("SUPPLIER_FULFILLMENT_REQUIRED", exception.getCode());
    verify(repository, never()).submitResult(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void supplierApiReceiptRequiresAnAcceptedProductionConnection() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of()));
    when(repository.supplierOrder(19L)).thenReturn(Map.of(
        "id", 19L,
        "status", "PENDING_SUBMISSION",
        "supplierId", 5L,
        "channelId", 30L));
    when(repository.activeSupplierCanServeChannel(5L, 30L)).thenReturn(true);
    when(integrationAdminService.isSupplierFulfillmentRuntimeReady(5L)).thenReturn(false);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.updateSupplierOrder(
            19L,
            new UpdateSupplierOrderRequest(
                5L, "SUBMITTED", "API", "UPSTREAM-19",
                "controlled/receipts/19", null, null)));

    assertEquals("SUPPLIER_API_NOT_ACCEPTED", exception.getCode());
    verify(repository, never()).updateSupplierOrder(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void onsiteWritingOfferRejectsAnAlreadyConfirmedOverlappingWriterSchedule() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("project:manage")));
    when(repository.offerWritingAssignment(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(19L),
        org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(WritingAssignmentOfferOutcome.SCHEDULE_CONFLICT);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.offerWritingAssignment(19L, new OfferWritingAssignmentRequest(7L, null)));

    assertEquals("WRITING_ASSIGNMENT_NOT_OFFERABLE", exception.getCode());
    org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("已确认任务"));
  }

  @Test
  void onsiteWritingOfferRequiresDistanceWhenTheWriterHasConfiguredServiceRadius() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("project:manage")));
    when(repository.offerWritingAssignment(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(19L),
        org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(WritingAssignmentOfferOutcome.DISTANCE_REQUIRED);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.offerWritingAssignment(19L, new OfferWritingAssignmentRequest(7L, null)));

    assertEquals("WRITING_ASSIGNMENT_DISTANCE_REQUIRED", exception.getCode());
  }

  @Test
  void onsiteWritingOfferRejectsDistanceOutsideTheWriterServiceRadius() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("project:manage")));
    when(repository.offerWritingAssignment(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(19L),
        org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(new BigDecimal("120"))))
        .thenReturn(WritingAssignmentOfferOutcome.OUT_OF_SERVICE_RADIUS);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.offerWritingAssignment(
            19L, new OfferWritingAssignmentRequest(7L, new BigDecimal("120"))));

    assertEquals("WRITING_ASSIGNMENT_OUT_OF_SERVICE_RADIUS", exception.getCode());
  }

  @Test
  void onsiteWritingOfferReturnsOnlyTheOfferedSeatState() {
    CurrentUser.set(new AuthPrincipal(1L, "USR-1", 1L, "平台", "admin", "平台运营",
        "13800000001", "admin@example.com", "PLATFORM_ADMIN", List.of("project:manage")));
    when(repository.offerWritingAssignment(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(19L),
        org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(WritingAssignmentOfferOutcome.OFFERED);

    Map<String, Object> result = service.offerWritingAssignment(
        19L, new OfferWritingAssignmentRequest(7L, null));

    assertEquals("OFFERED", result.get("memberStatus"));
    assertEquals(7L, result.get("writerProfileId"));
  }

  @Test
  void writerResponseReturnsTheSeatStateInsteadOfClaimingTheWholeOrderIsFilled() {
    CurrentUser.set(new AuthPrincipal(2L, "USR-2", 1L, "平台", "operator", "写手",
        "13800000002", "operator@example.com", "PUBLISH_OPERATOR", List.of("task:execute")));
    when(repository.respondWritingAssignment(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(19L),
        org.mockito.ArgumentMatchers.eq("ACCEPT"), org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(true);

    Map<String, Object> result = service.respondWritingAssignment(
        19L, new RespondWritingAssignmentRequest("ACCEPT", null));

    assertEquals("ACCEPTED", result.get("memberStatus"));
    assertFalse(result.containsKey("status"));
  }

  private Map<String, Object> createPublishPlan(CreatePublishPlanRequest request) {
    return service.createPublishPlan(1L, request, PUBLISH_PLAN_KEY);
  }

  private Map<String, Object> channel(Long id, String type) {
    Map<String, Object> channel = new HashMap<>();
    channel.put("id", id);
    channel.put("status", "ACTIVE");
    channel.put("channel_type", type);
    channel.put("channel_name", "测试渠道");
    channel.put("publish_form", "网站图文");
    if ("DIRECT_PUBLISHING".equals(type)) {
      channel.put("quote_id", 1L);
      channel.put("customer_price", new BigDecimal("100.00"));
      channel.put("valid_until", OffsetDateTime.now().plusDays(1));
    }
    return channel;
  }

  private CreateRequirementRequest directRequirementWithSource(
      Long manuscriptId, Long versionId) {
    return new CreateRequirementRequest(
        "客户已有稿件直编发稿", null, null, "客户确认稿件，申请按渠道发布", null, null,
        "DIRECT_PUBLISHING", null, null, null, null, null,
        null, null, null, null, null, null, null, null, null,
        null, manuscriptId, versionId);
  }

  private MediaCandidate candidate(
      String key, String type, String mediaId, String mediaName,
      String reporterId, String reporterName) {
    return new MediaCandidate(
        key, type, mediaId, mediaName, reporterId, reporterName,
        "行业媒体", "广东", "深圳市", "网站", "科技", List.of("芯片"),
        null, true, 88.0, 120L, 10000L, null, null, null);
  }
}
