package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXClip;

import chromatikmcp.domain.ClipEvents.AudioValue;
import chromatikmcp.domain.ClipEvents.EventDetail;
import chromatikmcp.domain.ClipEvents.EventPage;
import chromatikmcp.domain.ClipEvents.MidiNoteValue;
import chromatikmcp.domain.ClipEvents.ParameterEventEnvelope;
import chromatikmcp.domain.ClipEvents.ParameterValue;
import chromatikmcp.domain.ClipEvents.PatternValue;
import chromatikmcp.domain.ClipEvents.Span;
import chromatikmcp.domain.ClipEvents.TextNoteValue;
import chromatikmcp.domain.ClipLanes.LaneSummary;
import chromatikmcp.domain.Clips.ClipDetail;
import chromatikmcp.domain.Clips.ClipEnvelope;
import chromatikmcp.domain.Compositions.AudioEventDetail;
import chromatikmcp.domain.Compositions.CompositionDetail;
import chromatikmcp.domain.Compositions.LocatorList;
import chromatikmcp.domain.Compositions.LocatorSummary;
import chromatikmcp.domain.Cursors;
import chromatikmcp.domain.Cursors.CursorInfo;

/** Payload-shaping helpers shared across tool handlers. */
final class Payloads {

  private Payloads() {}

  /**
   * Puts {@code key} only when {@code value} is non-null. Used for fields backed by
   * {@code Resolve.canonicalPathOrNull} (an object that isn't path-registered has a null
   * path) — omit the key rather than emit a bogus "/null" or a literal JSON null, since a
   * key whose type flips between string and null breaks clients.
   */
  static void putIfPresent(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  // ── Composition/clip serializers ──────────────────────────────────────────────
  // The MCP boundary for the composition surface: the domain returns typed results, and
  // these own every wire key and map construction, so sibling tools emitting the same
  // named shape can't drift. Nesting mirrors the records — a serializer for a composed
  // shape calls the serializer of each part rather than restating its keys.

  /**
   * Read-side cursor object: {@code {millis, beatCount, beatBasis, formatted}}. Delegates
   * to the record-attached serializer, which {@code ParameterInfo} also reaches for a
   * {@code Cursor.Parameter}'s value — see {@code Cursors.CursorInfo}.
   */
  static Map<String, Object> cursor(CursorInfo cursor) {
    return cursor.toMap();
  }

  /** Snapshot-and-serialize, for the tools that emit a live engine cursor directly. */
  static Map<String, Object> cursor(LXClip clip, Cursor cursor) {
    return cursor(Cursors.describe(clip, cursor));
  }

  /** Shared clip envelope: identity, timeBase, and every marker as a cursor object. */
  static Map<String, Object> clipEnvelope(ClipEnvelope envelope) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("path", envelope.path());
    map.put("label", envelope.label());
    map.put("timeBase", envelope.timeBase());
    map.put("referenceBpm", envelope.referenceBpm());
    map.put("length", cursor(envelope.length()));
    map.put("loop", envelope.loop());
    map.put("loopStart", cursor(envelope.loopStart()));
    map.put("loopEnd", cursor(envelope.loopEnd()));
    map.put("playStart", cursor(envelope.playStart()));
    map.put("playEnd", cursor(envelope.playEnd()));
    map.put("insertMarker", cursor(envelope.insertMarker()));
    map.put("playhead", cursor(envelope.playhead()));
    map.put("running", envelope.running());
    map.put("hasContent", envelope.hasContent());
    map.put("laneCount", envelope.laneCount());
    return map;
  }

  /** get_clip / launch_clip / stop_clip: the envelope plus {@code pending}. */
  static Map<String, Object> clip(ClipDetail clip) {
    Map<String, Object> map = clipEnvelope(clip.envelope());
    map.put("pending", clip.pending());
    return map;
  }

  /** get_composition: the clip envelope plus arm/sync/locatorCount and the lane list. */
  static Map<String, Object> composition(CompositionDetail composition) {
    Map<String, Object> map = clipEnvelope(composition.envelope());
    map.put("armed", composition.armed());
    map.put("sync", composition.sync());
    map.put("locatorCount", composition.locatorCount());
    map.put("lanes", laneSummaries(composition.lanes()));
    return map;
  }

  /** Per-lane summary: path, index, type, label, eventCount, uiVisible, removable, target. */
  static Map<String, Object> laneSummary(LaneSummary lane) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("path", lane.path());
    map.put("index", lane.index());
    map.put("type", lane.type());
    map.put("label", lane.label());
    map.put("eventCount", lane.eventCount());
    map.put("uiVisible", lane.uiVisible());
    map.put("removable", lane.removable());
    putIfPresent(map, "parameterPath", lane.parameterPath());
    putIfPresent(map, "busPath", lane.busPath());
    putIfPresent(map, "channelPath", lane.channelPath());
    return map;
  }

  /** Lane summaries in engine order. */
  static List<Map<String, Object>> laneSummaries(List<LaneSummary> lanes) {
    List<Map<String, Object>> list = new ArrayList<>(lanes.size());
    for (LaneSummary lane : lanes) {
      list.add(laneSummary(lane));
    }
    return list;
  }

  /**
   * One event: the {@code {index, cursor}} address plus the lane-kind value fields
   * flattened onto the same level, so one wire shape covers every lane type.
   */
  static Map<String, Object> event(EventDetail event) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("index", event.index());
    map.put("cursor", cursor(event.cursor()));
    switch (event.value()) {
      case null -> { }
      case ParameterValue v -> {
        map.put("normalized", v.normalized());
        map.put("curve", v.curve());
        map.put("shape", v.shape());
      }
      case PatternValue v -> {
        map.put("patternLabel", v.patternLabel());
        putIfPresent(map, "patternPath", v.patternPath());
      }
      case MidiNoteValue v -> {
        map.put("noteOn", v.noteOn());
        map.put("pitch", v.pitch());
        map.put("velocity", v.velocity());
        map.put("midiChannel", v.midiChannel());
      }
      case AudioValue v -> {
        map.put("fileName", v.fileName());
        map.put("sourceLengthMs", v.sourceLengthMs());
        putSpan(map, v.span());
      }
      case TextNoteValue v -> {
        map.put("note", v.note());
        putSpan(map, v.span());
      }
    }
    return map;
  }

  /** get_clip_lane's paging envelope, events included. */
  static Map<String, Object> eventPage(EventPage page) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("eventCount", page.eventCount());
    map.put("total", page.total());
    map.put("offset", page.offset());
    map.put("limit", page.limit());
    map.put("returned", page.returned());
    map.put("truncated", page.truncated());
    List<Map<String, Object>> events = new ArrayList<>(page.events().size());
    for (EventDetail event : page.events()) {
      events.add(event(event));
    }
    map.put("events", events);
    return map;
  }

  /**
   * The one echo shape for both automation-point mutations: lane identity, then the exact
   * {@link #event} shape get_clip_lane emits, then the resulting lane eventCount.
   */
  static Map<String, Object> parameterEvent(ParameterEventEnvelope envelope) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("lanePath", envelope.lanePath());
    putIfPresent(map, "parameterPath", envelope.parameterPath());
    map.put("timeBase", envelope.timeBase());
    map.putAll(event(envelope.event()));
    map.put("eventCount", envelope.eventCount());
    return map;
  }

  /** Locator summary: path, 1-based index, label, cursor. */
  static Map<String, Object> locator(LocatorSummary locator) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("path", locator.path());
    map.put("index", locator.index());
    map.put("label", locator.label());
    map.put("cursor", cursor(locator.cursor()));
    return map;
  }

  /** list_locators: composition identity plus every locator in cursor order. */
  static Map<String, Object> locatorList(LocatorList locators) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("path", locators.path());
    map.put("timeBase", locators.timeBase());
    map.put("locatorCount", locators.locatorCount());
    List<Map<String, Object>> list = new ArrayList<>(locators.locators().size());
    for (LocatorSummary locator : locators.locators()) {
      list.add(locator(locator));
    }
    map.put("locators", list);
    return map;
  }

  /** add_audio_lane's event: the shared event shape plus the resolved {@code filePath}. */
  static Map<String, Object> audioEvent(AudioEventDetail audio) {
    Map<String, Object> map = event(audio.event());
    map.put("filePath", audio.filePath());
    return map;
  }

  private static void putSpan(Map<String, Object> map, Span span) {
    map.put("length", cursor(span.length()));
    map.put("end", cursor(span.end()));
  }
}
