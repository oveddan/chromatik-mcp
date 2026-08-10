package chromatikmcp.tools;

import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.CommandHistory;

public final class Undo implements LxTool {

  @Override
  public String name() {
    return "undo";
  }

  @Override
  public String description() {
    return "Undo the newest command in Chromatik's shared linear history, exactly like one "
        + "Cmd-Z. History includes command-backed changes from the UI and other MCP clients, "
        + "not only this session. Returns changed:false when there is nothing to undo; otherwise "
        + "command names what was undone. One call affects one command only — it cannot selectively "
        + "skip newer work. Re-list affected state after undoing structural or move commands because "
        + "canonical paths may have shifted. Call this tool directly; it is unavailable inside "
        + "apply_operations so a batch cannot unexpectedly rewrite shared history.";
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
    return Result.ok(CommandHistoryPayload.of(CommandHistory.undo(lx)));
  }
}
