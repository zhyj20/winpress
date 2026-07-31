package com.winpress.commercial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.dto.NiumediaDtos.DiscoveryTaxonomy;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchQuery;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchResult;
import com.winpress.commercial.exception.BusinessException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NiumediaMediaClientTest {
  private HttpServer server;
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> mediaQuery = new AtomicReference<>();
  private final AtomicReference<String> reporterQuery = new AtomicReference<>();

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/media/search", exchange -> {
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      mediaQuery.set(exchange.getRequestURI().getQuery());
      if (decoded(exchange).contains("keyword=HTTP限流")) {
        respond(exchange, 429, "5", "{\"status\":429,\"message\":\"too many requests\"}");
        return;
      }
      if (decoded(exchange).contains("keyword=额度")) {
        respond(exchange, """
            {"status":413,"message":"daily search limit reached","data":null}
            """);
        return;
      }
      respond(exchange, """
          {"status":200,"data":{"count":19,"page_total":2,"page_index":2,"page_size":10,
          "media_list":[{"id":9,"name":"产业观察","media_type":"media",
          "region":{"province":"广东","city":"广州市"},"cycle":"日更",
          "score":92.5,"news_count":1200,"fans_count":300000,
          "logo":"/assets/media-9.png"}]}}
          """);
    });
    server.createContext("/v1/reporter/search", exchange -> {
      reporterQuery.set(exchange.getRequestURI().getQuery());
      respond(exchange, """
          {"status":200,"data":{"count":1,"page_index":1,"page_size":20,
          "reporters":[{"id":88,"name":"林记者","medium_id":9,"medium_name":"产业观察",
          "province":"广东","city":"深圳市","category":"科技","score":86,
          "news_count":320,"avatar_url":"https://cdn.example.com/reporter-88.png"}]}}
          """);
    });
    server.createContext("/v1/media/types", exchange -> respond(exchange, """
        {"status":200,"data":[{"id":1,"name":"报纸"},{"id":5,"name":"网络"}]}
        """));
    server.createContext("/v1/media/mp_types", exchange -> respond(exchange, """
        {"status":200,"data":[{"id":11,"name":"网站"},{"id":12,"name":"客户端"}]}
        """));
    server.createContext("/v1/region", exchange -> respond(exchange, """
        {"status":200,"data":[{"id":44,"name":"广东","son_regions":[
        {"id":4403,"name":"深圳市"},{"id":4401,"name":"广州市"}]}]}
        """));
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void mapsCurrentMediaListShapeAndUsesDocumentedQueryNames() {
    NiumediaMediaClient client = new NiumediaMediaClient(configuredProperties("private-token"),
        new ObjectMapper());

    MediaSearchResult result = client.search(query(
        "MEDIA", "新品发布", "广东", "广州市", 5, "media", null, "11,12", 2, 10));

    String decodedQuery = URLDecoder.decode(mediaQuery.get(), StandardCharsets.UTF_8);
    assertEquals("private-token", authorization.get());
    assertTrue(decodedQuery.contains("page_index=2"));
    assertTrue(decodedQuery.contains("medium_type=5"));
    assertTrue(decodedQuery.contains("media_type=media"));
    assertTrue(decodedQuery.contains("mp_type_group=11,12"));
    assertFalse(decodedQuery.contains("channel_form"));
    assertEquals(19, result.total());
    assertEquals("产业观察", result.items().get(0).displayName());
    assertEquals("MEDIA:9", result.items().get(0).candidateKey());
    assertEquals(92.5, result.items().get(0).score());
    assertEquals("广东", result.items().get(0).province());
    assertNull(result.items().get(0).logoUrl());
  }

  @Test
  void mapsCurrentReporterShapeAndKeepsMediaAndReporterIdentity() {
    NiumediaMediaClient client = new NiumediaMediaClient(configuredProperties("test-token"),
        new ObjectMapper());

    MediaSearchResult result = client.search(query(
        "REPORTER", "科技", "广东", "深圳市", 5, null, "11", null, 1, 20));

    String decodedQuery = URLDecoder.decode(reporterQuery.get(), StandardCharsets.UTF_8);
    assertNull(authorization.get());
    assertTrue(decodedQuery.contains("mp_types=11"));
    assertEquals("REPORTER:88", result.items().get(0).candidateKey());
    assertEquals("REPORTER", result.items().get(0).candidateType());
    assertEquals("林记者", result.items().get(0).reporterName());
    assertEquals("产业观察", result.items().get(0).displayName());
    assertEquals("9", result.items().get(0).mediaId());
  }

  @Test
  void loadsTaxonomyForFriendlyFilters() {
    NiumediaMediaClient client = new NiumediaMediaClient(configuredProperties("test-token"),
        new ObjectMapper());

    DiscoveryTaxonomy taxonomy = client.taxonomy();

    assertEquals("网络", taxonomy.mediaTypes().get(1).name());
    assertEquals("网站", taxonomy.mediaForms().get(0).name());
    assertEquals("广东", taxonomy.regions().get(0).name());
    assertEquals("深圳市", taxonomy.regions().get(0).children().get(0).name());
  }

  @Test
  void translatesApplicationLevelDailyLimit() {
    NiumediaMediaClient client = new NiumediaMediaClient(configuredProperties("test-token"),
        new ObjectMapper());

    BusinessException exception = assertThrows(BusinessException.class,
        () -> client.search(query(
            "MEDIA", "额度", null, null, null, null, null, null, 1, 20)));

    assertEquals("MEDIA_DISCOVERY_LIMIT_REACHED", exception.getCode());
  }

  @Test
  void entersSharedCooldownWhenHttpRateLimitReturnsRetryAfter() {
    NiumediaMediaClient client = new NiumediaMediaClient(configuredProperties("test-token"), new ObjectMapper());

    BusinessException exception = assertThrows(BusinessException.class,
        () -> client.search(query(
            "MEDIA", "HTTP限流", null, null, null, null, null, null, 1, 20)));

    assertEquals("MEDIA_DISCOVERY_LIMIT_REACHED", exception.getCode());
    assertFalse(exception.getMessage().contains("秒"));
    assertFalse(exception.getMessage().contains("频率"));
    assertTrue(client.isRateLimited());
    assertTrue(client.retryAfterSeconds() >= 1);
    BusinessException cooldownException = assertThrows(BusinessException.class,
        () -> client.search(query(
            "MEDIA", "新品发布", null, null, null, null, null, null, 1, 20)));
    assertEquals("MEDIA_DISCOVERY_LIMIT_REACHED", cooldownException.getCode());
  }

  @Test
  void rejectsSearchWhenEndpointIsBlank() {
    WinPressProperties properties = configuredProperties("test-token");
    properties.getNiumedia().setBaseUrl("");
    NiumediaMediaClient client = new NiumediaMediaClient(properties, new ObjectMapper());

    BusinessException exception = assertThrows(BusinessException.class,
        () -> client.search(query(
            "MEDIA", null, null, null, null, null, null, null, 1, 20)));

    assertEquals("MEDIA_DISCOVERY_NOT_CONFIGURED", exception.getCode());
    assertFalse(exception.getMessage().contains("配置"));
  }

  @Test
  void reportsMediaAndReporterCapabilitiesIndependently() {
    WinPressProperties properties = configuredProperties("test-token");
    properties.getNiumedia().setReporterSearchPath("");
    NiumediaMediaClient client = new NiumediaMediaClient(properties, new ObjectMapper());

    assertTrue(client.isConfigured());
    assertTrue(client.isMediaSearchConfigured());
    assertFalse(client.isReporterSearchConfigured());
    assertTrue(client.isTaxonomyConfigured());

    MediaSearchResult mediaResult = client.search(query(
        "MEDIA", "新品发布", "广东", "广州市", 5, "media", null, "11", 1, 10));
    assertEquals("产业观察", mediaResult.items().get(0).displayName());

    BusinessException exception = assertThrows(BusinessException.class,
        () -> client.search(query(
            "REPORTER", "科技", null, null, null, null, null, null, 1, 20)));
    assertEquals("MEDIA_DISCOVERY_NOT_CONFIGURED", exception.getCode());
  }

  @Test
  void doesNotReportExternalDiscoveryAsConfiguredWithoutAnAuthorizationToken() {
    NiumediaMediaClient client = new NiumediaMediaClient(configuredProperties(""), new ObjectMapper());

    assertFalse(client.isConfigured());
    assertFalse(client.isMediaSearchConfigured());
    assertFalse(client.isReporterSearchConfigured());
    assertFalse(client.isTaxonomyConfigured());
  }

  private MediaSearchQuery query(
      String target,
      String keyword,
      String province,
      String city,
      Integer mediumType,
      String mediaType,
      String mpTypes,
      String mpTypeGroup,
      int page,
      int pageSize) {
    return new MediaSearchQuery(
        target, keyword, null, province, city, mediumType, mediaType, mpTypes,
        mpTypeGroup, null, null, null, "score", null, "MEDIA_PR", page, pageSize);
  }

  private WinPressProperties configuredProperties(String token) {
    WinPressProperties properties = new WinPressProperties();
    properties.getNiumedia().setBaseUrl(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    properties.getNiumedia().setToken(token);
    properties.getNiumedia().setSearchCacheSeconds(30);
    properties.getNiumedia().setTaxonomyCacheSeconds(300);
    properties.getNiumedia().setMinRequestIntervalMillis(0);
    properties.getNiumedia().setRateLimitCooldownSeconds(10);
    return properties;
  }

  private String decoded(HttpExchange exchange) {
    return URLDecoder.decode(exchange.getRequestURI().getQuery(), StandardCharsets.UTF_8);
  }

  private void respond(HttpExchange exchange, String json) throws IOException {
    respond(exchange, 200, null, json);
  }

  private void respond(HttpExchange exchange, int status, String retryAfter, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    if (retryAfter != null) exchange.getResponseHeaders().set("Retry-After", retryAfter);
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
