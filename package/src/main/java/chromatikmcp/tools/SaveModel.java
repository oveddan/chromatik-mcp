package chromatikmcp.tools;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Projects;

public final class SaveModel implements LxTool {

  @Override
  public String name() {
    return "save_model";
  }

  @Override
  public String description() {
    return "\"Save Model As\": export the project's structure (fixtures, normalization, "
        + "label config) to an external .lxm file and re-point the project's model link at "
        + "it (LXStructure.exportModel). path omitted re-exports to the currently linked "
        + "model file (invalid_argument if the model isn't linked to one yet — see "
        + "get_project_info's model.file); a given path resolves relative paths under LX's "
        + "Models media folder and moves the link there, absolute paths are used as-is. "
        + "overwrite (default false) is required to replace an existing file other than the "
        + "currently linked one — omitting it against an existing target returns "
        + "invalid_argument naming the resolved path instead of clobbering it. This is the "
        + "fix for the shared-.lxm hazard save_project's description warns about: export to "
        + "a NEW path here before calling save_project, rather than disabling "
        + "syncModelFile, so the .lxm other projects on the rig load is never touched. The "
        + "response echoes get_project_info's model block so a client can confirm the link "
        + "moved. model.external false while model.file is set means the link will not "
        + "survive a reload (see get_project_info's model.file/model.external description). "
        + "This is a file write, not undoable engine state — it does not appear in "
        + "undo history. Requires a dynamic structure — invalid_argument if model.isStatic "
        + "is true, since a static model has no fixture-based model to export.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Target .lxm path. Omit to re-export to the currently linked model file. Relative "
            + "paths resolve under LX's Models media folder; absolute paths are used "
            + "as-is."));
    properties.put("overwrite", Schemas.bool(
        "Required (true) to replace an existing file other than the currently linked "
            + "model file (default false)"));
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
    File target = Projects.resolveModelSaveFile(lx, path, overwrite);
    Projects.saveModel(lx, target);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", target.getAbsolutePath());
    payload.put("model", GetProjectInfo.modelPayload(Projects.info(lx).model()));
    return Result.ok(payload);
  }
}
