package com.winpress.commercial.controller;

import com.winpress.commercial.config.ApiResponse;
import com.winpress.commercial.dto.OpenApiDtos.IssueOpenApiKeyRequest;
import com.winpress.commercial.dto.OpenApiDtos.SaveOpenApiApplicationRequest;
import com.winpress.commercial.service.OpenApiAdminService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-only management of contracted customer API clients and their credentials. */
@RestController
@RequestMapping("/api/v1/admin/open-api")
@SecurityRequirement(name = "bearerAuth")
public class OpenApiAdminController {
  private final OpenApiAdminService service;

  public OpenApiAdminController(OpenApiAdminService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> overview() {
    return ApiResponse.ok(service.overview());
  }

  @PostMapping("/applications")
  public ApiResponse<Map<String, Object>> createApplication(
      @Valid @RequestBody SaveOpenApiApplicationRequest request) {
    return ApiResponse.ok(service.createApplication(request));
  }

  @PutMapping("/applications/{applicationId}")
  public ApiResponse<Map<String, Object>> updateApplication(
      @PathVariable Long applicationId,
      @Valid @RequestBody SaveOpenApiApplicationRequest request) {
    return ApiResponse.ok(service.updateApplication(applicationId, request));
  }

  @PostMapping("/applications/{applicationId}/keys")
  public ApiResponse<Map<String, Object>> issueKey(
      @PathVariable Long applicationId,
      @Valid @RequestBody IssueOpenApiKeyRequest request) {
    return ApiResponse.ok(service.issueKey(applicationId, request));
  }

  @PostMapping("/keys/{keyId}/revoke")
  public ApiResponse<Map<String, Object>> revokeKey(@PathVariable Long keyId) {
    return ApiResponse.ok(service.revokeKey(keyId));
  }
}
