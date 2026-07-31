package com.winpress.commercial.federation;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Shared fixed-window protection for the server-to-server GEO adapter.
 *
 * <p>The limiter stores only a short source fingerprint. Redis shares the limit across application
 * instances; an instance-local fallback keeps the adapter protected when Redis is unavailable.
 * The request body and signed assertion are never used as rate-limit keys.</p>
 */
@Component
public class FederationRequestLimiter {
  private static final int MAX_TRACKED_SOURCES = 4_096;
  private static final String REDIS_PREFIX = "winpress:federation-request:";
  private static final long WINDOW_MILLIS = 60_000L;

  private final WinPressProperties properties;
  private final LongSupplier clock;
  private final StringRedisTemplate redis;
  private final ConcurrentHashMap<String, RequestWindow> windows = new ConcurrentHashMap<>();

  @Autowired
  public FederationRequestLimiter(
      WinPressProperties properties, ObjectProvider<StringRedisTemplate> redisProvider) {
    this(properties, System::currentTimeMillis, redisProvider.getIfAvailable());
  }

  FederationRequestLimiter(WinPressProperties properties, LongSupplier clock) {
    this(properties, clock, null);
  }

  FederationRequestLimiter(
      WinPressProperties properties, LongSupplier clock, StringRedisTemplate redis) {
    this.properties = properties;
    this.clock = clock;
    this.redis = redis;
  }

  public void check(String clientSource) {
    long bucket = Math.floorDiv(clock.getAsLong(), WINDOW_MILLIS);
    String fingerprint = fingerprint(clientSource);
    if (checkRedis(fingerprint, bucket)) return;

    RequestWindow next = windows.compute(fingerprint, (ignored, current) -> {
      if (current == null || current.bucket() != bucket) return new RequestWindow(bucket, 1);
      return new RequestWindow(bucket, current.requests() + 1);
    });
    trimIfNeeded(bucket, fingerprint);
    if (next.requests() > maxRequestsPerMinute()) throw limited();
  }

  int trackedSources() {
    return windows.size();
  }

  private boolean checkRedis(String fingerprint, long bucket) {
    if (redis == null) return false;
    String key = redisKey(fingerprint, bucket);
    try {
      Long requests = redis.opsForValue().increment(key);
      if (requests == null) return false;
      if (requests == 1) redis.expire(key, Duration.ofMinutes(2));
      if (requests > maxRequestsPerMinute()) throw limited();
      return true;
    } catch (BusinessException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private void trimIfNeeded(long currentBucket, String currentFingerprint) {
    if (windows.size() <= MAX_TRACKED_SOURCES) return;
    windows.entrySet().removeIf(entry -> entry.getValue().bucket() < currentBucket);
    if (windows.size() <= MAX_TRACKED_SOURCES) return;
    for (String fingerprint : windows.keySet()) {
      if (windows.size() <= MAX_TRACKED_SOURCES) break;
      if (!fingerprint.equals(currentFingerprint)) windows.remove(fingerprint);
    }
  }

  private int maxRequestsPerMinute() {
    return Math.max(10, Math.min(10_000, properties.getFederation().getMaxRequestsPerMinute()));
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

  private static String redisKey(String fingerprint, long bucket) {
    return REDIS_PREFIX + fingerprint + ":" + bucket;
  }

  private BusinessException limited() {
    return new BusinessException(
        "FEDERATION_RATE_LIMITED",
        "接口请求过于频繁，请稍后重试。",
        HttpStatus.TOO_MANY_REQUESTS);
  }

  private record RequestWindow(long bucket, int requests) {}
}
