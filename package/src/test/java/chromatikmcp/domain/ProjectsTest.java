package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

  /** {@code save_project}/{@code save_model} resolve relative paths under a real media
   * folder ({@code LX.Media.PROJECTS}/{@code MODELS}), so these tests need an {@code LX}
   * whose media root is the per-test {@code @TempDir} rather than {@code newHeadlessLx()}'s
   * default ({@code "."} — writing there would pollute the working directory). */
  private LX newLxWithMediaPath(Path tempDir) {
    LX.Flags flags = new LX.Flags();
    flags.mediaPath = tempDir.toString();
    return track(new LX(flags));
  }

  @Test
  void resolveProjectSaveFileWithNoPathAndNoOpenProjectThrows() {
    LX lx = newHeadlessLx();
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Projects.resolveProjectSaveFile(lx, null, false));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure, "maps to invalid_argument at the seam");
  }

  @Test
  void resolveModelSaveFileWithNoPathAndNoLinkedModelThrows(@TempDir Path tempDir) {
    LX lx = newLxWithMediaPath(tempDir);
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Projects.resolveModelSaveFile(lx, null, false));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure, "maps to invalid_argument at the seam");
    assertTrue(e.getMessage().contains("No linked model file"));
  }

  @Test
  void resolveModelSaveFileOnAStaticStructureWithNoPathReportsTheStaticReasonNotTheMissingLinkReason() {
    // Issue #154's Fix 3: on a static structure, current (lx.structure.getModelFile()) is
    // also always null, so the "no linked model file" check used to fire first and tell
    // the caller to retry with a path — advice that cannot work, since saveModel rejects a
    // static structure regardless of path. requireDynamicStructure must run before path
    // resolution so this reports the actionable reason.
    LX lx = newHeadlessLx();
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Projects.resolveModelSaveFile(lx, null, false));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure, "maps to invalid_argument at the seam");
    assertTrue(e.getMessage().contains("static-model mode"),
        "must report the static-mode reason, not \"no linked model file\": " + e.getMessage());
  }

  @Test
  void saveProjectWritesANewFileAndMovesTheOpenProject(@TempDir Path tempDir) throws IOException {
    LX lx = newLxWithMediaPath(tempDir);

    File target = Projects.resolveProjectSaveFile(lx, "session.lxp", false);
    assertEquals(tempDir.resolve("Projects").resolve("session.lxp").toFile().getAbsolutePath(),
        target.getAbsolutePath(), "a relative path resolves under LX.Media.PROJECTS");
    assertNull(lx.getProject(), "resolving must not itself move the open project");

    File saved = Projects.saveProject(lx, target);
    assertSame(target, saved);
    assertTrue(target.isFile());
    assertTrue(target.length() > 0);

    JsonObject project = JsonParser.parseString(Files.readString(target.toPath())).getAsJsonObject();
    assertTrue(project.has("version"), "expected top-level key: version");
    assertTrue(project.has("model"), "expected top-level key: model");
    assertTrue(project.has("engine"), "expected top-level key: engine");
    assertTrue(project.has("externals"), "expected top-level key: externals");

    assertNotNull(lx.getProject());
    assertEquals(target.getAbsolutePath(), lx.getProject().getAbsolutePath());
  }

  @Test
  void saveProjectInPlaceSkipsTheOverwriteGuardEvenThoughTheFileAlreadyExists(@TempDir Path tempDir) {
    LX lx = newLxWithMediaPath(tempDir);
    File target = Projects.saveProject(lx, Projects.resolveProjectSaveFile(lx, "session.lxp", false));
    assertTrue(target.exists());

    // path omitted now resolves back to the already-open, already-existing file — no
    // overwrite=true required, because it isn't a *different* file being clobbered.
    File resolved = Projects.resolveProjectSaveFile(lx, null, false);
    assertEquals(target.getAbsolutePath(), resolved.getAbsolutePath());
    Projects.saveProject(lx, resolved);
  }

  /**
   * The bug: on a save-in-place, {@code file.isFile() && file.length() > 0 &&
   * lx.getProject() equals file} are ALL already true before the call, so a swallowed
   * {@code IOException} (LX.java:1058-1059 only logs) would previously be reported as
   * success with the file byte-unchanged. The fix re-reads the file's LX-stamped timestamp
   * after the call, which a failed write can't have advanced.
   */
  @Test
  void saveProjectInPlaceThrowsRatherThanReportingSuccessWhenTheWriteIsSwallowed(
      @TempDir Path tempDir) throws IOException {
    LX lx = newLxWithMediaPath(tempDir);
    File target = Projects.saveProject(lx, Projects.resolveProjectSaveFile(lx, "session.lxp", false));
    byte[] before = Files.readAllBytes(target.toPath());

    boolean setReadOnly = target.setReadOnly();
    assumeTrue(setReadOnly && !target.canWrite(),
        "cannot make the target file unwritable in this environment (e.g. running as root)");
    try {
      File resolved = Projects.resolveProjectSaveFile(lx, null, false);
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> Projects.saveProject(lx, resolved),
          "a swallowed IOException on a save-in-place must not report success");
      assertTrue(e.getMessage().contains(target.getAbsolutePath()),
          "message names the file that failed to save");
      assertArrayEquals(before, Files.readAllBytes(target.toPath()),
          "the on-disk file must be untouched by the failed write");
    } finally {
      target.setWritable(true);
    }
  }

  @Test
  void saveProjectOverwriteGuardRejectsAnExistingFileAndLeavesItUnchanged(@TempDir Path tempDir)
      throws IOException {
    LX lx = newLxWithMediaPath(tempDir);
    File target = Projects.resolveProjectSaveFile(lx, "existing.lxp", false);
    Files.writeString(target.toPath(), "not a project file");
    byte[] before = Files.readAllBytes(target.toPath());

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Projects.resolveProjectSaveFile(lx, "existing.lxp", false));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains(target.getAbsolutePath()),
        "message names the resolved absolute path");
    assertArrayEquals(before, Files.readAllBytes(target.toPath()),
        "overwrite=false must leave the existing file's bytes untouched");

    File resolved = Projects.resolveProjectSaveFile(lx, "existing.lxp", true);
    Projects.saveProject(lx, resolved);
    JsonObject saved = JsonParser.parseString(Files.readString(target.toPath())).getAsJsonObject();
    assertTrue(saved.has("version"), "overwrite=true replaces the file with a real project");
  }

  @Test
  void saveProjectWithAMissingParentDirectoryThrowsAndWritesNothing(@TempDir Path tempDir) {
    LX lx = newLxWithMediaPath(tempDir);
    File target = Projects.resolveProjectSaveFile(lx, "NoSuchDir/session.lxp", false);
    assertFalse(target.getParentFile().isDirectory(),
        "getMediaFile's create only makes the media root, not nested dirs inside path");

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Projects.saveProject(lx, target));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertFalse(target.exists(), "the swallowed IOException must not leave a partial file");
    assertNull(lx.getProject(), "a failed save must not move the open project");
  }

  @Test
  void saveProjectDoesNotTouchTheUndoStack(@TempDir Path tempDir) {
    LX lx = newLxWithMediaPath(tempDir);
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, null, null);
    var beforeUndo = lx.command.getUndoCommand();
    assertNotNull(beforeUndo, "adding a fixture pushes a real undo entry");

    Projects.saveProject(lx, Projects.resolveProjectSaveFile(lx, "session.lxp", false));

    assertSame(beforeUndo, lx.command.getUndoCommand(),
        "save_project is not LXCommand-backed and must not touch the undo stack");
  }

  @Test
  void saveModelWritesANewFileMovesTheLinkedModelAndProjectInfoReportsIt(@TempDir Path tempDir) {
    LX lx = newLxWithMediaPath(tempDir);

    File target = Projects.resolveModelSaveFile(lx, "rig.lxm", false);
    assertEquals(tempDir.resolve("Models").resolve("rig.lxm").toFile().getAbsolutePath(),
        target.getAbsolutePath(), "a relative path resolves under LX.Media.MODELS");

    File saved = Projects.saveModel(lx, target);
    assertSame(target, saved);
    assertTrue(target.isFile());
    assertTrue(target.length() > 0);

    assertNotNull(lx.structure.getModelFile());
    assertEquals(target.getAbsolutePath(), lx.structure.getModelFile().getAbsolutePath());

    // End-to-end demo of issue #154's fix: a client re-queries get_project_info (Projects.info)
    // and sees the link moved.
    Projects.ModelInfo model = Projects.info(lx).model();
    assertEquals(target.getAbsolutePath(), model.file(),
        "get_project_info's model.file must report the moved link");
    assertTrue(model.external());
  }

  /**
   * Guards {@code save_model} the same way {@code Fixtures.requireDynamicStructure} guards
   * fixture mutations: a static model has no fixture-based model to export, and {@code
   * LXStructure.load} discards any link this would write on the next project load anyway
   * (issue #154 follow-up). {@code newHeadlessLx()}'s immutable {@code GridModel} sets
   * {@code isStatic} true at construction (LXStructure.java:211-214), so it exercises the
   * guard without needing a dedicated static-model fixture.
   */
  @Test
  void saveModelOnAStaticStructureThrowsAndWritesNothing() {
    LX lx = newHeadlessLx();
    assertTrue(lx.structure.isStatic.isOn(), "precondition: this fixture's model is static");

    File target = new File("unreachable.lxm");
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Projects.saveModel(lx, target));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertFalse(target.exists(), "the guard must fire before any write is attempted");
  }

  @Test
  void saveModelInPlaceSkipsTheOverwriteGuardEvenThoughTheFileAlreadyExists(@TempDir Path tempDir) {
    LX lx = newLxWithMediaPath(tempDir);
    File target = Projects.saveModel(lx, Projects.resolveModelSaveFile(lx, "rig.lxm", false));
    assertTrue(target.exists());

    File resolved = Projects.resolveModelSaveFile(lx, null, false);
    assertEquals(target.getAbsolutePath(), resolved.getAbsolutePath());
    Projects.saveModel(lx, resolved);
  }

  @Test
  void saveModelOverwriteGuardRejectsAnExistingFileAndLeavesItUnchanged(@TempDir Path tempDir)
      throws IOException {
    LX lx = newLxWithMediaPath(tempDir);
    File target = Projects.resolveModelSaveFile(lx, "existing.lxm", false);
    Files.writeString(target.toPath(), "not a model file");
    byte[] before = Files.readAllBytes(target.toPath());

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Projects.resolveModelSaveFile(lx, "existing.lxm", false));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains(target.getAbsolutePath()),
        "message names the resolved absolute path");
    assertArrayEquals(before, Files.readAllBytes(target.toPath()),
        "overwrite=false must leave the existing file's bytes untouched");

    File resolved = Projects.resolveModelSaveFile(lx, "existing.lxm", true);
    Projects.saveModel(lx, resolved);
    JsonObject saved = JsonParser.parseString(Files.readString(target.toPath())).getAsJsonObject();
    assertTrue(saved.has("fixtures"), "overwrite=true replaces the file with a real exported model");
  }

  @Test
  void saveModelWithAMissingParentDirectoryThrowsAndWritesNothing(@TempDir Path tempDir) {
    LX lx = newLxWithMediaPath(tempDir);
    File target = Projects.resolveModelSaveFile(lx, "NoSuchDir/rig.lxm", false);
    assertFalse(target.getParentFile().isDirectory(),
        "getMediaFile's create only makes the media root, not nested dirs inside path");

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Projects.saveModel(lx, target));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertFalse(target.exists(), "the swallowed IOException must not leave a partial file");
    assertNull(lx.structure.getModelFile(), "a failed save must not move the linked model");
  }

  @Test
  void saveModelDoesNotTouchTheUndoStack(@TempDir Path tempDir) {
    LX lx = newLxWithMediaPath(tempDir);
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, null, null);
    var beforeUndo = lx.command.getUndoCommand();
    assertNotNull(beforeUndo, "adding a fixture pushes a real undo entry");

    Projects.saveModel(lx, Projects.resolveModelSaveFile(lx, "rig.lxm", false));

    assertSame(beforeUndo, lx.command.getUndoCommand(),
        "save_model is not LXCommand-backed and must not touch the undo stack");
  }

  /**
   * The round trip that motivated issue #154: a project linked to a {@code .lxm} shared by
   * other projects on the rig, with {@code syncModelFile} on (so a plain save_project would
   * silently rewrite the shared file — the incident get_project_info's model block now
   * surfaces). The fix is "Save Model As" to a NEW path before saving the project, not
   * disabling syncModelFile: after {@code save_model} re-points the link, save_project's
   * write-through hits the new file, and the shared file is never touched again.
   */
  @Test
  void saveModelToANewPathThenSaveProjectLeavesTheOriginallyLinkedModelUntouched(@TempDir Path tempDir)
      throws IOException {
    LX lx = newLxWithMediaPath(tempDir);

    File sharedModel = Projects.saveModel(lx, Projects.resolveModelSaveFile(lx, "shared.lxm", false));
    lx.structure.syncModelFile.setValue(true);
    byte[] sharedBefore = Files.readAllBytes(sharedModel.toPath());

    // Structure work "done over MCP" against the live session.
    Fixtures.addFixtureByClass(lx, GridFixture.class, null, null, null);

    File newModel = Projects.saveModel(lx, Projects.resolveModelSaveFile(lx, "new-fixture-rig.lxm", false));
    assertEquals(newModel.getAbsolutePath(), lx.structure.getModelFile().getAbsolutePath());

    File projectFile = Projects.saveProject(lx, Projects.resolveProjectSaveFile(lx, "session.lxp", false));

    byte[] sharedAfter = Files.readAllBytes(sharedModel.toPath());
    assertArrayEquals(sharedBefore, sharedAfter,
        "save_project's write-through (syncModelFile=true) must hit the newly linked model, "
            + "not the original shared one");

    JsonObject project = JsonParser.parseString(Files.readString(projectFile.toPath())).getAsJsonObject();
    String modelFileProperty = project.getAsJsonObject("model").get("file").getAsString();
    assertEquals(lx.getMediaPath(LX.Media.MODELS, newModel), modelFileProperty,
        "the saved .lxp must reference the new model file, not the original shared one");
  }
}
