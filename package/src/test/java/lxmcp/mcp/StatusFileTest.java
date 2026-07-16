package lxmcp.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * {@link StatusFile#path()} is a fixed {@code ~/.lx-mcp/status.json} — these tests
 * redirect {@code user.home} to a per-test temp dir (restored after each test) so writes
 * land somewhere disposable, then parse the result back with Gson (the library
 * {@link ConfigFile} reads with) to assert the JSON shape.
 */
class StatusFileTest {

  @TempDir
  Path tempDir;

  private String originalHome;

  @BeforeEach
  void redirectHome() {
    this.originalHome = System.getProperty("user.home");
    System.setProperty("user.home", this.tempDir.toString());
  }

  @AfterEach
  void restoreHome() {
    System.setProperty("user.home", this.originalHome);
  }

  @Test
  void writesAllFieldsAndAtomicallyReplaces() throws IOException {
    StatusFile.write(7000, "127.0.0.1", "http://127.0.0.1:7000/mcp", "/tmp/project.lxp", "1.2.1",
        true, 123_456_789L);

    JsonObject json = readJson();
    assertEquals(7000, json.get("port").getAsInt());
    assertEquals("127.0.0.1", json.get("host").getAsString());
    assertEquals("http://127.0.0.1:7000/mcp", json.get("url").getAsString());
    assertEquals("/tmp/project.lxp", json.get("projectPath").getAsString());
    assertEquals("1.2.1", json.get("lxVersion").getAsString());
    assertEquals(BuildInfo.version(), json.get("serverVersion").getAsString());
    assertEquals(BuildInfo.buildTime(), json.get("buildTime").getAsString());
    assertTrue(json.get("connected").getAsBoolean());
    assertEquals(java.time.Instant.ofEpochMilli(123_456_789L).toString(),
        json.get("lastActivityAt").getAsString());
    assertTrue(json.has("pid"));

    // Second write replaces the first (atomic move over the same path).
    StatusFile.write(8000, "127.0.0.1", "http://127.0.0.1:8000/mcp", null, "1.2.1", false, null);
    JsonObject second = readJson();
    assertEquals(8000, second.get("port").getAsInt());
    assertTrue(second.get("projectPath").isJsonNull());
    assertTrue(second.get("lastActivityAt").isJsonNull(), "never-active lastActivity is null");

    Path tmp = StatusFile.path().resolveSibling(StatusFile.path().getFileName() + ".tmp");
    assertTrue(Files.notExists(tmp), "the .tmp sibling is moved away, not left behind");
  }

  @Test
  void neverActiveLastActivityIsNullEvenWithZero() throws IOException {
    StatusFile.write(7000, "127.0.0.1", "http://127.0.0.1:7000/mcp", null, "1.2.1", false, 0L);
    JsonObject json = readJson();
    assertTrue(json.get("lastActivityAt").isJsonNull());
  }

  private JsonObject readJson() throws IOException {
    return JsonParser.parseString(Files.readString(StatusFile.path())).getAsJsonObject();
  }
}
