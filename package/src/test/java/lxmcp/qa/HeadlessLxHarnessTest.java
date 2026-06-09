package lxmcp.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;

/**
 * The PR-1c headless-harness gate: prove an {@link LX} instance constructs and
 * advances frames with no GUI/GL context and no engine thread, and that an
 * {@code LXCommand} round-trips through {@code do -> undo -> assert restored}.
 *
 * <p>This is the executable counterpart to {@code docs/spike/qa-strategy.md}: it
 * is the minimal seed every downstream tool test builds on (construct headless,
 * call a domain primitive, assert engine state, undo, assert restored). If LX
 * ever stopped being headless-testable this gate fails loudly — an
 * architecture-level escalation, not something to work around.
 */
class HeadlessLxHarnessTest {

  private LX lx;

  /** Build LX exactly as {@code heronarts.lx.headless.LXHeadless} does, but never start the engine. */
  private LX newHeadlessLx() {
    LXModel model = new GridModel(8, 8);
    this.lx = new LX(model);
    return this.lx;
  }

  /**
   * Dispose so LX's non-daemon audio/MIDI device-scan threads don't outlive the test. Left
   * running they contend on a JDK-global {@code javax.sound} monitor and can deadlock later
   * constructions in the same JVM.
   */
  @AfterEach
  void tearDown() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }

  @Test
  void constructsAndTicksWithoutAThread() {
    LX lx = newHeadlessLx();
    // Never call lx.engine.start(): run() advances one frame synchronously on
    // the test thread. Several ticks must not throw.
    for (int i = 0; i < 3; i++) {
      lx.engine.run();
    }
    assertTrue(lx.engine.speed.getValue() > 0, "engine should be live after manual ticks");
  }

  @Test
  void commandRoundTripsThroughUndo() {
    LX lx = newHeadlessLx();

    double original = lx.engine.speed.getValue();
    double target = original / 2.0;
    assertNotEquals(original, target, "test fixture must actually change the value");

    lx.command.perform(new LXCommand.Parameter.SetValue(lx.engine.speed, target));
    assertEquals(target, lx.engine.speed.getValue(), 1e-9, "perform should apply the mutation");

    lx.command.undo();
    assertEquals(original, lx.engine.speed.getValue(), 1e-9, "undo should restore prior state");
  }
}
