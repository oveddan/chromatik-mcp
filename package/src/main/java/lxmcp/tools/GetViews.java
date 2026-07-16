package lxmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.Views;

public final class GetViews implements LxTool {

  @Override
  public String name() {
    return "get_views";
  }

  @Override
  public String description() {
    return "Named model subsets ('views', at /lx/structure/views/view/<n>) that a device's "
        + "'view' selector can clip its rendering to. A device left on 'Default' inherits its "
        + "view from its parent (effect -> pattern -> channel -> master -> whole model); "
        + "assigning a named view clips that device to only the matched points. "
        + "'selector' matches model tags (see modelTags for the vocabulary this project's "
        + "fixtures expose) with a small grammar: a bare tag ('cube') selects everything "
        + "tagged with it; space is a descendant match ('cube face' = faces inside cubes); "
        + "',' unions ('cube, sphere'); '&' intersects with the preceding match "
        + "('cube & active'); '>' requires a direct child ('cube > face'); ';' separates "
        + "independent groups within one selector; '*' groups by the left side and "
        + "sub-selects within each group on the right ('cube * face'); and a tag can carry an "
        + "index range in brackets — 'cube[0]', 'cube[2-5]', 'cube[even]', 'cube[odd]', "
        + "'cube[:2]' (every 2nd), 'cube[1:2]' (every 2nd starting at 1). "
        + "'numGroups'/'numFixtures' are live match feedback: they update automatically after "
        + "editing 'selector' via set_parameter on its path, so re-read the view (or "
        + "get_views again) to check whether an edited selector matched anything. Fire "
        + "'cuePath' (a momentary trigger, via fire_trigger) to preview a view in the "
        + "Chromatik UI — only one view cues at a time. 'assignments' lists which devices "
        + "currently reference each view (devices left on Default are omitted, since they "
        + "aren't referencing any view definition).";
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
    Views.ViewsSnapshot snapshot = Views.describe(lx);

    List<Map<String, Object>> views = new ArrayList<>();
    for (Views.ViewInfo view : snapshot.views()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", view.path());
      entry.put("label", view.label());
      entry.put("selector", view.selector());
      entry.put("enabled", view.enabled());
      entry.put("priority", view.priority());
      entry.put("normalization", view.normalization());
      entry.put("orientation", view.orientation());
      entry.put("numGroups", view.numGroups());
      entry.put("numFixtures", view.numFixtures());
      entry.put("cuePath", view.cuePath());
      views.add(entry);
    }

    List<Map<String, Object>> assignments = new ArrayList<>();
    for (Views.AssignmentInfo assignment : snapshot.assignments()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("viewPath", assignment.viewPath());
      entry.put("devicePath", assignment.devicePath());
      entry.put("deviceLabel", assignment.deviceLabel());
      assignments.add(entry);
    }

    List<Map<String, Object>> modelTags = new ArrayList<>();
    for (Views.TagInfo tag : snapshot.modelTags()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("tag", tag.tag());
      entry.put("count", tag.count());
      modelTags.add(entry);
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("views", views);
    payload.put("assignments", assignments);
    payload.put("modelTags", modelTags);
    return Result.ok(payload);
  }
}
