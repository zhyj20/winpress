package com.winpress.commercial.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winpress.commercial.dto.OpenApiDtos.OpenApiRequirementRequest;
import com.winpress.commercial.dto.WorkflowDtos.CreateRequirementRequest;
import com.winpress.commercial.dto.WorkflowDtos.PageResult;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.AuthRepository;
import com.winpress.commercial.repository.OpenApiRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer-system entry point for the migrated API tool. Every accepted request is materialised
 * through the normal WinPress workflow so that it appears in existing project, task and order
 * views instead of creating a parallel operational database.
 */
@Service
public class OpenApiClientService {
  private static final Set<String> SERVICE_TYPES = Set.of(
      "ONSITE_WRITING", "MEDIA_PR", "DIRECT_PUBLISHING", "NEWS_CONFERENCE");
  private static final Set<String> REQUEST_FIELDS = Set.of(
      "external_request_id", "service_type", "title", "event_time", "event_location", "facts",
      "objective", "target_audience", "service_days", "writer_count", "onsite_contact_name",
      "onsite_contact_mobile", "deliverable_requirement", "conference_type", "conference_format",
      "conference_scale", "conference_media_goal", "conference_agenda_status",
      "conference_venue_status", "conference_contact_name", "conference_contact_mobile", "due_at");
  private static final Set<String> FORBIDDEN_FIELD_TOKENS = Set.of(
      "supplier", "cost", "margin", "internal_note", "upstream", "secret", "token", "api_key", "apikey");
  private static final Pattern EXTERNAL_REQUEST_ID =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{2,79}");

  private final OpenApiRepository repository;
  private final AuthRepository authRepository;
  private final WorkflowService workflowService;
  private final OpenApiRateLimiter limiter;
  private final ObjectMapper objectMapper;
  private final Validator validator;

  public OpenApiClientService(
      OpenApiRepository repository,
      AuthRepository authRepository,
      WorkflowService workflowService,
      OpenApiRateLimiter limiter,
      ObjectMapper objectMapper,
      Validator validator) {
    this.repository = repository;
    this.authRepository = authRepository;
    this.workflowService = workflowService;
    this.limiter = limiter;
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  public Map<String, Object> health() {
    return Map.of(
        "status", "AVAILABLE",
        "service", "WinPress Open API",
        "authentication", "X-WinPress-API-Key",
        "externalMediaData", "PENDING_AUTHORIZATION"
    );
  }

  public Map<String, Object> serviceCatalog(String rawKey) {
    OpenApiPrincipal principal = authenticate(rawKey, "SERVICE_CATALOG");
    long startedAt = System.nanoTime();
    try {
      List<Map<String, String>> services = List.of(
          catalogItem("ONSITE_WRITING", "云采写", "提交活动采写需求后进入写手匹配和稿件协同。"),
          catalogItem("MEDIA_PR", "邀请媒体", "建立媒体邀请项目，由项目负责人核定候选与执行范围。"),
          catalogItem("DIRECT_PUBLISHING", "直编发稿", "创建发稿项目后，在管理平台完成稿件确认与渠道计划。"),
          catalogItem("NEWS_CONFERENCE", "举办新闻发布会", "创建发布会项目后，在项目清单中推进会务和传播安排。")
      );
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), null, "SERVICE_CATALOG",
          null, 200, "OK", elapsedMillis(startedAt));
      return Map.of("items", services, "environment", principal.environment());
    } catch (BusinessException exception) {
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), null, "SERVICE_CATALOG",
          null, exception.getStatus().value(), exception.getCode(), elapsedMillis(startedAt));
      throw exception;
    }
  }

  public Map<String, Object> directPublishingChannels(
      String rawKey, String keyword, String region, String category, String publishForm,
      BigDecimal minPrice, BigDecimal maxPrice, Integer maxDays, Boolean linkSupport,
      String linkType, String newsSource, String entryLevel, String specialIndustry,
      String weekendPolicy, String sort, int page, int pageSize) {
    OpenApiPrincipal principal = authenticate(rawKey, "DIRECT_CHANNEL_CATALOG");
    long startedAt = System.nanoTime();
    try {
      PageResult<Map<String, Object>> result = workflowService.openApiChannels(
          "DIRECT_PUBLISHING", keyword, region, category, publishForm, minPrice, maxPrice, maxDays,
          linkSupport, linkType, newsSource, entryLevel, specialIndustry, weekendPolicy,
          sort, page, pageSize);
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("items", result.items());
      response.put("total", result.total());
      response.put("page", result.page());
      response.put("pageSize", result.pageSize());
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), null,
          "DIRECT_CHANNEL_CATALOG", null, 200, "OK", elapsedMillis(startedAt));
      return response;
    } catch (BusinessException exception) {
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), null,
          "DIRECT_CHANNEL_CATALOG", null, exception.getStatus().value(), exception.getCode(),
          elapsedMillis(startedAt));
      throw exception;
    }
  }

  public Map<String, Object> directPublishingTaxonomy(String rawKey) {
    OpenApiPrincipal principal = authenticate(rawKey, "DIRECT_CHANNEL_CATALOG");
    long startedAt = System.nanoTime();
    try {
      Map<String, Object> response = workflowService.openApiChannelTaxonomy("DIRECT_PUBLISHING");
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), null,
          "DIRECT_CHANNEL_TAXONOMY", null, 200, "OK", elapsedMillis(startedAt));
      return response;
    } catch (BusinessException exception) {
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), null,
          "DIRECT_CHANNEL_TAXONOMY", null, exception.getStatus().value(), exception.getCode(),
          elapsedMillis(startedAt));
      throw exception;
    }
  }

  @Transactional
  public Map<String, Object> submitRequirement(String rawKey, JsonNode body) {
    OpenApiPrincipal principal = authenticate(rawKey, "REQUIREMENT_CREATE");
    long startedAt = System.nanoTime();
    String requestHash = null;
    String externalRequestId = null;
    try {
      assertAllowedRequest(body);
      OpenApiRequirementRequest request = decode(body);
      externalRequestId = request.externalRequestId();
      validateRequest(request);
      requestHash = sha256(canonicalBody(body));
      Map<String, Object> existing = repository.receipt(principal.applicationId(), externalRequestId);
      if (!existing.isEmpty()) {
        if (!requestHash.equals(String.valueOf(existing.get("requestHash")))) {
          throw conflict("相同 external_request_id 不能用于不同的服务内容");
        }
        repository.accessLog(principal.applicationId(), principal.accessKeyId(), externalRequestId,
            "REQUIREMENT_CREATE", requestHash, 200, "IDEMPOTENT_REPLAY", elapsedMillis(startedAt));
        return receiptResponse(existing, true);
      }

      AuthPrincipal owner = authRepository.activePrincipalById(principal.customerUserId());
      if (owner == null || !"CUSTOMER".equals(owner.role())) {
        throw unavailable("接入应用归属客户账号不可用，请联系平台运营核验配置");
      }
      CreateRequirementRequest workflowRequest = workflowRequest(request);
      Map<String, Object> workflowResponse;
      CurrentUser.set(owner);
      try {
        workflowResponse = workflowService.createRequirement(workflowRequest, internalIdempotency(principal, requestHash));
      } finally {
        CurrentUser.clear();
      }
      Long projectId = number(workflowResponse.get("projectId"));
      Map<String, Object> reference = repository.projectReference(projectId, owner.userId());
      if (reference.isEmpty()) {
        throw unavailable("项目已创建但无法读取受理回执，请联系平台运营核验");
      }
      try {
        repository.createReceipt(principal.applicationId(), externalRequestId, requestHash,
            number(reference.get("requirementId")), projectId, request.serviceType().trim().toUpperCase(Locale.ROOT),
            "ACCEPTED");
      } catch (DataIntegrityViolationException exception) {
        Map<String, Object> replay = repository.receipt(principal.applicationId(), externalRequestId);
        if (!replay.isEmpty() && requestHash.equals(String.valueOf(replay.get("requestHash")))) {
          repository.accessLog(principal.applicationId(), principal.accessKeyId(), externalRequestId,
              "REQUIREMENT_CREATE", requestHash, 200, "IDEMPOTENT_REPLAY", elapsedMillis(startedAt));
          return receiptResponse(replay, true);
        }
        throw conflict("服务受理回执冲突，请使用新的 external_request_id");
      }
      Map<String, Object> receipt = repository.receipt(principal.applicationId(), externalRequestId);
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), externalRequestId,
          "REQUIREMENT_CREATE", requestHash, 201, "ACCEPTED", elapsedMillis(startedAt));
      return receiptResponse(receipt, false);
    } catch (BusinessException exception) {
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), externalRequestId,
          "REQUIREMENT_CREATE", requestHash, exception.getStatus().value(), exception.getCode(),
          elapsedMillis(startedAt));
      throw exception;
    }
  }

  public Map<String, Object> requirement(String rawKey, String externalRequestId) {
    OpenApiPrincipal principal = authenticate(rawKey, "PROJECT_READ");
    long startedAt = System.nanoTime();
    try {
      Map<String, Object> receipt = repository.receipt(principal.applicationId(), externalRequestId);
      if (receipt.isEmpty()) throw notFound("未找到本应用对应的服务受理记录");
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), externalRequestId,
          "PROJECT_READ", null, 200, "OK", elapsedMillis(startedAt));
      return receiptResponse(receipt, false);
    } catch (BusinessException exception) {
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), externalRequestId,
          "PROJECT_READ", null, exception.getStatus().value(), exception.getCode(), elapsedMillis(startedAt));
      throw exception;
    }
  }

  public Map<String, Object> requirements(String rawKey, int limit) {
    OpenApiPrincipal principal = authenticate(rawKey, "PROJECT_READ");
    long startedAt = System.nanoTime();
    try {
      List<Map<String, Object>> items = repository.receipts(principal.applicationId(), limit).stream()
          .map(row -> receiptResponse(row, false)).toList();
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), null,
          "PROJECT_LIST", null, 200, "OK", elapsedMillis(startedAt));
      return Map.of("items", items, "limit", Math.max(1, Math.min(limit, 100)));
    } catch (BusinessException exception) {
      repository.accessLog(principal.applicationId(), principal.accessKeyId(), null,
          "PROJECT_LIST", null, exception.getStatus().value(), exception.getCode(), elapsedMillis(startedAt));
      throw exception;
    }
  }

  private OpenApiPrincipal authenticate(String rawKey, String requiredScope) {
    if (rawKey == null || rawKey.length() < 24 || rawKey.length() > 200
        || !rawKey.matches("[A-Za-z0-9_-]+")) {
      throw unauthorized();
    }
    Map<String, Object> row = repository.activeKeyPrincipal(OpenApiAdminService.sha256(rawKey));
    if (row.isEmpty()) throw unauthorized();
    Set<String> scopes = scopes(String.valueOf(row.get("serviceScopes")));
    if (!scopes.contains(requiredScope)) {
      throw new BusinessException("OPEN_API_SCOPE_DENIED", "当前访问密钥未获授该接口能力", HttpStatus.FORBIDDEN);
    }
    Long applicationId = number(row.get("applicationId"));
    Long keyId = number(row.get("accessKeyId"));
    try {
      limiter.check(applicationId, ((Number) row.get("rateLimitPerMinute")).intValue());
    } catch (BusinessException exception) {
      if ("OPEN_API_RATE_LIMITED".equals(exception.getCode())) {
        // A rejected burst is itself useful audit evidence. It contains no request body or key.
        try {
          repository.accessLog(applicationId, keyId, null, requiredScope, null,
              exception.getStatus().value(), exception.getCode(), 0);
        } catch (RuntimeException ignored) {
          // Do not turn an already rate-limited request into a more revealing server failure.
        }
      }
      throw exception;
    }
    repository.markAccessKeyUsed(keyId);
    return new OpenApiPrincipal(applicationId, keyId, number(row.get("customerUserId")),
        String.valueOf(row.get("applicationNo")), String.valueOf(row.get("environment")), scopes);
  }

  private void assertAllowedRequest(JsonNode body) {
    if (body == null || !body.isObject()) throw invalid("请求体必须为 JSON 对象");
    List<String> unknown = new ArrayList<>();
    body.fieldNames().forEachRemaining(name -> {
      if (!REQUEST_FIELDS.contains(name)) unknown.add(name);
    });
    if (!unknown.isEmpty()) {
      throw invalid("请求包含未开放字段：" + String.join("、", unknown));
    }
    assertNoForbiddenFields(body);
  }

  private void assertNoForbiddenFields(JsonNode node) {
    if (node.isObject()) {
      node.fields().forEachRemaining(entry -> {
        String key = entry.getKey().toLowerCase(Locale.ROOT).replace('-', '_');
        for (String token : FORBIDDEN_FIELD_TOKENS) {
          if (key.contains(token)) throw invalid("请求不得包含内部字段");
        }
        assertNoForbiddenFields(entry.getValue());
      });
    } else if (node.isArray()) {
      for (JsonNode item : node) assertNoForbiddenFields(item);
    }
  }

  private OpenApiRequirementRequest decode(JsonNode body) {
    try {
      return objectMapper.treeToValue(body, OpenApiRequirementRequest.class);
    } catch (JsonProcessingException exception) {
      throw invalid("请求体格式不正确");
    }
  }

  private void validateRequest(OpenApiRequirementRequest request) {
    Set<ConstraintViolation<OpenApiRequirementRequest>> violations = validator.validate(request);
    if (!violations.isEmpty()) throw invalid(violations.iterator().next().getMessage());
    if (!EXTERNAL_REQUEST_ID.matcher(request.externalRequestId()).matches()) {
      throw invalid("external_request_id 只能使用字母、数字、点、下划线、连字符或冒号");
    }
    if (!SERVICE_TYPES.contains(request.serviceType().trim().toUpperCase(Locale.ROOT))) {
      throw invalid("service_type 仅支持四项独立服务");
    }
  }

  private static CreateRequirementRequest workflowRequest(OpenApiRequirementRequest request) {
    return new CreateRequirementRequest(
        request.title().trim(), request.eventTime(), trim(request.eventLocation()), trim(request.facts()),
        trim(request.objective()), trim(request.targetAudience()),
        request.serviceType().trim().toUpperCase(Locale.ROOT), request.serviceDays(), request.writerCount(),
        trim(request.onsiteContactName()), trim(request.onsiteContactMobile()),
        trim(request.deliverableRequirement()), trim(request.conferenceType()), trim(request.conferenceFormat()),
        trim(request.conferenceScale()), trim(request.conferenceMediaGoal()),
        trim(request.conferenceAgendaStatus()), trim(request.conferenceVenueStatus()),
        trim(request.conferenceContactName()), trim(request.conferenceContactMobile()), request.dueAt());
  }

  private static Map<String, Object> receiptResponse(Map<String, Object> receipt, boolean idempotentReplay) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("external_request_id", receipt.get("externalRequestId"));
    response.put("service_type", receipt.get("serviceType"));
    response.put("requirement_no", receipt.get("requirementNo"));
    response.put("project_no", receipt.get("projectNo"));
    response.put("project_name", receipt.get("projectName"));
    response.put("status", receipt.get("projectStatus"));
    response.put("accepted_at", receipt.get("createdAt"));
    response.put("updated_at", receipt.get("projectUpdatedAt"));
    response.put("idempotent_replay", idempotentReplay);
    return response;
  }

  private static String internalIdempotency(OpenApiPrincipal principal, String requestHash) {
    return "openapi." + principal.applicationId() + "." + requestHash.substring(0, 48);
  }

  private static Set<String> scopes(String source) {
    Set<String> output = new LinkedHashSet<>();
    for (String value : source.split(",")) {
      if (!value.isBlank()) output.add(value.trim().toUpperCase(Locale.ROOT));
    }
    return output;
  }

  private static Map<String, String> catalogItem(String code, String name, String detail) {
    return Map.of("code", code, "name", name, "detail", detail);
  }

  private static Long number(Object value) {
    if (value instanceof Number number) return number.longValue();
    throw unavailable("接口数据状态异常，请联系平台运营核验");
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  /**
   * Produces a stable digest input so an idempotent replay is not rejected merely because a client
   * serialised the same allowed JSON fields in a different order. The payload itself is never
   * retained in the database or access log.
   */
  private static String canonicalBody(JsonNode node) {
    if (node.isObject()) {
      TreeMap<String, JsonNode> fields = new TreeMap<>();
      node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
      StringBuilder output = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<String, JsonNode> entry : fields.entrySet()) {
        if (!first) output.append(',');
        output.append('"').append(entry.getKey()).append('"').append(':')
            .append(canonicalBody(entry.getValue()));
        first = false;
      }
      return output.append('}').toString();
    }
    if (node.isArray()) {
      StringBuilder output = new StringBuilder("[");
      boolean first = true;
      for (JsonNode item : node) {
        if (!first) output.append(',');
        output.append(canonicalBody(item));
        first = false;
      }
      return output.append(']').toString();
    }
    return node.toString();
  }

  private static int elapsedMillis(long startedAt) {
    long millis = (System.nanoTime() - startedAt) / 1_000_000L;
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, millis));
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  private static BusinessException invalid(String message) {
    return new BusinessException("INVALID_OPEN_API_REQUEST", message, HttpStatus.BAD_REQUEST);
  }

  private static BusinessException unauthorized() {
    return new BusinessException("OPEN_API_UNAUTHORIZED", "访问密钥无效、已过期或未启用", HttpStatus.UNAUTHORIZED);
  }

  private static BusinessException conflict(String message) {
    return new BusinessException("OPEN_API_IDEMPOTENCY_CONFLICT", message, HttpStatus.CONFLICT);
  }

  private static BusinessException notFound(String message) {
    return new BusinessException("OPEN_API_RECORD_NOT_FOUND", message, HttpStatus.NOT_FOUND);
  }

  private static BusinessException unavailable(String message) {
    return new BusinessException("OPEN_API_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
  }

  private record OpenApiPrincipal(
      Long applicationId, Long accessKeyId, Long customerUserId, String applicationNo,
      String environment, Set<String> scopes) {}
}
