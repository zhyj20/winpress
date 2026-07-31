package com.winpress.commercial.federation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FederationSnapshotIntegrity {
  private final ObjectMapper objectMapper;

  public FederationSnapshotIntegrity(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

  public String hash(JsonNode node) {
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(canonical(node));
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception error) {
      throw new IllegalStateException("无法计算联邦快照哈希", error);
    }
  }

  /** Uses a scale-insensitive numerical canonical form for customer-visible quote receipts. */
  public String quoteHash(JsonNode node) {
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(canonicalQuote(node));
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception error) {
      throw new IllegalStateException("无法计算联邦报价哈希", error);
    }
  }

  public ObjectNode object(JsonNode node) {
    JsonNode canonical = canonical(node);
    if (!(canonical instanceof ObjectNode object)) throw new IllegalArgumentException("联邦快照必须是对象");
    return object;
  }

  private JsonNode canonical(JsonNode node) {
    if (node == null || node.isNull()) return objectMapper.nullNode();
    if (node.isArray()) {
      ArrayNode result = objectMapper.createArrayNode();
      node.forEach(value -> result.add(canonical(value)));
      return result;
    }
    if (node.isObject()) {
      ObjectNode result = objectMapper.createObjectNode();
      List<String> fields = new ArrayList<>();
      node.fieldNames().forEachRemaining(fields::add);
      fields.sort(Comparator.naturalOrder());
      fields.forEach(field -> result.set(field, canonical(node.get(field))));
      return result;
    }
    return node.deepCopy();
  }

  private JsonNode canonicalQuote(JsonNode node) {
    if (node == null || node.isNull()) return objectMapper.nullNode();
    if (node.isArray()) {
      ArrayNode result = objectMapper.createArrayNode();
      node.forEach(value -> result.add(canonicalQuote(value)));
      return result;
    }
    if (node.isObject()) {
      ObjectNode result = objectMapper.createObjectNode();
      List<String> fields = new ArrayList<>();
      node.fieldNames().forEachRemaining(fields::add);
      fields.sort(Comparator.naturalOrder());
      fields.forEach(field -> result.set(field, canonicalQuote(node.get(field))));
      return result;
    }
    if (node.isNumber()) {
      BigDecimal normalized = node.decimalValue().stripTrailingZeros();
      if (normalized.signum() == 0) normalized = BigDecimal.ZERO;
      return DecimalNode.valueOf(new BigDecimal(normalized.toPlainString()));
    }
    return node.deepCopy();
  }
}
