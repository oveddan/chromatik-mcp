package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lxmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.LXPath;
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
}
