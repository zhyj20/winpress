package com.winpress.commercial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winpress.commercial.dto.NiumediaDtos.MediaCandidate;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchQuery;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchResult;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.IntegrationAdminRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NiumediaMediaServiceTest {
  @AfterEach
  void clearCurrentUser() {
    CurrentUser.clear();
  }

  @Test
  void issuesAnOpaqueCustomerReferenceAndKeepsProviderIdsServerSide() throws Exception {
    CurrentUser.set(customer(3L, 2L));
    NiumediaMediaClient client = mock(NiumediaMediaClient.class);
    NiumediaMediaService service = governedService(client);
    MediaSearchQuery query = query("MEDIA");
    MediaCandidate raw = new MediaCandidate(
        "MEDIA:9", "MEDIA", "9", "产业观察", null, null, "行业媒体", "广东", "深圳",
        "网站", "科技", List.of("芯片"), "provider-only", true, 92.5, 120L, 900L,
        null, null, "2026-07-28T00:00:00Z");
    when(client.search(query)).thenReturn(new MediaSearchResult(
        List.of(raw), 1, 1, 20, "2026-07-28T00:00:00Z", false, null));

    MediaSearchResult safe = service.search(query);
    MediaCandidate outward = safe.items().get(0);
    String json = new ObjectMapper().writeValueAsString(safe);

    assertTrue(outward.candidateKey().startsWith("SEL-"));
    assertNull(outward.mediaId());
    assertNull(outward.reporterId());
    assertNull(outward.operationNote());
    assertNull(outward.logoUrl());
    assertNull(outward.avatarUrl());
    assertFalse(json.contains("MEDIA:9"));
    assertFalse(json.contains("provider-only"));
    assertFalse(json.contains("\"mediaId\""));
    assertFalse(json.contains("\"logoUrl\""));
    assertEquals(raw, service.resolveCandidate(outward.candidateKey()));
    assertEquals(9L, service.resolveMediaId(outward.candidateKey()));
  }

  @Test
  void opaqueReferenceCannotBeReusedByAnotherCustomer() {
    CurrentUser.set(customer(3L, 2L));
    NiumediaMediaClient client = mock(NiumediaMediaClient.class);
    NiumediaMediaService service = governedService(client);
    MediaSearchQuery query = query("MEDIA");
    MediaCandidate raw = new MediaCandidate(
        "MEDIA:9", "MEDIA", "9", "产业观察", null, null, null, null, null,
        null, null, List.of(), null, true, null, null, null, null, null, null);
    when(client.search(query)).thenReturn(new MediaSearchResult(List.of(raw), 1, 1, 20, null, false, null));
    String reference = service.search(query).items().get(0).candidateKey();
    CurrentUser.set(customer(4L, 3L));

    BusinessException error = assertThrows(
        BusinessException.class, () -> service.resolveCandidate(reference));

    assertEquals("FORBIDDEN", error.getCode());
  }

  @Test
  void customerStatusHidesProviderThrottleDetails() {
    CurrentUser.set(customer(3L, 2L));
    NiumediaMediaClient client = mock(NiumediaMediaClient.class);
    when(client.isMediaSearchConfigured()).thenReturn(true);
    when(client.isReporterSearchConfigured()).thenReturn(true);
    when(client.isTaxonomyConfigured()).thenReturn(true);
    when(client.isRateLimited()).thenReturn(true);

    var status = governedService(client).status();

    assertEquals(true, status.get("temporarilyUnavailable"));
    assertFalse(status.containsKey("retryAfterSeconds"));
    assertFalse(status.containsKey("minRequestIntervalMillis"));
    assertFalse(status.containsKey("runtimeConfigured"));
    assertFalse(status.containsKey("governanceReady"));
    assertFalse(status.containsKey("verificationStatus"));
  }

  @Test
  void administrationStatusKeepsConnectionDetailOutOfCustomerRoutes() {
    CurrentUser.set(customer(3L, 2L));
    NiumediaMediaClient client = mock(NiumediaMediaClient.class);
    NiumediaMediaService service = governedService(client);

    BusinessException forbidden = assertThrows(BusinessException.class, service::adminStatus);

    assertEquals("FORBIDDEN", forbidden.getCode());
    CurrentUser.set(platformAdmin());
    var status = service.adminStatus();
    assertTrue(status.containsKey("runtimeConfigured"));
    assertTrue(status.containsKey("governanceReady"));
    assertTrue(status.containsKey("verificationStatus"));
  }

  @Test
  void searchUsesACustomerSafePendingConfirmationNotice() {
    CurrentUser.set(customer(3L, 2L));
    NiumediaMediaClient client = mock(NiumediaMediaClient.class);
    MediaSearchQuery query = query("MEDIA");
    MediaCandidate raw = new MediaCandidate(
        "MEDIA:9", "MEDIA", "9", "产业观察", null, null, null, null, null,
        null, null, List.of(), null, true, null, null, null, null, null, null);
    when(client.search(query)).thenReturn(new MediaSearchResult(
        List.of(raw), 1, 1, 20, null, false, "provider connection detail"));

    MediaSearchResult safe = governedService(client).search(query);

    assertEquals("候选资料待项目核验，不代表媒体或记者已确认参与。", safe.notice());
    assertFalse(safe.notice().contains("provider"));
  }

  @Test
  void runtimeVariablesDoNotEnableCustomerSearchWithoutAcceptedGovernance() {
    CurrentUser.set(customer(3L, 2L));
    NiumediaMediaClient client = mock(NiumediaMediaClient.class);
    IntegrationAdminRepository repository = mock(IntegrationAdminRepository.class);
    when(client.isMediaSearchConfigured()).thenReturn(true);
    when(repository.isExternalMediaDataOperational("NIUMEDIA")).thenReturn(false);
    NiumediaMediaService service = new NiumediaMediaService(client, repository);

    var status = service.status();
    BusinessException error = assertThrows(
        BusinessException.class, () -> service.search(query("MEDIA")));

    assertEquals(false, status.get("available"));
    assertFalse(status.containsKey("verificationStatus"));
    assertEquals("MEDIA_DISCOVERY_UNAVAILABLE", error.getCode());
    assertTrue(error.getMessage().contains("项目负责人补充"));
  }

  private NiumediaMediaService governedService(NiumediaMediaClient client) {
    IntegrationAdminRepository repository = mock(IntegrationAdminRepository.class);
    when(client.isMediaSearchConfigured()).thenReturn(true);
    when(repository.isExternalMediaDataOperational("NIUMEDIA")).thenReturn(true);
    return new NiumediaMediaService(client, repository);
  }

  private MediaSearchQuery query(String target) {
    return new MediaSearchQuery(
        target, "新品发布", null, "广东", "深圳", null, null, null,
        null, null, null, null, null, null, "MEDIA_PR", 1, 20);
  }

  private AuthPrincipal customer(Long userId, Long organizationId) {
    return new AuthPrincipal(
        userId, "USR-" + userId, organizationId, "客户组织", "customer" + userId,
        "客户", "1380000000" + userId, "customer" + userId + "@example.com",
        "CUSTOMER", List.of("publish:submit"));
  }

  private AuthPrincipal platformAdmin() {
    return new AuthPrincipal(
        1L, "USR-1", 1L, "平台", "admin", "管理员", "13800000001",
        "admin@example.com", "PLATFORM_ADMIN", List.of("admin:manage"));
  }
}
