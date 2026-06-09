package lxmcp;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;

@LXPlugin.Name("LX-MCP")
public class LxMcpPlugin implements LXPlugin {

  @Override
  public void initialize(LX lx) {
    LX.log("[LX-MCP] plugin loaded");
  }
}
