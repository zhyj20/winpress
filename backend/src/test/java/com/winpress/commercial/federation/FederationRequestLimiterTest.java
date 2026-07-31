package com.winpress.commercial.federation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FederationRequestLimiterTest {
  @Test
  void blocksRequestsBeyondConfiguredMinuteLimit() {
    WinPressProperties properties = new WinPressProperties();
    properties.getFederation().setMaxRequestsPerMinute(10);
    AtomicLong now = new AtomicLong(1_000_000L);
    FederationRequestLimiter limiter = new FederationRequestLimiter(properties, now::get);

    for (int index = 0; index < 10; index++) {
      assertDoesNotThrow(() -> limiter.check("127.0.0.1"));
    }

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> limiter.check("127.0.0.1"));
    assertEquals("FEDERATION_RATE_LIMITED", exception.getCode());
  }

  @Test
  void resetsCounterWhenMinuteChanges() {
    WinPressProperties properties = new WinPressProperties();
    properties.getFederation().setMaxRequestsPerMinute(10);
    AtomicLong now = new AtomicLong(1_000_000L);
    FederationRequestLimiter limiter = new FederationRequestLimiter(properties, now::get);

    for (int index = 0; index < 11; index++) {
      if (index < 10) limiter.check("127.0.0.1");
      else assertThrows(BusinessException.class, () -> limiter.check("127.0.0.1"));
    }

    now.addAndGet(60_000L);
    assertDoesNotThrow(() -> limiter.check("127.0.0.1"));
    assertEquals(1, limiter.trackedSources());
  }

  @Test
  void boundsInstanceLocalSourceTracking() {
    WinPressProperties properties = new WinPressProperties();
    AtomicLong now = new AtomicLong(1_000_000L);
    FederationRequestLimiter limiter = new FederationRequestLimiter(properties, now::get);

    for (int index = 0; index < 4_200; index++) {
      limiter.check("source-" + index);
    }

    assertEquals(4_096, limiter.trackedSources());
  }
}
