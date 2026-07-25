package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.command.LXCommand;
import heronarts.lx.effect.BlurEffect;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.color.GradientPattern;
import heronarts.lx.pattern.color.SolidPattern;

/**
 * Domain-primitive unit tests for Channels mutations. Each test follows the qa-strategy
 * do → undo → assert-restored template; undo success proves the primitive went through
 * a real LXCommand.
 */
class ChannelMutationsTest extends HeadlessLxTest {

  // ── add/remove channel ───────────────────────────────────────────────────────

  @Test
  void addChannelAddsToMixerAndUndo() {
    LX lx = newHeadlessLx();
    int before = lx.engine.mixer.channels.size();

    LXChannel channel = Channels.addChannel(lx, null);
    assertEquals(before + 1, lx.engine.mixer.channels.size());
    assertNotNull(channel.getCanonicalPath());

    lx.command.undo();
    assertEquals(before, lx.engine.mixer.channels.size(), "undo should restore channel count");
  }

  @Test
  void addChannelWithPatternSeedsFirstPattern() {
    LX lx = newHeadlessLx();

    LXChannel channel = Channels.addChannel(lx, GradientPattern.class);
    assertEquals(1, channel.patterns.size());
    assertTrue(channel.patterns.get(0) instanceof GradientPattern);

    lx.command.undo();
    assertEquals(0, lx.engine.mixer.channels.stream()
        .filter(c -> c == channel).count(), "channel gone after undo");
  }

  @Test
  void removeChannelAndUndo() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    String path = channel.getCanonicalPath();
    int before = lx.engine.mixer.channels.size();

    Channels.removeChannel(lx, path);
    assertEquals(before - 1, lx.engine.mixer.channels.size());

    lx.command.undo();
    assertEquals(before, lx.engine.mixer.channels.size(), "channel restored after undo");
  }

  @Test
  void removeChannelUnknownPathThrowsNotFound() {
    LX lx = newHeadlessLx();
    var ex = assertThrows(Resolve.ResolveException.class,
        () -> Channels.removeChannel(lx, "/lx/mixer/channel/999"));
    assertEquals(Resolve.Failure.NOT_FOUND, ex.failure);
  }

  // ── add/remove pattern ────────────────────────────────────────────────────────

  @Test
  void addPatternAppendsAndUndo() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    int before = channel.patterns.size();

    LXPattern pattern = Channels.addPattern(lx, channel.getCanonicalPath(), GradientPattern.class, -1);
    assertEquals(before + 1, channel.patterns.size());
    assertTrue(pattern instanceof GradientPattern);
    assertSame(channel.patterns.get(before), pattern, "pattern appended at end");

    lx.command.undo();
    assertEquals(before, channel.patterns.size(), "pattern removed after undo");
  }

  @Test
  void addPatternAtIndexInsertsAtCorrectPosition() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.addPattern(new GradientPattern(lx));
    channel.addPattern(new GradientPattern(lx));
    int before = channel.patterns.size();

    LXPattern inserted = Channels.addPattern(lx, channel.getCanonicalPath(), SolidPattern.class, 0);
    assertEquals(0, inserted.getIndex(), "inserted at index 0");
    assertEquals(before + 1, channel.patterns.size());

    lx.command.undo();
    assertEquals(before, channel.patterns.size());
  }

  @Test
  void addPatternRejectsOutOfRangeIndex() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.addPattern(new GradientPattern(lx));
    var undoBefore = lx.command.getUndoCommand();

    // LX's addPattern throws on index > size INSIDE perform() — the swallow-and-clear
    // path that wipes the undo stack. The primitive must reject before the command.
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Channels.addPattern(lx, channel.getCanonicalPath(), SolidPattern.class, 5));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertEquals(1, channel.patterns.size(), "nothing was added");
    assertSame(undoBefore, lx.command.getUndoCommand(),
        "pre-check fired before perform — undo history untouched");
  }

  @Test
  void patternPrimitivesResolveRackHostedPatterns() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    heronarts.lx.pattern.PatternRack rack = new heronarts.lx.pattern.PatternRack(lx);
    channel.addPattern(rack);
    GradientPattern nested = new GradientPattern(lx);
    rack.patternEngine.addPattern(nested);
    int before = rack.patternEngine.patterns.size();

    // getEngine(), not the deprecated getChannel(): a rack-hosted pattern's parent is
    // the rack, and the old LXChannel cast blew up with ClassCastException.
    Channels.removePattern(lx, nested.getCanonicalPath());
    assertEquals(before - 1, rack.patternEngine.patterns.size(),
        "removed from the rack's own engine");

    lx.command.undo();
    assertEquals(before, rack.patternEngine.patterns.size(), "undo restores into the rack");
  }

  @Test
  void removePatternAndUndo() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPattern p1 = new GradientPattern(lx);
    LXPattern p2 = new GradientPattern(lx);
    channel.addPattern(p1);
    channel.addPattern(p2);
    int before = channel.patterns.size();
    String path = p2.getCanonicalPath();

    Channels.removePattern(lx, path);
    assertEquals(before - 1, channel.patterns.size());

    lx.command.undo();
    assertEquals(before, channel.patterns.size(), "pattern restored after undo");
  }

  @Test
  void addPatternUnknownClassThrowsTypeMismatch() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    var ex = assertThrows(Resolve.ResolveException.class,
        () -> Channels.resolvePatternClass(lx, "com.fake.NonExistentPattern"));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
  }

  @Test
  void resolvePatternClassAcceptsFullAndShortNames() {
    LX lx = newHeadlessLx();
    assertSame(GradientPattern.class,
        Channels.resolvePatternClass(lx, GradientPattern.class.getName()));
    assertSame(GradientPattern.class, Channels.resolvePatternClass(lx, "GradientPattern"));
    // GradientPattern's display name (LXComponent.getComponentName) has the "Pattern"
    // suffix stripped by LX's generic-superclass convention — differs from the simple name.
    assertEquals("Gradient", LXComponent.getComponentName(GradientPattern.class),
        "test assumption: display name strips the Pattern suffix");
    assertSame(GradientPattern.class, Channels.resolvePatternClass(lx, "Gradient"));
  }

  // ── activate pattern ──────────────────────────────────────────────────────────

  @Test
  void activatePatternChangesActiveAndUndo() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPattern p1 = new GradientPattern(lx);
    LXPattern p2 = new SolidPattern(lx);
    channel.addPattern(p1);
    channel.addPattern(p2);
    // p1 is active by default (first added)
    assertSame(p1, channel.getPatternEngine().getActivePattern());

    Channels.activatePattern(lx, p2.getCanonicalPath());
    assertSame(p2, channel.getPatternEngine().getActivePattern(), "p2 now active");

    lx.command.undo();
    assertSame(p1, channel.getPatternEngine().getActivePattern(), "p1 restored after undo");
  }

  @Test
  void activatePatternOnBlendChannelThrowsTypeMismatch() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPattern p = new GradientPattern(lx);
    channel.addPattern(p);
    // Switch to BLEND mode directly (no command wrapper needed for this setup)
    channel.getPatternEngine().compositeMode.setValue(
        heronarts.lx.mixer.LXPatternEngine.CompositeMode.BLEND);

    var ex = assertThrows(Resolve.ResolveException.class,
        () -> Channels.activatePattern(lx, p.getCanonicalPath()));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
    // Confirm undo stack was not touched by the rejection
    assertEquals(null, lx.command.getUndoCommand(),
        "undo stack must be untouched after TYPE_MISMATCH rejection");
  }

  // ── move pattern ──────────────────────────────────────────────────────────────

  @Test
  void movePatternChangesIndexAndUndo() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPattern p0 = new GradientPattern(lx);
    LXPattern p1 = new GradientPattern(lx);
    LXPattern p2 = new GradientPattern(lx);
    channel.addPattern(p0);
    channel.addPattern(p1);
    channel.addPattern(p2);
    String p0Before = p0.getCanonicalPath();
    String p1Before = p1.getCanonicalPath();
    String p2Before = p2.getCanonicalPath();

    // Move p0 to index 2
    Channels.PatternMoveResult result = Channels.movePattern(lx, p0.getCanonicalPath(), 2);
    LXPattern moved = result.pattern();
    assertEquals(2, moved.getIndex(), "p0 moved to index 2");

    // p1 and p2 shift down to fill p0's old slot; only these three paths changed.
    assertEquals(3, result.oscChanges().size());
    assertPathChange(result.oscChanges(), p0.getId(), p0Before, p0.getCanonicalPath());
    assertPathChange(result.oscChanges(), p1.getId(), p1Before, p1.getCanonicalPath());
    assertPathChange(result.oscChanges(), p2.getId(), p2Before, p2.getCanonicalPath());

    lx.command.undo();
    assertEquals(0, p0.getIndex(), "p0 back at index 0 after undo");
  }

  @Test
  void movePatternToSameIndexReportsNoChanges() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPattern p0 = new GradientPattern(lx);
    LXPattern p1 = new GradientPattern(lx);
    channel.addPattern(p0);
    channel.addPattern(p1);

    // Same-index move: no shift, so oscChanges must be empty.
    Channels.PatternMoveResult same = Channels.movePattern(lx, p0.getCanonicalPath(), 0);
    assertEquals(0, same.pattern().getIndex());
    assertTrue(same.oscChanges().isEmpty(), "moving to the same index changes nothing");

    // A real move on the same channel must report changes, so an always-empty
    // implementation of oscChanges would fail this test.
    Channels.PatternMoveResult moved = Channels.movePattern(lx, p0.getCanonicalPath(), 1);
    assertTrue(!moved.oscChanges().isEmpty(), "a real move must report path changes");
  }

  @Test
  void movePatternReportsOwnedEffectPathChange() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPattern p0 = new GradientPattern(lx);
    LXPattern p1 = new GradientPattern(lx);
    LXPattern p2 = new GradientPattern(lx);
    channel.addPattern(p0);
    channel.addPattern(p1);
    channel.addPattern(p2);
    BlurEffect e1 = new BlurEffect(lx);
    p1.addEffect(e1);
    String e1Before = e1.getCanonicalPath();

    // Move p0 past p1, shifting p1's index and therefore its owned effect's path too.
    Channels.PatternMoveResult result = Channels.movePattern(lx, p0.getCanonicalPath(), 2);

    assertPathChange(result.oscChanges(), e1.getId(), e1Before, e1.getCanonicalPath());
  }

  @Test
  void movePatternReportsNestedRackEffectPathChange() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPattern p0 = new GradientPattern(lx);
    heronarts.lx.pattern.PatternRack rack = new heronarts.lx.pattern.PatternRack(lx);
    LXPattern p2 = new GradientPattern(lx);
    channel.addPattern(p0);
    channel.addPattern(rack);
    channel.addPattern(p2);
    GradientPattern nested = new GradientPattern(lx);
    rack.patternEngine.addPattern(nested);
    BlurEffect nestedEffect = new BlurEffect(lx);
    nested.addEffect(nestedEffect);
    String rackBefore = rack.getCanonicalPath();
    String nestedBefore = nested.getCanonicalPath();
    String nestedEffectBefore = nestedEffect.getCanonicalPath();

    // Moving p0 past the rack shifts the rack's own path, which cascades into every
    // pattern and effect the rack hosts.
    Channels.PatternMoveResult result = Channels.movePattern(lx, p0.getCanonicalPath(), 1);

    assertPathChange(result.oscChanges(), rack.getId(), rackBefore, rack.getCanonicalPath());
    assertPathChange(result.oscChanges(), nested.getId(), nestedBefore, nested.getCanonicalPath());
    assertPathChange(result.oscChanges(), nestedEffect.getId(), nestedEffectBefore,
        nestedEffect.getCanonicalPath());
  }

  @Test
  void movePatternReportsDeviceScopedModulatorPathChange() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPattern p0 = new GradientPattern(lx);
    LXPattern p1 = new GradientPattern(lx);
    channel.addPattern(p0);
    channel.addPattern(p1);
    heronarts.lx.modulator.VariableLFO lfo = new heronarts.lx.modulator.VariableLFO();
    p1.modulation.addModulator(lfo, -1, null);
    String lfoBefore = lfo.getCanonicalPath();

    // Move p0 past p1, shifting p1's index and therefore the path of its device-local
    // modulator too.
    Channels.PatternMoveResult result = Channels.movePattern(lx, p0.getCanonicalPath(), 1);

    assertPathChange(result.oscChanges(), lfo.getId(), lfoBefore, lfo.getCanonicalPath());
  }

  @Test
  void movePatternReportsModulatorScopedToOwnedEffectPathChange() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPattern p0 = new GradientPattern(lx);
    LXPattern p1 = new GradientPattern(lx);
    channel.addPattern(p0);
    channel.addPattern(p1);
    BlurEffect e1 = new BlurEffect(lx);
    p1.addEffect(e1);
    heronarts.lx.modulator.VariableLFO lfo = new heronarts.lx.modulator.VariableLFO();
    e1.modulation.addModulator(lfo, -1, null);
    String lfoBefore = lfo.getCanonicalPath();

    // Move p0 past p1, shifting p1's index, which cascades into its owned effect's
    // path and that effect's own device-local modulator.
    Channels.PatternMoveResult result = Channels.movePattern(lx, p0.getCanonicalPath(), 1);

    assertPathChange(result.oscChanges(), lfo.getId(), lfoBefore, lfo.getCanonicalPath());
  }

  @Test
  void movePatternOutOfRangeIsTypeMismatch() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPattern p = new GradientPattern(lx);
    channel.addPattern(p);

    var ex = assertThrows(Resolve.ResolveException.class,
        () -> Channels.movePattern(lx, p.getCanonicalPath(), 99));
    // TYPE_MISMATCH is the invalid-value bucket; INVALID_PATH means malformed path.
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
    // Undo stack must be untouched
    assertEquals(null, lx.command.getUndoCommand(),
        "undo stack untouched when move rejected");
  }

  // ── add/remove effect ─────────────────────────────────────────────────────────

  @Test
  void addEffectToChannelAndUndo() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    int before = channel.effects.size();

    LXEffect effect = Channels.addEffect(lx, channel.getCanonicalPath(), BlurEffect.class);
    assertEquals(before + 1, channel.effects.size());
    assertSame(effect, channel.effects.get(before));

    lx.command.undo();
    assertEquals(before, channel.effects.size(), "effect removed after undo");
  }

  @Test
  void addEffectToMasterBusAndUndo() {
    LX lx = newHeadlessLx();
    int before = lx.engine.mixer.masterBus.effects.size();

    LXEffect effect = Channels.addEffect(lx,
        lx.engine.mixer.masterBus.getCanonicalPath(), BlurEffect.class);
    assertEquals(before + 1, lx.engine.mixer.masterBus.effects.size());

    lx.command.undo();
    assertEquals(before, lx.engine.mixer.masterBus.effects.size(), "effect removed after undo");
  }

  @Test
  void addEffectToPatternAndUndo() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    GradientPattern pattern = new GradientPattern(lx);
    channel.addPattern(pattern);
    int before = pattern.effects.size();

    LXEffect effect = Channels.addEffect(lx, pattern.getCanonicalPath(), BlurEffect.class);
    assertEquals(before + 1, pattern.effects.size());

    lx.command.undo();
    assertEquals(before, pattern.effects.size(), "effect removed from pattern after undo");
  }

  @Test
  void addEffectInvalidContainerThrowsTypeMismatch() {
    LX lx = newHeadlessLx();
    var ex = assertThrows(Resolve.ResolveException.class,
        () -> Channels.addEffect(lx, "/lx/mixer", BlurEffect.class));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
  }

  @Test
  void addEffectUnknownClassThrowsTypeMismatch() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    var ex = assertThrows(Resolve.ResolveException.class,
        () -> Channels.resolveEffectClass(lx, "com.fake.NonExistentEffect"));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
  }

  @Test
  void resolveEffectClassAcceptsFullAndShortNames() {
    LX lx = newHeadlessLx();
    assertSame(BlurEffect.class, Channels.resolveEffectClass(lx, BlurEffect.class.getName()));
    assertSame(BlurEffect.class, Channels.resolveEffectClass(lx, "BlurEffect"));
    assertEquals("Blur", LXComponent.getComponentName(BlurEffect.class),
        "test assumption: display name strips the Effect suffix");
    assertSame(BlurEffect.class, Channels.resolveEffectClass(lx, "Blur"));
  }

  @Test
  void removeEffectAndUndo() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    BlurEffect effect = new BlurEffect(lx);
    channel.addEffect(effect);
    int before = channel.effects.size();
    String path = effect.getCanonicalPath();

    Channels.removeEffect(lx, path);
    assertEquals(before - 1, channel.effects.size());

    lx.command.undo();
    assertEquals(before, channel.effects.size(), "effect restored after undo");
  }

  @Test
  void removeLockedEffectThrowsTypeMismatchWithoutTouchingUndoStack() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    BlurEffect effect = new BlurEffect(lx);
    channel.addEffect(effect);
    effect.locked.setValue(true);

    LXCommand undoBefore = lx.command.getUndoCommand();
    var ex = assertThrows(Resolve.ResolveException.class,
        () -> Channels.removeEffect(lx, effect.getCanonicalPath()));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
    assertSame(undoBefore, lx.command.getUndoCommand(),
        "undo stack must be untouched when locked effect rejection");
  }

  // ── move effect ───────────────────────────────────────────────────────────────

  @Test
  void moveEffectChangesIndexAndUndo() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    BlurEffect e0 = new BlurEffect(lx);
    BlurEffect e1 = new BlurEffect(lx);
    BlurEffect e2 = new BlurEffect(lx);
    channel.addEffect(e0);
    channel.addEffect(e1);
    channel.addEffect(e2);
    String e0Before = e0.getCanonicalPath();
    String e1Before = e1.getCanonicalPath();
    String e2Before = e2.getCanonicalPath();

    Channels.EffectMoveResult result = Channels.moveEffect(lx, e0.getCanonicalPath(), 2);
    assertEquals(2, result.effect().getIndex(), "e0 moved to index 2");

    assertEquals(3, result.oscChanges().size());
    assertPathChange(result.oscChanges(), e0.getId(), e0Before, e0.getCanonicalPath());
    assertPathChange(result.oscChanges(), e1.getId(), e1Before, e1.getCanonicalPath());
    assertPathChange(result.oscChanges(), e2.getId(), e2Before, e2.getCanonicalPath());

    lx.command.undo();
    assertEquals(0, e0.getIndex(), "e0 back at index 0 after undo");
  }

  @Test
  void moveEffectToSameIndexReportsNoChanges() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    BlurEffect e0 = new BlurEffect(lx);
    BlurEffect e1 = new BlurEffect(lx);
    channel.addEffect(e0);
    channel.addEffect(e1);

    // Same-index move: no shift, so oscChanges must be empty.
    Channels.EffectMoveResult same = Channels.moveEffect(lx, e0.getCanonicalPath(), 0);
    assertEquals(0, same.effect().getIndex());
    assertTrue(same.oscChanges().isEmpty(), "moving to the same index changes nothing");

    // A real move on the same channel must report changes, so an always-empty
    // implementation of oscChanges would fail this test.
    Channels.EffectMoveResult moved = Channels.moveEffect(lx, e0.getCanonicalPath(), 1);
    assertTrue(!moved.oscChanges().isEmpty(), "a real move must report path changes");
  }

  @Test
  void moveEffectOnPatternAndUndo() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    GradientPattern pattern = new GradientPattern(lx);
    channel.addPattern(pattern);
    BlurEffect e0 = new BlurEffect(lx);
    BlurEffect e1 = new BlurEffect(lx);
    pattern.addEffect(e0);
    pattern.addEffect(e1);

    Channels.moveEffect(lx, e0.getCanonicalPath(), 1);
    assertEquals(1, e0.getIndex(), "e0 moved to index 1 on pattern");

    lx.command.undo();
    assertEquals(0, e0.getIndex(), "e0 back at 0 on pattern after undo");
  }

  @Test
  void moveEffectReportsDeviceScopedModulatorPathChange() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    BlurEffect e0 = new BlurEffect(lx);
    BlurEffect e1 = new BlurEffect(lx);
    channel.addEffect(e0);
    channel.addEffect(e1);
    heronarts.lx.modulator.VariableLFO lfo = new heronarts.lx.modulator.VariableLFO();
    e1.modulation.addModulator(lfo, -1, null);
    String lfoBefore = lfo.getCanonicalPath();

    // Move e0 past e1, shifting e1's index and therefore the path of its device-local
    // modulator too. LXEffect is an LXDeviceComponent like LXPattern.
    Channels.EffectMoveResult result = Channels.moveEffect(lx, e0.getCanonicalPath(), 1);

    assertPathChange(result.oscChanges(), lfo.getId(), lfoBefore, lfo.getCanonicalPath());
  }

  @Test
  void moveEffectOutOfRangeIsTypeMismatch() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    BlurEffect e = new BlurEffect(lx);
    channel.addEffect(e);

    LXCommand undoBefore = lx.command.getUndoCommand();
    var ex = assertThrows(Resolve.ResolveException.class,
        () -> Channels.moveEffect(lx, e.getCanonicalPath(), 99));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, ex.failure);
    assertSame(undoBefore, lx.command.getUndoCommand(),
        "undo stack untouched on out-of-range move");
  }

  /** Asserts a Channels.PathChange for componentId with the given before/after is present. */
  private static void assertPathChange(List<Channels.PathChange> changes, int componentId,
      String before, String after) {
    Channels.PathChange expected = new Channels.PathChange(componentId, before, after);
    assertTrue(changes.contains(expected),
        "expected " + expected + " in " + changes);
  }
}
