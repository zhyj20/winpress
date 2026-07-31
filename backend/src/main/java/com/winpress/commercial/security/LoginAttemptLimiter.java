package com.winpress.commercial.security;

import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * A bounded protection against repeated password guesses. When Redis is available, the lock is
 * shared by every application instance. If Redis is temporarily unavailable, an instance-local
 * fallback still slows a burst without making login unavailable.
 *
 * <p>It stores only an opaque source fingerprint and no account name. The response stays the same
 * for every account so that a failed attempt cannot be used to enumerate users.</p>
 */
@Component
public class LoginAttemptLimiter {
  private static final int MAX_TRACKED_SOURCES = 4_096;
  private static final String REDIS_PREFIX = "winpress:login-attempt:";

  private final WinPressProperties properties;
  private final LongSupplier clock;
  private final StringRedisTemplate redis;
  private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

  @Autowired
  public LoginAttemptLimiter(
      WinPressProperties properties, ObjectProvider<StringRedisTemplate> redisProvider) {
    this(properties, System::currentTimeMillis, redisProvider.getIfAvailable());
  }

  LoginAttemptLimiter(WinPressProperties properties, LongSupplier clock) {
    this(properties, clock, null);
  }

  LoginAttemptLimiter(
      WinPressProperties properties, LongSupplier clock, StringRedisTemplate redis) {
    this.properties = properties;
    this.clock = clock;
    this.redis = redis;
  }

  public void check(String clientSource) {
    String key = fingerprint(clientSource);
    if (checkRedis(key)) return;

    long now = clock.getAsLong();
    AttemptWindow window = attempts.get(key);
    if (window == null) return;
    if (window.blockedUntilMillis() > now) throw blocked();
    if (window.blockedUntilMillis() > 0) {
      attempts.remove(key, window);
      return;
    }
    if (window.firstFailureAtMillis() + failureWindowMillis() <= now) attempts.remove(key, window);
  }

  public void recordFailure(String clientSource) {
    String key = fingerprint(clientSource);
    if (recordRedisFailure(key)) return;

    long now = clock.getAsLong();
    attempts.compute(key, (ignored, current) -> {
      if (current == null || current.firstFailureAtMillis() + failureWindowMillis() <= now) {
        return new AttemptWindow(now, 1, 0);
      }
      int nextFailures = current.failures() + 1;
      long blockedUntil = nextFailures >= maxFailures()
          ? Math.max(current.blockedUntilMillis(), now + cooldownMillis())
          : current.blockedUntilMillis();
      return new AttemptWindow(current.firstFailureAtMillis(), nextFailures, blockedUntil);
    });
    trimIfNeeded();
  }

  public void recordSuccess(String clientSource) {
    String key = fingerprint(clientSource);
    if (clearRedis(key)) return;
    attempts.remove(key);
  }

  int trackedSources() { return attempts.size(); }

  private void trimIfNeeded() {
    if (attempts.size() <= MAX_TRACKED_SOURCES) return;
    long cutoff = clock.getAsLong() - failureWindowMillis();
    attempts.entrySet().removeIf(entry ->
        entry.getValue().blockedUntilMillis() <= cutoff
            && entry.getValue().firstFailureAtMillis() <= cutoff);
  }

  private int maxFailures() {
    return Math.max(3, Math.min(30, properties.getLogin().getMaxFailures()));
  }

  private long failureWindowMillis() {
    return Math.max(60, Math.min(86_400, properties.getLogin().getFailureWindowSeconds())) * 1_000L;
  }

  private long cooldownMillis() {
    return Math.max(30, Math.min(86_400, properties.getLogin().getCooldownSeconds())) * 1_000L;
  }

  private boolean checkRedis(String key) {
    if (redis == null) return false;
    try {
      if (Boolean.TRUE.equals(redis.hasKey(redisLockKey(key)))) throw blocked();
      return true;
    } catch (BusinessException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private boolean recordRedisFailure(String key) {
    if (redis == null) return false;
    try {
      Long failures = redis.opsForValue().increment(redisAttemptKey(key));
      if (failures == null) return false;
      if (failures == 1) {
        redis.expire(redisAttemptKey(key), Duration.ofMillis(failureWindowMillis()));
      }
      if (failures >= maxFailures()) {
        redis.opsForValue().set(redisLockKey(key), "1", Duration.ofMillis(cooldownMillis()));
        redis.delete(redisAttemptKey(key));
      }
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private boolean clearRedis(String key) {
    if (redis == null) return false;
    try {
      redis.delete(java.util.List.of(redisAttemptKey(key), redisLockKey(key)));
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private static String redisAttemptKey(String fingerprint) {
    return REDIS_PREFIX + fingerprint + ":failures";
  }

  private static String redisLockKey(String fingerprint) {
    return REDIS_PREFIX + fingerprint + ":locked";
  }

  private String fingerprint(String clientSource) {
    String source = clientSource == null || clientSource.isBlank() ? "unknown" : clientSource.trim();
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(source.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 16);
    } catch (NoSuchAlgorithmException exception) {
      return Integer.toUnsignedString(source.hashCode(), 36);
    }
  }

  private BusinessException blocked() {
    return new BusinessException(
        "LOGIN_RATE_LIMITED", "登录尝试过于频繁，请稍后再试。", HttpStatus.TOO_MANY_REQUESTS);
  }

  private record AttemptWindow(long firstFailureAtMillis, int failures, long blockedUntilMillis) {}
}
