package com.winpress.commercial.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.winpress.commercial.config.ApiResponse;
import com.winpress.commercial.federation.FederatedOrderService;
import com.winpress.commercial.federation.FederatedQuoteService;
import com.winpress.commercial.federation.FederationRequestLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal, server-only GEO adapter. Each operation verifies a signed assertion in its service. */
@RestController
@RequestMapping("/api/v1/integrations/geo")
public class FederatedOrderController {
  private final FederatedOrderService service;
  private final FederatedQuoteService quoteService;
  private final FederationRequestLimiter limiter;

  public FederatedOrderController(
      FederatedOrderService service,
      FederatedQuoteService quoteService,
      FederationRequestLimiter limiter
  ) {
    this.service = service;
    this.quoteService = quoteService;
    this.limiter = limiter;
  }

  @PostMapping("/orders")
  public ApiResponse<Map<String, Object>> accept(
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request
  ) {
    limiter.check(request.getRemoteAddr());
    return ApiResponse.ok(service.accept(body));
  }

  @PostMapping("/quotes")
  public ApiResponse<Map<String, Object>> quote(
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request
  ) {
    limiter.check(request.getRemoteAddr());
    return ApiResponse.ok(quoteService.quote(body));
  }

  @PostMapping("/catalog/direct-publishing-offers")
  public ApiResponse<Map<String, Object>> directPublishingOffers(
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request
  ) {
    limiter.check(request.getRemoteAddr());
    return ApiResponse.ok(quoteService.directPublishingOffers(body));
  }
}
