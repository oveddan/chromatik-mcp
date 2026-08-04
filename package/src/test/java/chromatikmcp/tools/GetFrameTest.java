package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.domain.Frames;

class GetFrameTest {

  @Test
  void summarySerializerPreservesTheEstablishedWireShape() {
    Frames.FrameSummary summary = new Frames.FrameSummary(
        2,
        0.5,
        0.25,
        0.75,
        List.of(new Frames.DominantColor("#abcdef", 1.0)),
        List.of(List.of("#010203")));

    assertEquals(Map.of(
        "points", 2,
        "nonBlackFraction", 0.5,
        "litFraction", 0.25,
        "meanBrightness", 0.75,
        "dominantColors", List.of(Map.of("hex", "#abcdef", "fraction", 1.0)),
        "grid", List.of(List.of("#010203"))),
        GetFrame.summaryToMap(summary));
  }
}
