package com.winpress.commercial.federation;

import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Owns the WinPress instance identifier used by every GEO federation receipt and callback.
 *
 * <p>The identifier is operational provenance, not a client-supplied field. New receipts use the
 * configured value; later callbacks must keep the value stored with the original receipt even if
 * deployment configuration changes.
 */
@Component
public class FederationSourceIdentity {
  private static final String DEFAULT_ID = "default";
  private static final String PATTERN = "[a-z0-9][a-z0-9._:-]{0,127}";

  private final WinPressProperties properties;

  public FederationSourceIdentity(WinPressProperties properties) {
    this.properties = properties;
  }

  public String current() {
    String value = properties.getFederation().getSourceInstanceId();
    return normalize(value == null || value.isBlank() ? DEFAULT_ID : value);
  }

  public String requireStored(Object value) {
    if (value == null || String.valueOf(value).isBlank()) {
      throw invalidConfiguration();
    }
    return normalize(String.valueOf(value));
  }

  private String normalize(String value) {
    String normalized = value.trim().toLowerCase();
    if (!normalized.matches(PATTERN)) throw invalidConfiguration();
    return normalized;
  }

  private BusinessException invalidConfiguration() {
    return new BusinessException(
        "FEDERATION_CONFIGURATION_INVALID",
        "联邦服务配置无效",
        HttpStatus.SERVICE_UNAVAILABLE
    );
  }
}
