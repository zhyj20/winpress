package com.winpress.commercial.service;

import com.winpress.commercial.dto.AuthDtos.LoginRequest;
import com.winpress.commercial.dto.AuthDtos.LoginResponse;
import com.winpress.commercial.dto.AuthDtos.RegisterRequest;
import com.winpress.commercial.dto.AuthDtos.UserView;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.AuthRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import com.winpress.commercial.security.LoginAttemptLimiter;
import com.winpress.commercial.security.SessionService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final AuthRepository repository;
  private final SessionService sessions;
  private final LoginAttemptLimiter loginAttempts;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

  public AuthService(AuthRepository repository, SessionService sessions, LoginAttemptLimiter loginAttempts) {
    this.repository = repository;
    this.sessions = sessions;
    this.loginAttempts = loginAttempts;
  }

  public LoginResponse login(LoginRequest request) {
    return login(request, "unknown");
  }

  public LoginResponse login(LoginRequest request, String clientSource) {
    loginAttempts.check(clientSource);
    Map<String, Object> row;
    try {
      row = repository.findLogin(request.username().trim());
    } catch (BusinessException exception) {
      if ("INVALID_CREDENTIALS".equals(exception.getCode())) loginAttempts.recordFailure(clientSource);
      throw exception;
    }
    if (row == null || row.get("id") == null || row.get("status") == null || row.get("password_hash") == null) {
      loginAttempts.recordFailure(clientSource);
      throw new BusinessException("INVALID_CREDENTIALS", "账号或密码不正确", HttpStatus.UNAUTHORIZED);
    }
    String status = String.valueOf(row.get("status"));
    String encodedPassword = String.valueOf(row.get("password_hash"));
    if (!"ACTIVE".equalsIgnoreCase(status) || !encoder.matches(request.password(), encodedPassword)) {
      loginAttempts.recordFailure(clientSource);
      throw new BusinessException("INVALID_CREDENTIALS", "账号或密码不正确", HttpStatus.UNAUTHORIZED);
    }
    AuthPrincipal principal = repository.principalFrom(row);
    repository.updateLastLogin(principal.userId());
    loginAttempts.recordSuccess(clientSource);
    return new LoginResponse(sessions.create(principal), toView(principal));
  }

  public LoginResponse register(RegisterRequest request) {
    AuthPrincipal principal = repository.register(request, encoder.encode(request.password()));
    return new LoginResponse(sessions.create(principal), toView(principal));
  }

  public UserView me() { return toView(CurrentUser.get()); }

  public void logout(String authorization) {
    if (authorization != null && authorization.startsWith("Bearer ")) {
      sessions.revoke(authorization.substring(7).trim());
    }
  }

  private UserView toView(AuthPrincipal principal) {
    return new UserView(principal.userId(), principal.userNo(), principal.organizationId(), principal.organizationName(),
        principal.username(), principal.displayName(), principal.mobile(), principal.email(),
        principal.role(), principal.permissions());
  }
}
