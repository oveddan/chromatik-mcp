package lxmcp.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import heronarts.lx.LX;

/**
 * The version and build timestamp of the running plugin jar, read once from the
 * classpath resource {@code lxmcp-build.properties} (filtered by Maven from {@code
 * project.version} / {@code maven.build.timestamp} at build time).
 *
 * <p>Exists so {@code get_status} and {@link StatusFile} can identify the CODE currently
 * running — a client that reinstalled a new jar but hasn't restarted Chromatik has no
 * other way to detect it's still talking to the stale process.
 *
 * <p>Mirrors {@link ConfigFile}'s defensive style: any read/parse failure logs via {@link
 * LX#error} and falls back to {@code "unknown"} rather than propagating — a missing or
 * malformed resource must never take the plugin down.
 */
public final class BuildInfo {

  private static final String RESOURCE = "/lxmcp-build.properties";
  private static final String UNKNOWN = "unknown";

  /** The plugin jar's version and build timestamp; {@link #UNKNOWN} on any read failure. */
  public record Info(String version, String buildTime) {}

  private static final Info INFO = load(RESOURCE);

  private BuildInfo() {}

  public static String version() {
    return INFO.version();
  }

  public static String buildTime() {
    return INFO.buildTime();
  }

  /** Package-visible so a unit test can point at present/absent/malformed fixtures. */
  static Info load(String resourcePath) {
    Properties props = new Properties();
    try (InputStream in = BuildInfo.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        LX.error("[LX-MCP] Missing classpath resource " + resourcePath + " — build identity unknown");
        return new Info(UNKNOWN, UNKNOWN);
      }
      props.load(in);
    } catch (IOException | IllegalArgumentException e) {
      LX.error(e, "[LX-MCP] Failed to read " + resourcePath + " — build identity unknown");
      return new Info(UNKNOWN, UNKNOWN);
    }
    String version = props.getProperty("version");
    String buildTime = props.getProperty("buildTime");
    if (version == null || version.isBlank()) {
      version = UNKNOWN;
    }
    if (buildTime == null || buildTime.isBlank()) {
      buildTime = UNKNOWN;
    }
    return new Info(version, buildTime);
  }
}
