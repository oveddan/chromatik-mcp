package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;

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
  void unknownPathReturnsNull() {
    LX lx = newHeadlessLx();
    assertNull(Parameters.get(lx, "/lx/nope/nothing"));
    assertNull(Parameters.get(lx, "/lx/mixer/channel/99/fader"));
    assertNull(Parameters.get(lx, "/lx/mixer"), "a component path is not a parameter");
  }
}
