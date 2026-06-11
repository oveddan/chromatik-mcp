package lxmcp.domain;

import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.modulator.LXModulator;

/** Mutations on the global modulation engine, routed through LXCommand for undo. */
public final class Modulators {

  private Modulators() {}

  /**
   * Add a modulator to the global modulation engine; call on the engine thread.
   *
   * <p>{@code lx.command.perform()} swallows command failures (it pushes a UI error and
   * returns normally — LXCommandEngine.java:77-85), so success is verified by observing
   * the mutation; a command that didn't apply throws rather than silently returning the
   * wrong modulator.
   */
  public static LXModulator addGlobalModulator(LX lx, Class<? extends LXModulator> kind) {
    List<LXModulator> modulators = lx.engine.modulation.modulators;
    int before = modulators.size();
    lx.command.perform(new LXCommand.Modulation.AddModulator(lx.engine.modulation, kind));
    if (modulators.size() != before + 1) {
      throw new IllegalStateException("AddModulator did not add a " + kind.getName());
    }
    return modulators.get(before);
  }
}
