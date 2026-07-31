package com.winpress.commercial.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.winpress.commercial.exception.BusinessException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class OpenApiRateLimiterTest {
  @Test
  void blocksRequestsBeyondApplicationLimit() {
    AtomicLong now = new AtomicLong(1_000_000L);
    OpenApiRateLimiter limiter = new OpenApiRateLimiter(now::get);

    assertDoesNotThrow(() -> limiter.check(101L, 2));
    assertDoesNotThrow(() -> limiter.check(101L, 2));

    BusinessException exception = assertThrows(
        BusinessException.class, () -> limiter.check(101L, 2));
    assertEquals("OPEN_API_RATE_LIMITED", exception.getCode());
  }

  @Test
  void usesIndependentWindowsForDifferentApplicationsAndMinutes() {
    AtomicLong now = new AtomicLong(1_000_000L);
    OpenApiRateLimiter limiter = new OpenApiRateLimiter(now::get);

    limiter.check(101L, 1);
    assertDoesNotThrow(() -> limiter.check(202L, 1));
    assertThrows(BusinessException.class, () -> limiter.check(101L, 1));

    now.addAndGet(60_000L);
    assertDoesNotThrow(() -> limiter.check(101L, 1));
    assertEquals(2, limiter.trackedApplications());
  }
}
