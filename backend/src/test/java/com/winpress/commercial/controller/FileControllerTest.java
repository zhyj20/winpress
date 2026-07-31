package com.winpress.commercial.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.WorkflowRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import com.winpress.commercial.service.StorageService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

class FileControllerTest {
  private WorkflowRepository repository;
  private StorageService storage;
  private FileController controller;

  @BeforeEach
  void setUp() {
    repository = mock(WorkflowRepository.class);
    storage = mock(StorageService.class);
    controller = new FileController(storage, repository);
    CurrentUser.set(new AuthPrincipal(9L, "USR-9", 2L, "客户组织", "customer", "客户",
        "13800000009", "customer@example.com", "CUSTOMER", List.of("project:read_own")));
  }

  @AfterEach
  void tearDown() {
    CurrentUser.clear();
  }

  @Test
  void deniesDownloadBeforeReadingStorageWhenProjectIsOutsideCurrentScope() {
    when(repository.fileAsset("FIL-1")).thenReturn(asset());
    when(repository.canViewProject(CurrentUser.get(), 42L)).thenReturn(false);

    BusinessException exception = assertThrows(BusinessException.class, () -> controller.download("FIL-1"));

    assertEquals("FORBIDDEN", exception.getCode());
    verify(storage, never()).load(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void returnsAttachmentOnlyAfterProjectScopeCheck() {
    when(repository.fileAsset("FIL-1")).thenReturn(asset());
    when(repository.canViewProject(CurrentUser.get(), 42L)).thenReturn(true);
    when(storage.load("2026-07/file.pdf")).thenReturn(new ByteArrayResource("ok".getBytes()));

    var response = controller.download("FIL-1");

    assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
    assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment;"));
    assertEquals("no-store, private", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
    assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    verify(storage).load("2026-07/file.pdf");
  }

  @Test
  void treatsAnUnsafeLegacyContentTypeAsBinaryDownload() {
    when(repository.fileAsset("FIL-LEGACY-TYPE")).thenReturn(Map.of(
        "projectId", 42L,
        "storageKey", "2026-07/file.bin",
        "originalName", "legacy.pdf",
        "contentType", "text/html",
        "fileSize", 2L));
    when(repository.canViewProject(CurrentUser.get(), 42L)).thenReturn(true);
    when(storage.load("2026-07/file.bin")).thenReturn(new ByteArrayResource("ok".getBytes()));

    var response = controller.download("FIL-LEGACY-TYPE");

    assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
  }

  @Test
  void treatsLegacyFileWithoutAProjectAsUnavailable() {
    when(repository.fileAsset("FIL-LEGACY")).thenReturn(Map.of(
        "storageKey", "legacy/file.pdf", "originalName", "legacy.pdf", "fileSize", 1L));

    BusinessException exception = assertThrows(
        BusinessException.class, () -> controller.download("FIL-LEGACY"));

    assertEquals("FILE_NOT_FOUND", exception.getCode());
    verify(storage, never()).load(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void discardsPhysicalUploadWhenTheDatabaseRecordCannotBeCommitted() {
    MultipartFile upload = mock(MultipartFile.class);
    StorageService.StoredFile stored = new StorageService.StoredFile(
        "活动资料.pdf", "2026-07/new.pdf", "application/pdf", 2L, "checksum");
    when(repository.canViewProject(CurrentUser.get(), 42L)).thenReturn(true);
    when(storage.store(upload)).thenReturn(stored);
    when(repository.saveFile(CurrentUser.get(), 42L, "活动资料.pdf", "2026-07/new.pdf",
        "application/pdf", 2L, "checksum")).thenThrow(new IllegalStateException("database rejected"));

    assertThrows(IllegalStateException.class, () -> controller.upload(upload, 42L));

    verify(storage).discard("2026-07/new.pdf");
  }

  @Test
  void deniesUploadBeforeWritingStorageWhenProjectIsOutsideCurrentScope() {
    MultipartFile upload = mock(MultipartFile.class);
    when(repository.canViewProject(CurrentUser.get(), 42L)).thenReturn(false);

    BusinessException exception = assertThrows(BusinessException.class, () -> controller.upload(upload, 42L));

    assertEquals("FORBIDDEN", exception.getCode());
    verify(storage, never()).store(upload);
  }

  private Map<String, Object> asset() {
    return Map.of(
        "projectId", 42L,
        "storageKey", "2026-07/file.pdf",
        "originalName", "活动资料.pdf",
        "contentType", "application/pdf",
        "fileSize", 2L);
  }
}
