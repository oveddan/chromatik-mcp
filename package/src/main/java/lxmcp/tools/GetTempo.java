package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.Tempos;

public final class GetTempo implements LxTool {

  @Override
  public String name() {
    return "get_tempo";
  }

  @Override
  public String description() {
    return "The engine tempo: bpm (settable via set_parameter on bpm.path), clockSource "
        + "(INTERNAL = free-running clock driven by bpm, MIDI = synced to an external MIDI "
        + "clock, OSC = driven by incoming OSC tempo messages — bpm is read-only under MIDI/"
        + "OSC), beatsPerBar (time signature), enabled (tempo trigger modulation on/off), and "
        + "launchQuantization (governs quantized pattern/clip launches — a fire_trigger call "
        + "on a quantized trigger under a non-NONE quantization reports pending:true and "
        + "defers to the next tempo boundary rather than firing immediately). tapPath is a "
        + "momentary trigger: fire it with fire_trigger repeatedly, in rhythm, to learn bpm "
        + "from the timing between taps. nudgeUpPath/nudgeDownPath are momentary triggers "
        + "that temporarily bend the tempo while held. triggerSourcePath is a beat pulse "
        + "usable as a wire_trigger source. beatCount/barCount/beatCountWithinBar/basis "
        + "report the current position in the running tempo clock.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Tempos.TempoInfo info = Tempos.info(lx);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("bpm", info.bpm().toMap());
    payload.put("clockSource", info.clockSource().toMap());
    payload.put("beatsPerBar", info.beatsPerBar().toMap());
    payload.put("enabled", info.enabled().toMap());
    payload.put("launchQuantization", info.launchQuantization().toMap());
    payload.put("tapPath", info.tapPath());
    payload.put("nudgeUpPath", info.nudgeUpPath());
    payload.put("nudgeDownPath", info.nudgeDownPath());
    payload.put("triggerSourcePath", info.triggerSourcePath());
    payload.put("beatCount", info.beatCount());
    payload.put("barCount", info.barCount());
    payload.put("beatCountWithinBar", info.beatCountWithinBar());
    payload.put("basis", info.basis());
    payload.put("periodMs", info.periodMs());
    return Result.ok(payload);
  }
}
