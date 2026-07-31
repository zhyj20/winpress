package com.winpress.commercial.federation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winpress.commercial.config.WinPressProperties;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class FederationCallbackDispatcherTest {

  @Test
  void acceptsHttpsAndLoopbackHttpOnly() {
    WinPressProperties properties = new WinPressProperties();
    FederationCallbackDispatcher dispatcher = dispatcher(properties);

    properties.getFederation().setGeoCallbackUrl("https://geo.example.cn/callback");
    assertEquals(URI.create("https://geo.example.cn/callback"), dispatcher.validCallbackUri());

    properties.getFederation().setGeoCallbackUrl("http://127.0.0.1:9000/callback");
    assertEquals(URI.create("http://127.0.0.1:9000/callback"), dispatcher.validCallbackUri());

    properties.getFederation().setGeoCallbackUrl("http://geo.example.cn/callback");
    assertThrows(IllegalStateException.class, dispatcher::validCallbackUri);
  }

  @Test
  void storesOnlyGenericFailureText() {
    FederatedOrderRepository repository = mock(FederatedOrderRepository.class);
    FederationTokenService tokens = mock(FederationTokenService.class);
    WinPressProperties properties = new WinPressProperties();
    properties.getFederation().setGeoCallbackUrl("http://geo.example.cn/callback?token=not-stored");
    when(tokens.isConfigured()).thenReturn(true);
    when(tokens.issueGeoOrderEvent(org.mockito.ArgumentMatchers.any())).thenReturn("signed");
    when(repository.leaseOutbox(anyString(), anyInt(), anyInt())).thenReturn(List.of(Map.of(
        "id", 7L,
        "eventId", "event-7",
        "payload", "{\"event_id\":\"event-7\"}",
        "attemptCount", 1
    )));
    FederationCallbackDispatcher dispatcher = new FederationCallbackDispatcher(
        repository,
        tokens,
        properties,
        new ObjectMapper(),
        RestClient.builder()
    );

    dispatcher.dispatch();

    verify(repository).failOutbox(
        eq(7L),
        anyString(),
        anyInt(),
        eq("GEO_CALLBACK_FAILED"),
        eq("GEO 回调未完成，等待重试。")
    );
  }

  private FederationCallbackDispatcher dispatcher(WinPressProperties properties) {
    return new FederationCallbackDispatcher(
        mock(FederatedOrderRepository.class),
        mock(FederationTokenService.class),
        properties,
        new ObjectMapper(),
        RestClient.builder()
    );
  }
}
