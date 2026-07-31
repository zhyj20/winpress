package com.winpress.commercial.controller;

import com.winpress.commercial.config.ApiResponse;
import com.winpress.commercial.dto.IntegrationAdminDtos.SaveSupplierApiConnectionRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateAcceptanceGateRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateAcceptanceEvidenceRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateLegacyServiceReviewRequest;
import com.winpress.commercial.service.IntegrationAdminService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/integrations")
public class IntegrationAdminController {
  private final IntegrationAdminService service;

  public IntegrationAdminController(IntegrationAdminService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> overview() {
    return ApiResponse.ok(service.overview());
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(
      @Valid @RequestBody SaveSupplierApiConnectionRequest request) {
    return ApiResponse.ok(service.createConnection(request));
  }

  @PutMapping("/{connectionId}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable Long connectionId,
      @Valid @RequestBody SaveSupplierApiConnectionRequest request) {
    return ApiResponse.ok(service.updateConnection(connectionId, request));
  }

  @PostMapping("/{connectionId}/check")
  public ApiResponse<Map<String, Object>> check(@PathVariable Long connectionId) {
    return ApiResponse.ok(service.checkConfiguration(connectionId));
  }

  @PutMapping("/acceptance-gates/{gateCode}")
  public ApiResponse<Map<String, Object>> updateAcceptanceGate(
      @PathVariable String gateCode,
      @Valid @RequestBody UpdateAcceptanceGateRequest request) {
    return ApiResponse.ok(service.updateAcceptanceGate(gateCode, request));
  }

  @PutMapping("/acceptance-evidence/{evidenceItemId}")
  public ApiResponse<Map<String, Object>> updateAcceptanceEvidence(
      @PathVariable Long evidenceItemId,
      @Valid @RequestBody UpdateAcceptanceEvidenceRequest request) {
    return ApiResponse.ok(service.updateAcceptanceEvidenceItem(evidenceItemId, request));
  }

  @PutMapping("/legacy-service-reviews/{reviewId}")
  public ApiResponse<Map<String, Object>> updateLegacyServiceReview(
      @PathVariable Long reviewId,
      @Valid @RequestBody UpdateLegacyServiceReviewRequest request) {
    return ApiResponse.ok(service.updateLegacyServiceReview(reviewId, request));
  }
}
