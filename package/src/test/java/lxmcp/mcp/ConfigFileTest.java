package lxmcp.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lxmcp.mcp.ConfigFile.Config;

class ConfigFileTest {

  @TempDir
  Path tempDir;

  private Path write(String contents) throws IOException {
    Path file = tempDir.resolve("config.json");
    Files.writeString(file, contents);
    return file;
  }

  @Test
  void missingFileYieldsDefaults() {
    Path file = tempDir.resolve("does-not-exist.json");
    assertEquals(ConfigFile.DEFAULTS, ConfigFile.load(file));
  }

  @Test
  void fullConfigOverridesBoth() throws IOException {
    Path file = write("{\"port\":7000,\"host\":\"0.0.0.0\"}");
    Config config = ConfigFile.load(file);
    assertEquals(7000, config.port());
    assertEquals("0.0.0.0", config.host());
  }

  @Test
  void partialConfigFallsBackPerKey() throws IOException {
    Path file = write("{\"port\":7000}");
    Config config = ConfigFile.load(file);
    assertEquals(7000, config.port());
    assertEquals(ConfigFile.DEFAULTS.host(), config.host());
  }

  @Test
  void malformedJsonYieldsDefaults() throws IOException {
    Path file = write("{ this is not json");
    assertEquals(ConfigFile.DEFAULTS, ConfigFile.load(file));
  }

  @Test
  void outOfRangePortYieldsDefaults() throws IOException {
    Path file = write("{\"port\":70000}");
    assertEquals(ConfigFile.DEFAULTS, ConfigFile.load(file));
  }

  @Test
  void negativePortYieldsDefaults() throws IOException {
    Path file = write("{\"port\":-1}");
    assertEquals(ConfigFile.DEFAULTS, ConfigFile.load(file));
  }

  @Test
  void fractionalPortYieldsDefaults() throws IOException {
    Path file = write("{\"port\":7000.5}");
    assertEquals(ConfigFile.DEFAULTS, ConfigFile.load(file));
  }

  @Test
  void blankHostYieldsDefaults() throws IOException {
    Path file = write("{\"host\":\"   \"}");
    assertEquals(ConfigFile.DEFAULTS, ConfigFile.load(file));
  }

  @Test
  void isLoopbackTruthTable() {
    assertTrue(new Config(0, "127.0.0.1").isLoopback());
    assertTrue(new Config(0, "127.5.5.5").isLoopback());
    assertTrue(new Config(0, "localhost").isLoopback());
    assertTrue(new Config(0, "::1").isLoopback());
    assertFalse(new Config(0, "0.0.0.0").isLoopback());
    assertFalse(new Config(0, "192.168.1.10").isLoopback());
  }
}
