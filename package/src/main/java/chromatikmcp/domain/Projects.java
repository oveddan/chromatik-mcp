package chromatikmcp.domain;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import heronarts.lx.LX;

/** Read-only snapshot of project-level state. */
public final class Projects {

  public record OscInfo(int receivePort, boolean receiveActive,
      int transmitPort, boolean transmitActive) {}

  public record OutputInfo(String enabledPath, boolean enabled, String brightnessPath, double brightness,
      String gammaPath, double gamma, String gammaModePath, String gammaMode) {}

  public record EngineInfo(
      String speedPath, double speed, String framesPerSecondPath, double framesPerSecond) {}

  public record ModelInfo(
      String name, String file, boolean external, boolean isStatic, boolean hasUnsavedChanges,
      boolean syncModelFile, String syncModelFilePath) {}

  public record ProjectInfo(
      String lxVersion, String projectPath, int channelCount, OscInfo osc, OutputInfo output,
      EngineInfo engine, ModelInfo model) {}

  private Projects() {}

  /** Call on the engine thread; the returned record is safe to read anywhere. */
  public static ProjectInfo info(LX lx) {
    File project = lx.getProject();
    File modelFile = lx.structure.getModelFile();
    return new ProjectInfo(
        LX.VERSION,
        (project == null) ? null : project.getAbsolutePath(),
        lx.engine.mixer.channels.size(),
        new OscInfo(
            lx.engine.osc.receivePort.getValuei(),
            lx.engine.osc.receiveActive.isOn(),
            lx.engine.osc.transmitPort.getValuei(),
            lx.engine.osc.transmitActive.isOn()),
        new OutputInfo(
            Resolve.canonicalPathOrNull(lx.engine.output.enabled),
            lx.engine.output.enabled.isOn(),
            Resolve.canonicalPathOrNull(lx.engine.output.brightness),
            lx.engine.output.brightness.getValue(),
            Resolve.canonicalPathOrNull(lx.engine.output.gamma),
            lx.engine.output.gamma.getValue(),
            Resolve.canonicalPathOrNull(lx.engine.output.gammaMode),
            lx.engine.output.gammaMode.getEnum().name()),
        new EngineInfo(
            Resolve.canonicalPathOrNull(lx.engine.speed),
            lx.engine.speed.getValue(),
            Resolve.canonicalPathOrNull(lx.engine.framesPerSecond),
            lx.engine.framesPerSecond.getValue()),
        new ModelInfo(
            lx.structure.modelName.getString(),
            (modelFile == null) ? null : modelFile.getAbsolutePath(),
            lx.structure.isExternalModel(),
            lx.structure.isStatic.isOn(),
            lx.structure.isDirty(),
            lx.structure.syncModelFile.isOn(),
            // syncModelFile is a structure-tree parameter (lx.structure isn't parented
            // under lx.engine), so it needs Resolve.canonicalPath's /lx-root
            // normalization rather than canonicalPathOrNull, which every other field in
            // this record uses for its lx.engine-descendant parameters (see Resolve's
            // javadoc on the two methods). It's addParameter'd unconditionally at
            // LXStructure construction, so it's always resolvable.
            Resolve.canonicalPath(lx.structure.syncModelFile)));
  }

  /**
   * Resolves {@code save_project}'s optional {@code path}: omitted saves in place over the
   * currently open project file; given, resolves relative paths under {@code
   * LX.Media.PROJECTS} (absolute paths are used as-is — {@link LX#getMediaFile(LX.Media,
   * String)}). Enforces the overwrite guard shared with {@link #resolveModelSaveFile} (see
   * {@link #requireOverwriteOk}).
   *
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} (wire: invalid_argument) — no
   *     open project to save in place over, or an existing target other than the open
   *     project without {@code overwrite=true}
   */
  public static File resolveProjectSaveFile(LX lx, String path, boolean overwrite) {
    File current = lx.getProject();
    File target = (path == null)
        ? requireCurrent(current, "No open project file to save in place — pass path to save as a new file")
        : lx.getMediaFile(LX.Media.PROJECTS, path);
    requireOverwriteOk(target, current, overwrite);
    return target;
  }

  /**
   * Resolves {@code save_model}'s optional {@code path}: omitted re-exports to the
   * currently linked model file; given, resolves relative paths under {@code
   * LX.Media.MODELS} the same way {@link #resolveProjectSaveFile} resolves under {@code
   * PROJECTS} — {@link #saveModel} moves the link there once it runs.
   *
   * <p>Checks {@link #requireDynamicStructure} before resolving {@code path} at all: on a
   * static structure there is never a linked model file, so resolving first would report
   * "no linked model file — pass path" — advice that cannot work, since {@link #saveModel}
   * rejects a static structure regardless of {@code path}. Checking the static-mode guard
   * first gives the caller the actionable reason on the very first call.
   *
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} (wire: invalid_argument) — the
   *     structure is in static-model mode, the model isn't linked to an external file, or
   *     an existing target other than the linked file without {@code overwrite=true}
   */
  public static File resolveModelSaveFile(LX lx, String path, boolean overwrite) {
    requireDynamicStructure(lx);
    File current = lx.structure.getModelFile();
    File target = (path == null)
        ? requireCurrent(current, "No linked model file — pass path to \"Save Model As\"")
        : lx.getMediaFile(LX.Media.MODELS, path);
    requireOverwriteOk(target, current, overwrite);
    return target;
  }

  private static File requireCurrent(File current, String message) {
    if (current == null) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH, message);
    }
    return current;
  }

  /**
   * The guard behind both tools' {@code overwrite} argument: a resolved {@code target}
   * that already exists on disk and isn't {@code current} (the open project, or the
   * linked model file) requires {@code overwrite=true} — otherwise a save-as call would
   * silently clobber an unrelated file the caller never named.
   */
  private static void requireOverwriteOk(File target, File current, boolean overwrite) {
    if (overwrite || !target.exists() || (current != null && sameFile(target, current))) {
      return;
    }
    throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
        "File already exists: " + target.getAbsolutePath() + " — pass overwrite=true to replace it");
  }

  private static boolean sameFile(File a, File b) {
    return a.getAbsolutePath().equals(b.getAbsolutePath());
  }

  /**
   * Persists the project to {@code file} via {@code LX.saveProject(File)}. Not
   * {@code LXCommand}-backed: writing a file to disk is an action, not undoable engine
   * state — the same rationale as {@code fire_trigger} (docs/build-plan.md), there is
   * nothing left in memory for Cmd-Z to restore once the write lands.
   *
   * <p>{@code LX.saveProject} silently no-ops when {@code lx.permissions.canSave()} is
   * false and swallows {@code IOException} on a write failure, logging only
   * (LX.java:1045-1063) — this checks both preconditions up front (a clear up-front error
   * beats a silent no-op) and verifies the write actually landed afterward by re-reading
   * the timestamp LX stamps into every successful save (see {@link #verifyWriteLanded}).
   * A plain "does the open-project pointer already equal {@code file}" check is
   * insufficient on a save-in-place: that condition is already true before the call, so a
   * swallowed {@code IOException} would otherwise be reported as success.
   *
   * <p>{@code LXStructure.save()} writes through to a linked external {@code .lxm}
   * whenever {@code syncModelFile} is on (LXStructure.java:874-880): saving the project
   * can rewrite that file even though nothing about this call mentions the model — see
   * {@code get_project_info}'s {@code model} block before calling this against a project
   * linked to a shared model file.
   *
   * @return {@code file}
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} (wire: invalid_argument) — LX
   *     permissions deny saving, or {@code file}'s parent directory doesn't exist
   * @throws IllegalStateException the write didn't land — a swallowed {@code IOException},
   *     or a write truncated by an {@code IOException} on close — detected by re-reading
   *     {@code file} and checking LX's own timestamp rather than trusting the open-project
   *     pointer; mapped to {@code internal} at the tool seam
   */
  public static File saveProject(LX lx, File file) {
    requireCanSave(lx);
    requireParentDirectory(file);
    long since = System.currentTimeMillis();
    lx.saveProject(file);
    verifyWriteLanded(file, since, "LX.saveProject");
    File open = lx.getProject();
    if (open == null || !sameFile(open, file)) {
      throw new IllegalStateException(
          "LX.saveProject wrote " + file.getAbsolutePath()
              + " but did not move the open project to it");
    }
    return file;
  }

  /**
   * "Save Model As": exports the structure to {@code file} via {@code
   * LXStructure.exportModel(File)}, which also re-points the project's model link at
   * {@code file} on success — the actual fix for the shared-{@code .lxm} hazard {@code
   * get_project_info}'s {@code model} block documents: export to a new path here, then
   * call {@link #saveProject}, and the write-through in {@code LXStructure.save()} hits
   * the new file instead of the old one. Not {@code LXCommand}-backed, same rationale as
   * {@link #saveProject}.
   *
   * <p>{@code exportModel} silently no-ops when {@code lx.permissions.canSave()} is false
   * and swallows {@code IOException} on a write failure (LXStructure.java:928-953) — same
   * verification shape as {@link #saveProject}, and same rationale (re-read {@code file}'s
   * timestamp rather than trust the linked-model pointer, which a save-in-place already
   * satisfies before the call).
   *
   * <p>Requires a dynamic structure: in static-model mode there is no fixture-based model
   * to export, and {@code LXStructure.load} discards any model link on reload for a
   * project carrying a {@code staticModel} key regardless of what this wrote (see {@link
   * #requireDynamicStructure}).
   *
   * @return {@code file}
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} (wire: invalid_argument) — LX
   *     permissions deny saving, {@code file}'s parent directory doesn't exist, or the
   *     structure is in static-model mode
   * @throws IllegalStateException the write didn't land — a swallowed {@code IOException}
   *     or a truncated write, detected the same way as {@link #saveProject}; mapped to
   *     {@code internal} at the tool seam
   */
  public static File saveModel(LX lx, File file) {
    requireDynamicStructure(lx);
    requireCanSave(lx);
    requireParentDirectory(file);
    long since = System.currentTimeMillis();
    lx.structure.exportModel(file);
    verifyWriteLanded(file, since, "LXStructure.exportModel");
    File linked = lx.structure.getModelFile();
    if (linked == null || !sameFile(linked, file)) {
      throw new IllegalStateException(
          "LXStructure.exportModel wrote " + file.getAbsolutePath()
              + " but did not move the linked model file to it");
    }
    return file;
  }

  /**
   * Guards {@link #saveModel} the way {@code Fixtures.requireDynamicStructure} guards
   * fixture mutations — a static model (see {@code lx.structure.isStatic}) has no
   * fixture-based model to export, so writing one is meaningless: the resulting {@code
   * .lxm} would carry an empty {@code fixtures} array, and any link this points at is
   * discarded on the next project load anyway ({@code LXStructure.load} takes the {@code
   * KEY_STATIC_MODEL} branch first, which nulls {@code modelFile}).
   */
  private static void requireDynamicStructure(LX lx) {
    if (lx.structure.isStatic.isOn()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "cannot save a model: the structure is in static-model mode (model.isStatic "
              + "true) — there is no fixture-based model to export, and any file this "
              + "would write is discarded on the next project load");
    }
  }

  /**
   * Verifies a save actually landed by re-reading {@code file} and checking the timestamp
   * LX stamps into every successful serialization ({@code LX.KEY_TIMESTAMP}, written by
   * both {@code LX.saveProjectJson()} at LX.java:1068 and {@code
   * LXStructure.exportModel()} at LXStructure.java:935) — not {@code File.lastModified()},
   * which is unreliable in both directions on APFS (identical length plus same-millisecond
   * mtime on a fast save-in-place). Re-parsing also catches a write truncated by an {@code
   * IOException} on close: both writers move their success pointer ({@code
   * LX.setProject}/{@code LXStructure.modelFile}) from inside the try-with-resources block,
   * before the writer is flushed and closed, so that pointer can already read as "moved"
   * even when the file on disk is corrupt.
   *
   * @throws IllegalStateException naming {@code file} — the write didn't land
   */
  private static void verifyWriteLanded(File file, long since, String writer) {
    if (!file.isFile() || file.length() == 0) {
      throw new IllegalStateException(
          writer + " did not write a file to " + file.getAbsolutePath());
    }
    JsonObject obj;
    try (FileReader reader = new FileReader(file)) {
      obj = JsonParser.parseReader(reader).getAsJsonObject();
    } catch (IOException | JsonParseException | IllegalStateException e) {
      throw new IllegalStateException(
          writer + " wrote " + file.getAbsolutePath() + " but it is not valid JSON — the "
              + "write was likely truncated by an IOException on close", e);
    }
    long timestamp = obj.has(LX.KEY_TIMESTAMP) ? obj.get(LX.KEY_TIMESTAMP).getAsLong() : -1;
    if (timestamp < since) {
      throw new IllegalStateException(
          writer + " did not update " + file.getAbsolutePath()
              + " — its on-disk timestamp is stale, so the write was swallowed");
    }
  }

  private static void requireCanSave(LX lx) {
    if (!lx.permissions.canSave()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "LX permissions deny saving (lx.permissions.canSave() is false)");
    }
  }

  private static void requireParentDirectory(File file) {
    File parent = file.getAbsoluteFile().getParentFile();
    if (parent != null && !parent.isDirectory()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Parent directory does not exist: " + parent.getAbsolutePath());
    }
  }
}
