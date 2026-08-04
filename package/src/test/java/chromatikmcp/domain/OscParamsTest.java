package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.osc.LXOscEngine;

class OscParamsTest extends HeadlessLxTest {

  @Test
  void listIncludesTypedOscAddressableParameters() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    String faderOscAddress = LXOscEngine.getOscAddress(channel.fader);

    List<OscParams.OscParamInfo> params = OscParams.list(lx);

    assertFalse(params.isEmpty(), "a fresh channel's fader is OSC-addressable");
    OscParams.OscParamInfo fader = params.stream()
        .filter(p -> faderOscAddress.equals(p.oscAddress()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("fader OSC address not found in " + params));

    assertEquals(channel.fader.getLabel(), fader.label());
    assertEquals(channel.fader.getCanonicalPath(), fader.path());
    assertEquals("CompoundParameter", fader.type());
    assertEquals(0.0, fader.min(), 1e-9);
    assertEquals(1.0, fader.max(), 1e-9);
    assertNotNull(fader.componentPath());
    assertNotNull(fader.componentLabel());
    assertEquals(channel.getClass().getSimpleName(), fader.componentType());
  }

  @Test
  void stringParameterEntriesCarryTheirValue() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.label.setValue("My Channel");
    String labelOscAddress = LXOscEngine.getOscAddress(channel.label);

    List<OscParams.OscParamInfo> params = OscParams.list(lx);

    OscParams.OscParamInfo label = params.stream()
        .filter(p -> labelOscAddress.equals(p.oscAddress()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("label OSC address not found in " + params));

    assertEquals("StringParameter", label.type());
    assertEquals("My Channel", label.value());
  }

  @Test
  void nonStringParameterEntriesDoNotCarryAValue() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    String faderOscAddress = LXOscEngine.getOscAddress(channel.fader);

    List<OscParams.OscParamInfo> params = OscParams.list(lx);

    OscParams.OscParamInfo fader = params.stream()
        .filter(p -> faderOscAddress.equals(p.oscAddress()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("fader OSC address not found in " + params));

    assertNull(fader.value(), "non-string entries have no value");
  }

  @Test
  void everyEntryHasANonNullOscAddress() {
    LX lx = newHeadlessLx();
    lx.engine.mixer.addChannel();

    for (OscParams.OscParamInfo entry : OscParams.list(lx)) {
      assertNotNull(entry.oscAddress(),
          "entries with a null oscAddress must be filtered out: " + entry);
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
    List<OscParams.OscParamInfo> params = OscParams.list(lx);
    assertFalse(params.isEmpty());
  }
}
