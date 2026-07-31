package com.winpress.commercial.config;

import java.time.OffsetDateTime;

public record ApiResponse<T>(boolean success, String code, String message, T data, OffsetDateTime timestamp) {
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, "OK", "操作成功", data, OffsetDateTime.now());
  }

  public static ApiResponse<Void> error(String code, String message) {
    return new ApiResponse<>(false, code, message, null, OffsetDateTime.now());
  }
}
