package lxmcp.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link BuildInfo} must never throw — a missing or malformed classpath resource falls
 * back to {@code "unknown"} rather than taking the plugin down. {@link BuildInfo#load}
 * takes an explicit resource path so these fixtures don't need to sit at the real {@code
 * /lxmcp-build.properties} location that Maven filters at build time.
 */
class BuildInfoTest {

  @Test
  void presentAndWellFormedResourceIsParsed() {
    BuildInfo.Info info = BuildInfo.load("/lxmcp-build-test-fixture.properties");
    assertEquals("9.9.9", info.version());
    assertEquals("2026-01-01T00:00:00Z", info.buildTime());
  }

  @Test
  void absentResourceFallsBackToUnknownWithoutThrowing() {
    BuildInfo.Info info = assertDoesNotThrow(() -> BuildInfo.load("/does-not-exist.properties"));
    assertEquals("unknown", info.version());
    assertEquals("unknown", info.buildTime());
  }

  @Test
  void malformedResourceFallsBackToUnknownWithoutThrowing() {
    BuildInfo.Info info = assertDoesNotThrow(() -> BuildInfo.load("/lxmcp-build-test-malformed.properties"));
    assertEquals("unknown", info.version());
    assertEquals("unknown", info.buildTime());
  }

  @Test
  void realFilteredResourceHasNonBlankValues() {
    // The real /lxmcp-build.properties comes from target/classes in the test JVM — Maven
    // has already filtered it, so this proves the resource-filtering wiring works end to end.
    BuildInfo.Info info = BuildInfo.load("/lxmcp-build.properties");
    assertNotEquals("unknown", info.version());
    assertNotEquals("unknown", info.buildTime());
    assertFalse(info.version().isBlank());
    assertFalse(info.buildTime().isBlank());
  }
}
