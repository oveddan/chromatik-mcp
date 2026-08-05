package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.Locator;

/**
 * F2 locator primitives: 1-indexed positional addressing, do→undo→assert for the three
 * LXCommand-backed mutations, and the go jump's two branches (running relaunch vs
 * stopped insert-marker move, the latter clamped by the composition length).
 */
class CompositionLocatorsTest extends CompositionTestSupport {

  @Test
  void listLocatorsIsInTimelineOrderWithResolvablePositionalPaths() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    // Added out of order: the engine sorts by cursor on every add.
    composition.addLocator(composition.constructAbsoluteCursor(5_000));
    composition.addLocator(composition.constructAbsoluteCursor(2_000));

    Compositions.LocatorList payload = Compositions.listLocators(lx);
    assertEquals("/lx/timeline/composition", payload.path());
    assertEquals(2, payload.locatorCount());
    List<Compositions.LocatorSummary> locators = payload.locators();
    assertEquals(2_000.0, locators.get(0).cursor().millis());
    assertEquals(5_000.0, locators.get(1).cursor().millis());
    for (int i = 0; i < locators.size(); ++i) {
      assertEquals(i + 1, locators.get(i).index(), "locator index is 1-based");
      String path = locators.get(i).path();
      assertEquals("/lx/timeline/composition/locator/" + (i + 1), path);
      assertSame(composition.locators.get(i), Resolve.component(lx, path));
    }
  }

  @Test
  void summaryIndexSurvivesTheUpstreamStaleReindexAfterRemove() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    composition.addLocator(composition.constructAbsoluteCursor(1_000));
    composition.addLocator(composition.constructAbsoluteCursor(2_000));
    Locator last = composition.addLocator(composition.constructAbsoluteCursor(3_000));

    // Upstream removeLocator skips the re-index pass, so last.getIndex() stays stale at
    // 2 — the summary must report list position (2nd, 1-indexed) and a path that
    // resolves, not the stale locator/3.
    composition.removeLocator(composition.locators.get(1));
    Compositions.LocatorSummary summary = Compositions.locatorSummary(composition, last);
    assertEquals(2, summary.index());
    assertSame(last, Resolve.component(lx, summary.path()));
  }

  @Test
  void resolveLocatorByIndexAndByUnambiguousLabel() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    Locator verse = composition.addLocator(composition.constructAbsoluteCursor(1_000));
    verse.label.setValue("Verse");
    Locator chorus = composition.addLocator(composition.constructAbsoluteCursor(4_000));
    chorus.label.setValue("Chorus");

    assertSame(verse, Compositions.resolveLocator(composition, 1, null));
    assertSame(chorus, Compositions.resolveLocator(composition, null, "Chorus"));

    assertEquals(Resolve.Failure.NOT_FOUND, assertThrows(Resolve.ResolveException.class,
        () -> Compositions.resolveLocator(composition, 3, null)).failure);
    assertEquals(Resolve.Failure.NOT_FOUND, assertThrows(Resolve.ResolveException.class,
        () -> Compositions.resolveLocator(composition, null, "Bridge")).failure);
    // Exactly-one-of: both and neither are argument errors, not lookups.
    assertEquals(Resolve.Failure.TYPE_MISMATCH, assertThrows(Resolve.ResolveException.class,
        () -> Compositions.resolveLocator(composition, 1, "Verse")).failure);
    assertEquals(Resolve.Failure.TYPE_MISMATCH, assertThrows(Resolve.ResolveException.class,
        () -> Compositions.resolveLocator(composition, null, null)).failure);

    chorus.label.setValue("Verse");
    assertEquals(Resolve.Failure.TYPE_MISMATCH, assertThrows(Resolve.ResolveException.class,
        () -> Compositions.resolveLocator(composition, null, "Verse")).failure);
  }

  @Test
  void addLocatorSortsLabelsAndUndoes() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    composition.addLocator(composition.constructAbsoluteCursor(5_000));

    Locator added = Compositions.addLocator(
        lx, composition.constructAbsoluteCursor(2_000), "Intro");
    assertEquals(2, composition.locators.size());
    // Sorted ahead of the pre-existing 5s locator; 1-indexed summary echoes that.
    assertEquals(1, Compositions.locatorSummary(composition, added).index());
    assertEquals("Intro", added.getLabel());
    assertCursorEqual(composition, composition.constructAbsoluteCursor(2_000),
        added.position.cursor);

    lx.command.undo();
    assertEquals(1, composition.locators.size());
    assertFalse(composition.locators.contains(added));
  }

  @Test
  void removeLocatorUndoRestoresPositionAndLabel() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    Locator locator = Compositions.addLocator(
        lx, composition.constructAbsoluteCursor(3_000), "Drop");

    Compositions.removeLocator(lx, locator);
    assertEquals(0, composition.locators.size());

    // RemoveLocator undo reloads from serialized JSON: label and position both survive.
    lx.command.undo();
    assertEquals(1, composition.locators.size());
    Locator restored = composition.locators.get(0);
    assertEquals("Drop", restored.getLabel());
    assertCursorEqual(composition, composition.constructAbsoluteCursor(3_000),
        restored.position.cursor);
  }

  @Test
  void moveLocatorResortsAndUndoes() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    Locator a = Compositions.addLocator(lx, composition.constructAbsoluteCursor(2_000), "A");
    Compositions.addLocator(lx, composition.constructAbsoluteCursor(5_000), "B");
    Cursor before = a.position.cursor.immutable();

    Compositions.moveLocator(lx, a, composition.constructAbsoluteCursor(8_000));
    assertCursorEqual(composition, composition.constructAbsoluteCursor(8_000),
        a.position.cursor);
    assertEquals(2, Compositions.locatorSummary(composition, a).index(),
        "move re-sorts: A passed B");

    lx.command.undo();
    assertCursorEqual(composition, before, a.position.cursor);
    assertEquals(1, Compositions.locatorSummary(composition, a).index());
  }

  @Test
  void goLocatorStoppedMovesTheInsertMarkerBoundedByLength() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    Locator mid = composition.addLocator(composition.constructAbsoluteCursor(5_000));
    Locator past = composition.addLocator(composition.constructAbsoluteCursor(15_000));

    assertFalse(Compositions.goLocator(lx, mid));
    assertFalse(composition.isRunning(), "stopped jump must not start playback");
    assertCursorEqual(composition, composition.constructAbsoluteCursor(5_000),
        composition.insertMarker.cursor);

    // A locator past the end is legal, but setInsertMarker bounds to length — the echo
    // read back from the marker is the truth, not the locator position.
    assertFalse(Compositions.goLocator(lx, past));
    assertCursorEqual(composition, composition.length.cursor, composition.insertMarker.cursor);
    assertFalse(composition.CursorOp().isEqual(
        past.position.cursor, composition.insertMarker.cursor));
    // The bound is non-destructive: the locator itself did not move.
    assertCursorEqual(composition, composition.constructAbsoluteCursor(15_000),
        past.position.cursor);
  }

  @Test
  void goLocatorRunningRelaunchesFromTheLocator() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    Locator mid = composition.addLocator(composition.constructAbsoluteCursor(5_000));

    composition.playFrom(composition.constructAbsoluteCursor(0));
    assertTrue(composition.isRunning());

    assertTrue(Compositions.goLocator(lx, mid));
    assertTrue(composition.isRunning());
    // launchAutomationFrom stages the (bounded) launch cursor synchronously; the launch
    // itself is subject to global quantization, so the staged cursor is the assertable
    // headless state.
    assertCursorEqual(composition, mid.position.cursor, composition.launchFromCursor);
    composition.stop();
  }

  @Test
  void locatorSummaryHasTheFullCursorObject() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    Locator locator = composition.addLocator(composition.constructTempoCursor(8, 0));
    Compositions.LocatorSummary summary = Compositions.locatorSummary(composition, locator);
    Cursors.CursorInfo cursor = summary.cursor();
    assertEquals(8, cursor.beatCount());
    assertEquals(0.0, cursor.beatBasis());
    assertNotNull(cursor.formatted());
  }
}
