package com.winpress.commercial.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.AuthRepository;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
  private static final String PREFIX = "winpress:session:";
  private static final String USER_PREFIX = "winpress:user-sessions:";
  private static final String GENERATION_PREFIX = "winpress:user-session-generation:";
  private static final Pattern TOKEN_PATTERN = Pattern.compile("^[0-9a-f]{32}$");
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final WinPressProperties properties;
  private final AuthRepository repository;

  public SessionService(StringRedisTemplate redis, ObjectMapper objectMapper, WinPressProperties properties,
      AuthRepository repository) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.repository = repository;
  }

  public String create(AuthPrincipal principal) {
    String token = UUID.randomUUID().toString().replace("-", "");
    try {
      Duration ttl = sessionTtl();
      String generation = currentOrCreateGeneration(principal.userId(), ttl);
      redis.opsForValue().set(PREFIX + token,
          objectMapper.writeValueAsString(new SessionRecord(principal, generation)), ttl);
      redis.opsForSet().add(USER_PREFIX + principal.userId(), token);
      redis.expire(USER_PREFIX + principal.userId(), ttl);
      return token;
    } catch (Exception ex) {
      throw new BusinessException("SESSION_SERVICE_UNAVAILABLE", "登录服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  public AuthPrincipal resolve(String token) {
    if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
      throw expired();
    }
    try {
      String json = redis.opsForValue().get(PREFIX + token);
      if (json == null) {
        throw expired();
      }
      SessionRecord session = objectMapper.readValue(json, SessionRecord.class);
      if (session.principal() == null || session.generation() == null || session.generation().isBlank()) {
        throw invalid();
      }
      AuthPrincipal stored = session.principal();
      String activeGeneration = redis.opsForValue().get(GENERATION_PREFIX + stored.userId());
      if (!session.generation().equals(activeGeneration)) {
        removeKnownSession(token, stored.userId());
        throw expired();
      }
      AuthPrincipal current = repository.activePrincipalById(stored.userId());
      if (current == null) {
        removeKnownSession(token, stored.userId());
        throw expired();
      }

      Duration ttl = sessionTtl();
      redis.opsForValue().set(PREFIX + token,
          objectMapper.writeValueAsString(new SessionRecord(current, session.generation())), ttl);
      redis.opsForSet().add(USER_PREFIX + current.userId(), token);
      redis.expire(USER_PREFIX + current.userId(), ttl);
      redis.expire(GENERATION_PREFIX + current.userId(), ttl);
      return current;
    } catch (BusinessException ex) {
      throw ex;
    } catch (JsonProcessingException ex) {
      throw new BusinessException("SESSION_INVALID", "登录状态异常，请重新登录", HttpStatus.UNAUTHORIZED);
    } catch (Exception ex) {
      throw new BusinessException("SESSION_SERVICE_UNAVAILABLE", "登录服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  public void revoke(String token) {
    if (token != null && TOKEN_PATTERN.matcher(token).matches()) {
      String json = redis.opsForValue().get(PREFIX + token);
      redis.delete(PREFIX + token);
      if (json != null) {
        try {
          SessionRecord session = objectMapper.readValue(json, SessionRecord.class);
          if (session.principal() != null) {
            redis.opsForSet().remove(USER_PREFIX + session.principal().userId(), token);
          }
        } catch (JsonProcessingException ignored) {
          redis.delete(PREFIX + token);
        }
      }
    }
  }

  public void invalidateUser(Long userId) {
    redis.opsForValue().set(
        GENERATION_PREFIX + userId, newGeneration(), sessionTtl());
    Set<String> tokens = redis.opsForSet().members(USER_PREFIX + userId);
    if (tokens != null && !tokens.isEmpty()) redis.delete(tokens.stream().map(PREFIX::concat).toList());
    redis.delete(USER_PREFIX + userId);
  }

  private String currentOrCreateGeneration(Long userId, Duration ttl) {
    String key = GENERATION_PREFIX + userId;
    String generation = redis.opsForValue().get(key);
    if (generation == null || generation.isBlank()) {
      String candidate = newGeneration();
      Boolean created = redis.opsForValue().setIfAbsent(key, candidate, ttl);
      generation = Boolean.TRUE.equals(created) ? candidate : redis.opsForValue().get(key);
    }
    if (generation == null || generation.isBlank()) {
      throw new IllegalStateException("Session generation is unavailable");
    }
    redis.expire(key, ttl);
    return generation;
  }

  private void removeKnownSession(String token, Long userId) {
    redis.delete(PREFIX + token);
    redis.opsForSet().remove(USER_PREFIX + userId, token);
  }

  private Duration sessionTtl() {
    return Duration.ofHours(properties.getSessionTtlHours());
  }

  private static String newGeneration() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private static BusinessException expired() {
    return new BusinessException("SESSION_EXPIRED", "登录已失效，请重新登录", HttpStatus.UNAUTHORIZED);
  }

  private static BusinessException invalid() {
    return new BusinessException("SESSION_INVALID", "登录状态异常，请重新登录", HttpStatus.UNAUTHORIZED);
  }

  record SessionRecord(AuthPrincipal principal, String generation) {}
}
