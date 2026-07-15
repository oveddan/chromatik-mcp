package lxmcp;

import java.io.File;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;

import lxmcp.engine.EngineExecutor;
import lxmcp.mcp.EmbeddedMcpServer;
import lxmcp.mcp.StatusFile;
import lxmcp.tools.Tools;

@LXPlugin.Name("LX-MCP")
public class LxMcpPlugin implements LXPlugin {

  private static final String PREFIX = "[LX-MCP] ";
  private static final String SERVER_NAME = "LX-MCP";
  private static final String SERVER_VERSION = "0.0.1";

  private EmbeddedMcpServer server;

  @Override
  public void initialize(LX lx) {
    LX.log(PREFIX + "plugin loaded");
    // Let failures propagate: LX wraps initialize() and surfaces them via
    // pushError (user-facing) + marks the plugin as errored. Swallowing here
    // would leave the plugin looking healthy while the server is down.
    this.server = EmbeddedMcpServer.start(
        SERVER_NAME, SERVER_VERSION, 0,
        Tools.specifications(lx, new EngineExecutor(lx)), Tools.INSTRUCTIONS);
    File project = lx.getProject();
    StatusFile.write(this.server.port(), project == null ? null : project.getAbsolutePath(), LX.VERSION);
    LX.log(PREFIX + "MCP server listening on port " + this.server.port());
  }

  @Override
  public void dispose() {
    if (this.server != null) {
      this.server.stop();
      this.server = null;
    }
  }
}
