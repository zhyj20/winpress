package com.winpress.commercial.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.winpress.commercial.config.ApiResponse;
import com.winpress.commercial.service.OpenApiClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contracted customer integration endpoints. They use a distinct API-key guard, never an
 * interactive console session, and only return customer-safe project and channel fields.
 */
@RestController
@RequestMapping("/api/v1/open-api")
public class OpenApiClientController {
  private final OpenApiClientService service;

  public OpenApiClientController(OpenApiClientService service) {
    this.service = service;
  }

  @GetMapping("/health")
  @Operation(summary = "服务状态")
  public ApiResponse<Map<String, Object>> health() {
    return ApiResponse.ok(service.health());
  }

  @GetMapping("/v1/services")
  @SecurityRequirement(name = "openApiKey")
  public ApiResponse<Map<String, Object>> serviceCatalog(
      @RequestHeader(value = "X-WinPress-API-Key", required = false) String accessKey) {
    return ApiResponse.ok(service.serviceCatalog(accessKey));
  }

  @GetMapping("/v1/direct-publishing/channels")
  @SecurityRequirement(name = "openApiKey")
  public ApiResponse<Map<String, Object>> directPublishingChannels(
      @RequestHeader(value = "X-WinPress-API-Key", required = false) String accessKey,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String category,
      @RequestParam(required = false, name = "publish_form") String publishForm,
      @RequestParam(required = false, name = "min_price") BigDecimal minPrice,
      @RequestParam(required = false, name = "max_price") BigDecimal maxPrice,
      @RequestParam(required = false, name = "max_days") Integer maxDays,
      @RequestParam(required = false, name = "link_support") Boolean linkSupport,
      @RequestParam(required = false, name = "link_type") String linkType,
      @RequestParam(required = false, name = "news_source") String newsSource,
      @RequestParam(required = false, name = "entry_level") String entryLevel,
      @RequestParam(required = false, name = "special_industry") String specialIndustry,
      @RequestParam(required = false, name = "weekend_policy") String weekendPolicy,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20", name = "page_size") int pageSize) {
    return ApiResponse.ok(service.directPublishingChannels(
        accessKey, keyword, region, category, publishForm, minPrice, maxPrice, maxDays,
        linkSupport, linkType, newsSource, entryLevel, specialIndustry, weekendPolicy,
        sort, page, pageSize));
  }

  @GetMapping("/v1/direct-publishing/taxonomy")
  @SecurityRequirement(name = "openApiKey")
  public ApiResponse<Map<String, Object>> directPublishingTaxonomy(
      @RequestHeader(value = "X-WinPress-API-Key", required = false) String accessKey) {
    return ApiResponse.ok(service.directPublishingTaxonomy(accessKey));
  }

  @PostMapping("/v1/requirements")
  @SecurityRequirement(name = "openApiKey")
  public ApiResponse<Map<String, Object>> submitRequirement(
      @RequestHeader(value = "X-WinPress-API-Key", required = false) String accessKey,
      @RequestBody(required = false) JsonNode body) {
    return ApiResponse.ok(service.submitRequirement(accessKey, body));
  }

  @GetMapping("/v1/requirements")
  @SecurityRequirement(name = "openApiKey")
  public ApiResponse<Map<String, Object>> requirements(
      @RequestHeader(value = "X-WinPress-API-Key", required = false) String accessKey,
      @RequestParam(defaultValue = "20") int limit) {
    return ApiResponse.ok(service.requirements(accessKey, limit));
  }

  @GetMapping("/v1/requirements/{externalRequestId}")
  @SecurityRequirement(name = "openApiKey")
  public ApiResponse<Map<String, Object>> requirement(
      @RequestHeader(value = "X-WinPress-API-Key", required = false) String accessKey,
      @PathVariable String externalRequestId) {
    return ApiResponse.ok(service.requirement(accessKey, externalRequestId));
  }
}
