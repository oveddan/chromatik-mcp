package chromatikmcp.ui;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;
import heronarts.lx.studio.LXStudio;

import chromatikmcp.Log;
import chromatikmcp.ChromatikMcpPlugin;
import chromatikmcp.ServerStatus;

/**
 * Chromatik-only companion to {@link ChromatikMcpPlugin}: adds a left-pane status section.
 *
 * <p>This class references {@code heronarts.lx.studio.*}, which is absent from a pure-core
 * headless LX runtime. LX's package class loader (see {@code LXClassLoader.loadClassEntry})
 * catches {@link NoClassDefFoundError} per-class and skips the offending class rather than
 * failing the whole package, so in headless mode this plugin silently fails to register while
 * {@link ChromatikMcpPlugin} (a plain {@link LXPlugin}, no studio dependency) keeps the MCP server
 * alive. This split is deliberate — never merge the two plugins into one.
 *
 * <p>{@link ChromatikMcpPlugin} auto-enables this plugin's registry entry when it itself is
 * enabled ({@code ChromatikMcpPlugin.autoEnableUiPlugin}), so users only ever need to check the
 * "Chromatik-MCP" box — they never interact with an "Chromatik-MCP UI" checkbox directly.
 */
@LXPlugin.Name("Chromatik-MCP UI")
public class ChromatikMcpUiPlugin implements LXStudio.Plugin {

  // Held so dispose() can tear it down symmetrically; null until onUIReady runs (headless,
  // or the core plugin disabled) and null again after dispose().
  private UIChromatikMcpSection section;

  @Override
  public void initialize(LX lx) {}

  @Override
  public void initializeUI(LXStudio lx, LXStudio.UI ui) {}

  @Override
  public void onUIReady(LXStudio lx, LXStudio.UI ui) {
    ServerStatus status = ChromatikMcpPlugin.status();
    if (status == null) {
      Log.log("core Chromatik-MCP plugin not enabled; skipping status UI section");
      return;
    }
    this.section = new UIChromatikMcpSection(ui, status);
    this.section.addToContainer(ui.leftPane.global);
  }

  @Override
  public void dispose() {
    // Symmetric with onUIReady's addToContainer: removeFromContainer() detaches from the
    // parent's child list (and triggers a reflow) but does not release resources;
    // dispose() releases the ServerStatus parameter listener but does not touch the
    // parent. Both are needed, in this order, to avoid leaving the parent with a
    // reference to an about-to-be-disposed child.
    if (this.section != null) {
      this.section.removeFromContainer();
      this.section.dispose();
      this.section = null;
    }
  }
}
