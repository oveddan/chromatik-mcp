package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.LXPath;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXSwatch;

class PalettesTest extends HeadlessLxTest {

  @Test
  void activeSwatchSnapshotMatchesLiveStateAndPathsRoundTrip() {
    LX lx = newHeadlessLx();

    Palettes.PaletteInfo info = Palettes.info(lx);
    assertEquals(lx.engine.palette.swatch.getCanonicalPath(), info.activeSwatch().path());
    assertSame(lx.engine.palette.swatch, LXPath.get(lx, info.activeSwatch().path()));

    assertEquals(lx.engine.palette.swatch.colors.size(), info.activeSwatch().colors().size());
    for (int i = 0; i < info.activeSwatch().colors().size(); i++) {
      var liveColor = lx.engine.palette.swatch.colors.get(i);
      var colorInfo = info.activeSwatch().colors().get(i);
      assertEquals(liveColor.getCanonicalPath(), colorInfo.path());
      assertSame(liveColor, LXPath.get(lx, colorInfo.path()));
      assertEquals(liveColor.mode.getEnum().label, colorInfo.mode());
      assertEquals(String.format("0x%08x", liveColor.getColor()), colorInfo.effectiveColor());
      assertEquals(liveColor.primary.getCanonicalPath(), colorInfo.primaryPath());
      assertSame(liveColor.primary, LXPath.get(lx, colorInfo.primaryPath()));
      assertEquals(liveColor.secondary.getCanonicalPath(), colorInfo.secondaryPath());
      assertSame(liveColor.secondary, LXPath.get(lx, colorInfo.secondaryPath()));
    }
  }

  @Test
  void savedSwatchesSnapshotMatchesLiveStateAndPathsRoundTrip() {
    LX lx = newHeadlessLx();

    LXSwatch cool = lx.engine.palette.saveSwatch();
    cool.label.setValue("Cool");
    LXSwatch warm = lx.engine.palette.saveSwatch();
    warm.label.setValue("Warm");
    warm.autoCycleEligible.setValue(false);

    Palettes.PaletteInfo info = Palettes.info(lx);
    assertEquals(2, info.swatches().size());

    Palettes.SwatchInfo coolInfo = info.swatches().get(0);
    assertEquals(cool.getCanonicalPath(), coolInfo.path());
    assertSame(cool, LXPath.get(lx, coolInfo.path()));
    assertEquals("Cool", coolInfo.label());
    assertEquals(cool.recall.getCanonicalPath(), coolInfo.recallPath());
    assertSame(cool.recall, LXPath.get(lx, coolInfo.recallPath()));
    assertTrue(coolInfo.autoCycleEligible());

    Palettes.SwatchInfo warmInfo = info.swatches().get(1);
    assertEquals("Warm", warmInfo.label());
    assertEquals(warm.getCanonicalPath(), warmInfo.path());
    assertEquals(false, warmInfo.autoCycleEligible());
  }

  @Test
  void transitionAndAutoCycleSnapshotMatchesLiveState() {
    LX lx = newHeadlessLx();
    lx.engine.palette.transitionEnabled.setValue(true);
    lx.engine.palette.transitionTimeSecs.setValue(2.5);
    lx.engine.palette.autoCycleEnabled.setValue(true);
    lx.engine.palette.autoCycleTimeSecs.setValue(30);

    Palettes.PaletteInfo info = Palettes.info(lx);

    assertTrue(info.transition().enabled());
    assertEquals(lx.engine.palette.transitionEnabled.getCanonicalPath(), info.transition().enabledPath());
    assertEquals(2.5, info.transition().timeSecs(), 1e-9);
    assertEquals(lx.engine.palette.transitionTimeSecs.getCanonicalPath(), info.transition().timeSecsPath());
    assertEquals(0.0, info.transition().transitionProgress(), 1e-9);

    assertTrue(info.autoCycle().enabled());
    assertEquals(30, info.autoCycle().timeSecs(), 1e-9);
    assertEquals(lx.engine.palette.autoCycleCursor.getValuei(), info.autoCycle().cursor());
  }

  @Test
  void recallLoadsSavedColorsOntoActiveSwatchWithTransitionsDisabled() {
    LX lx = newHeadlessLx();
    // Transitions default to disabled: recall applies immediately (no interpolation loop
    // to run in a headless test).
    assertEquals(false, lx.engine.palette.transitionEnabled.isOn());

    LXSwatch cool = lx.engine.palette.saveSwatch();
    cool.colors.get(0).primary.setColor(0xff0000ff);

    int before = lx.engine.palette.swatch.colors.get(0).getColor();
    assertNotNull(before);

    cool.recall.trigger();

    Palettes.PaletteInfo info = Palettes.info(lx);
    assertEquals(String.format("0x%08x", 0xff0000ff), info.activeSwatch().colors().get(0).effectiveColor());
    assertEquals(0xff0000ff, lx.engine.palette.swatch.colors.get(0).getColor());
  }

  // ---- swatch mutations: do -> undo -> assert restored ----

  @Test
  void saveSwatchAppendsASwatchAndUndoRemovesIt() {
    LX lx = newHeadlessLx();
    int before = lx.engine.palette.swatches.size();

    LXSwatch saved = Palettes.saveSwatch(lx);

    assertEquals(before + 1, lx.engine.palette.swatches.size());
    assertSame(saved, lx.engine.palette.swatches.get(before));

    lx.command.undo();

    assertEquals(before, lx.engine.palette.swatches.size(), "undo removes the saved swatch");
    assertFalse(lx.engine.palette.swatches.contains(saved));
  }

  @Test
  void setSwatchAppliesSavedColorsAndUndoRestoresThePreviousActiveColors() {
    LX lx = newHeadlessLx();
    assertEquals(false, lx.engine.palette.transitionEnabled.isOn());

    LXSwatch saved = lx.engine.palette.saveSwatch();
    saved.colors.get(0).primary.setColor(0xff00ff00);
    int original = lx.engine.palette.swatch.colors.get(0).getColor();

    Palettes.setSwatch(lx, saved);

    assertEquals(0xff00ff00, lx.engine.palette.swatch.colors.get(0).getColor());

    lx.command.undo();

    assertEquals(original, lx.engine.palette.swatch.colors.get(0).getColor(),
        "undo restores the active swatch's prior colors");
  }

  @Test
  void removeSwatchDeletesItAndUndoRestoresIt() {
    LX lx = newHeadlessLx();
    LXSwatch saved = lx.engine.palette.saveSwatch();
    int before = lx.engine.palette.swatches.size();

    Palettes.removeSwatch(lx, saved);

    assertEquals(before - 1, lx.engine.palette.swatches.size());
    assertFalse(lx.engine.palette.swatches.contains(saved));

    lx.command.undo();

    assertEquals(before, lx.engine.palette.swatches.size(), "undo restores the removed swatch");
  }

  @Test
  void moveSwatchReindexesAndUndoRestoresTheOriginalOrder() {
    LX lx = newHeadlessLx();
    LXSwatch first = lx.engine.palette.saveSwatch();
    LXSwatch second = lx.engine.palette.saveSwatch();
    assertEquals(0, first.getIndex());
    assertEquals(1, second.getIndex());

    Palettes.moveSwatch(lx, first, 1);

    assertEquals(1, first.getIndex());
    assertEquals(0, second.getIndex());

    lx.command.undo();

    assertEquals(0, first.getIndex(), "undo restores the original order");
    assertEquals(1, second.getIndex());
  }

  @Test
  void addColorAppendsAColorAndUndoRemovesIt() {
    LX lx = newHeadlessLx();
    LXSwatch swatch = lx.engine.palette.swatch;
    int before = swatch.colors.size();

    LXDynamicColor added = Palettes.addColor(lx, swatch);

    assertEquals(before + 1, swatch.colors.size());
    assertSame(added, swatch.colors.get(before));

    lx.command.undo();

    assertEquals(before, swatch.colors.size(), "undo removes the added color");
  }

  @Test
  void removeColorRemovesTheLastColorAndUndoRestoresIt() {
    LX lx = newHeadlessLx();
    LXSwatch swatch = lx.engine.palette.swatch;
    Palettes.addColor(lx, swatch);
    int before = swatch.colors.size();
    String lastPath = swatch.colors.get(before - 1).getCanonicalPath();

    String removedPath = Palettes.removeColor(lx, swatch);

    assertEquals(lastPath, removedPath);
    assertEquals(before - 1, swatch.colors.size());

    lx.command.undo();

    assertEquals(before, swatch.colors.size(), "undo restores the removed color");
  }

  @Test
  void removeColorRejectsASwatchWithOnlyOneColor() {
    LX lx = newHeadlessLx();
    LXSwatch swatch = lx.engine.palette.swatch;
    assertEquals(1, swatch.colors.size());

    assertThrows(Resolve.ResolveException.class, () -> Palettes.removeColor(lx, swatch));
    assertEquals(1, swatch.colors.size(), "the guard rejects before performing any command");
  }
}
