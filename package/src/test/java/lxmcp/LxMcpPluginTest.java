package lxmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;

/**
 * Covers {@link LxMcpPlugin#autoEnableUiPlugin} against a real {@code LXRegistry.Plugin}
 * list — the "LX-MCP UI" companion should get flipped on whenever "LX-MCP" is, so users
 * only ever interact with one checkbox.
 */
class LxMcpPluginTest {

  private static final String UI_PLUGIN_CLASS_NAME = "lxmcp.ui.LxMcpUiPlugin";

  private LX lx;

  @AfterEach
  void tearDown() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }

  @Test
  void enablesDisabledUiPluginEntry() {
    // Classpath-plugin registration force-enables the entry (LXRegistry.Plugin.cliEnabled);
    // disable it manually to reproduce "entry present, not yet enabled" before exercising
    // the auto-enable logic.
    LX.Flags flags = new LX.Flags();
    flags.classpathPlugins.add(UI_PLUGIN_CLASS_NAME);
    LXModel model = new GridModel(8, 8);
    this.lx = new LX(flags, model);

    heronarts.lx.LXRegistry.Plugin uiPlugin = findUiPlugin(this.lx);
    uiPlugin.setEnabled(false);
    assertTrue(!uiPlugin.isEnabled(), "test fixture must start disabled");

    LxMcpPlugin.autoEnableUiPlugin(this.lx.registry.plugins);

    assertTrue(uiPlugin.isEnabled(), "auto-enable should flip the UI companion plugin on");
  }

  @Test
  void noOpWhenUiPluginEntryIsAbsent() {
    LXModel model = new GridModel(8, 8);
    this.lx = new LX(model);

    assertEquals(0, this.lx.registry.plugins.size(), "headless fixture should have no registered plugins");

    // Must not throw when the UI class was never scanned into the registry (headless).
    LxMcpPlugin.autoEnableUiPlugin(this.lx.registry.plugins);
  }

  private static heronarts.lx.LXRegistry.Plugin findUiPlugin(LX lx) {
    for (heronarts.lx.LXRegistry.Plugin plugin : lx.registry.plugins) {
      if (plugin.clazz.getName().equals(UI_PLUGIN_CLASS_NAME)) {
        return plugin;
      }
    }
    throw new IllegalStateException("test fixture: LX-MCP UI plugin entry not found in registry");
  }
}
