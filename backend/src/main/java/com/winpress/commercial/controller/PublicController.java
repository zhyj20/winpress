package com.winpress.commercial.controller;

import com.winpress.commercial.config.ApiResponse;
import com.winpress.commercial.dto.WorkflowDtos.CreateBusinessInquiryRequest;
import com.winpress.commercial.service.WorkflowService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicController {
  private final WorkflowService service;

  public PublicController(WorkflowService service) {
    this.service = service;
  }

  @PostMapping("/inquiries")
  public ApiResponse<Map<String, Object>> createInquiry(
      @Valid @RequestBody CreateBusinessInquiryRequest request) {
    return ApiResponse.ok(service.createBusinessInquiry(request));
  }
}
