package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.MacroKnobs;

/**
 * The PR-4 gate, instantiating the qa-strategy verification template for the first
 * mutation: do → undo → assert restored. The undo assertion doubles as proof the
 * primitive routes through a real LXCommand rather than a direct edit.
 */
class ModulatorsTest {

  private LX lx;

  private LX newHeadlessLx() {
    this.lx = new LX(new GridModel(8, 8));
    return this.lx;
  }

  @AfterEach
  void tearDown() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }

  @Test
  void primitiveMutatesEngineState() {
    LX lx = newHeadlessLx();
    int before = lx.engine.modulation.modulators.size();

    LXModulator added = Modulators.addGlobalModulator(lx, MacroKnobs.class);

    assertEquals(before + 1, lx.engine.modulation.modulators.size());
    assertInstanceOf(MacroKnobs.class, added);
    assertSame(added, lx.engine.modulation.modulators.get(before),
        "the returned modulator is the appended one");
    assertTrue(added.isRunning(),
        "running: AddModulator calls autostart() and MacroKnobs doesn't disable autoStart");
  }

  @Test
  void primitiveThrowsWhenTheCommandSilentlyFails() {
    LX lx = newHeadlessLx();
    int before = lx.engine.modulation.modulators.size();

    // An abstract class can't instantiate; perform() swallows the failure (pushes a UI
    // error and returns normally), so only the primitive's state-read verification
    // surfaces it. This is the headline behavior of the Mutations convention.
    assertThrows(IllegalStateException.class,
        () -> Modulators.addGlobalModulator(lx, LXModulator.class));
    assertEquals(before, lx.engine.modulation.modulators.size(), "nothing was added");
  }

  @Test
  void primitiveUndoRestoresState() {
    LX lx = newHeadlessLx();
    int before = lx.engine.modulation.modulators.size();

    LXModulator added = Modulators.addGlobalModulator(lx, MacroKnobs.class);
    assertEquals(before + 1, lx.engine.modulation.modulators.size());

    lx.command.undo();

    assertEquals(before, lx.engine.modulation.modulators.size(), "undo restores the list");
    assertFalse(lx.engine.modulation.modulators.contains(added), "the instance is removed");
  }
}
