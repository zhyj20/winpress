package com.winpress.commercial.service;

import com.winpress.commercial.dto.WorkflowDtos.PageResult;
import com.winpress.commercial.dto.WorkflowDtos.UpdateUserRequest;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.AuthRepository;
import com.winpress.commercial.repository.WorkflowRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import com.winpress.commercial.security.SessionService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
  private final AuthRepository repository;
  private final WorkflowRepository workflowRepository;
  private final SessionService sessions;

  public AdminUserService(AuthRepository repository, WorkflowRepository workflowRepository, SessionService sessions) {
    this.repository = repository;
    this.workflowRepository = workflowRepository;
    this.sessions = sessions;
  }

  public PageResult<Map<String, Object>> users(String role, String status, int page, int pageSize) {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    int safePage = Math.max(1, page), safeSize = Math.min(100, Math.max(1, pageSize));
    return new PageResult<>(repository.users(role, status, safeSize, (safePage - 1) * safeSize),
        repository.usersCount(role, status), safePage, safeSize);
  }

  public List<Map<String, Object>> roles() {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    return repository.roles();
  }

  @Transactional
  public Map<String, Object> updateUser(Long userId, UpdateUserRequest request) {
    AuthPrincipal admin = CurrentUser.requireRole("PLATFORM_ADMIN");
    if (admin.userId().equals(userId)) {
      throw new BusinessException("SELF_ROLE_CHANGE_FORBIDDEN", "不能在当前会话中修改自己的角色或状态", HttpStatus.CONFLICT);
    }
    if (!Set.of("CUSTOMER", "PUBLISH_OPERATOR", "PLATFORM_ADMIN").contains(request.role()) ||
        !Set.of("ACTIVE", "SUSPENDED").contains(request.status())) {
      throw new BusinessException("INVALID_USER_SETTING", "账号角色或状态不正确", HttpStatus.BAD_REQUEST);
    }
    repository.updateUser(userId, request.role(), request.status());
    sessions.invalidateUser(userId);
    workflowRepository.log(admin, "UPDATE_USER_ACCESS", "USER", String.valueOf(userId),
        Map.of("role", request.role(), "status", request.status()));
    return Map.of("userId", userId, "role", request.role(), "status", request.status());
  }
}
