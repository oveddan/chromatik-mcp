package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;
import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Modulators;
import chromatikmcp.domain.Resolve;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXClipLane;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.ParameterClipLane;
import heronarts.lx.clip.PatternClipLane;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.modulator.MacroKnobs;
import heronarts.lx.pattern.LXPattern;

/**
 * F3 lane lifecycle: add_clip_lane / remove_clip_lane / move_clip_lane /
 * set_clip_lane_visible, handler-level. The load-bearing contracts pinned here: the
 * state-read alreadyExisted detector (AddLane/AddPatternLane are isIgnored no-ops on an
 * existing lane, which Commands.perform would misread as failure), the auto-managed-lane
 * guard that must run before the command engine (risk 6), the actual-index echo on moves
 * the composition constrains, the not-undoable direct uiVisible edit (risk 7), and the
 * remove_modulator cascade that invalidates earlier lane lists (risk 9).
 */
class LaneLifecycleToolsTest extends CompositionTestSupport {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    return (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> lane(Map<String, Object> payload) {
    return (Map<String, Object>) assertInstanceOf(Map.class, payload.get("lane"));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> lanes(Map<String, Object> payload) {
    return (List<Map<String, Object>>) assertInstanceOf(List.class, payload.get("lanes"));
  }

  @Test
  void addParameterLaneCreatesEchoesTheEngineSummaryAndUndoRemovesIt() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    int baseline = composition.lanes.size();

    Map<String, Object> payload = ok(new AddClipLane().handle(lx, Map.of(
        "kind", "parameter", "targetPath", "/lx/mixer/channel/1/fader")));

    assertEquals("/lx/timeline/composition", payload.get("clipPath"));
    assertEquals(false, payload.get("alreadyExisted"));
    assertEquals(baseline + 1, payload.get("laneCount"));
    Map<String, Object> lane = lane(payload);
    assertEquals("parameter", lane.get("type"));
    assertEquals("/lx/mixer/channel/1/fader", lane.get("parameterPath"));
    assertEquals(true, lane.get("removable"));

    // The echoed path is the address: it resolves to the lane the engine actually holds.
    ParameterClipLane created = composition.getParameterLane(channel.fader);
    assertNotNull(created);
    assertSame(created, Resolve.component(lx, (String) lane.get("path")));

    lx.command.undo();
    assertNull(composition.getParameterLane(channel.fader), "undo removes the added lane");
    assertEquals(baseline, composition.lanes.size());
  }

  @Test
  void addClipLaneIsIdempotentAndPushesNoCommandWhenTheLaneExists() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);
    LXComposition composition = composition(lx);

    Map<String, Object> first = ok(new AddClipLane().handle(lx, Map.of(
        "kind", "parameter", "targetPath", "/lx/mixer/channel/1/fader")));
    Object undoAfterFirst = lx.command.getUndoCommand();
    int laneCount = composition.lanes.size();

    // Upstream AddLane would be an isIgnored no-op here; the state-read detector must
    // answer with the existing lane instead of an internal "command did not apply".
    Map<String, Object> second = ok(new AddClipLane().handle(lx, Map.of(
        "kind", "parameter", "targetPath", "/lx/mixer/channel/1/fader")));

    assertEquals(true, second.get("alreadyExisted"));
    assertEquals(lane(first).get("path"), lane(second).get("path"));
    assertEquals(laneCount, composition.lanes.size());
    assertSame(undoAfterFirst, lx.command.getUndoCommand(),
        "the idempotent case must not touch the undo stack");
  }

  @Test
  void addPatternLaneCreatesWhenMissingAndReportsExistingOtherwise() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);

    // The composition auto-created the channel's pattern lane; clear it so the create
    // path is actually exercised (on the composition it is removable, unlike grid clips).
    PatternClipLane existing = composition.getPatternLane(channel.getPatternEngine(), false);
    assertNotNull(existing);
    ok(new RemoveClipLane().handle(lx, Map.of("path", ClipLanes.lanePath(existing))));
    assertNull(composition.getPatternLane(channel.getPatternEngine(), false));

    Map<String, Object> payload = ok(new AddClipLane().handle(lx, Map.of(
        "kind", "pattern", "targetPath", "/lx/mixer/channel/1")));
    assertEquals(false, payload.get("alreadyExisted"));
    assertEquals("pattern", lane(payload).get("type"));
    assertEquals("/lx/mixer/channel/1", lane(payload).get("channelPath"));
    assertNotNull(composition.getPatternLane(channel.getPatternEngine(), false));

    Map<String, Object> again = ok(new AddClipLane().handle(lx, Map.of(
        "kind", "pattern", "targetPath", "/lx/mixer/channel/1")));
    assertEquals(true, again.get("alreadyExisted"));

    // A grid channel clip's pattern lane is permanent — always the alreadyExisted case.
    channel.addClip(0);
    Map<String, Object> grid = ok(new AddClipLane().handle(lx, Map.of(
        "path", "/lx/mixer/channel/1/clip/1",
        "kind", "pattern", "targetPath", "/lx/mixer/channel/1")));
    assertEquals(true, grid.get("alreadyExisted"));
    assertEquals(false, lane(grid).get("removable"));
  }

  @Test
  void addClipLaneKindTargetMismatchIsTypedInvalidArgument() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);

    // kind 'parameter' pointed at a component
    Resolve.ResolveException parameterMismatch = assertThrows(Resolve.ResolveException.class,
        () -> new AddClipLane().handle(lx, Map.of(
            "kind", "parameter", "targetPath", "/lx/mixer/channel/1")));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, parameterMismatch.failure);

    // kind 'pattern' pointed at a parameter
    Resolve.ResolveException patternMismatch = assertThrows(Resolve.ResolveException.class,
        () -> new AddClipLane().handle(lx, Map.of(
            "kind", "pattern", "targetPath", "/lx/mixer/channel/1/fader")));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, patternMismatch.failure);

    // kind 'pattern' pointed at a component that is no pattern container
    Resolve.ResolveException notAContainer = assertThrows(Resolve.ResolveException.class,
        () -> new AddClipLane().handle(lx, Map.of(
            "kind", "pattern", "targetPath", "/lx/mixer/master")));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, notAContainer.failure);
    assertTrue(notAContainer.getMessage().contains("pattern container"));
  }

  @Test
  void removeClipLaneRejectsAutoManagedLanesBeforeTouchingTheEngine() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    int baseline = composition.lanes.size();

    // Composition bus lane: removing it corrupts the composition (risk 6).
    LXClipLane<?> busLane = composition.lanes.get(0);
    assertEquals("bus", ClipLanes.type(busLane));
    Resolve.ResolveException busRejected = assertThrows(Resolve.ResolveException.class,
        () -> new RemoveClipLane().handle(lx, Map.of("path", ClipLanes.lanePath(busLane))));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, busRejected.failure);
    assertTrue(busRejected.getMessage().contains("auto-managed"));
    assertEquals(baseline, composition.lanes.size(), "nothing was touched");
    assertSame(busLane, composition.lanes.get(0));

    // Grid clip: MIDI/pattern lanes are permanent there (upstream removeClipLane throws).
    LXClip clip = channel.addClip(0);
    LXClipLane<?> gridMidiLane = clip.lanes.stream()
        .filter(l -> "midiNote".equals(ClipLanes.type(l))).findFirst().orElseThrow();
    Resolve.ResolveException gridRejected = assertThrows(Resolve.ResolveException.class,
        () -> new RemoveClipLane().handle(lx, Map.of("path", ClipLanes.lanePath(gridMidiLane))));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, gridRejected.failure);
    assertTrue(clip.lanes.contains(gridMidiLane));
  }

  @Test
  void removeClipLaneEchoesResultingLanesAndUndoRestores() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    int baseline = composition.lanes.size();
    ParameterClipLane faderLane = addParameterLane(composition, channel.fader);

    Map<String, Object> payload = ok(new RemoveClipLane().handle(
        lx, Map.of("path", ClipLanes.lanePath(faderLane))));

    assertEquals("/lx/timeline/composition", payload.get("clipPath"));
    Map<?, ?> removed = assertInstanceOf(Map.class, payload.get("removed"));
    assertEquals("parameter", removed.get("type"));
    assertEquals(baseline, payload.get("laneCount"));
    // The echoed lane list is the resulting engine state: the fader lane is gone from it.
    assertTrue(lanes(payload).stream().noneMatch(
        l -> "/lx/mixer/channel/1/fader".equals(l.get("parameterPath"))));
    assertNull(composition.getParameterLane(channel.fader));

    lx.command.undo();
    assertNotNull(composition.getParameterLane(channel.fader), "undo restores the lane");
    assertEquals(baseline + 1, composition.lanes.size());
  }

  @Test
  void moveClipLaneEchoesTheActualEngineIndexNotTheRequest() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    ParameterClipLane faderLane = addParameterLane(composition, channel.fader);
    int fromIndex = faderLane.getIndex();

    // Requesting index 0 would put the parameter lane above its channel's bus lane;
    // LXComposition.validateMoveClipLaneIndex constrains it back into the section.
    Map<String, Object> payload = ok(new MoveClipLane().handle(
        lx, Map.of("path", ClipLanes.lanePath(faderLane), "index", 0)));

    assertEquals(0, payload.get("requestedIndex"));
    assertEquals(fromIndex, payload.get("fromIndex"));
    int actualIndex = composition.lanes.indexOf(faderLane);
    assertNotEquals(0, actualIndex, "the composition overrode the request");
    assertEquals(actualIndex, lane(payload).get("index"),
        "the payload echoes the index read back from the engine, never the request");
    assertEquals(true, payload.get("moved"));

    lx.command.undo();
    assertEquals(fromIndex, faderLane.getIndex(), "undo restores the original position");
  }

  @Test
  void moveClipLaneRejectsBusLanesAndOutOfRangeIndices() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    ParameterClipLane faderLane = addParameterLane(composition, channel.fader);

    // Upstream silently no-ops bus lane moves ("reorder via the mixer") — surface it.
    LXClipLane<?> busLane = composition.lanes.get(0);
    Resolve.ResolveException busRejected = assertThrows(Resolve.ResolveException.class,
        () -> new MoveClipLane().handle(lx, Map.of("path", ClipLanes.lanePath(busLane), "index", 2)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, busRejected.failure);
    assertTrue(busRejected.getMessage().contains("mixer"));

    Resolve.ResolveException outOfRange = assertThrows(Resolve.ResolveException.class,
        () -> new MoveClipLane().handle(lx, Map.of("path", ClipLanes.lanePath(faderLane), "index", 99)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, outOfRange.failure);
    assertTrue(outOfRange.getMessage().contains("out of range"));
  }

  @Test
  void setClipLaneVisibleIsADirectEditThatNeverTouchesUndoHistory() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    ParameterClipLane faderLane = addParameterLane(composition, channel.fader);
    Object undoBefore = lx.command.getUndoCommand();

    Map<String, Object> hidden = ok(new SetClipLaneVisible().handle(
        lx, Map.of("path", ClipLanes.lanePath(faderLane), "visible", false)));
    assertEquals(false, lane(hidden).get("uiVisible"));
    assertFalse(faderLane.uiVisible.isOn());
    assertSame(undoBefore, lx.command.getUndoCommand(),
        "uiVisible is not command-backed: nothing lands on the undo stack (risk 7)");

    Map<String, Object> shown = ok(new SetClipLaneVisible().handle(
        lx, Map.of("path", ClipLanes.lanePath(faderLane), "visible", true)));
    assertEquals(true, lane(shown).get("uiVisible"));
    assertTrue(faderLane.uiVisible.isOn());
  }

  @Test
  void removeModulatorCascadeInvalidatesAnEarlierLaneList() {
    // Risk 9: LXModulationEngine.removeModulator cascade-removes composition lanes
    // recorded against the modulator's parameters — so a held lane list (paths AND
    // indices) can go stale from a mutation that never mentioned lanes.
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    LXPattern pattern = channel.patterns.get(0);
    MacroKnobs knobs = (MacroKnobs) Modulators.addModulator(lx, pattern.modulation, MacroKnobs.class);
    ParameterClipLane macroLane = addParameterLane(composition, knobs.macro1);

    Map<String, Object> before = ok(new ListClipLanes().handle(lx, Map.of()));
    int beforeCount = (Integer) before.get("laneCount");
    String stalePath = ClipLanes.lanePath(macroLane);
    assertEquals("parameter", ClipLanes.type(macroLane));

    Modulators.removeModulator(lx, knobs);

    assertEquals(beforeCount - 1, composition.lanes.size(),
        "the modulator removal cascade-removed its composition lane");
    // The held path is positional: it now addresses a DIFFERENT lane (or nothing).
    LXClipLane<?> nowAtStalePath = (LXClipLane<?>)
        Resolve.component(lx, stalePath, LXClipLane.class);
    assertNotEquals("parameter", ClipLanes.type(nowAtStalePath),
        "the stale lane path silently retargeted after the cascade — re-list, don't reuse");
  }
}
