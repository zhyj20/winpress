package com.winpress.commercial.service;

import com.winpress.commercial.dto.IntegrationAdminDtos.SaveSupplierApiConnectionRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateAcceptanceGateRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateAcceptanceEvidenceRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateLegacyServiceReviewRequest;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.federation.FederationTokenService;
import com.winpress.commercial.repository.IntegrationAdminRepository;
import com.winpress.commercial.repository.WorkflowRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationAdminService {
  private static final Set<String> CONNECTION_KINDS = Set.of(
      "MEDIA_DATA", "ORDER_FULFILLMENT", "QUOTE_SYNC", "GEO_FEDERATION");
  private static final Set<String> ENVIRONMENTS = Set.of("SANDBOX", "PRODUCTION");
  private static final Set<String> AUTH_TYPES = Set.of(
      "NONE", "BEARER", "API_KEY_HEADER", "HMAC_SHA256");
  private static final Set<String> AUTHORIZATION_STATES = Set.of(
      "NOT_SUBMITTED", "PENDING", "VERIFIED", "REJECTED");
  private static final Set<String> SANDBOX_STATES = Set.of(
      "NOT_TESTED", "PENDING", "PASSED", "FAILED");
  private static final Set<String> PRODUCTION_STATES = Set.of(
      "NOT_APPROVED", "PENDING", "APPROVED", "REVOKED");
  private static final Set<String> GATE_STATES = Set.of(
      "PENDING", "IN_REVIEW", "PASSED", "BLOCKED");
  private static final Set<String> EVIDENCE_STATES = Set.of(
      "PENDING", "IN_REVIEW", "VERIFIED", "REJECTED", "NOT_APPLICABLE");
  private static final Set<String> LEGACY_REVIEW_STATES = Set.of(
      "PENDING", "IN_REVIEW", "APPROVED", "REJECTED");
  private static final Set<String> LEGACY_ACTIONS = Set.of(
      "ARCHIVE_ONLY", "MAP_TO_ONSITE_WRITING", "MAP_TO_MEDIA_PR",
      "MAP_TO_DIRECT_PUBLISHING", "MAP_TO_NEWS_CONFERENCE", "MANUAL_RECONSTRUCTION");
  private static final Pattern PROVIDER_CODE =
      Pattern.compile("[A-Z0-9][A-Z0-9._-]{1,79}");
  private static final Pattern ENVIRONMENT_KEY =
      Pattern.compile("[A-Z][A-Z0-9_]{2,159}");

  private final IntegrationAdminRepository repository;
  private final WorkflowRepository workflowRepository;
  private final NiumediaMediaService niumediaMediaService;
  private final FederationTokenService federationTokenService;

  public IntegrationAdminService(
      IntegrationAdminRepository repository,
      WorkflowRepository workflowRepository,
      NiumediaMediaService niumediaMediaService,
      FederationTokenService federationTokenService) {
    this.repository = repository;
    this.workflowRepository = workflowRepository;
    this.niumediaMediaService = niumediaMediaService;
    this.federationTokenService = federationTokenService;
  }

  public Map<String, Object> overview() {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    List<Map<String, Object>> gates = repository.acceptanceGates();
    Map<String, String> gateStates = new LinkedHashMap<>();
    for (Map<String, Object> gate : gates) {
      gateStates.put(String.valueOf(gate.get("gateCode")), String.valueOf(gate.get("status")));
    }

    List<Map<String, Object>> connections = new ArrayList<>();
    int enabledCount = 0;
    int readyCount = 0;
    for (Map<String, Object> source : repository.connections()) {
      Map<String, Object> connection = new LinkedHashMap<>(source);
      boolean credentialConfigured = credentialConfigured(connection);
      boolean acceptanceReady = acceptanceReady(connection);
      boolean configurationReady = configurationBlockers(connection).isEmpty();
      connection.put("credentialConfigured", credentialConfigured);
      connection.put("acceptanceReady", acceptanceReady);
      connection.put("configurationReady", configurationReady);
      connection.put("credentialValueExposed", false);
      connections.add(connection);
      if (Boolean.TRUE.equals(connection.get("enabled"))) enabledCount++;
      if (configurationReady) readyCount++;
    }

    List<Map<String, Object>> legacyReviews = repository.legacyServiceReviews();
    List<Map<String, Object>> evidenceItems = repository.acceptanceEvidenceItems();
    long pendingLegacy = legacyReviews.stream()
        .filter(row -> !"APPROVED".equals(row.get("reviewStatus"))
            && !"REJECTED".equals(row.get("reviewStatus")))
        .count();

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("connectionCount", connections.size());
    summary.put("enabledConnectionCount", enabledCount);
    summary.put("configurationReadyCount", readyCount);
    summary.put("pendingGateCount", gates.stream()
        .filter(gate -> !"PASSED".equals(gate.get("status"))).count());
    summary.put("requiredEvidenceItemCount", evidenceItems.stream()
        .filter(item -> Boolean.TRUE.equals(item.get("required"))).count());
    summary.put("verifiedRequiredEvidenceItemCount", evidenceItems.stream()
        .filter(item -> Boolean.TRUE.equals(item.get("required"))
            && "VERIFIED".equals(item.get("itemStatus"))).count());
    summary.put("legacyReviewCount", legacyReviews.size());
    summary.put("pendingLegacyReviewCount", pendingLegacy);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("summary", summary);
    response.put("builtInAdapters", builtInAdapters(gateStates));
    response.put("connections", connections);
    response.put("acceptanceGates", gates);
    response.put("acceptanceEvidenceItems", evidenceItems);
    response.put("legacyServiceReviews", legacyReviews);
    response.put("securityNotice",
        "页面只保存凭据环境变量名称，不保存或回显令牌。配置检查只核对本机配置和验收材料，不向供应商发起测试请求。");
    return response;
  }

  /**
   * An accepted supplier record is not enough to record an API fulfillment. Runtime secrets can
   * be withdrawn independently of the database evidence; use the same complete configuration
   * test shown to the administrator before allowing that operational claim.
   */
  public boolean isSupplierFulfillmentRuntimeReady(Long supplierId) {
    if (supplierId == null) return false;
    if (!acceptanceGatePassed("SUPPLIER_FULFILLMENT")
        || repository.pendingRequiredEvidenceCount("SUPPLIER_FULFILLMENT") > 0) {
      return false;
    }
    return repository.connections().stream().anyMatch(connection ->
        sameId(connection.get("supplierId"), supplierId)
            && "ORDER_FULFILLMENT".equals(connection.get("connectionKind"))
            && "PRODUCTION".equals(connection.get("environment"))
            && configurationBlockers(connection).isEmpty());
  }

  @Transactional
  public Map<String, Object> createConnection(SaveSupplierApiConnectionRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    validateConnection(request);
    if (!repository.supplierExists(request.supplierId())) {
      throw new BusinessException("SUPPLIER_NOT_FOUND", "所选供应商不存在", HttpStatus.NOT_FOUND);
    }
    try {
      Long connectionId = repository.createConnection(user, request);
      workflowRepository.log(user, "CREATE_SUPPLIER_API_CONNECTION", "SUPPLIER_API_CONNECTION",
          String.valueOf(connectionId), Map.of(
              "providerCode", request.providerCode().trim().toUpperCase(Locale.ROOT),
              "connectionKind", request.connectionKind(),
              "environment", request.environment(),
              "enabled", request.enabled()));
      return Map.of("id", connectionId, "status", "SAVED");
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(
          "INTEGRATION_CONFIGURATION_CONFLICT",
          "同一供应商标识、接口用途和环境只能保留一项配置",
          HttpStatus.CONFLICT);
    }
  }

  @Transactional
  public Map<String, Object> updateConnection(
      Long connectionId, SaveSupplierApiConnectionRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (repository.connection(connectionId) == null) {
      throw new BusinessException("INTEGRATION_NOT_FOUND", "接口配置不存在", HttpStatus.NOT_FOUND);
    }
    validateConnection(request);
    if (!repository.supplierExists(request.supplierId())) {
      throw new BusinessException("SUPPLIER_NOT_FOUND", "所选供应商不存在", HttpStatus.NOT_FOUND);
    }
    try {
      repository.updateConnection(user, connectionId, request);
      workflowRepository.log(user, "UPDATE_SUPPLIER_API_CONNECTION", "SUPPLIER_API_CONNECTION",
          String.valueOf(connectionId), Map.of(
              "authorizationStatus", request.authorizationStatus(),
              "sandboxStatus", request.sandboxStatus(),
              "productionStatus", request.productionStatus(),
              "enabled", request.enabled()));
      return Map.of("id", connectionId, "status", "SAVED");
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(
          "INTEGRATION_CONFIGURATION_CONFLICT",
          "接口配置与现有记录冲突，请检查供应商标识、用途和环境",
          HttpStatus.CONFLICT);
    }
  }

  @Transactional
  public Map<String, Object> checkConfiguration(Long connectionId) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    Map<String, Object> connection = repository.connection(connectionId);
    if (connection == null) {
      throw new BusinessException("INTEGRATION_NOT_FOUND", "接口配置不存在", HttpStatus.NOT_FOUND);
    }
    List<String> blockers = configurationBlockers(connection);
    String status = blockers.isEmpty() ? "READY" : "BLOCKED";
    String detail = blockers.isEmpty()
        ? "本机配置与验收材料齐备；尚未执行外部网络联调。"
        : String.join("；", blockers);
    repository.saveConfigurationCheck(user, connectionId, status, detail);
    workflowRepository.log(user, "CHECK_SUPPLIER_API_CONFIGURATION",
        "SUPPLIER_API_CONNECTION", String.valueOf(connectionId),
        Map.of("status", status, "blockerCount", blockers.size()));

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", connectionId);
    response.put("status", status);
    response.put("configurationReady", blockers.isEmpty());
    response.put("enabled", connection.get("enabled"));
    response.put("blockers", blockers);
    response.put("networkRequestPerformed", false);
    response.put("credentialValueExposed", false);
    return response;
  }

  @Transactional
  public Map<String, Object> updateAcceptanceGate(
      String gateCode, UpdateAcceptanceGateRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!repository.acceptanceGateExists(gateCode)) {
      throw new BusinessException("ACCEPTANCE_GATE_NOT_FOUND", "验收项不存在", HttpStatus.NOT_FOUND);
    }
    if (!GATE_STATES.contains(request.status())) {
      throw invalid("验收状态无效");
    }
    if ("PASSED".equals(request.status())) {
      if (blank(request.evidenceReference())) {
        throw invalid("标记为已通过时必须填写汇总验收记录位置");
      }
      long pendingItems = repository.pendingRequiredEvidenceCount(gateCode);
      if (pendingItems > 0) {
        throw invalid("仍有 " + pendingItems + " 项必备验收材料未逐项核验");
      }
      if ("EXTERNAL_MEDIA_DATA".equals(gateCode)
          && !hasRuntimeReadyProductionConnection("MEDIA_DATA")) {
        throw invalid("外部媒体数据关卡须先关联已授权、已联调并获生产批准的接口");
      }
      if ("SUPPLIER_FULFILLMENT".equals(gateCode)
          && !hasRuntimeReadyProductionConnection("ORDER_FULFILLMENT")) {
        throw invalid("供应商履约关卡须先关联具备回执、对账和服务等级记录的生产接口");
      }
      if ("LEGACY_COMBINATION_REVIEW".equals(gateCode)
          && repository.pendingLegacyReviewCount() > 0) {
        throw invalid("仍有历史组合记录未完成业务确认，不能通过该关卡");
      }
    }
    try {
      repository.updateAcceptanceGate(user, gateCode, request);
    } catch (DataIntegrityViolationException exception) {
      throw invalid("上线关卡的必备证据或前置条件尚未完成");
    }
    workflowRepository.log(user, "UPDATE_PLATFORM_ACCEPTANCE_GATE", "ACCEPTANCE_GATE",
        gateCode, Map.of("status", request.status()));
    return Map.of("gateCode", gateCode, "status", request.status());
  }

  @Transactional
  public Map<String, Object> updateAcceptanceEvidenceItem(
      Long evidenceItemId, UpdateAcceptanceEvidenceRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    Map<String, Object> item = repository.acceptanceEvidenceItem(evidenceItemId);
    if (item == null) {
      throw new BusinessException(
          "ACCEPTANCE_EVIDENCE_NOT_FOUND", "验收材料项不存在", HttpStatus.NOT_FOUND);
    }
    if (!EVIDENCE_STATES.contains(request.itemStatus())) {
      throw invalid("验收材料状态无效");
    }
    if ("VERIFIED".equals(request.itemStatus()) && blank(request.evidenceReference())) {
      throw invalid("标记为已核验时必须填写可追溯证据位置");
    }
    if (Boolean.TRUE.equals(item.get("required"))
        && "NOT_APPLICABLE".equals(request.itemStatus())) {
      throw invalid("必备验收项不能标记为不适用");
    }
    try {
      repository.updateAcceptanceEvidenceItem(user, evidenceItemId, request);
    } catch (DataIntegrityViolationException exception) {
      throw invalid("验收材料不符合必备证据约束");
    }
    workflowRepository.log(user, "UPDATE_ACCEPTANCE_EVIDENCE", "ACCEPTANCE_EVIDENCE",
        String.valueOf(evidenceItemId), Map.of(
            "gateCode", String.valueOf(item.get("gateCode")),
            "itemStatus", request.itemStatus()));
    return Map.of(
        "id", evidenceItemId,
        "gateCode", item.get("gateCode"),
        "itemStatus", request.itemStatus());
  }

  @Transactional
  public Map<String, Object> updateLegacyServiceReview(
      Long reviewId, UpdateLegacyServiceReviewRequest request) {
    AuthPrincipal user = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (!LEGACY_REVIEW_STATES.contains(request.reviewStatus())) {
      throw invalid("历史记录审核状态无效");
    }
    String action = blank(request.approvedAction()) ? null : request.approvedAction();
    if ("APPROVED".equals(request.reviewStatus())) {
      if (action == null || !LEGACY_ACTIONS.contains(action)) {
        throw invalid("批准历史记录处理方案时必须选择明确动作");
      }
      if (blank(request.evidenceReference())) {
        throw invalid("批准历史记录处理方案时必须填写业务确认凭据");
      }
    } else if (action != null) {
      throw invalid("未批准的历史记录不能登记为已确认处理动作");
    }
    if (!repository.updateLegacyServiceReview(user, reviewId, request)) {
      throw new BusinessException(
          "LEGACY_REVIEW_NOT_FOUND", "历史组合记录审核项不存在", HttpStatus.NOT_FOUND);
    }
    workflowRepository.log(user, "UPDATE_LEGACY_SERVICE_REVIEW", "LEGACY_SERVICE_REVIEW",
        String.valueOf(reviewId), Map.of("reviewStatus", request.reviewStatus()));
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", reviewId);
    response.put("reviewStatus", request.reviewStatus());
    response.put("businessRecordChanged", false);
    return response;
  }

  private List<Map<String, Object>> builtInAdapters(Map<String, String> gateStates) {
    Map<String, Object> niumediaRuntime = niumediaMediaService.adminStatus();
    boolean externalDataAccepted = "PASSED".equals(gateStates.get("EXTERNAL_MEDIA_DATA"));
    boolean niumediaConfigured = Boolean.TRUE.equals(niumediaRuntime.get("runtimeConfigured"));
    boolean niumediaGoverned = Boolean.TRUE.equals(niumediaRuntime.get("governanceReady"));
    Map<String, Object> niumedia = new LinkedHashMap<>();
    niumedia.put("code", "NIUMEDIA_DISCOVERY");
    niumedia.put("name", "牛媒媒体与记者检索");
    niumedia.put("connectionKind", "MEDIA_DATA");
    niumedia.put("runtimeConfigured", niumediaConfigured);
    niumedia.put("mediaSearchConfigured", niumediaRuntime.get("rawMediaSearchConfigured"));
    niumedia.put("reporterSearchConfigured", niumediaRuntime.get("rawReporterSearchConfigured"));
    niumedia.put("acceptanceStatus", externalDataAccepted ? "PASSED" : "PENDING");
    niumedia.put("operationalStatus",
        externalDataAccepted && niumediaConfigured && niumediaGoverned
            ? "ACCEPTED" : "UNAVAILABLE");
    niumedia.put("customerFallback", "人工补充候选名单并由项目负责人核验");

    boolean federationConfigured = federationTokenService.isConfigured();
    Map<String, Object> federation = new LinkedHashMap<>();
    federation.put("code", "GEO_FEDERATION");
    federation.put("name", "GEO 服务端联动");
    federation.put("connectionKind", "GEO_FEDERATION");
    federation.put("runtimeConfigured", federationConfigured);
    federation.put("acceptanceStatus", "PENDING");
    federation.put("operationalStatus", "UNAVAILABLE");
    federation.put("customerFallback", "通过商务咨询确认接入范围和验收计划");
    return List.of(niumedia, federation);
  }

  private void validateConnection(SaveSupplierApiConnectionRequest request) {
    String providerCode = request.providerCode().trim().toUpperCase(Locale.ROOT);
    if (!PROVIDER_CODE.matcher(providerCode).matches()) {
      throw invalid("供应商标识只能使用大写字母、数字、点、下划线或连字符");
    }
    if (!CONNECTION_KINDS.contains(request.connectionKind())) throw invalid("接口用途无效");
    if (!ENVIRONMENTS.contains(request.environment())) throw invalid("运行环境无效");
    if (!AUTH_TYPES.contains(request.authType())) throw invalid("鉴权方式无效");
    if (!AUTHORIZATION_STATES.contains(request.authorizationStatus())) {
      throw invalid("授权状态无效");
    }
    if (!SANDBOX_STATES.contains(request.sandboxStatus())) throw invalid("沙箱状态无效");
    if (!PRODUCTION_STATES.contains(request.productionStatus())) {
      throw invalid("生产验收状态无效");
    }

    validateBaseUrl(request.baseUrl(), request.environment());
    validatePath(request.mediaSearchPath(), "媒体检索路径");
    validatePath(request.reporterSearchPath(), "记者检索路径");
    validatePath(request.quotePath(), "报价路径");
    validatePath(request.orderPath(), "下单路径");
    validatePath(request.orderStatusPath(), "订单状态路径");
    validatePath(request.callbackPath(), "回调路径");
    validatePath(request.reconciliationPath(), "对账路径");

    boolean noAuth = "NONE".equals(request.authType());
    if (noAuth && !blank(request.credentialEnvKey())) {
      throw invalid("无鉴权接口不能填写凭据环境变量");
    }
    if (!noAuth) {
      if (blank(request.credentialEnvKey())
          || !ENVIRONMENT_KEY.matcher(request.credentialEnvKey().trim()).matches()) {
        throw invalid("凭据环境变量须使用大写字母、数字和下划线，且不能填写令牌本身");
      }
    }
    if ("API_KEY_HEADER".equals(request.authType()) && blank(request.authHeaderName())) {
      throw invalid("API Key 鉴权必须填写请求头名称");
    }
    if ("PRODUCTION".equals(request.environment()) && noAuth) {
      throw invalid("生产接口必须使用受控鉴权");
    }
    if ("VERIFIED".equals(request.authorizationStatus())
        && (blank(request.contractReference()) || blank(request.authorizationEvidenceRef()))) {
      throw invalid("授权已核验时必须填写合同编号和授权证据位置");
    }
    if ("PASSED".equals(request.sandboxStatus()) && blank(request.sandboxEvidenceRef())) {
      throw invalid("沙箱已通过时必须填写联调证据位置");
    }
    if ("APPROVED".equals(request.productionStatus())
        && blank(request.productionEvidenceRef())) {
      throw invalid("生产已批准时必须填写批准证据位置");
    }
    if (request.enabled()) {
      if (!"VERIFIED".equals(request.authorizationStatus())
          || !"PASSED".equals(request.sandboxStatus())
          || !"APPROVED".equals(request.productionStatus())) {
        throw invalid("接口启用前必须完成授权、沙箱和生产验收");
      }
      if (!noAuth && !environmentVariableConfigured(request.credentialEnvKey())) {
        throw invalid("接口启用前必须在运行环境配置凭据；页面不能保存令牌明文");
      }
      if ("MEDIA_DATA".equals(request.connectionKind())) {
        if (blank(request.dataScope())) {
          throw invalid("媒体数据接口启用前必须明确合同允许的数据范围");
        }
        if (blank(request.mediaSearchPath()) && blank(request.reporterSearchPath())) {
          throw invalid("媒体数据接口至少需要一个媒体或记者检索路径");
        }
      }
      if ("ORDER_FULFILLMENT".equals(request.connectionKind())) {
        if (request.supplierId() == null) {
          throw invalid("订单履约接口必须关联具体供应商");
        }
        if (blank(request.orderPath()) || blank(request.orderStatusPath())
            || blank(request.callbackPath()) || blank(request.reconciliationPath())
            || blank(request.slaReference())) {
          throw invalid("订单履约接口启用前须补齐下单、状态、回调、对账路径和服务等级凭据");
        }
      }
      if ("QUOTE_SYNC".equals(request.connectionKind()) && blank(request.quotePath())) {
        throw invalid("报价同步接口启用前必须填写报价路径");
      }
    }
  }

  private static void validateBaseUrl(String value, String environment) {
    try {
      URI uri = URI.create(value.trim());
      if (uri.getHost() == null || uri.getUserInfo() != null
          || uri.getQuery() != null || uri.getFragment() != null) {
        throw invalid("接口地址必须是无账号、查询参数和片段的基础地址");
      }
      if ("https".equalsIgnoreCase(uri.getScheme())) return;
      boolean loopback = "localhost".equalsIgnoreCase(uri.getHost())
          || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost());
      if (!("SANDBOX".equals(environment)
          && "http".equalsIgnoreCase(uri.getScheme()) && loopback)) {
        throw invalid("接口地址必须使用 HTTPS；仅本机沙箱允许 HTTP");
      }
    } catch (IllegalArgumentException exception) {
      throw invalid("接口地址格式不正确");
    }
  }

  private static void validatePath(String value, String label) {
    if (blank(value)) return;
    String path = value.trim();
    if (!path.startsWith("/") || path.contains("://") || path.contains("..")
        || path.contains("?") || path.contains("#")) {
      throw invalid(label + "必须是以 / 开头的相对路径");
    }
  }

  private List<String> configurationBlockers(Map<String, Object> connection) {
    List<String> blockers = new ArrayList<>();
    if (!acceptanceReady(connection)) {
      if (!"VERIFIED".equals(connection.get("authorizationStatus"))) {
        blockers.add("正式授权尚未核验");
      }
      if (!"PASSED".equals(connection.get("sandboxStatus"))) {
        blockers.add("沙箱联调尚未通过");
      }
      if (!"APPROVED".equals(connection.get("productionStatus"))) {
        blockers.add("生产启用尚未批准");
      }
    }
    if (!credentialConfigured(connection)) {
      blockers.add("运行环境尚未配置凭据");
    }
    if ("MEDIA_DATA".equals(connection.get("connectionKind"))) {
      if (blank((String) connection.get("dataScope"))) blockers.add("数据范围尚未登记");
      if (blank((String) connection.get("mediaSearchPath"))
          && blank((String) connection.get("reporterSearchPath"))) {
        blockers.add("媒体或记者检索路径尚未登记");
      }
    }
    if ("ORDER_FULFILLMENT".equals(connection.get("connectionKind"))) {
      if (blank((String) connection.get("orderPath"))
          || blank((String) connection.get("orderStatusPath"))
          || blank((String) connection.get("callbackPath"))
          || blank((String) connection.get("reconciliationPath"))
          || blank((String) connection.get("slaReference"))) {
        blockers.add("下单、状态、回调、对账或服务等级资料尚未补齐");
      }
    }
    if (!Boolean.TRUE.equals(connection.get("enabled"))) {
      blockers.add("接口当前保持停用");
    }
    return blockers;
  }

  private boolean hasRuntimeReadyProductionConnection(String connectionKind) {
    return repository.connections().stream().anyMatch(connection ->
        connectionKind.equals(connection.get("connectionKind"))
            && "PRODUCTION".equals(connection.get("environment"))
            && configurationBlockers(connection).isEmpty());
  }

  private boolean acceptanceGatePassed(String gateCode) {
    return repository.acceptanceGates().stream().anyMatch(gate ->
        gateCode.equals(gate.get("gateCode")) && "PASSED".equals(gate.get("status")));
  }

  private static boolean acceptanceReady(Map<String, Object> connection) {
    return "VERIFIED".equals(connection.get("authorizationStatus"))
        && "PASSED".equals(connection.get("sandboxStatus"))
        && "APPROVED".equals(connection.get("productionStatus"));
  }

  private static boolean credentialConfigured(Map<String, Object> connection) {
    if ("NONE".equals(connection.get("authType"))) return true;
    Object key = connection.get("credentialEnvKey");
    return key instanceof String value && environmentVariableConfigured(value);
  }

  private static boolean environmentVariableConfigured(String key) {
    if (blank(key)) return false;
    String value = System.getenv(key.trim());
    return value != null && !value.isBlank();
  }

  private static boolean sameId(Object value, Long expected) {
    if (!(value instanceof Number number) || expected == null) return false;
    return number.longValue() == expected.longValue();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static BusinessException invalid(String message) {
    return new BusinessException("INVALID_INTEGRATION_CONFIGURATION", message, HttpStatus.BAD_REQUEST);
  }
}
