package lxmcp.domain;

import heronarts.lx.LX;
import heronarts.lx.Tempo;

/**
 * Read-only snapshot of the engine tempo: the settable transport parameters (bpm,
 * clockSource, beatsPerBar, enabled, launchQuantization), the tap/nudge/trigger-source
 * paths that back {@code fire_trigger}/{@code wire_trigger}, and the current beat
 * position.
 */
public final class Tempos {

  public record TempoInfo(
      Parameters.ParameterInfo bpm,
      Parameters.ParameterInfo clockSource,
      Parameters.ParameterInfo beatsPerBar,
      Parameters.ParameterInfo enabled,
      Parameters.ParameterInfo launchQuantization,
      String tapPath,
      String nudgeUpPath,
      String nudgeDownPath,
      String triggerSourcePath,
      int beatCount,
      int barCount,
      int beatCountWithinBar,
      double basis,
      double periodMs) {}

  private Tempos() {}

  /** Call on the engine thread; the returned record is safe to read anywhere. */
  public static TempoInfo info(LX lx) {
    Tempo tempo = lx.engine.tempo;
    return new TempoInfo(
        Parameters.describe(tempo.bpm),
        Parameters.describe(tempo.clockSource),
        Parameters.describe(tempo.beatsPerBar),
        Parameters.describe(tempo.enabled),
        Parameters.describe(tempo.launchQuantization),
        Resolve.canonicalPath(tempo.tap),
        Resolve.canonicalPath(tempo.nudgeUp),
        Resolve.canonicalPath(tempo.nudgeDown),
        Resolve.canonicalPath(tempo.getTriggerSource()),
        tempo.beatCount(),
        tempo.barCount(),
        tempo.beatCountWithinBar(),
        tempo.basis(),
        tempo.period.getValue());
  }
}
