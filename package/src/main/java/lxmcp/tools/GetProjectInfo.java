package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.Projects;

public final class GetProjectInfo implements LxTool {

  @Override
  public String name() {
    return "get_project_info";
  }

  @Override
  public String description() {
    return "The open LX project: LX version, project file path (absent if never saved), "
        + "channel count, and OSC engine state (receive/transmit ports and whether active).";
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
    if (info.projectPath() != null) {
      payload.put("projectPath", info.projectPath());
    }
    payload.put("channelCount", info.channelCount());
    Map<String, Object> osc = new LinkedHashMap<>();
    osc.put("receivePort", info.osc().receivePort());
    osc.put("receiveActive", info.osc().receiveActive());
    osc.put("transmitPort", info.osc().transmitPort());
    osc.put("transmitActive", info.osc().transmitActive());
    payload.put("osc", osc);
    return Result.ok(payload);
  }
}
