package lxmcp.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Writes {@code ~/.lx-mcp/status.json} so MCP clients can discover the running
 * server's port. This is the only filesystem touchpoint of the plugin (alongside the
 * read-only {@link ConfigFile}).
 *
 * <p>Shape: {@code {pid, port, host, url, projectPath, lxVersion, connected,
 * lastActivityAt}}.
 *
 * <p>Writes are strictly ordered — the initial write from {@code initialize()} always
 * precedes any rewrite from the plugin's loop task on connection-state changes — so
 * there is no concurrent-writer hazard and no locking is needed, even though {@code
 * initialize()} itself runs on LX's startup thread rather than the engine thread.
 */
public final class StatusFile {

  private StatusFile() {}

  /** The {@code ~/.lx-mcp/status.json} path (does not require the file to exist). */
  public static Path path() {
    return Paths.get(System.getProperty("user.home"), ".lx-mcp", "status.json");
  }

  /**
   * Write the discovery handshake. {@code projectPath} may be null (no project open);
   * {@code lastActivityMs} may be null or {@code 0} (never active) — either renders as
   * {@code null} in the {@code lastActivityAt} field.
   *
   * <p>Writes atomically via a {@code .tmp} sibling + {@code ATOMIC_MOVE}: the file is
   * rewritten mid-session as connection state changes, and clients may poll it.
   *
   * <p>Throws {@link UncheckedIOException} on I/O failure so it propagates out of the
   * plugin's {@code initialize()} into LX's error handling rather than forcing a catch.
   *
   * @return the path written.
   */
  public static Path write(
      int port,
      String host,
      String url,
      String projectPath,
      String lxVersion,
      boolean connected,
      Long lastActivityMs) {
    Path file = path();
    long pid = ProcessHandle.current().pid();
    String lastActivityAt = (lastActivityMs == null || lastActivityMs == 0)
        ? null
        : Instant.ofEpochMilli(lastActivityMs).toString();
    String json = "{\n"
        + "  \"pid\": " + pid + ",\n"
        + "  \"port\": " + port + ",\n"
        + "  \"host\": " + jsonStringOrNull(host) + ",\n"
        + "  \"url\": " + jsonStringOrNull(url) + ",\n"
        + "  \"projectPath\": " + jsonStringOrNull(projectPath) + ",\n"
        + "  \"lxVersion\": " + jsonStringOrNull(lxVersion) + ",\n"
        + "  \"connected\": " + connected + ",\n"
        + "  \"lastActivityAt\": " + jsonStringOrNull(lastActivityAt) + "\n"
        + "}\n";
    try {
      Files.createDirectories(file.getParent());
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.writeString(tmp, json);
      Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException iox) {
      throw new UncheckedIOException("Failed to write " + file, iox);
    }
    return file;
  }

  private static String jsonStringOrNull(String value) {
    if (value == null) {
      return "null";
    }
    String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
    return "\"" + escaped + "\"";
  }
}
