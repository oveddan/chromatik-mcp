package lxmcp.domain;

import java.io.File;

import heronarts.lx.LX;

/** Read-only snapshot of project-level state. */
public final class Projects {

  public record ProjectInfo(String lxVersion, String projectPath, int channelCount) {}

  private Projects() {}

  /** Call on the engine thread; the returned record is safe to read anywhere. */
  public static ProjectInfo info(LX lx) {
    File project = lx.getProject();
    return new ProjectInfo(
        LX.VERSION,
        (project == null) ? null : project.getAbsolutePath(),
        lx.engine.mixer.channels.size());
  }
}
