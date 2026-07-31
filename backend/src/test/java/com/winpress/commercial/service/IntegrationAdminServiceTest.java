package com.winpress.commercial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winpress.commercial.dto.IntegrationAdminDtos.SaveSupplierApiConnectionRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateAcceptanceEvidenceRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateAcceptanceGateRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateLegacyServiceReviewRequest;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.federation.FederationTokenService;
import com.winpress.commercial.repository.IntegrationAdminRepository;
import com.winpress.commercial.repository.WorkflowRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IntegrationAdminServiceTest {
  private final IntegrationAdminRepository repository = mock(IntegrationAdminRepository.class);
  private final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
  private final NiumediaMediaService niumediaMediaService = mock(NiumediaMediaService.class);
  private final FederationTokenService federationTokenService = mock(FederationTokenService.class);
  private final IntegrationAdminService service = new IntegrationAdminService(
      repository, workflowRepository, niumediaMediaService, federationTokenService);

  @AfterEach
  void clearUser() {
    CurrentUser.clear();
  }

  @Test
  void overviewIsRestrictedToPlatformAdministrators() {
    CurrentUser.set(principal("CUSTOMER"));

    BusinessException exception = assertThrows(BusinessException.class, service::overview);

    assertEquals("FORBIDDEN", exception.getCode());
    verify(repository, never()).connections();
  }

  @Test
  void enabledConnectionRequiresAllAcceptanceGates() {
    CurrentUser.set(principal("PLATFORM_ADMIN"));

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.createConnection(request(true, "PENDING", "NOT_TESTED", "NOT_APPROVED")));

    assertEquals("INVALID_INTEGRATION_CONFIGURATION", exception.getCode());
    assertTrue(exception.getMessage().contains("授权、沙箱和生产验收"));
    verify(repository, never()).createConnection(any(), any());
  }

  @Test
  void configurationCheckDoesNotCallTheExternalNetworkOrExposeCredentials() {
    CurrentUser.set(principal("PLATFORM_ADMIN"));
    Map<String, Object> connection = new LinkedHashMap<>();
    connection.put("authType", "BEARER");
    connection.put("credentialEnvKey", "WINPRESS_TEST_TOKEN_THAT_IS_NOT_SET");
    connection.put("authorizationStatus", "VERIFIED");
    connection.put("sandboxStatus", "PASSED");
    connection.put("productionStatus", "APPROVED");
    connection.put("enabled", false);
    when(repository.connection(7L)).thenReturn(connection);

    Map<String, Object> result = service.checkConfiguration(7L);

    assertFalse((Boolean) result.get("configurationReady"));
    assertFalse((Boolean) result.get("networkRequestPerformed"));
    assertFalse((Boolean) result.get("credentialValueExposed"));
    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) result.get("blockers");
    assertTrue(blockers.contains("运行环境尚未配置凭据"));
    verify(repository).saveConfigurationCheck(
        eq(principal("PLATFORM_ADMIN")), eq(7L), eq("BLOCKED"), any());
  }

  @Test
  void oldMediaRuntimeConfigurationIsNotPresentedAsAcceptedWithoutEvidence() {
    CurrentUser.set(principal("PLATFORM_ADMIN"));
    when(repository.connections()).thenReturn(List.of());
    when(repository.legacyServiceReviews()).thenReturn(List.of());
    when(repository.acceptanceGates()).thenReturn(List.of(
        Map.of("gateCode", "EXTERNAL_MEDIA_DATA", "status", "PENDING")));
    when(niumediaMediaService.adminStatus()).thenReturn(Map.of(
        "runtimeConfigured", true,
        "rawMediaSearchConfigured", true,
        "rawReporterSearchConfigured", true,
        "governanceReady", true));
    when(federationTokenService.isConfigured()).thenReturn(false);

    Map<String, Object> result = service.overview();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> adapters =
        (List<Map<String, Object>>) result.get("builtInAdapters");
    assertEquals("UNAVAILABLE", adapters.get(0).get("operationalStatus"));
    assertEquals("PENDING", adapters.get(0).get("acceptanceStatus"));
  }

  @Test
  void overviewDoesNotPresentAStoppedConnectionAsRuntimeReady() {
    CurrentUser.set(principal("PLATFORM_ADMIN"));
    Map<String, Object> connection = new LinkedHashMap<>();
    connection.put("supplierId", 8L);
    connection.put("connectionKind", "GEO_FEDERATION");
    connection.put("environment", "PRODUCTION");
    connection.put("authType", "NONE");
    connection.put("authorizationStatus", "VERIFIED");
    connection.put("sandboxStatus", "PASSED");
    connection.put("productionStatus", "APPROVED");
    connection.put("enabled", false);
    when(repository.connections()).thenReturn(List.of(connection));
    when(repository.legacyServiceReviews()).thenReturn(List.of());
    when(repository.acceptanceEvidenceItems()).thenReturn(List.of());
    when(repository.acceptanceGates()).thenReturn(List.of());
    when(niumediaMediaService.adminStatus()).thenReturn(Map.of());

    Map<String, Object> result = service.overview();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> connections = (List<Map<String, Object>>) result.get("connections");
    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) result.get("summary");
    assertFalse((Boolean) connections.get(0).get("configurationReady"));
    assertEquals(0, ((Number) summary.get("configurationReadyCount")).intValue());
  }

  @Test
  void gateCannotPassWhenTheDatabaseRecordLacksItsRuntimeCredential() {
    CurrentUser.set(principal("PLATFORM_ADMIN"));
    Map<String, Object> connection = productionMediaConnection("WINPRESS_TEST_TOKEN_THAT_IS_NOT_SET");
    when(repository.acceptanceGateExists("EXTERNAL_MEDIA_DATA")).thenReturn(true);
    when(repository.pendingRequiredEvidenceCount("EXTERNAL_MEDIA_DATA")).thenReturn(0L);
    when(repository.connections()).thenReturn(List.of(connection));

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.updateAcceptanceGate(
            "EXTERNAL_MEDIA_DATA",
            new UpdateAcceptanceGateRequest("PASSED", "controlled/evidence/register", "复核完成")));

    assertEquals("INVALID_INTEGRATION_CONFIGURATION", exception.getCode());
    verify(repository, never()).updateAcceptanceGate(any(), any(), any());
  }

  @Test
  void supplierApiRuntimeReadinessRequiresTheCurrentEnvironmentCredential() {
    Map<String, Object> connection = new LinkedHashMap<>();
    connection.put("supplierId", 5L);
    connection.put("connectionKind", "ORDER_FULFILLMENT");
    connection.put("environment", "PRODUCTION");
    connection.put("authType", "BEARER");
    connection.put("credentialEnvKey", "WINPRESS_TEST_TOKEN_THAT_IS_NOT_SET");
    connection.put("authorizationStatus", "VERIFIED");
    connection.put("sandboxStatus", "PASSED");
    connection.put("productionStatus", "APPROVED");
    connection.put("enabled", true);
    connection.put("orderPath", "/orders");
    connection.put("orderStatusPath", "/orders/status");
    connection.put("callbackPath", "/callbacks");
    connection.put("reconciliationPath", "/reconciliation");
    connection.put("slaReference", "controlled/sla");
    when(repository.connections()).thenReturn(List.of(connection));
    when(repository.acceptanceGates()).thenReturn(List.of(
        Map.of("gateCode", "SUPPLIER_FULFILLMENT", "status", "PASSED")));
    when(repository.pendingRequiredEvidenceCount("SUPPLIER_FULFILLMENT")).thenReturn(0L);

    assertFalse(service.isSupplierFulfillmentRuntimeReady(5L));
  }

  @Test
  void supplierApiRuntimeReadinessRequiresPassedGateAndVerifiedEvidence() {
    Map<String, Object> connection = productionSupplierConnection();
    when(repository.connections()).thenReturn(List.of(connection));
    when(repository.acceptanceGates()).thenReturn(List.of(
        Map.of("gateCode", "SUPPLIER_FULFILLMENT", "status", "PENDING")));

    assertFalse(service.isSupplierFulfillmentRuntimeReady(5L));

    when(repository.acceptanceGates()).thenReturn(List.of(
        Map.of("gateCode", "SUPPLIER_FULFILLMENT", "status", "PASSED")));
    when(repository.pendingRequiredEvidenceCount("SUPPLIER_FULFILLMENT")).thenReturn(1L);
    assertFalse(service.isSupplierFulfillmentRuntimeReady(5L));

    when(repository.pendingRequiredEvidenceCount("SUPPLIER_FULFILLMENT")).thenReturn(0L);
    assertTrue(service.isSupplierFulfillmentRuntimeReady(5L));
  }

  @Test
  void approvedLegacyDecisionRequiresActionAndEvidence() {
    CurrentUser.set(principal("PLATFORM_ADMIN"));

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.updateLegacyServiceReview(
            3L, new UpdateLegacyServiceReviewRequest("APPROVED", null, null, "待确认")));

    assertEquals("INVALID_INTEGRATION_CONFIGURATION", exception.getCode());
    verify(repository, never()).updateLegacyServiceReview(any(), any(), any());
  }

  @Test
  void summaryGateCannotPassWhileRequiredEvidenceIsOpen() {
    CurrentUser.set(principal("PLATFORM_ADMIN"));
    when(repository.acceptanceGateExists("EXTERNAL_MEDIA_DATA")).thenReturn(true);
    when(repository.pendingRequiredEvidenceCount("EXTERNAL_MEDIA_DATA")).thenReturn(2L);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.updateAcceptanceGate(
            "EXTERNAL_MEDIA_DATA",
            new UpdateAcceptanceGateRequest("PASSED", "controlled/evidence/register", "复核完成")));

    assertEquals("INVALID_INTEGRATION_CONFIGURATION", exception.getCode());
    assertTrue(exception.getMessage().contains("2 项必备验收材料"));
    verify(repository, never()).updateAcceptanceGate(any(), any(), any());
  }

  @Test
  void requiredEvidenceCannotBeWaivedOrVerifiedWithoutAReference() {
    CurrentUser.set(principal("PLATFORM_ADMIN"));
    when(repository.acceptanceEvidenceItem(11L)).thenReturn(Map.of(
        "id", 11L,
        "gateCode", "LEGAL_TRUST",
        "required", true));

    BusinessException waived = assertThrows(
        BusinessException.class,
        () -> service.updateAcceptanceEvidenceItem(
            11L, new UpdateAcceptanceEvidenceRequest("NOT_APPLICABLE", null, "无需核验")));
    BusinessException missingReference = assertThrows(
        BusinessException.class,
        () -> service.updateAcceptanceEvidenceItem(
            11L, new UpdateAcceptanceEvidenceRequest("VERIFIED", null, "已完成")));

    assertEquals("INVALID_INTEGRATION_CONFIGURATION", waived.getCode());
    assertEquals("INVALID_INTEGRATION_CONFIGURATION", missingReference.getCode());
    verify(repository, never()).updateAcceptanceEvidenceItem(any(), any(), any());
  }

  private static SaveSupplierApiConnectionRequest request(
      boolean enabled, String authorization, String sandbox, String production) {
    return new SaveSupplierApiConnectionRequest(
        null, "测试供应商接口", "TEST_PROVIDER", "MEDIA_DATA", "SANDBOX",
        "https://api.example.com/v1", "BEARER", "Authorization",
        "WINPRESS_TEST_PROVIDER_TOKEN", "媒体检索", "/media/search", "/reporter/search",
        null, null, null, null, null, null, 60, 15, 2, "仅测试字段", null,
        authorization, null, sandbox, null, production, null, null, enabled);
  }

  private static Map<String, Object> productionMediaConnection(String credentialEnvKey) {
    Map<String, Object> connection = new LinkedHashMap<>();
    connection.put("connectionKind", "MEDIA_DATA");
    connection.put("environment", "PRODUCTION");
    connection.put("authType", "BEARER");
    connection.put("credentialEnvKey", credentialEnvKey);
    connection.put("authorizationStatus", "VERIFIED");
    connection.put("sandboxStatus", "PASSED");
    connection.put("productionStatus", "APPROVED");
    connection.put("enabled", true);
    connection.put("dataScope", "授权媒体字段");
    connection.put("mediaSearchPath", "/media/search");
    return connection;
  }

  private static Map<String, Object> productionSupplierConnection() {
    Map<String, Object> connection = new LinkedHashMap<>();
    connection.put("supplierId", 5L);
    connection.put("connectionKind", "ORDER_FULFILLMENT");
    connection.put("environment", "PRODUCTION");
    connection.put("authType", "NONE");
    connection.put("authorizationStatus", "VERIFIED");
    connection.put("sandboxStatus", "PASSED");
    connection.put("productionStatus", "APPROVED");
    connection.put("enabled", true);
    connection.put("orderPath", "/orders");
    connection.put("orderStatusPath", "/orders/status");
    connection.put("callbackPath", "/callbacks");
    connection.put("reconciliationPath", "/reconciliation");
    connection.put("slaReference", "controlled/sla");
    return connection;
  }

  private static AuthPrincipal principal(String role) {
    return new AuthPrincipal(
        1L, "USR-1", 1L, "平台", "admin", "平台运营",
        null, "admin@example.com", role, new ArrayList<>());
  }
}
