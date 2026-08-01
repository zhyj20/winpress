package com.winpress.commercial.controller;

import com.winpress.commercial.config.ApiResponse;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
  // Do not treat a source constant as proof that the active database has received the migrations
  // required by the four-service workflow. This unauthenticated endpoint intentionally exposes
  // only a generic readiness state; migration versions, API contracts and build metadata stay in
  // the controlled deployment evidence and database migration ledger.
  private static final String SCHEMA_READINESS_SQL = """
      SELECT
        to_regclass('public.customer_requirement') IS NOT NULL
        AND to_regclass('public.project') IS NOT NULL
        AND to_regclass('public.conference_work_item') IS NOT NULL
        AND to_regclass('public.editorial_task') IS NOT NULL
        AND to_regclass('public.service_intake_task') IS NOT NULL
        AND to_regclass('public.manuscript_version') IS NOT NULL
        AND to_regclass('public.publish_task') IS NOT NULL
        AND to_regclass('public.direct_publish_order') IS NOT NULL
        AND to_regclass('public.settlement_order') IS NOT NULL
        AND to_regclass('public.settlement_transaction') IS NOT NULL
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'project'
            AND column_name = 'activity_root_project_id'
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'customer_requirement'
            AND column_name = 'submission_key'
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'customer_requirement'
            AND column_name = 'submission_hash'
        )
        AND to_regclass('public.uq_customer_requirement_submission_key') IS NOT NULL
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_customer_requirement_submission_pair'
            AND conrelid = 'public.customer_requirement'::regclass
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'publish_plan'
            AND column_name = 'submission_key'
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'publish_plan'
            AND column_name = 'submission_hash'
        )
        AND to_regclass('public.uq_publish_plan_submission_key') IS NOT NULL
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_publish_plan_submission_pair'
            AND conrelid = 'public.publish_plan'::regclass
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'manuscript_version'
            AND column_name = 'source_manuscript_id'
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'manuscript_version'
            AND column_name = 'source_version_id'
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'publish_plan_item'
            AND column_name = 'channel_id' AND is_nullable = 'YES'
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'publish_task'
            AND column_name = 'channel_id' AND is_nullable = 'YES'
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_service_intake_task_title_not_placeholder'
            AND conrelid = 'public.service_intake_task'::regclass
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_settlement_transaction_evidence'
            AND conrelid = 'public.settlement_transaction'::regclass
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'settlement_transaction'
            AND column_name = 'submission_key'
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'settlement_transaction'
            AND column_name = 'submission_hash'
        )
        AND to_regclass('public.uq_settlement_transaction_submission_key') IS NOT NULL
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_settlement_transaction_submission_pair'
            AND conrelid = 'public.settlement_transaction'::regclass
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_publish_task_status'
            AND conrelid = 'public.publish_task'::regclass
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_publish_task_not_proceeding_channel'
            AND conrelid = 'public.publish_task'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname = 'trg_publish_task_terminal_integrity'
            AND tgrelid = 'public.publish_task'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_media_pr_invitation_status'
            AND conrelid = 'public.media_pr_invitation'::regclass
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_result_link_status'
            AND conrelid = 'public.result_link'::regclass
        )
        AND to_regclass('public.uq_result_link_task_url') IS NOT NULL
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_channel_quote_price_integrity'
            AND conrelid = 'public.channel_quote'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_channel_quote_validity'
            AND conrelid = 'public.channel_quote'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_channel_quote_status'
            AND conrelid = 'public.channel_quote'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1
          FROM pg_index
          WHERE indexrelid = to_regclass('public.uq_channel_quote_one_active_per_channel')
            AND indisunique
            AND indisvalid
            AND indisready
        )
        AND to_regclass('public.quote_adjustment_batch') IS NOT NULL
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'quote_adjustment'
            AND column_name = 'batch_id'
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'uq_quote_adjustment_batch_submission'
            AND conrelid = 'public.quote_adjustment_batch'::regclass
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_quote_adjustment_batch_key'
            AND conrelid = 'public.quote_adjustment_batch'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_quote_adjustment_batch_hash'
            AND conrelid = 'public.quote_adjustment_batch'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_quote_adjustment_batch_counts'
            AND conrelid = 'public.quote_adjustment_batch'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_quote_adjustment_batch_status'
            AND conrelid = 'public.quote_adjustment_batch'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'fk_quote_adjustment_batch'
            AND conrelid = 'public.quote_adjustment'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1
          FROM pg_index
          WHERE indexrelid = to_regclass('public.idx_quote_adjustment_batch')
            AND indisvalid
            AND indisready
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname = 'trg_publish_plan_item_service_integrity'
            AND tgrelid = 'public.publish_plan_item'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname = 'trg_publish_plan_project_service_integrity'
            AND tgrelid = 'public.publish_plan'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname = 'trg_project_plan_service_integrity'
            AND tgrelid = 'public.project'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname = 'trg_requirement_plan_service_integrity'
            AND tgrelid = 'public.customer_requirement'::regclass
            AND NOT tgisinternal
        )
        AND to_regclass('public.supplier_api_connection') IS NOT NULL
        AND to_regclass('public.platform_acceptance_gate') IS NOT NULL
        AND to_regclass('public.platform_acceptance_evidence_item') IS NOT NULL
        AND to_regclass('public.legacy_service_review') IS NOT NULL
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema='public' AND table_name='supplier_api_connection'
            AND column_name='reconciliation_path'
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema='public' AND table_name='supplier_order'
            AND column_name='fulfillment_mode'
        )
        AND EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema='public' AND table_name='supplier_order'
            AND column_name='submission_evidence_ref'
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_supplier_api_enablement'
            AND conrelid = 'public.supplier_api_connection'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_platform_acceptance_gate_evidence'
            AND conrelid = 'public.platform_acceptance_gate'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_legacy_service_review_approval'
            AND conrelid = 'public.legacy_service_review'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_platform_acceptance_evidence_reference'
            AND conrelid = 'public.platform_acceptance_evidence_item'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_supplier_order_fulfillment_mode'
            AND conrelid = 'public.supplier_order'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname = 'trg_platform_acceptance_gate_readiness'
            AND tgrelid = 'public.platform_acceptance_gate'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname = 'trg_supplier_order_fulfillment_evidence'
            AND tgrelid = 'public.supplier_order'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname = 'trg_legacy_combination_service_boundary'
            AND tgrelid = 'public.customer_requirement'::regclass
            AND NOT tgisinternal
        )
        AND to_regclass('public.open_api_application') IS NOT NULL
        AND to_regclass('public.open_api_access_key') IS NOT NULL
        AND to_regclass('public.open_api_request_receipt') IS NOT NULL
        AND to_regclass('public.open_api_access_log') IS NOT NULL
        AND to_regclass('public.writing_assignment_member') IS NOT NULL
        AND EXISTS (
          SELECT 1 FROM pg_extension WHERE extname='btree_gist'
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname='ex_writing_assignment_member_no_overlap'
            AND conrelid=to_regclass('public.writing_assignment_member')
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname='ck_writer_profile_service_radius_nonnegative'
            AND conrelid='public.writer_profile'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname='ck_writing_assignment_member_distance_nonnegative'
            AND conrelid='public.writing_assignment_member'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname='trg_writing_assignment_member_radius_integrity'
            AND tgrelid='public.writing_assignment_member'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname='trg_writer_profile_radius_integrity'
            AND tgrelid='public.writer_profile'::regclass
            AND NOT tgisinternal
        )
        AND to_regclass('public.schema_migration_ledger') IS NOT NULL
        AND EXISTS (
          SELECT 1 FROM schema_migration_ledger
          WHERE migration_version=36
            AND script_name='36-schema-migration-ledger.sql'
            AND release_contract='winpress-v4.2.25-20260731'
            AND apply_mode='BASELINE'
            AND verification_reference='SCHEMA35_STRUCTURAL_BASELINE_20260731'
        )
        AND EXISTS (
          SELECT 1 FROM schema_migration_ledger
          WHERE migration_version=37
            AND script_name='37-media-pr-result-integrity.sql'
            AND release_contract='winpress-v4.2.26-20260731'
            AND apply_mode='FORWARD'
            AND verification_reference='MEDIA_PR_RESULT_CHAIN_INTEGRITY_20260731'
        )
        AND EXISTS (
          SELECT 1 FROM schema_migration_ledger
          WHERE migration_version=38
            AND script_name='38-writing-assignment-slot-schedule-integrity.sql'
            AND release_contract='winpress-v4.2.27-20260731'
            AND apply_mode='FORWARD'
            AND verification_reference='WRITING_ASSIGNMENT_SLOT_AND_SCHEDULE_INTEGRITY_20260731'
        )
        AND EXISTS (
          SELECT 1 FROM schema_migration_ledger
          WHERE migration_version=39
            AND script_name='39-writing-assignment-radius-integrity.sql'
            AND release_contract='winpress-v4.2.28-20260731'
            AND apply_mode='FORWARD'
            AND verification_reference='WRITING_ASSIGNMENT_RADIUS_INTEGRITY_20260731'
        )
        AND EXISTS (
          SELECT 1 FROM schema_migration_ledger
          WHERE migration_version=40
            AND script_name='40-conference-work-item-state-integrity.sql'
            AND release_contract='winpress-v4.2.29-20260731'
            AND apply_mode='FORWARD'
            AND verification_reference='CONFERENCE_WORK_ITEM_STATE_INTEGRITY_20260731'
        )
        AND EXISTS (
          SELECT 1 FROM schema_migration_ledger
          WHERE migration_version=41
            AND script_name='41-conference-media-candidate-state-integrity.sql'
            AND release_contract='winpress-v4.2.30-20260731'
            AND apply_mode='FORWARD'
            AND verification_reference='CONFERENCE_MEDIA_CANDIDATE_STATE_INTEGRITY_20260731'
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname='ck_conference_work_item_completion_time'
            AND conrelid='public.conference_work_item'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname='trg_conference_work_item_terminal_integrity'
            AND tgrelid='public.conference_work_item'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname='trg_conference_project_completion_integrity'
            AND tgrelid='public.conference_project'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname='ck_conference_media_candidate_status_timeline'
            AND conrelid='public.conference_media_candidate'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname='ck_conference_media_candidate_contact_time_order'
            AND conrelid='public.conference_media_candidate'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname='ck_conference_media_candidate_outcome_note'
            AND conrelid='public.conference_media_candidate'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname='trg_conference_media_candidate_state_integrity'
            AND tgrelid='public.conference_media_candidate'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_trigger
          WHERE tgname='trg_schema_migration_ledger_append_only'
            AND tgrelid='public.schema_migration_ledger'::regclass
            AND NOT tgisinternal
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_open_api_application_activation'
            AND conrelid = 'public.open_api_application'::regclass
            AND convalidated
        )
        AND EXISTS (
          SELECT 1 FROM pg_constraint
          WHERE conname = 'ck_open_api_access_key_revocation'
            AND conrelid = 'public.open_api_access_key'::regclass
            AND convalidated
        )
      """;
  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @GetMapping("/health")
  public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
    try {
      Integer value = jdbc.queryForObject("SELECT 1", Integer.class);
      if (value == null || value != 1) {
        return databaseUnavailable();
      }
      Boolean schemaReady = jdbc.queryForObject(SCHEMA_READINESS_SQL, Boolean.class);
      if (!Boolean.TRUE.equals(schemaReady)) {
        return schemaOutOfDate();
      }
      return ResponseEntity.ok(ApiResponse.ok(healthData("UP", "UP", "UP")));
    } catch (DataAccessException exception) {
      return databaseUnavailable();
    }
  }

  private ResponseEntity<ApiResponse<Map<String, Object>>> schemaOutOfDate() {
    ApiResponse<Map<String, Object>> response = new ApiResponse<>(
        false,
        "SCHEMA_OUT_OF_DATE",
        "服务正在升级，请稍后重试",
        healthData("DEGRADED", "UP", "OUT_OF_DATE"),
        OffsetDateTime.now());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
  }

  private ResponseEntity<ApiResponse<Map<String, Object>>> databaseUnavailable() {
    ApiResponse<Map<String, Object>> response = new ApiResponse<>(
        false,
        "DATABASE_UNAVAILABLE",
        "数据服务暂时不可用",
        healthData("DOWN", "DOWN", "UNKNOWN"),
        OffsetDateTime.now());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
  }

  private static Map<String, Object> healthData(
      String status, String database, String schemaStatus) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", status);
    data.put("database", database);
    data.put("schemaStatus", schemaStatus);
    return data;
  }
}
