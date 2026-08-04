package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
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

    Map<String, Object> payload = GetFrame.summaryToMap(summary);

    assertEquals(Map.of(
        "points", 2,
        "nonBlackFraction", 0.5,
        "litFraction", 0.25,
        "meanBrightness", 0.75,
        "dominantColors", List.of(Map.of("hex", "#abcdef", "fraction", 1.0)),
        "grid", List.of(List.of("#010203"))),
        payload);
    assertEquals(List.of(
        "points", "nonBlackFraction", "litFraction", "meanBrightness", "dominantColors", "grid"),
        List.copyOf(payload.keySet()), "preserve the established JSON field order");
  }

  @Test
  void frameSummaryDefensivelyCopiesListsWhilePreservingNullGridCells() {
    List<Frames.DominantColor> colors = new ArrayList<>();
    colors.add(new Frames.DominantColor("#abcdef", 1.0));
    List<String> row = new ArrayList<>();
    row.add(null);
    List<List<String>> grid = new ArrayList<>();
    grid.add(row);

    Frames.FrameSummary summary =
        new Frames.FrameSummary(1, 1.0, 1.0, 1.0, colors, grid);
    colors.clear();
    row.set(0, "#ffffff");
    grid.clear();

    assertEquals(1, summary.dominantColors().size());
    assertNull(summary.grid().get(0).get(0));
    assertThrows(UnsupportedOperationException.class, summary.dominantColors()::clear);
    assertThrows(UnsupportedOperationException.class, summary.grid()::clear);
    assertThrows(UnsupportedOperationException.class,
        () -> summary.grid().get(0).set(0, "#ffffff"));
  }
}
