package com.winpress.commercial.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LoginAttemptLimiterTest {
  @Test
  void blocksRepeatedFailuresThenResetsAfterCooldown() {
    WinPressProperties properties = new WinPressProperties();
    properties.getLogin().setMaxFailures(3);
    properties.getLogin().setFailureWindowSeconds(60);
    properties.getLogin().setCooldownSeconds(30);
    AtomicLong now = new AtomicLong(1_000_000L);
    LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties, now::get);

    limiter.recordFailure("127.0.0.1");
    limiter.recordFailure("127.0.0.1");
    limiter.recordFailure("127.0.0.1");

    BusinessException exception = assertThrows(BusinessException.class,
        () -> limiter.check("127.0.0.1"));
    assertEquals("LOGIN_RATE_LIMITED", exception.getCode());

    now.addAndGet(30_001L);
    assertDoesNotThrow(() -> limiter.check("127.0.0.1"));
    limiter.recordFailure("127.0.0.1");
    assertDoesNotThrow(() -> limiter.check("127.0.0.1"));
  }

  @Test
  void successfulLoginClearsOnlyItsSourceWindow() {
    WinPressProperties properties = new WinPressProperties();
    AtomicLong now = new AtomicLong(1_000_000L);
    LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties, now::get);

    limiter.recordFailure("127.0.0.1");
    limiter.recordSuccess("127.0.0.1");

    assertEquals(0, limiter.trackedSources());
  }

  @Test
  @SuppressWarnings("unchecked")
  void usesRedisForSharedFailuresWhenRedisIsAvailable() {
    WinPressProperties properties = new WinPressProperties();
    properties.getLogin().setFailureWindowSeconds(60);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.hasKey(anyString())).thenReturn(false);
    when(redis.opsForValue()).thenReturn(values);
    when(values.increment(anyString())).thenReturn(1L);
    LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties, () -> 1_000_000L, redis);

    limiter.recordFailure("127.0.0.1");

    verify(redis).expire(anyString(), eq(Duration.ofSeconds(60)));
    assertEquals(0, limiter.trackedSources());
  }
}
