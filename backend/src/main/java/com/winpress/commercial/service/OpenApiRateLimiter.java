package com.winpress.commercial.service;

import com.winpress.commercial.exception.BusinessException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fixed-window guard for the customer Open API.
 *
 * <p>Redis makes a rate limit effective across application replicas. The bounded in-memory
 * fallback remains available only if Redis is temporarily unavailable.</p>
 */
@Component
public class OpenApiRateLimiter {
  private static final int MAX_TRACKED_APPLICATIONS = 4_096;
  private static final String REDIS_PREFIX = "winpress:open-api-rate:";
  private static final long WINDOW_MILLIS = 60_000L;
  private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();
  private final LongSupplier clock;
  private final StringRedisTemplate redis;

  @Autowired
  public OpenApiRateLimiter(ObjectProvider<StringRedisTemplate> redisProvider) {
    this(System::currentTimeMillis, redisProvider.getIfAvailable());
  }

  OpenApiRateLimiter(LongSupplier clock) {
    this(clock, null);
  }

  OpenApiRateLimiter(LongSupplier clock, StringRedisTemplate redis) {
    this.clock = clock;
    this.redis = redis;
  }

  /** @deprecated Test-only compatibility constructor. */
  @Deprecated(forRemoval = false)
  public OpenApiRateLimiter() {
    this(System::currentTimeMillis, null);
  }

  public void check(Long applicationId, int limitPerMinute) {
    long bucket = Math.floorDiv(clock.getAsLong(), WINDOW_MILLIS);
    int safeLimit = Math.max(1, Math.min(limitPerMinute, 10_000));
    if (checkRedis(applicationId, bucket, safeLimit)) return;

    Window next = windows.compute(applicationId, (id, current) -> {
      if (current == null || current.bucket() != bucket) return new Window(bucket, 1);
      return new Window(current.bucket(), current.count() + 1);
    });
    trimIfNeeded(bucket, applicationId);
    if (next.count() > safeLimit) {
      throw new BusinessException("OPEN_API_RATE_LIMITED", "当前接口调用频率超过已约定上限，请稍后重试。", HttpStatus.TOO_MANY_REQUESTS);
    }
  }

  int trackedApplications() {
    return windows.size();
  }

  private boolean checkRedis(Long applicationId, long bucket, int limitPerMinute) {
    if (redis == null) return false;
    try {
      String key = REDIS_PREFIX + applicationId + ":" + bucket;
      Long requests = redis.opsForValue().increment(key);
      if (requests == null) return false;
      if (requests == 1) redis.expire(key, Duration.ofMinutes(2));
      if (requests > limitPerMinute) {
        throw new BusinessException("OPEN_API_RATE_LIMITED", "当前接口调用频率超过已约定上限，请稍后重试。", HttpStatus.TOO_MANY_REQUESTS);
      }
      return true;
    } catch (BusinessException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private void trimIfNeeded(long currentBucket, Long currentApplicationId) {
    if (windows.size() <= MAX_TRACKED_APPLICATIONS) return;
    windows.entrySet().removeIf(entry -> entry.getValue().bucket() < currentBucket);
    if (windows.size() <= MAX_TRACKED_APPLICATIONS) return;
    for (Long applicationId : windows.keySet()) {
      if (windows.size() <= MAX_TRACKED_APPLICATIONS) break;
      if (!applicationId.equals(currentApplicationId)) windows.remove(applicationId);
    }
  }

  record Window(long bucket, int count) {}
}
