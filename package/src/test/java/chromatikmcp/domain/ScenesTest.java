package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.mixer.LXChannel;

/** Scene launch: a whole grid row fired at once (issue #230). */
class ScenesTest extends CompositionTestSupport {

  @Test
  void launchFiresEveryClipOnTheRowAcrossChannels() {
    LX lx = newHeadlessLx();
    LXChannel first = addChannelWithPattern(lx);
    LXChannel second = addChannelWithPattern(lx);
    LXClip a = Clips.addClip(lx, first, 0, false, false);
    LXClip b = Clips.addClip(lx, second, 0, false, false);
    // A clip one row down must stay untouched — the row is the unit, not the channel.
    LXClip other = Clips.addClip(lx, first, 1, false, false);

    Scenes.SceneState state = Scenes.launch(lx, 0, true);

    assertEquals(0, state.index());
    assertEquals(2, state.clips().size());
    assertTrue(a.isRunning() || a.isPending());
    assertTrue(b.isRunning() || b.isPending());
    assertEquals(false, other.isRunning());
    assertEquals(false, other.isPending());
  }

  @Test
  void launchRejectsAnEmptyRowInsteadOfSilentlyCancelling() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);

    // Upstream _launchClipSceneScheduled cancels the scene when nothing is on the row,
    // so the call looks like it worked and nothing happens.
    Resolve.ResolveException failure = assertThrows(Resolve.ResolveException.class,
        () -> Scenes.launch(lx, 0, false));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, failure.failure);
  }

  @Test
  void sceneIndexIsBoundedByTheEnginesNumScenes() {
    LX lx = newHeadlessLx();
    int numScenes = lx.engine.clips.numScenes.getValuei();

    assertThrows(Resolve.ResolveException.class, () -> Scenes.launch(lx, numScenes, false));
    assertThrows(Resolve.ResolveException.class, () -> Scenes.launch(lx, -1, false));
    assertThrows(Resolve.ResolveException.class, () -> Scenes.describe(lx, numScenes));
  }

  @Test
  void describeReportsTheRowWithoutLaunchingIt() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXClip clip = Clips.addClip(lx, channel, 3, false, false);
    clip.label.setValue("Chapter Four");

    Scenes.SceneState state = Scenes.describe(lx, 3);

    assertEquals(1, state.clips().size());
    Scenes.SceneClip entry = state.clips().get(0);
    assertEquals(Resolve.canonicalPath(clip), entry.path());
    assertEquals("Chapter Four", entry.label());
    assertEquals(false, entry.running());
    assertEquals(false, clip.isRunning());
  }
}
