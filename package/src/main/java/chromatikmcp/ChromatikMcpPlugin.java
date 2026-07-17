package chromatikmcp;

import java.io.File;
import java.io.UncheckedIOException;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXLoopTask;
import heronarts.lx.LXPlugin;
import heronarts.lx.LXRegistry;

import chromatikmcp.engine.EngineExecutor;
import chromatikmcp.mcp.BuildInfo;
import chromatikmcp.mcp.ConfigFile;
import chromatikmcp.mcp.ConnectionTracker;
import chromatikmcp.mcp.EmbeddedMcpServer;
import chromatikmcp.mcp.StatusFile;
import chromatikmcp.tools.GetStatus;
import chromatikmcp.tools.Tools;

@LXPlugin.Name("Chromatik-MCP")
public class ChromatikMcpPlugin implements LXPlugin {

  private static final String SERVER_NAME = "Chromatik-MCP";

  // Referenced by fully-qualified name, never `ChromatikMcpUiPlugin.class` — this class must stay
  // free of any compile-time coupling to the `ui` package (ChromatikMcpUiPlugin already imports
  // this class; a reference the other way would create a cycle).
  private static final String UI_PLUGIN_CLASS_NAME = "chromatikmcp.ui.ChromatikMcpUiPlugin";

  // LXPlugin instances are singletons per LX instance, and LXStudio runs
  // onUIReady() for every plugin only after every plugin's initialize() has
  // returned — so ChromatikMcpUiPlugin.onUIReady() is guaranteed to observe the
  // status set below, never a stale null from a race. JVM-wide (not per-LX):
  // two LX instances in one JVM would cross-wire this field, but Chromatik
  // never runs more than one LX per process, so that's an acceptable limit.
  private static volatile ServerStatus currentStatus;

  private LX lx;
  private ServerStatus status;
  private EmbeddedMcpServer server;
  private LXLoopTask loopTask;

  @Override
  public void initialize(LX lx) {
    Log.log("plugin loaded");
    ConfigFile.Config config = ConfigFile.load(ConfigFile.path());
    if (!config.isLoopback()) {
      Log.error("SECURITY WARNING: binding MCP server to non-loopback host '" + config.host()
          + "'. This server is UNAUTHENTICATED and can mutate a live show — anyone who can reach "
          + "this address has full control. Remove \"host\" from " + ConfigFile.path()
          + " to restore loopback-only.");
    }

    autoEnableUiPlugin(lx.registry.plugins);

    this.lx = lx;
    this.status = new ServerStatus();
    // Constructed here (not inside EmbeddedMcpServer.start()) and passed in, so the
    // get_status supplier below can close over this tracker directly instead of over
    // `this.server` — a client could otherwise call get_status between tomcat.start()
    // returning and `this.server` being assigned a few lines down, hitting a null
    // dereference (only possible with a fixed configured port racy enough to matter).
    ConnectionTracker connectionTracker = new ConnectionTracker();
    GetStatus getStatus = new GetStatus(
        this.status, () -> connectionTracker.snapshot(System.currentTimeMillis()));

    // Let failures propagate: LX wraps initialize() and surfaces them via
    // pushError (user-facing) + marks the plugin as errored. Swallowing here
    // would leave the plugin looking healthy while the server is down.
    this.server = EmbeddedMcpServer.start(
        SERVER_NAME, BuildInfo.version(), config.port(), config.host(),
        Tools.specifications(lx, new EngineExecutor(lx), getStatus), Tools.INSTRUCTIONS,
        connectionTracker);
    long startedAtMs = System.currentTimeMillis();
    this.status.initialize(config.host(), this.server.port(), startedAtMs, EmbeddedMcpServer.ENDPOINT);
    // Only published for the UI plugin to find once fully initialized — if start()
    // above had thrown (e.g. port already in use, which PR-A made fatal), publishing
    // this earlier would let ChromatikMcpUiPlugin's null check pass and build a section over
    // an uninitialized ServerStatus ("http://null:0null", a grey dot that looks healthy).
    currentStatus = this.status;

    writeStatusFile(false, null);

    this.loopTask = deltaMs -> {
      long nowMs = System.currentTimeMillis();
      boolean connected = connectionTracker.isConnected(nowMs);
      long lastActivityMs = connectionTracker.lastActivityMs();
      boolean wasConnected = this.status.connected.isOn();
      this.status.connected.setValue(connected);
      this.status.lastActivityMs.setValue(lastActivityMs);
      if (connected != wasConnected) {
        Log.log(connected ? "MCP client connected" : "MCP client disconnected");
        // A disk-full/permissions blip writing status.json must never take down a live
        // show: LXEngine.run() catches Throwable from loop tasks and marks the engine
        // permanently failed, so an uncaught UncheckedIOException here would kill the
        // whole performance over a cosmetic discovery-file write.
        try {
          writeStatusFile(connected, lastActivityMs);
        } catch (UncheckedIOException e) {
          Log.error(e, "Failed to rewrite " + StatusFile.path() + " on connection-state change");
        }
      }
    };
    lx.engine.addLoopTask(this.loopTask);

    Log.log("MCP server listening on " + this.status.url());
  }

  /**
   * Enables the {@code chromatikmcp.ui.ChromatikMcpUiPlugin} companion so users only ever need to toggle
   * "Chromatik-MCP", not both plugins. Registration order within the jar puts this class before
   * {@code chromatikmcp/ui/}, so this entry's {@code initialize(lx)} runs before the UI entry's during
   * {@code LXRegistry.initializePlugins()} — flipping it here takes effect in the same session,
   * not just after a restart. If the UI entry is somehow visited first (ordering isn't
   * contractual) or missing (pure headless, class skipped by the classloader), the persisted
   * enabled state in the registry JSON self-heals the UI plugin's enabled-ness on the next
   * launch either way, so we don't need to special-case that here.
   *
   * <p>Package-private (not private) so the test in this package can exercise it directly
   * against a real {@code LXRegistry.Plugin} list.
   */
  static void autoEnableUiPlugin(List<LXRegistry.Plugin> plugins) {
    for (LXRegistry.Plugin plugin : plugins) {
      if (plugin.clazz.getName().equals(UI_PLUGIN_CLASS_NAME)) {
        if (!plugin.isEnabled()) {
          plugin.setEnabled(true);
          Log.log("auto-enabled Chromatik-MCP UI companion plugin");
        }
        return;
      }
    }
    // Headless: the UI class was never scanned into the registry (studio classes absent).
    // Expected and unremarkable — headless logs must stay clean.
  }

  private void writeStatusFile(boolean connected, Long lastActivityMs) {
    File project = this.lx.getProject();
    StatusFile.write(
        this.server.port(),
        this.status.host(),
        this.status.url(),
        project == null ? null : project.getAbsolutePath(),
        LX.VERSION,
        connected,
        lastActivityMs);
  }

  @Override
  public void dispose() {
    // Stop observing connection state BEFORE the shutdown status write — otherwise a
    // connection-state transition landing between the write below and loop-task removal
    // could rewrite the file again right after, leaving a stale connected:true.
    if (this.lx != null && this.loopTask != null) {
      this.lx.engine.removeLoopTask(this.loopTask);
    }
    this.loopTask = null;
    currentStatus = null;
    if (this.server != null) {
      // Best-effort: leave the discovery file honest (connected=false) rather than
      // stale from the last-observed state. Must never throw out of dispose() over a
      // cosmetic write failure. Preserve the last-known activity timestamp rather than
      // erasing it to null — shutdown doesn't mean a client was never active.
      try {
        long lastActivityMs = (long) this.status.lastActivityMs.getValue();
        writeStatusFile(false, lastActivityMs == 0 ? null : lastActivityMs);
      } catch (UncheckedIOException e) {
        Log.error(e, "Failed to rewrite " + StatusFile.path() + " on dispose");
      }
    }
    if (this.server != null) {
      this.server.stop();
      this.server = null;
    }
  }

  /** {@code null} until {@link #initialize} has run; the UI plugin checks for this. */
  public static ServerStatus status() {
    return currentStatus;
  }
}
