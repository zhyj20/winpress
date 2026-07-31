package com.winpress.commercial.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.winpress.commercial.controller.OpenApiAdminController;
import com.winpress.commercial.controller.OpenApiClientController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.models.OpenAPI;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class OpenApiDocumentationSecurityTest {
  @Test
  void documentsTheClientApiKeyAndAdminSessionAsSeparateGuards() throws NoSuchMethodException {
    SecurityRequirement adminGuard =
        OpenApiAdminController.class.getAnnotation(SecurityRequirement.class);

    assertNotNull(adminGuard);
    assertEquals("bearerAuth", adminGuard.name());

    OpenAPI document = new OpenApiConfig().winPressOpenApi();
    assertTrue(document.getSecurity() == null || document.getSecurity().isEmpty());

    Method health = OpenApiClientController.class.getMethod("health");
    Operation operation = health.getAnnotation(Operation.class);
    assertNotNull(operation);
    assertTrue(health.getAnnotation(SecurityRequirement.class) == null);

    for (String methodName : new String[] {
        "serviceCatalog", "directPublishingChannels", "directPublishingTaxonomy",
        "submitRequirement", "requirements", "requirement"
    }) {
      Method method = java.util.Arrays.stream(OpenApiClientController.class.getMethods())
          .filter(candidate -> methodName.equals(candidate.getName()))
          .findFirst()
          .orElseThrow();
      SecurityRequirement guard = method.getAnnotation(SecurityRequirement.class);
      assertNotNull(guard);
      assertEquals("openApiKey", guard.name());
    }
  }
}
