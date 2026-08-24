package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;
import chromatikmcp.domain.Envelopes.StageInfo;

import heronarts.lx.LX;
import heronarts.lx.modulator.MultiStageEnvelope;
import heronarts.lx.modulator.MultiStageEnvelope.Stage;

/**
 * MultiStageEnvelope stages are plain (non-LXComponent) objects held in a bare list, so
 * these primitives are direct engine edits addressed positionally — no LXCommand, no
 * undo — but stages ARE serialized, so they must mark the project dirty themselves.
 * Coverage: the fixed-endpoint list shape, basis-order insertion, the endpoint/duplicate-
 * basis guards, the neighbor-clamp on interior basis moves, the shape validity guard
 * (negative rejected, zero a valid step), the initial-stage shape guard, and the dirty
 * flag.
 */
class EnvelopesTest extends HeadlessLxTest {

  private MultiStageEnvelope newEnvelope(LX lx) {
    MultiStageEnvelope envelope =
        (MultiStageEnvelope) Modulators.addModulator(lx, lx.engine.modulation, MultiStageEnvelope.class);
    return envelope;
  }

  @Test
  void listReturnsTheDefaultTwoStageRampWithFixedEndpoints() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    var stages = Envelopes.list(envelope);

    assertEquals(2, stages.size());
    StageInfo first = stages.get(0);
    assertEquals(0, first.index());
    assertEquals(0.0, first.basis(), 1e-9);
    assertTrue(first.initial());
    assertTrue(!first.last());
    StageInfo last = stages.get(1);
    assertEquals(1, last.index());
    assertEquals(1.0, last.basis(), 1e-9);
    assertTrue(last.last());
    assertTrue(!last.initial());
  }

  @Test
  void addStageInsertsInBasisOrderSetsShapeAndMarksDirty() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    lx.command.setDirty(false);

    StageInfo added = Envelopes.addStage(lx, envelope, 0.5, 0.75, 2.0);

    assertEquals(1, added.index(), "inserted between the fixed first/last stage");
    assertEquals(0.5, added.basis(), 1e-9);
    assertEquals(0.75, added.value(), 1e-9);
    assertEquals(2.0, added.shape(), 1e-9);
    assertEquals(3, envelope.stages.size());
    assertTrue(lx.command.isDirty(),
        "stages are serialized, so a direct edit must mark the project dirty itself");
  }

  @Test
  void addStageDefaultsShapeToLinearWhenOmitted() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    StageInfo added = Envelopes.addStage(lx, envelope, 0.3, 0.4, null);

    assertEquals(1.0, added.shape(), 1e-9);
  }

  @Test
  void addStageAcceptsAZeroShapeStep() {
    // Math.pow(x, 0) is always 1.0 in Java, regardless of x — a valid instant step,
    // not a source of Infinity/NaN.
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    StageInfo added = Envelopes.addStage(lx, envelope, 0.5, 0.75, 0.0);

    assertEquals(0.0, added.shape(), 1e-9);
  }

  @Test
  void addStageRejectsBasisAtOrBeyondTheFixedEndpointsWithoutMutation() {
    // A basis of exactly 0 or 1 would land the new stage adjacent to the fixed
    // first/last endpoint at that same basis; MultiStageEnvelope.compute() then resolves
    // that basis to whichever stage comes first in iteration order, silently shadowing
    // the other — for basis 1 that orphans the fixed last stage's configured value.
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    int before = envelope.stages.size();

    for (double basis : new double[] {0.0, 1.0, -0.5, 1.5}) {
      Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
          () -> Envelopes.addStage(lx, envelope, basis, 0.5, null));
      assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    }
    assertEquals(before, envelope.stages.size(), "nothing was added");
  }

  @Test
  void addStageRejectsADuplicateInteriorBasisWithoutMutation() {
    // MultiStageEnvelope.addStage has no duplicate-basis check of its own; a duplicate
    // leaves one of the two stages' value/shape unreachable during interpolation while
    // both calls report success.
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    Envelopes.addStage(lx, envelope, 0.5, 0.5, null);
    int before = envelope.stages.size();

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Envelopes.addStage(lx, envelope, 0.5, 0.9, null));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertEquals(before, envelope.stages.size(), "nothing was added");
  }

  @Test
  void addStageRejectsNegativeShapeWithoutMutation() {
    // Math.pow(relativeBasis, shape) grows unbounded (and can hit Infinity) as
    // relativeBasis approaches 0 near a segment's start when shape is negative.
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    int before = envelope.stages.size();

    for (double shape : new double[] {-1.0, -0.001}) {
      Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
          () -> Envelopes.addStage(lx, envelope, 0.5, 0.5, shape));
      assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    }
    assertEquals(before, envelope.stages.size(), "nothing was added");
  }

  @Test
  void removeStageRejectsFixedEndpointsWithoutMutation() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    Envelopes.addStage(lx, envelope, 0.5, 0.5, null);
    int before = envelope.stages.size();

    Resolve.ResolveException firstRejected = assertThrows(Resolve.ResolveException.class,
        () -> Envelopes.removeStage(lx, envelope, 0));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, firstRejected.failure);
    assertTrue(firstRejected.getMessage().contains("fixed endpoint"));

    Resolve.ResolveException lastRejected = assertThrows(Resolve.ResolveException.class,
        () -> Envelopes.removeStage(lx, envelope, envelope.stages.size() - 1));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, lastRejected.failure);

    assertEquals(before, envelope.stages.size(), "nothing was removed");
  }

  @Test
  void removeStageRemovesAnInteriorStageAndMarksDirty() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    Envelopes.addStage(lx, envelope, 0.5, 0.5, null);
    assertEquals(3, envelope.stages.size());
    lx.command.setDirty(false);

    Envelopes.removeStage(lx, envelope, 1);

    assertEquals(2, envelope.stages.size());
    assertTrue(lx.command.isDirty());
  }

  @Test
  void removeStageRejectsOutOfRangeIndex() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Envelopes.removeStage(lx, envelope, 99));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("out of range"));
  }

  @Test
  void setStageAppliesBasisWithinNeighborsAlongsideValueShapeAndMarksDirty() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    Envelopes.addStage(lx, envelope, 0.5, 0.5, null);
    lx.command.setDirty(false);

    StageInfo updated = Envelopes.setStage(lx, envelope, 1, 0.7, 0.9, 3.0);

    assertEquals(0.7, updated.basis(), 1e-9);
    assertEquals(0.9, updated.value(), 1e-9);
    assertEquals(3.0, updated.shape(), 1e-9);
    assertTrue(lx.command.isDirty());
  }

  @Test
  void setStageRejectsBasisAtOrBeyondANeighborWithoutMutation() {
    // Stage.setPosition's own clamp is INCLUSIVE of the neighbor's basis, which would
    // land the moved stage exactly on the neighbor — shadowing it during interpolation,
    // the same failure mode addStage guards against on insert.
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    Envelopes.addStage(lx, envelope, 0.5, 0.5, null);
    double before = envelope.stages.get(1).getBasis();

    for (double basis : new double[] {1.0, 1.5, 0.0, -0.5}) {
      Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
          () -> Envelopes.setStage(lx, envelope, 1, basis, null, null));
      assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    }
    assertEquals(before, envelope.stages.get(1).getBasis(), 1e-9, "basis never moved");
  }

  @Test
  void setStageOnFixedEndpointNeverMovesBasisButValueApplies() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    StageInfo updated = Envelopes.setStage(lx, envelope, 0, 0.5, 0.9, null);

    assertEquals(0.0, updated.basis(), 1e-9, "the fixed first stage's basis never moves");
    assertEquals(0.9, updated.value(), 1e-9);
  }

  @Test
  void setStageRejectsShapeOnTheFixedInitialStageWithoutMutation() {
    // compute()'s per-stage shape only shapes the segment ARRIVING at that stage; the
    // initial stage (basis 0) has no preceding segment, so its shape field is never read
    // — silently accepting this would persist a value with no effect.
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Envelopes.setStage(lx, envelope, 0, null, null, 2.0));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertEquals(1.0, envelope.stages.get(0).getShape(), 1e-9, "shape was never applied");
  }

  @Test
  void setStageAcceptsAZeroShapeStep() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    Envelopes.addStage(lx, envelope, 0.5, 0.5, null);

    StageInfo updated = Envelopes.setStage(lx, envelope, 1, null, null, 0.0);

    assertEquals(0.0, updated.shape(), 1e-9);
  }

  @Test
  void setStageRejectsNegativeShapeWithoutMutation() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    Envelopes.addStage(lx, envelope, 0.5, 0.5, null);

    for (double shape : new double[] {-1.0, -0.001}) {
      Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
          () -> Envelopes.setStage(lx, envelope, 1, null, null, shape));
      assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    }
    assertEquals(1.0, envelope.stages.get(1).getShape(), 1e-9, "the shape was never applied");
  }

  @Test
  void setStageRejectsOutOfRangeIndex() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Envelopes.setStage(lx, envelope, 99, 0.5, null, null));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void resolveRejectsAWrongTypedPath() {
    LX lx = newHeadlessLx();
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Envelopes.resolve(lx, "/lx/mixer/master"));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void resolveFindsAnAddedEnvelopeByCanonicalPath() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);

    MultiStageEnvelope resolved = Envelopes.resolve(lx, envelope.getCanonicalPath());

    assertEquals(envelope, resolved);
  }

  @Test
  void summaryReflectsTheStagesCurrentIndex() {
    LX lx = newHeadlessLx();
    MultiStageEnvelope envelope = newEnvelope(lx);
    Envelopes.addStage(lx, envelope, 0.5, 0.5, null);
    Stage last = envelope.stages.get(envelope.stages.size() - 1);

    StageInfo summary = Envelopes.summary(envelope, last);

    assertEquals(envelope.stages.size() - 1, summary.index());
    assertTrue(summary.last());
  }
}
