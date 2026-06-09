package lxmcp.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes {@code ~/.lx-mcp/status.json} so MCP clients can discover the running
 * server's port. This is the only filesystem touchpoint of the plugin.
 *
 * <p>Shape: {@code {pid, port, projectPath, lxVersion}}.
 */
public final class StatusFile {

  private StatusFile() {}

  /** The {@code ~/.lx-mcp/status.json} path (does not require the file to exist). */
  public static Path path() {
    return Paths.get(System.getProperty("user.home"), ".lx-mcp", "status.json");
  }

  /**
   * Write the discovery handshake. {@code projectPath} may be null (no project open).
   *
   * <p>Throws {@link UncheckedIOException} on I/O failure so it propagates out of the
   * plugin's {@code initialize()} into LX's error handling rather than forcing a catch.
   *
   * @return the path written.
   */
  public static Path write(int port, String projectPath, String lxVersion) {
    Path file = path();
    long pid = ProcessHandle.current().pid();
    String json = "{\n"
        + "  \"pid\": " + pid + ",\n"
        + "  \"port\": " + port + ",\n"
        + "  \"projectPath\": " + jsonStringOrNull(projectPath) + ",\n"
        + "  \"lxVersion\": " + jsonStringOrNull(lxVersion) + "\n"
        + "}\n";
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, json);
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
