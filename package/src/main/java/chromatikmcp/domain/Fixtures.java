package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.LXFixture;
import heronarts.lx.structure.LXProtocolFixture;

/**
 * Read-only snapshot of the fixture layer: the physical wiring beneath the model tree
 * ({@link Model}/{@code describe_model}) — each fixture's geometry transform, output
 * protocol wiring, and (for {@link JsonFixture}) its declared parameters.
 */
public final class Fixtures {

  public record TransformInfo(double x, double y, double z,
      double yaw, double pitch, double roll, double scale) {}

  /**
   * {@code protocol}/{@code byteOrder} report the raw LX enum constant name (e.g.
   * {@code ARTNET}, {@code RGB}) rather than a lower-cased label — these are wiring
   * identifiers, not UI copy. {@code universe}/{@code channel}/{@code port} are already
   * resolved per-protocol (e.g. {@code universe} is {@code artNetUniverse} for ARTNET/SACN
   * but {@code ddpDataOffset} for DDP) — see {@link #protocolPort}/{@link #protocolUniverse}/
   * {@link #protocolChannel}.
   */
  public record OutputInfo(String protocol, String host, int port, int universe, int channel,
      String byteOrder, boolean reverse) {}

  /** Present only for a {@link JsonFixture}: its {@code error}/{@code warning} state. */
  public record JsonInfo(String fixturePath, boolean error, String errorMessage,
      List<String> warnings) {}

  /** One parameter the {@code .lxf} file declares (a {@code JsonFixture}-only knob). */
  public record JsonParameterInfo(String name, String label, String type, Object value) {}

  public record FixtureInfo(String path, int id, int index, String label, String type,
      int size, Integer firstIndex, Integer lastIndex, boolean enabled, boolean deactivate,
      double brightness, List<String> tags, int childCount, TransformInfo transform,
      OutputInfo output, JsonInfo json) {}

  public record FixturesSnapshot(String modelName, boolean isStatic, int totalPoints,
      String outputError, List<FixtureInfo> fixtures) {}

  private Fixtures() {}

  /** Call on the engine thread; the returned records are safe to read anywhere. */
  public static FixturesSnapshot describe(LX lx) {
    List<FixtureInfo> fixtures = new ArrayList<>();
    for (LXFixture fixture : lx.structure.fixtures) {
      fixtures.add(describeFixture(fixture));
    }
    // outputError defaults to null (unset), not "" — normalize so the wire payload always
    // carries a string, and "empty when clean" (per the tool description) is unambiguous.
    String outputError = lx.structure.outputError.getString();
    return new FixturesSnapshot(
        lx.structure.modelName.getString(),
        lx.structure.isStatic.isOn(),
        lx.getModel().size,
        (outputError == null) ? "" : outputError,
        fixtures);
  }

  /** Describe a single fixture. Call on the engine thread. */
  public static FixtureInfo describeFixture(LXFixture fixture) {
    int size = fixture.totalSize();
    Integer firstIndex = null;
    Integer lastIndex = null;
    if (size > 0) {
      firstIndex = fixture.getIndexBufferOffset();
      lastIndex = firstIndex + size - 1;
    }
    LXModel model = fixture.getModel();
    return new FixtureInfo(
        Resolve.canonicalPath(fixture),
        fixture.getId(),
        fixture.getIndex(),
        fixture.getLabel(),
        typeOf(fixture),
        size,
        firstIndex,
        lastIndex,
        fixture.enabled.isOn(),
        fixture.deactivate.isOn(),
        fixture.brightness.getValue(),
        List.copyOf(fixture.tagList),
        model.children.length,
        new TransformInfo(
            fixture.x.getValue(), fixture.y.getValue(), fixture.z.getValue(),
            fixture.yaw.getValue(), fixture.pitch.getValue(), fixture.roll.getValue(),
            fixture.scale.getValue()),
        (fixture instanceof LXProtocolFixture protocolFixture) ? describeOutput(protocolFixture) : null,
        (fixture instanceof JsonFixture jsonFixture) ? describeJson(jsonFixture) : null);
  }

  private static String typeOf(LXFixture fixture) {
    if (fixture instanceof JsonFixture jsonFixture) {
      String fixturePath = jsonFixture.getFixturePath();
      return fixturePath.isEmpty() ? fixture.getClass().getSimpleName() : fixturePath;
    }
    return fixture.getClass().getSimpleName();
  }

  private static OutputInfo describeOutput(LXProtocolFixture fixture) {
    return new OutputInfo(
        fixture.protocol.getEnum().name(),
        fixture.host.getString(),
        protocolPort(fixture),
        protocolUniverse(fixture),
        protocolChannel(fixture),
        fixture.byteOrder.getEnum().name(),
        fixture.reverse.isOn());
  }

  private static JsonInfo describeJson(JsonFixture fixture) {
    return new JsonInfo(
        fixture.getFixturePath(),
        fixture.error.isOn(),
        fixture.errorMessage.getString(),
        List.copyOf(fixture.warnings));
  }

  /**
   * The knobs a {@code JsonFixture}'s {@code .lxf} file declares via its
   * {@code "parameters"} block — a caller can set any of these through {@code
   * set_parameter} on {@code <fixturePath>/<name>} like any other fixture parameter; this
   * just surfaces what exists and its current value, since the set is JSON-file-defined
   * and not otherwise discoverable. Call on the engine thread.
   */
  public static List<JsonParameterInfo> jsonParameters(JsonFixture fixture) {
    List<JsonParameterInfo> parameters = new ArrayList<>();
    for (JsonFixture.ParameterDefinition definition : fixture.getJsonParameters()) {
      Object value = switch (definition.type) {
        case STRING -> definition.stringParameter.getString();
        case STRING_SELECT -> definition.stringSelectParameter.getObject();
        case INT -> definition.intParameter.getValuei();
        case FLOAT -> definition.floatParameter.getValue();
        case BOOLEAN -> definition.booleanParameter.isOn();
      };
      parameters.add(new JsonParameterInfo(
          definition.name, definition.label, definition.type.name(), value));
    }
    return parameters;
  }

  // LXProtocolFixture.getProtocolPort/Universe/Channel/Priority() and
  // resolveHostAddress() carry exactly this per-protocol mapping, but are `protected` —
  // unreachable from outside heronarts.lx.structure. Replicated here against the public
  // parameters only (LXProtocolFixture.java:143-217 mirrors this logic).

  private static int protocolPort(LXProtocolFixture fixture) {
    LXFixture.Protocol protocol = fixture.protocol.getEnum();
    return switch (protocol) {
      case OPC -> fixture.port.getValuei();
      default -> protocol.defaultPort;
    };
  }

  private static int protocolUniverse(LXProtocolFixture fixture) {
    return switch (fixture.protocol.getEnum()) {
      case ARTNET, SACN -> fixture.artNetUniverse.getValuei();
      case DDP -> fixture.ddpDataOffset.getValuei();
      case KINET -> fixture.kinetPort.getValuei();
      case OPC -> fixture.opcChannel.getValuei();
      default -> 0;
    };
  }

  private static int protocolChannel(LXProtocolFixture fixture) {
    return switch (fixture.protocol.getEnum()) {
      case ARTNET, SACN, KINET -> fixture.dmxChannel.getValuei();
      case OPC -> fixture.opcOffset.getValuei();
      default -> 0;
    };
  }
}
