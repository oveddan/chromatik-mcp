package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;

import chromatikmcp.HeadlessLxTest;

class CommandHistoryTest extends HeadlessLxTest {

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
}
