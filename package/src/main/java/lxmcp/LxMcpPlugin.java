package lxmcp;

import java.io.File;
import java.io.UncheckedIOException;

import heronarts.lx.LX;
import heronarts.lx.LXLoopTask;
import heronarts.lx.LXPlugin;

import lxmcp.engine.EngineExecutor;
import lxmcp.mcp.ConfigFile;
import lxmcp.mcp.ConnectionTracker;
import lxmcp.mcp.EmbeddedMcpServer;
import lxmcp.mcp.StatusFile;
import lxmcp.tools.GetStatus;
import lxmcp.tools.Tools;

@LXPlugin.Name("LX-MCP")
public class LxMcpPlugin implements LXPlugin {

  private static final String PREFIX = "[LX-MCP] ";
  private static final String SERVER_NAME = "LX-MCP";
  private static final String SERVER_VERSION = "0.0.1";

  private LX lx;
  private ServerStatus status;
  private EmbeddedMcpServer server;
  private LXLoopTask loopTask;

  @Override
  public void initialize(LX lx) {
    LX.log(PREFIX + "plugin loaded");
    ConfigFile.Config config = ConfigFile.load(ConfigFile.path());
    if (!config.isLoopback()) {
      LX.error(PREFIX + "SECURITY WARNING: binding MCP server to non-loopback host '" + config.host()
          + "'. This server is UNAUTHENTICATED and can mutate a live show — anyone who can reach "
          + "this address has full control. Remove \"host\" from " + ConfigFile.path()
          + " to restore loopback-only.");
    }

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
        SERVER_NAME, SERVER_VERSION, config.port(), config.host(),
        Tools.specifications(lx, new EngineExecutor(lx), getStatus), Tools.INSTRUCTIONS,
        connectionTracker);
    long startedAtMs = System.currentTimeMillis();
    this.status.initialize(config.host(), this.server.port(), startedAtMs, EmbeddedMcpServer.ENDPOINT);

    writeStatusFile(false, null);

    this.loopTask = deltaMs -> {
      ConnectionSnapshot snapshot = connectionTracker.snapshot(System.currentTimeMillis());
      boolean wasConnected = this.status.connected.isOn();
      this.status.connected.setValue(snapshot.connected());
      this.status.lastActivityMs.setValue(snapshot.lastActivityMs());
      if (snapshot.connected() != wasConnected) {
        LX.log(PREFIX + (snapshot.connected() ? "MCP client connected" : "MCP client disconnected"));
        // A disk-full/permissions blip writing status.json must never take down a live
        // show: LXEngine.run() catches Throwable from loop tasks and marks the engine
        // permanently failed, so an uncaught UncheckedIOException here would kill the
        // whole performance over a cosmetic discovery-file write.
        try {
          writeStatusFile(snapshot.connected(), snapshot.lastActivityMs());
        } catch (UncheckedIOException e) {
          LX.error(e, PREFIX + "Failed to rewrite " + StatusFile.path() + " on connection-state change");
        }
      }
    };
    lx.engine.addLoopTask(this.loopTask);

    LX.log(PREFIX + "MCP server listening on " + this.status.url());
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
    if (this.server != null) {
      // Best-effort: leave the discovery file honest (connected=false) rather than
      // stale from the last-observed state. Must never throw out of dispose() over a
      // cosmetic write failure. Preserve the last-known activity timestamp rather than
      // erasing it to null — shutdown doesn't mean a client was never active.
      try {
        long lastActivityMs = (long) this.status.lastActivityMs.getValue();
        writeStatusFile(false, lastActivityMs == 0 ? null : lastActivityMs);
      } catch (UncheckedIOException e) {
        LX.error(e, PREFIX + "Failed to rewrite " + StatusFile.path() + " on dispose");
      }
    }
    if (this.server != null) {
      this.server.stop();
      this.server = null;
    }
  }
}
