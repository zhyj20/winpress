package com.winpress.commercial.repository;

import com.winpress.commercial.dto.IntegrationAdminDtos.SaveSupplierApiConnectionRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateAcceptanceGateRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateAcceptanceEvidenceRequest;
import com.winpress.commercial.dto.IntegrationAdminDtos.UpdateLegacyServiceReviewRequest;
import com.winpress.commercial.security.AuthPrincipal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class IntegrationAdminRepository {
  private final JdbcTemplate jdbc;

  public IntegrationAdminRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> connections() {
    return jdbc.queryForList("""
        SELECT connection.id, connection.connection_no AS "connectionNo",
               connection.supplier_id AS "supplierId",
               supplier.supplier_no AS "supplierNo",
               supplier.supplier_name AS "supplierName",
               connection.connection_name AS "connectionName",
               connection.provider_code AS "providerCode",
               connection.connection_kind AS "connectionKind",
               connection.environment, connection.base_url AS "baseUrl",
               connection.auth_type AS "authType",
               connection.auth_header_name AS "authHeaderName",
               connection.credential_env_key AS "credentialEnvKey",
               connection.capability_scope AS "capabilityScope",
               connection.media_search_path AS "mediaSearchPath",
               connection.reporter_search_path AS "reporterSearchPath",
               connection.quote_path AS "quotePath",
               connection.order_path AS "orderPath",
               connection.order_status_path AS "orderStatusPath",
               connection.callback_path AS "callbackPath",
               connection.reconciliation_path AS "reconciliationPath",
               connection.sla_reference AS "slaReference",
               connection.rate_limit_per_minute AS "rateLimitPerMinute",
               connection.timeout_seconds AS "timeoutSeconds",
               connection.max_retries AS "maxRetries",
               connection.data_scope AS "dataScope",
               connection.contract_reference AS "contractReference",
               connection.authorization_status AS "authorizationStatus",
               connection.authorization_evidence_ref AS "authorizationEvidenceRef",
               connection.sandbox_status AS "sandboxStatus",
               connection.sandbox_evidence_ref AS "sandboxEvidenceRef",
               connection.production_status AS "productionStatus",
               connection.production_evidence_ref AS "productionEvidenceRef",
               connection.internal_note AS "internalNote",
               connection.enabled,
               connection.last_config_checked_at AS "lastConfigCheckedAt",
               connection.last_config_check_status AS "lastConfigCheckStatus",
               connection.last_config_check_detail AS "lastConfigCheckDetail",
               connection.created_at AS "createdAt",
               connection.updated_at AS "updatedAt"
        FROM supplier_api_connection connection
        LEFT JOIN supplier ON supplier.id=connection.supplier_id
        ORDER BY connection.enabled DESC, connection.updated_at DESC, connection.id DESC
        """);
  }

  public Map<String, Object> connection(Long connectionId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT id, connection_no AS "connectionNo", supplier_id AS "supplierId",
               connection_name AS "connectionName", provider_code AS "providerCode",
               connection_kind AS "connectionKind", environment, base_url AS "baseUrl",
               auth_type AS "authType", auth_header_name AS "authHeaderName",
               credential_env_key AS "credentialEnvKey",
               data_scope AS "dataScope", media_search_path AS "mediaSearchPath",
               reporter_search_path AS "reporterSearchPath",
               quote_path AS "quotePath", order_path AS "orderPath",
               order_status_path AS "orderStatusPath", callback_path AS "callbackPath",
               reconciliation_path AS "reconciliationPath",
               sla_reference AS "slaReference",
               authorization_status AS "authorizationStatus",
               sandbox_status AS "sandboxStatus", production_status AS "productionStatus",
               enabled
        FROM supplier_api_connection
        WHERE id=?
        """, connectionId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public boolean supplierExists(Long supplierId) {
    if (supplierId == null) return true;
    Long count = jdbc.queryForObject(
        "SELECT count(*) FROM supplier WHERE id=?", Long.class, supplierId);
    return count != null && count > 0;
  }

  public Long createConnection(
      AuthPrincipal user, SaveSupplierApiConnectionRequest request) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO supplier_api_connection
          (connection_no, supplier_id, connection_name, provider_code, connection_kind,
           environment, base_url, auth_type, auth_header_name, credential_env_key,
           capability_scope, media_search_path, reporter_search_path, quote_path,
           order_path, order_status_path, callback_path, reconciliation_path,
           sla_reference, rate_limit_per_minute,
           timeout_seconds, max_retries, data_scope, contract_reference,
           authorization_status, authorization_evidence_ref, sandbox_status,
           sandbox_evidence_ref, production_status, production_evidence_ref,
           internal_note, enabled, created_by, updated_by)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      int index = 1;
      statement.setString(index++, WorkflowRepository.no("API"));
      set(statement, index++, request.supplierId());
      statement.setString(index++, request.connectionName().trim());
      statement.setString(index++, request.providerCode().trim().toUpperCase(Locale.ROOT));
      statement.setString(index++, request.connectionKind());
      statement.setString(index++, request.environment());
      statement.setString(index++, request.baseUrl().trim());
      statement.setString(index++, request.authType());
      set(statement, index++, blankToNull(request.authHeaderName()));
      set(statement, index++, blankToNull(request.credentialEnvKey()));
      set(statement, index++, blankToNull(request.capabilityScope()));
      set(statement, index++, blankToNull(request.mediaSearchPath()));
      set(statement, index++, blankToNull(request.reporterSearchPath()));
      set(statement, index++, blankToNull(request.quotePath()));
      set(statement, index++, blankToNull(request.orderPath()));
      set(statement, index++, blankToNull(request.orderStatusPath()));
      set(statement, index++, blankToNull(request.callbackPath()));
      set(statement, index++, blankToNull(request.reconciliationPath()));
      set(statement, index++, blankToNull(request.slaReference()));
      statement.setInt(index++, request.rateLimitPerMinute());
      statement.setInt(index++, request.timeoutSeconds());
      statement.setInt(index++, request.maxRetries());
      set(statement, index++, blankToNull(request.dataScope()));
      set(statement, index++, blankToNull(request.contractReference()));
      statement.setString(index++, request.authorizationStatus());
      set(statement, index++, blankToNull(request.authorizationEvidenceRef()));
      statement.setString(index++, request.sandboxStatus());
      set(statement, index++, blankToNull(request.sandboxEvidenceRef()));
      statement.setString(index++, request.productionStatus());
      set(statement, index++, blankToNull(request.productionEvidenceRef()));
      set(statement, index++, blankToNull(request.internalNote()));
      statement.setBoolean(index++, request.enabled());
      statement.setLong(index++, user.userId());
      statement.setLong(index, user.userId());
      return statement;
    }, keyHolder);
    Number key = keyHolder.getKey();
    return key == null ? null : key.longValue();
  }

  public boolean updateConnection(
      AuthPrincipal user, Long connectionId, SaveSupplierApiConnectionRequest request) {
    return jdbc.update("""
        UPDATE supplier_api_connection
        SET supplier_id=?, connection_name=?, provider_code=?, connection_kind=?,
            environment=?, base_url=?, auth_type=?, auth_header_name=?,
            credential_env_key=?, capability_scope=?, media_search_path=?,
            reporter_search_path=?, quote_path=?, order_path=?, order_status_path=?,
            callback_path=?, reconciliation_path=?, sla_reference=?,
            rate_limit_per_minute=?, timeout_seconds=?, max_retries=?,
            data_scope=?, contract_reference=?, authorization_status=?,
            authorization_evidence_ref=?, sandbox_status=?, sandbox_evidence_ref=?,
            production_status=?, production_evidence_ref=?, internal_note=?,
            enabled=?, updated_by=?
        WHERE id=?
        """,
        request.supplierId(), request.connectionName().trim(),
        request.providerCode().trim().toUpperCase(Locale.ROOT), request.connectionKind(),
        request.environment(), request.baseUrl().trim(), request.authType(),
        blankToNull(request.authHeaderName()), blankToNull(request.credentialEnvKey()),
        blankToNull(request.capabilityScope()), blankToNull(request.mediaSearchPath()),
        blankToNull(request.reporterSearchPath()), blankToNull(request.quotePath()),
        blankToNull(request.orderPath()), blankToNull(request.orderStatusPath()),
        blankToNull(request.callbackPath()), blankToNull(request.reconciliationPath()),
        blankToNull(request.slaReference()), request.rateLimitPerMinute(),
        request.timeoutSeconds(), request.maxRetries(), blankToNull(request.dataScope()),
        blankToNull(request.contractReference()), request.authorizationStatus(),
        blankToNull(request.authorizationEvidenceRef()), request.sandboxStatus(),
        blankToNull(request.sandboxEvidenceRef()), request.productionStatus(),
        blankToNull(request.productionEvidenceRef()), blankToNull(request.internalNote()),
        request.enabled(), user.userId(), connectionId) > 0;
  }

  public void saveConfigurationCheck(
      AuthPrincipal user, Long connectionId, String status, String detail) {
    jdbc.update("""
        UPDATE supplier_api_connection
        SET last_config_checked_at=CURRENT_TIMESTAMP,
            last_config_check_status=?, last_config_check_detail=?, updated_by=?
        WHERE id=?
        """, status, detail, user.userId(), connectionId);
  }

  public List<Map<String, Object>> acceptanceGates() {
    return jdbc.queryForList("""
        SELECT gate.id, gate.gate_code AS "gateCode", gate.gate_name AS "gateName",
               gate.status, gate.evidence_reference AS "evidenceReference",
               gate.review_note AS "reviewNote", gate.reviewed_at AS "reviewedAt",
               reviewer.display_name AS "reviewedBy", gate.updated_at AS "updatedAt",
               count(item.id) FILTER (WHERE item.required) AS "requiredItemCount",
               count(item.id) FILTER (
                 WHERE item.required AND item.item_status='VERIFIED'
               ) AS "verifiedRequiredItemCount",
               count(item.id) FILTER (
                 WHERE item.required AND item.item_status<>'VERIFIED'
               ) AS "pendingRequiredItemCount"
        FROM platform_acceptance_gate gate
        LEFT JOIN app_user reviewer ON reviewer.id=gate.reviewed_by
        LEFT JOIN platform_acceptance_evidence_item item ON item.gate_code=gate.gate_code
        GROUP BY gate.id, reviewer.display_name
        ORDER BY gate.id
        """);
  }

  public List<Map<String, Object>> acceptanceEvidenceItems() {
    return jdbc.queryForList("""
        SELECT item.id, item.gate_code AS "gateCode", item.item_code AS "itemCode",
               item.item_name AS "itemName", item.required,
               item.item_status AS "itemStatus",
               item.evidence_reference AS "evidenceReference",
               item.review_note AS "reviewNote",
               item.reviewed_at AS "reviewedAt",
               reviewer.display_name AS "reviewedBy",
               item.updated_at AS "updatedAt"
        FROM platform_acceptance_evidence_item item
        LEFT JOIN app_user reviewer ON reviewer.id=item.reviewed_by
        ORDER BY item.gate_code, item.id
        """);
  }

  public Map<String, Object> acceptanceEvidenceItem(Long evidenceItemId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT id, gate_code AS "gateCode", item_code AS "itemCode",
               item_name AS "itemName", required, item_status AS "itemStatus"
        FROM platform_acceptance_evidence_item
        WHERE id=?
        """, evidenceItemId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public boolean updateAcceptanceEvidenceItem(
      AuthPrincipal user, Long evidenceItemId, UpdateAcceptanceEvidenceRequest request) {
    return jdbc.update("""
        UPDATE platform_acceptance_evidence_item
        SET item_status=?, evidence_reference=?, review_note=?, reviewed_by=?,
            reviewed_at=CASE WHEN ? IN ('VERIFIED','REJECTED','NOT_APPLICABLE')
              THEN CURRENT_TIMESTAMP ELSE NULL END
        WHERE id=?
        """, request.itemStatus(), blankToNull(request.evidenceReference()),
        blankToNull(request.reviewNote()), user.userId(), request.itemStatus(),
        evidenceItemId) > 0;
  }

  public long pendingRequiredEvidenceCount(String gateCode) {
    Long count = jdbc.queryForObject("""
        SELECT count(*)
        FROM platform_acceptance_evidence_item
        WHERE gate_code=? AND required AND item_status<>'VERIFIED'
        """, Long.class, gateCode);
    return count == null ? 0 : count;
  }

  public boolean acceptanceGateExists(String gateCode) {
    Long count = jdbc.queryForObject(
        "SELECT count(*) FROM platform_acceptance_gate WHERE gate_code=?",
        Long.class, gateCode);
    return count != null && count > 0;
  }

  public boolean isExternalMediaDataOperational(String providerCode) {
    Long count = jdbc.queryForObject("""
        SELECT count(*)
        FROM supplier_api_connection connection
        JOIN platform_acceptance_gate gate
          ON gate.gate_code='EXTERNAL_MEDIA_DATA' AND gate.status='PASSED'
        WHERE connection.connection_kind='MEDIA_DATA'
          AND connection.environment='PRODUCTION'
          AND connection.enabled
          AND connection.provider_code=?
          AND connection.authorization_status='VERIFIED'
          AND connection.sandbox_status='PASSED'
          AND connection.production_status='APPROVED'
          AND NULLIF(btrim(connection.data_scope), '') IS NOT NULL
          AND (
            NULLIF(btrim(connection.media_search_path), '') IS NOT NULL
            OR NULLIF(btrim(connection.reporter_search_path), '') IS NOT NULL
          )
          AND NOT EXISTS (
            SELECT 1
            FROM platform_acceptance_evidence_item item
            WHERE item.gate_code='EXTERNAL_MEDIA_DATA'
              AND item.required
              AND item.item_status<>'VERIFIED'
          )
        """, Long.class, providerCode.toUpperCase(Locale.ROOT));
    return count != null && count > 0;
  }

  public long pendingLegacyReviewCount() {
    Long count = jdbc.queryForObject("""
        SELECT count(*)
        FROM legacy_service_review
        WHERE review_status NOT IN ('APPROVED','REJECTED')
        """, Long.class);
    return count == null ? 0 : count;
  }

  public boolean updateAcceptanceGate(
      AuthPrincipal user, String gateCode, UpdateAcceptanceGateRequest request) {
    return jdbc.update("""
        UPDATE platform_acceptance_gate
        SET status=?, evidence_reference=?, review_note=?, reviewed_by=?,
            reviewed_at=CASE WHEN ? IN ('PASSED','BLOCKED') THEN CURRENT_TIMESTAMP ELSE NULL END
        WHERE gate_code=?
        """, request.status(), blankToNull(request.evidenceReference()),
        blankToNull(request.reviewNote()), user.userId(), request.status(), gateCode) > 0;
  }

  public List<Map<String, Object>> legacyServiceReviews() {
    return jdbc.queryForList("""
        SELECT review.id, review.review_no AS "reviewNo",
               review.requirement_id AS "requirementId",
               requirement.requirement_no AS "requirementNo",
               requirement.title, requirement.status AS "requirementStatus",
               organization.name AS "organizationName",
               review.original_service_type AS "originalServiceType",
               review.review_status AS "reviewStatus",
               review.approved_action AS "approvedAction",
               review.evidence_reference AS "evidenceReference",
               review.business_note AS "businessNote",
               review.reviewed_at AS "reviewedAt",
               reviewer.display_name AS "reviewedBy",
               string_agg(DISTINCT project.project_no, ', ' ORDER BY project.project_no)
                 FILTER (WHERE project.project_no IS NOT NULL) AS "projectNos",
               count(DISTINCT task.id) AS "taskCount",
               count(DISTINCT settlement.id) AS "settlementCount",
               review.updated_at AS "updatedAt"
        FROM legacy_service_review review
        JOIN customer_requirement requirement ON requirement.id=review.requirement_id
        JOIN organization ON organization.id=requirement.organization_id
        LEFT JOIN project ON project.requirement_id=requirement.id
        LEFT JOIN publish_task task ON task.project_id=project.id
        LEFT JOIN settlement_order settlement ON settlement.project_id=project.id
        LEFT JOIN app_user reviewer ON reviewer.id=review.reviewed_by
        GROUP BY review.id, requirement.id, organization.name, reviewer.display_name
        ORDER BY review.review_status, review.updated_at DESC, review.id DESC
        LIMIT 1000
        """);
  }

  public boolean updateLegacyServiceReview(
      AuthPrincipal user, Long reviewId, UpdateLegacyServiceReviewRequest request) {
    return jdbc.update("""
        UPDATE legacy_service_review
        SET review_status=?, approved_action=?, evidence_reference=?, business_note=?,
            reviewed_by=?,
            reviewed_at=CASE WHEN ? IN ('APPROVED','REJECTED') THEN CURRENT_TIMESTAMP ELSE NULL END
        WHERE id=?
        """, request.reviewStatus(), blankToNull(request.approvedAction()),
        blankToNull(request.evidenceReference()), blankToNull(request.businessNote()),
        user.userId(), request.reviewStatus(), reviewId) > 0;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static void set(PreparedStatement statement, int index, Object value)
      throws java.sql.SQLException {
    statement.setObject(index, value);
  }
}
