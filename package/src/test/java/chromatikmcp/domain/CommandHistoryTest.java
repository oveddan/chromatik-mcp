package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;

import chromatikmcp.HeadlessLxTest;

class CommandHistoryTest extends HeadlessLxTest {

  private static final class FailingUndoCommand extends LXCommand {
    private final int[] state;

    private FailingUndoCommand(int[] state) {
      this.state = state;
    }

    @Override
    public String getDescription() {
      return "Failing undo";
    }

    @Override
    public void perform(LX lx) {
      this.state[0] = 1;
    }

    @Override
    public void undo(LX lx) {
      this.state[0] = -1;
      throw new IllegalStateException("undo boom");
    }
  }

  private static final class FailingRedoCommand extends LXCommand {
    private final int[] state;
    private boolean firstPerform = true;

    private FailingRedoCommand(int[] state) {
      this.state = state;
    }

    @Override
    public String getDescription() {
      return "Failing redo";
    }

    @Override
    public void perform(LX lx) {
      if (this.firstPerform) {
        this.firstPerform = false;
        this.state[0] = 1;
        return;
      }
      this.state[0] = 2;
      throw new IllegalStateException("redo boom");
    }

    @Override
    public void undo(LX lx) {
      this.state[0] = 0;
    }
  }

  @Test
  void undoAndRedoMoveOneNamedCommandBetweenStacks() {
    LX lx = newHeadlessLx();
    double before = lx.engine.speed.getValue();
    LXCommand command = new LXCommand.Parameter.SetValue(lx.engine.speed, 0.5);
    lx.command.perform(command);

    CommandHistory.HistoryResult undone = CommandHistory.undo(lx);
    assertEquals(CommandHistory.Action.UNDO, undone.action());
    assertTrue(undone.changed());
    assertEquals(command.getDescription(), undone.command());
    assertEquals(before, lx.engine.speed.getValue(), 1e-9);
    assertNull(undone.nextUndoCommand());
    assertEquals(command.getDescription(), undone.nextRedoCommand());

    CommandHistory.HistoryResult redone = CommandHistory.redo(lx);
    assertEquals(CommandHistory.Action.REDO, redone.action());
    assertTrue(redone.changed());
    assertEquals(command.getDescription(), redone.command());
    assertEquals(0.5, lx.engine.speed.getValue(), 1e-9);
    assertEquals(command.getDescription(), redone.nextUndoCommand());
    assertNull(redone.nextRedoCommand());
  }

  @Test
  void emptyHistoryIsAnExplicitNoOp() {
    LX lx = newHeadlessLx();

    CommandHistory.HistoryResult undo = CommandHistory.undo(lx);
    assertFalse(undo.changed());
    assertNull(undo.command());
    assertNull(undo.nextUndoCommand());
    assertNull(undo.nextRedoCommand());

    CommandHistory.HistoryResult redo = CommandHistory.redo(lx);
    assertFalse(redo.changed());
    assertNull(redo.command());
    assertNull(redo.nextUndoCommand());
    assertNull(redo.nextRedoCommand());
  }

  @Test
  void undoIsStrictlyOneStepAndReportsBothSidesOfHistory() {
    LX lx = newHeadlessLx();
    LXCommand first = new LXCommand.Parameter.SetValue(lx.engine.speed, 0.5);
    lx.command.perform(first);
    LXCommand second = new LXCommand.Parameter.SetValue(lx.engine.speed, 0.25);
    lx.command.perform(second);

    CommandHistory.HistoryResult result = CommandHistory.undo(lx);

    assertEquals(0.5, lx.engine.speed.getValue(), 1e-9);
    assertEquals(second.getDescription(), result.command());
    assertEquals(first.getDescription(), result.nextUndoCommand());
    assertEquals(second.getDescription(), result.nextRedoCommand());
  }

  @Test
  void newMutationAfterUndoClearsRedoHistory() {
    LX lx = newHeadlessLx();
    double before = lx.engine.speed.getValue();
    lx.command.perform(new LXCommand.Parameter.SetValue(lx.engine.speed, 0.5));
    CommandHistory.undo(lx);
    assertTrue(lx.command.getRedoCommand() != null);

    lx.command.perform(new LXCommand.Parameter.SetValue(lx.engine.framesPerSecond, 30));

    assertNull(lx.command.getRedoCommand());
    CommandHistory.HistoryResult redo = CommandHistory.redo(lx);
    assertFalse(redo.changed());
    assertEquals(before, lx.engine.speed.getValue(), 1e-9);
  }

  @Test
  void undoFailureReportsClearedHistoryAndPossiblePartialState() {
    LX lx = newHeadlessLx();
    lx.command.perform(new LXCommand.Parameter.SetValue(lx.engine.speed, 0.5));
    int[] state = {0};
    lx.command.perform(new FailingUndoCommand(state));

    IllegalStateException failure = assertThrows(
        IllegalStateException.class, () -> CommandHistory.undo(lx));

    assertEquals(-1, state[0], "the failing command partially changed state before throwing");
    assertTrue(failure.getMessage().contains("post-failure canUndo=false, canRedo=false"));
    assertTrue(failure.getMessage().contains("partially changed engine state"));
    assertNull(lx.command.getUndoCommand(), "LX cleared older undo history too");
    assertNull(lx.command.getRedoCommand());
  }

  @Test
  void redoFailureReportsClearedHistoryAndPossiblePartialState() {
    LX lx = newHeadlessLx();
    lx.command.perform(new LXCommand.Parameter.SetValue(lx.engine.speed, 0.5));
    int[] state = {0};
    lx.command.perform(new FailingRedoCommand(state));
    CommandHistory.undo(lx);

    IllegalStateException failure = assertThrows(
        IllegalStateException.class, () -> CommandHistory.redo(lx));

    assertEquals(2, state[0], "the failing command partially changed state before throwing");
    assertTrue(failure.getMessage().contains("post-failure canUndo=false, canRedo=false"));
    assertTrue(failure.getMessage().contains("partially changed engine state"));
    assertNull(lx.command.getUndoCommand(), "LX cleared older undo history too");
    assertNull(lx.command.getRedoCommand());
  }
}
