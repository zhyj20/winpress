package com.winpress.commercial.security;

import com.winpress.commercial.exception.BusinessException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;

public final class CurrentUser {
  private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

  private CurrentUser() {}

  public static void set(AuthPrincipal principal) { HOLDER.set(principal); }
  public static void clear() { HOLDER.remove(); }

  public static AuthPrincipal get() {
    AuthPrincipal principal = HOLDER.get();
    if (principal == null) {
      throw new BusinessException("UNAUTHORIZED", "请先登录", HttpStatus.UNAUTHORIZED);
    }
    return principal;
  }

  public static AuthPrincipal requireRole(String... roles) {
    AuthPrincipal principal = get();
    if (Arrays.stream(roles).noneMatch(principal.role()::equals)) {
      throw new BusinessException("FORBIDDEN", "当前账号无权执行此操作", HttpStatus.FORBIDDEN);
    }
    return principal;
  }
}
