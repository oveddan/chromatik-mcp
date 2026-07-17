package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

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
    Map<String, Object> summary = Frames.summarize(snap, Frames.View.FRONT, 3);

    assertEquals(64, summary.get("points"));
    assertEquals(1.0, summary.get("nonBlackFraction"));
    assertEquals(1.0, summary.get("meanBrightness"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> dominant = (List<Map<String, Object>>) summary.get("dominantColors");
    assertEquals(1, dominant.size(), "one hue: red");
    assertEquals("#ff0000", dominant.get(0).get("hex"));
    assertEquals(1.0, dominant.get(0).get("fraction"));

    @SuppressWarnings("unchecked")
    List<List<String>> grid = (List<List<String>>) summary.get("grid");
    assertEquals(3, grid.size());
    for (List<String> row : grid) {
      assertEquals(3, row.size());
      for (String cell : row) {
        assertEquals("#ff0000", cell, "an 8x8 grid populates every 3x3 cell");
      }
    }
  }

  @Test
  void summaryGridMarksEmptyCellsNull() {
    LX lx = newHeadlessLx();
    redChannel(lx);
    lx.engine.run();
    lx.engine.run();

    // The grid is planar (xn/yn); viewed from the TOP an 8x8 grid collapses to one zn row.
    Frames.FrameSnapshot snap = Frames.capture(lx, Frames.Bus.MAIN);
    Map<String, Object> summary = Frames.summarize(snap, Frames.View.TOP, 3);

    @SuppressWarnings("unchecked")
    List<List<String>> grid = (List<List<String>>) summary.get("grid");
    assertNull(grid.get(0).get(0), "no points at near depth");
    assertEquals("#ff0000", grid.get(1).get(0), "planar model normalizes zn to 0.5 (mid row)");
    assertNull(grid.get(2).get(0), "no points at far depth");
  }
}
