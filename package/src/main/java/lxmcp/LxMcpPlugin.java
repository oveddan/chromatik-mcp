package lxmcp;

import java.io.File;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;

import lxmcp.mcp.EmbeddedMcpServer;
import lxmcp.mcp.StatusFile;

@LXPlugin.Name("LX-MCP")
public class LxMcpPlugin implements LXPlugin {

  private static final String SERVER_NAME = "LX-MCP";
  private static final String SERVER_VERSION = "0.0.1";

  private EmbeddedMcpServer server;

  @Override
  public void initialize(LX lx) {
    LX.log("[LX-MCP] plugin loaded");
    try {
      this.server = EmbeddedMcpServer.start(SERVER_NAME, SERVER_VERSION, 0);
      File project = lx.getProject();
      StatusFile.write(this.server.port(), project == null ? null : project.getAbsolutePath(), LX.VERSION);
      LX.log("[LX-MCP] MCP server listening on port " + this.server.port());
    } catch (Exception e) {
      LX.error(e, "[LX-MCP] failed to start MCP server");
    }
  }
}
