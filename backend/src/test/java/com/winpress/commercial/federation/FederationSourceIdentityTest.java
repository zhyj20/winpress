package com.winpress.commercial.federation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FederationSourceIdentityTest {

  @Test
  void usesDefaultWhenConfigurationIsBlank() {
    WinPressProperties properties = new WinPressProperties();
    properties.getFederation().setSourceInstanceId(" ");

    assertEquals("default", new FederationSourceIdentity(properties).current());
  }

  @Test
  void normalizesConfiguredAndStoredIdentifiers() {
    WinPressProperties properties = new WinPressProperties();
    properties.getFederation().setSourceInstanceId(" Edge-Shenzhen:01 ");
    FederationSourceIdentity identity = new FederationSourceIdentity(properties);

    assertEquals("edge-shenzhen:01", identity.current());
    assertEquals("edge-history:02", identity.requireStored(" Edge-History:02 "));
  }

  @Test
  void rejectsInvalidConfigurationAsUnavailable() {
    WinPressProperties properties = new WinPressProperties();
    properties.getFederation().setSourceInstanceId("invalid instance");
    FederationSourceIdentity identity = new FederationSourceIdentity(properties);

    BusinessException error = assertThrows(BusinessException.class, identity::current);

    assertEquals("FEDERATION_CONFIGURATION_INVALID", error.getCode());
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
  }
}
