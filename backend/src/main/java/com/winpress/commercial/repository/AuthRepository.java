package com.winpress.commercial.repository;

import com.winpress.commercial.dto.AuthDtos.RegisterRequest;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.security.AuthPrincipal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuthRepository {
  private final JdbcTemplate jdbc;

  public AuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Map<String, Object> findLogin(String username) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT u.id, u.user_no, u.organization_id, o.name AS organization_name, u.username,
               u.password_hash, u.display_name, u.mobile, u.email, u.status,
               r.role_code
        FROM app_user u
        JOIN organization o ON o.id = u.organization_id
        JOIN user_role ur ON ur.user_id = u.id AND ur.status = 'ACTIVE'
        JOIN sys_role r ON r.id = ur.role_id AND r.status = 'ACTIVE'
        WHERE lower(u.username) = lower(?) AND o.status = 'ACTIVE'
        ORDER BY r.id
        """, username);
    if (rows.size() != 1) {
      throw new BusinessException("INVALID_CREDENTIALS", "账号或密码不正确", HttpStatus.UNAUTHORIZED);
    }
    return rows.get(0);
  }

  /**
   * Reloads the current account and role state for an existing session.
   *
   * <p>A session is valid only while its user, organization and exactly one assigned role remain
   * active. Returning {@code null} deliberately fails closed when an account is suspended or its
   * role assignment is ambiguous.</p>
   */
  public AuthPrincipal activePrincipalById(Long userId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT u.id, u.user_no, u.organization_id, o.name AS organization_name, u.username,
               u.display_name, u.mobile, u.email, r.role_code
        FROM app_user u
        JOIN organization o ON o.id = u.organization_id AND o.status = 'ACTIVE'
        JOIN user_role ur ON ur.user_id = u.id AND ur.status = 'ACTIVE'
        JOIN sys_role r ON r.id = ur.role_id AND r.status = 'ACTIVE'
        WHERE u.id = ? AND u.status = 'ACTIVE'
        ORDER BY r.id
        """, userId);
    return rows.size() == 1 ? principalFrom(rows.get(0)) : null;
  }

  public List<String> permissions(Long userId) {
    return jdbc.queryForList("""
        SELECT DISTINCT p.permission_code
        FROM user_role ur
        JOIN role_permission rp ON rp.role_id = ur.role_id AND rp.status = 'ACTIVE'
        JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 'ACTIVE'
        WHERE ur.user_id = ? AND ur.status = 'ACTIVE'
        ORDER BY p.permission_code
        """, String.class, userId);
  }

  public void updateLastLogin(Long userId) {
    jdbc.update("UPDATE app_user SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
  }

  public boolean usernameExists(String username) {
    Integer count = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE lower(username)=lower(?)", Integer.class, username);
    return count != null && count > 0;
  }

  @Transactional
  public AuthPrincipal register(RegisterRequest request, String passwordHash) {
    if (usernameExists(request.username())) {
      throw new BusinessException("USERNAME_EXISTS", "该账号已注册", HttpStatus.CONFLICT);
    }
    KeyHolder orgKey = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO organization (organization_no, name, organization_type, contact_name, contact_phone, contact_email, status)
          VALUES (?, ?, 'CUSTOMER', ?, ?, ?, 'ACTIVE')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, "ORG-" + compactId());
      ps.setString(2, request.organizationName());
      ps.setString(3, request.displayName());
      ps.setString(4, request.mobile());
      ps.setString(5, request.email());
      return ps;
    }, orgKey);
    Long organizationId = ((Number) orgKey.getKeys().get("id")).longValue();

    KeyHolder userKey = new GeneratedKeyHolder();
    String userNo = "USR-" + compactId();
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO app_user (user_no, organization_id, username, password_hash, display_name, mobile, email, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, userNo);
      ps.setLong(2, organizationId);
      ps.setString(3, request.username());
      ps.setString(4, passwordHash);
      ps.setString(5, request.displayName());
      ps.setString(6, request.mobile());
      ps.setString(7, request.email());
      return ps;
    }, userKey);
    Long userId = ((Number) userKey.getKeys().get("id")).longValue();
    jdbc.update("""
        INSERT INTO user_role (user_id, role_id, status)
        SELECT ?, id, 'ACTIVE' FROM sys_role WHERE role_code = 'CUSTOMER'
        """, userId);
    return new AuthPrincipal(userId, userNo, organizationId, request.organizationName(), request.username(),
        request.displayName(), request.mobile(), request.email(), "CUSTOMER", permissions(userId));
  }

  public AuthPrincipal principalFrom(Map<String, Object> row) {
    Long userId = ((Number) row.get("id")).longValue();
    return new AuthPrincipal(userId, String.valueOf(row.get("user_no")),
        ((Number) row.get("organization_id")).longValue(), String.valueOf(row.get("organization_name")),
        String.valueOf(row.get("username")), String.valueOf(row.get("display_name")),
        String.valueOf(row.get("mobile")), String.valueOf(row.get("email")),
        String.valueOf(row.get("role_code")), permissions(userId));
  }

  public List<Map<String, Object>> users(String role, String status, int limit, int offset) {
    StringBuilder sql = new StringBuilder("""
        SELECT u.id, u.user_no AS "userNo", u.username, u.display_name AS "displayName", u.mobile, u.email,
               o.name AS "organizationName", r.role_code AS role, u.status, u.last_login_at AS "lastLoginAt",
               u.created_at AS "createdAt"
        FROM app_user u JOIN organization o ON o.id=u.organization_id
        JOIN user_role ur ON ur.user_id=u.id AND ur.status='ACTIVE'
        JOIN sys_role r ON r.id=ur.role_id
        WHERE 1=1
        """);
    List<Object> params = new ArrayList<>();
    if (hasText(role)) {
      sql.append(" AND r.role_code=?");
      params.add(role);
    }
    if (hasText(status)) {
      sql.append(" AND u.status=?");
      params.add(status);
    }
    sql.append(" ORDER BY u.created_at DESC LIMIT ? OFFSET ?");
    params.add(limit);
    params.add(offset);
    return jdbc.queryForList(sql.toString(), params.toArray());
  }

  public long usersCount(String role, String status) {
    StringBuilder sql = new StringBuilder("""
        SELECT count(*) FROM app_user u JOIN user_role ur ON ur.user_id=u.id AND ur.status='ACTIVE'
        JOIN sys_role r ON r.id=ur.role_id
        WHERE 1=1
        """);
    List<Object> params = new ArrayList<>();
    if (hasText(role)) {
      sql.append(" AND r.role_code=?");
      params.add(role);
    }
    if (hasText(status)) {
      sql.append(" AND u.status=?");
      params.add(status);
    }
    Long count = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
    return count == null ? 0 : count;
  }

  public List<Map<String, Object>> roles() {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT r.role_code AS "roleCode", r.role_name AS "roleName",
               string_agg(p.permission_code, ',' ORDER BY p.permission_code) AS "permissionCsv"
        FROM sys_role r LEFT JOIN role_permission rp ON rp.role_id=r.id AND rp.status='ACTIVE'
        LEFT JOIN sys_permission p ON p.id=rp.permission_id AND p.status='ACTIVE'
        WHERE r.status='ACTIVE' GROUP BY r.id, r.role_code, r.role_name ORDER BY r.id
        """);
    return rows.stream().map(row -> {
      Map<String, Object> result = new LinkedHashMap<>(row);
      Object csv = result.remove("permissionCsv");
      result.put("permissions", csv == null || csv.toString().isBlank()
          ? List.of() : Arrays.asList(csv.toString().split(",")));
      return result;
    }).toList();
  }

  @Transactional
  public void updateUser(Long userId, String role, String status) {
    int userUpdated = jdbc.update("UPDATE app_user SET status=? WHERE id=?", status, userId);
    if (userUpdated != 1) {
      throw new BusinessException("USER_NOT_FOUND", "账号不存在", HttpStatus.NOT_FOUND);
    }
    jdbc.update("UPDATE user_role SET status='INACTIVE' WHERE user_id=?", userId);
    int roleUpdated = jdbc.update("""
        INSERT INTO user_role (user_id, role_id, status)
        SELECT ?, id, 'ACTIVE' FROM sys_role WHERE role_code=?
        ON CONFLICT (user_id, role_id) DO UPDATE SET status='ACTIVE', updated_at=CURRENT_TIMESTAMP
        """, userId, role);
    if (roleUpdated != 1) {
      throw new IllegalStateException("Configured role is unavailable");
    }
  }

  private static String compactId() {
    return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
