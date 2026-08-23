package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;
import chromatikmcp.domain.Resolve;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.mixer.LXChannel;

/** add_clip, remove_clip, capture_clip, launch_scene handlers (issue #230). */
class ClipLifecycleToolsTest extends CompositionTestSupport {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    return (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
  }

  @Test
  void addClipCreatesTheSlotAndEchoesTheClipState() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);

    Map<String, Object> payload = ok(new AddClip().handle(lx, Map.of(
        "containerPath", "/lx/mixer/channel/1",
        "index", 0,
        "label", "Chapter One")));

    assertEquals("/lx/mixer/channel/1", payload.get("containerPath"));
    assertEquals(0, payload.get("index"));
    // The 0-based arg addresses the 1-indexed path — echoed so the caller never guesses.
    assertEquals("/lx/mixer/channel/1/clip/1", payload.get("path"));
    assertEquals("Chapter One", payload.get("label"));
    assertEquals(true, payload.get("snapshotEnabled"));
    assertTrue((Integer) payload.get("snapshotViewCount") > 0);
    assertNotNull(channel.getClip(0));
  }

  @Test
  void addClipRefusesAnOccupiedSlotAndAcceptsReplace() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);
    Map<String, Object> args = Map.of("containerPath", "/lx/mixer/channel/1", "index", 0);
    ok(new AddClip().handle(lx, args));

    assertThrows(Resolve.ResolveException.class, () -> new AddClip().handle(lx, args));

    Map<String, Object> replaced = ok(new AddClip().handle(lx, Map.of(
        "containerPath", "/lx/mixer/channel/1", "index", 0, "replace", true)));
    assertEquals("/lx/mixer/channel/1/clip/1", replaced.get("path"));
  }

  @Test
  void addClipWithoutASnapshotStoresNothingUntilCaptureClipRuns() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);

    Map<String, Object> added = ok(new AddClip().handle(lx, Map.of(
        "containerPath", "/lx/mixer/channel/1", "index", 0, "snapshot", false)));
    assertEquals(false, added.get("snapshotEnabled"));
    assertEquals(0, added.get("snapshotViewCount"));

    Map<String, Object> captured = ok(new CaptureClip().handle(
        lx, Map.of("path", "/lx/mixer/channel/1/clip/1")));
    // Recall was off, so capture had to turn it on — reported, because it costs a
    // second Cmd-Z and silently changes launch behavior.
    assertEquals(true, captured.get("enabledRecall"));
    assertEquals(true, captured.get("snapshotEnabled"));
    assertTrue((Integer) captured.get("snapshotViewCount") > 0);
  }

  @Test
  void removeClipEmptiesTheSlotAndReportsWhatWentAway() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    ok(new AddClip().handle(lx, Map.of(
        "containerPath", "/lx/mixer/channel/1", "index", 0, "label", "Chapter One")));

    Map<String, Object> payload = ok(new RemoveClip().handle(
        lx, Map.of("path", "/lx/mixer/channel/1/clip/1")));

    // The shared remove_* contract: removed is the PATH and kind identifies the entity,
    // so a client dispatching generically across remove tools keeps working here.
    assertEquals("/lx/mixer/channel/1/clip/1", payload.get("removed"));
    assertEquals("clip", payload.get("kind"));
    assertEquals("/lx/mixer/channel/1", payload.get("containerPath"));
    assertEquals(0, payload.get("index"));
    assertEquals("Chapter One", payload.get("label"));
    assertNull(channel.getClip(0));
  }

  @Test
  void removeClipRejectsTheArrangeComposition() {
    LX lx = newHeadlessLx();
    assertThrows(Resolve.ResolveException.class, () -> new RemoveClip().handle(
        lx, Map.of("path", "/lx/timeline/composition")));
  }

  @Test
  void captureClipRejectsTheArrangeComposition() {
    LX lx = newHeadlessLx();
    assertThrows(Resolve.ResolveException.class, () -> new CaptureClip().handle(
        lx, Map.of("path", "/lx/timeline/composition")));
  }

  @Test
  void launchSceneFiresTheWholeRowAndReportsEveryClipOnIt() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);
    addChannelWithPattern(lx);
    ok(new AddClip().handle(lx, Map.of("containerPath", "/lx/mixer/channel/1", "index", 0)));
    ok(new AddClip().handle(lx, Map.of("containerPath", "/lx/mixer/channel/2", "index", 0)));

    Map<String, Object> payload = ok(new LaunchScene().handle(
        lx, Map.of("index", 0, "immediate", true)));

    assertEquals(0, payload.get("index"));
    assertEquals(2, payload.get("clipCount"));
    List<?> clips = assertInstanceOf(List.class, payload.get("clips"));
    for (Object entry : clips) {
      Map<?, ?> clip = assertInstanceOf(Map.class, entry);
      assertTrue(clip.containsKey("path") && clip.containsKey("label")
          && clip.containsKey("running") && clip.containsKey("pending"));
    }
    LXClip first = (LXClip) heronarts.lx.LXPath.get(lx, "/lx/mixer/channel/1/clip/1");
    assertTrue(first.isRunning() || first.isPending());
  }

  @Test
  void launchSceneRejectsAnEmptyRow() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);
    assertThrows(Resolve.ResolveException.class,
        () -> new LaunchScene().handle(lx, Map.of("index", 0)));
  }
}
