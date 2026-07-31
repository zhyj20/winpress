package com.winpress.commercial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class NiumediaDtos {
  private NiumediaDtos() {}

  public record MediaSearchQuery(
      @NotBlank @Size(max = 20) String target,
      @Size(max = 80) String keyword,
      @Size(max = 80) String name,
      @Size(max = 80) String province,
      @Size(max = 80) String city,
      Integer mediumType,
      @Size(max = 40) String mediaType,
      @Size(max = 120) String mpTypes,
      @Size(max = 120) String mpTypeGroup,
      Long mediaId,
      Integer reporterType,
      @Size(max = 80) String platform,
      @Size(max = 40) String sort,
      @Size(max = 40) String field,
      @Size(max = 40) String workflow,
      int page,
      int pageSize) {}

  /**
   * A media candidate as it moves through the server-side workflow.
   *
   * <p>Search responses use an opaque {@code candidateKey}; raw provider IDs remain in the
   * server-side selection cache until a candidate is persisted.  The nullable fields are omitted
   * from customer-facing JSON, while resolved internal candidates still retain the IDs required
   * for the protected persistence path.</p>
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MediaCandidate(
      @NotBlank @Size(max = 260) String candidateKey,
      @NotBlank @Size(max = 20) String candidateType,
      @Size(max = 120) String mediaId,
      @NotBlank @Size(max = 180) String displayName,
      @Size(max = 120) String reporterId,
      @Size(max = 80) String reporterName,
      @Size(max = 80) String attribute,
      @Size(max = 80) String province,
      @Size(max = 80) String city,
      @Size(max = 120) String channelForm,
      @Size(max = 80) String category,
      List<@Size(max = 80) String> coverageTags,
      @Size(max = 1000) String operationNote,
      boolean available,
      Double score,
      Long newsCount,
      Long fansCount,
      @Size(max = 800) String logoUrl,
      @Size(max = 800) String avatarUrl,
      @Size(max = 80) String updatedAt) {}

  public record MediaSearchResult(
      List<MediaCandidate> items,
      long total,
      int page,
      int pageSize,
      String updatedAt,
      boolean stale,
      String notice) {}

  public record LookupOption(int id, String name) {}

  public record RegionOption(String code, String name, List<RegionOption> children) {}

  public record DiscoveryTaxonomy(
      List<LookupOption> mediaTypes,
      List<LookupOption> mediaForms,
      List<RegionOption> regions,
      String updatedAt) {}

  public record BatchMediaCandidateRequest(
      @NotEmpty(message = "请至少选择一个媒体候选")
      @Size(max = 100, message = "单次最多加入100个媒体候选")
      List<@Valid MediaCandidate> candidates) {}

  public record UpdateConferenceMediaCandidateRequest(
      @NotBlank(message = "请选择媒体候选状态") String status,
      @NotBlank(message = "媒体候选状态已变化，请刷新后重试") String expectedStatus,
      @Size(max = 1000) String note) {}
}
