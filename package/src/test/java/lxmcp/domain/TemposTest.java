package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lxmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.LXPath;

class TemposTest extends HeadlessLxTest {

  @Test
  void snapshotMatchesLiveStateAndPathsRoundTrip() {
    LX lx = newHeadlessLx();

    Tempos.TempoInfo info = Tempos.info(lx);

    assertEquals(lx.engine.tempo.bpm.getCanonicalPath(), info.bpm().path());
    assertEquals(lx.engine.tempo.bpm.getValue(), ((Number) info.bpm().value()).doubleValue(), 1e-9);
    assertSame(lx.engine.tempo.bpm, LXPath.get(lx, info.bpm().path()));

    assertEquals(lx.engine.tempo.clockSource.getCanonicalPath(), info.clockSource().path());
    assertEquals(lx.engine.tempo.clockSource.getValuei(),
        ((Number) info.clockSource().value()).intValue());

    assertEquals(lx.engine.tempo.beatsPerBar.getCanonicalPath(), info.beatsPerBar().path());
    assertEquals(lx.engine.tempo.beatsPerBar.getValuei(),
        ((Number) info.beatsPerBar().value()).intValue());

    assertEquals(lx.engine.tempo.enabled.getCanonicalPath(), info.enabled().path());
    assertEquals(lx.engine.tempo.enabled.isOn(), info.enabled().value());

    assertEquals(lx.engine.tempo.launchQuantization.getCanonicalPath(), info.launchQuantization().path());
    assertTrue(info.launchQuantization().options().size() > 1, "quantization options: "
        + info.launchQuantization().options());

    assertEquals(lx.engine.tempo.tap.getCanonicalPath(), info.tapPath());
    assertSame(lx.engine.tempo.tap, LXPath.get(lx, info.tapPath()));
    assertEquals(lx.engine.tempo.nudgeUp.getCanonicalPath(), info.nudgeUpPath());
    assertEquals(lx.engine.tempo.nudgeDown.getCanonicalPath(), info.nudgeDownPath());
    assertEquals(lx.engine.tempo.getTriggerSource().getCanonicalPath(), info.triggerSourcePath());
    assertSame(lx.engine.tempo.getTriggerSource(), LXPath.get(lx, info.triggerSourcePath()));

    assertEquals(lx.engine.tempo.beatCount(), info.beatCount());
    assertEquals(lx.engine.tempo.barCount(), info.barCount());
    assertEquals(lx.engine.tempo.beatCountWithinBar(), info.beatCountWithinBar());
    assertEquals(lx.engine.tempo.basis(), info.basis(), 1e-9);
    assertEquals(lx.engine.tempo.period.getValue(), info.periodMs(), 1e-9);
  }

  @Test
  void snapshotTracksBpmChanges() {
    LX lx = newHeadlessLx();
    lx.engine.tempo.bpm.setValue(140);
    Tempos.TempoInfo info = Tempos.info(lx);
    assertEquals(140.0, ((Number) info.bpm().value()).doubleValue(), 1e-9);
  }
}
