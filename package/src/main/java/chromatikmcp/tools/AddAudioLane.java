package chromatikmcp.tools;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.AudioClipLane;

import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Compositions;
import chromatikmcp.domain.Resolve;

public final class AddAudioLane implements LxTool {

  @Override
  public String name() {
    return "add_audio_lane";
  }

  @Override
  public String description() {
    return "Add an audio lane to the arrange composition (/lx/timeline/composition), "
        + "loading an audio file from an absolute path on the Chromatik machine (WAV/AIFF "
        + "— whatever javax.sound.sampled reads; MP3 is not supported). The new lane lands "
        + "at the TOP of the lane list (index 0), shifting every other lane's index — the "
        + "returned laneCount shows the new lane total — the composition length grows to "
        + "at least the audio length, and an empty composition gets its timeline enabled. "
        + "Returns the shared lane-creation envelope {clipPath, lane, laneCount} plus the "
        + "audio event {index, cursor, fileName, sourceLengthMs, length, end, filePath} "
        + "and the composition's resulting length. The lane's enabled/gain are "
        + "registered parameters — use set_parameter on the lane path. Lane paths are "
        + "positional: they shift whenever lanes are added, removed, or moved, so re-run "
        + "list_clip_lanes rather than reuse a path from an earlier response. Undoable in "
        + "Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("file", Schemas.string(
        "Absolute path of the audio file on the machine running Chromatik "
            + "(WAV/AIFF; rejected with invalid_argument if missing or unreadable)"));
    return Schemas.object(properties, List.of("file"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    File file = new File(Args.requireString(args, "file"));
    AudioClipLane lane = Compositions.addAudioLane(lx, file);
    // Shared lane-creation envelope {clipPath, lane, laneCount}, extended additively
    // with the imported event and the grown composition length.
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("clipPath", Resolve.canonicalPath(lane.clip));
    payload.put("lane", Payloads.laneSummary(ClipLanes.summary(lane)));
    payload.put("laneCount", lane.clip.lanes.size());
    // A freshly imported lane carries exactly the one event built from the file.
    payload.put("event",
        Payloads.audioEvent(Compositions.describeAudioEvent(lane, lane.events.get(0))));
    payload.put("compositionLength", Payloads.cursor(lane.clip, lane.clip.length.cursor));
    return Result.ok(payload);
  }
}
