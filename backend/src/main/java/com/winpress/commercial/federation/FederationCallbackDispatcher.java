package com.winpress.commercial.federation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winpress.commercial.config.WinPressProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Delivers signed, retryable WinPress fulfillment events to GEO without a browser hop. */
@Component
public class FederationCallbackDispatcher {
  private final FederatedOrderRepository repository;
  private final FederationTokenService tokens;
  private final WinPressProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient client;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final String workerId = "winpress-federation-" + UUID.randomUUID();

  public FederationCallbackDispatcher(
      FederatedOrderRepository repository,
      FederationTokenService tokens,
      WinPressProperties properties,
      ObjectMapper objectMapper,
      RestClient.Builder builder
  ) {
    this.repository = repository;
    this.tokens = tokens;
    this.properties = properties;
    this.objectMapper = objectMapper;
    int seconds = Math.max(5, Math.min(properties.getFederation().getCallbackTimeoutSeconds(), 60));
    var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
        java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(seconds)).build());
    factory.setReadTimeout(Duration.ofSeconds(seconds));
    this.client = builder.requestFactory(factory).build();
  }

  @Scheduled(initialDelayString = "${winpress.federation.callback-initial-delay-ms:30000}", fixedDelayString = "${winpress.federation.callback-fixed-delay-ms:30000}")
  public void dispatch() {
    if (!ready() || !running.compareAndSet(false, true)) return;
    try {
      List<Map<String, Object>> events = repository.leaseOutbox(workerId, 20, 120);
      for (Map<String, Object> event : events) dispatchOne(event);
    } finally {
      running.set(false);
    }
  }

  private void dispatchOne(Map<String, Object> event) {
    long id = number(event.get("id"));
    try {
      ObjectNode claims = payload(event.get("payload"));
      String assertion = tokens.issueGeoOrderEvent(claims);
      JsonNode response = client.post()
          .uri(validCallbackUri())
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("assertion", assertion))
          .retrieve()
          .body(JsonNode.class);
      if (response == null || !response.path("accepted").asBoolean(false)) {
        throw new IllegalStateException("GEO callback did not acknowledge the federated event");
      }
      if (!repository.markOutboxPublished(id, workerId)) {
        throw new IllegalStateException("federated callback lease expired before acknowledgement");
      }
      repository.recordCallbackDelivery(String.valueOf(event.get("eventId")), "", 200);
    } catch (Exception error) {
      int attempt = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, number(event.get("attemptCount"))));
      int retrySeconds = Math.min(300, 5 * (1 << Math.min(Math.max(0, attempt - 1), 6)));
      repository.failOutbox(
          id,
          workerId,
          retrySeconds,
          "GEO_CALLBACK_FAILED",
          "GEO 回调未完成，等待重试。"
      );
    }
  }

  private boolean ready() {
    return tokens.isConfigured() && !blank(properties.getFederation().getGeoCallbackUrl());
  }

  URI validCallbackUri() {
    String raw = properties.getFederation().getGeoCallbackUrl().trim();
    URI uri = URI.create(raw);
    boolean https = "https".equalsIgnoreCase(uri.getScheme());
    boolean localHttp = "http".equalsIgnoreCase(uri.getScheme()) && isLoopbackHost(uri.getHost());
    if ((!https && !localHttp) || uri.getHost() == null || uri.getUserInfo() != null) {
      throw new IllegalStateException("GEO callback URL is invalid");
    }
    return uri;
  }

  private boolean isLoopbackHost(String host) {
    if (host == null) return false;
    String normalized = host.trim().toLowerCase();
    return "localhost".equals(normalized)
        || "127.0.0.1".equals(normalized)
        || "::1".equals(normalized)
        || "0:0:0:0:0:0:0:1".equals(normalized);
  }

  private ObjectNode payload(Object raw) {
    try {
      JsonNode node = raw instanceof JsonNode json ? json : objectMapper.readTree(String.valueOf(raw));
      if (!(node instanceof ObjectNode object)) throw new IllegalArgumentException("federated callback payload is invalid");
      return object;
    } catch (Exception error) {
      throw new IllegalArgumentException("federated callback payload is invalid", error);
    }
  }

  private long number(Object value) { if (value instanceof Number number) return number.longValue(); return Long.parseLong(String.valueOf(value)); }
  private boolean blank(String value) { return value == null || value.isBlank(); }
}
