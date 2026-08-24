package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;
import chromatikmcp.domain.Modulators;
import chromatikmcp.domain.Resolve;

import heronarts.lx.LX;
import heronarts.lx.modulator.MultiStageEnvelope;

/**
 * list_stages / add_stage / remove_stage / set_stage, handler-level. Pins: the {path,
 * index} positional addressing (stages have no canonical path), the endpoint-removal
 * guard surfacing as invalid_argument before the engine is touched, the read-back-after-
 * clamp echo on set_stage, and that no LXCommand backs any of these (undo stack
 * untouched).
 */
class StageToolsTest extends HeadlessLxTest {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    return (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> stage(Map<String, Object> payload) {
    return (Map<String, Object>) assertInstanceOf(Map.class, payload.get("stage"));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> stages(Map<String, Object> payload) {
    return (List<Map<String, Object>>) assertInstanceOf(List.class, payload.get("stages"));
  }

  private MultiStageEnvelope newEnvelope(LX lx) {
    return (MultiStageEnvelope)
        Modulators.addModulator(lx, lx.engine.modulation, MultiStageEnvelope.class);
  }

  @Test
  void listStagesReturnsTheDefaultRamp() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    Map<String, Object> payload =
        ok(new ListStages().handle(lx, Map.of("path", envelope.getCanonicalPath())));

    assertEquals(envelope.getCanonicalPath(), payload.get("path"));
    assertEquals(2, payload.get("stageCount"));
    List<Map<String, Object>> stages = stages(payload);
    assertEquals(2, stages.size());
    assertEquals(true, stages.get(0).get("initial"));
    assertEquals(true, stages.get(1).get("last"));
  }

  @Test
  void addStageInsertsAtTheReadBackIndexDoesNotTouchUndoHistoryAndMarksDirty() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    Object undoBefore = lx.command.getUndoCommand();
    lx.command.setDirty(false);

    Map<String, Object> payload = ok(new AddStage().handle(lx, Map.of(
        "path", envelope.getCanonicalPath(), "basis", 0.5, "value", 0.75, "shape", 2.0)));

    Map<String, Object> added = stage(payload);
    assertEquals(1, added.get("index"));
    assertEquals(0.5, (double) added.get("basis"), 1e-9);
    assertEquals(0.75, (double) added.get("value"), 1e-9);
    assertEquals(2.0, (double) added.get("shape"), 1e-9);
    assertEquals(3, payload.get("stageCount"));
    assertEquals(3, envelope.stages.size());
    assertEquals(undoBefore, lx.command.getUndoCommand(),
        "direct engine edit — nothing lands on the undo stack");
    assertTrue(lx.command.isDirty(),
        "stages are serialized, so a direct edit must mark the project dirty itself");
  }

  @Test
  void addStageAcceptsAZeroShapeStep() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    Map<String, Object> payload = ok(new AddStage().handle(lx, Map.of(
        "path", envelope.getCanonicalPath(), "basis", 0.5, "value", 0.75, "shape", 0.0)));

    assertEquals(0.0, (double) stage(payload).get("shape"), 1e-9);
  }

  @Test
  void addStageDefaultsShapeToLinear() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    Map<String, Object> payload = ok(new AddStage().handle(lx, Map.of(
        "path", envelope.getCanonicalPath(), "basis", 0.3, "value", 0.4)));

    assertEquals(1.0, (double) stage(payload).get("shape"), 1e-9);
  }

  @Test
  void removeStageRejectsFixedEndpointsBeforeMutatingAndEchoesTheResultingList() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    ok(new AddStage().handle(lx, Map.of(
        "path", envelope.getCanonicalPath(), "basis", 0.5, "value", 0.5)));

    Resolve.ResolveException rejected = assertThrows(Resolve.ResolveException.class,
        () -> new RemoveStage().handle(lx, Map.of(
            "path", envelope.getCanonicalPath(), "index", 0)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, rejected.failure);
    assertTrue(rejected.getMessage().contains("fixed endpoint"));
    assertEquals(3, envelope.stages.size(), "nothing was touched");

    Map<String, Object> payload = ok(new RemoveStage().handle(lx, Map.of(
        "path", envelope.getCanonicalPath(), "index", 1)));
    Map<String, Object> removed = (Map<String, Object>) payload.get("removed");
    assertEquals(1, removed.get("index"));
    assertEquals(2, payload.get("stageCount"));
    assertEquals(2, stages(payload).size());
    assertEquals(2, envelope.stages.size());
  }

  @Test
  void setStageAppliesBasisValueAndShapeWithinNeighbors() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    ok(new AddStage().handle(lx, Map.of(
        "path", envelope.getCanonicalPath(), "basis", 0.5, "value", 0.5)));

    Map<String, Object> payload = ok(new SetStage().handle(lx, Map.of(
        "path", envelope.getCanonicalPath(), "index", 1,
        "basis", 0.7, "value", 0.9, "shape", 3.0)));

    Map<String, Object> updated = stage(payload);
    assertEquals(0.7, (double) updated.get("basis"), 1e-9);
    assertEquals(0.9, (double) updated.get("value"), 1e-9);
    assertEquals(3.0, (double) updated.get("shape"), 1e-9);
  }

  @Test
  void setStageRejectsBasisAtOrBeyondANeighbor() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    ok(new AddStage().handle(lx, Map.of(
        "path", envelope.getCanonicalPath(), "basis", 0.5, "value", 0.5)));

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new SetStage().handle(lx, Map.of(
            "path", envelope.getCanonicalPath(), "index", 1, "basis", 1.5)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void setStageOnFixedEndpointLeavesBasisFixedButAppliesValue() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    Map<String, Object> payload = ok(new SetStage().handle(lx, Map.of(
        "path", envelope.getCanonicalPath(), "index", 0, "basis", 0.5, "value", 0.9)));

    Map<String, Object> updated = stage(payload);
    assertEquals(0.0, (double) updated.get("basis"), 1e-9);
    assertEquals(0.9, (double) updated.get("value"), 1e-9);
  }

  @Test
  void setStageRejectsShapeOnTheFixedInitialStage() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new SetStage().handle(lx, Map.of(
            "path", envelope.getCanonicalPath(), "index", 0, "shape", 2.0)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void setStageRequiresAtLeastOneEdit() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new SetStage().handle(lx, Map.of(
            "path", envelope.getCanonicalPath(), "index", 0)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void toolsRejectAWrongTypedPathAndOutOfRangeIndex() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    Resolve.ResolveException typeMismatch = assertThrows(Resolve.ResolveException.class,
        () -> new ListStages().handle(lx, Map.of("path", "/lx/mixer/master")));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, typeMismatch.failure);

    Resolve.ResolveException outOfRange = assertThrows(Resolve.ResolveException.class,
        () -> new SetStage().handle(lx, Map.of(
            "path", envelope.getCanonicalPath(), "index", 99, "value", 0.5)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, outOfRange.failure);
    assertTrue(outOfRange.getMessage().contains("out of range"));
  }
}
