package com.winpress.commercial.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.dto.NiumediaDtos.DiscoveryTaxonomy;
import com.winpress.commercial.dto.NiumediaDtos.LookupOption;
import com.winpress.commercial.dto.NiumediaDtos.MediaCandidate;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchQuery;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchResult;
import com.winpress.commercial.dto.NiumediaDtos.RegionOption;
import com.winpress.commercial.exception.BusinessException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class NiumediaMediaClient {
  private static final Set<String> SEARCH_TARGETS = Set.of("MEDIA", "REPORTER");

  private final WinPressProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final Map<String, CachedSearch> searchCache = new ConcurrentHashMap<>();
  private final Object upstreamRequestMonitor = new Object();
  private volatile CachedTaxonomy taxonomyCache;
  private volatile long nextUpstreamRequestAtMillis;
  private volatile long rateLimitedUntilMillis;

  @Autowired
  public NiumediaMediaClient(WinPressProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  NiumediaMediaClient(WinPressProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  public boolean isConfigured() {
    return isMediaSearchConfigured() || isReporterSearchConfigured();
  }

  public boolean isMediaSearchConfigured() {
    WinPressProperties.Niumedia settings = properties.getNiumedia();
    return present(settings.getBaseUrl())
        && present(settings.getToken())
        && present(settings.getMediaSearchPath());
  }

  public boolean isReporterSearchConfigured() {
    WinPressProperties.Niumedia settings = properties.getNiumedia();
    return present(settings.getBaseUrl())
        && present(settings.getToken())
        && present(settings.getReporterSearchPath());
  }

  public boolean isTaxonomyConfigured() {
    WinPressProperties.Niumedia settings = properties.getNiumedia();
    return present(settings.getBaseUrl())
        && present(settings.getToken())
        && present(settings.getRegionPath())
        && present(settings.getMediaTypesPath())
        && present(settings.getMediaFormsPath());
  }

  /**
   * Exposes only connection health for the signed-in product UI. The provider identity, quota
   * and credential details remain server-side.
   */
  public boolean isRateLimited() {
    return rateLimitedUntilMillis > System.currentTimeMillis();
  }

  public long retryAfterSeconds() {
    long remainingMillis = rateLimitedUntilMillis - System.currentTimeMillis();
    return remainingMillis <= 0 ? 0 : Math.max(1, (remainingMillis + 999) / 1000);
  }

  public int minRequestIntervalMillis() {
    return Math.max(0, Math.min(5_000, properties.getNiumedia().getMinRequestIntervalMillis()));
  }

  public MediaSearchResult search(MediaSearchQuery query) {
    String target = normalizeTarget(query.target());
    boolean targetConfigured = "REPORTER".equals(target)
        ? isReporterSearchConfigured()
        : isMediaSearchConfigured();
    if (!targetConfigured) {
      throw unavailable("MEDIA_DISCOVERY_NOT_CONFIGURED", "媒体资料检索暂不可用，请稍后再试。");
    }
    String cacheKey = target + "|" + query;
    CachedSearch cached = searchCache.get(cacheKey);
    long now = System.currentTimeMillis();
    if (cached != null && cached.expiresAt() > now) return cached.value();

    try {
      String body = get(buildSearchUri(query, target));
      MediaSearchResult result = normalizeSearch(body, query, target);
      int ttl = Math.max(30, properties.getNiumedia().getSearchCacheSeconds());
      if (searchCache.size() > 500) searchCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
      searchCache.put(cacheKey, new CachedSearch(result, now + ttl * 1000L));
      return result;
    } catch (BusinessException exception) {
      if (cached != null) {
        MediaSearchResult value = cached.value();
        return new MediaSearchResult(
            value.items(), value.total(), value.page(), value.pageSize(), value.updatedAt(), true,
            "实时检索暂不可用，当前显示最近一次成功结果。");
      }
      throw exception;
    }
  }

  public DiscoveryTaxonomy taxonomy() {
    if (!isTaxonomyConfigured()) {
      throw unavailable("MEDIA_DISCOVERY_NOT_CONFIGURED", "媒体资料检索暂不可用，请稍后再试。");
    }
    long now = System.currentTimeMillis();
    CachedTaxonomy cached = taxonomyCache;
    if (cached != null && cached.expiresAt() > now) return cached.value();

    try {
      List<LookupOption> mediaTypes = normalizeLookup(get(buildUri(
          properties.getNiumedia().getMediaTypesPath(), Map.of())));
      List<LookupOption> mediaForms = normalizeLookup(get(buildUri(
          properties.getNiumedia().getMediaFormsPath(), Map.of())));
      List<RegionOption> regions = normalizeRegions(get(buildUri(
          properties.getNiumedia().getRegionPath(), Map.of("type", "2"))));
      DiscoveryTaxonomy value = new DiscoveryTaxonomy(
          mediaTypes, mediaForms, regions, OffsetDateTime.now().toString());
      int ttl = Math.max(300, properties.getNiumedia().getTaxonomyCacheSeconds());
      taxonomyCache = new CachedTaxonomy(value, now + ttl * 1000L);
      return value;
    } catch (BusinessException exception) {
      if (cached != null) return cached.value();
      throw exception;
    } catch (Exception exception) {
      if (cached != null) return cached.value();
      throw unavailable("MEDIA_DISCOVERY_RESPONSE_INVALID", "媒体资料库返回异常，请稍后重试。");
    }
  }

  private URI buildSearchUri(MediaSearchQuery query, String target) {
    Map<String, String> params = new LinkedHashMap<>();
    put(params, "keyword", query.keyword());
    put(params, "name", query.name());
    put(params, "province", query.province());
    put(params, "city", query.city());
    if (query.mediumType() != null) params.put("medium_type", String.valueOf(query.mediumType()));
    put(params, "platform", query.platform());
    put(params, "sort", query.sort());
    put(params, "field", query.field());
    if ("REPORTER".equals(target)) {
      put(params, "mp_types", query.mpTypes());
      if (query.mediaId() != null) params.put("media_id", String.valueOf(query.mediaId()));
      if (query.reporterType() != null) params.put("reporter_type", String.valueOf(query.reporterType()));
    } else {
      put(params, "media_type", query.mediaType());
      put(params, "mp_type_group", query.mpTypeGroup());
    }
    params.put("page_index", String.valueOf(query.page()));
    params.put("page_size", String.valueOf(query.pageSize()));
    String path = "REPORTER".equals(target)
        ? properties.getNiumedia().getReporterSearchPath()
        : properties.getNiumedia().getMediaSearchPath();
    return buildUri(path, params);
  }

  private URI buildUri(String path, Map<String, String> params) {
    String base = properties.getNiumedia().getBaseUrl().trim().replaceAll("/+$", "");
    String cleanPath = path == null ? "" : path.trim();
    String endpoint = base + (cleanPath.startsWith("/") ? cleanPath : "/" + cleanPath);
    URI parsed = URI.create(endpoint);
    if (!"https".equalsIgnoreCase(parsed.getScheme()) && !"http".equalsIgnoreCase(parsed.getScheme())) {
      throw new IllegalArgumentException("unsupported scheme");
    }
    StringBuilder uri = new StringBuilder(endpoint);
    if (!params.isEmpty()) {
      uri.append(endpoint.contains("?") ? '&' : '?');
      boolean first = true;
      for (Map.Entry<String, String> entry : params.entrySet()) {
        if (!present(entry.getValue())) continue;
        if (!first) uri.append('&');
        first = false;
        uri.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
        uri.append('=');
        uri.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
      }
    }
    return URI.create(uri.toString());
  }

  private String get(URI uri) {
    reserveUpstreamRequest();
    try {
      HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
          .timeout(Duration.ofSeconds(Math.max(
              3, Math.min(30, properties.getNiumedia().getRequestTimeoutSeconds()))))
          .header("Accept", "application/json");
      if (present(properties.getNiumedia().getToken())) {
        builder.header("Authorization", properties.getNiumedia().getToken().trim());
      }
      HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 401 || response.statusCode() == 403) {
        throw unavailable("MEDIA_DISCOVERY_AUTH_FAILED", "媒体资料检索暂不可用，请稍后再试。");
      }
      if (response.statusCode() == 429) {
        registerRateLimit(retryAfterSeconds(response));
        throw limited();
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw unavailable("MEDIA_DISCOVERY_UPSTREAM_UNAVAILABLE", "媒体资料库暂时不可用，请稍后重试。");
      }
      return response.body();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw unavailable("MEDIA_DISCOVERY_UPSTREAM_UNAVAILABLE", "媒体资料库暂时不可用，请稍后重试。");
    } catch (IOException | IllegalArgumentException exception) {
      throw unavailable("MEDIA_DISCOVERY_UPSTREAM_UNAVAILABLE", "媒体资料库暂时不可用，请稍后重试。");
    }
  }

  private MediaSearchResult normalizeSearch(String body, MediaSearchQuery query, String target) {
    try {
      JsonNode data = successfulData(body);
      JsonNode rawItems = firstArray(
          data,
          "REPORTER".equals(target)
              ? new String[] {"reporters", "results", "items"}
              : new String[] {"media_list", "results", "items"});
      if (!rawItems.isArray() && data.isArray()) rawItems = data;
      if (!rawItems.isArray()) throw new IllegalArgumentException("search items missing");

      List<MediaCandidate> items = new ArrayList<>();
      for (JsonNode item : rawItems) {
        MediaCandidate candidate = "REPORTER".equals(target)
            ? reporterCandidate(item)
            : mediaCandidate(item);
        if (candidate != null) items.add(candidate);
      }
      long total = longValue(
          data, "count",
          longValue(data, "total", longValue(data, "page_total", items.size())));
      int page = intValue(data, "page_index", intValue(data, "page", query.page()));
      int pageSize = intValue(
          data, "page_size", intValue(data, "pageSize", query.pageSize()));
      String updatedAt = firstText(data, "last_updated_at", "lastUpdatedAt", "updated_at", "updatedAt");
      if (!present(updatedAt)) updatedAt = OffsetDateTime.now().toString();
      return new MediaSearchResult(items, total, page, pageSize, updatedAt, false, null);
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw unavailable("MEDIA_DISCOVERY_RESPONSE_INVALID", "媒体资料库返回异常，请稍后重试。");
    }
  }

  private MediaCandidate mediaCandidate(JsonNode item) {
    String id = firstText(item, "media_id", "mediaId", "id");
    String name = firstText(item, "display_name", "displayName", "media_name", "mediaName", "name");
    if (!present(id) || !present(name)) return null;
    Region region = region(item);
    String mediaType = firstText(item, "attribute", "media_attribute", "medium_type_name", "media_type");
    String channelForm = firstText(item, "channel_form", "channelForm", "publish_form", "mp_type_name");
    String category = firstText(item, "category", "industry_category");
    List<String> tags = tags(item, "coverage_tags", "coverageTags", "applicable_scene", "applicableScene");
    addTag(tags, firstText(item, "cycle", "publishing_cycle"));
    addTag(tags, category);
    return new MediaCandidate(
        "MEDIA:" + id, "MEDIA", id, name, null, null,
        emptyToNull(mediaTypeLabel(mediaType)), region.province(), region.city(),
        emptyToNull(channelForm), emptyToNull(category), tags,
        null,
        available(item), doubleValue(item, "score", "fit_score", "fitScore"),
        nullableLong(item, "news_count", "newsCount"),
        nullableLong(item, "fans_count", "fansCount"),
        null,
        null,
        emptyToNull(firstText(item, "score_date", "last_updated_at", "updated_at", "updatedAt")));
  }

  private MediaCandidate reporterCandidate(JsonNode item) {
    String reporterId = firstText(item, "reporter_id", "reporterId", "id");
    String reporterName = firstText(item, "reporter_name", "reporterName", "name");
    if (!present(reporterId) || !present(reporterName)) return null;
    String mediaId = firstText(item, "medium_id", "media_id", "mediaId");
    if (!present(mediaId)) mediaId = "REPORTER-MEDIA-" + reporterId;
    String mediaName = firstText(item, "medium_name", "media_name", "mediaName");
    if (!present(mediaName)) mediaName = "媒体机构待确认";
    Region region = region(item);
    String beat = firstText(item, "beat", "line", "category", "industry");
    List<String> tags = tags(item, "coverage_tags", "coverageTags", "tags");
    addTag(tags, beat);
    addTag(tags, firstText(item, "platform"));
    return new MediaCandidate(
        "REPORTER:" + reporterId, "REPORTER", mediaId, mediaName,
        reporterId, reporterName,
        emptyToNull(firstText(item, "medium_type_name", "media_attribute", "reporter_type_name")),
        region.province(), region.city(), null, emptyToNull(beat), tags,
        null, available(item),
        doubleValue(item, "score", "fit_score", "fitScore"),
        nullableLong(item, "news_count", "newsCount"), null, null,
        null,
        emptyToNull(firstText(item, "max_published_at", "updated_at", "updatedAt")));
  }

  private List<LookupOption> normalizeLookup(String body) throws Exception {
    JsonNode data = successfulData(body);
    if (!data.isArray()) throw new IllegalArgumentException("lookup array missing");
    List<LookupOption> values = new ArrayList<>();
    for (JsonNode item : data) {
      Integer id = nullableInt(item, "id");
      String name = firstText(item, "name");
      if (id != null && present(name)) values.add(new LookupOption(id, name));
    }
    return values;
  }

  private List<RegionOption> normalizeRegions(String body) throws Exception {
    JsonNode data = successfulData(body);
    if (!data.isArray()) throw new IllegalArgumentException("region array missing");
    List<RegionOption> values = new ArrayList<>();
    for (JsonNode item : data) {
      RegionOption region = regionOption(item);
      if (region != null) values.add(region);
    }
    return values;
  }

  private RegionOption regionOption(JsonNode item) {
    String name = firstText(item, "name");
    if (!present(name)) return null;
    List<RegionOption> children = new ArrayList<>();
    JsonNode rawChildren = firstArray(item, "son_regions", "children");
    if (rawChildren.isArray()) {
      for (JsonNode child : rawChildren) {
        RegionOption value = regionOption(child);
        if (value != null) children.add(value);
      }
    }
    return new RegionOption(firstText(item, "code", "id"), name, children);
  }

  private JsonNode successfulData(String body) throws Exception {
    JsonNode root = objectMapper.readTree(body);
    if (root == null || root.isNull()) throw new IllegalArgumentException("empty response");
    if (root.has("status")) {
      int status = root.path("status").asInt(-1);
      if (status == 413 || status == 429) {
        registerRateLimit(null);
        throw limited();
      }
      if (status != 200) {
        throw unavailable("MEDIA_DISCOVERY_UPSTREAM_REJECTED", "媒体资料库暂未返回可用结果，请稍后重试。");
      }
    }
    return root.hasNonNull("data") ? root.path("data") : root;
  }

  private JsonNode firstArray(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (value.isArray()) return value;
    }
    return objectMapper.createArrayNode();
  }

  private Region region(JsonNode node) {
    String province = firstText(node, "province");
    String city = firstText(node, "city");
    JsonNode rawRegion = node.path("region");
    if ((!present(province) || !present(city)) && rawRegion.isObject()) {
      if (!present(province)) province = firstText(rawRegion, "province", "province_name", "name");
      if (!present(city)) city = firstText(rawRegion, "city", "city_name");
    }
    if (!present(province) && rawRegion.isTextual()) {
      String[] parts = rawRegion.asText("").trim().split("[/·,，\\s-]+", 3);
      if (parts.length > 0) province = parts[0];
      if (parts.length > 1 && !present(city)) city = parts[1];
    }
    return new Region(emptyToNull(province), emptyToNull(city));
  }

  private List<String> tags(JsonNode node, String... keys) {
    LinkedHashSet<String> values = new LinkedHashSet<>();
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (value.isArray()) {
        for (JsonNode child : value) {
          String text = child.asText("").trim();
          if (present(text)) values.add(text);
        }
      } else if (value.isTextual()) {
        for (String text : value.asText("").split("[,，、|]")) {
          if (present(text)) values.add(text.trim());
        }
      }
    }
    return new ArrayList<>(values);
  }

  private void addTag(List<String> tags, String value) {
    if (present(value) && !tags.contains(value.trim())) tags.add(value.trim());
  }

  private boolean available(JsonNode node) {
    JsonNode value = node.has("available") ? node.path("available") : node.path("available_status");
    if (value.isMissingNode() || value.isNull()) return true;
    if (value.isBoolean()) return value.asBoolean();
    String status = value.asText("").trim().toLowerCase(Locale.ROOT);
    return !Set.of("false", "0", "inactive", "unavailable", "disabled").contains(status);
  }

  private String mediaTypeLabel(String value) {
    if (!present(value)) return null;
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "media" -> "媒体机构";
      case "self_media" -> "自媒体";
      default -> value.trim();
    };
  }

  private String normalizeTarget(String value) {
    String target = value == null ? "MEDIA" : value.trim().toUpperCase(Locale.ROOT);
    if (!SEARCH_TARGETS.contains(target)) {
      throw new BusinessException("INVALID_MEDIA_DISCOVERY_TARGET", "媒体检索类型不正确", HttpStatus.BAD_REQUEST);
    }
    return target;
  }

  private void put(Map<String, String> params, String key, String value) {
    if (present(value)) params.put(key, value.trim());
  }

  private String firstText(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (value.isValueNode() && !value.isNull()) {
        String text = value.asText("").trim();
        if (present(text)) return text;
      }
    }
    return "";
  }

  private Double doubleValue(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (value.isNumber()) return value.asDouble();
      if (value.isTextual()) {
        try {
          return Double.valueOf(value.asText().trim());
        } catch (NumberFormatException ignored) {
          // Continue to the next alias.
        }
      }
    }
    return null;
  }

  private Long nullableLong(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (value.canConvertToLong()) return value.asLong();
    }
    return null;
  }

  private Integer nullableInt(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (value.canConvertToInt()) return value.asInt();
    }
    return null;
  }

  private long longValue(JsonNode node, String key, long fallback) {
    JsonNode value = node.path(key);
    return value.canConvertToLong() ? value.asLong() : fallback;
  }

  private int intValue(JsonNode node, String key, int fallback) {
    JsonNode value = node.path(key);
    return value.canConvertToInt() ? value.asInt() : fallback;
  }

  private boolean present(String value) { return value != null && !value.isBlank(); }
  private String emptyToNull(String value) { return present(value) ? value.trim() : null; }

  private void reserveUpstreamRequest() {
    synchronized (upstreamRequestMonitor) {
      long now = System.currentTimeMillis();
      if (rateLimitedUntilMillis > now) throw limited();
      long waitMillis = Math.max(0, nextUpstreamRequestAtMillis - now);
      if (waitMillis > 0) {
        try {
          Thread.sleep(waitMillis);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw unavailable("MEDIA_DISCOVERY_UPSTREAM_UNAVAILABLE", "媒体资料库暂时不可用，请稍后重试。");
        }
      }
      now = System.currentTimeMillis();
      if (rateLimitedUntilMillis > now) throw limited();
      nextUpstreamRequestAtMillis = now + minRequestIntervalMillis();
    }
  }

  private Long retryAfterSeconds(HttpResponse<?> response) {
    return response.headers().firstValue("Retry-After")
        .flatMap(value -> {
          try {
            long seconds = Long.parseLong(value.trim());
            return seconds > 0 ? java.util.Optional.of(seconds) : java.util.Optional.empty();
          } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
          }
        })
        .orElse(null);
  }

  private void registerRateLimit(Long retryAfter) {
    long configured = Math.max(5, Math.min(3_600, properties.getNiumedia().getRateLimitCooldownSeconds()));
    long seconds = retryAfter == null ? configured : Math.max(1, Math.min(3_600, retryAfter));
    long until = System.currentTimeMillis() + seconds * 1_000L;
    synchronized (upstreamRequestMonitor) {
      rateLimitedUntilMillis = Math.max(rateLimitedUntilMillis, until);
      nextUpstreamRequestAtMillis = Math.max(nextUpstreamRequestAtMillis, rateLimitedUntilMillis);
    }
  }

  private BusinessException limited() {
    return new BusinessException(
        "MEDIA_DISCOVERY_LIMIT_REACHED",
        "媒体资料暂时不可用，请稍后再试；也可先人工补充拟邀对象。",
        HttpStatus.TOO_MANY_REQUESTS);
  }

  private BusinessException unavailable(String code, String message) {
    return new BusinessException(code, message, HttpStatus.SERVICE_UNAVAILABLE);
  }

  private record CachedSearch(MediaSearchResult value, long expiresAt) {}
  private record CachedTaxonomy(DiscoveryTaxonomy value, long expiresAt) {}
  private record Region(String province, String city) {}
}
