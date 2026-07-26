package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Projects;

import static chromatikmcp.tools.Payloads.putIfPresent;

public final class GetProjectInfo implements LxTool {

  @Override
  public String name() {
    return "get_project_info";
  }

  @Override
  public String description() {
    return "The open LX project: LX version, project file path (absent if never saved), "
        + "channel count, OSC engine state (receive/transmit ports and whether active), "
        + "engine output state, engine-global playback settings, and the model's link to an "
        + "external .lxm file. output.enabled is the engine's \"Live\" toggle — when false, "
        + "nothing reaches physical fixtures regardless of mixer state; set it via "
        + "set_parameter on output.enabledPath. output.gamma/gammaMode control the output "
        + "gamma curve. engine.speed is a global playback rate multiplier for animations "
        + "(1.0 = normal); engine.framesPerSecond caps the render loop rate. "
        + "model.file is the external .lxm this project's structure is bound to — absent when "
        + "the model is embedded in the project file, and absent when a static model is in use "
        + "(model.isStatic true). model.name is LX's display label for the model: the .lxm file "
        + "name when one is linked, \"<Embedded in Project>\", or \"<ClassName>.class\" for a "
        + "static model loaded from a project's staticModel entry — note a static model set at "
        + "construction reports model.isStatic true while model.name stays \"<Embedded in "
        + "Project>\". A trailing '*' on model.name means LX considers the structure dirty, and "
        + "appears only while a model file is linked. "
        + "When model.syncModelFile is true, LX rewrites model.file with the ENTIRE current "
        + "structure every time the project is serialized — not at the moment of mutation. The "
        + "write lands on the next serialization: a save in Chromatik, or LX's autosave timer, "
        + "which writes the project copy to the autosave folder but still exports the model to "
        + "the real model.file — so a shared .lxm can be rewritten with no explicit save at "
        + "all. That file may be referenced by other projects on the same rig, which this "
        + "server cannot detect. "
        + "Do NOT use model.hasUnsavedChanges to decide whether model.file is at risk. It is "
        + "LX's internal dirty flag, not a diff against the file: some structure edits set it "
        + "and others do not (renaming a fixture does not, and reload_fixtures never does even "
        + "though it can replace the whole model from edited .lxf files), while serialization "
        + "exports whatever is in memory either way. "
        + "The reliable rule: if model.syncModelFile is true, assume any structure work — "
        + "add_fixture, set_fixture_params, set_fixture_tags, move_fixture, remove_fixture, "
        + "duplicate_fixture, reload_fixtures, or set_parameter on a fixture parameter or on "
        + "one of lx.structure's own normalization parameters — will reach model.file at the "
        + "next save or autosave. Check model.syncModelFile BEFORE mutating structure. "
        + "Turning sync off afterwards is not a safe rescue: the project still records the file "
        + "reference, but LX ignores it when loading a project whose sync is off, so the "
        + "project reopens as an embedded-model project carrying your edits — and saving again "
        + "from there drops the reference for good.";
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
    Projects.ProjectInfo info = Projects.info(lx);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("lxVersion", info.lxVersion());
    putIfPresent(payload, "projectPath", info.projectPath());
    payload.put("channelCount", info.channelCount());
    Map<String, Object> osc = new LinkedHashMap<>();
    osc.put("receivePort", info.osc().receivePort());
    osc.put("receiveActive", info.osc().receiveActive());
    osc.put("transmitPort", info.osc().transmitPort());
    osc.put("transmitActive", info.osc().transmitActive());
    payload.put("osc", osc);
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("enabled", info.output().enabled());
    putIfPresent(output, "enabledPath", info.output().enabledPath());
    output.put("brightness", info.output().brightness());
    putIfPresent(output, "brightnessPath", info.output().brightnessPath());
    output.put("gamma", info.output().gamma());
    putIfPresent(output, "gammaPath", info.output().gammaPath());
    output.put("gammaMode", info.output().gammaMode());
    putIfPresent(output, "gammaModePath", info.output().gammaModePath());
    payload.put("output", output);
    Map<String, Object> engine = new LinkedHashMap<>();
    engine.put("speed", info.engine().speed());
    putIfPresent(engine, "speedPath", info.engine().speedPath());
    engine.put("framesPerSecond", info.engine().framesPerSecond());
    putIfPresent(engine, "framesPerSecondPath", info.engine().framesPerSecondPath());
    payload.put("engine", engine);
    payload.put("model", modelPayload(info.model()));
    return Result.ok(payload);
  }

  /**
   * The {@code model} block's payload shape, shared with {@link SaveModel} so a "Save
   * Model As" response echoes exactly what a follow-up {@code get_project_info} call would
   * report (docs/tool-conventions.md's shared-helper drill-down pattern — see {@code
   * GetChannel}/{@code ListChannels}).
   */
  static Map<String, Object> modelPayload(Projects.ModelInfo model) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", model.name());
    putIfPresent(payload, "file", model.file());
    payload.put("external", model.external());
    payload.put("isStatic", model.isStatic());
    if (model.external()) {
      payload.put("hasUnsavedChanges", model.hasUnsavedChanges());
    }
    payload.put("syncModelFile", model.syncModelFile());
    putIfPresent(payload, "syncModelFilePath", model.syncModelFilePath());
    return payload;
  }
}
