package chromatikmcp.domain;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;

/** One-step access to LX's shared linear undo/redo history. */
public final class CommandHistory {

  public enum Action {
    UNDO,
    REDO
  }

  public record HistoryResult(
      Action action,
      boolean changed,
      String command,
      String nextUndoCommand,
      String nextRedoCommand) {}

  private CommandHistory() {}

  /** Undo the newest command, if any, and verify that LX moved it to the redo stack. */
  public static HistoryResult undo(LX lx) {
    LXCommand command = lx.command.getUndoCommand();
    if (command == null) {
      return result(lx, Action.UNDO, false, null);
    }

    String description = describe(command);
    lx.command.undo();
    if (lx.command.getRedoCommand() != command) {
      throw new IllegalStateException("Undo failed for command: " + description);
    }
    return result(lx, Action.UNDO, true, description);
  }

  /** Redo the newest reverted command, if any, and verify that LX moved it to the undo stack. */
  public static HistoryResult redo(LX lx) {
    LXCommand command = lx.command.getRedoCommand();
    if (command == null) {
      return result(lx, Action.REDO, false, null);
    }

    String description = describe(command);
    lx.command.redo();
    if (lx.command.getUndoCommand() != command) {
      throw new IllegalStateException("Redo failed for command: " + description);
    }
    return result(lx, Action.REDO, true, description);
  }

  private static HistoryResult result(
      LX lx, Action action, boolean changed, String command) {
    return new HistoryResult(
        action,
        changed,
        command,
        describe(lx.command.getUndoCommand()),
        describe(lx.command.getRedoCommand()));
  }

  private static String describe(LXCommand command) {
    if (command == null) {
      return null;
    }
    try {
      String description = command.getDescription();
      if (description != null && !description.isBlank()) {
        return description;
      }
    } catch (RuntimeException ignored) {
      // Mirror LXCommandEngine's private getName() fallback without depending on internals.
    }
    return command.getClass().getSimpleName();
  }
}
