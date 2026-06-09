package lxmcp.mcp;

import java.io.IOException;
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
   * @return the path written.
   */
  public static Path write(int port, String projectPath, String lxVersion) throws IOException {
    Path file = path();
    Files.createDirectories(file.getParent());
    long pid = ProcessHandle.current().pid();
    String json = "{\n"
        + "  \"pid\": " + pid + ",\n"
        + "  \"port\": " + port + ",\n"
        + "  \"projectPath\": " + jsonStringOrNull(projectPath) + ",\n"
        + "  \"lxVersion\": " + jsonStringOrNull(lxVersion) + "\n"
        + "}\n";
    Files.writeString(file, json);
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
