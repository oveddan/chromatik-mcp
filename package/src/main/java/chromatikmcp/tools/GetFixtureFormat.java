package chromatikmcp.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import heronarts.lx.LX;

/**
 * {@code get_fixture_format}: returns a bundled markdown reference for the {@code .lxf}
 * fixture-file JSON schema — geometry, component types, the {@code $expr} parameter
 * system, and outputs/segments — so an MCP client can author or understand a fixture
 * file without access to LX source.
 */
public final class GetFixtureFormat implements LxTool {

  private static final String RESOURCE_PATH = "docs/lxf-format.md";

  @Override
  public String name() {
    return "get_fixture_format";
  }

  @Override
  public String description() {
    return "Return the .lxf fixture-file JSON schema reference: top-level keys, component "
        + "types (point/points/strip/arc/class/file-reference), the parameter + $expr "
        + "expression system ($instance/$instances and instances-expansion), outputs and "
        + "segments per protocol, and tag rules — with worked examples. Use this to author "
        + "or understand a fixture file; pairs with reload_fixtures to pick up on-disk edits "
        + "to an existing fixture, and get_fixture/describe_model to inspect a fixture's "
        + "already-loaded structure.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    try (InputStream in = GetFixtureFormat.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
      if (in == null) {
        return Result.error(Result.INTERNAL, "Bundled doc resource missing: " + RESOURCE_PATH);
      }
      String markdown = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return Result.ok(Map.of("markdown", markdown));
    } catch (IOException e) {
      return Result.error(Result.INTERNAL, "Failed to read bundled doc resource: " + e.getMessage());
    }
  }
}
