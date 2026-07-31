package com.winpress.commercial.federation;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FederationFulfillmentReconcilerTest {

  @Test
  void mediaPrDoesNotAdvanceBeforeAnOperatorCreatesAnInvitation() {
    FederatedOrderRepository repository = mock(FederatedOrderRepository.class);
    FederationSnapshotIntegrity integrity = mock(FederationSnapshotIntegrity.class);
    FederationTokenService tokens = mock(FederationTokenService.class);
    FederationSourceIdentity sourceIdentity = mock(FederationSourceIdentity.class);
    when(tokens.isConfigured()).thenReturn(true);
    when(repository.reconciliationReceipts(100)).thenReturn(List.of(Map.of(
        "serviceType", "MEDIA_PR",
        "projectId", 42L,
        "sourceInstanceId", "edge-a",
        "status", "PENDING_MEDIA_SCOPE"
    )));
    when(repository.mediaInvitationCount(42L)).thenReturn(0);

    FederationFulfillmentReconciler reconciler = new FederationFulfillmentReconciler(
        repository, integrity, new ObjectMapper(), tokens, sourceIdentity
    );

    reconciler.reconcile();

    verify(repository).mediaInvitationCount(42L);
    verify(repository, never()).projectStatus(anyLong());
    verify(repository, never()).updateReceiptStatus(anyString(), anyString(), anyString());
  }
}
