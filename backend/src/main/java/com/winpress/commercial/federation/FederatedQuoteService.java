package com.winpress.commercial.federation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winpress.commercial.exception.BusinessException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Read-only server adapter for customer-visible pricing.  It deliberately selects no supplier,
 * internal-cost, margin, or upstream-order fields and returns a signed quote receipt to GEO.
 */
@Service
public class FederatedQuoteService {
  private static final Set<String> SERVICE_TYPES = Set.of(
      "ONSITE_WRITING", "MEDIA_PR", "DIRECT_PUBLISHING", "NEWS_CONFERENCE");
  private static final Set<String> FORBIDDEN_FIELD_TOKENS = Set.of(
      "supplier", "cost", "margin", "internal_note", "upstream", "secret", "token", "api_key", "apikey");

  private final FederatedOrderRepository repository;
  private final FederationTokenService tokens;
  private final FederationSnapshotIntegrity integrity;
  private final ObjectMapper objectMapper;
  private final FederationSourceIdentity sourceIdentity;

  public FederatedQuoteService(
      FederatedOrderRepository repository,
      FederationTokenService tokens,
      FederationSnapshotIntegrity integrity,
      ObjectMapper objectMapper,
      FederationSourceIdentity sourceIdentity
  ) {
    this.repository = repository;
    this.tokens = tokens;
    this.integrity = integrity;
    this.objectMapper = objectMapper;
    this.sourceIdentity = sourceIdentity;
  }

  public Map<String, Object> quote(JsonNode body) {
    if (!tokens.isConfigured()) throw unavailable("联邦服务暂不可用");
    String assertion = required(body, "assertion");
    JsonNode claims = tokens.verifyGeoQuoteAssertion(assertion);
    ObjectNode request = requiredObject(body, "request");
    assertNoInternalFields(request);
    assertIdentity(claims, request);
    ObjectNode quote = buildQuote(request);
    String quoteHash = integrity.quoteHash(quote);
    ObjectNode receiptClaims = objectMapper.createObjectNode();
    for (String field : new String[]{
        "quote_request_id", "tenant_id", "organization_id", "brand_id", "project_id", "service_type"
    }) receiptClaims.put(field, required(request, field));
    receiptClaims.put("quote_hash", quoteHash);
    receiptClaims.put("source_instance_id", sourceIdentity.current());
    return Map.of("quote", quote, "receipt", tokens.issueGeoQuoteReceipt(receiptClaims));
  }

  /**
   * Signed, customer-safe channel catalogue for GEO's direct-publishing order wizard.  A channel
   * becomes selectable only when both the channel and its customer quote are current; no supplier
   * selection or internal price information is exposed here.
   */
  public Map<String, Object> directPublishingOffers(JsonNode body) {
    if (!tokens.isConfigured()) throw unavailable("联邦服务暂不可用");
    String assertion = required(body, "assertion");
    JsonNode claims = tokens.verifyGeoQuoteAssertion(assertion);
    ObjectNode request = requiredObject(body, "request");
    assertNoInternalFields(request);
    assertIdentity(claims, request);
    if (!"DIRECT_PUBLISHING".equals(required(request, "service_type"))) {
      throw bad("直编渠道目录仅支持 DIRECT_PUBLISHING 服务");
    }

    int limit = request.path("limit").asInt(40);
    if (limit < 1 || limit > 100) throw bad("直编渠道目录数量超出允许范围");
    ObjectNode catalog = objectMapper.createObjectNode();
    for (String field : new String[]{
        "quote_request_id", "tenant_id", "organization_id", "brand_id", "project_id", "service_type"
    }) catalog.put(field, required(request, field));
    catalog.put("catalog_type", "DIRECT_PUBLISHING_CUSTOMER_OFFERS");
    ArrayNode offers = catalog.putArray("offers");
    for (Map<String, Object> row : repository.currentDirectCustomerOffers(limit)) {
      ObjectNode offer = offers.addObject();
      offer.put("channel_id", String.valueOf(row.get("channelId")));
      offer.put("channel_name", bounded(text(row, "channelName"), 180));
      offer.put("customer_amount", decimal(row.get("customerPrice")));
      offer.put("currency", defaultText(text(row, "currency"), "CNY"));
      OffsetDateTime validUntil = time(row.get("validUntil"));
      offer.put("valid_until", validUntil.toString());
    }
    String catalogHash = integrity.quoteHash(catalog);
    ObjectNode receiptClaims = objectMapper.createObjectNode();
    for (String field : new String[]{
        "quote_request_id", "tenant_id", "organization_id", "brand_id", "project_id", "service_type"
    }) receiptClaims.put(field, required(request, field));
    receiptClaims.put("quote_hash", catalogHash);
    receiptClaims.put("source_instance_id", sourceIdentity.current());
    return Map.of("catalog", catalog, "receipt", tokens.issueGeoQuoteReceipt(receiptClaims));
  }

  private ObjectNode buildQuote(ObjectNode request) {
    String serviceType = required(request, "service_type");
    if (!SERVICE_TYPES.contains(serviceType)) throw bad("联邦报价服务类型无效");
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ObjectNode quote = objectMapper.createObjectNode();
    for (String field : new String[]{
        "quote_request_id", "tenant_id", "organization_id", "brand_id", "project_id", "service_type"
    }) quote.put(field, required(request, field));
    ObjectNode details = requiredObject(request, "service_details");
    switch (serviceType) {
      case "DIRECT_PUBLISHING" -> directQuote(quote, details, now);
      case "ONSITE_WRITING" -> onsiteQuote(quote, details, now);
      case "MEDIA_PR", "NEWS_CONFERENCE" -> manualQuote(quote, now);
      default -> throw bad("联邦报价服务类型无效");
    }
    return quote;
  }

  private void directQuote(ObjectNode quote, ObjectNode details, OffsetDateTime now) {
    JsonNode rawSelections = details.path("channel_selections");
    if (!rawSelections.isArray() || rawSelections.isEmpty() || rawSelections.size() > 30) {
      throw bad("直编报价需要一至三十个渠道");
    }
    LinkedHashSet<Long> channelIds = new LinkedHashSet<>();
    for (JsonNode selection : rawSelections) {
      long channelId = positiveId(required(selection, "channel_id"));
      if (!channelIds.add(channelId)) throw bad("直编报价渠道不能重复");
    }
    ArrayNode items = quote.putArray("items");
    BigDecimal total = BigDecimal.ZERO;
    String currency = "";
    OffsetDateTime expiresAt = now.plusMinutes(15);
    for (Long channelId : channelIds) {
      Map<String, Object> row = repository.currentDirectCustomerQuote(channelId);
      String itemCurrency = text(row, "currency");
      if (currency.isBlank()) currency = itemCurrency;
      if (!currency.equals(itemCurrency)) throw bad("所选直编渠道币种不一致，不能合并报价");
      OffsetDateTime channelExpiry = time(row.get("validUntil"));
      if (channelExpiry.isBefore(expiresAt)) expiresAt = channelExpiry;
      BigDecimal amount = decimal(row.get("customerPrice"));
      ObjectNode item = items.addObject();
      item.put("channel_id", String.valueOf(channelId));
      item.put("channel_name", bounded(text(row, "channelName"), 180));
      item.put("customer_amount", amount);
      item.put("currency", itemCurrency);
      item.put("valid_until", channelExpiry.toString());
      total = total.add(amount);
    }
    if (!expiresAt.isAfter(now)) throw conflict("直编客户报价已失效，请重新查询");
    quote.put("pricing_status", "QUOTED");
    quote.put("customer_amount", total);
    quote.put("currency", currency);
    quote.put("valid_until", expiresAt.toString());
  }

  private void onsiteQuote(ObjectNode quote, ObjectNode details, OffsetDateTime now) {
    int days = positiveBounded(details.path("service_days").asInt(0), 30, "服务天数");
    int writers = positiveBounded(details.path("writer_count").asInt(0), 10, "写手人数");
    Map<String, Object> row = repository.currentOnsiteCustomerPrice();
    BigDecimal unit = decimal(row.get("customerPrice"));
    OffsetDateTime effectiveUntil = nullableTime(row.get("effectiveUntil"));
    OffsetDateTime expiresAt = now.plusMinutes(15);
    if (effectiveUntil != null && effectiveUntil.isBefore(expiresAt)) expiresAt = effectiveUntil;
    if (!expiresAt.isAfter(now)) throw conflict("云采写客户报价已失效，请重新查询");
    quote.put("pricing_status", "QUOTED");
    quote.put("service_days", days);
    quote.put("writer_count", writers);
    quote.put("unit_customer_amount", unit);
    quote.put("customer_amount", unit.multiply(BigDecimal.valueOf(days)).multiply(BigDecimal.valueOf(writers)));
    quote.put("currency", defaultText(text(row, "currency"), "CNY"));
    quote.put("valid_until", expiresAt.toString());
  }

  private void manualQuote(ObjectNode quote, OffsetDateTime now) {
    quote.put("pricing_status", "MANUAL_QUOTE_REQUIRED");
    quote.put("currency", "CNY");
    quote.put("valid_until", now.plusMinutes(15).toString());
  }

  private void assertIdentity(JsonNode claims, ObjectNode request) {
    for (String field : new String[]{
        "quote_request_id", "tenant_id", "organization_id", "brand_id", "project_id", "service_type"
    }) {
      if (!required(claims, field).equals(required(request, field))) {
        throw unauthorized("联邦报价签名与请求身份不一致：" + field);
      }
    }
  }

  private void assertNoInternalFields(JsonNode node) {
    if (node == null || node.isNull()) return;
    if (node.isObject()) {
      node.fields().forEachRemaining(entry -> {
        String normalized = entry.getKey().toLowerCase();
        if (FORBIDDEN_FIELD_TOKENS.stream().anyMatch(normalized::contains)) {
          throw bad("联邦报价请求不接受供应商、成本、凭据或毛利字段");
        }
        assertNoInternalFields(entry.getValue());
      });
    } else if (node.isArray()) {
      node.forEach(this::assertNoInternalFields);
    }
  }

  private ObjectNode requiredObject(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (!(value instanceof ObjectNode object)) throw bad(field + " 为必填对象");
    return object;
  }
  private String required(JsonNode node, String field) {
    String value = node == null ? "" : node.path(field).asText("").trim();
    if (value.isBlank()) throw bad(field + " 为必填项");
    return value;
  }
  private long positiveId(String raw) {
    try { long value = Long.parseLong(raw); if (value <= 0) throw new NumberFormatException(); return value; }
    catch (NumberFormatException error) { throw bad("渠道编号无效"); }
  }
  private int positiveBounded(int value, int max, String label) { if (value < 1 || value > max) throw bad(label + "超出允许范围"); return value; }
  private OffsetDateTime time(Object value) { OffsetDateTime parsed = nullableTime(value); if (parsed == null) throw conflict("云发布报价有效期缺失"); return parsed; }
  private OffsetDateTime nullableTime(Object value) {
    if (value == null || String.valueOf(value).isBlank()) return null;
    if (value instanceof OffsetDateTime time) return time;
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant().atOffset(ZoneOffset.UTC);
    String text = String.valueOf(value).trim();
    try { return OffsetDateTime.parse(text); }
    catch (Exception ignored) {
      try { return OffsetDateTime.parse(text.replace(' ', 'T')); }
      catch (Exception error) { throw conflict("云发布报价有效期无效"); }
    }
  }
  private BigDecimal decimal(Object value) { try { BigDecimal amount = value instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(value)); if (amount.signum() < 0) throw new NumberFormatException(); return amount; } catch (Exception error) { throw conflict("云发布客户报价无效"); } }
  private String text(Map<String, Object> row, String field) { Object value = row.get(field); return value == null ? "" : String.valueOf(value).trim(); }
  private String bounded(String value, int max) { String safe = value == null ? "" : value.trim(); return safe.length() <= max ? safe : safe.substring(0, max); }
  private String defaultText(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
  private BusinessException bad(String message) { return new BusinessException("FEDERATION_QUOTE_INVALID", message, HttpStatus.BAD_REQUEST); }
  private BusinessException conflict(String message) { return new BusinessException("FEDERATION_QUOTE_CONFLICT", message, HttpStatus.CONFLICT); }
  private BusinessException unauthorized(String message) { return new BusinessException("FEDERATION_QUOTE_UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED); }
  private BusinessException unavailable(String message) { return new BusinessException("FEDERATION_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE); }
}
