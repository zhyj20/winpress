package com.winpress.commercial.controller;

import com.winpress.commercial.config.ApiResponse;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.WorkflowRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import com.winpress.commercial.service.StorageService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
  private final StorageService storage;
  private final WorkflowRepository repository;

  public FileController(StorageService storage, WorkflowRepository repository) {
    this.storage = storage;
    this.repository = repository;
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> upload(
      @RequestParam MultipartFile file,
      @RequestParam Long projectId) {
    AuthPrincipal user = CurrentUser.get();
    if (!repository.canViewProject(user, projectId)) {
      throw new BusinessException("FORBIDDEN", "当前账号无权向该项目上传文件", HttpStatus.FORBIDDEN);
    }
    StorageService.StoredFile stored = storage.store(file);
    try {
      String fileNo = repository.saveFile(
          user, projectId, stored.originalName(), stored.storageKey(), stored.contentType(), stored.size(), stored.sha256());
      return ApiResponse.ok(Map.of("fileNo", fileNo, "name", stored.originalName(), "size", stored.size()));
    } catch (RuntimeException exception) {
      storage.discard(stored.storageKey());
      throw exception;
    }
  }

  @GetMapping("/{fileNo}")
  public ResponseEntity<Resource> download(@PathVariable String fileNo) {
    AuthPrincipal user = CurrentUser.get();
    Map<String, Object> asset = repository.fileAsset(fileNo);
    if (asset.isEmpty()) {
      throw new BusinessException("FILE_NOT_FOUND", "文件不存在或已被移除", HttpStatus.NOT_FOUND);
    }
    Object project = asset.get("projectId");
    if (project == null) {
      throw new BusinessException("FILE_NOT_FOUND", "文件不存在或已被移除", HttpStatus.NOT_FOUND);
    }
    Long projectId;
    try {
      projectId = project instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(project));
    } catch (NumberFormatException exception) {
      throw new BusinessException("FILE_NOT_FOUND", "文件不存在或已被移除", HttpStatus.NOT_FOUND);
    }
    if (!repository.canViewProject(user, projectId)) {
      throw new BusinessException("FORBIDDEN", "当前账号无权下载该项目文件", HttpStatus.FORBIDDEN);
    }
    String contentType = asset.get("contentType") == null ? null : String.valueOf(asset.get("contentType"));
    MediaType mediaType = StorageService.safeDownloadMediaType(contentType);
    long contentLength = asset.get("fileSize") instanceof Number number ? number.longValue() : -1L;
    Resource resource = storage.load(String.valueOf(asset.get("storageKey")));
    ResponseEntity.BodyBuilder response = ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(StorageService.safeDownloadFilename(
                asset.get("originalName") == null ? null : String.valueOf(asset.get("originalName"))),
                StandardCharsets.UTF_8)
            .build().toString());
    if (contentLength >= 0) response.contentLength(contentLength);
    return response.body(resource);
  }
}
