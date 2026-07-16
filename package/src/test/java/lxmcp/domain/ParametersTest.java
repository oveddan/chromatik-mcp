package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulator.MacroKnobs;
import heronarts.lx.modulator.MacroTriggers;
import heronarts.lx.pattern.color.GradientPattern;

class ParametersTest {

  private LX lx;

  private LX newHeadlessLx() {
    this.lx = new LX(new GridModel(8, 8));
    return this.lx;
  }

  @AfterEach
  void tearDown() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }

  @Test
  void resolvesBoundedParameterByCanonicalPath() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    String path = channel.fader.getCanonicalPath();

    Parameters.ParameterInfo info = Parameters.get(lx, path);
    assertNotNull(info, "canonical path produced by LX must resolve");
    assertEquals(path, info.path(), "path round-trips");
    assertEquals(channel.fader.getValue(), ((Number) info.value()).doubleValue(), 1e-9);
    assertEquals(0.0, info.min(), 1e-9);
    assertEquals(1.0, info.max(), 1e-9);
    assertNotNull(info.normalized(), "fader is a normalized parameter");
    assertNotNull(info.formatted());
  }

  @Test
  void describesBooleanParameter() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    Parameters.ParameterInfo info = Parameters.get(lx, channel.enabled.getCanonicalPath());
    assertNotNull(info);
    assertEquals(channel.enabled.isOn(), info.value());
    assertEquals("BooleanParameter", info.type());
    assertNull(info.options(), "boolean has no options list");
  }

  @Test
  void describesEnumParameterWithOptionsAndFormattedLabel() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    Parameters.ParameterInfo info = Parameters.get(lx, channel.crossfadeGroup.getCanonicalPath());
    assertNotNull(info);
    assertEquals(channel.crossfadeGroup.getValuei(), info.value());
    assertNotNull(info.options(), "enum parameters expose their options");
    assertEquals(channel.crossfadeGroup.getOption(), info.formatted(),
        "formatted is the current option label, not a number");
    assertEquals((double) channel.crossfadeGroup.getMinValue(), info.min(), 1e-9);
    assertEquals((double) channel.crossfadeGroup.getMaxValue(), info.max(), 1e-9);
  }

  @Test
  void describesStringParameterWithoutBogusFormatting() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    Parameters.ParameterInfo info = Parameters.get(lx, channel.label.getCanonicalPath());
    assertNotNull(info);
    assertEquals(channel.label.getString(), info.value());
    assertEquals("StringParameter", info.type());
    assertNull(info.formatted(), "getValue() is a change counter for strings — never formatted");
  }

  @Test
  void describesColorParameterAsHexWithoutBogusFormatting() {
    LX lx = newHeadlessLx();
    // The default palette swatch always carries at least one dynamic color.
    Parameters.ParameterInfo info =
        Parameters.get(lx, lx.engine.palette.getSwatchColor(0).primary.getCanonicalPath());
    assertNotNull(info);
    String value = (String) info.value();
    assertEquals(10, value.length(), "color value is an 0xAARRGGBB hex string");
    assertEquals("0x", value.substring(0, 2));
    assertNull(info.formatted(), "the double formatter yields NaN for packed colors");
  }

  // ---- listFor: enumerate a component's own parameters ----

  @Test
  void listForIncludesFaderAndEnabledWithRoundTrippingPaths() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    String channelPath = channel.getCanonicalPath();

    Parameters.ComponentParameters info = Parameters.listFor(lx, channelPath);
    assertEquals(channelPath, info.path());
    assertEquals(channel.getId(), info.id());
    assertEquals(channel.getLabel(), info.label());
    assertEquals(channel.getClass().getName(), info.className(),
        "class is fully-qualified, matching the other list tools and get_component_doc input");

    Parameters.ParameterInfo fader = info.parameters().stream()
        .filter(p -> channel.fader.getCanonicalPath().equals(p.path()))
        .findFirst().orElseThrow(() -> new AssertionError("fader not listed"));
    assertEquals(channel.fader.getValue(), ((Number) fader.value()).doubleValue(), 1e-9);
    assertSame(channel.fader, Resolve.parameter(lx, fader.path()), "path round-trips via LXPath.get");

    Parameters.ParameterInfo enabled = info.parameters().stream()
        .filter(p -> channel.enabled.getCanonicalPath().equals(p.path()))
        .findFirst().orElseThrow(() -> new AssertionError("enabled not listed"));
    assertEquals(channel.enabled.isOn(), enabled.value());
    assertSame(channel.enabled, Resolve.parameter(lx, enabled.path()), "path round-trips via LXPath.get");
  }

  @Test
  void listForPaletteIncludesSingletonAndArrayChildren() {
    LX lx = newHeadlessLx();

    Parameters.ComponentParameters before = Parameters.listFor(lx, lx.engine.palette.getCanonicalPath());
    Parameters.ChildInfo swatchChild = before.children().stream()
        .filter(c -> "swatch".equals(c.key()))
        .findFirst().orElseThrow(() -> new AssertionError("singleton swatch child not listed"));
    assertEquals(lx.engine.palette.swatch.getCanonicalPath(), swatchChild.path());
    assertEquals(lx.engine.palette.swatch.getClass().getName(), swatchChild.className());

    lx.engine.palette.saveSwatch();
    Parameters.ComponentParameters after = Parameters.listFor(lx, lx.engine.palette.getCanonicalPath());
    Parameters.ChildInfo swatchesEntry = after.children().stream()
        .filter(c -> "swatches".equals(c.key()))
        .findFirst().orElseThrow(() -> new AssertionError("saved swatch array child not listed"));
    assertSame(lx.engine.palette.swatches.get(0), Resolve.component(lx, swatchesEntry.path()),
        "array child path round-trips via LXPath.get");
  }

  @Test
  void listForPatternIncludesEffectChild() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    GradientPattern pattern = new GradientPattern(lx);
    channel.addPattern(pattern);
    heronarts.lx.effect.BlurEffect effect = new heronarts.lx.effect.BlurEffect(lx);
    pattern.addEffect(effect);

    Parameters.ComponentParameters info = Parameters.listFor(lx, pattern.getCanonicalPath());
    Parameters.ChildInfo effectChild = info.children().stream()
        .filter(c -> "effect".equals(c.key()))
        .findFirst().orElseThrow(() -> new AssertionError("effect child not listed"));
    assertEquals(effect.getCanonicalPath(), effectChild.path());
    assertEquals(effect.getClass().getName(), effectChild.className());
  }

  @Test
  void listForLeafComponentReturnsEmptyChildren() {
    LX lx = newHeadlessLx();
    // A bare modulator registers no child components or arrays — a leaf in the tree.
    MacroKnobs knobs =
        (MacroKnobs) Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);

    Parameters.ComponentParameters info = Parameters.listFor(lx, knobs.getCanonicalPath());
    assertNotNull(info.children());
    assertTrue(info.children().isEmpty(), "a leaf component has no children");
  }

  @Test
  void deduplicatesChildrenByCanonicalPath() {
    LX lx = newHeadlessLx();
    // Tempo registers named modulators in both children and childArrays with the same path.
    Parameters.ComponentParameters info = Parameters.listFor(lx, lx.engine.tempo.getCanonicalPath());

    java.util.Set<String> seenPaths = new java.util.HashSet<>();
    for (Parameters.ChildInfo child : info.children()) {
      assertTrue(seenPaths.add(child.path()),
          "Canonical path " + child.path() + " appears multiple times in children");
    }
  }

  @Test
  void listForBogusPathIsNotFound() {
    LX lx = newHeadlessLx();
    assertEquals(Resolve.Failure.NOT_FOUND,
        assertThrows(Resolve.ResolveException.class,
            () -> Parameters.listFor(lx, "/lx/nope/nothing")).failure);
  }

  @Test
  void listForModulatorPathListsAsComponent() {
    LX lx = newHeadlessLx();
    // An LXModulator is both LXComponent and LXParameter — the dual-typed case must
    // resolve as a component and list its parameters, not bounce toward get_parameter.
    MacroKnobs knobs =
        (MacroKnobs) Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);

    Parameters.ComponentParameters info = Parameters.listFor(lx, knobs.getCanonicalPath());
    assertEquals(knobs.getCanonicalPath(), info.path());
    assertEquals(MacroKnobs.class.getName(), info.className());
    assertTrue(info.parameters().stream()
            .anyMatch(p -> knobs.macro1.getCanonicalPath().equals(p.path())),
        "macro1 is listed");
  }

  @Test
  void listForParameterPathPointsAtGetParameter() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Parameters.listFor(lx, channel.fader.getCanonicalPath()));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("get_parameter"),
        "error tells the caller to use get_parameter instead");
  }

  @Test
  void badPathsThrowTypedResolveErrors() {
    LX lx = newHeadlessLx();
    assertEquals(Resolve.Failure.NOT_FOUND,
        assertThrows(Resolve.ResolveException.class,
            () -> Parameters.get(lx, "/lx/nope/nothing")).failure);
    assertEquals(Resolve.Failure.NOT_FOUND,
        assertThrows(Resolve.ResolveException.class,
            () -> Parameters.get(lx, "/lx/mixer/channel/99/fader")).failure);
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Parameters.get(lx, "/lx/mixer")).failure,
        "a component path is not a parameter");
  }

  @Test
  void describesOscAddresses() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    // Ordinary component parameters: OSC address == canonical path.
    Parameters.ParameterInfo fader = Parameters.get(lx, channel.fader.getCanonicalPath());
    assertEquals(channel.fader.getCanonicalPath(), fader.oscAddress());

    // Modulator parameters: OSC segments use the sanitized *label*, not the array index —
    // the address an OSC controller must send to (docs/osc-addressing.md).
    MacroKnobs knobs =
        (MacroKnobs) Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    Parameters.ParameterInfo macro = Parameters.get(lx, knobs.macro1.getCanonicalPath());
    assertEquals(lx.engine.modulation.getCanonicalPath() + "/" + knobs.getOscLabel() + "/macro1",
        macro.oscAddress());
    assertNotEquals(macro.path(), macro.oscAddress(),
        "label-based OSC address differs from the canonical path");
  }

  // ---- describe/get: live effective value for a modulated compound parameter ----

  @Test
  void getReportsEffectiveModulatedValueAndBaseSeparately() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.fader.setValue(0.0);
    MacroKnobs knobs =
        (MacroKnobs) Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    // A deterministic source value (unlike an LFO) so the effective reading is exact.
    knobs.macro1.setValue(0.5);
    LXCompoundModulation modulation =
        Modulators.wireModulation(lx, lx.engine.modulation, knobs.macro1, channel.fader);
    modulation.range.setValue(1.0);

    Parameters.ParameterInfo info = Parameters.get(lx, channel.fader.getCanonicalPath());
    assertTrue(info.modulated(), "an active compound modulation is wired to this target");
    assertEquals(0.0 + 0.5 * 1.0, ((Number) info.value()).doubleValue(), 1e-9,
        "value is the live effective reading — base plus the modulation, not a static base");
    assertEquals(0.0, ((Number) info.baseValue()).doubleValue(), 1e-9,
        "baseValue is the knob's unmodulated set position");
    assertEquals(0.5, info.normalized(), 1e-9);
    assertEquals(0.0, info.baseNormalized(), 1e-9);

    // Polling again without touching anything reproduces the same effective reading —
    // regression check for the incident: a modulated read must never look like a frozen
    // base-value snapshot.
    Parameters.ParameterInfo again = Parameters.get(lx, channel.fader.getCanonicalPath());
    assertEquals(0.5, ((Number) again.value()).doubleValue(), 1e-9);
  }

  @Test
  void getOmitsModulationFieldsWhenNoModulationIsWired() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    Parameters.ParameterInfo info = Parameters.get(lx, channel.fader.getCanonicalPath());
    assertFalse(info.modulated());
    assertNull(info.baseValue());
    assertNull(info.baseNormalized());
  }

  @Test
  void getOmitsModulationFieldsWhenModulationIsDisabled() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.fader.setValue(0.0);
    MacroKnobs knobs =
        (MacroKnobs) Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    knobs.macro1.setValue(0.5);
    LXCompoundModulation modulation =
        Modulators.wireModulation(lx, lx.engine.modulation, knobs.macro1, channel.fader);
    modulation.range.setValue(1.0);

    // Modulation is active; verify fields are present.
    Parameters.ParameterInfo infoEnabled = Parameters.get(lx, channel.fader.getCanonicalPath());
    assertTrue(infoEnabled.modulated(), "active modulation is reported");
    assertNotNull(infoEnabled.baseValue(), "baseValue is present when modulated");

    // Disable the modulation's enabled parameter.
    modulation.enabled.setValue(false);

    // Now the modulation is ignored — describe should omit the modulation fields.
    Parameters.ParameterInfo infoDisabled = Parameters.get(lx, channel.fader.getCanonicalPath());
    assertFalse(infoDisabled.modulated(), "disabled modulation is not counted as active");
    assertNull(infoDisabled.baseValue(), "baseValue is omitted when no enabled modulation is wired");
    assertNull(infoDisabled.baseNormalized(), "baseNormalized is omitted when no enabled modulation is wired");
  }

  // ---- set: do -> undo -> assert restored, one per dispatched type ----

  @Test
  void setNumericParameterUndoes() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    String path = channel.fader.getCanonicalPath();
    double before = channel.fader.getValue();

    Parameters.ParameterInfo after = Parameters.set(lx, path, 0.25);
    assertEquals(0.25, channel.fader.getValue(), 1e-9);
    assertEquals(0.25, ((Number) after.value()).doubleValue(), 1e-9, "returns the new state");

    lx.command.undo();
    assertEquals(before, channel.fader.getValue(), 1e-9, "undo restores — proves a real LXCommand");
  }

  @Test
  void setBooleanParameterUndoes() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    boolean before = channel.enabled.isOn();

    Parameters.set(lx, channel.enabled.getCanonicalPath(), !before);
    assertEquals(!before, channel.enabled.isOn());

    lx.command.undo();
    assertEquals(before, channel.enabled.isOn());
  }

  @Test
  void setDiscreteEnumParameterUndoes() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    int before = channel.crossfadeGroup.getValuei();
    int target = (before == channel.crossfadeGroup.getMaxValue())
        ? channel.crossfadeGroup.getMinValue() : before + 1;

    Parameters.ParameterInfo after = Parameters.set(lx, channel.crossfadeGroup.getCanonicalPath(), target);
    assertEquals(target, channel.crossfadeGroup.getValuei());
    assertEquals(target, after.value(), "discrete value comes back as an integer");

    lx.command.undo();
    assertEquals(before, channel.crossfadeGroup.getValuei());
  }

  @Test
  void setStringParameterUndoes() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    String before = channel.label.getString();

    Parameters.set(lx, channel.label.getCanonicalPath(), "Renamed");
    assertEquals("Renamed", channel.label.getString());

    lx.command.undo();
    assertEquals(before, channel.label.getString());
  }

  @Test
  void setRejectsValueOfTheWrongType() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    double before = channel.fader.getValue();

    // String into a numeric parameter -> typed TYPE_MISMATCH, nothing mutated.
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Parameters.set(lx, channel.fader.getCanonicalPath(), "loud")).failure);
    assertEquals(before, channel.fader.getValue(), 1e-9, "a rejected set mutates nothing");

    // Non-integral into a discrete parameter -> rejected.
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Parameters.set(lx, channel.crossfadeGroup.getCanonicalPath(), 0.5)).failure);

    // Number into a boolean, and number into a string -> rejected.
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Parameters.set(lx, channel.enabled.getCanonicalPath(), 1)).failure);
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Parameters.set(lx, channel.label.getCanonicalPath(), 7)).failure);
  }

  @Test
  void setRejectsOutOfRangeDiscreteInsteadOfWrapping() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    int before = channel.crossfadeGroup.getValuei();

    // DiscreteParameter.updateValue wraps modulo the range — the primitive must reject first.
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Parameters.set(lx, channel.crossfadeGroup.getCanonicalPath(),
            channel.crossfadeGroup.getMaxValue() + 1));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("[" + channel.crossfadeGroup.getMinValue()),
        "error names the valid range");
    assertEquals(before, channel.crossfadeGroup.getValuei(), "a rejected set mutates nothing");
  }

  @Test
  void setRejectsAggregateParametersGenerally() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    // MidiFilterParameter bit-unpacks a raw double — a numeric set would scramble it.
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Parameters.set(lx, channel.midiFilter.getCanonicalPath(), 0.5));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("enabled"), "error points at the component paths");
  }

  @Test
  void setRejectsMomentaryTrigger() {
    LX lx = newHeadlessLx();
    // A trigger auto-resets to false synchronously — set(true) would fire side effects,
    // echo value=false, and push a no-op undo entry.
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Parameters.set(lx, lx.engine.tempo.trigger.getCanonicalPath(), true));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("trigger"), "error says why it is unsettable");
  }

  @Test
  void setModulatedParameterReportsEffectiveValueAndEchoesTheBaseSeparately() throws Exception {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXCompoundModulation modulation = new LXCompoundModulation(
        lx.engine.modulation, lx.engine.output.brightness, channel.fader);
    lx.engine.modulation.addModulation(modulation);
    modulation.range.setValue(0.5);

    Parameters.ParameterInfo after = Parameters.set(lx, channel.fader.getCanonicalPath(), 0.25);
    assertEquals(0.25, channel.fader.getBaseValue(), 1e-9);
    assertTrue(channel.fader.getValue() > 0.25 + 1e-6, "modulation rides on top of the base");
    assertTrue(after.modulated(), "a live compound modulation is wired to this target");
    assertEquals(channel.fader.getValue(), ((Number) after.value()).doubleValue(), 1e-9,
        "value is the live effective (modulated) reading, matching the render");
    assertEquals(0.25, ((Number) after.baseValue()).doubleValue(), 1e-9,
        "baseValue is the position just set");
    assertEquals(channel.fader.getNormalized(), after.normalized(), 1e-9);
    assertEquals(0.25, after.baseNormalized(), 1e-9);
  }

  // ---- fire: momentary triggers, deliberately outside LXCommand ----

  @Test
  void fireMomentaryBooleanFiresWiredTriggerModulations() {
    LX lx = newHeadlessLx();
    MacroTriggers triggers =
        (MacroTriggers) Modulators.addModulator(lx, lx.engine.modulation, MacroTriggers.class);
    Modulators.wireTrigger(lx, lx.engine.modulation, triggers.macro1, triggers.macro2);
    int[] sourceEdges = {0};
    int[] targetEdges = {0};
    triggers.macro1.addListener(p -> {
      if (triggers.macro1.isOn()) {
        sourceEdges[0]++;
      }
    });
    triggers.macro2.addListener(p -> {
      if (triggers.macro2.isOn()) {
        targetEdges[0]++;
      }
    });

    Parameters.FireInfo fire = Parameters.fire(lx, triggers.macro1.getCanonicalPath());
    assertEquals(1, sourceEdges[0], "exactly one press/release pulse");
    assertEquals(1, targetEdges[0], "the wired trigger modulation fired the target — end to end");
    assertFalse(fire.pending());
    assertEquals(false, fire.parameter().value(), "the value has auto-reset");
    assertEquals(false, triggers.macro1.isOn());
  }

  @Test
  void fireAlreadyHeldMomentaryStillDeliversARisingEdge() {
    LX lx = newHeadlessLx();
    MacroTriggers triggers =
        (MacroTriggers) Modulators.addModulator(lx, lx.engine.modulation, MacroTriggers.class);
    triggers.macro1.setValue(true); // held by a UI/MIDI press
    int[] risingEdges = {0};
    triggers.macro1.addListener(p -> {
      if (triggers.macro1.isOn()) {
        risingEdges[0]++;
      }
    });

    // setValue(true) on an already-true parameter is a no-op — fire releases first.
    Parameters.fire(lx, triggers.macro1.getCanonicalPath());
    assertEquals(1, risingEdges[0], "released then pulsed — the edge still happens");
    assertEquals(false, triggers.macro1.isOn());
  }

  @Test
  void fireTriggerParameterFiresAndResets() {
    LX lx = newHeadlessLx();
    // tempo.trigger is a real TriggerParameter — listeners see the rising edge before
    // the synchronous auto-reset (LX queues the listener-initiated reset).
    int[] fired = {0};
    lx.engine.tempo.trigger.addListener(p -> {
      if (lx.engine.tempo.trigger.isOn()) {
        fired[0]++;
      }
    });
    Parameters.FireInfo fire = Parameters.fire(lx, lx.engine.tempo.trigger.getCanonicalPath());
    assertEquals(1, fired[0], "the trigger actually raised");
    assertFalse(fire.pending());
    assertEquals(false, fire.parameter().value());
  }

  @Test
  void fireReportsPendingUnderLaunchQuantization() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    GradientPattern pattern = new GradientPattern(lx);
    channel.addPattern(pattern);
    // Quantize launches: the headless tempo never marks a division active, so a
    // quantized fire deterministically defers instead of firing.
    lx.engine.tempo.launchQuantization.setValue(1);

    Parameters.FireInfo fire = Parameters.fire(lx, pattern.launch.getCanonicalPath());
    assertTrue(fire.pending(),
        "a quantized launch is pending, not fired — clients must not re-fire");
  }

  @Test
  void fireRejectsNonMomentaryParameters() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    // A toggle boolean and a numeric parameter are set_parameter territory.
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Parameters.fire(lx, channel.enabled.getCanonicalPath())).failure);
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> Parameters.fire(lx, channel.fader.getCanonicalPath())).failure);
  }

  @Test
  void setRejectsAggregateColorDirectly() {
    LX lx = newHeadlessLx();
    // The default palette swatch always carries a dynamic color (an aggregate ColorParameter).
    String colorPath = lx.engine.palette.getSwatchColor(0).primary.getCanonicalPath();
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Parameters.set(lx, colorPath, "0xffff0000"));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("hue"), "error points at the component paths");

    // The components themselves are settable as numeric parameters.
    String huePath = lx.engine.palette.getSwatchColor(0).primary.hue.getCanonicalPath();
    Parameters.set(lx, huePath, 180.0);
    assertEquals(180.0, lx.engine.palette.getSwatchColor(0).primary.hue.getValue(), 1e-6);
  }
}
