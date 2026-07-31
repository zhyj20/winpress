package com.winpress.commercial;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.winpress.commercial.service.NiumediaMediaClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class WinPressCommercialApplicationContextTest {
  @Autowired private NiumediaMediaClient niumediaMediaClient;

  @Test
  void applicationContextStartsAndCreatesTheMediaClient() {
    assertNotNull(niumediaMediaClient);
  }
}
