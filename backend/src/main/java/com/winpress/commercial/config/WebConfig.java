package com.winpress.commercial.config;

import com.winpress.commercial.security.AuthInterceptor;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AuthInterceptor authInterceptor;
  private final WinPressProperties properties;

  public WebConfig(AuthInterceptor authInterceptor, WinPressProperties properties) {
    this.authInterceptor = authInterceptor;
    this.properties = properties;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor)
        .addPathPatterns("/api/v1/**")
        .excludePathPatterns(
            "/api/v1/health",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/public/**",
            "/api/v1/integrations/geo/**",
            // Contracted client integrations use a separate API-key guard inside the controller.
            // They must never be treated as an unauthenticated public endpoint or a console session.
            "/api/v1/open-api/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    String[] allowedOrigins = Arrays.stream(properties.getCorsOrigins().split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .distinct()
        .toArray(String[]::new);
    if (allowedOrigins.length == 0) {
      throw new IllegalStateException("At least one explicit CORS origin must be configured.");
    }

    registry.addMapping("/api/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type", "X-Request-Id", "X-WinPress-API-Key",
            "Idempotency-Key", "Accept", "Origin")
        .allowCredentials(true)
        .maxAge(3600);
  }

}
