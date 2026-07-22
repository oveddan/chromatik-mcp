package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;
import heronarts.lx.structure.GridFixture;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.LXFixture;

/**
 * {@code addFixture}/{@code removeFixture}/{@code moveFixture}/{@code duplicateFixture}
 * throw against a static-model structure (see {@code requireDynamicStructure}), so every
 * mutation test here constructs its own dynamic-structure {@link LX} via
 * {@code track(new LX())} — mirroring {@link FixturesTest}. The static-guard tests use
 * {@code track(new LX(new GridModel(...)))} instead, to exercise the rejection path.
 */
class FixtureLifecycleTest extends HeadlessLxTest {

  private static void writeFixture(Path fixturesDir, String name, String json) throws IOException {
    Path file = fixturesDir.resolve(name + ".lxf");
    try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      writer.write(json);
    }
  }

  private LX dynamicLxWithFixturesDir(Path mediaRoot) throws IOException {
    Path fixturesDir = mediaRoot.resolve("Fixtures");
    Files.createDirectories(fixturesDir);
    writeFixture(fixturesDir, "TestPoint", """
        {
          "label": "TestPoint",
          "parameters": {
            "spacing": { "type": "float", "default": 2 }
          },
          "components": [
            { "type": "point" }
          ]
        }
        """);
    LX.Flags flags = new LX.Flags();
    flags.mediaPath = mediaRoot.toString();
    return track(new LX(flags));
  }

  // --- add by class ---

  @Test
  void addFixtureByClassAddsAndDescribesIt() {
    LX lx = track(new LX());
    Fixtures.FixtureInfo info = Fixtures.addFixtureByClass(
        lx, GridFixture.class, null, null, null);

    assertEquals(1, lx.structure.fixtures.size());
    assertEquals("GridFixture", info.type());
    assertEquals(Resolve.canonicalPath(lx.structure.fixtures.get(0)), info.path());
  }

  @Test
  void addFixtureByClassIsUndoable() {
    LX lx = track(new LX());
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, null, null);
    assertEquals(1, lx.structure.fixtures.size());

    lx.command.undo();

    assertTrue(lx.structure.fixtures.isEmpty(), "undo removes the added fixture entirely");
  }

  @Test
  void addFixtureByClassWithLabelAndParamsAppliesInSameUndoStep() {
    LX lx = track(new LX());
    Fixtures.FixtureInfo info = Fixtures.addFixtureByClass(lx, GridFixture.class, null,
        "MyGrid", Map.of("numRows", 4.0, "numColumns", 5.0));

    GridFixture fixture = (GridFixture) lx.structure.fixtures.get(0);
    assertEquals("MyGrid", fixture.getLabel());
    assertEquals(4, fixture.numRows.getValuei());
    assertEquals(5, fixture.numColumns.getValuei());
    assertEquals("MyGrid", info.label());

    lx.command.undo();
    assertTrue(lx.structure.fixtures.isEmpty(),
        "a single undo removes the fully-configured fixture");
  }

  @Test
  void addFixtureByClassAtAnExplicitIndexInsertsThere() {
    LX lx = track(new LX());
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, "First", null);
    Fixtures.addFixtureByClass(lx, GridFixture.class, 0, "Second", null);

    assertEquals(2, lx.structure.fixtures.size());
    assertEquals("Second", lx.structure.fixtures.get(0).getLabel());
    assertEquals("First", lx.structure.fixtures.get(1).getLabel());
  }

  @Test
  void addFixtureByClassWithUnknownParamNameRollsBackTheWholeAdd() {
    LX lx = track(new LX());
    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Fixtures.addFixtureByClass(lx, GridFixture.class, null, null,
            Map.of("noSuchParam", 1.0)));

    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
    assertTrue(lx.structure.fixtures.isEmpty(),
        "an invalid params entry leaves nothing added — no half-configured fixture");
  }

  @Test
  void addFixtureByClassWithATypeMismatchedParamRollsBackTheWholeAdd() {
    LX lx = track(new LX());
    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Fixtures.addFixtureByClass(lx, GridFixture.class, null, null,
            Map.of("numRows", "not-a-number")));

    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
    assertTrue(lx.structure.fixtures.isEmpty());
  }

  @Test
  void addFixtureByClassRejectedOnAStaticStructure() {
    LX lx = track(new LX(new GridModel(4, 4)));
    assertTrue(lx.structure.isStatic.isOn());

    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Fixtures.addFixtureByClass(lx, GridFixture.class, null, null, null));

    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
    assertTrue(lx.structure.fixtures.isEmpty());
  }

  @Test
  void resolveFixtureClassRejectsUnknownName() {
    LX lx = track(new LX());
    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Fixtures.resolveFixtureClass(lx, "NoSuchFixtureClass"));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
  }

  // --- add by type ---

  @Test
  void addFixtureByTypeAddsAJsonFixtureAndIsUndoable(@TempDir Path mediaRoot) throws IOException {
    LX lx = dynamicLxWithFixturesDir(mediaRoot);

    Fixtures.FixtureInfo info = Fixtures.addFixtureByType(lx, "TestPoint", null, null);
    assertEquals(1, lx.structure.fixtures.size());
    assertTrue(lx.structure.fixtures.get(0) instanceof JsonFixture);
    assertEquals(Resolve.canonicalPath(lx.structure.fixtures.get(0)), info.path());

    lx.command.undo();
    assertTrue(lx.structure.fixtures.isEmpty(), "undo removes the added JsonFixture entirely");
  }

  @Test
  void addFixtureByTypeRejectsUnknownType(@TempDir Path mediaRoot) throws IOException {
    LX lx = dynamicLxWithFixturesDir(mediaRoot);
    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Fixtures.addFixtureByType(lx, "NoSuchType", null, null));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
    assertTrue(lx.structure.fixtures.isEmpty());
  }

  @Test
  void addFixtureByTypeRejectedOnAStaticStructure() {
    LX lx = track(new LX(new GridModel(4, 4)));
    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Fixtures.addFixtureByType(lx, "TestPoint", null, null));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
  }

  // --- remove ---

  @Test
  void removeFixtureRemovesAndIsUndoable() {
    LX lx = track(new LX());
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, null, null);
    LXFixture fixture = lx.structure.fixtures.get(0);

    Fixtures.removeFixture(lx, fixture);
    assertTrue(lx.structure.fixtures.isEmpty());

    lx.command.undo();
    assertEquals(1, lx.structure.fixtures.size(), "undo restores the removed fixture");
  }

  @Test
  void removeFixtureRejectedOnAStaticStructure() {
    LX lx = track(new LX(new GridModel(4, 4)));
    // Static-model LX has no fixtures to remove; exercise the guard directly against a
    // fixture reference obtained from a separate dynamic instance would cross LX
    // instances, so assert the exception is thrown before any structure access matters.
    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Fixtures.removeFixture(lx, null));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
  }

  // --- move ---

  @Test
  void moveFixtureRepositionsAndIsUndoable() {
    LX lx = track(new LX());
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, "First", null);
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, "Second", null);
    LXFixture first = lx.structure.fixtures.get(0);
    assertEquals(0, first.getIndex());

    Fixtures.FixtureInfo info = Fixtures.moveFixture(lx, first, 1);
    assertEquals(1, first.getIndex());
    assertEquals(1, info.index());
    assertEquals("Second", lx.structure.fixtures.get(0).getLabel());

    lx.command.undo();
    assertEquals(0, first.getIndex(), "undo restores the original index");
  }

  @Test
  void moveFixtureClampsAnOutOfRangeIndex() {
    LX lx = track(new LX());
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, "First", null);
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, "Second", null);
    LXFixture first = lx.structure.fixtures.get(0);

    Fixtures.FixtureInfo info = Fixtures.moveFixture(lx, first, 99);
    assertEquals(1, info.index(), "clamped into [0, count - 1]");
  }

  @Test
  void moveFixtureRejectedOnAStaticStructure() {
    LX lx = track(new LX(new GridModel(4, 4)));
    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Fixtures.moveFixture(lx, null, 0));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
  }

  // --- duplicate ---

  @Test
  void duplicateFixtureClonesParameterValuesAtTheDefaultIndex() {
    LX lx = track(new LX());
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, "First", null);
    GridFixture source = (GridFixture) lx.structure.fixtures.get(0);
    source.numRows.setValue(3);
    source.numColumns.setValue(6);
    source.x.setValue(1.25);
    source.enabled.setValue(true);

    Fixtures.FixtureInfo info = Fixtures.duplicateFixture(lx, source, null);

    assertEquals(2, lx.structure.fixtures.size());
    assertEquals(1, info.index(), "defaults to right after the source");
    GridFixture duplicate = (GridFixture) lx.structure.fixtures.get(1);
    assertEquals(3, duplicate.numRows.getValuei());
    assertEquals(6, duplicate.numColumns.getValuei());
    assertEquals(1.25, duplicate.x.getValue(), 1e-9);
    assertFalse(duplicate.enabled.isOn(), "output-enabled is stripped on the clone");
    assertTrue(duplicate.getId() != source.getId(), "clone gets a fresh component id");
  }

  @Test
  void duplicateFixtureAtAnExplicitIndexInsertsThere() {
    LX lx = track(new LX());
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, "First", null);
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, "Second", null);
    LXFixture source = lx.structure.fixtures.get(0);

    Fixtures.FixtureInfo info = Fixtures.duplicateFixture(lx, source, 0);

    assertEquals(3, lx.structure.fixtures.size());
    assertEquals(0, info.index());
  }

  @Test
  void duplicateFixtureIsUndoable() {
    LX lx = track(new LX());
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, "First", null);
    LXFixture source = lx.structure.fixtures.get(0);

    Fixtures.duplicateFixture(lx, source, null);
    assertEquals(2, lx.structure.fixtures.size());

    lx.command.undo();
    assertEquals(1, lx.structure.fixtures.size(), "undo removes the clone");
  }

  @Test
  void duplicateFixtureClonesAJsonFixture(@TempDir Path mediaRoot) throws IOException {
    LX lx = dynamicLxWithFixturesDir(mediaRoot);
    Fixtures.addFixtureByType(lx, "TestPoint", null, null);
    JsonFixture source = (JsonFixture) lx.structure.fixtures.get(0);

    Fixtures.FixtureInfo info = Fixtures.duplicateFixture(lx, source, null);

    assertEquals(2, lx.structure.fixtures.size());
    JsonFixture duplicate = (JsonFixture) lx.structure.fixtures.get(1);
    assertEquals(Fixtures.describeFixture(source).type(), info.type(),
        "clone is the same .lxf type as the source");
    assertEquals(source.getFixturePath(), duplicate.getFixturePath());
    assertEquals(source.totalSize(), duplicate.totalSize(), "same declared point count");
    assertTrue(duplicate.getId() != source.getId(), "clone gets a fresh component id");

    lx.command.undo();
    assertEquals(1, lx.structure.fixtures.size(), "undo removes the clone");
  }

  @Test
  void duplicateFixtureRejectedOnAStaticStructure() {
    LX lx = track(new LX(new GridModel(4, 4)));
    Resolve.ResolveException ex = assertThrows(Resolve.ResolveException.class,
        () -> Fixtures.duplicateFixture(lx, null, null));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
  }
}
