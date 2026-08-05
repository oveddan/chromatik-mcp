package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;

import heronarts.lx.LX;
import heronarts.lx.clip.AudioClipLane;
import heronarts.lx.clip.BusClipLane;
import heronarts.lx.clip.ColorPaletteClipLane;
import heronarts.lx.clip.GlobalModulationClipLane;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXClipLane;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.MidiNoteClipLane;
import heronarts.lx.clip.ParameterClipLane;
import heronarts.lx.clip.PatternClipLane;
import heronarts.lx.clip.TextNoteClipLane;
import heronarts.lx.mixer.LXChannel;

import com.google.gson.JsonObject;

/**
 * The Resolve timeline branch and the lane-aware canonical path — the contract every
 * composition tool's addressing depends on. The round-trip property test is the guard
 * for risk 1: without lane substitution, three of the six lane types 404 on the
 * over-long-path guard and BusClipLane emits a wrong (0-indexed) path.
 */
class CompositionResolveTest extends CompositionTestSupport {

  private static Resolve.Failure failureOf(Runnable call) {
    return assertThrows(Resolve.ResolveException.class, call::run).failure;
  }

  @Test
  void timelineEngineStillResolvesNatively() {
    LX lx = newHeadlessLx();
    assertSame(lx.engine.timeline, Resolve.component(lx, "/lx/timeline"));
    assertSame(lx.engine.timeline.sync, Resolve.parameter(lx, "/lx/timeline/sync"));
    assertSame(lx.engine.timeline.focusedClip, Resolve.parameter(lx, "/lx/timeline/focusedClip"));
  }

  @Test
  void timelineArmIsUnresolvableByDesign() {
    // arm is a bare public field with no addParameter (LXTimelineEngine.java:48-65). Do
    // not paper over it in Resolve: F7's set_composition_arm is the write path, and this
    // test failing is the loud signal that upstream registered the parameter — at which
    // point the tool surface should be revisited, not silently doubled.
    LX lx = newHeadlessLx();
    assertEquals(Resolve.Failure.NOT_FOUND,
        failureOf(() -> Resolve.parameter(lx, "/lx/timeline/arm")));
  }

  @Test
  void compositionResolvesThroughTheTimelineBranch() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    assertNotNull(composition);
    assertSame(composition, Resolve.component(lx, "/lx/timeline/composition"));
    assertEquals("/lx/timeline/composition", Resolve.canonicalPath(composition));
    // Registered parameters and aggregate subparameters come free via LXPath delegation.
    assertSame(composition.loop, Resolve.parameter(lx, "/lx/timeline/composition/loop"));
    assertSame(composition.length, Resolve.parameter(lx, "/lx/timeline/composition/length"));
    assertSame(composition.length.millis,
        Resolve.parameter(lx, "/lx/timeline/composition/length/millis"));
    assertSame(composition.insertMarker,
        Resolve.parameter(lx, "/lx/timeline/composition/insertMarker"));
  }

  @Test
  void bogusCompositionPathsAreNotFound() {
    LX lx = newHeadlessLx();
    assertEquals(Resolve.Failure.NOT_FOUND,
        failureOf(() -> Resolve.component(lx, "/lx/timeline/composition/nope")));
    assertEquals(Resolve.Failure.NOT_FOUND,
        failureOf(() -> Resolve.component(lx, "/lx/timeline/composition/lane/999")));
  }

  @Test
  void everyLaneTypeRoundTripsThroughItsCanonicalPath() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    // Cover the user-addable lane types too: parameter, text-note, audio (a null laneObj
    // creates an empty audio lane without file I/O).
    addParameterLane(composition, channel.fader);
    composition.addTextNoteLane();
    composition.addAudioLane((JsonObject) null);

    Set<Class<?>> covered = new HashSet<>();
    for (LXClipLane<?> lane : composition.lanes) {
      covered.add(lane.getClass());
      String path = ClipLanes.lanePath(lane);
      assertTrue(path.startsWith("/lx/timeline/composition/lane/"),
          "lane path uses the lane/<n> form: " + path);
      assertSame(lane, Resolve.component(lx, path), "round-trip failed for " + path
          + " (" + lane.getClass().getSimpleName() + ")");
    }
    // The property test is only meaningful if the fixture really exercised every type —
    // including the ones whose upstream getPath() is null or wrong.
    for (Class<?> type : new Class<?>[] {
        BusClipLane.class, GlobalModulationClipLane.class, ColorPaletteClipLane.class,
        MidiNoteClipLane.class, PatternClipLane.class, TextNoteClipLane.class,
        AudioClipLane.class }) {
      assertTrue(covered.stream().anyMatch(type::isAssignableFrom),
          "fixture is missing lane type " + type.getSimpleName());
    }
    assertTrue(covered.stream().anyMatch(ParameterClipLane.class::isAssignableFrom),
        "fixture is missing a ParameterClipLane");
  }

  @Test
  void canonicalPathOrNullSynthesizesLanePaths() {
    // ParameterClipLane has no getPath() at all — before the lane-aware helper,
    // canonicalPathOrNull returned null and every lane payload silently omitted its path.
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    ParameterClipLane lane = addParameterLane(composition(lx), channel.fader);
    assertEquals(Resolve.canonicalPath(lane), Resolve.canonicalPathOrNull(lane));
  }

  @Test
  void gridClipsResolveNativelyWithTheWholeLaneSurface() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXClip clip = channel.addClip(0);

    assertSame(clip, Resolve.component(lx, "/lx/mixer/channel/1/clip/1"));
    assertSame(clip.length.millis,
        Resolve.parameter(lx, "/lx/mixer/channel/1/clip/1/length/millis"));

    // A channel grid clip carries permanent MIDI + pattern lanes; their canonical paths
    // round-trip through the same lane-aware helper as composition lanes.
    assertTrue(clip.lanes.size() >= 2, "channel clip carries its permanent lanes");
    for (LXClipLane<?> lane : clip.lanes) {
      String path = ClipLanes.lanePath(lane);
      assertTrue(path.startsWith("/lx/mixer/channel/1/clip/1/lane/"), path);
      assertSame(lane, Resolve.component(lx, path));
    }
  }
}
