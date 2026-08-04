package chromatikmcp.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.domain.OscParams;

class OscParamsServletSerializationTest {

  @Test
  void serializerPreservesOptionalFieldsAndEstablishedOrder() {
    OscParams.OscParamInfo info = new OscParams.OscParamInfo(
        "/lx/mixer/channel/1/fader",
        "Fader",
        "/lx/mixer/channel/1/fader",
        "CompoundParameter",
        "/lx/mixer/channel/1",
        "Channel 1",
        "LXChannel",
        "PERCENT_NORMALIZED",
        0.0,
        1.0,
        null);

    Map<String, Object> payload = OscParamsServlet.toMap(info);

    assertEquals(List.of(
        "oscAddress", "label", "path", "type", "componentPath", "componentLabel",
        "componentType", "units", "min", "max"),
        List.copyOf(payload.keySet()));
    assertEquals(0.0, payload.get("min"));
    assertEquals(1.0, payload.get("max"));
    assertFalse(payload.containsKey("value"));
  }

  @Test
  void serializerIncludesStringValueAndOmitsAbsentBounds() {
    OscParams.OscParamInfo info = new OscParams.OscParamInfo(
        "/lx/mixer/channel/1/label",
        "Label",
        "/lx/mixer/channel/1/label",
        "StringParameter",
        "/lx/mixer/channel/1",
        "Channel 1",
        "LXChannel",
        "NONE",
        null,
        null,
        "My Channel");

    Map<String, Object> payload = OscParamsServlet.toMap(info);

    assertFalse(payload.containsKey("min"));
    assertFalse(payload.containsKey("max"));
    assertEquals("My Channel", payload.get("value"));
  }
}
