package lxmcp.ui;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;
import heronarts.lx.studio.LXStudio;

import lxmcp.LxMcpPlugin;
import lxmcp.ServerStatus;

/**
 * Chromatik-only companion to {@link LxMcpPlugin}: adds a left-pane status section.
 *
 * <p>This class references {@code heronarts.lx.studio.*}, which is absent from a pure-core
 * headless LX runtime. LX's package class loader (see {@code LXClassLoader.loadClassEntry})
 * catches {@link NoClassDefFoundError} per-class and skips the offending class rather than
 * failing the whole package, so in headless mode this plugin silently fails to register while
 * {@link LxMcpPlugin} (a plain {@link LXPlugin}, no studio dependency) keeps the MCP server
 * alive. This split is deliberate — never merge the two plugins into one.
 *
 * <p>{@link LxMcpPlugin} auto-enables this plugin's registry entry when it itself is
 * enabled ({@code LxMcpPlugin.autoEnableUiPlugin}), so users only ever need to check the
 * "LX-MCP" box — they never interact with an "LX-MCP UI" checkbox directly.
 */
@LXPlugin.Name("LX-MCP UI")
public class LxMcpUiPlugin implements LXStudio.Plugin {

  @Override
  public void initialize(LX lx) {}

  @Override
  public void initializeUI(LXStudio lx, LXStudio.UI ui) {}

  @Override
  public void onUIReady(LXStudio lx, LXStudio.UI ui) {
    ServerStatus status = LxMcpPlugin.status();
    if (status == null) {
      LX.log("[LX-MCP] core LX-MCP plugin not enabled; skipping status UI section");
      return;
    }
    new UILxMcpSection(ui, status).addToContainer(ui.leftPane.global);
  }
}
