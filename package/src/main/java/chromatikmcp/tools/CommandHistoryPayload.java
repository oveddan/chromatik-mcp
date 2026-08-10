package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import chromatikmcp.domain.CommandHistory;

/** Shared wire serializer for undo and redo results. */
final class CommandHistoryPayload {

  private CommandHistoryPayload() {}

  static Map<String, Object> of(CommandHistory.HistoryResult result) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("action", result.action().name().toLowerCase(Locale.ROOT));
    payload.put("changed", result.changed());
    if (result.command() != null) {
      payload.put("command", result.command());
    }
    payload.put("canUndo", result.nextUndoCommand() != null);
    payload.put("canRedo", result.nextRedoCommand() != null);
    if (result.nextUndoCommand() != null) {
      payload.put("nextUndoCommand", result.nextUndoCommand());
    }
    if (result.nextRedoCommand() != null) {
      payload.put("nextRedoCommand", result.nextRedoCommand());
    }
    return payload;
  }
}
