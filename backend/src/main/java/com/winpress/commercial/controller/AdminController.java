package com.winpress.commercial.controller;

import com.winpress.commercial.config.ApiResponse;
import com.winpress.commercial.dto.WorkflowDtos.AssignProjectRequest;
import com.winpress.commercial.dto.WorkflowDtos.AssignSupplierChannelRequest;
import com.winpress.commercial.dto.WorkflowDtos.BatchQuoteAdjustmentRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateChannelRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateQuoteRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateSettlementTransactionRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateSupplierRequest;
import com.winpress.commercial.dto.WorkflowDtos.PageResult;
import com.winpress.commercial.dto.WorkflowDtos.OfferWritingAssignmentRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateSettlementRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateChannelRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateBusinessInquiryRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateSupplierOrderRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateSupplierRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateUserRequest;
import com.winpress.commercial.dto.WorkflowDtos.VoidSettlementTransactionRequest;
import com.winpress.commercial.service.AdminUserService;
import com.winpress.commercial.service.WorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final WorkflowService service;
  private final AdminUserService userService;

  public AdminController(WorkflowService service, AdminUserService userService) { this.service = service; this.userService = userService; }

  @GetMapping("/operators")
  public ApiResponse<List<Map<String, Object>>> operators() { return ApiResponse.ok(service.operators()); }

  @GetMapping("/writers")
  public ApiResponse<List<Map<String, Object>>> writers() { return ApiResponse.ok(service.writerProfiles()); }

  @PostMapping("/writing-assignments/{assignmentId}/offer")
  public ApiResponse<Map<String, Object>> offerWritingAssignment(
      @PathVariable Long assignmentId, @Valid @RequestBody OfferWritingAssignmentRequest request) {
    return ApiResponse.ok(service.offerWritingAssignment(assignmentId, request));
  }

  @PatchMapping("/projects/{projectId}/assign")
  public ApiResponse<Map<String, Object>> assign(
      @PathVariable Long projectId, @Valid @RequestBody AssignProjectRequest request) {
    return ApiResponse.ok(service.assignProject(projectId, request));
  }

  @PostMapping("/channels")
  public ApiResponse<Map<String, Object>> createChannel(@Valid @RequestBody CreateChannelRequest request) {
    return ApiResponse.ok(service.createChannel(request));
  }

  @GetMapping("/channels")
  public ApiResponse<PageResult<Map<String, Object>>> channels(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.adminChannels(type, status, keyword, page, pageSize));
  }

  @PutMapping("/channels/{channelId}")
  public ApiResponse<Map<String, Object>> updateChannel(
      @PathVariable Long channelId, @Valid @RequestBody UpdateChannelRequest request) {
    return ApiResponse.ok(service.updateChannel(channelId, request));
  }

  @GetMapping("/pricing")
  public ApiResponse<PageResult<Map<String, Object>>> pricing(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String category,
      @RequestParam(required = false, name = "publish_form") String publishForm,
      @RequestParam(required = false, name = "channel_status") String channelStatus,
      @RequestParam(required = false, name = "quote_state") String quoteState,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "30") int pageSize) {
    return ApiResponse.ok(service.pricingChannels(
        keyword, region, category, publishForm, channelStatus, quoteState, page, pageSize));
  }

  @GetMapping("/pricing/summary")
  public ApiResponse<Map<String, Object>> pricingSummary() { return ApiResponse.ok(service.pricingSummary()); }

  @GetMapping("/pricing/compare")
  public ApiResponse<List<Map<String, Object>>> comparePricing(@RequestParam List<Long> channelIds) {
    return ApiResponse.ok(service.comparePricing(channelIds));
  }

  @PostMapping("/pricing/quotes")
  public ApiResponse<Map<String, Object>> createQuote(@Valid @RequestBody CreateQuoteRequest request) {
    return ApiResponse.ok(service.createQuote(request));
  }

  @PostMapping("/pricing/adjustments")
  public ApiResponse<Map<String, Object>> batchAdjustQuotes(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody BatchQuoteAdjustmentRequest request) {
    return ApiResponse.ok(service.batchAdjustQuotes(request, idempotencyKey));
  }

  @GetMapping("/pricing/{channelId}/adjustments")
  public ApiResponse<List<Map<String, Object>>> quoteAdjustments(@PathVariable Long channelId) {
    return ApiResponse.ok(service.quoteAdjustments(channelId));
  }

  @GetMapping("/suppliers")
  public ApiResponse<PageResult<Map<String, Object>>> suppliers(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.suppliers(type, status, keyword, page, pageSize));
  }

  @GetMapping("/suppliers/options")
  public ApiResponse<List<Map<String, Object>>> supplierOptions(
      @RequestParam(required = false) Long channelId) {
    return ApiResponse.ok(service.supplierOptions(channelId));
  }

  @PostMapping("/suppliers")
  public ApiResponse<Map<String, Object>> createSupplier(
      @Valid @RequestBody CreateSupplierRequest request) {
    return ApiResponse.ok(service.createSupplier(request));
  }

  @PutMapping("/suppliers/{supplierId}")
  public ApiResponse<Map<String, Object>> updateSupplier(
      @PathVariable Long supplierId, @Valid @RequestBody UpdateSupplierRequest request) {
    return ApiResponse.ok(service.updateSupplier(supplierId, request));
  }

  @GetMapping("/supplier-channels")
  public ApiResponse<PageResult<Map<String, Object>>> supplierChannels(
      @RequestParam(required = false) Long supplierId,
      @RequestParam(required = false) Long channelId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "30") int pageSize) {
    return ApiResponse.ok(service.supplierChannels(supplierId, channelId, page, pageSize));
  }

  @PostMapping("/supplier-channels")
  public ApiResponse<Map<String, Object>> assignSupplierChannel(
      @Valid @RequestBody AssignSupplierChannelRequest request) {
    return ApiResponse.ok(service.assignSupplierChannel(request));
  }

  @GetMapping("/supplier-orders")
  public ApiResponse<PageResult<Map<String, Object>>> supplierOrders(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long supplierId,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.supplierOrders(status, supplierId, keyword, page, pageSize));
  }

  @GetMapping("/supplier-orders/summary")
  public ApiResponse<Map<String, Object>> supplierOrderSummary() {
    return ApiResponse.ok(service.supplierOrderSummary());
  }

  @GetMapping("/supplier-orders/{supplierOrderId}/history")
  public ApiResponse<List<Map<String, Object>>> supplierOrderHistory(
      @PathVariable Long supplierOrderId) {
    return ApiResponse.ok(service.supplierOrderHistory(supplierOrderId));
  }

  @PatchMapping("/supplier-orders/{supplierOrderId}")
  public ApiResponse<Map<String, Object>> updateSupplierOrder(
      @PathVariable Long supplierOrderId,
      @Valid @RequestBody UpdateSupplierOrderRequest request) {
    return ApiResponse.ok(service.updateSupplierOrder(supplierOrderId, request));
  }

  @GetMapping("/inquiries")
  public ApiResponse<PageResult<Map<String, Object>>> inquiries(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.businessInquiries(status, type, page, pageSize));
  }

  @PatchMapping("/inquiries/{inquiryId}")
  public ApiResponse<Map<String, Object>> updateInquiry(
      @PathVariable Long inquiryId,
      @Valid @RequestBody UpdateBusinessInquiryRequest request) {
    return ApiResponse.ok(service.updateBusinessInquiry(inquiryId, request));
  }

  @GetMapping("/settlements")
  public ApiResponse<PageResult<Map<String, Object>>> settlements(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.settlements(status, page, pageSize));
  }

  @PatchMapping("/settlements/{settlementId}")
  public ApiResponse<Map<String, Object>> updateSettlement(
      @PathVariable Long settlementId, @Valid @RequestBody UpdateSettlementRequest request) {
    return ApiResponse.ok(service.updateSettlement(settlementId, request.status(), request.invoiceNo()));
  }

  @GetMapping("/settlement-transactions")
  public ApiResponse<PageResult<Map<String, Object>>> settlementTransactions(
      @RequestParam(required = false) Long settlementId,
      @RequestParam(required = false) String transactionType,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.settlementTransactions(
        settlementId, transactionType, status, page, pageSize));
  }

  @PostMapping("/settlements/{settlementId}/transactions")
  public ApiResponse<Map<String, Object>> createSettlementTransaction(
      @PathVariable Long settlementId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateSettlementTransactionRequest request) {
    return ApiResponse.ok(
        service.createSettlementTransaction(settlementId, request, idempotencyKey));
  }

  @PostMapping("/settlement-transactions/{transactionId}/void")
  public ApiResponse<Map<String, Object>> voidSettlementTransaction(
      @PathVariable Long transactionId,
      @Valid @RequestBody VoidSettlementTransactionRequest request) {
    return ApiResponse.ok(service.voidSettlementTransaction(transactionId, request));
  }

  @GetMapping("/operation-logs")
  public ApiResponse<List<Map<String, Object>>> logs(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int pageSize) {
    return ApiResponse.ok(service.logs(page, pageSize));
  }

  @GetMapping("/users")
  public ApiResponse<PageResult<Map<String, Object>>> users(
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(userService.users(role, status, page, pageSize));
  }

  @GetMapping("/roles")
  public ApiResponse<List<Map<String, Object>>> roles() { return ApiResponse.ok(userService.roles()); }

  @PatchMapping("/users/{userId}")
  public ApiResponse<Map<String, Object>> updateUser(
      @PathVariable Long userId, @Valid @RequestBody UpdateUserRequest request) {
    return ApiResponse.ok(userService.updateUser(userId, request));
  }
}
