package com.winpress.commercial.service;

import com.winpress.commercial.dto.OpenApiDtos.IssueOpenApiKeyRequest;
import com.winpress.commercial.dto.OpenApiDtos.SaveOpenApiApplicationRequest;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.OpenApiRepository;
import com.winpress.commercial.repository.WorkflowRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform-only administration of API clients and one-time-issued client credentials. */
@Service
public class OpenApiAdminService {
  static final Set<String> SCOPES = Set.of(
      "SERVICE_CATALOG", "REQUIREMENT_CREATE", "PROJECT_READ", "DIRECT_CHANNEL_CATALOG");
  private static final Set<String> ENVIRONMENTS = Set.of("SANDBOX", "PRODUCTION");
  private static final Set<String> APPLICATION_STATES = Set.of("DRAFT", "ACTIVE", "SUSPENDED", "REVOKED");
  private static final Set<String> AUTHORIZATION_STATES = Set.of(
      "NOT_SUBMITTED", "PENDING", "VERIFIED", "REJECTED");
  private static final Set<String> SANDBOX_STATES = Set.of(
      "NOT_TESTED", "PENDING", "PASSED", "FAILED");
  private static final Set<String> PRODUCTION_STATES = Set.of(
      "NOT_APPROVED", "PENDING", "APPROVED", "REVOKED");
  private static final Pattern CLIENT_CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{1,79}");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OpenApiRepository repository;
  private final WorkflowRepository workflowRepository;

  public OpenApiAdminService(OpenApiRepository repository, WorkflowRepository workflowRepository) {
    this.repository = repository;
    this.workflowRepository = workflowRepository;
  }

  public Map<String, Object> overview() {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    repository.expireDueAccessKeys();
    List<Map<String, Object>> applications = repository.applications();
    List<Map<String, Object>> keys = repository.accessKeys();
    List<Map<String, Object>> logs = repository.accessLogs(300);
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("applicationCount", applications.size());
    summary.put("activeApplicationCount", applications.stream()
        .filter(application -> "ACTIVE".equals(application.get("status"))).count());
    summary.put("activeKeyCount", keys.stream()
        .filter(key -> "ACTIVE".equals(key.get("status"))).count());
    summary.put("last24hRequestCount", logs.stream()
        .filter(log -> log.get("createdAt") != null).count());

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("summary", summary);
    response.put("applications", applications);
    response.put("customerOwners", repository.customerOwners());
    response.put("accessKeys", keys);
    response.put("accessLogs", logs);
    response.put("capabilities", capabilities());
    response.put("securityNotice",
        "访问密钥只在创建时显示一次；数据库、页面和操作日志均不保存或回显原始密钥与请求正文。");
    return response;
  }

  @Transactional
  public Map<String, Object> createApplication(SaveOpenApiApplicationRequest source) {
    AuthPrincipal admin = CurrentUser.requireRole("PLATFORM_ADMIN");
    SaveOpenApiApplicationRequest request = normalize(source);
    validateApplication(request);
    if (repository.customerOwner(request.customerUserId()).isEmpty()) {
      throw notFound("归属客户账号不存在、已停用或不具备客户角色");
    }
    try {
      Long applicationId = repository.createApplication(admin, no("APP"), request, joinScopes(request.serviceScopes()));
      workflowRepository.log(admin, "CREATE_OPEN_API_APPLICATION", "OPEN_API_APPLICATION",
          String.valueOf(applicationId), Map.of(
              "clientCode", request.clientCode(), "environment", request.environment(),
              "status", request.status(), "scopeCount", request.serviceScopes().size()));
      return Map.of("id", applicationId, "status", "SAVED");
    } catch (DataIntegrityViolationException exception) {
      throw conflict("客户标识已存在，或应用配置不满足启用约束");
    }
  }

  @Transactional
  public Map<String, Object> updateApplication(Long applicationId, SaveOpenApiApplicationRequest source) {
    AuthPrincipal admin = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (repository.application(applicationId).isEmpty()) throw notFound("开放 API 应用不存在");
    SaveOpenApiApplicationRequest request = normalize(source);
    validateApplication(request);
    if (repository.customerOwner(request.customerUserId()).isEmpty()) {
      throw notFound("归属客户账号不存在、已停用或不具备客户角色");
    }
    try {
      if (!repository.updateApplication(admin, applicationId, request, joinScopes(request.serviceScopes()))) {
        throw notFound("开放 API 应用不存在");
      }
      workflowRepository.log(admin, "UPDATE_OPEN_API_APPLICATION", "OPEN_API_APPLICATION",
          String.valueOf(applicationId), Map.of(
              "environment", request.environment(), "status", request.status(),
              "scopeCount", request.serviceScopes().size()));
      return Map.of("id", applicationId, "status", "SAVED");
    } catch (DataIntegrityViolationException exception) {
      throw conflict("客户标识已存在，或应用配置不满足启用约束");
    }
  }

  @Transactional
  public Map<String, Object> issueKey(Long applicationId, IssueOpenApiKeyRequest request) {
    AuthPrincipal admin = CurrentUser.requireRole("PLATFORM_ADMIN");
    Map<String, Object> application = repository.application(applicationId);
    if (application.isEmpty()) throw notFound("开放 API 应用不存在");
    if (!"ACTIVE".equals(application.get("status"))) {
      throw invalid("应用尚未完成启用审核，不能签发访问密钥");
    }
    Object customerUserId = application.get("customerUserId");
    if (!(customerUserId instanceof Number ownerId)
        || repository.customerOwner(ownerId.longValue()).isEmpty()) {
      throw invalid("归属客户账号不可用，不能签发访问密钥");
    }
    OffsetDateTime expiresAt = request.expiresAt();
    if (expiresAt == null || !expiresAt.isAfter(OffsetDateTime.now())) {
      throw invalid("请填写未来的密钥到期时间");
    }
    if (expiresAt.isAfter(OffsetDateTime.now().plusDays(366))) {
      throw invalid("单个访问密钥的有效期不能超过366天");
    }
    String environment = String.valueOf(application.get("environment"));
    String rawKey = generateKey(environment);
    String keyPrefix = rawKey.substring(0, Math.min(rawKey.length(), 18));
    Long keyId = repository.createAccessKey(admin, applicationId, no("KEY"), request.keyLabel().trim(),
        keyPrefix, sha256(rawKey), expiresAt);
    workflowRepository.log(admin, "ISSUE_OPEN_API_ACCESS_KEY", "OPEN_API_ACCESS_KEY",
        String.valueOf(keyId), Map.of("applicationId", applicationId, "keyPrefix", keyPrefix));
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", keyId);
    response.put("keyPrefix", keyPrefix);
    response.put("accessKey", rawKey);
    response.put("expiresAt", expiresAt);
    response.put("oneTimeDisplay", true);
    return response;
  }

  @Transactional
  public Map<String, Object> revokeKey(Long keyId) {
    AuthPrincipal admin = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!repository.revokeAccessKey(admin, keyId)) {
      throw notFound("访问密钥不存在、已失效或已撤销");
    }
    workflowRepository.log(admin, "REVOKE_OPEN_API_ACCESS_KEY", "OPEN_API_ACCESS_KEY",
        String.valueOf(keyId), Map.of());
    return Map.of("id", keyId, "status", "REVOKED");
  }

  private static SaveOpenApiApplicationRequest normalize(SaveOpenApiApplicationRequest request) {
    List<String> scopes = request.serviceScopes() == null ? List.of() : request.serviceScopes().stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.trim().toUpperCase(Locale.ROOT))
        .distinct()
        .toList();
    return new SaveOpenApiApplicationRequest(
        trim(request.applicationName()), upper(request.clientCode()), request.customerUserId(),
        upper(request.environment()), scopes, request.rateLimitPerMinute(),
        upper(request.authorizationStatus()), trim(request.authorizationEvidenceRef()),
        upper(request.sandboxStatus()), trim(request.sandboxEvidenceRef()),
        upper(request.productionStatus()), trim(request.productionEvidenceRef()),
        trim(request.contractReference()), trim(request.internalNote()), upper(request.status()));
  }

  private static void validateApplication(SaveOpenApiApplicationRequest request) {
    if (!CLIENT_CODE.matcher(request.clientCode()).matches()) {
      throw invalid("客户标识只能使用大写字母、数字、点、下划线或连字符");
    }
    if (!ENVIRONMENTS.contains(request.environment())) throw invalid("运行环境无效");
    if (!APPLICATION_STATES.contains(request.status())) throw invalid("应用状态无效");
    if (!AUTHORIZATION_STATES.contains(request.authorizationStatus())) throw invalid("授权状态无效");
    if (!SANDBOX_STATES.contains(request.sandboxStatus())) throw invalid("沙箱状态无效");
    if (!PRODUCTION_STATES.contains(request.productionStatus())) throw invalid("生产状态无效");
    if (request.serviceScopes().isEmpty() || !SCOPES.containsAll(request.serviceScopes())) {
      throw invalid("接口能力范围无效");
    }
    if ("VERIFIED".equals(request.authorizationStatus())
        && (blank(request.contractReference()) || blank(request.authorizationEvidenceRef()))) {
      throw invalid("标记为已核验时必须填写合同与授权证据位置");
    }
    if ("PASSED".equals(request.sandboxStatus()) && blank(request.sandboxEvidenceRef())) {
      throw invalid("标记为沙箱通过时必须填写联调证据位置");
    }
    if ("APPROVED".equals(request.productionStatus()) && blank(request.productionEvidenceRef())) {
      throw invalid("标记为生产批准时必须填写验收证据位置");
    }
    if ("ACTIVE".equals(request.status())) {
      boolean approved = "VERIFIED".equals(request.authorizationStatus())
          && "PASSED".equals(request.sandboxStatus())
          && ("SANDBOX".equals(request.environment()) || "APPROVED".equals(request.productionStatus()));
      if (!approved) throw invalid("应用启用前须完成授权和沙箱验收；生产环境还须完成生产批准");
    }
  }

  private static List<Map<String, String>> capabilities() {
    return List.of(
        capability("SERVICE_CATALOG", "服务目录", "读取四项独立服务及其受理边界"),
        capability("REQUIREMENT_CREATE", "提交服务需求", "写入现有项目、任务与订单链路"),
        capability("PROJECT_READ", "查询项目状态", "仅查询本应用创建的记录"),
        capability("DIRECT_CHANNEL_CATALOG", "直编渠道目录", "读取当前可对客展示的渠道与价格字段")
    );
  }

  private static Map<String, String> capability(String code, String name, String detail) {
    return Map.of("code", code, "name", name, "detail", detail);
  }

  private static String joinScopes(List<String> scopes) {
    return String.join(",", new LinkedHashSet<>(scopes));
  }

  private static String generateKey(String environment) {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String prefix = "SANDBOX".equals(environment) ? "wp_sb_" : "wp_pr_";
    return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String sha256(String value) {
    try {
      return java.util.HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String no(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  private static String upper(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static BusinessException invalid(String message) {
    return new BusinessException("INVALID_OPEN_API_CONFIGURATION", message, HttpStatus.BAD_REQUEST);
  }

  private static BusinessException notFound(String message) {
    return new BusinessException("OPEN_API_RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
  }

  private static BusinessException conflict(String message) {
    return new BusinessException("OPEN_API_CONFIGURATION_CONFLICT", message, HttpStatus.CONFLICT);
  }
}
