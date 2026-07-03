package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulator.MacroKnobs;

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
  void setModulatedParameterEchoesTheBaseValue() throws Exception {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXCompoundModulation modulation = new LXCompoundModulation(
        lx.engine.modulation, lx.engine.output.brightness, channel.fader);
    lx.engine.modulation.addModulation(modulation);
    modulation.range.setValue(0.5);

    Parameters.ParameterInfo after = Parameters.set(lx, channel.fader.getCanonicalPath(), 0.25);
    assertEquals(0.25, channel.fader.getBaseValue(), 1e-9);
    assertTrue(channel.fader.getValue() > 0.25 + 1e-6, "modulation rides on top of the base");
    assertEquals(0.25, ((Number) after.value()).doubleValue(), 1e-9,
        "the echoed value is the base value just set, not the modulated one");
    assertEquals(0.25, after.normalized(), 1e-9);
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
