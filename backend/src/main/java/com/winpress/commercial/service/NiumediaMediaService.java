package com.winpress.commercial.service;

import com.winpress.commercial.dto.NiumediaDtos.DiscoveryTaxonomy;
import com.winpress.commercial.dto.NiumediaDtos.MediaCandidate;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchQuery;
import com.winpress.commercial.dto.NiumediaDtos.MediaSearchResult;
import com.winpress.commercial.exception.BusinessException;
import com.winpress.commercial.repository.IntegrationAdminRepository;
import com.winpress.commercial.security.AuthPrincipal;
import com.winpress.commercial.security.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class NiumediaMediaService {
  private static final long SELECTION_TTL_MILLIS = 15 * 60 * 1000L;
  private static final int MAX_SELECTIONS = 10_000;
  private final NiumediaMediaClient client;
  private final IntegrationAdminRepository integrationRepository;
  private final Map<String, CandidateSelection> selections = new ConcurrentHashMap<>();

  public NiumediaMediaService(
      NiumediaMediaClient client, IntegrationAdminRepository integrationRepository) {
    this.client = client;
    this.integrationRepository = integrationRepository;
  }

  public Map<String, Object> status() {
    CurrentUser.get();
    return status(false);
  }

  /**
   * Returns connection-governance detail for the protected platform administration console.
   * Customer routes only receive the operational capabilities from {@link #status()}.
   */
  public Map<String, Object> adminStatus() {
    CurrentUser.requireRole("PLATFORM_ADMIN");
    return status(true);
  }

  private Map<String, Object> status(boolean includeAdministrationDetail) {
    boolean governanceReady = integrationRepository.isExternalMediaDataOperational("NIUMEDIA");
    boolean rawMediaSearch = client.isMediaSearchConfigured();
    boolean rawReporterSearch = client.isReporterSearchConfigured();
    boolean rawTaxonomy = client.isTaxonomyConfigured();
    boolean runtimeConfigured = rawMediaSearch || rawReporterSearch;
    boolean mediaSearch = governanceReady && rawMediaSearch;
    boolean reporterSearch = governanceReady && rawReporterSearch;
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("available", governanceReady && runtimeConfigured);
    status.put("mediaSearch", mediaSearch);
    status.put("reporterSearch", reporterSearch);
    status.put("taxonomy", governanceReady && rawTaxonomy);
    status.put("manualFallbackAvailable", true);
    // The customer interface needs to know whether it can search now, not provider-side rate
    // limits, retry windows, or request throttling settings.
    status.put("temporarilyUnavailable",
        !governanceReady || !runtimeConfigured || client.isRateLimited());
    if (includeAdministrationDetail) {
      status.put("runtimeConfigured", runtimeConfigured);
      status.put("rawMediaSearchConfigured", rawMediaSearch);
      status.put("rawReporterSearchConfigured", rawReporterSearch);
      status.put("governanceReady", governanceReady);
      status.put("verificationStatus", governanceReady ? "ACCEPTED" : "PENDING");
    }
    return status;
  }

  public MediaSearchResult search(MediaSearchQuery query) {
    AuthPrincipal user = CurrentUser.get();
    requireSearchOperational(query == null ? null : query.target());
    MediaSearchResult result = client.search(query);
    long now = System.currentTimeMillis();
    evictExpiredSelections(now);
    List<MediaCandidate> safeItems = new ArrayList<>();
    for (MediaCandidate candidate : result.items()) {
      safeItems.add(issueSelection(user, candidate, now));
    }
    // Do not forward provider messages verbatim. A selected record is only a candidate for
    // project verification; it is never a promise that a media outlet or reporter will attend.
    String notice = result.stale()
        ? "当前显示最近一次成功结果。候选资料仍待项目核验，不代表媒体或记者已确认参与。"
        : "候选资料待项目核验，不代表媒体或记者已确认参与。";
    return new MediaSearchResult(
        safeItems, result.total(), result.page(), result.pageSize(), result.updatedAt(),
        result.stale(), notice);
  }

  /** Resolves a short-lived selection reference for the current user only. */
  public MediaCandidate resolveCandidate(String reference) {
    AuthPrincipal user = CurrentUser.get();
    if (reference == null || !reference.startsWith("SEL-")) {
      throw selectionExpired();
    }
    CandidateSelection selection = selections.get(reference);
    long now = System.currentTimeMillis();
    if (selection == null || selection.expiresAt() <= now) {
      selections.remove(reference);
      throw selectionExpired();
    }
    if (!Objects.equals(user.userId(), selection.userId())
        || !Objects.equals(user.organizationId(), selection.organizationId())) {
      throw new BusinessException("FORBIDDEN", "当前账号无权使用该媒体候选", HttpStatus.FORBIDDEN);
    }
    return selection.candidate();
  }

  /** Resolves the selected media's provider ID only inside the server-side reporter query. */
  public Long resolveMediaId(String reference) {
    MediaCandidate candidate = resolveCandidate(reference);
    if (!"MEDIA".equalsIgnoreCase(candidate.candidateType()) || candidate.mediaId() == null) {
      throw new BusinessException("MEDIA_ID_REQUIRED", "请先选择媒体后再筛选记者", HttpStatus.BAD_REQUEST);
    }
    try {
      return Long.valueOf(candidate.mediaId());
    } catch (NumberFormatException exception) {
      throw new BusinessException("MEDIA_ID_REQUIRED", "所选媒体暂不支持记者筛选", HttpStatus.BAD_REQUEST);
    }
  }

  public DiscoveryTaxonomy taxonomy() {
    CurrentUser.get();
    requireTaxonomyOperational();
    return client.taxonomy();
  }

  private void requireSearchOperational(String target) {
    boolean governanceReady = integrationRepository.isExternalMediaDataOperational("NIUMEDIA");
    String safeTarget = target == null ? "MEDIA" : target.trim().toUpperCase();
    boolean targetConfigured = "REPORTER".equals(safeTarget)
        ? client.isReporterSearchConfigured()
        : client.isMediaSearchConfigured();
    if (!governanceReady || !targetConfigured) {
      throw new BusinessException(
          "MEDIA_DISCOVERY_UNAVAILABLE",
          "媒体资料检索暂不可用。您仍可提交需求，由项目负责人补充并核验候选名单。",
          HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private void requireTaxonomyOperational() {
    boolean governanceReady = integrationRepository.isExternalMediaDataOperational("NIUMEDIA");
    if (!governanceReady || !client.isTaxonomyConfigured()) {
      throw new BusinessException(
          "MEDIA_DISCOVERY_UNAVAILABLE",
          "媒体资料检索暂不可用。您仍可提交需求，由项目负责人补充并核验候选名单。",
          HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private MediaCandidate issueSelection(AuthPrincipal user, MediaCandidate source, long now) {
    String reference = "SEL-" + UUID.randomUUID().toString().replace("-", "");
    selections.put(reference, new CandidateSelection(
        user.userId(), user.organizationId(), source, now + SELECTION_TTL_MILLIS));
    return new MediaCandidate(
        reference, source.candidateType(), null, source.displayName(), null, source.reporterName(),
        source.attribute(), source.province(), source.city(), source.channelForm(), source.category(),
        source.coverageTags(), null, source.available(), source.score(), source.newsCount(),
        // Image URLs can be relative to an upstream data service. Do not disclose that origin
        // to the browser; the product renders a neutral local fallback when no image is present.
        source.fansCount(), null, null, source.updatedAt());
  }

  private void evictExpiredSelections(long now) {
    if (selections.size() < MAX_SELECTIONS) return;
    selections.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    if (selections.size() >= MAX_SELECTIONS) selections.clear();
  }

  private BusinessException selectionExpired() {
    return new BusinessException(
        "MEDIA_SELECTION_EXPIRED", "媒体候选已失效，请重新筛选后再继续", HttpStatus.BAD_REQUEST);
  }

  private record CandidateSelection(
      Long userId, Long organizationId, MediaCandidate candidate, long expiresAt) {}
}
