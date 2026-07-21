package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.osc.LXOscEngine;

class OscParamsTest extends HeadlessLxTest {

  @Test
  void listIncludesOscAddressableParametersWithFlatWireShape() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    String faderOscAddress = LXOscEngine.getOscAddress(channel.fader);

    List<Map<String, Object>> params = OscParams.list(lx);

    assertFalse(params.isEmpty(), "a fresh channel's fader is OSC-addressable");
    Map<String, Object> fader = params.stream()
        .filter(p -> faderOscAddress.equals(p.get("oscAddress")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("fader OSC address not found in " + params));

    assertEquals(channel.fader.getLabel(), fader.get("label"));
    assertEquals(channel.fader.getCanonicalPath(), fader.get("path"));
    assertEquals("CompoundParameter", fader.get("type"));
    assertEquals(0.0, ((Number) fader.get("min")).doubleValue(), 1e-9);
    assertEquals(1.0, ((Number) fader.get("max")).doubleValue(), 1e-9);
    assertTrue(fader.containsKey("componentPath"));
    assertTrue(fader.containsKey("componentLabel"));
  }

  @Test
  void everyEntryHasANonNullOscAddress() {
    LX lx = newHeadlessLx();
    lx.engine.mixer.addChannel();

    for (Map<String, Object> entry : OscParams.list(lx)) {
      assertTrue(entry.get("oscAddress") != null, "entries with a null oscAddress must be filtered out: " + entry);
    }
  }

  @Test
  void doesNotStackOverflowOnARealisticProject() {
    LX lx = newHeadlessLx();
    for (int i = 0; i < 4; i++) {
      lx.engine.mixer.addChannel();
    }

    // Merely completing without a StackOverflowError/infinite loop is the assertion — the
    // visited-set cycle guard is defense-in-depth, not expected to trigger on a normal
    // project, but this proves the walk terminates.
    List<Map<String, Object>> params = OscParams.list(lx);
    assertFalse(params.isEmpty());
  }
}
