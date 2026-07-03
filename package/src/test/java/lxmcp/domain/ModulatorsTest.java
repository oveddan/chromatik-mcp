package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXTriggerModulation;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.MacroKnobs;
import heronarts.lx.modulator.MacroTriggers;
import heronarts.lx.modulator.Timer;
import heronarts.lx.pattern.color.GradientPattern;
import heronarts.lx.pattern.LXPattern;

/**
 * Instantiates the qa-strategy verification template for modulation mutations:
 * do → undo → assert restored. The undo assertion doubles as proof each primitive routes
 * through a real LXCommand rather than a direct edit.
 */
class ModulatorsTest {

  private LX lx;

  private LX newHeadlessLx() {
    this.lx = new LX(new GridModel(8, 8));
    return this.lx;
  }

  private LXPattern newDevice(LX lx) {
    LXChannel channel = lx.engine.mixer.addChannel();
    GradientPattern pattern = new GradientPattern(lx);
    channel.addPattern(pattern);
    return pattern;
  }

  @AfterEach
  void tearDown() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }

  @Test
  void primitiveMutatesEngineState() {
    LX lx = newHeadlessLx();
    int before = lx.engine.modulation.modulators.size();

    LXModulator added = Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);

    assertEquals(before + 1, lx.engine.modulation.modulators.size());
    assertInstanceOf(MacroKnobs.class, added);
    assertSame(added, lx.engine.modulation.modulators.get(before),
        "the returned modulator is the appended one");
    assertTrue(added.isRunning(),
        "running: AddModulator calls autostart() and MacroKnobs doesn't disable autoStart");
  }

  // Annotated so it clears the scope check, abstract so instantiation fails inside perform().
  @LXModulator.Global("Broken")
  abstract static class BrokenModulator extends LXModulator {
    protected BrokenModulator() {
      super("Broken");
    }
  }

  @Test
  void primitiveThrowsWhenTheCommandSilentlyFails() {
    LX lx = newHeadlessLx();
    int before = lx.engine.modulation.modulators.size();

    // An abstract class can't instantiate; perform() swallows the failure (pushes a UI
    // error and returns normally), so only the primitive's state-read verification
    // surfaces it. This is the headline behavior of the Mutations convention.
    assertThrows(IllegalStateException.class,
        () -> Modulators.addModulator(lx, lx.engine.modulation, BrokenModulator.class));
    assertEquals(before, lx.engine.modulation.modulators.size(), "nothing was added");
  }

  @Test
  void primitiveUndoRestoresState() {
    LX lx = newHeadlessLx();
    int before = lx.engine.modulation.modulators.size();

    LXModulator added = Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    assertEquals(before + 1, lx.engine.modulation.modulators.size());

    lx.command.undo();

    assertEquals(before, lx.engine.modulation.modulators.size(), "undo restores the list");
    assertFalse(lx.engine.modulation.modulators.contains(added), "the instance is removed");
  }

  // ---- device-scoped add ----

  @Test
  void addModulatorOnDeviceEngineUndoes() {
    LX lx = newHeadlessLx();
    LXPattern pattern = newDevice(lx);
    int before = pattern.modulation.modulators.size();

    LXModulator added = Modulators.addModulator(lx, pattern.modulation, MacroKnobs.class);
    assertEquals(before + 1, pattern.modulation.modulators.size(),
        "the modulator lands in the device's own engine");
    assertTrue(added.getCanonicalPath().startsWith(pattern.getCanonicalPath()),
        "the knob bank is addressed under the device");

    lx.command.undo();
    assertEquals(before, pattern.modulation.modulators.size());
  }

  // Device-only: stock LX has none (every registered modulator is at least @Global),
  // so the global-rejection gate needs a synthetic class.
  @LXModulator.Device("DeviceOnly")
  abstract static class DeviceOnlyModulator extends LXModulator {
    protected DeviceOnlyModulator() {
      super("DeviceOnly");
    }
  }

  @Test
  void addModulatorEnforcesScopeAnnotations() {
    LX lx = newHeadlessLx();
    LXPattern pattern = newDevice(lx);

    // Timer is @LXModulator.Global only — the Chromatik UI never offers it in a chain.
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Modulators.addModulator(lx, pattern.modulation, Timer.class)).failure);
    assertEquals(0, pattern.modulation.modulators.size(), "nothing was added");

    int globalBefore = lx.engine.modulation.modulators.size();
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Modulators.addModulator(lx, lx.engine.modulation, DeviceOnlyModulator.class)).failure);
    assertEquals(globalBefore, lx.engine.modulation.modulators.size());
  }

  @Test
  void resolveModulatorClassRejectsUnknownNames() {
    LX lx = newHeadlessLx();
    assertSame(MacroKnobs.class,
        Modulators.resolveModulatorClass(lx, MacroKnobs.class.getName()));
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Modulators.resolveModulatorClass(lx, "com.evil.NotAModulator")).failure);
  }

  @Test
  void resolveEngineDefaultsGlobalAndAddressesDevices() {
    LX lx = newHeadlessLx();
    LXPattern pattern = newDevice(lx);

    assertSame(lx.engine.modulation, Modulators.resolveEngine(lx, null));
    assertSame(pattern.modulation,
        Modulators.resolveEngine(lx, pattern.getCanonicalPath()));
    // An engine's own path is accepted — the only way to host a device-sourced wiring
    // in the global engine (legal in LX: global scope admits any engine-descendant end).
    assertSame(lx.engine.modulation, Modulators.resolveEngine(lx, "/lx/modulation"));
    assertSame(pattern.modulation,
        Modulators.resolveEngine(lx, pattern.modulation.getCanonicalPath()));
    // A channel is a component but not a device — typed mismatch, not a crash.
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Modulators.resolveEngine(lx, "/lx/mixer/channel/1")).failure);
  }

  @Test
  void inferEngineWalksToTheOwningEngine() {
    LX lx = newHeadlessLx();
    LXPattern pattern = newDevice(lx);
    MacroKnobs global = (MacroKnobs) Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    MacroKnobs device = (MacroKnobs) Modulators.addModulator(lx, pattern.modulation, MacroKnobs.class);

    assertSame(lx.engine.modulation, Modulators.inferEngine(lx, global.macro1));
    assertSame(pattern.modulation, Modulators.inferEngine(lx, device.macro1));
    assertSame(lx.engine.modulation,
        Modulators.inferEngine(lx, lx.engine.mixer.channels.get(0).fader),
        "an ordinary parameter has no engine ancestor — global fallback");
  }

  // ---- wiring: do -> undo -> assert, plus the rejection paths ----

  @Test
  void wireModulationGlobalUndoes() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    MacroKnobs knobs = (MacroKnobs) Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    int before = lx.engine.modulation.modulations.size();

    LXCompoundModulation modulation =
        Modulators.wireModulation(lx, lx.engine.modulation, knobs.macro1, channel.fader);
    assertEquals(before + 1, lx.engine.modulation.modulations.size());
    assertSame(lx.engine.modulation, modulation.scope);
    assertSame(modulation, Resolve.component(lx, modulation.getCanonicalPath()),
        "the modulation's canonical path round-trips through the resolver");

    lx.command.undo();
    assertEquals(before, lx.engine.modulation.modulations.size());
  }

  @Test
  void wireModulationDeviceScopedUndoes() {
    LX lx = newHeadlessLx();
    LXPattern pattern = newDevice(lx);
    MacroKnobs knobs = (MacroKnobs) Modulators.addModulator(lx, pattern.modulation, MacroKnobs.class);

    LXCompoundModulation modulation =
        Modulators.wireModulation(lx, pattern.modulation, knobs.macro1, knobs.macro2);
    assertEquals(1, pattern.modulation.modulations.size(),
        "the wiring lands in the device engine");
    assertSame(pattern.modulation, modulation.scope);

    lx.command.undo();
    assertEquals(0, pattern.modulation.modulations.size());
  }

  @Test
  void wireModulationRejectsOutOfScopeEnds() {
    LX lx = newHeadlessLx();
    LXPattern pattern = newDevice(lx);
    MacroKnobs knobs = (MacroKnobs) Modulators.addModulator(lx, pattern.modulation, MacroKnobs.class);
    var undoBefore = lx.command.getUndoCommand();

    // Target is the channel fader — outside the device. LX's own check would fire only
    // AFTER registering the graph edge, so the primitive must reject before any command.
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Modulators.wireModulation(lx, pattern.modulation, knobs.macro1,
            lx.engine.mixer.channels.get(0).fader));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertEquals(0, pattern.modulation.modulations.size(), "nothing was wired");
    assertSame(undoBefore, lx.command.getUndoCommand(),
        "the pre-check fired before perform — undo history untouched");
  }

  @Test
  void wireModulationRejectsCircularDependencies() {
    LX lx = newHeadlessLx();
    MacroKnobs a = (MacroKnobs) Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    MacroKnobs b = (MacroKnobs) Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    Modulators.wireModulation(lx, lx.engine.modulation, a.macro1, b.macro1);
    int before = lx.engine.modulation.modulations.size();

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Modulators.wireModulation(lx, lx.engine.modulation, b.macro1, a.macro1));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("circular"), "error names the likely cause");
    assertEquals(before, lx.engine.modulation.modulations.size(), "nothing was wired");
    // Documented LX cost of the perform-time detection: the swallowed rejection clears
    // the undo history (LXCommandEngine catches and clear()s). Cycles can't be
    // pre-checked from public state in the general case, so this is accepted and
    // surfaced in the tool description.
    assertNull(lx.command.getUndoCommand(), "circular rejection wipes the undo stack");
  }

  @Test
  void wireTriggerRejectsOutOfScopeEnds() {
    LX lx = newHeadlessLx();
    LXPattern pattern = newDevice(lx);
    MacroTriggers triggers =
        (MacroTriggers) Modulators.addModulator(lx, pattern.modulation, MacroTriggers.class);

    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Modulators.wireTrigger(lx, pattern.modulation, triggers.macro1,
                lx.engine.mixer.channels.get(0).enabled)).failure);
    assertEquals(0, pattern.modulation.triggers.size(), "nothing was wired");
  }

  @Test
  void wireTriggerUndoes() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    MacroTriggers triggers =
        (MacroTriggers) Modulators.addModulator(lx, lx.engine.modulation, MacroTriggers.class);
    int before = lx.engine.modulation.triggers.size();

    LXTriggerModulation trigger =
        Modulators.wireTrigger(lx, lx.engine.modulation, triggers.macro1, channel.enabled);
    assertEquals(before + 1, lx.engine.modulation.triggers.size());
    assertSame(lx.engine.modulation, trigger.scope);

    lx.command.undo();
    assertEquals(before, lx.engine.modulation.triggers.size());
  }

  // ---- removes: do -> undo -> assert restored (undo reconstructs a fresh instance) ----

  @Test
  void removeModulationUndoRestores() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    MacroKnobs knobs = (MacroKnobs) Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    LXCompoundModulation modulation =
        Modulators.wireModulation(lx, lx.engine.modulation, knobs.macro1, channel.fader);

    Modulators.removeModulation(lx, modulation);
    assertEquals(0, lx.engine.modulation.modulations.size());

    lx.command.undo();
    assertEquals(1, lx.engine.modulation.modulations.size(), "undo reconstructs the wiring");
    LXCompoundModulation restored = lx.engine.modulation.modulations.get(0);
    assertEquals(knobs.macro1.getCanonicalPath(), restored.source.getCanonicalPath(),
        "compare by path — undo builds a fresh instance");
    assertEquals(channel.fader.getCanonicalPath(), restored.target.getCanonicalPath());
  }

  @Test
  void removeTriggerUndoRestores() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    MacroTriggers triggers =
        (MacroTriggers) Modulators.addModulator(lx, lx.engine.modulation, MacroTriggers.class);
    LXTriggerModulation trigger =
        Modulators.wireTrigger(lx, lx.engine.modulation, triggers.macro1, channel.enabled);

    Modulators.removeTrigger(lx, trigger);
    assertEquals(0, lx.engine.modulation.triggers.size());

    lx.command.undo();
    assertEquals(1, lx.engine.modulation.triggers.size(), "undo reconstructs the trigger");
    LXTriggerModulation restored = lx.engine.modulation.triggers.get(0);
    assertEquals(triggers.macro1.getCanonicalPath(), restored.source.getCanonicalPath());
    assertEquals(channel.enabled.getCanonicalPath(), restored.target.getCanonicalPath());
  }
}
