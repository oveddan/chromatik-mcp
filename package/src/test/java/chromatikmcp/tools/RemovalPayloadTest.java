package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RemovalPayloadTest {

  @Test
  void serializesTheSharedRemoveShape() {
    assertEquals(
        Map.of("removed", "/lx/mixer/channel/1", "kind", "channel"),
        RemovalPayload.of("/lx/mixer/channel/1", "channel"));
  }

  @Test
  void colorIncludesItsSwatchContextWithoutChangingTheSharedKeys() {
    assertEquals(
        Map.of(
            "removed", "/lx/palette/swatch/color/2",
            "swatch", "/lx/palette/swatch",
            "kind", "color"),
        RemovalPayload.color("/lx/palette/swatch/color/2", "/lx/palette/swatch"));
  }
}
