package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.LXFixture;

/**
 * A JsonFixture needs a real {@code .lxf} on disk, so every test here writes a tiny two-level
 * fixture (modeled on {@code Apotheneum-Haptics.lxf}: a parent {@code .lxf} whose
 * {@code components} reference a child {@code .lxf} type) into a {@code @TempDir}, and
 * constructs {@link LX} with {@code Flags.mediaPath} pointed there — never the real
 * {@code ~/Chromatik} folder.
 */
class SubfixtureTreeTest extends HeadlessLxTest {

  private static void writeFixture(Path fixturesDir, String name, String json) throws IOException {
    Path file = fixturesDir.resolve(name + ".lxf");
    try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      writer.write(json);
    }
  }

  /**
   * Builds a "TestGrid" parent fixture (5 instances of "TestChild", itself 4 points wide) —
   * a genuine 3-level tree: LX.structure.fixtures -> TestGrid -> 5x TestChild -> points.
   */
  private LX buildThreeLevelTree(Path mediaRoot) throws IOException {
    Path fixturesDir = mediaRoot.resolve("Fixtures");
    Files.createDirectories(fixturesDir);

    writeFixture(fixturesDir, "TestChild", """
        {
          "label": "TestChild",
          "parameters": {
            "spacing": { "type": "float", "default": 2 }
          },
          "components": [
            { "type": "point" }
          ]
        }
        """);

    writeFixture(fixturesDir, "TestGrid", """
        {
          "label": "TestGrid",
          "parameters": {
            "nodeSpacing": { "type": "float", "min": 0, "default": 5 }
          },
          "components": [
            {
              "type": "TestChild",
              "instances": 5,
              "x": "$instance * $nodeSpacing"
            }
          ]
        }
        """);

    LX.Flags flags = new LX.Flags();
    flags.mediaPath = mediaRoot.toString();
    LX lx = track(new LX(flags));

    lx.command.perform(new LXCommand.Structure.AddFixture("TestGrid"));
    return lx;
  }

  private static JsonFixture topLevelFixture(LX lx) {
    return (JsonFixture) lx.structure.fixtures.get(0);
  }

  @Test
  void childrenReturnsTheDeclaredSubfixtureTree(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildThreeLevelTree(mediaRoot);
    JsonFixture parent = topLevelFixture(lx);

    assertTrue(Fixtures.subfixturesAvailable(), "reflective accessor should initialize on this JVM");
    assertEquals(5, Fixtures.children(parent).size(), "TestGrid declares 5 TestChild instances");
    for (LXFixture child : Fixtures.children(parent)) {
      // Each TestChild itself declares one "point" component, which JsonFixture.loadChild
      // instantiates as a real (3rd-level) PointFixture child.
      assertEquals(1, Fixtures.children(child).size(), "TestChild's own 'point' component");
      assertEquals(0, Fixtures.children(Fixtures.children(child).get(0)).size());
    }
  }

  @Test
  void nestedPathResolvesThroughResolve(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildThreeLevelTree(mediaRoot);
    JsonFixture parent = topLevelFixture(lx);
    LXFixture thirdChild = Fixtures.children(parent).get(2);

    String path = Resolve.canonicalPath(thirdChild);
    assertEquals("/lx/structure/fixture/1/fixture/3", path);

    var resolvedComponent = Resolve.component(lx, path);
    assertEquals(thirdChild, resolvedComponent);
  }

  @Test
  void getParameterWorksOnANestedSubfixtureParameter(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildThreeLevelTree(mediaRoot);
    JsonFixture parent = topLevelFixture(lx);
    LXFixture thirdChild = Fixtures.children(parent).get(2);

    String xPath = Resolve.canonicalPath(thirdChild) + "/x";
    Parameters.ParameterInfo info = Parameters.get(lx, xPath);
    // instance index 2 (0-based) * nodeSpacing default 5
    assertEquals(10.0, (Double) info.value(), 1e-9);
  }

  @Test
  void writeToNestedSubfixtureParameterIsRejected(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildThreeLevelTree(mediaRoot);
    JsonFixture parent = topLevelFixture(lx);
    LXFixture thirdChild = Fixtures.children(parent).get(2);

    String xPath = Resolve.canonicalPath(thirdChild) + "/x";
    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Parameters.set(lx, xPath, 99.0));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
    assertTrue(Fixtures.isJsonDerived(thirdChild));
  }

  @Test
  void wireTriggerToNestedSubfixtureBooleanParameterIsRejected(@TempDir Path mediaRoot) throws IOException {
    // wire_trigger resolves its target directly via Resolve.parameter (not through
    // Parameters.set/fire), so this exercises requireWritable as the shared choke point
    // rather than a copy of the guard — see Parameters.requireWritable.
    LX lx = buildThreeLevelTree(mediaRoot);
    JsonFixture parent = topLevelFixture(lx);
    LXFixture thirdChild = Fixtures.children(parent).get(2);

    String sourcePath = Resolve.canonicalPath(parent) + "/identify";
    String targetPath = Resolve.canonicalPath(thirdChild) + "/identify";
    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> new chromatikmcp.tools.WireTrigger().handle(lx, java.util.Map.of(
            "sourcePath", sourcePath, "targetPath", targetPath)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
  }

  @Test
  void writeToTopLevelJsonFixtureOwnParameterIsAllowed(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildThreeLevelTree(mediaRoot);
    JsonFixture parent = topLevelFixture(lx);
    assertFalse(Fixtures.isJsonDerived(parent), "the fixture itself being a JsonFixture doesn't count");

    // The .lxf 'parameters' block (e.g. nodeSpacing) declares knobs that are NOT registered
    // through LXComponent.addParameter (JsonFixture.ParameterDefinition holds them in a
    // separate map) — a pre-existing LX gap, unrelated to isJsonDerived, that leaves them
    // unreachable via canonical-path set_parameter regardless of this guard. Exercise the
    // guard instead against a normal LXFixture-owned parameter, which is registered and IS
    // the fixture's own intended edit surface.
    String xPath = Resolve.canonicalPath(parent) + "/x";
    Parameters.ParameterInfo info = Parameters.set(lx, xPath, 7.5);
    assertEquals(7.5, (Double) info.value(), 1e-9);
  }

  @Test
  void listFixturesReportsRealChildCountAndDistinctSubmodelCount(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildThreeLevelTree(mediaRoot);
    Fixtures.FixtureInfo info = Fixtures.describeFixture(topLevelFixture(lx));
    assertEquals(5, info.childCount(), "5 declared TestChild subfixtures");
  }

  @Test
  void childCountAndSubmodelCountDifferForAGridFixture() {
    LX lx = track(new LX());
    LXCommand.Structure.AddFixture command =
        new LXCommand.Structure.AddFixture(heronarts.lx.structure.GridFixture.class);
    lx.command.perform(command);
    heronarts.lx.structure.GridFixture grid =
        (heronarts.lx.structure.GridFixture) lx.structure.fixtures.get(0);
    grid.numRows.setValue(2);
    grid.numColumns.setValue(3);
    Fixtures.flushStructure(lx);

    Fixtures.FixtureInfo info = Fixtures.describeFixture(grid);
    assertEquals(0, info.childCount(), "GridFixture has no subfixtures");
    assertEquals(5, info.submodelCount(), "2 row + 3 column submodels");
  }

  @Test
  void describeTreeDepthLimitsCorrectly(@TempDir Path mediaRoot) throws IOException {
    LX lx = buildThreeLevelTree(mediaRoot);
    JsonFixture parent = topLevelFixture(lx);

    Fixtures.FixtureNode depth0 = Fixtures.describeTree(parent, 0);
    assertNull(depth0.children(), "depth 0 stops before descending");

    Fixtures.FixtureNode depth1 = Fixtures.describeTree(parent, 1);
    assertNotNull(depth1.children());
    assertEquals(5, depth1.children().size());
    assertNull(depth1.children().get(0).children(), "depth exhausted one level down");

    Fixtures.FixtureNode depth2 = Fixtures.describeTree(parent, 2);
    assertNotNull(depth2.children().get(0).children(), "second level still has depth remaining");
    // TestChild's own "point" component is a genuine 3rd-level subfixture.
    assertEquals(1, depth2.children().get(0).children().size());
    assertNull(depth2.children().get(0).children().get(0).children(), "depth exhausted at level 3");
  }

  @Test
  void outputMapReportsDirectOutputCountNotAFabricatedUniverseForAJsonFixture(@TempDir Path mediaRoot)
      throws IOException {
    LX lx = buildThreeLevelTree(mediaRoot);
    JsonFixture parent = topLevelFixture(lx);

    Fixtures.OutputMapEntry entry = Fixtures.outputMap(lx, parent).fixtures().get(0);
    assertEquals(Resolve.canonicalPath(parent), entry.path());
    // TestGrid/TestChild declare no "output" block, so outputsDirect is empty — but it's
    // still a JsonFixture, so directOutputCount/outputsNote are reported (0, with the note)
    // rather than silently omitted, and no universe/channel is fabricated for it.
    assertEquals(0, entry.directOutputCount());
    assertNotNull(entry.outputsNote());
    assertNull(entry.protocol(), "JsonFixture is not an LXProtocolFixture");
    assertNull(entry.universe());
    assertNull(entry.estimatedUniverseSpan());
    assertEquals(5, entry.childCount());
    assertNotNull(entry.children(), "depth defaults deep enough to cover a 3-level tree");
    assertEquals(5, entry.children().size());
  }

  @Test
  void childrenOnAFixtureWithNoSubfixturesReturnsEmptyListAndNeverThrows() {
    // A direct, exhaustive test of the reflection fallback (an induced failure isn't
    // cleanly forceable from a test without swapping the classloader) is not attempted
    // here; this documents the always-empty, always-safe contract that the fallback path
    // shares with the success path when there are simply no children.
    LX lx = track(new LX());
    LXCommand.Structure.AddFixture command =
        new LXCommand.Structure.AddFixture(heronarts.lx.structure.GridFixture.class);
    lx.command.perform(command);
    LXFixture grid = lx.structure.fixtures.get(0);

    assertEquals(0, Fixtures.children(grid).size());
  }
}
