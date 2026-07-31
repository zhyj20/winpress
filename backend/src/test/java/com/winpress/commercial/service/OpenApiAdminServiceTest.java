package com.winpress.commercial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winpress.commercial.dto.OpenApiDtos.IssueOpenApiKeyRequest;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.OpenApiRepository;
import com.winpress.commercial.repository.WorkflowRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenApiAdminServiceTest {
  private final OpenApiRepository repository = mock(OpenApiRepository.class);
  private final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
  private final OpenApiAdminService service = new OpenApiAdminService(repository, workflowRepository);

  @AfterEach
  void clearUser() {
    CurrentUser.clear();
  }

  @Test
  void doesNotIssueAKeyWhenItsCustomerOwnerIsNoLongerEligible() {
    CurrentUser.set(platformAdmin());
    when(repository.application(7L)).thenReturn(Map.of(
        "status", "ACTIVE", "environment", "PRODUCTION", "customerUserId", 19L));
    when(repository.customerOwner(19L)).thenReturn(Map.of());

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.issueKey(7L, new IssueOpenApiKeyRequest(
            "客户系统生产接入", OffsetDateTime.now().plusDays(30))));

    assertEquals("INVALID_OPEN_API_CONFIGURATION", exception.getCode());
    verify(repository, never()).createAccessKey(any(), any(), any(), any(), any(), any(), any());
  }

  private static AuthPrincipal platformAdmin() {
    return new AuthPrincipal(
        1L, "USR-1", 1L, "平台", "admin", "平台运营", null,
        "admin@example.com", "PLATFORM_ADMIN", List.of());
  }
}
