package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.LXFixture;

/**
 * {@code set_fixture_params}'s JSON-parameter path, the write guard against a JsonFixture
 * descendant, and {@code reload_fixtures} — all need a real {@code .lxf} on disk, so this
 * mirrors {@code SubfixtureTreeTest}'s {@code @TempDir} + {@code Flags.mediaPath} harness.
 */
class FixtureEditingTest extends HeadlessLxTest {

  private static void writeFixture(Path fixturesDir, String name, String json) throws IOException {
    Path file = fixturesDir.resolve(name + ".lxf");
    try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      writer.write(json);
    }
  }

  private static final String CONTROLLER_FIXTURE = """
      {
        "label": "TestController",
        "parameters": {
          "host": { "type": "string", "default": "10.0.0.1" },
          "nodeSpacing": { "type": "float", "min": 0, "default": 5 }
        },
        "components": [
          {
            "type": "strip",
            "numPoints": 4,
            "spacing": "$nodeSpacing"
          }
        ]
      }
      """;

  private LX buildFixture(Path mediaRoot, String lxfBody) throws IOException {
    Path fixturesDir = mediaRoot.resolve("Fixtures");
    Files.createDirectories(fixturesDir);
    writeFixture(fixturesDir, "TestController", lxfBody);

    LX.Flags flags = new LX.Flags();
    flags.mediaPath = mediaRoot.toString();
    LX lx = track(new LX(flags));
    lx.command.perform(new LXCommand.Structure.AddFixture("TestController"));
    return lx;
  }

  private static JsonFixture topLevelFixture(LX lx) {
    return (JsonFixture) lx.structure.fixtures.get(0);
  }

  @Test
  void setParamsRoundTripsAJsonStringParameterWithUndo(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildFixture(mediaRoot, CONTROLLER_FIXTURE);
    JsonFixture fixture = topLevelFixture(lx);

    Fixtures.SetParamsResult result = Fixtures.setParams(lx, fixture, Map.of("host", "10.0.1.42"));
    assertEquals(1, result.undoEntries());
    assertEquals("10.0.1.42", result.values().get("host"));
    assertEquals("10.0.1.42", jsonParamValue(fixture, "host"));

    lx.command.undo();
    assertEquals("10.0.0.1", jsonParamValue(fixture, "host"), "undo restores the JSON parameter");
  }

  private static final String COLLIDING_FIXTURE = """
      {
        "label": "TestCollidingController",
        "parameters": {
          "scale": { "type": "float", "min": 0, "default": 2.0 },
          "nodeSpacing": { "type": "float", "min": 0, "default": 5 }
        },
        "components": [
          {
            "type": "strip",
            "numPoints": 4,
            "spacing": "$nodeSpacing"
          }
        ]
      }
      """;

  @Test
  void setParamsResolvesRegisteredParameterBeforeJsonParameterOfTheSameNameAndReportsTheShadow(
      @TempDir Path mediaRoot) throws IOException {
    // LX only forbids a .lxf parameter named "instances"/"instance" — "scale" collides with
    // the registered LXFixture geometry parameter of the same name, so it's a real shadow.
    Path fixturesDir = mediaRoot.resolve("Fixtures");
    Files.createDirectories(fixturesDir);
    writeFixture(fixturesDir, "TestCollidingController", COLLIDING_FIXTURE);

    LX.Flags flags = new LX.Flags();
    flags.mediaPath = mediaRoot.toString();
    LX lx = track(new LX(flags));
    lx.command.perform(new LXCommand.Structure.AddFixture("TestCollidingController"));
    JsonFixture fixture = topLevelFixture(lx);

    Fixtures.SetParamsResult result = Fixtures.setParams(lx, fixture, Map.of("scale", 3.0));
    assertEquals(3.0, (Double) result.values().get("scale"), 1e-9,
        "registered geometry 'scale' wins over the JSON-declared 'scale'");
    assertEquals(3.0, fixture.scale.getValue(), 1e-9);
    assertEquals(2.0, (double) jsonParamValue(fixture, "scale"), 1e-9,
        "the JSON-declared 'scale' is left untouched");
    assertEquals(List.of("scale"), result.shadowedJsonParams());
  }

  @Test
  void setParamsWritingAnExprReferencedJsonParameterRebuildsGeometry(@TempDir Path mediaRoot)
      throws IOException {
    // CONTROLLER_FIXTURE's "spacing": "$nodeSpacing" is never itself written by another
    // test — writing "nodeSpacing" fires ParameterDefinition.onParameterChanged -> reload(false),
    // which disposes and rebuilds the strip's children.
    LX lx = buildFixture(mediaRoot, CONTROLLER_FIXTURE);
    JsonFixture fixture = topLevelFixture(lx);
    LXFixture strip = Fixtures.children(fixture).get(0);
    double before = strip.points.get(strip.points.size() - 1).x;

    Fixtures.SetParamsResult result = Fixtures.setParams(lx, fixture, Map.of("nodeSpacing", 20.0));
    assertEquals(20.0, (Double) result.values().get("nodeSpacing"), 1e-9);
    assertEquals(20.0, (double) jsonParamValue(fixture, "nodeSpacing"), 1e-9);

    LXFixture rebuiltStrip = Fixtures.children(fixture).get(0);
    double after = rebuiltStrip.points.get(rebuiltStrip.points.size() - 1).x;
    assertTrue(after > before, "wider spacing pushes the last point further out");
  }

  @Test
  void setParamsRejectsWriteToASubfixtureOfAJsonFixture(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildFixture(mediaRoot, CONTROLLER_FIXTURE);
    JsonFixture parent = topLevelFixture(lx);
    LXFixture strip = Fixtures.children(parent).get(0);

    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Fixtures.setParams(lx, strip, Map.of("x", 5.0)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
  }

  @Test
  void setParamsAllowsWriteToTheTopLevelJsonFixtureOwnParameter(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildFixture(mediaRoot, CONTROLLER_FIXTURE);
    JsonFixture fixture = topLevelFixture(lx);

    Fixtures.SetParamsResult result = Fixtures.setParams(lx, fixture, Map.of("x", 3.0));
    assertEquals(3.0, (Double) result.values().get("x"), 1e-9);
  }

  @Test
  void reloadFixturesPicksUpAnEditMadeToTheLxfOnDisk(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildFixture(mediaRoot, CONTROLLER_FIXTURE);
    JsonFixture fixture = topLevelFixture(lx);
    assertEquals(4, fixture.totalSize(), "original .lxf declares numPoints 4");

    writeFixture(mediaRoot.resolve("Fixtures"), "TestController", """
        {
          "label": "TestController",
          "parameters": {
            "host": { "type": "string", "default": "10.0.0.1" },
            "nodeSpacing": { "type": "float", "min": 0, "default": 5 }
          },
          "components": [
            {
              "type": "strip",
              "numPoints": 7,
              "spacing": "$nodeSpacing"
            }
          ]
        }
        """);

    Fixtures.ReloadResult result = Fixtures.reload(lx);
    assertTrue(result.jsonTypes().contains("TestController"));
    assertTrue(result.errors().isEmpty());
    assertEquals(7, fixture.totalSize(), "reload_fixtures picked up the edited numPoints");
    assertEquals(1, result.fixtures().size());
    assertEquals(7, result.fixtures().get(0).size());
  }

  @Test
  void reloadFixturesSurvivesAJsonParameterStillPresentInTheNewFile(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildFixture(mediaRoot, CONTROLLER_FIXTURE);
    JsonFixture fixture = topLevelFixture(lx);
    Fixtures.setParams(lx, fixture, Map.of("host", "10.0.1.99"));

    Fixtures.reload(lx);

    assertEquals("10.0.1.99", jsonParamValue(topLevelFixture(lx), "host"),
        "a JSON parameter that still exists in the reloaded file keeps its edited value");
  }

  private static Object jsonParamValue(JsonFixture fixture, String name) {
    for (Fixtures.JsonParameterInfo info : Fixtures.jsonParameters(fixture)) {
      if (info.name().equals(name)) {
        return info.value();
      }
    }
    throw new IllegalArgumentException("No JSON parameter named " + name);
  }
}
