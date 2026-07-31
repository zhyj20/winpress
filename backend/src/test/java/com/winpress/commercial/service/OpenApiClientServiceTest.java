package com.winpress.commercial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.AuthRepository;
import com.winpress.commercial.repository.OpenApiRepository;
import com.winpress.commercial.security.CurrentUser;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenApiClientServiceTest {
  private final OpenApiRepository repository = mock(OpenApiRepository.class);
  private final AuthRepository authRepository = mock(AuthRepository.class);
  private final WorkflowService workflowService = mock(WorkflowService.class);
  private final OpenApiRateLimiter limiter = mock(OpenApiRateLimiter.class);
  private final Validator validator = mock(Validator.class);
  private final OpenApiClientService service = new OpenApiClientService(
      repository, authRepository, workflowService, limiter, new ObjectMapper(), validator);

  @AfterEach
  void clearUser() {
    CurrentUser.clear();
  }

  @Test
  void rejectsAnAccessKeyWhoseCustomerOwnerIsNoLongerEligible() {
    // The repository returns no principal when the customer, organization, or CUSTOMER role is
    // inactive. The API must fail before it can expose the catalog or mark the key as used.
    when(repository.activeKeyPrincipal(anyString())).thenReturn(Map.of());

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.serviceCatalog("a".repeat(24)));

    assertEquals("OPEN_API_UNAUTHORIZED", exception.getCode());
    verify(repository, never()).markAccessKeyUsed(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void serviceCatalogUsesTheSameFormalConferenceServiceNameAsTheConsole() {
    when(repository.activeKeyPrincipal(anyString())).thenReturn(Map.of(
        "serviceScopes", "SERVICE_CATALOG",
        "applicationId", 1L,
        "accessKeyId", 2L,
        "customerUserId", 3L,
        "applicationNo", "OAPI-TEST",
        "environment", "SANDBOX",
        "rateLimitPerMinute", 60));

    Map<String, Object> response = service.serviceCatalog("a".repeat(24));
    List<Map<String, String>> items = (List<Map<String, String>>) response.get("items");

    assertEquals("举办新闻发布会", items.stream()
        .filter(item -> "NEWS_CONFERENCE".equals(item.get("code")))
        .findFirst()
        .orElseThrow()
        .get("name"));
  }
}
