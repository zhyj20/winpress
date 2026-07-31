package com.winpress.commercial.controller;

import com.winpress.commercial.config.ApiResponse;
import com.winpress.commercial.dto.WorkflowDtos.SubmitManuscriptRequest;
import com.winpress.commercial.dto.WorkflowDtos.SubmitResultRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateConferenceWorkItemRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateMediaInvitationRequest;
import com.winpress.commercial.dto.WorkflowDtos.UpdateTaskRequest;
import com.winpress.commercial.dto.NiumediaDtos.UpdateConferenceMediaCandidateRequest;
import com.winpress.commercial.service.WorkflowService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operator")
public class OperatorController {
  private final WorkflowService service;

  public OperatorController(WorkflowService service) { this.service = service; }

  @PostMapping("/projects/{projectId}/manuscripts")
  public ApiResponse<Map<String, Object>> submitManuscript(
      @PathVariable Long projectId, @Valid @RequestBody SubmitManuscriptRequest request) {
    return ApiResponse.ok(service.submitManuscript(projectId, request));
  }

  @PatchMapping("/publish-tasks/{taskId}")
  public ApiResponse<Map<String, Object>> updateTask(
      @PathVariable Long taskId, @Valid @RequestBody UpdateTaskRequest request) {
    return ApiResponse.ok(service.updateTask(taskId, request));
  }

  @PatchMapping("/publish-tasks/{taskId}/media-invitation")
  public ApiResponse<Map<String, Object>> updateMediaInvitation(
      @PathVariable Long taskId, @Valid @RequestBody UpdateMediaInvitationRequest request) {
    return ApiResponse.ok(service.updateMediaInvitation(taskId, request));
  }

  @PatchMapping("/projects/{projectId}/conference-work-items/{itemId}")
  public ApiResponse<Map<String, Object>> updateConferenceWorkItem(
      @PathVariable Long projectId,
      @PathVariable Long itemId,
      @Valid @RequestBody UpdateConferenceWorkItemRequest request) {
    return ApiResponse.ok(service.updateConferenceWorkItem(projectId, itemId, request));
  }

  @PatchMapping("/projects/{projectId}/conference-media-candidates/{candidateId}")
  public ApiResponse<Map<String, Object>> updateConferenceMediaCandidate(
      @PathVariable Long projectId,
      @PathVariable Long candidateId,
      @Valid @RequestBody UpdateConferenceMediaCandidateRequest request) {
    return ApiResponse.ok(service.updateConferenceMediaCandidate(projectId, candidateId, request));
  }

  @PostMapping("/publish-tasks/{taskId}/results")
  public ApiResponse<Map<String, Object>> submitResult(
      @PathVariable Long taskId, @Valid @RequestBody SubmitResultRequest request) {
    return ApiResponse.ok(service.submitResult(taskId, request));
  }
}
