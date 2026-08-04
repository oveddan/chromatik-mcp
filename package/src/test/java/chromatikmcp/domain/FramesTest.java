package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;
import heronarts.lx.pattern.color.SolidPattern;

class FramesTest extends HeadlessLxTest {

  @Override
  protected LXModel newModel() {
    // reindexPoints: LXPoint indices come from a JVM-global counter, and the immutable-
    // model LX constructor does not reindex (only LXStructure.setStaticModel does) — so a
    // second LX in the same JVM gets points indexed 64.., breaking per-point readback.
    return new GridModel(8, 8).reindexPoints();
  }

  private static LXChannel redChannel(LX lx) {
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.fader.setValue(1);
    channel.addPattern(new SolidPattern(lx)); // defaults to LXColor.RED
    return channel;
  }

  @Test
  void bufferIsBlackBeforeFirstEngineCycle() {
    LX lx = newHeadlessLx();
    redChannel(lx);

    // The double buffer only publishes at the end of an engine cycle: until run() has
    // ticked at least once, readback is all black. Tool description sets this expectation.
    Frames.FrameSnapshot snap = Frames.capture(lx, Frames.Bus.MAIN);
    for (int c : snap.colors()) {
      assertEquals(0, c & 0xFFFFFF, "no frame published yet");
    }
  }

  @Test
  void capturesSolidRedFrameAfterEngineCycle() {
    LX lx = newHeadlessLx();
    redChannel(lx);
    lx.engine.run();
    lx.engine.run();

    Frames.FrameSnapshot snap = Frames.capture(lx, Frames.Bus.MAIN);
    assertEquals(64, snap.size());
    assertEquals(64, snap.colors().length);
    for (int c : snap.colors()) {
      assertEquals(0xFF0000, c & 0xFFFFFF, "solid red on every point");
    }
  }

  @Test
  void cueBusIsBlackWhenUnused() {
    LX lx = newHeadlessLx();
    redChannel(lx);
    lx.engine.run();
    lx.engine.run();

    Frames.FrameSnapshot snap = Frames.capture(lx, Frames.Bus.CUE);
    assertEquals("cue", snap.bus());
    for (int c : snap.colors()) {
      assertEquals(0, c & 0xFFFFFF, "cue bus idle");
    }
  }

  @Test
  void summarizesSolidRedFrame() {
    LX lx = newHeadlessLx();
    redChannel(lx);
    lx.engine.run();
    lx.engine.run();

    Frames.FrameSnapshot snap = Frames.capture(lx, Frames.Bus.MAIN);
    Frames.FrameSummary summary =
        Frames.summarize(snap, Frames.View.FRONT, 3, Frames.LIT_THRESHOLD);

    assertEquals(64, summary.points());
    assertEquals(1.0, summary.nonBlackFraction());
    assertEquals(1.0, summary.litFraction());
    assertEquals(1.0, summary.meanBrightness());

    List<Frames.DominantColor> dominant = summary.dominantColors();
    assertEquals(1, dominant.size(), "one hue: red");
    assertEquals("#ff0000", dominant.get(0).hex());
    assertEquals(1.0, dominant.get(0).fraction());

    List<List<String>> grid = summary.grid();
    assertEquals(3, grid.size());
    for (List<String> row : grid) {
      assertEquals(3, row.size());
      for (String cell : row) {
        assertEquals("#ff0000", cell, "an 8x8 grid populates every 3x3 cell");
      }
    }
  }

  @Test
  void summarizeDistinguishesNearBlackFromLit() {
    // #101010 (max channel 16) is the issue's motivating case: a blur/residual tail dark
    // enough to read as "off" but with a nonzero channel, so nonBlackFraction still
    // counts it. Built directly (no engine run needed — FrameSnapshot is a public record).
    int size = 4;
    int[] colors = new int[size];
    Arrays.fill(colors, 0x101010);
    float[] xn = {0f, 0.33f, 0.66f, 1f};
    float[] yn = {0f, 0.33f, 0.66f, 1f};
    float[] zn = {0.5f, 0.5f, 0.5f, 0.5f};
    Frames.FrameSnapshot snap = new Frames.FrameSnapshot(colors, xn, yn, zn, size, 1f, 1f, 1f, "main");

    Frames.FrameSummary summary =
        Frames.summarize(snap, Frames.View.FRONT, 3, Frames.LIT_THRESHOLD);

    assertEquals(1.0, summary.nonBlackFraction(),
        "unchanged: any nonzero channel counts as non-black");
    assertEquals(0.0, summary.litFraction(), "near-black residual sits below the lit threshold");
  }

  @Test
  void litThresholdOverrideChangesLitFraction() {
    // Same #101010 fixture as summarizeDistinguishesNearBlackFromLit, but with a caller
    // threshold (8) below the pixel's max channel (16): now it counts as lit.
    int size = 4;
    int[] colors = new int[size];
    Arrays.fill(colors, 0x101010);
    float[] xn = {0f, 0.33f, 0.66f, 1f};
    float[] yn = {0f, 0.33f, 0.66f, 1f};
    float[] zn = {0.5f, 0.5f, 0.5f, 0.5f};
    Frames.FrameSnapshot snap = new Frames.FrameSnapshot(colors, xn, yn, zn, size, 1f, 1f, 1f, "main");

    Frames.FrameSummary defaultSummary =
        Frames.summarize(snap, Frames.View.FRONT, 3, Frames.LIT_THRESHOLD);
    assertEquals(0.0, defaultSummary.litFraction(), "default threshold: near-black stays unlit");

    Frames.FrameSummary lowThresholdSummary =
        Frames.summarize(snap, Frames.View.FRONT, 3, 8);
    assertEquals(1.0, lowThresholdSummary.litFraction(),
        "threshold below max channel: counts as lit");
  }

  @Test
  void summaryFractionsUseTotalPointCountAsDenominator() {
    // Mixed fixture where size, nonBlack, and lit are three different numbers, so a
    // fraction computed as x/nonBlack instead of x/size would produce a visibly wrong
    // result: 2 fully black, 2 near-black (#101010, max=16, non-black but unlit at the
    // default threshold), 1 bright (#ffffff, lit).
    int size = 5;
    int[] colors = {0x000000, 0x000000, 0x101010, 0x101010, 0xFFFFFF};
    float[] xn = {0f, 0.2f, 0.4f, 0.6f, 1f};
    float[] yn = {0f, 0.2f, 0.4f, 0.6f, 1f};
    float[] zn = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
    Frames.FrameSnapshot snap = new Frames.FrameSnapshot(colors, xn, yn, zn, size, 1f, 1f, 1f, "main");

    Frames.FrameSummary summary =
        Frames.summarize(snap, Frames.View.FRONT, 3, Frames.LIT_THRESHOLD);

    assertEquals(0.6, summary.nonBlackFraction(), "3 of 5 points have a nonzero channel");
    assertEquals(0.2, summary.litFraction(),
        "1 of 5 points (the bright one) clears the lit threshold");
    assertEquals(0.2251, summary.meanBrightness(), "mean of max/255 across all 5 points");
  }

  @Test
  void summaryGridMarksEmptyCellsNull() {
    LX lx = newHeadlessLx();
    redChannel(lx);
    lx.engine.run();
    lx.engine.run();

    // The grid is planar (xn/yn); viewed from the TOP an 8x8 grid collapses to one zn row.
    Frames.FrameSnapshot snap = Frames.capture(lx, Frames.Bus.MAIN);
    Frames.FrameSummary summary =
        Frames.summarize(snap, Frames.View.TOP, 3, Frames.LIT_THRESHOLD);

    List<List<String>> grid = summary.grid();
    assertNull(grid.get(0).get(0), "no points at near depth");
    assertEquals("#ff0000", grid.get(1).get(0), "planar model normalizes zn to 0.5 (mid row)");
    assertNull(grid.get(2).get(0), "no points at far depth");
  }
}
