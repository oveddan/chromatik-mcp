package chromatikmcp.tools;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Projects;

public final class SaveProject implements LxTool {

  @Override
  public String name() {
    return "save_project";
  }

  @Override
  public String description() {
    return "Persist the running session to a .lxp project file — the only way structure/"
        + "mixer/modulation changes made over this API survive a restart; until this is "
        + "called, they exist only in the running engine. path omitted saves in place over "
        + "the currently open project (invalid_argument if none is open yet — save-as with "
        + "a path first); a given path resolves relative paths under LX's Projects media "
        + "folder, absolute paths are used as-is. overwrite (default false) is required to "
        + "replace an existing file other than the currently open project — omitting it "
        + "against an existing target returns invalid_argument naming the resolved path "
        + "instead of clobbering it. Hazard: when the project's model is linked to an "
        + "external .lxm with syncModelFile on (see get_project_info's model block), saving "
        + "the project ALSO rewrites that .lxm — even though nothing about this call "
        + "mentions the model — and that file may be shared by other projects on the rig; "
        + "check model.syncModelFile first, or save_model to a new path before calling this "
        + "if you don't want the shared file touched. The response echoes "
        + "get_project_info's model block so a client can tell, without a separate call, "
        + "whether this save also rewrote a linked .lxm. This is a file write, not undoable "
        + "engine state — it does not appear in undo history. In the Chromatik UI, saving "
        + "over a dirty external model may raise a confirmation dialog; this call does not "
        + "suppress it. When batched via apply_operations, check this operation's own "
        + "result — apply_operations reports top-level ok regardless of individual "
        + "operation failures (see apply_operations' description).";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Target .lxp path. Omit to save in place over the currently open project. Relative "
            + "paths resolve under LX's Projects media folder; absolute paths are used "
            + "as-is."));
    properties.put("overwrite", Schemas.bool(
        "Required (true) to replace an existing file other than the currently open "
            + "project (default false)"));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object pathArg = args.get("path");
    String path = null;
    if (pathArg != null) {
      if (!(pathArg instanceof String s) || s.isBlank()) {
        return Result.error(Result.INVALID_ARGUMENT, "path must be a non-empty string");
      }
      path = s;
    }
    boolean overwrite = args.get("overwrite") instanceof Boolean b && b;
    File target = Projects.resolveProjectSaveFile(lx, path, overwrite);
    File saved = Projects.saveProject(lx, target);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", saved.getAbsolutePath());
    payload.put("model", GetProjectInfo.modelPayload(Projects.info(lx).model()));
    return Result.ok(payload);
  }
}
