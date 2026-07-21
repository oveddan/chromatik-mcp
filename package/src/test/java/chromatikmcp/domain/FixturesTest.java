package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.structure.GridFixture;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.LXFixture;

/**
 * {@code addFixture} throws against a static model (docs/build-plan.md), so every test
 * here constructs its own dynamic-structure {@link LX} via {@code track(new LX())} — the
 * no-model constructor leaves {@code lx.structure.isStatic} false — rather than the
 * per-test {@code GridModel} the shared {@link HeadlessLxTest#newHeadlessLx()} default
 * provides.
 */
class FixturesTest extends HeadlessLxTest {

  private static GridFixture addGridFixture(LX lx) {
    LXCommand.Structure.AddFixture command = new LXCommand.Structure.AddFixture(GridFixture.class);
    int before = lx.structure.fixtures.size();
    lx.command.perform(command);
    if (lx.structure.fixtures.size() != before + 1) {
      throw new IllegalStateException("AddFixture did not add a fixture");
    }
    return (GridFixture) lx.structure.fixtures.get(before);
  }

  @Test
  void describesAGridFixtureWithIndexRangeTransformAndSubmodels() {
    LX lx = track(new LX());
    GridFixture fixture = addGridFixture(lx);
    fixture.numRows.setValue(2);
    fixture.numColumns.setValue(3);
    fixture.x.setValue(1.5);

    Fixtures.FixturesSnapshot snapshot = Fixtures.describe(lx);
    assertEquals(1, snapshot.fixtures().size());
    Fixtures.FixtureInfo info = snapshot.fixtures().get(0);

    assertEquals(Resolve.canonicalPath(fixture), info.path());
    assertEquals("/lx/structure/fixture/1", info.path());
    assertEquals(0, info.index());
    assertEquals("GridFixture", info.type());
    assertEquals(6, info.size(), "2 rows x 3 columns");
    assertNotNull(info.firstIndex());
    assertEquals(0, info.firstIndex());
    assertEquals(5, info.lastIndex());
    assertEquals(1.5, info.transform().x(), 1e-9);
    assertFalse(info.enabled(), "LXFixture.enabled (output enabled) defaults to false");
    assertFalse(info.deactivate());
    // GridFixture has no subfixtures (childCount 0) but registers row + column submodels
    // (toSubmodels()) as its model's children (submodelCount).
    assertEquals(0, info.childCount());
    assertEquals(2 + 3, info.submodelCount());
    assertNotNull(info.output(), "GridFixture is an LXProtocolFixture");
    assertEquals("NONE", info.output().protocol(), "no protocol configured by default");
  }

  @Test
  void everyFixtureParameterHasARegisteredNonNullPath() {
    LX lx = track(new LX());
    GridFixture fixture = addGridFixture(lx);

    for (heronarts.lx.parameter.LXParameter parameter : fixture.getParameters()) {
      String path = Resolve.canonicalPath(parameter);
      assertNotNull(path, "unregistered fixture parameter: " + parameter.getLabel());
      assertFalse(path.equals("/null") || path.endsWith("/null"),
          "unregistered fixture parameter path: " + path + " (" + parameter.getLabel() + ")");
    }
  }

  @Test
  void artNetProtocolReportsUniverseAndChannelFromTheirDedicatedParameters() {
    LX lx = track(new LX());
    GridFixture fixture = addGridFixture(lx);
    fixture.protocol.setValue(LXFixture.Protocol.ARTNET);
    fixture.artNetUniverse.setValue(3);
    fixture.dmxChannel.setValue(7);

    Fixtures.FixtureInfo info = Fixtures.describeFixture(fixture);
    assertEquals("ARTNET", info.output().protocol());
    assertEquals(3, info.output().universe());
    assertEquals(7, info.output().channel());
  }

  @Test
  void removingAFixtureIsUndoableAndListFixturesReflectsIt() {
    LX lx = track(new LX());
    addGridFixture(lx);
    assertEquals(1, Fixtures.describe(lx).fixtures().size());

    LXFixture fixture = lx.structure.fixtures.get(0);
    lx.command.perform(new LXCommand.Structure.RemoveFixture(fixture));
    assertEquals(0, Fixtures.describe(lx).fixtures().size());

    lx.command.undo();
    assertEquals(1, Fixtures.describe(lx).fixtures().size(), "undo restores the fixture");
  }

  @Test
  void outOfRangeIndexIsNotAFixtureListingConcern() {
    // list_fixtures never indexes past the live list — this documents that the tool's
    // out-of-range handling lives entirely in Resolve (see ResolveTest), not here.
    LX lx = track(new LX());
    assertTrue(Fixtures.describe(lx).fixtures().isEmpty());
  }

  // These lock in the per-protocol universe/channel/port mapping replicated from
  // LXProtocolFixture.getProtocolPort/Universe/Channel (protected, unreachable from here) —
  // a reviewer manually verified each branch against that upstream logic.

  @Test
  void sacnProtocolReportsUniverseAndChannelFromTheirDedicatedParameters() {
    LX lx = track(new LX());
    GridFixture fixture = addGridFixture(lx);
    fixture.protocol.setValue(LXFixture.Protocol.SACN);
    fixture.artNetUniverse.setValue(4);
    fixture.dmxChannel.setValue(8);

    Fixtures.FixtureInfo info = Fixtures.describeFixture(fixture);
    assertEquals("SACN", info.output().protocol());
    assertEquals(4, info.output().universe());
    assertEquals(8, info.output().channel());
  }

  @Test
  void ddpProtocolReportsDataOffsetAsUniverseAndZeroChannel() {
    LX lx = track(new LX());
    GridFixture fixture = addGridFixture(lx);
    fixture.protocol.setValue(LXFixture.Protocol.DDP);
    fixture.ddpDataOffset.setValue(12);

    Fixtures.FixtureInfo info = Fixtures.describeFixture(fixture);
    assertEquals("DDP", info.output().protocol());
    assertEquals(12, info.output().universe());
    assertEquals(0, info.output().channel(), "DDP packets are sent individually; no dedicated channel");
  }

  @Test
  void kinetProtocolReportsPortAsUniverseAndDmxChannel() {
    LX lx = track(new LX());
    GridFixture fixture = addGridFixture(lx);
    fixture.protocol.setValue(LXFixture.Protocol.KINET);
    fixture.kinetPort.setValue(5);
    fixture.dmxChannel.setValue(9);

    Fixtures.FixtureInfo info = Fixtures.describeFixture(fixture);
    assertEquals("KINET", info.output().protocol());
    assertEquals(5, info.output().universe());
    assertEquals(9, info.output().channel());
  }

  @Test
  void opcProtocolReportsPortChannelAndOffset() {
    LX lx = track(new LX());
    GridFixture fixture = addGridFixture(lx);
    fixture.protocol.setValue(LXFixture.Protocol.OPC);
    fixture.port.setValue(7890);
    fixture.opcChannel.setValue(2);
    fixture.opcOffset.setValue(3);

    Fixtures.FixtureInfo info = Fixtures.describeFixture(fixture);
    assertEquals("OPC", info.output().protocol());
    assertEquals(7890, info.output().port());
    assertEquals(2, info.output().universe());
    assertEquals(3, info.output().channel());
  }

  @Test
  void noneProtocolReportsTheDefaultPortAndZeroUniverseAndChannel() {
    LX lx = track(new LX());
    GridFixture fixture = addGridFixture(lx);
    // NONE is the default, but set it explicitly since other tests mutate protocol too.
    fixture.protocol.setValue(LXFixture.Protocol.NONE);

    Fixtures.FixtureInfo info = Fixtures.describeFixture(fixture);
    assertEquals("NONE", info.output().protocol());
    assertEquals(-1, info.output().port(), "Protocol.NONE is defined NONE(\"None\", -1, -1)");
    assertEquals(0, info.output().universe());
    assertEquals(0, info.output().channel());
  }

  @Test
  void jsonFixtureReportsFixtureTypeAsTypeAndDeclaredParametersButNoOutput(@TempDir Path mediaPath)
      throws IOException {
    Path fixturesDir = mediaPath.resolve("Fixtures");
    Files.createDirectories(fixturesDir);
    Files.writeString(fixturesDir.resolve("FixturesTestFixture.lxf"), """
        {
          "label": "FixturesTestFixture",
          "parameters": {
            "nodeSpacing": { "type": "float", "min": 1, "default": 9.375, "label": "Spacing" }
          },
          "components": [
            {
              "type": "strip",
              "numPoints": 4,
              "spacing": "$nodeSpacing"
            }
          ]
        }
        """);

    LX.Flags flags = new LX.Flags();
    flags.mediaPath = mediaPath.toString();
    LX lx = track(new LX(flags));

    LXCommand.Structure.AddFixture command =
        new LXCommand.Structure.AddFixture("FixturesTestFixture");
    lx.command.perform(command);
    JsonFixture fixture = (JsonFixture) lx.structure.fixtures.get(0);

    Fixtures.FixtureInfo info = Fixtures.describeFixture(fixture);
    assertEquals("FixturesTestFixture.lxf", info.type());
    assertNull(info.output(), "JsonFixture is not an LXProtocolFixture");

    java.util.List<Fixtures.JsonParameterInfo> parameters = Fixtures.jsonParameters(fixture);
    assertEquals(1, parameters.size());
    assertEquals("nodeSpacing", parameters.get(0).name());
    assertEquals("FLOAT", parameters.get(0).type());
    assertEquals(9.375, (double) parameters.get(0).value(), 1e-9);
  }
}
