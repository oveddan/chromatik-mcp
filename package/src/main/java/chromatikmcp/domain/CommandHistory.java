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
      throw failure(Action.UNDO, description, lx);
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
      throw failure(Action.REDO, description, lx);
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

  private static IllegalStateException failure(Action action, String command, LX lx) {
    boolean canUndo = lx.command.getUndoCommand() != null;
    boolean canRedo = lx.command.getRedoCommand() != null;
    String verb = (action == Action.UNDO) ? "Undo" : "Redo";
    return new IllegalStateException(
        verb + " failed for command '" + command + "'; post-failure canUndo=" + canUndo
            + ", canRedo=" + canRedo + ". LX clears the full undo/redo history when a command "
            + "fails, and the command may have partially changed engine state; inspect affected "
            + "state before continuing.");
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
    String className = command.getClass().getName();
    String marker = ".LXCommand.";
    int markerIndex = className.indexOf(marker);
    if (markerIndex >= 0) {
      return className.substring(markerIndex + marker.length());
    }
    String simpleName = command.getClass().getSimpleName();
    return simpleName.isBlank() ? className : simpleName;
  }
}
