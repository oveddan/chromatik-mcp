package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.command.LXCommand;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.output.LXOutputGroup;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.structure.GridFixture;
import heronarts.lx.structure.LXFixture;
import heronarts.lx.structure.LXProtocolFixture;

/**
 * The PR-3b gate: every path-taking mutation downstream depends on this resolver, so
 * each failure mode (missing object, wrong kind, malformed path) must map to its typed
 * error rather than an unchecked surprise.
 */
class ResolveTest extends HeadlessLxTest {

  private static Resolve.Failure failureOf(Runnable call) {
    return assertThrows(Resolve.ResolveException.class, call::run).failure;
  }

  @Test
  void resolvesParameterAndComponentByCanonicalPath() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    assertSame(channel.fader, Resolve.parameter(lx, channel.fader.getCanonicalPath()));
    assertSame(channel, Resolve.component(lx, channel.getCanonicalPath()));
    assertSame(channel, Resolve.component(lx, channel.getCanonicalPath(), LXChannel.class));
  }

  @Test
  void acceptsPathsWithoutLeadingSlash() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    String path = channel.fader.getCanonicalPath().substring(1);
    assertSame(channel.fader, Resolve.parameter(lx, path));
  }

  @Test
  void missingObjectIsNotFound() {
    LX lx = newHeadlessLx();
    assertEquals(Resolve.Failure.NOT_FOUND, failureOf(() -> Resolve.parameter(lx, "/lx/nope/nothing")));
    assertEquals(Resolve.Failure.NOT_FOUND, failureOf(() -> Resolve.component(lx, "/lx/mixer/channel/99")));
  }

  @Test
  void wrongKindIsTypeMismatch() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    // A component where a parameter was requested, and vice versa.
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        failureOf(() -> Resolve.parameter(lx, channel.getCanonicalPath())));
    Resolve.ResolveException mismatch = assertThrows(Resolve.ResolveException.class,
        () -> Resolve.component(lx, channel.fader.getCanonicalPath()));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, mismatch.failure);
    assertTrue(mismatch.getMessage().contains(channel.fader.getCanonicalPath()),
        "message names the offending path");

    // Right component, wrong requested type.
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        failureOf(() -> Resolve.component(lx, channel.getCanonicalPath(), LXGroup.class)));
  }

  @Test
  void malformedPathIsInvalidPath() {
    LX lx = newHeadlessLx();
    assertEquals(Resolve.Failure.INVALID_PATH, failureOf(() -> Resolve.parameter(lx, "")));
    assertEquals(Resolve.Failure.INVALID_PATH, failureOf(() -> Resolve.parameter(lx, null)));
    assertEquals(Resolve.Failure.INVALID_PATH,
        failureOf(() -> Resolve.parameter(lx, "/mixer/channel/1/fader")));
  }

  @Test
  void emptySegmentsAreTypedNotUncheckedCrashes() {
    LX lx = newHeadlessLx();
    // These escape LX's path walker as IllegalArgumentException if not pre-validated.
    assertEquals(Resolve.Failure.INVALID_PATH, failureOf(() -> Resolve.parameter(lx, "/lx//")));
    assertEquals(Resolve.Failure.INVALID_PATH, failureOf(() -> Resolve.parameter(lx, "/lx///")));
    assertEquals(Resolve.Failure.INVALID_PATH, failureOf(() -> Resolve.parameter(lx, "/lx/")));
    assertEquals(Resolve.Failure.INVALID_PATH, failureOf(() -> Resolve.component(lx, "/lx/mixer/")));
    assertEquals(Resolve.Failure.INVALID_PATH, failureOf(() -> Resolve.component(lx, "/lx//mixer")));
  }

  @Test
  void trailingSegmentsAfterAParameterDoNotResolve() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    // LX's walker matches the fader and ignores the rest; for a resolver that mutations
    // depend on, an over-long path must be a typed miss, not a silent parent match.
    assertEquals(Resolve.Failure.NOT_FOUND,
        failureOf(() -> Resolve.parameter(lx, channel.fader.getCanonicalPath() + "/garbage")));
  }

  @Test
  void bareRootResolvesToTheEngine() {
    LX lx = newHeadlessLx();
    assertSame(lx.engine, Resolve.component(lx, "/lx"));
    assertSame(lx.engine, Resolve.component(lx, "lx"));
  }

  // ── resolveClassName: short-name resolution shared by Modulators/Channels/Catalog ──

  @LXComponent.Name("Dup")
  abstract static class DupA extends LXModulator {
    protected DupA() { super("DupA"); }
  }

  @LXComponent.Name("Dup")
  abstract static class DupB extends LXModulator {
    protected DupB() { super("DupB"); }
  }

  abstract static class Solo extends LXModulator {
    protected Solo() { super("Solo"); }
  }

  @Test
  void resolveClassNameMatchesFullNameOverShortName() {
    List<Class<? extends LXModulator>> registry = List.of(DupA.class, DupB.class, Solo.class);
    assertSame(Solo.class,
        Resolve.resolveClassName(registry, Solo.class.getName(), Resolve.Failure.TYPE_MISMATCH, "unused"));
  }

  @Test
  void resolveClassNameMatchesUniqueSimpleOrDisplayName() {
    List<Class<? extends LXModulator>> registry = List.of(DupA.class, DupB.class, Solo.class);
    assertSame(Solo.class,
        Resolve.resolveClassName(registry, "Solo", Resolve.Failure.TYPE_MISMATCH, "unused"));
  }

  @Test
  void resolveClassNameAmbiguousDisplayNameThrowsListingCandidates() {
    List<Class<? extends LXModulator>> registry = List.of(DupA.class, DupB.class, Solo.class);
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Resolve.resolveClassName(registry, "Dup", Resolve.Failure.TYPE_MISMATCH, "unused"));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains(DupA.class.getName()), "names DupA as a candidate");
    assertTrue(e.getMessage().contains(DupB.class.getName()), "names DupB as a candidate");
  }

  @Test
  void resolveClassNameUnknownUsesCallerSuppliedFailureAndMessage() {
    List<Class<? extends LXModulator>> registry = List.of(Solo.class);
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Resolve.resolveClassName(registry, "Nope", Resolve.Failure.NOT_FOUND, "custom message"));
    assertEquals(Resolve.Failure.NOT_FOUND, e.failure);
    assertEquals("custom message", e.getMessage());
  }

  @Test
  void resolvesIntoTheStructureTree() {
    LX lx = newHeadlessLx();
    var view = lx.structure.views.addView();

    assertSame(lx.structure, Resolve.component(lx, "/lx/structure"));
    assertSame(lx.structure.views, Resolve.component(lx, "/lx/structure/views"));
    assertSame(view, Resolve.component(lx, "/lx/structure/views/view/1"));
    assertSame(view.selector, Resolve.parameter(lx, "/lx/structure/views/view/1/selector"));
  }

  @Test
  void badStructurePathsAreNotFound() {
    LX lx = newHeadlessLx();
    assertEquals(Resolve.Failure.NOT_FOUND,
        failureOf(() -> Resolve.component(lx, "/lx/structure/views/view/1")));
    assertEquals(Resolve.Failure.NOT_FOUND,
        failureOf(() -> Resolve.component(lx, "/lx/structure/nope")));
  }

  // ── fixture paths: LXStructure never registers "fixture" via addArray, so it needs a
  // dedicated case in Resolve.walk (unlike "view", which LXViewEngine registers) ──

  @Test
  void resolvesIntoTheFixtureTree() {
    // addFixture throws against a static model — construct a dynamic-structure LX
    // (no model passed) rather than the per-test GridModel default.
    LX lx = track(new LX());
    Commands.perform(lx, new LXCommand.Structure.AddFixture(GridFixture.class));
    LXProtocolFixture fixture = (LXProtocolFixture) lx.structure.fixtures.get(0);

    LXComponent resolved = Resolve.component(lx, "/lx/structure/fixture/1");
    assertSame(fixture, resolved);
    assertSame(fixture.x, Resolve.parameter(lx, "/lx/structure/fixture/1/x"));

    // set_parameter's underlying primitive round-trips through the same resolver.
    Parameters.set(lx, "/lx/structure/fixture/1/artNetUniverse", 5);
    assertEquals(5, fixture.artNetUniverse.getValuei());

    // The existing view paths still resolve unchanged.
    var view = lx.structure.views.addView();
    assertSame(view, Resolve.component(lx, "/lx/structure/views/view/1"));
  }

  @Test
  void outOfRangeFixtureIndexIsNotFound() {
    LX lx = track(new LX());
    Commands.perform(lx, new LXCommand.Structure.AddFixture(GridFixture.class));
    LXFixture fixture = lx.structure.fixtures.get(0);

    assertEquals(Resolve.Failure.NOT_FOUND,
        failureOf(() -> Resolve.component(lx, "/lx/structure/fixture/2")));
  }

  // ── canonicalPathOrNull: the third-occurrence "/null" hazard (issue #119) ──

  @Test
  void canonicalPathOrNullIsNullForABareUnregisteredParameter() {
    // Never added to a component via addParameter — getPath() is null, so
    // getCanonicalPath() would build the bogus literal "/null" (LXPath.java:85-93).
    BooleanParameter unregistered = new BooleanParameter("unregistered");

    assertNull(Resolve.canonicalPathOrNull(unregistered));
  }

  @Test
  void canonicalPathOrNullMatchesGetCanonicalPathForARegisteredParameter() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    assertEquals(channel.fader.getCanonicalPath(), Resolve.canonicalPathOrNull(channel.fader));
    assertEquals(channel.getCanonicalPath(), Resolve.canonicalPathOrNull(channel));
  }

  @Test
  void canonicalPathOrNullIsNullWhenAnAncestorIsUnregistered() {
    // LXOutputGroup.addChild(LXOutput) calls child.setGroup(this), which calls
    // LXComponent.setParent(parent) with no path — the child output is never
    // path-registered (LXOutput doesn't override getPath() the way LXModulator/LXLayer do),
    // even though it's live in the tree. Its own parameters (e.g. enabled, addParameter'd in
    // the LXOutput constructor) still get their own non-null path, so a leaf-only null check
    // would miss this: the hazard is one level up the ancestor chain, not at the leaf.
    LX lx = newHeadlessLx();
    LXOutputGroup group = new LXOutputGroup(lx);
    LXOutputGroup child = new LXOutputGroup(lx);
    group.addChild(child);

    assertNull(child.getPath());
    assertNull(Resolve.canonicalPathOrNull(child.enabled));
  }
}
