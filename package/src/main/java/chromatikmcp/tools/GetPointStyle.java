package chromatikmcp.tools;

import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.PointStyle;

public final class GetPointStyle implements LxTool {

  private final PointStyle pointStyle;

  public GetPointStyle(PointStyle pointStyle) {
    this.pointStyle = pointStyle;
  }

  @Override
  public String name() {
    return "get_point_style";
  }

  @Override
  public String description() {
    return "Read the LED point-rendering settings used by Chromatik's live main 3D preview, "
        + "including point size, sparkle, LED style, gamma, depth, and directional controls. "
        + "Each entry uses the ordinary parameter wire shape (value, type, range, options, "
        + "units, and formatting), with name in place of a canonical path because UIPointCloud "
        + "is not an LXComponent. Unavailable headless. These settings affect only the preview "
        + "a person watches: get_frame uses an independent filled-disc raster and does not show "
        + "sparkle, LED style, or any other preview point-style setting.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    return Result.ok(PointStylePayload.settings(this.pointStyle.get()));
  }
}
