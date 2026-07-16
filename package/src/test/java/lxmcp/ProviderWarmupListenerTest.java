package lxmcp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProviderWarmupListenerTest {

  @Test
  void warmupRanBeforeThisTest() {
    assertTrue(ProviderWarmupListener.ran, "LauncherSessionListener should have warmed up javax.sound providers");
  }
}
