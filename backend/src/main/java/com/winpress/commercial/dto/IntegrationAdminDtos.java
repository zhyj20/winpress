package com.winpress.commercial.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class IntegrationAdminDtos {
  private IntegrationAdminDtos() {}

  public record SaveSupplierApiConnectionRequest(
      Long supplierId,
      @NotBlank(message = "请填写接口名称")
      @Size(max = 180, message = "接口名称最多180个字符")
      String connectionName,
      @NotBlank(message = "请填写供应商标识")
      @Size(max = 80, message = "供应商标识最多80个字符")
      String providerCode,
      @NotBlank(message = "请选择接口用途")
      @Size(max = 40)
      String connectionKind,
      @NotBlank(message = "请选择运行环境")
      @Size(max = 20)
      String environment,
      @NotBlank(message = "请填写接口地址")
      @Size(max = 500, message = "接口地址最多500个字符")
      String baseUrl,
      @NotBlank(message = "请选择鉴权方式")
      @Size(max = 30)
      String authType,
      @Size(max = 100)
      String authHeaderName,
      @Size(max = 160)
      String credentialEnvKey,
      @Size(max = 2000)
      String capabilityScope,
      @Size(max = 300)
      String mediaSearchPath,
      @Size(max = 300)
      String reporterSearchPath,
      @Size(max = 300)
      String quotePath,
      @Size(max = 300)
      String orderPath,
      @Size(max = 300)
      String orderStatusPath,
      @Size(max = 300)
      String callbackPath,
      @Size(max = 300)
      String reconciliationPath,
      @Size(max = 300)
      String slaReference,
      @NotNull @Min(1) @Max(10000)
      Integer rateLimitPerMinute,
      @NotNull @Min(1) @Max(120)
      Integer timeoutSeconds,
      @NotNull @Min(0) @Max(10)
      Integer maxRetries,
      @Size(max = 3000)
      String dataScope,
      @Size(max = 300)
      String contractReference,
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
      @Size(max = 3000)
      String internalNote,
      @NotNull
      Boolean enabled) {}

  public record UpdateAcceptanceGateRequest(
      @NotBlank(message = "请选择验收状态")
      @Size(max = 30)
      String status,
      @Size(max = 500)
      String evidenceReference,
      @Size(max = 3000)
      String reviewNote) {}

  public record UpdateAcceptanceEvidenceRequest(
      @NotBlank(message = "请选择核验状态")
      @Size(max = 30)
      String itemStatus,
      @Size(max = 500)
      String evidenceReference,
      @Size(max = 3000)
      String reviewNote) {}

  public record UpdateLegacyServiceReviewRequest(
      @NotBlank(message = "请选择审核状态")
      @Size(max = 30)
      String reviewStatus,
      @Size(max = 40)
      String approvedAction,
      @Size(max = 500)
      String evidenceReference,
      @Size(max = 3000)
      String businessNote) {}
}
