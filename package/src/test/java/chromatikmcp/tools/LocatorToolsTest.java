package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

import chromatikmcp.CompositionTestSupport;
import chromatikmcp.domain.Resolve;

import heronarts.lx.LX;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.Locator;

/**
 * Handler-level coverage of the five locator tools: payload shapes, the 1-indexed
 * addressing contract, the exactly-one-of index/label guard, and the clamp-echo on the
 * go jump. Registry-level coverage (Tools.allTools) arrives at integration.
 */
class LocatorToolsTest extends CompositionTestSupport {

  private static final Gson GSON = new Gson();

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(String name, Result<Map<String, Object>> result) {
    Map<String, Object> payload =
        (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
    // Real wire-shaped payloads for the tool manifest / docs examples.
    System.out.println("PAYLOAD " + name + ": " + GSON.toJson(payload));
    return payload;
  }

  @Test
  void addListMoveRemoveRoundTrip() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);

    // Add out of order with the bars sugar and a label: Chorus at bar 3, Intro at bar 1.
    Map<String, Object> chorus = ok("add_locator", new AddLocator().handle(lx,
        Map.of("cursor", Map.of("bars", 3), "label", "Chorus")));
    assertEquals(1, chorus.get("index"));
    assertEquals("Chorus", chorus.get("label"));
    assertEquals(1, chorus.get("locatorCount"));
    assertEquals(8, ((Map<?, ?>) chorus.get("cursor")).get("beatCount"));

    Map<String, Object> intro = ok("add_locator2", new AddLocator().handle(lx,
        Map.of("cursor", Map.of("bars", 1), "label", "Intro")));
    assertEquals(1, intro.get("index"), "sorted ahead of Chorus");
    assertEquals(2, intro.get("locatorCount"));

    Map<String, Object> list = ok("list_locators", new ListLocators().handle(lx, Map.of()));
    assertEquals(2, list.get("locatorCount"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> locators = (List<Map<String, Object>>) list.get("locators");
    assertEquals("Intro", locators.get(0).get("label"));
    assertEquals("Chorus", locators.get(1).get("label"));
    assertEquals("/lx/timeline/composition/locator/2", locators.get(1).get("path"));

    // Move Intro past Chorus by label: the echoed index is the NEW sorted position.
    Map<String, Object> moved = ok("move_locator", new MoveLocator().handle(lx,
        Map.of("label", "Intro", "cursor", Map.of("at", "end"))));
    assertEquals(2, moved.get("index"));
    assertEquals(10_000.0, ((Map<?, ?>) moved.get("cursor")).get("millis"));

    Map<String, Object> removed = ok("remove_locator", new RemoveLocator().handle(lx,
        Map.of("index", 1)));
    assertEquals("Chorus", ((Map<?, ?>) removed.get("removed")).get("label"));
    assertEquals(1, removed.get("locatorCount"));
    assertEquals(1, composition.locators.size());
  }

  @Test
  void addressingErrorsAreTyped() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    Locator a = composition.addLocator(composition.constructAbsoluteCursor(1_000));
    a.label.setValue("Dup");
    composition.addLocator(composition.constructAbsoluteCursor(2_000)).label.setValue("Dup");

    assertEquals(Resolve.Failure.TYPE_MISMATCH, assertThrows(Resolve.ResolveException.class,
        () -> new GoLocator().handle(lx, Map.of())).failure);
    assertEquals(Resolve.Failure.TYPE_MISMATCH, assertThrows(Resolve.ResolveException.class,
        () -> new RemoveLocator().handle(lx, Map.of("index", 1, "label", "Dup"))).failure);
    assertEquals(Resolve.Failure.TYPE_MISMATCH, assertThrows(Resolve.ResolveException.class,
        () -> new MoveLocator().handle(lx,
            Map.of("label", "Dup", "cursor", Map.of("millis", 0)))).failure);
    assertEquals(Resolve.Failure.NOT_FOUND, assertThrows(Resolve.ResolveException.class,
        () -> new GoLocator().handle(lx, Map.of("index", 9))).failure);
  }

  @Test
  void goLocatorStoppedEchoesTheBoundedInsertMarker() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    composition.addLocator(composition.constructAbsoluteCursor(15_000)).label.setValue("Outro");

    Map<String, Object> payload = ok("go_locator", new GoLocator().handle(lx,
        Map.of("label", "Outro")));
    assertEquals(false, payload.get("launched"));
    assertEquals(false, payload.get("running"));
    // The locator sits past the end; the echoed marker is the CLAMPED engine read-back.
    assertEquals(15_000.0,
        ((Map<?, ?>) ((Map<?, ?>) payload.get("locator")).get("cursor")).get("millis"));
    assertEquals(10_000.0, ((Map<?, ?>) payload.get("insertMarker")).get("millis"));
  }

  @Test
  void goLocatorRunningRelaunches() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    composition.addLocator(composition.constructAbsoluteCursor(5_000)).label.setValue("Drop");
    composition.playFrom(composition.constructAbsoluteCursor(0));

    Map<String, Object> payload = ok("go_locator_running", new GoLocator().handle(lx,
        Map.of("label", "Drop")));
    assertEquals(true, payload.get("launched"));
    assertEquals(true, payload.get("running"));
    assertCursorEqual(composition, composition.constructAbsoluteCursor(5_000),
        composition.launchFromCursor);
    composition.stop();
  }
}
