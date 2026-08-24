package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.modulator.MultiStageEnvelope;
import heronarts.lx.modulator.MultiStageEnvelope.Stage;

/**
 * Stage-list primitives for {@link MultiStageEnvelope}. Stages are plain (non-{@code
 * LXComponent}, non-{@code LXParameter}) objects held in a bare list — invisible to both
 * {@code list_parameters}'s generic child walk and canonical-path resolution — so they are
 * addressed positionally, {@code {envelopePath, index}}, mirroring clip-lane events
 * ({@link ClipEvents}). LX ships no {@code LXCommand} for stage mutation, so every write
 * here is a direct engine edit: not undoable, matching {@code set_clip_lane_visible}.
 */
public final class Envelopes {

  private Envelopes() {}

  /** One stage's state, plus its current 0-based position in {@code envelope.stages}. */
  public record StageInfo(
      int index, double basis, double value, double shape, boolean initial, boolean last) {}

  /** Resolve {@code path} to a {@link MultiStageEnvelope} modulator. */
  public static MultiStageEnvelope resolve(LX lx, String path) {
    return Resolve.component(lx, path, MultiStageEnvelope.class);
  }

  /** Snapshot a stage at its current index; {@code index} is read from the live list. */
  public static StageInfo summary(MultiStageEnvelope envelope, Stage stage) {
    int index = envelope.stages.indexOf(stage);
    return new StageInfo(
        index, stage.getBasis(), stage.getValue(), stage.getShape(), stage.initial, stage.last);
  }

  /** Every stage on the envelope, in basis order (index 0 = fixed at basis 0). */
  public static List<StageInfo> list(MultiStageEnvelope envelope) {
    List<StageInfo> stages = new ArrayList<>();
    for (Stage stage : envelope.stages) {
      stages.add(summary(envelope, stage));
    }
    return stages;
  }

  /**
   * Resolve a stage by its current 0-based position in {@code envelope.stages}.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if {@code index} is out of range
   */
  public static Stage stageAt(MultiStageEnvelope envelope, int index) {
    List<Stage> stages = envelope.stages;
    if (index < 0 || index >= stages.size()) {
      throw Resolve.invalidArgument("index " + index + " out of range: envelope has "
          + stages.size() + " stages (0-" + (stages.size() - 1) + ")");
    }
    return stages.get(index);
  }

  /**
   * Insert an interior stage at {@code basis} with {@code value} (both must land strictly
   * inside (0,1) — {@code MultiStageEnvelope.addStage(double,double)} only clamps into
   * [0,1] INCLUSIVE, and a basis of exactly 0 or 1 lands the new stage adjacent to the
   * fixed first/last endpoint at that same basis; {@code compute()} then resolves that
   * basis to whichever of the two stages comes first in iteration order, silently
   * shadowing the other — for basis 1 that orphans the fixed last stage, whose configured
   * value becomes permanently unreachable even though it still reports as present), and
   * optionally overriding the default shape (1, linear). Direct engine edit, not
   * undoable — but stages ARE serialized ({@code MultiStageEnvelope.save}), so this
   * marks the project dirty itself ({@code lx.command.setDirty(true)}) since no
   * {@code LXCommand} runs to do that automatically.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if {@code basis} is not strictly
   *     between 0 and 1, coincides with an existing stage's basis, or {@code shape} is
   *     negative
   */
  public static StageInfo addStage(LX lx, MultiStageEnvelope envelope, double basis,
      double value, Double shape) {
    if (basis <= 0 || basis >= 1) {
      throw Resolve.invalidArgument("basis must be strictly between 0 and 1 (exclusive): "
          + basis + " — 0 and 1 are the fixed first/last stage's positions; an interior "
          + "stage there would shadow one of them");
    }
    requireNoStageAt(envelope, basis);
    requireValidShape(shape);
    Stage stage = envelope.addStage(basis, value);
    if (shape != null) {
      stage.setShape(shape);
    }
    lx.command.setDirty(true);
    return summary(envelope, stage);
  }

  /**
   * Remove the interior stage at {@code index}. {@code MultiStageEnvelope.removeStage}
   * silently no-ops on the fixed first/last endpoints rather than failing, which would
   * misreport success to a caller trying to remove one — pre-checked and rejected here
   * instead. Direct engine edit, not undoable; marks the project dirty itself (see
   * {@link #addStage}).
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if {@code index} is out of range or
   *     addresses a fixed endpoint (basis 0 or 1)
   */
  public static void removeStage(LX lx, MultiStageEnvelope envelope, int index) {
    Stage stage = stageAt(envelope, index);
    if (stage.initial || stage.last) {
      throw Resolve.invalidArgument("Not removable: stage " + index
          + " is a fixed endpoint (basis " + stage.getBasis() + ") — only interior stages "
          + "can be removed");
    }
    envelope.removeStage(stage);
    lx.command.setDirty(true);
  }

  /**
   * Apply any combination of {@code basis}/{@code value}/{@code shape} to the stage at
   * {@code index}; at least one must be present. On an interior stage, a requested
   * {@code basis} is rejected unless it lands strictly between its neighbors — {@code
   * Stage.setPosition}'s own clamp is INCLUSIVE of the neighbor's basis, which would
   * otherwise let a move land exactly on a neighbor (shadowing it, the same failure mode
   * {@link #addStage} guards against on insert). On a fixed endpoint (index 0 or the
   * last), {@code basis} never moves — {@code value} still applies. Direct engine edit,
   * not undoable; marks the project dirty itself (see {@link #addStage}).
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if {@code index} is out of range,
   *     {@code basis} would not land strictly between its neighbors, {@code shape} is
   *     negative, or {@code shape} targets the fixed initial stage
   */
  public static StageInfo setStage(
      LX lx, MultiStageEnvelope envelope, int index, Double basis, Double value, Double shape) {
    Stage stage = stageAt(envelope, index);
    requireValidShape(shape);
    if (shape != null && stage.initial) {
      // compute()'s per-stage shape shapes the segment ARRIVING at that stage; the
      // initial stage (basis 0) has no preceding segment, so its own shape field is
      // never read — silently accepting this would persist a value that has no effect.
      throw Resolve.invalidArgument("shape has no effect on the fixed initial stage "
          + "(index " + index + ", basis 0) — it has no preceding segment; shape the "
          + "segment arriving at the NEXT stage instead");
    }
    if (basis != null && !stage.initial && !stage.last) {
      double prevBasis = envelope.stages.get(index - 1).getBasis();
      double nextBasis = envelope.stages.get(index + 1).getBasis();
      if (basis <= prevBasis || basis >= nextBasis) {
        throw Resolve.invalidArgument("basis must land strictly between its neighbors "
            + "(" + prevBasis + ", " + nextBasis + " exclusive): " + basis
            + " — landing on a neighbor's basis would shadow it during interpolation");
      }
    }
    if (basis != null || value != null) {
      stage.setPosition(basis != null ? basis : stage.getBasis(),
          value != null ? value : stage.getValue());
    }
    if (shape != null) {
      stage.setShape(shape);
    }
    lx.command.setDirty(true);
    return summary(envelope, stage);
  }

  /**
   * Guards {@link #addStage} against landing on an existing interior stage's exact
   * basis — {@code MultiStageEnvelope.addStage} has no such check itself, and a
   * duplicate basis leaves one of the two stages' value/shape unreachable during
   * interpolation while both the original add and this one report success.
   */
  private static void requireNoStageAt(MultiStageEnvelope envelope, double basis) {
    for (Stage existing : envelope.stages) {
      if (existing.getBasis() == basis) {
        throw Resolve.invalidArgument("a stage already exists at basis " + basis
            + " — pick a different basis, or edit the existing stage with set_stage");
      }
    }
  }

  /**
   * {@code compute()} raises a segment's relative basis to the {@code shape} power
   * ({@code Math.pow(relativeBasis, shape)}); {@code shape} 0 is valid ({@code
   * Math.pow(x, 0)} is always {@code 1.0} in Java, so it jumps to this stage's value
   * partway through the segment — a legitimate step). A negative shape is rejected: as
   * the driving basis approaches this segment's start, relativeBasis approaches 0 from
   * above, and {@code Math.pow} of a value near 0 raised to a negative exponent grows
   * without bound (and hits literal {@code +Infinity} at the floating-point-underflow
   * edge case where relativeBasis rounds to exactly 0.0), corrupting the envelope's
   * output with an unbounded spike rather than a smooth curve.
   */
  private static void requireValidShape(Double shape) {
    if (shape != null && shape < 0) {
      throw Resolve.invalidArgument("shape must not be negative: " + shape
          + " — Math.pow(relativeBasis, shape) grows unbounded (and can hit Infinity) as "
          + "relativeBasis approaches 0 near the start of the segment");
    }
  }
}
