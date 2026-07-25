package chromatikmcp.domain;

import java.io.File;

import heronarts.lx.LX;

/** Read-only snapshot of project-level state. */
public final class Projects {

  public record OscInfo(int receivePort, boolean receiveActive,
      int transmitPort, boolean transmitActive) {}

  public record OutputInfo(String enabledPath, boolean enabled, String brightnessPath, double brightness,
      String gammaPath, double gamma, String gammaModePath, String gammaMode) {}

  public record EngineInfo(
      String speedPath, double speed, String framesPerSecondPath, double framesPerSecond) {}

  public record ProjectInfo(
      String lxVersion, String projectPath, int channelCount, OscInfo osc, OutputInfo output,
      EngineInfo engine) {}

  private Projects() {}

  /** Call on the engine thread; the returned record is safe to read anywhere. */
  public static ProjectInfo info(LX lx) {
    File project = lx.getProject();
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
            lx.engine.framesPerSecond.getValue()));
  }
}
