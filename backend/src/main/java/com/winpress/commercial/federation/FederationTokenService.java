package com.winpress.commercial.federation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** HMAC assertions for server-to-server GEO federation; never exposed to the Vue client. */
@Service
public class FederationTokenService {
  private static final String ALGORITHM = "HmacSHA256";
  private static final long MAX_LIFETIME_SECONDS = 120;

  private final ObjectMapper objectMapper;
  private final WinPressProperties properties;

  public FederationTokenService(ObjectMapper objectMapper, WinPressProperties properties) {
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  public JsonNode verifyGeoOrderAssertion(String token) {
    return verify(token, platformIssuer(), "winpress-commercial-federated-orders", "geo_to_winpress_order");
  }

  public JsonNode verifyGeoQuoteAssertion(String token) {
    return verify(token, platformIssuer(), "winpress-commercial-federated-quotes", "geo_to_winpress_quote");
  }

  public String issueGeoOrderReceipt(ObjectNode claims) {
    return issue(claims, winpressIssuer(), "niumedia-platform-federated-orders", "winpress_to_geo_order_receipt");
  }

  public String issueGeoOrderEvent(ObjectNode claims) {
    return issue(claims, winpressIssuer(), "niumedia-platform-federated-orders", "winpress_to_geo_order_event");
  }

  public String issueGeoQuoteReceipt(ObjectNode claims) {
    return issue(claims, winpressIssuer(), "niumedia-platform-federated-quotes", "winpress_to_geo_quote_receipt");
  }

  private String issue(ObjectNode claims, String issuer, String audience, String direction) {
    requireConfigured();
    long issuedAt = Instant.now().getEpochSecond();
    long expiresAt = issuedAt + 90;
    ObjectNode payload = claims == null ? objectMapper.createObjectNode() : claims.deepCopy();
    payload.put("iss", issuer);
    payload.put("aud", audience);
    payload.put("direction", direction);
    payload.put("iat", issuedAt);
    payload.put("exp", expiresAt);
    payload.put("jti", UUID.randomUUID().toString());
    ObjectNode header = objectMapper.createObjectNode();
    header.put("alg", "HS256");
    header.put("typ", "JWT");
    header.put("kid", "geo-winpress-federated-order-v1");
    try {
      String unsigned = encode(objectMapper.writeValueAsBytes(header)) + "." + encode(objectMapper.writeValueAsBytes(payload));
      return unsigned + "." + encode(hmac(unsigned));
    } catch (Exception error) {
      throw unavailable("无法生成平台联邦签名");
    }
  }

  private JsonNode verify(String token, String issuer, String audience, String direction) {
    requireConfigured();
    try {
      if (token == null || token.isBlank() || token.length() > 8192) throw invalid();
      String[] parts = token.split("\\.", -1);
      if (parts.length != 3) throw invalid();
      String unsigned = parts[0] + "." + parts[1];
      byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
      if (!MessageDigest.isEqual(hmac(unsigned), signature)) throw invalid();
      JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
      JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
      if (!"HS256".equals(text(header, "alg")) || !"JWT".equals(text(header, "typ"))) throw invalid();
      if (!issuer.equals(text(payload, "iss")) || !audience.equals(text(payload, "aud"))) throw invalid();
      if (!direction.equals(text(payload, "direction")) || text(payload, "jti").isBlank()) throw invalid();
      long now = Instant.now().getEpochSecond();
      long issuedAt = payload.path("iat").asLong(0);
      long expiresAt = payload.path("exp").asLong(0);
      if (issuedAt <= 0 || expiresAt <= now || issuedAt > now + 30) throw invalid();
      if (expiresAt <= issuedAt || expiresAt - issuedAt > MAX_LIFETIME_SECONDS) throw invalid();
      return payload;
    } catch (BusinessException error) {
      throw error;
    } catch (Exception error) {
      throw invalid();
    }
  }

  public boolean isConfigured() {
    String secret = properties.getFederation().getSharedSecret();
    return properties.getFederation().isEnabled() && secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= 32;
  }

  private void requireConfigured() {
    if (!isConfigured()) throw unavailable("联邦服务暂不可用");
  }

  private byte[] hmac(String input) throws Exception {
    Mac mac = Mac.getInstance(ALGORITHM);
    mac.init(new SecretKeySpec(properties.getFederation().getSharedSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
    return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
  }

  private String platformIssuer() {
    String value = properties.getFederation().getPlatformIssuer();
    return value == null || value.isBlank() ? "niumedia-platform" : value.trim();
  }

  private String winpressIssuer() {
    String value = properties.getFederation().getWinpressIssuer();
    return value == null || value.isBlank() ? "winpress-commercial" : value.trim();
  }

  private String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
  private String text(JsonNode node, String field) { JsonNode value = node == null ? null : node.get(field); return value != null && value.isTextual() ? value.asText() : ""; }
  private BusinessException invalid() { return new BusinessException("FEDERATION_ASSERTION_INVALID", "联邦签名无效", HttpStatus.UNAUTHORIZED); }
  private BusinessException unavailable(String message) { return new BusinessException("FEDERATION_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE); }
}
