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
        + "engine output state, and engine-global playback settings. output.enabled is the "
        + "engine's \"Live\" toggle — when false, nothing reaches physical fixtures "
        + "regardless of mixer state; set it via set_parameter on output.enabledPath. "
        + "output.gamma/gammaMode control the output gamma curve. engine.speed is a global "
        + "playback rate multiplier for animations (1.0 = normal); engine.framesPerSecond "
        + "caps the render loop rate.";
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
    return Result.ok(payload);
  }
}
