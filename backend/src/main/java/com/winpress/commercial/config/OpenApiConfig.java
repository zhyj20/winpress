package com.winpress.commercial.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  public OpenAPI winPressOpenApi() {
    return new OpenAPI()
        .info(new Info().title("WinPress 云发布 API").version("1.0.0")
            .description("客户、服务运营和平台运营使用的项目协作与多渠道发布接口。开放 API 的密钥签发与验收由平台运营后台受控管理。"))
        .components(new Components()
            .addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("session-token"))
            .addSecuritySchemes("openApiKey",
                new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER).name("X-WinPress-API-Key")));
  }
}
