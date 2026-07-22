package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.structure.view.LXViewDefinition;

import chromatikmcp.domain.Resolve;
import chromatikmcp.domain.Views;

public final class RemoveView implements LxTool {

  @Override
  public String name() {
    return "remove_view";
  }

  @Override
  public String description() {
    return "Remove a view by its canonical path (as returned by add_view/get_views). "
        + "Devices selecting a different, surviving view are unaffected. But a device whose "
        + "'view' selector pointed at the removed view is NOT reset to Default — LX only "
        + "clamps the selector's stored index into the shrunk view list, so it silently "
        + "reassigns to whichever view (or Default) now sits at that index. Re-check device "
        + "'view' assignments (get_views' assignments) after removing a view rather than "
        + "assuming they reset — undo does not fix this trap either; remap affected devices "
        + "to Default before removing a view they still reference.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the view to remove, e.g. /lx/structure/views/view/1"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("path") instanceof String path)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: path");
    }
    LXViewDefinition view = Resolve.component(lx, path, LXViewDefinition.class);
    Views.removeView(lx, view);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", path);
    payload.put("kind", "view");
    return Result.ok(payload);
  }
}
