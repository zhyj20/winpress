package com.winpress.commercial.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** Input contracts for the customer-facing Open API and its platform-only management surface. */
public final class OpenApiDtos {
  private OpenApiDtos() {}

  public record SaveOpenApiApplicationRequest(
      @NotBlank(message = "请填写接入应用名称")
      @Size(max = 160, message = "接入应用名称最多160个字符")
      String applicationName,
      @NotBlank(message = "请填写客户标识")
      @Size(max = 80, message = "客户标识最多80个字符")
      String clientCode,
      @NotNull(message = "请选择归属客户账号")
      @Positive(message = "归属客户账号无效")
      Long customerUserId,
      @NotBlank(message = "请选择运行环境")
      @Size(max = 20)
      String environment,
      @NotEmpty(message = "至少选择一项接口能力")
      @Size(max = 8)
      List<@NotBlank @Size(max = 40) String> serviceScopes,
      @NotNull @Min(1) @Max(10000)
      Integer rateLimitPerMinute,
      @NotBlank @Size(max = 30)
      String authorizationStatus,
      @Size(max = 500)
      String authorizationEvidenceRef,
      @NotBlank @Size(max = 30)
      String sandboxStatus,
      @Size(max = 500)
      String sandboxEvidenceRef,
      @NotBlank @Size(max = 30)
      String productionStatus,
      @Size(max = 500)
      String productionEvidenceRef,
      @Size(max = 300)
      String contractReference,
      @Size(max = 3000)
      String internalNote,
      @NotBlank @Size(max = 30)
      String status) {}

  public record IssueOpenApiKeyRequest(
      @NotBlank(message = "请填写密钥用途")
      @Size(max = 120, message = "密钥用途最多120个字符")
      String keyLabel,
      OffsetDateTime expiresAt) {}

  public record RevokeOpenApiKeyRequest(
      @Size(max = 1000)
      String revokeReason) {}

  /**
   * The migrated API accepts only information that can enter the normal four-service workflow.
   * Supplier, cost, upstream and credential fields are deliberately absent from this contract.
   */
  public record OpenApiRequirementRequest(
      @JsonProperty("external_request_id")
      @NotBlank(message = "external_request_id 不能为空")
      @Size(max = 80, message = "external_request_id 最多80个字符")
      String externalRequestId,
      @JsonProperty("service_type")
      @NotBlank(message = "service_type 不能为空")
      @Size(max = 40)
      String serviceType,
      @NotBlank(message = "title 不能为空")
      @Size(max = 200, message = "title 最多200个字符")
      String title,
      @JsonProperty("event_time")
      OffsetDateTime eventTime,
      @JsonProperty("event_location")
      @Size(max = 200)
      String eventLocation,
      @Size(max = 3000)
      String facts,
      @Size(max = 500)
      String objective,
      @JsonProperty("target_audience")
      @Size(max = 300)
      String targetAudience,
      @JsonProperty("service_days")
      Integer serviceDays,
      @JsonProperty("writer_count")
      Integer writerCount,
      @JsonProperty("onsite_contact_name")
      @Size(max = 80)
      String onsiteContactName,
      @JsonProperty("onsite_contact_mobile")
      @Size(max = 30)
      String onsiteContactMobile,
      @JsonProperty("deliverable_requirement")
      @Size(max = 2000)
      String deliverableRequirement,
      @JsonProperty("conference_type")
      @Size(max = 40)
      String conferenceType,
      @JsonProperty("conference_format")
      @Size(max = 40)
      String conferenceFormat,
      @JsonProperty("conference_scale")
      @Size(max = 40)
      String conferenceScale,
      @JsonProperty("conference_media_goal")
      @Size(max = 1000)
      String conferenceMediaGoal,
      @JsonProperty("conference_agenda_status")
      @Size(max = 30)
      String conferenceAgendaStatus,
      @JsonProperty("conference_venue_status")
      @Size(max = 30)
      String conferenceVenueStatus,
      @JsonProperty("conference_contact_name")
      @Size(max = 80)
      String conferenceContactName,
      @JsonProperty("conference_contact_mobile")
      @Size(max = 30)
      String conferenceContactMobile,
      @JsonProperty("due_at")
      OffsetDateTime dueAt) {}
}
