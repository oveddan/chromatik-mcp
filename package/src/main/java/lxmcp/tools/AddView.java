package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.model.LXView;

import lxmcp.domain.Views;

public final class AddView implements LxTool {

  @Override
  public String name() {
    return "add_view";
  }

  @Override
  public String description() {
    return "Compose a new named model subset ('view', at /lx/structure/views/view/<n>), "
        + "matched by a tag selector — see get_views for the selector grammar and the "
        + "modelTags vocabulary this project's fixtures expose. 'numFixtures'/'numGroups' in "
        + "the response are immediate match feedback: 0 fixtures is legal (an empty view) but "
        + "usually means a selector typo — cross-check against get_views' modelTags. Map a "
        + "device to the new view by setting its 'view' parameter (set_parameter) to the "
        + "view's label.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("label", Schemas.string("Display label for the new view, e.g. 'Cubes'"));
    properties.put("selector", Schemas.string(
        "Tag selector matched against the model, e.g. 'cube' or 'cube & active' — see "
            + "get_views for the full grammar"));
    properties.put("normalization", Schemas.enumString(
        "Whether point coordinates renormalize to the view's own bounds ('relative', "
            + "default) or keep the whole model's absolute bounds ('absolute')",
        List.of("relative", "absolute")));
    properties.put("orientation", Schemas.enumString(
        "Whether view points orient in absolute/global space ('global', default) or "
            + "relative to their matching group's own orientation ('group')",
        List.of("global", "group")));
    return Schemas.object(properties, List.of("label", "selector"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("label") instanceof String label) || label.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, "Required non-empty string argument: label");
    }
    if (!(args.get("selector") instanceof String selector)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: selector");
    }
    LXView.Normalization normalization;
    try {
      normalization = parseNormalization(args.get("normalization"));
    } catch (IllegalArgumentException e) {
      return Result.error(Result.INVALID_ARGUMENT, e.getMessage());
    }
    LXView.Orientation orientation;
    try {
      orientation = parseOrientation(args.get("orientation"));
    } catch (IllegalArgumentException e) {
      return Result.error(Result.INVALID_ARGUMENT, e.getMessage());
    }

    Views.ViewInfo view = Views.addView(lx, label, selector, normalization, orientation);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", view.path());
    payload.put("label", view.label());
    payload.put("selector", view.selector());
    payload.put("enabled", view.enabled());
    payload.put("priority", view.priority());
    payload.put("normalization", view.normalization());
    payload.put("orientation", view.orientation());
    payload.put("numGroups", view.numGroups());
    payload.put("numFixtures", view.numFixtures());
    payload.put("cuePath", view.cuePath());
    if (view.numFixtures() == 0) {
      payload.put("warning", "selector matched no fixtures — check modelTags from get_views");
    }
    return Result.ok(payload);
  }

  private static LXView.Normalization parseNormalization(Object value) {
    if (value == null) {
      return LXView.Normalization.RELATIVE;
    }
    if (!(value instanceof String s)) {
      throw new IllegalArgumentException("normalization must be a string");
    }
    return switch (s) {
      case "relative" -> LXView.Normalization.RELATIVE;
      case "absolute" -> LXView.Normalization.ABSOLUTE;
      default -> throw new IllegalArgumentException(
          "normalization must be 'relative' or 'absolute', got: " + s);
    };
  }

  private static LXView.Orientation parseOrientation(Object value) {
    if (value == null) {
      return LXView.Orientation.GLOBAL;
    }
    if (!(value instanceof String s)) {
      throw new IllegalArgumentException("orientation must be a string");
    }
    return switch (s) {
      case "global" -> LXView.Orientation.GLOBAL;
      case "group" -> LXView.Orientation.GROUP;
      default -> throw new IllegalArgumentException(
          "orientation must be 'global' or 'group', got: " + s);
    };
  }
}
