package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.LXPath;
import heronarts.lx.structure.GridFixture;

class ProjectsTest extends HeadlessLxTest {

  @Test
  void reportsVersionPathAndChannelCount() {
    LX lx = newHeadlessLx();
    Projects.ProjectInfo info = Projects.info(lx);
    assertEquals(LX.VERSION, info.lxVersion());
    assertNull(info.projectPath(), "headless LX has no project file");
    assertEquals(lx.engine.mixer.channels.size(), info.channelCount());
  }

  @Test
  void reportsOscEngineState() {
    LX lx = newHeadlessLx();
    Projects.OscInfo osc = Projects.info(lx).osc();
    assertEquals(lx.engine.osc.receivePort.getValuei(), osc.receivePort());
    assertEquals(lx.engine.osc.receiveActive.isOn(), osc.receiveActive());
    assertEquals(lx.engine.osc.transmitPort.getValuei(), osc.transmitPort());
    assertEquals(lx.engine.osc.transmitActive.isOn(), osc.transmitActive());
  }

  @Test
  void channelCountTracksMixer() {
    LX lx = newHeadlessLx();
    int before = Projects.info(lx).channelCount();
    lx.engine.mixer.addChannel();
    assertEquals(before + 1, Projects.info(lx).channelCount());
  }

  @Test
  void reportsOutputEngineState() {
    LX lx = newHeadlessLx();
    Projects.OutputInfo output = Projects.info(lx).output();
    assertEquals(lx.engine.output.enabled.isOn(), output.enabled());
    assertEquals(lx.engine.output.brightness.getValue(), output.brightness());
    assertSame(lx.engine.output.enabled, LXPath.get(lx, output.enabledPath()),
        "output.enabled path must round-trip through LXPath");
    assertSame(lx.engine.output.brightness, LXPath.get(lx, output.brightnessPath()),
        "output.brightness path must round-trip through LXPath");
  }

  @Test
  void outputSnapshotTracksEngineToggle() {
    LX lx = newHeadlessLx();
    lx.engine.output.enabled.setValue(false);
    assertEquals(false, Projects.info(lx).output().enabled());
    lx.engine.output.enabled.setValue(true);
    assertEquals(true, Projects.info(lx).output().enabled());
  }

  @Test
  void reportsOutputGammaState() {
    LX lx = newHeadlessLx();
    Projects.OutputInfo output = Projects.info(lx).output();
    assertEquals(lx.engine.output.gamma.getValue(), output.gamma());
    assertSame(lx.engine.output.gamma, LXPath.get(lx, output.gammaPath()));
    assertEquals(lx.engine.output.gammaMode.getEnum().name(), output.gammaMode());
    assertSame(lx.engine.output.gammaMode, LXPath.get(lx, output.gammaModePath()));
  }

  @Test
  void reportsEngineGlobalState() {
    LX lx = newHeadlessLx();
    Projects.EngineInfo engine = Projects.info(lx).engine();
    assertEquals(lx.engine.speed.getValue(), engine.speed());
    assertSame(lx.engine.speed, LXPath.get(lx, engine.speedPath()));
    assertEquals(lx.engine.framesPerSecond.getValue(), engine.framesPerSecond());
    assertSame(lx.engine.framesPerSecond, LXPath.get(lx, engine.framesPerSecondPath()));
  }

  @Test
  void reportsModelInfoWithNoLinkedModelFile() {
    LX lx = newHeadlessLx();
    Projects.ModelInfo model = Projects.info(lx).model();
    assertNull(model.file(), "headless LX with no import/export has no linked model file");
    assertFalse(model.external());
    assertEquals(lx.structure.modelName.getString(), model.name(),
        "name mirrors LXStructure's own default, not a hardcoded literal");
  }

  @Test
  void reportsModelInfoForAProjectBoundToAnExternalModelFile(@TempDir Path tempDir) {
    LX.Flags flags = new LX.Flags();
    flags.mediaPath = tempDir.toString();
    LX lx = track(new LX(flags));
    File modelFile = tempDir.resolve("Test.lxm").toFile();
    lx.structure.exportModel(modelFile);

    Projects.ModelInfo model = Projects.info(lx).model();
    assertEquals(modelFile.getAbsolutePath(), model.file());
    assertEquals(modelFile.getName(), model.name());
    assertTrue(model.external());
    assertFalse(model.isStatic());
    assertFalse(model.hasUnsavedChanges());
    assertEquals(lx.structure.syncModelFile.isOn(), model.syncModelFile());
    assertEquals(Resolve.canonicalPath(lx.structure.syncModelFile), model.syncModelFilePath());
    // lx.structure isn't parented under lx.engine, so its parameters are unreachable
    // through the raw LXPath.get(lx, ...) used above for engine-descendant paths;
    // Resolve.parameter is the domain-level resolver that special-cases the structure
    // tree (see Resolve.canonicalPath's javadoc) and is what set_parameter uses.
    assertSame(lx.structure.syncModelFile, Resolve.parameter(lx, model.syncModelFilePath()),
        "syncModelFilePath must round-trip through Resolve");
  }

  @Test
  void reportsSyncModelFileWhenEnabled(@TempDir Path tempDir) {
    LX.Flags flags = new LX.Flags();
    flags.mediaPath = tempDir.toString();
    LX lx = track(new LX(flags));
    File modelFile = tempDir.resolve("Test.lxm").toFile();
    lx.structure.exportModel(modelFile);
    lx.structure.syncModelFile.setValue(true);

    Projects.ModelInfo model = Projects.info(lx).model();
    assertTrue(model.external());
    assertEquals(modelFile.getAbsolutePath(), model.file());
    assertTrue(model.syncModelFile());
  }

  @Test
  void syncModelFilePathCanBeSetFalseThroughSetParameter(@TempDir Path tempDir) {
    LX.Flags flags = new LX.Flags();
    flags.mediaPath = tempDir.toString();
    LX lx = track(new LX(flags));
    File modelFile = tempDir.resolve("Test.lxm").toFile();
    lx.structure.exportModel(modelFile);
    lx.structure.syncModelFile.setValue(true);

    Projects.ModelInfo model = Projects.info(lx).model();
    Parameters.set(lx, model.syncModelFilePath(), false);

    assertFalse(lx.structure.syncModelFile.isOn());

    lx.command.undo();

    assertTrue(lx.structure.syncModelFile.isOn());
  }

  @Test
  void reportsUnsavedChangesAfterStructureMutation(@TempDir Path tempDir) {
    LX.Flags flags = new LX.Flags();
    flags.mediaPath = tempDir.toString();
    LX lx = track(new LX(flags));
    File modelFile = tempDir.resolve("Test.lxm").toFile();
    lx.structure.exportModel(modelFile);

    Fixtures.addFixtureByClass(lx, GridFixture.class, null, null, null);

    Projects.ModelInfo model = Projects.info(lx).model();
    assertTrue(model.hasUnsavedChanges());
    assertTrue(model.name().endsWith("*"), "dirty external model name carries a trailing *");
  }
}
