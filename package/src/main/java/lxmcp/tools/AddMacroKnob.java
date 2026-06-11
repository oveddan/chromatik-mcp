package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.MacroKnobs;

import lxmcp.domain.Modulators;

public final class AddMacroKnob implements LxTool {

  @Override
  public String name() {
    return "add_macro_knob";
  }

  @Override
  public String description() {
    return "Add a global Macro Knobs modulator (a bank of eight mappable knobs; the "
        + "Chromatik UI shows five unless expanded). Undoable in Chromatik with Cmd-Z.";
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
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXModulator modulator = Modulators.addGlobalModulator(lx, MacroKnobs.class);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", modulator.getCanonicalPath());
    payload.put("id", modulator.getId());
    payload.put("label", modulator.getLabel());
    payload.put("class", modulator.getClass().getName());
    payload.put("running", modulator.isRunning());
    return Result.ok(payload);
  }
}
