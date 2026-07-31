package com.winpress.commercial.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
  private AuthDtos() {}

  public record LoginRequest(
      @NotBlank(message = "请输入账号") String username,
      @NotBlank(message = "请输入密码") String password) {}

  public record RegisterRequest(
      @NotBlank(message = "请输入用户名") @Size(max = 80) String username,
      @NotBlank(message = "请输入单位名称") @Size(max = 160) String organizationName,
      @NotBlank(message = "请输入联系人姓名") @Size(max = 80) String displayName,
      @NotBlank(message = "请输入手机号") @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String mobile,
      @NotBlank(message = "请输入邮箱") @Email(message = "邮箱格式不正确") String email,
      @NotBlank(message = "请输入密码") @Size(min = 8, max = 64, message = "密码须为 8 至 64 位") String password) {}

  public record UserView(Long id, String userNo, Long organizationId, String organizationName,
                         String username, String displayName, String mobile, String email,
                         String role, List<String> permissions) {}

  public record LoginResponse(String token, UserView user) {}
}
