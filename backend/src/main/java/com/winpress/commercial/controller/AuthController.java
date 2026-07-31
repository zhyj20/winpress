package com.winpress.commercial.controller;

import com.winpress.commercial.config.ApiResponse;
import com.winpress.commercial.dto.AuthDtos.LoginRequest;
import com.winpress.commercial.dto.AuthDtos.LoginResponse;
import com.winpress.commercial.dto.AuthDtos.RegisterRequest;
import com.winpress.commercial.dto.AuthDtos.UserView;
import com.winpress.commercial.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) { this.service = service; }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    return ApiResponse.ok(service.login(request, servletRequest.getRemoteAddr()));
  }

  @PostMapping("/register")
  public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResponse.ok(service.register(request));
  }

  @GetMapping("/me")
  public ApiResponse<UserView> me() { return ApiResponse.ok(service.me()); }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
    service.logout(authorization);
    return ApiResponse.ok(null);
  }
}
