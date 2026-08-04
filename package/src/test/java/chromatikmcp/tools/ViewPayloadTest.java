package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.domain.Views;

class ViewPayloadTest {

  @Test
  void serializesEveryViewInfoField() {
    Views.ViewInfo view = new Views.ViewInfo(
        "/lx/structure/views/view/1", "Cubes", "cube", true, false,
        "relative", "global", 2, 4, "/lx/structure/views/view/1/cueActive");

    assertEquals(Map.of(
        "path", view.path(),
        "label", view.label(),
        "selector", view.selector(),
        "enabled", view.enabled(),
        "priority", view.priority(),
        "normalization", view.normalization(),
        "orientation", view.orientation(),
        "numGroups", view.numGroups(),
        "numFixtures", view.numFixtures(),
        "cuePath", view.cuePath()), ViewPayload.toMap(view));
  }
}
