package com.winpress.commercial.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class OpenApiRepositoryTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final OpenApiRepository repository = new OpenApiRepository(jdbc);

  @Test
  void activeKeyLookupFailsClosedWhenTheCustomerOwnerIsNoLongerActive() {
    when(jdbc.queryForList(anyString(), eq("digest"))).thenReturn(List.of());

    repository.activeKeyPrincipal("digest");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForList(sql.capture(), eq("digest"));
    String query = sql.getValue();
    assertTrue(query.contains("customer.status='ACTIVE'"));
    assertTrue(query.contains("organization.status='ACTIVE'"));
    assertTrue(query.contains("assignment.status='ACTIVE'"));
    assertTrue(query.contains("role.role_code='CUSTOMER'"));
  }
}
