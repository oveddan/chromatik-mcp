package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.model.GridModel;

/**
 * The PR-3b gate: every path-taking mutation downstream depends on this resolver, so
 * each failure mode (missing object, wrong kind, malformed path) must map to its typed
 * error rather than an unchecked surprise.
 */
class ResolveTest {

  private LX lx;

  private LX newHeadlessLx() {
    this.lx = new LX(new GridModel(8, 8));
    return this.lx;
  }

  @AfterEach
  void tearDown() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }

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
}
