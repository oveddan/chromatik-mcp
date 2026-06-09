package lxmcp;

import java.io.File;

import heronarts.lx.LX;

/**
 * Headless verification harness (PR-0). Boots LX with no UI, force-enables the
 * LX-MCP plugin, and lets the registry run its {@code initialize()}. A freshly
 * dropped plugin is disabled by default, so the only way to exercise
 * initialize() without the Chromatik UI is to enable it explicitly here.
 *
 * Lives in src/test so Maven keeps it out of the shipping package jar. Invoked
 * by scripts/verify-load.sh. PR-1c formalizes the headless test harness; this is
 * the minimal seed.
 */
public final class HeadlessLoadCheck {

  private HeadlessLoadCheck() {}

  public static void main(String[] args) {
    LX.Flags flags = new LX.Flags();
    flags.loadPreferences = false;
    // LX scans <mediaPath>/Packages. main()/headless() derive this from
    // user.home via bootstrapMediaPath (a protected method we can't call from
    // here), so replicate it: point mediaPath at <user.home>/LXStudio.
    flags.mediaPath = new File(System.getProperty("user.home"), "LXStudio").getPath();
    // Enable by class-name string: the plugin class is loaded by LX's own child
    // classloader from the Packages dir, so we must not reference it directly.
    flags.enabledPlugins.add("lxmcp.LxMcpPlugin");

    // The LX constructor scans <user.home>/LXStudio/Packages, registers the
    // plugin, and calls initialize() on it (enabled plugins only).
    new LX(flags);

    LX.log("[verify] LX construction complete");
    System.exit(0);
  }
}
