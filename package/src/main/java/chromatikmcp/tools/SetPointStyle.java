package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.PointStyle;

public final class SetPointStyle implements LxTool {

  private final PointStyle pointStyle;

  public SetPointStyle(PointStyle pointStyle) {
    this.pointStyle = pointStyle;
  }

  @Override
  public String name() {
    return "set_point_style";
  }

  @Override
  public String description() {
    return "Set one LED point-rendering setting on Chromatik's live main 3D preview. Numeric, "
        + "boolean, and discrete values follow set_parameter's rules; discrete/enum settings "
        + "accept an option name string as well as an integer index (for example ledStyle: "
        + "'CIRCLE'). The response is the resulting ordinary parameter wire shape plus its "
        + "setting name. Unavailable headless and not undoable with Cmd-Z. This changes only "
        + "the preview a person watches: get_frame uses an independent filled-disc raster and "
        + "does not show sparkle, LED style, or any other preview point-style setting.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("setting", Schemas.string(
        "Point-style setting name returned by get_point_style (for example sparkleAmount or ledStyle)"));
    Map<String, Object> valueSchema = new LinkedHashMap<>();
    valueSchema.put("type", List.of("number", "boolean", "string"));
    valueSchema.put("description",
        "New value; discrete/enum settings also accept an exact option name string");
    properties.put("value", valueSchema);
    return Schemas.object(properties, List.of("setting", "value"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String setting = Args.requireString(args, "setting");
    if (!args.containsKey("value")) {
      return Result.error(Result.INVALID_ARGUMENT, "Required argument: value");
    }
    return Result.ok(PointStylePayload.setting(this.pointStyle.set(setting, args.get("value"))));
  }
}
