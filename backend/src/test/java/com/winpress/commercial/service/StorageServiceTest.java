package com.winpress.commercial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageServiceTest {
  @TempDir
  Path temporaryRoot;

  @Test
  void storesAFileOnlyWhenItsContentMatchesTheDeclaredType() {
    StorageService service = service();
    MockMultipartFile upload = new MockMultipartFile(
        "file", "brief.pdf", "application/pdf",
        "%PDF-1.7\nlocal test".getBytes(StandardCharsets.US_ASCII));

    StorageService.StoredFile stored = service.store(upload);

    assertEquals("brief.pdf", stored.originalName());
    assertEquals("application/pdf", stored.contentType());
    assertTrue(Files.isRegularFile(temporaryRoot.resolve(stored.storageKey())));
  }

  @Test
  void rejectsRenamedContentAndRemovesThePartialFile() throws Exception {
    StorageService service = service();
    MockMultipartFile upload = new MockMultipartFile(
        "file", "brief.pdf", "application/pdf",
        "this is not a PDF".getBytes(StandardCharsets.US_ASCII));

    BusinessException exception = assertThrows(BusinessException.class, () -> service.store(upload));

    assertEquals("FILE_CONTENT_MISMATCH", exception.getCode());
    try (Stream<Path> files = Files.walk(temporaryRoot)) {
      assertFalse(files.anyMatch(Files::isRegularFile));
    }
  }

  @Test
  void rejectsAnOrdinaryZipFilePretendingToBeADocx() throws Exception {
    StorageService service = service();
    MockMultipartFile upload = new MockMultipartFile(
        "file", "brief.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        zip(Map.of("notes.txt", "not a word document".getBytes(StandardCharsets.UTF_8))));

    BusinessException exception = assertThrows(BusinessException.class, () -> service.store(upload));

    assertEquals("FILE_CONTENT_MISMATCH", exception.getCode());
    try (Stream<Path> files = Files.walk(temporaryRoot)) {
      assertFalse(files.anyMatch(Files::isRegularFile));
    }
  }

  @Test
  void acceptsTheExpectedOoxmlPackageStructure() {
    StorageService service = service();
    Map<String, byte[]> entries = new LinkedHashMap<>();
    entries.put("[Content_Types].xml", "<Types/>".getBytes(StandardCharsets.UTF_8));
    entries.put("word/document.xml", "<w:document/>".getBytes(StandardCharsets.UTF_8));
    MockMultipartFile upload = new MockMultipartFile(
        "file", "brief.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        zip(entries));

    StorageService.StoredFile stored = service.store(upload);

    assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        stored.contentType());
    assertTrue(Files.isRegularFile(temporaryRoot.resolve(stored.storageKey())));
  }

  @Test
  void rejectsAnOoxmlPackageWhoseExpandedContentExceedsTheSafetyLimit() throws Exception {
    StorageService service = service(10L * 1024L);
    Map<String, byte[]> entries = new LinkedHashMap<>();
    entries.put("[Content_Types].xml", "<Types/>".getBytes(StandardCharsets.UTF_8));
    entries.put("xl/worksheets/sheet1.xml", new byte[120 * 1024]);
    MockMultipartFile upload = new MockMultipartFile(
        "file", "brief.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        zip(entries));

    BusinessException exception = assertThrows(BusinessException.class, () -> service.store(upload));

    assertEquals("FILE_CONTENT_MISMATCH", exception.getCode());
    try (Stream<Path> files = Files.walk(temporaryRoot)) {
      assertFalse(files.anyMatch(Files::isRegularFile));
    }
  }

  @Test
  void discardsOnlyAFileInsideTheConfiguredUploadRoot() {
    StorageService service = service();
    MockMultipartFile upload = new MockMultipartFile(
        "file", "notes.txt", "text/plain", "local notes".getBytes(StandardCharsets.UTF_8));
    StorageService.StoredFile stored = service.store(upload);
    Path storedPath = temporaryRoot.resolve(stored.storageKey());

    service.discard(stored.storageKey());
    service.discard("../outside.txt");

    assertFalse(Files.exists(storedPath));
  }

  @Test
  void rejectsMalformedFilenameBeforeWritingAnyFile() throws Exception {
    StorageService service = service();
    MockMultipartFile upload = new MockMultipartFile(
        "file", "invalid\u0000name.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

    BusinessException exception = assertThrows(BusinessException.class, () -> service.store(upload));

    assertEquals("INVALID_FILE_NAME", exception.getCode());
    try (Stream<Path> files = Files.walk(temporaryRoot)) {
      assertFalse(files.anyMatch(Files::isRegularFile));
    }
  }

  @Test
  void enforcesTheActualStreamSizeAndDeletesPartialContent() throws Exception {
    StorageService service = service(8L);
    MultipartFile upload = mock(MultipartFile.class);
    when(upload.isEmpty()).thenReturn(false);
    when(upload.getContentType()).thenReturn("text/plain; charset=UTF-8");
    when(upload.getOriginalFilename()).thenReturn("notes.txt");
    when(upload.getSize()).thenReturn(1L);
    when(upload.getInputStream()).thenReturn(new ByteArrayInputStream("more than eight bytes".getBytes(StandardCharsets.UTF_8)));

    BusinessException exception = assertThrows(BusinessException.class, () -> service.store(upload));

    assertEquals("FILE_TOO_LARGE", exception.getCode());
    try (Stream<Path> files = Files.walk(temporaryRoot)) {
      assertFalse(files.anyMatch(Files::isRegularFile));
    }
  }

  @Test
  void recordsTheActualByteCountInsteadOfCallerMetadata() {
    StorageService service = service();
    MultipartFile upload = mock(MultipartFile.class);
    byte[] body = "actual contents".getBytes(StandardCharsets.UTF_8);
    try {
      when(upload.isEmpty()).thenReturn(false);
      when(upload.getContentType()).thenReturn("text/plain");
      when(upload.getOriginalFilename()).thenReturn("notes.txt");
      when(upload.getSize()).thenReturn(1L);
      when(upload.getInputStream()).thenReturn(new ByteArrayInputStream(body));
    } catch (java.io.IOException exception) {
      throw new AssertionError(exception);
    }

    StorageService.StoredFile stored = service.store(upload);

    assertEquals(body.length, stored.size());
  }

  private StorageService service() {
    return service(20L * 1024 * 1024);
  }

  private StorageService service(long maxFileBytes) {
    WinPressProperties properties = new WinPressProperties();
    properties.setStoragePath(temporaryRoot.toString());
    properties.setStorageMaxFileBytes(maxFileBytes);
    return new StorageService(properties);
  }

  private byte[] zip(Map<String, byte[]> entries) {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
         ZipOutputStream archive = new ZipOutputStream(output)) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        archive.putNextEntry(new ZipEntry(entry.getKey()));
        archive.write(entry.getValue());
        archive.closeEntry();
      }
      archive.finish();
      return output.toByteArray();
    } catch (java.io.IOException exception) {
      throw new AssertionError(exception);
    }
  }
}
