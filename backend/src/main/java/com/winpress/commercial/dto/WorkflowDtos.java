package com.winpress.commercial.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class WorkflowDtos {
  private WorkflowDtos() {}

  public record CreateRequirementRequest(
      @NotBlank(message = "请输入需求标题") @Size(max = 200) String title,
      OffsetDateTime eventTime,
      @Size(max = 200) String eventLocation,
      @Size(max = 3000) String facts,
      @Size(max = 500) String objective,
      @Size(max = 300) String targetAudience,
      @NotBlank(message = "请选择服务类型") String requestedService,
      Integer serviceDays,
      Integer writerCount,
      @Size(max = 80) String onsiteContactName,
      @Size(max = 30) String onsiteContactMobile,
      @Size(max = 2000) String deliverableRequirement,
      @Size(max = 40) String conferenceType,
      @Size(max = 40) String conferenceFormat,
      @Size(max = 40) String conferenceScale,
      @Size(max = 1000) String conferenceMediaGoal,
      @Size(max = 30) String conferenceAgendaStatus,
      @Size(max = 30) String conferenceVenueStatus,
      @Size(max = 80) String conferenceContactName,
      @Size(max = 30) String conferenceContactMobile,
      OffsetDateTime dueAt,
      @Positive Long relatedProjectId,
      @Positive Long sourceManuscriptId,
      @Positive Long sourceManuscriptVersionId) {
    /**
     * Keeps existing internal callers source-compatible while activity linkage is optional.
     * A relation is never inferred from a client-side project identifier.
     */
    public CreateRequirementRequest(
        String title,
        OffsetDateTime eventTime,
        String eventLocation,
        String facts,
        String objective,
        String targetAudience,
        String requestedService,
        Integer serviceDays,
        Integer writerCount,
        String onsiteContactName,
        String onsiteContactMobile,
        String deliverableRequirement,
        String conferenceType,
        String conferenceFormat,
        String conferenceScale,
        String conferenceMediaGoal,
        String conferenceAgendaStatus,
        String conferenceVenueStatus,
        String conferenceContactName,
        String conferenceContactMobile,
        OffsetDateTime dueAt) {
      this(title, eventTime, eventLocation, facts, objective, targetAudience, requestedService,
          serviceDays, writerCount, onsiteContactName, onsiteContactMobile,
          deliverableRequirement, conferenceType, conferenceFormat, conferenceScale,
          conferenceMediaGoal, conferenceAgendaStatus, conferenceVenueStatus,
          conferenceContactName, conferenceContactMobile, dueAt, null, null, null);
    }

    /**
     * Compatibility constructor for an independently priced service related to an activity.
     * Source-manuscript reuse remains explicitly opt-in and is never inferred from the relation.
     */
    public CreateRequirementRequest(
        String title,
        OffsetDateTime eventTime,
        String eventLocation,
        String facts,
        String objective,
        String targetAudience,
        String requestedService,
        Integer serviceDays,
        Integer writerCount,
        String onsiteContactName,
        String onsiteContactMobile,
        String deliverableRequirement,
        String conferenceType,
        String conferenceFormat,
        String conferenceScale,
        String conferenceMediaGoal,
        String conferenceAgendaStatus,
        String conferenceVenueStatus,
        String conferenceContactName,
        String conferenceContactMobile,
        OffsetDateTime dueAt,
        Long relatedProjectId) {
      this(title, eventTime, eventLocation, facts, objective, targetAudience, requestedService,
          serviceDays, writerCount, onsiteContactName, onsiteContactMobile,
          deliverableRequirement, conferenceType, conferenceFormat, conferenceScale,
          conferenceMediaGoal, conferenceAgendaStatus, conferenceVenueStatus,
          conferenceContactName, conferenceContactMobile, dueAt, relatedProjectId, null, null);
    }
  }

  public record ReviewManuscriptRequest(
      @NotNull(message = "请选择稿件版本") @Positive Long versionId,
      @NotBlank(message = "请选择审核结果") String decision,
      @Size(max = 1000) String comment) {}

  public record ChannelSelection(
      @Positive Long channelId,
      OffsetDateTime plannedPublishAt,
      @Size(max = 80) String journalistName,
      @Size(max = 180) String mediaName,
      @Size(max = 500) String note,
      @Valid NiumediaDtos.MediaCandidate mediaCandidate) {}

  public record CreatePublishPlanRequest(
      @Positive Long manuscriptId,
      @Positive Long manuscriptVersionId,
      @Size(max = 160) String planName,
      @Size(max = 500) String objective,
      Boolean exclusiveMediaPr,
      OffsetDateTime lockExpiresAt,
      @NotEmpty(message = "请至少选择一个发布渠道") List<@Valid ChannelSelection> selections) {}

  public record OfferWritingAssignmentRequest(
      @NotNull @Positive Long writerProfileId,
      @PositiveOrZero BigDecimal distanceKm) {}

  public record RespondWritingAssignmentRequest(
      @NotBlank(message = "请选择接单或拒单") String decision,
      @Size(max = 1000) String note) {}

  public record UpdateTaskRequest(
      @NotBlank(message = "请选择任务状态") String status,
      @Size(max = 2000) String executionNote,
      @Size(max = 1000) String exceptionReason) {}

  /** Records a verifiable media-contact milestone after a media invitation task exists. */
  public record UpdateMediaInvitationRequest(
      @NotBlank(message = "请选择媒体沟通状态") String status,
      @Size(max = 1000) String note) {}

  public record UpdateConferenceWorkItemRequest(
      @NotBlank(message = "请选择统筹事项状态") String status,
      @NotBlank(message = "统筹事项状态已变化，请刷新后重试") String expectedStatus,
      @Size(max = 1000) String note,
      OffsetDateTime dueAt,
      @Positive Long assignedOperatorId) {}

  public record UpdateConferenceProjectRequest(
      @Size(max = 240) String theme,
      OffsetDateTime eventTime,
      @Size(max = 240) String eventLocation,
      @Size(max = 40) String conferenceType,
      @Size(max = 40) String conferenceFormat,
      @Size(max = 40) String conferenceScale,
      @Size(max = 1000) String mediaGoal,
      @Size(max = 2000) String guestPlan,
      @Size(max = 2000) String agendaPlan,
      @Size(max = 2000) String venuePlan,
      @Size(max = 1000) String mediaDirection,
      @Size(max = 1000) String communicationGoal,
      @Size(max = 30) String agendaStatus,
      @Size(max = 30) String venueStatus,
      @NotBlank(message = "请填写会务联系人") @Size(max = 80) String contactName,
      @NotBlank(message = "请填写会务联系人手机号") @Size(max = 30) String contactMobile) {}

  public record SubmitManuscriptRequest(
      @NotBlank(message = "请输入稿件标题") @Size(max = 240) String title,
      @Size(max = 1000) String summary,
      @NotBlank(message = "请输入稿件正文") String content,
      @Size(max = 500) String changeNote) {}

  public record SubmitResultRequest(
      @NotBlank(message = "请输入成果标题") @Size(max = 240) String title,
      @NotBlank(message = "请输入成果链接") @Size(max = 800) String url,
      OffsetDateTime publishedAt,
      @Size(max = 500) String note) {}

  public record AssignProjectRequest(@NotNull @Positive Long operatorId) {}

  public record CreateChannelRequest(
      @NotBlank @Size(max = 180) String channelName,
      @NotBlank String channelType,
      @Size(max = 80) String category,
      @Size(max = 80) String region,
      @Size(max = 120) String publishForm,
      @Positive Integer expectedDays,
      Boolean linkSupport,
      @Size(max = 1000) String publicNotes,
      BigDecimal customerPrice,
      BigDecimal costPrice,
      OffsetDateTime validUntil) {}

  public record UpdateChannelRequest(
      @NotBlank @Size(max = 180) String channelName,
      @NotBlank String channelType,
      @Size(max = 80) String category,
      @Size(max = 80) String region,
      @Size(max = 120) String publishForm,
      @Positive Integer expectedDays,
      Boolean linkSupport,
      @Size(max = 1000) String publicNotes,
      BigDecimal customerPrice,
      BigDecimal costPrice,
      OffsetDateTime validUntil,
      @NotBlank String status) {}

  public record CreateQuoteRequest(
      @NotNull @Positive Long channelId,
      @Positive Long supplierId,
      @PositiveOrZero BigDecimal costPrice,
      @NotNull @Positive BigDecimal customerPrice,
      @NotNull OffsetDateTime validUntil,
      @Size(max = 1000) String publicTerms,
      @NotBlank @Size(max = 300) String reason) {}

  public record CreateSupplierRequest(
      @NotBlank @Size(max = 180) String supplierName,
      @NotBlank @Size(max = 40) String supplierType,
      @Size(max = 80) String contactName,
      @Size(max = 30) String contactPhone,
      @Email @Size(max = 160) String contactEmail,
      @Size(max = 2000) String serviceScope,
      @Size(max = 2000) String internalNote) {}

  public record UpdateSupplierRequest(
      @NotBlank @Size(max = 180) String supplierName,
      @NotBlank @Size(max = 40) String supplierType,
      @Size(max = 80) String contactName,
      @Size(max = 30) String contactPhone,
      @Email @Size(max = 160) String contactEmail,
      @Size(max = 2000) String serviceScope,
      @Size(max = 2000) String internalNote,
      @NotBlank @Size(max = 30) String status) {}

  public record AssignSupplierChannelRequest(
      @NotNull @Positive Long supplierId,
      @NotNull @Positive Long channelId,
      @Size(max = 120) String externalProductCode,
      @Size(max = 1000) String serviceScope,
      @Positive Integer priority) {}

  public record UpdateSupplierOrderRequest(
      @Positive Long supplierId,
      @NotBlank @Size(max = 30) String status,
      @NotBlank @Size(max = 20) String fulfillmentMode,
      @Size(max = 120) String externalOrderNo,
      @Size(max = 500) String submissionEvidenceReference,
      @Size(max = 2000) String note,
      @Size(max = 1000) String exceptionReason) {}

  public record CreateBusinessInquiryRequest(
      @NotBlank @Size(max = 40) String inquiryType,
      @NotBlank @Size(max = 160) String companyName,
      @NotBlank @Size(max = 80) String contactName,
      @NotBlank @Size(max = 30) String mobile,
      @Email @Size(max = 160) String email,
      @NotBlank @Size(max = 3000) String message,
      @NotNull Boolean privacyAccepted) {}

  public record UpdateBusinessInquiryRequest(
      @NotBlank @Size(max = 30) String status,
      @Size(max = 2000) String handlingNote) {}

  public record BatchQuoteAdjustmentRequest(
      @NotEmpty @Size(max = 200) List<@NotNull @Positive Long> channelIds,
      @NotNull BigDecimal percentage,
      @NotNull OffsetDateTime validUntil,
      @Size(max = 1000) String publicTerms,
      @NotBlank @Size(max = 300) String reason) {}

  public record UpdateSettlementRequest(
      @NotBlank(message = "请选择结算状态") String status,
      @Size(max = 80) String invoiceNo) {}

  public record CreateSettlementTransactionRequest(
      @NotBlank(message = "请选择交易类型") @Size(max = 30) String transactionType,
      @NotNull(message = "请输入交易金额") @Positive @Digits(integer = 12, fraction = 2)
          BigDecimal amount,
      @NotNull(message = "请选择交易发生时间") OffsetDateTime occurredAt,
      @Size(max = 120) String referenceNo,
      @Size(max = 500) String customerNote,
      @Size(max = 1000) String internalNote) {}

  public record VoidSettlementTransactionRequest(
      @NotBlank(message = "请填写作废原因") @Size(max = 500) String reason) {}

  public record UpdateUserRequest(
      @NotBlank(message = "请选择账号角色") String role,
      @NotBlank(message = "请选择账号状态") String status) {}

  public record PageResult<T>(List<T> items, long total, int page, int pageSize) {}
}
