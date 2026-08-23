package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.snapshot.LXSnapshot;

/** Grid clip lifecycle + snapshot capture primitives (issue #230). */
class ClipLifecycleTest extends CompositionTestSupport {

  @Test
  void addClipLandsAtTheRowAndTheCanonicalPathIsOneIndexed() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);

    LXClip clip = Clips.addClip(lx, channel, 0, true, false);

    assertEquals(0, clip.getIndex());
    // The arg is 0-based, the address is 1-based — the mapping agents most often get wrong.
    assertEquals("/lx/mixer/channel/1/clip/1", Resolve.canonicalPath(clip));
    assertTrue(clip.snapshotEnabled.isOn());
    assertEquals(clip, channel.getClip(0));
  }

  @Test
  void addClipIsUndoableAndRedoable() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);

    Clips.addClip(lx, channel, 2, false, false);
    assertNotNull(channel.getClip(2));

    lx.command.undo();
    assertNull(channel.getClip(2));

    lx.command.redo();
    assertNotNull(channel.getClip(2));
  }

  @Test
  void addClipRefusesAnOccupiedSlotUnlessReplaceIsAskedFor() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXClip first = Clips.addClip(lx, channel, 0, false, false);

    // Upstream Clip.Add silently removes whatever is there first; the guard is ours.
    Resolve.ResolveException failure = assertThrows(Resolve.ResolveException.class,
        () -> Clips.addClip(lx, channel, 0, false, false));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, failure.failure);
    assertEquals(first, channel.getClip(0), "the refused add must not have touched the slot");

    LXClip replacement = Clips.addClip(lx, channel, 0, false, true);
    assertEquals(replacement, channel.getClip(0));
    assertFalse(replacement == first);
  }

  @Test
  void undoingAReplaceRestoresTheClobberedClip() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    Clips.addClip(lx, channel, 0, false, false).label.setValue("Original");

    Clips.addClip(lx, channel, 0, false, true).label.setValue("Replacement");
    assertEquals("Replacement", channel.getClip(0).getLabel());

    // The one path that destroys user data — undo has to bring the old clip back, which
    // is what add_clip's "in a single undo step" promise rests on.
    lx.command.undo();
    assertEquals("Original", channel.getClip(0).getLabel());
  }

  @Test
  void addClipRefusesARowPastNumScenes() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    int numScenes = lx.engine.clips.numScenes.getValuei();

    // getClip() returns null past numScenes, so a clip added there is invisible to the
    // grid, to launch_scene, and to LXBus's own occupancy check.
    Resolve.ResolveException failure = assertThrows(Resolve.ResolveException.class,
        () -> Clips.addClip(lx, channel, numScenes, false, false));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, failure.failure);
    assertThrows(Resolve.ResolveException.class,
        () -> Clips.addClip(lx, channel, -1, false, false));
  }

  @Test
  void removeClipEmptiesTheSlotAndUndoRestoresIt() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXClip clip = Clips.addClip(lx, channel, 1, false, false);
    clip.label.setValue("Chapter One");

    Clips.removeClip(lx, clip);
    assertNull(channel.getClip(1));

    lx.command.undo();
    LXClip restored = channel.getClip(1);
    assertNotNull(restored);
    assertEquals("Chapter One", restored.getLabel());
  }

  @Test
  void removeClipRejectsTheArrangeComposition() {
    LX lx = newHeadlessLx();
    Resolve.ResolveException failure = assertThrows(Resolve.ResolveException.class,
        () -> Clips.removeClip(lx, composition(lx)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, failure.failure);
  }

  @Test
  void captureRejectsTheArrangeComposition() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);

    // It resolves as an LXClip and would not throw upstream — it would store zero views
    // while flipping snapshotEnabled on, i.e. report success for a no-op mutation.
    Resolve.ResolveException failure = assertThrows(Resolve.ResolveException.class,
        () -> Clips.captureSnapshot(lx, composition(lx)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, failure.failure);
    assertFalse(composition(lx).snapshotEnabled.isOn(),
        "the rejected capture must not have touched the composition");
  }

  @Test
  void removingOneSlotDoesNotReindexTheOthers() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    Clips.addClip(lx, channel, 0, false, false);
    LXClip middle = Clips.addClip(lx, channel, 1, false, false);
    LXClip last = Clips.addClip(lx, channel, 2, false, false);

    Clips.removeClip(lx, middle);

    // A grid row is an address, not a list position — the clip above must not slide down.
    assertNull(channel.getClip(1));
    assertEquals(last, channel.getClip(2));
    assertEquals(2, last.getIndex());
  }

  @Test
  void enablingRecallAtCreationAlreadyCapturesTheStateOfThatMoment() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);

    LXClip captured = Clips.addClip(lx, channel, 0, true, false);
    LXClip empty = Clips.addClip(lx, channel, 1, false, false);

    // LX initializes a clip snapshot the moment snapshotEnabled turns on, so add_clip
    // with snapshot:true is itself a capture — capture_clip is how you REcapture later.
    assertTrue(captured.snapshot.views.size() > 0);
    assertEquals(0, empty.snapshot.views.size());
  }

  @Test
  void captureOverwritesTheStoredValueAndUndoRestoresIt() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXClip clip = Clips.addClip(lx, channel, 0, true, false);

    CompoundParameter parameter = firstNormalizedParameter(clip);
    double before = storedNormalized(clip, parameter);
    double moved = (before > 0.5) ? 0.1 : 0.9;
    parameter.setNormalized(moved);

    Clips.captureSnapshot(lx, clip);
    assertEquals(moved, storedNormalized(clip, parameter), 1e-6,
        "capture must store the value as it is now, not as it was");

    lx.command.undo();
    assertEquals(before, storedNormalized(clip, parameter), 1e-6,
        "undo restores the pre-capture snapshot");
  }

  @Test
  void captureEnablesRecallWhenItIsOffSoTheSnapshotCanActuallyFire() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXClip clip = Clips.addClip(lx, channel, 0, false, false);
    assertFalse(clip.snapshotEnabled.isOn());
    assertEquals(0, clip.snapshot.views.size());

    boolean enabledRecall = Clips.captureSnapshot(lx, clip);

    assertTrue(enabledRecall);
    assertTrue(clip.snapshotEnabled.isOn(),
        "capturing into a clip that never recalls would be a silent no-op");
    assertTrue(clip.snapshot.views.size() > 0);

    // Two commands, so two undo steps — the contract capture_clip documents.
    lx.command.undo();
    lx.command.undo();
    assertFalse(clip.snapshotEnabled.isOn());
  }

  /**
   * The first continuous parameter a clip snapshot stored for the bus. Compound
   * specifically, not any normalized parameter: the view list also holds selectors and
   * booleans, whose setNormalized quantizes and would make the assertion meaningless.
   */
  private static CompoundParameter firstNormalizedParameter(LXClip clip) {
    for (LXSnapshot.View view : clip.snapshot.views) {
      if (view instanceof LXSnapshot.ParameterView parameterView
          && parameterView.parameter instanceof CompoundParameter compound) {
        return compound;
      }
    }
    throw new AssertionError("clip snapshot stored no compound parameter view");
  }

  /**
   * The value the snapshot currently holds for {@code parameter}, looked up by parameter
   * identity — update() rebuilds the view list, so a held View reference goes stale.
   */
  private static double storedNormalized(LXClip clip, LXParameter parameter) {
    for (LXSnapshot.View view : clip.snapshot.views) {
      if (view instanceof LXSnapshot.ParameterView parameterView
          && parameterView.parameter == parameter) {
        return parameterView.getParameterNormalizedValue();
      }
    }
    throw new AssertionError("clip snapshot no longer stores " + parameter.getLabel());
  }

  @Test
  void captureIsBusScopedAndDoesNotStoreTheChannelFader() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXClip clip = Clips.addClip(lx, channel, 0, true, false);

    Clips.captureSnapshot(lx, clip);

    // Pinned because capture_clip's description promises it: a clip snapshot stores the
    // owning bus's pattern/effect state, NOT the mixer state a global snapshot covers.
    // An agent authoring fader moves needs an automation lane or add_snapshot instead.
    for (LXSnapshot.View view : clip.snapshot.views) {
      if (view instanceof LXSnapshot.ParameterView parameterView) {
        assertFalse(parameterView.parameter == channel.fader,
            "the channel fader must not be in a clip snapshot");
      }
    }
  }
}
