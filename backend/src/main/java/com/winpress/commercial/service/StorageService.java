package com.winpress.commercial.service;

import com.winpress.commercial.config.WinPressProperties;
import com.winpress.commercial.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {
  private static final int MAX_ORIGINAL_NAME_LENGTH = 240;
  private static final int MAX_OOXML_ENTRY_COUNT = 1024;
  private static final long MAX_OOXML_EXPANSION_MULTIPLIER = 10L;
  private static final Set<String> ALLOWED_TYPES = Set.of(
      "application/pdf", "application/msword",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "image/jpeg", "image/png", "image/webp", "text/plain");
  private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of(
      "application/pdf", Set.of(".pdf"),
      "application/msword", Set.of(".doc"),
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of(".docx"),
      "application/vnd.ms-excel", Set.of(".xls"),
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of(".xlsx"),
      "image/jpeg", Set.of(".jpg", ".jpeg"),
      "image/png", Set.of(".png"),
      "image/webp", Set.of(".webp"),
      "text/plain", Set.of(".txt"));
  private final Path root;
  private final long maxFileBytes;

  public StorageService(WinPressProperties properties) {
    this.root = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
    this.maxFileBytes = properties.getStorageMaxFileBytes();
    if (maxFileBytes <= 0) {
      throw new IllegalStateException("winpress.storage-max-file-bytes must be greater than zero");
    }
    try { Files.createDirectories(root); }
    catch (IOException ex) { throw new IllegalStateException("Cannot initialize file storage", ex); }
  }

  public StoredFile store(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException("EMPTY_FILE", "请选择要上传的文件", HttpStatus.BAD_REQUEST);
    }
    String contentType = normalizedContentType(file.getContentType());
    if (!ALLOWED_TYPES.contains(contentType)) {
      throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "该文件格式不支持", HttpStatus.BAD_REQUEST);
    }
    String original = normalizedOriginalName(file.getOriginalFilename());
    String extension = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
    if (!ALLOWED_EXTENSIONS.get(contentType).contains(extension)) {
      throw new BusinessException("FILE_EXTENSION_NOT_ALLOWED", "文件扩展名与文件格式不匹配", HttpStatus.BAD_REQUEST);
    }
    if (file.getSize() > maxFileBytes) {
      throw new BusinessException("FILE_TOO_LARGE", "文件超过允许大小", HttpStatus.PAYLOAD_TOO_LARGE);
    }
    String key = LocalDate.now() + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
    Path destination = root.resolve(key).normalize();
    if (!destination.startsWith(root)) {
      throw new BusinessException("INVALID_FILE_PATH", "文件路径不合法", HttpStatus.BAD_REQUEST);
    }
    try {
      Files.createDirectories(destination.getParent());
      long storedSize;
      try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(destination)) {
        storedSize = copyWithinLimit(input, output);
      }
      if (!matchesDeclaredType(destination, contentType)) {
        throw new BusinessException(
            "FILE_CONTENT_MISMATCH", "文件内容与所选格式不一致", HttpStatus.BAD_REQUEST);
      }
      long actualSize = Files.size(destination);
      if (actualSize != storedSize) {
        throw new BusinessException("FILE_STORE_FAILED", "文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
      }
      return new StoredFile(original, key.replace('\\', '/'), contentType, actualSize, sha256(destination));
    } catch (BusinessException ex) {
      deleteQuietly(destination);
      throw ex;
    } catch (IOException ex) {
      deleteQuietly(destination);
      throw new BusinessException("FILE_STORE_FAILED", "文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns a response-safe media type for a stored asset. The database value is treated as
   * untrusted because legacy records may predate the current upload validator.
   */
  public static MediaType safeDownloadMediaType(String value) {
    String normalized = normalizedContentType(value);
    return ALLOWED_TYPES.contains(normalized)
        ? MediaType.parseMediaType(normalized)
        : MediaType.APPLICATION_OCTET_STREAM;
  }

  /**
   * Prevents a malformed legacy filename from reaching the Content-Disposition response.
   */
  public static String safeDownloadFilename(String value) {
    try {
      return normalizedOriginalName(value);
    } catch (BusinessException ignored) {
      return "download";
    }
  }

  public Resource load(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      throw new BusinessException("FILE_NOT_FOUND", "文件不存在或已被移除", HttpStatus.NOT_FOUND);
    }
    Path source = root.resolve(storageKey).normalize();
    if (!source.startsWith(root) || !Files.isRegularFile(source)) {
      throw new BusinessException("FILE_NOT_FOUND", "文件不存在或已被移除", HttpStatus.NOT_FOUND);
    }
    Resource resource;
    try {
      resource = new UrlResource(source.toUri());
    } catch (MalformedURLException ex) {
      throw new BusinessException("FILE_READ_FAILED", "文件暂时无法读取，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    if (!resource.exists() || !resource.isReadable()) {
      throw new BusinessException("FILE_NOT_FOUND", "文件不存在或已被移除", HttpStatus.NOT_FOUND);
    }
    return resource;
  }

  /**
   * Removes a newly stored file when its database record could not be committed.
   *
   * <p>Cleanup is deliberately best-effort so the original database error remains visible to the
   * caller. The normalized root check prevents an unexpected key from escaping the upload area.</p>
   */
  public void discard(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) return;
    Path target = root.resolve(storageKey).normalize();
    if (!target.startsWith(root)) return;
    try {
      Files.deleteIfExists(target);
    } catch (IOException ignored) {
      // A later storage maintenance job can remove an orphan that the filesystem kept locked.
    }
  }

  private long copyWithinLimit(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[8192];
    long total = 0L;
    int read;
    while ((read = input.read(buffer)) != -1) {
      if (total > maxFileBytes - read) {
        throw new BusinessException("FILE_TOO_LARGE", "文件超过允许大小", HttpStatus.PAYLOAD_TOO_LARGE);
      }
      output.write(buffer, 0, read);
      total += read;
    }
    return total;
  }

  private static String normalizedContentType(String value) {
    if (value == null || value.isBlank()) return "";
    try {
      MediaType mediaType = MediaType.parseMediaType(value);
      return (mediaType.getType() + "/" + mediaType.getSubtype()).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException ignored) {
      return "";
    }
  }

  private static String normalizedOriginalName(String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException("INVALID_FILE_NAME", "文件名不合法", HttpStatus.BAD_REQUEST);
    }
    try {
      String filename = Path.of(value.replace('\\', '/')).getFileName().toString();
      if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)
          || filename.length() > MAX_ORIGINAL_NAME_LENGTH
          || filename.codePoints().anyMatch(Character::isISOControl)) {
        throw new BusinessException("INVALID_FILE_NAME", "文件名不合法", HttpStatus.BAD_REQUEST);
      }
      return filename;
    } catch (InvalidPathException ignored) {
      throw new BusinessException("INVALID_FILE_NAME", "文件名不合法", HttpStatus.BAD_REQUEST);
    }
  }

  private static void deleteQuietly(Path destination) {
    try {
      Files.deleteIfExists(destination);
    } catch (IOException ignored) {
      // The existing orphan cleanup process can handle a file locked by the host filesystem.
    }
  }

  private boolean matchesDeclaredType(Path path, String contentType) throws IOException {
    byte[] header;
    try (InputStream input = Files.newInputStream(path)) {
      header = input.readNBytes(8192);
    }
    return switch (contentType) {
      case "application/pdf" -> startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
      case "application/msword", "application/vnd.ms-excel" ->
          startsWith(header, new byte[] {
              (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
              (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
          });
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
           "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
          startsWith(header, new byte[] {0x50, 0x4B, 0x03, 0x04})
              && hasExpectedOoxmlPackage(path,
                  contentType.endsWith("wordprocessingml.document") ? "word/" : "xl/");
      case "image/jpeg" ->
          startsWith(header, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
      case "image/png" ->
          startsWith(header, new byte[] {
              (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
          });
      case "image/webp" -> header.length >= 12
          && startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
          && bytesEqualAt(header, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
      case "text/plain" -> containsNoNullByte(header);
      default -> false;
    };
  }

  /**
   * A DOCX/XLSX is a constrained OOXML ZIP package, not an arbitrary ZIP renamed by the client.
   * This check deliberately reads the central directory only: it requires the package manifest and
   * its expected document family while rejecting path traversal, implausible entry counts and
   * decompression ratios before any downstream user opens the attachment.
   */
  private boolean hasExpectedOoxmlPackage(Path path, String expectedDirectory) throws IOException {
    long maximumExpandedBytes = maximumOoxmlExpandedBytes();
    long totalExpandedBytes = 0L;
    int entryCount = 0;
    boolean hasContentTypes = false;
    boolean hasExpectedDocumentPart = false;

    try (ZipFile archive = new ZipFile(path.toFile())) {
      var entries = archive.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (++entryCount > MAX_OOXML_ENTRY_COUNT || !hasSafeArchiveEntryName(entry.getName())) {
          return false;
        }
        if (entry.isDirectory()) {
          continue;
        }

        long expandedSize = entry.getSize();
        long compressedSize = entry.getCompressedSize();
        if (expandedSize < 0 || compressedSize < 0 || expandedSize > maximumExpandedBytes
            || totalExpandedBytes > maximumExpandedBytes - expandedSize) {
          return false;
        }
        if (expandedSize > 0
            && (compressedSize == 0 || expandedSize / compressedSize > MAX_OOXML_EXPANSION_MULTIPLIER)) {
          return false;
        }
        totalExpandedBytes += expandedSize;
        hasContentTypes |= "[Content_Types].xml".equals(entry.getName());
        hasExpectedDocumentPart |= entry.getName().startsWith(expectedDirectory);
      }
    }
    return hasContentTypes && hasExpectedDocumentPart;
  }

  private long maximumOoxmlExpandedBytes() {
    if (maxFileBytes > Long.MAX_VALUE / MAX_OOXML_EXPANSION_MULTIPLIER) {
      return Long.MAX_VALUE;
    }
    return maxFileBytes * MAX_OOXML_EXPANSION_MULTIPLIER;
  }

  private static boolean hasSafeArchiveEntryName(String entryName) {
    if (entryName == null || entryName.isBlank() || entryName.startsWith("/")
        || entryName.startsWith("\\") || entryName.contains("\\")) {
      return false;
    }
    for (String segment : entryName.split("/")) {
      if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
        return false;
      }
    }
    return true;
  }

  private static boolean startsWith(byte[] value, byte[] prefix) {
    return bytesEqualAt(value, 0, prefix);
  }

  private static boolean bytesEqualAt(byte[] value, int offset, byte[] expected) {
    if (value.length < offset + expected.length) return false;
    for (int index = 0; index < expected.length; index++) {
      if (value[offset + index] != expected[index]) return false;
    }
    return true;
  }

  private static boolean containsNoNullByte(byte[] value) {
    for (byte item : value) {
      if (item == 0) return false;
    }
    return true;
  }

  private String sha256(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream in = Files.newInputStream(path); DigestInputStream ignored = new DigestInputStream(in, digest)) {
        ignored.transferTo(java.io.OutputStream.nullOutputStream());
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public record StoredFile(String originalName, String storageKey, String contentType, long size, String sha256) {}
}
