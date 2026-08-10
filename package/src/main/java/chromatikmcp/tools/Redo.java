package chromatikmcp.tools;

import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.CommandHistory;

public final class Redo implements LxTool {

  @Override
  public String name() {
    return "redo";
  }

  @Override
  public String description() {
    return "Redo the newest command in Chromatik's shared linear history, exactly like one "
        + "Cmd-Shift-Z. History includes command-backed changes from the UI and other MCP clients, "
        + "not only this session. Returns changed:false when there is nothing to redo; otherwise "
        + "command names what was redone. One call affects one command only. Any new command-backed "
        + "mutation after an undo clears LX's redo stack. Re-list affected state after redoing "
        + "structural or move commands because canonical paths may have shifted. Call this tool "
        + "directly; it is unavailable inside apply_operations so a batch cannot unexpectedly "
        + "rewrite shared history. If the LX command throws while redoing, the call returns "
        + "internal after Chromatik also reports the error; LX clears both history stacks, the "
        + "error reports post-failure canUndo/canRedo, and engine state may be partially changed "
        + "— inspect affected state before continuing.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public boolean batchable() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    return Result.ok(CommandHistoryPayload.of(CommandHistory.redo(lx)));
  }
}
