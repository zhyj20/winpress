package com.winpress.commercial.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.AuthRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class SessionServiceTest {
  private static final String TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String GENERATION = "generation-current";

  @Test
  @SuppressWarnings("unchecked")
  void resolvesAgainstCurrentAccountStateAndRefreshesTheRevocationIndex() throws Exception {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    SetOperations<String, String> sets = mock(SetOperations.class);
    AuthRepository repository = mock(AuthRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();
    WinPressProperties properties = new WinPressProperties();
    properties.setSessionTtlHours(6);

    AuthPrincipal stored = principal("CUSTOMER", List.of("PROJECT_VIEW"));
    AuthPrincipal current = principal("OPERATOR", List.of("PROJECT_VIEW", "TASK_UPDATE"));
    when(redis.opsForValue()).thenReturn(values);
    when(redis.opsForSet()).thenReturn(sets);
    SessionService.SessionRecord session = new SessionService.SessionRecord(stored, GENERATION);
    when(values.get("winpress:session:" + TOKEN)).thenReturn(objectMapper.writeValueAsString(session));
    when(values.get("winpress:user-session-generation:7")).thenReturn(GENERATION);
    when(repository.activePrincipalById(7L)).thenReturn(current);

    SessionService service = new SessionService(redis, objectMapper, properties, repository);

    assertEquals(current, service.resolve(TOKEN));
    verify(values).set("winpress:session:" + TOKEN,
        objectMapper.writeValueAsString(new SessionService.SessionRecord(current, GENERATION)),
        Duration.ofHours(6));
    verify(sets).add("winpress:user-sessions:7", TOKEN);
    verify(redis).expire("winpress:user-sessions:7", Duration.ofHours(6));
    verify(redis).expire("winpress:user-session-generation:7", Duration.ofHours(6));
  }

  @Test
  @SuppressWarnings("unchecked")
  void rejectsAStoredSessionWhenTheAccountIsNoLongerActive() throws Exception {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    SetOperations<String, String> sets = mock(SetOperations.class);
    AuthRepository repository = mock(AuthRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();
    WinPressProperties properties = new WinPressProperties();
    AuthPrincipal stored = principal("CUSTOMER", List.of("PROJECT_VIEW"));
    when(redis.opsForValue()).thenReturn(values);
    when(redis.opsForSet()).thenReturn(sets);
    SessionService.SessionRecord session = new SessionService.SessionRecord(stored, GENERATION);
    when(values.get("winpress:session:" + TOKEN)).thenReturn(objectMapper.writeValueAsString(session));
    when(values.get("winpress:user-session-generation:7")).thenReturn(GENERATION);
    when(repository.activePrincipalById(7L)).thenReturn(null);

    SessionService service = new SessionService(redis, objectMapper, properties, repository);

    BusinessException exception = assertThrows(BusinessException.class, () -> service.resolve(TOKEN));
    assertEquals("SESSION_EXPIRED", exception.getCode());
    verify(redis).delete("winpress:session:" + TOKEN);
    verify(sets).remove("winpress:user-sessions:7", TOKEN);
  }

  @Test
  @SuppressWarnings("unchecked")
  void rejectsAnOldGenerationEvenWhenTheRevocationIndexIsMissing() throws Exception {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    SetOperations<String, String> sets = mock(SetOperations.class);
    AuthRepository repository = mock(AuthRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();
    AuthPrincipal stored = principal("CUSTOMER", List.of("PROJECT_VIEW"));
    when(redis.opsForValue()).thenReturn(values);
    when(redis.opsForSet()).thenReturn(sets);
    when(values.get("winpress:session:" + TOKEN)).thenReturn(objectMapper.writeValueAsString(
        new SessionService.SessionRecord(stored, "generation-before-access-change")));
    when(values.get("winpress:user-session-generation:7")).thenReturn("generation-after-access-change");

    SessionService service = new SessionService(
        redis, objectMapper, new WinPressProperties(), repository);

    BusinessException exception = assertThrows(BusinessException.class, () -> service.resolve(TOKEN));

    assertEquals("SESSION_EXPIRED", exception.getCode());
    verify(redis).delete("winpress:session:" + TOKEN);
    verify(sets).remove("winpress:user-sessions:7", TOKEN);
    verifyNoInteractions(repository);
  }

  @Test
  @SuppressWarnings("unchecked")
  void rotatesTheGenerationEvenWhenNoIndexedTokensRemain() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    SetOperations<String, String> sets = mock(SetOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(redis.opsForSet()).thenReturn(sets);
    when(sets.members("winpress:user-sessions:7")).thenReturn(java.util.Set.of());
    SessionService service = new SessionService(
        redis, new ObjectMapper(), new WinPressProperties(), mock(AuthRepository.class));

    service.invalidateUser(7L);

    verify(values).set(eq("winpress:user-session-generation:7"), anyString(), eq(Duration.ofHours(12)));
    verify(redis).delete("winpress:user-sessions:7");
  }

  @Test
  void rejectsMalformedTokensBeforeAccessingRedisOrTheDatabase() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    AuthRepository repository = mock(AuthRepository.class);
    SessionService service = new SessionService(redis, new ObjectMapper(), new WinPressProperties(), repository);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.resolve("../../arbitrary-redis-key"));

    assertEquals("SESSION_EXPIRED", exception.getCode());
    verifyNoInteractions(redis, repository);
  }

  private static AuthPrincipal principal(String role, List<String> permissions) {
    return new AuthPrincipal(7L, "USR-0007", 3L, "测试企业", "user@example.com",
        "测试用户", "13800000000", "user@example.com", role, permissions);
  }
}
