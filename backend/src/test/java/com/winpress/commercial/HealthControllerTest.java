package com.winpress.commercial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winpress.commercial.controller.HealthController;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {
  @Test
  void reportsUpOnlyAfterTheRequiredWorkflowSchemaIsPresent() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);

    var response = new HealthController(jdbc).health();

    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().success());
    assertEquals("UP", response.getBody().data().get("schemaStatus"));
    assertEquals("41", response.getBody().data().get("schemaVersion"));
    assertEquals(
        "winpress-v4.2.30-20260731",
        response.getBody().data().get("apiContractVersion"));
    ArgumentCaptor<String> readinessSql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForObject(readinessSql.capture(), eq(Boolean.class));
    assertTrue(readinessSql.getValue().contains("trg_publish_task_terminal_integrity"));
    assertTrue(readinessSql.getValue().contains("uq_publish_plan_submission_key"));
    assertTrue(readinessSql.getValue().contains("uq_settlement_transaction_submission_key"));
    assertTrue(readinessSql.getValue().contains("quote_adjustment_batch"));
    assertTrue(readinessSql.getValue().contains("fk_quote_adjustment_batch"));
    assertTrue(readinessSql.getValue().contains("supplier_api_connection"));
    assertTrue(readinessSql.getValue().contains("platform_acceptance_gate"));
    assertTrue(readinessSql.getValue().contains("legacy_service_review"));
    assertTrue(readinessSql.getValue().contains("platform_acceptance_evidence_item"));
    assertTrue(readinessSql.getValue().contains("trg_supplier_order_fulfillment_evidence"));
    assertTrue(readinessSql.getValue().contains("trg_legacy_combination_service_boundary"));
    assertTrue(readinessSql.getValue().contains("open_api_application"));
    assertTrue(readinessSql.getValue().contains("open_api_access_key"));
    assertTrue(readinessSql.getValue().contains("open_api_request_receipt"));
    assertTrue(readinessSql.getValue().contains("schema_migration_ledger"));
    assertTrue(readinessSql.getValue().contains("trg_schema_migration_ledger_append_only"));
    assertTrue(readinessSql.getValue().contains("37-media-pr-result-integrity.sql"));
    assertTrue(readinessSql.getValue().contains("writing_assignment_member"));
    assertTrue(readinessSql.getValue().contains("ex_writing_assignment_member_no_overlap"));
    assertTrue(readinessSql.getValue().contains("38-writing-assignment-slot-schedule-integrity.sql"));
    assertTrue(readinessSql.getValue().contains("ck_writer_profile_service_radius_nonnegative"));
    assertTrue(readinessSql.getValue().contains("ck_writing_assignment_member_distance_nonnegative"));
    assertTrue(readinessSql.getValue().contains("trg_writing_assignment_member_radius_integrity"));
    assertTrue(readinessSql.getValue().contains("trg_writer_profile_radius_integrity"));
    assertTrue(readinessSql.getValue().contains("39-writing-assignment-radius-integrity.sql"));
    assertTrue(readinessSql.getValue().contains("40-conference-work-item-state-integrity.sql"));
    assertTrue(readinessSql.getValue().contains("ck_conference_work_item_completion_time"));
    assertTrue(readinessSql.getValue().contains("trg_conference_work_item_terminal_integrity"));
    assertTrue(readinessSql.getValue().contains("trg_conference_project_completion_integrity"));
    assertTrue(readinessSql.getValue().contains("41-conference-media-candidate-state-integrity.sql"));
    assertTrue(readinessSql.getValue().contains("ck_conference_media_candidate_status_timeline"));
    assertTrue(readinessSql.getValue().contains("ck_conference_media_candidate_contact_time_order"));
    assertTrue(readinessSql.getValue().contains("ck_conference_media_candidate_outcome_note"));
    assertTrue(readinessSql.getValue().contains("trg_conference_media_candidate_state_integrity"));
  }

  @Test
  void reportsServiceUnavailableWhenTheDatabaseSchemaIsNotReady() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);

    var response = new HealthController(jdbc).health();

    assertEquals(503, response.getStatusCode().value());
    assertFalse(response.getBody().success());
    assertEquals("SCHEMA_OUT_OF_DATE", response.getBody().code());
    assertEquals("UP", response.getBody().data().get("database"));
    assertEquals("OUT_OF_DATE", response.getBody().data().get("schemaStatus"));
    assertEquals("unknown", response.getBody().data().get("schemaVersion"));
  }

  @Test
  void returnsServiceUnavailableWhenTheDatabaseCannotBeReached() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("SELECT 1", Integer.class))
        .thenThrow(new CannotGetJdbcConnectionException("database unavailable", new IllegalStateException()));

    var response = new HealthController(jdbc).health();

    assertEquals(503, response.getStatusCode().value());
    assertFalse(response.getBody().success());
    assertEquals("DATABASE_UNAVAILABLE", response.getBody().code());
    assertEquals("DOWN", response.getBody().data().get("database"));
    assertEquals("UNKNOWN", response.getBody().data().get("schemaStatus"));
  }
}
