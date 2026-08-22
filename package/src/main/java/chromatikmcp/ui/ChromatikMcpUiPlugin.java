package chromatikmcp.ui;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;
import heronarts.lx.studio.LXStudio;

import chromatikmcp.Log;
import chromatikmcp.ChromatikMcpPlugin;
import chromatikmcp.ServerStatus;
import chromatikmcp.domain.Cameras;
import chromatikmcp.domain.PointStyle;

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
 * <p>It also binds the camera tools to Chromatik's main 3D preview
 * ({@code LXStudio.UI.preview}), which is why {@code set_camera} moves what a person is
 * watching here and moves only a held viewpoint headless. The aux preview window is
 * deliberately left alone — one camera surface, one window.
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

  // Held so dispose() can unbind it; null whenever nothing is bound.
  private Cameras boundCameras;
  private PointStyle boundPointStyle;

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

    // Non-null whenever status is (both are published by the same initialize()), but the
    // camera surface degrades to headless behavior rather than failing the UI section.
    Cameras cameras = ChromatikMcpPlugin.cameras();
    if (cameras != null) {
      cameras.bindPreview(new PreviewCameraBinding(ui.preview));
      this.boundCameras = cameras;
    }
    PointStyle pointStyle = ChromatikMcpPlugin.pointStyle();
    if (pointStyle != null) {
      pointStyle.bindPreview(new PreviewPointStyleBinding(ui.preview));
      this.boundPointStyle = pointStyle;
    }
  }

  @Override
  public void dispose() {
    if (this.boundPointStyle != null) {
      this.boundPointStyle.unbindPreview();
      this.boundPointStyle = null;
    }
    // First, so nothing can reach a UI3dContext that is on its way out — the camera store
    // outlives this plugin and would otherwise keep driving a disposed preview.
    if (this.boundCameras != null) {
      this.boundCameras.unbindPreview();
      this.boundCameras = null;
    }
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
