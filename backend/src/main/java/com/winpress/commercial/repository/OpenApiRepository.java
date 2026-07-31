package com.winpress.commercial.repository;

import com.winpress.commercial.dto.OpenApiDtos.SaveOpenApiApplicationRequest;
import com.winpress.commercial.security.AuthPrincipal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Persistence boundary for customer Open API applications. It intentionally never returns a raw
 * access key or a stored key hash to the administrative UI.
 */
@Repository
public class OpenApiRepository {
  private final JdbcTemplate jdbc;

  public OpenApiRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> applications() {
    return jdbc.queryForList("""
        SELECT application.id, application.application_no AS "applicationNo",
               application.application_name AS "applicationName",
               application.client_code AS "clientCode",
               application.customer_user_id AS "customerUserId",
               customer.display_name AS "customerName", customer.username AS "customerUsername",
               organization.name AS "organizationName", application.environment,
               application.service_scopes AS "serviceScopes",
               application.rate_limit_per_minute AS "rateLimitPerMinute",
               application.authorization_status AS "authorizationStatus",
               application.authorization_evidence_ref AS "authorizationEvidenceRef",
               application.sandbox_status AS "sandboxStatus",
               application.sandbox_evidence_ref AS "sandboxEvidenceRef",
               application.production_status AS "productionStatus",
               application.production_evidence_ref AS "productionEvidenceRef",
               application.contract_reference AS "contractReference",
               application.internal_note AS "internalNote", application.status,
               application.created_at AS "createdAt", application.updated_at AS "updatedAt",
               (SELECT count(*) FROM open_api_access_key key
                 WHERE key.application_id=application.id AND key.status='ACTIVE'
                   AND (key.expires_at IS NULL OR key.expires_at>CURRENT_TIMESTAMP)) AS "activeKeyCount",
               (SELECT max(key.last_used_at) FROM open_api_access_key key
                 WHERE key.application_id=application.id) AS "lastKeyUsedAt",
               (SELECT count(*) FROM open_api_access_log log
                 WHERE log.application_id=application.id
                   AND log.created_at>=CURRENT_TIMESTAMP - INTERVAL '24 hours') AS "last24hRequestCount"
        FROM open_api_application application
        JOIN app_user customer ON customer.id=application.customer_user_id
        JOIN organization ON organization.id=customer.organization_id
        ORDER BY application.updated_at DESC, application.id DESC
        """);
  }

  public Map<String, Object> application(Long applicationId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT application.id, application.application_no AS "applicationNo",
               application.application_name AS "applicationName",
               application.client_code AS "clientCode",
               application.customer_user_id AS "customerUserId",
               customer.display_name AS "customerName", customer.username AS "customerUsername",
               organization.name AS "organizationName", application.environment,
               application.service_scopes AS "serviceScopes",
               application.rate_limit_per_minute AS "rateLimitPerMinute",
               application.authorization_status AS "authorizationStatus",
               application.authorization_evidence_ref AS "authorizationEvidenceRef",
               application.sandbox_status AS "sandboxStatus",
               application.sandbox_evidence_ref AS "sandboxEvidenceRef",
               application.production_status AS "productionStatus",
               application.production_evidence_ref AS "productionEvidenceRef",
               application.contract_reference AS "contractReference",
               application.internal_note AS "internalNote", application.status,
               application.created_at AS "createdAt", application.updated_at AS "updatedAt"
        FROM open_api_application application
        JOIN app_user customer ON customer.id=application.customer_user_id
        JOIN organization ON organization.id=customer.organization_id
        WHERE application.id=?
        """, applicationId);
    return rows.isEmpty() ? Map.of() : rows.get(0);
  }

  public Map<String, Object> customerOwner(Long userId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT customer.id, customer.organization_id AS "organizationId",
               customer.display_name AS "displayName", customer.username,
               organization.name AS "organizationName"
        FROM app_user customer
        JOIN organization ON organization.id=customer.organization_id AND organization.status='ACTIVE'
        JOIN user_role assignment ON assignment.user_id=customer.id AND assignment.status='ACTIVE'
        JOIN sys_role role ON role.id=assignment.role_id AND role.status='ACTIVE'
        WHERE customer.id=? AND customer.status='ACTIVE' AND role.role_code='CUSTOMER'
        """, userId);
    return rows.size() == 1 ? rows.get(0) : Map.of();
  }

  public List<Map<String, Object>> customerOwners() {
    return jdbc.queryForList("""
        SELECT customer.id, customer.display_name AS "displayName", customer.username,
               organization.name AS "organizationName"
        FROM app_user customer
        JOIN organization ON organization.id=customer.organization_id AND organization.status='ACTIVE'
        JOIN user_role assignment ON assignment.user_id=customer.id AND assignment.status='ACTIVE'
        JOIN sys_role role ON role.id=assignment.role_id AND role.status='ACTIVE'
        WHERE customer.status='ACTIVE' AND role.role_code='CUSTOMER'
        ORDER BY organization.name, customer.display_name, customer.id
        """);
  }

  public Long createApplication(
      AuthPrincipal admin, String applicationNo, SaveOpenApiApplicationRequest request, String scopes) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO open_api_application
          (application_no, application_name, client_code, customer_user_id, environment,
           service_scopes, rate_limit_per_minute, authorization_status,
           authorization_evidence_ref, sandbox_status, sandbox_evidence_ref,
           production_status, production_evidence_ref, contract_reference, internal_note,
           status, created_by, updated_by)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setString(1, applicationNo);
      statement.setString(2, request.applicationName().trim());
      statement.setString(3, request.clientCode().trim());
      statement.setLong(4, request.customerUserId());
      statement.setString(5, request.environment());
      statement.setString(6, scopes);
      statement.setInt(7, request.rateLimitPerMinute());
      statement.setString(8, request.authorizationStatus());
      set(statement, 9, blankToNull(request.authorizationEvidenceRef()));
      statement.setString(10, request.sandboxStatus());
      set(statement, 11, blankToNull(request.sandboxEvidenceRef()));
      statement.setString(12, request.productionStatus());
      set(statement, 13, blankToNull(request.productionEvidenceRef()));
      set(statement, 14, blankToNull(request.contractReference()));
      set(statement, 15, blankToNull(request.internalNote()));
      statement.setString(16, request.status());
      statement.setLong(17, admin.userId());
      statement.setLong(18, admin.userId());
      return statement;
    }, keyHolder);
    return generatedId(keyHolder);
  }

  public boolean updateApplication(
      AuthPrincipal admin, Long applicationId, SaveOpenApiApplicationRequest request, String scopes) {
    return jdbc.update("""
        UPDATE open_api_application
        SET application_name=?, client_code=?, customer_user_id=?, environment=?, service_scopes=?,
            rate_limit_per_minute=?, authorization_status=?, authorization_evidence_ref=?,
            sandbox_status=?, sandbox_evidence_ref=?, production_status=?, production_evidence_ref=?,
            contract_reference=?, internal_note=?, status=?, updated_by=?
        WHERE id=?
        """, request.applicationName().trim(), request.clientCode().trim(), request.customerUserId(),
        request.environment(), scopes, request.rateLimitPerMinute(), request.authorizationStatus(),
        blankToNull(request.authorizationEvidenceRef()), request.sandboxStatus(),
        blankToNull(request.sandboxEvidenceRef()), request.productionStatus(),
        blankToNull(request.productionEvidenceRef()), blankToNull(request.contractReference()),
        blankToNull(request.internalNote()), request.status(), admin.userId(), applicationId) > 0;
  }

  public List<Map<String, Object>> accessKeys() {
    return jdbc.queryForList("""
        SELECT key.id, key.key_no AS "keyNo", key.application_id AS "applicationId",
               application.application_no AS "applicationNo",
               application.application_name AS "applicationName",
               application.environment, key.key_label AS "keyLabel", key.key_prefix AS "keyPrefix",
               key.status, key.expires_at AS "expiresAt", key.last_used_at AS "lastUsedAt",
               key.revoked_at AS "revokedAt", key.created_at AS "createdAt"
        FROM open_api_access_key key
        JOIN open_api_application application ON application.id=key.application_id
        ORDER BY key.created_at DESC, key.id DESC
        LIMIT 1000
        """);
  }

  public Long createAccessKey(
      AuthPrincipal admin, Long applicationId, String keyNo, String keyLabel, String keyPrefix,
      String keyHash, OffsetDateTime expiresAt) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO open_api_access_key
          (key_no, application_id, key_label, key_prefix, key_hash, expires_at, created_by)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setString(1, keyNo);
      statement.setLong(2, applicationId);
      statement.setString(3, keyLabel);
      statement.setString(4, keyPrefix);
      statement.setString(5, keyHash);
      statement.setObject(6, expiresAt);
      statement.setLong(7, admin.userId());
      return statement;
    }, keyHolder);
    return generatedId(keyHolder);
  }

  public boolean revokeAccessKey(AuthPrincipal admin, Long keyId) {
    return jdbc.update("""
        UPDATE open_api_access_key
        SET status='REVOKED', revoked_at=CURRENT_TIMESTAMP, revoked_by=?
        WHERE id=? AND status='ACTIVE'
        """, admin.userId(), keyId) > 0;
  }

  public int expireDueAccessKeys() {
    return jdbc.update("""
        UPDATE open_api_access_key
        SET status='EXPIRED'
        WHERE status='ACTIVE' AND expires_at IS NOT NULL AND expires_at<=CURRENT_TIMESTAMP
        """);
  }

  public Map<String, Object> activeKeyPrincipal(String keyHash) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT key.id AS "accessKeyId", key.application_id AS "applicationId",
               application.application_no AS "applicationNo",
               application.application_name AS "applicationName",
               application.customer_user_id AS "customerUserId",
               application.service_scopes AS "serviceScopes",
               application.rate_limit_per_minute AS "rateLimitPerMinute",
               application.environment, application.status AS "applicationStatus",
               key.status AS "keyStatus", key.expires_at AS "expiresAt"
        FROM open_api_access_key key
        JOIN open_api_application application ON application.id=key.application_id
        JOIN app_user customer
          ON customer.id=application.customer_user_id AND customer.status='ACTIVE'
        JOIN organization organization
          ON organization.id=customer.organization_id AND organization.status='ACTIVE'
        WHERE key.key_hash=? AND key.status='ACTIVE' AND application.status='ACTIVE'
          AND (key.expires_at IS NULL OR key.expires_at>CURRENT_TIMESTAMP)
          AND EXISTS (
            SELECT 1
            FROM user_role assignment
            JOIN sys_role role ON role.id=assignment.role_id AND role.status='ACTIVE'
            WHERE assignment.user_id=customer.id
              AND assignment.status='ACTIVE'
              AND role.role_code='CUSTOMER'
          )
        """, keyHash);
    return rows.size() == 1 ? rows.get(0) : Map.of();
  }

  public void markAccessKeyUsed(Long keyId) {
    jdbc.update("UPDATE open_api_access_key SET last_used_at=CURRENT_TIMESTAMP WHERE id=?", keyId);
  }

  public Map<String, Object> receipt(Long applicationId, String externalRequestId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT receipt.id, receipt.external_request_id AS "externalRequestId",
               receipt.request_hash AS "requestHash", receipt.service_type AS "serviceType",
               receipt.status AS "receiptStatus", receipt.created_at AS "createdAt",
               requirement.requirement_no AS "requirementNo", project.id AS "projectId",
               project.project_no AS "projectNo", project.project_name AS "projectName",
               project.status AS "projectStatus", project.updated_at AS "projectUpdatedAt"
        FROM open_api_request_receipt receipt
        JOIN customer_requirement requirement ON requirement.id=receipt.requirement_id
        JOIN project ON project.id=receipt.project_id
        WHERE receipt.application_id=? AND receipt.external_request_id=?
        """, applicationId, externalRequestId);
    return rows.isEmpty() ? Map.of() : rows.get(0);
  }

  public List<Map<String, Object>> receipts(Long applicationId, int limit) {
    return jdbc.queryForList("""
        SELECT receipt.external_request_id AS "externalRequestId",
               receipt.service_type AS "serviceType", receipt.status AS "receiptStatus",
               receipt.created_at AS "createdAt", requirement.requirement_no AS "requirementNo",
               project.project_no AS "projectNo", project.project_name AS "projectName",
               project.status AS "projectStatus", project.updated_at AS "projectUpdatedAt"
        FROM open_api_request_receipt receipt
        JOIN customer_requirement requirement ON requirement.id=receipt.requirement_id
        JOIN project ON project.id=receipt.project_id
        WHERE receipt.application_id=?
        ORDER BY receipt.created_at DESC, receipt.id DESC
        LIMIT ?
        """, applicationId, Math.max(1, Math.min(limit, 100)));
  }

  public Map<String, Object> projectReference(Long projectId, Long customerUserId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT project.id AS "projectId", project.project_no AS "projectNo",
               project.project_name AS "projectName", project.status AS "projectStatus",
               project.requirement_id AS "requirementId", requirement.requirement_no AS "requirementNo"
        FROM project
        JOIN customer_requirement requirement ON requirement.id=project.requirement_id
        WHERE project.id=? AND project.customer_id=?
        """, projectId, customerUserId);
    return rows.size() == 1 ? rows.get(0) : Map.of();
  }

  public Long createReceipt(
      Long applicationId, String externalRequestId, String requestHash, Long requirementId,
      Long projectId, String serviceType, String status) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO open_api_request_receipt
          (application_id, external_request_id, request_hash, requirement_id, project_id,
           service_type, status)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setLong(1, applicationId);
      statement.setString(2, externalRequestId);
      statement.setString(3, requestHash);
      statement.setLong(4, requirementId);
      statement.setLong(5, projectId);
      statement.setString(6, serviceType);
      statement.setString(7, status);
      return statement;
    }, keyHolder);
    return generatedId(keyHolder);
  }

  public void accessLog(
      Long applicationId, Long accessKeyId, String externalRequestId, String operationCode,
      String requestHash, int responseStatus, String outcomeCode, Integer durationMillis) {
    jdbc.update("""
        INSERT INTO open_api_access_log
        (application_id, access_key_id, external_request_id, operation_code, request_hash,
         response_status, outcome_code, duration_millis)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, applicationId, accessKeyId, blankToNull(externalRequestId), operationCode,
        blankToNull(requestHash), responseStatus, outcomeCode, durationMillis);
  }

  public List<Map<String, Object>> accessLogs(int limit) {
    return jdbc.queryForList("""
        SELECT log.id, application.application_no AS "applicationNo",
               application.application_name AS "applicationName", key.key_prefix AS "keyPrefix",
               log.external_request_id AS "externalRequestId", log.operation_code AS "operationCode",
               log.response_status AS "responseStatus", log.outcome_code AS "outcomeCode",
               log.duration_millis AS "durationMillis", log.created_at AS "createdAt"
        FROM open_api_access_log log
        LEFT JOIN open_api_application application ON application.id=log.application_id
        LEFT JOIN open_api_access_key key ON key.id=log.access_key_id
        ORDER BY log.created_at DESC, log.id DESC
        LIMIT ?
        """, Math.max(1, Math.min(limit, 1000)));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static void set(PreparedStatement statement, int index, Object value)
      throws java.sql.SQLException {
    statement.setObject(index, value);
  }

  private static Long generatedId(KeyHolder keyHolder) {
    // PostgreSQL returns the full inserted row for RETURN_GENERATED_KEYS. Calling getKey() is
    // therefore unsafe here because Spring correctly rejects a multi-column key map.
    for (Map<String, Object> keys : keyHolder.getKeyList()) {
      Object value = keys.get("id");
      if (value instanceof Number number) return number.longValue();
    }
    throw new IllegalStateException("Unable to obtain generated Open API record id");
  }
}
