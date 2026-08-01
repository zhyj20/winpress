package com.winpress.commercial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.winpress.commercial.exception.GlobalExceptionHandler;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {
  @Test
  void returnsBadRequestForAnInvalidNumericPathParameter() throws Exception {
    Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleEndpoint", Long.class);
    MethodParameter parameter = new MethodParameter(method, 0);
    MethodArgumentTypeMismatchException exception =
        new MethodArgumentTypeMismatchException(
            "undefined",
            Long.class,
            "projectId",
            parameter,
            new NumberFormatException("invalid project id"));

    var response = new GlobalExceptionHandler().handleTypeMismatch(exception);

    assertEquals(400, response.getStatusCode().value());
    assertFalse(response.getBody().success());
    assertEquals("INVALID_REQUEST_PARAMETER", response.getBody().code());
    assertEquals("请求参数格式不正确", response.getBody().message());
  }

  @Test
  void returnsPayloadTooLargeForFrameworkUploadLimit() {
    var response = new GlobalExceptionHandler().handleMaxUploadSize(new MaxUploadSizeExceededException(20L));

    assertEquals(413, response.getStatusCode().value());
    assertFalse(response.getBody().success());
    assertEquals("FILE_TOO_LARGE", response.getBody().code());
  }

  @Test
  void returnsBadRequestForMalformedJsonBody() {
    var response = new GlobalExceptionHandler().handleUnreadableBody(
        new HttpMessageNotReadableException("Malformed JSON"));

    assertEquals(400, response.getStatusCode().value());
    assertFalse(response.getBody().success());
    assertEquals("INVALID_REQUEST_BODY", response.getBody().code());
  }

  @SuppressWarnings("unused")
  private void sampleEndpoint(Long projectId) {}
}
