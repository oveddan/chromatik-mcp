package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.effect.audio.SoundObjectEffect;
import heronarts.lx.model.GridModel;
import heronarts.lx.modulator.SinLFO;
import heronarts.lx.pattern.audio.SoundObjectPattern;
import heronarts.lx.pattern.color.GradientPattern;

class CatalogTest {

  @AutoClose("dispose")
  private static final LX lx = new LX(new GridModel(8, 8));

  private static final Path DEFAULT_OVERLAY =
      Path.of(System.getProperty("user.home"), ".chromatik-mcp", "catalog");

  @AfterAll
  static void tearDown() {
    // Always restore default overlay dir in case a test leaves it pointing elsewhere
    Catalog.setOverlayDir(DEFAULT_OVERLAY);
  }

  // ── frontmatter / section parse round-trip ──────────────────────────────────

  @Test
  void parsesGradientPatternEntry() {
    Catalog.CatalogEntry entry = Catalog.locateEntry(lx, GradientPattern.class);
    assertNotNull(entry, "GradientPattern has a catalog entry");
    assertEquals("class-jar", entry.source());
    assertEquals("heronarts.lx.pattern.color.GradientPattern", entry.frontmatter().get("class"));
    assertEquals("pattern", entry.frontmatter().get("kind"));
    assertNotNull(entry.summary(), "summary is present");
    assertFalse(entry.summary().isEmpty());
    assertNotNull(entry.parameterInteractions(), "parameterInteractions is present");
    assertNotNull(entry.usageTips(), "usageTips is present");
  }

  @Test
  void parseFrontmatterRoundTrip() {
    String content = """
        ---
        class: com.example.Foo
        kind: pattern
        lxVersion: 1.2.1
        generatedAt: 2026-07-09T00:00:00Z
        tags: color, gradient
        ---

        ## Summary

        A test summary paragraph.

        ## Parameter interactions

        Test interactions text.

        ## Usage tips

        Test tips text.
        """;
    Catalog.CatalogEntry entry = Catalog.parseEntry(content, "class-jar");
    assertEquals("com.example.Foo", entry.frontmatter().get("class"));
    assertEquals("pattern", entry.frontmatter().get("kind"));
    assertEquals("1.2.1", entry.frontmatter().get("lxVersion"));
    assertEquals("color, gradient", entry.frontmatter().get("tags"));
    assertEquals("A test summary paragraph.", entry.summary());
    assertEquals("Test interactions text.", entry.parameterInteractions());
    assertEquals("Test tips text.", entry.usageTips());
    assertEquals("class-jar", entry.source());
  }

  @Test
  void parseMissingOpeningDashesThrows() {
    assertThrows(IllegalArgumentException.class, () ->
        Catalog.parseEntry("class: Foo\nkind: pattern\n", "class-jar"));
  }

  // ── staleness matrix ────────────────────────────────────────────────────────

  @Test
  void stalenessNullOrEmptyHashIsUnknown() {
    assertEquals(Catalog.Staleness.UNKNOWN, Catalog.staleness(GradientPattern.class, null));
    assertEquals(Catalog.Staleness.UNKNOWN, Catalog.staleness(GradientPattern.class, ""));
  }

  @Test
  void stalenessMismatchedHashIsTrue() {
    // A hash that cannot match any real class bytes
    Catalog.Staleness result = Catalog.staleness(GradientPattern.class, "a".repeat(64));
    assertEquals(Catalog.Staleness.STALE, result);
  }

  @Test
  void stalenessMatchingHashIsFalse() {
    // Compute the actual bytecode hash so the fixture matches reality
    String actualHash = Catalog.computeBytesHash(GradientPattern.class);
    assertNotNull(actualHash, "GradientPattern bytecode should be readable");
    assertEquals(64, actualHash.length(), "SHA-256 hex is 64 chars");
    assertTrue(actualHash.matches("[0-9a-f]{64}"), "all lowercase hex");

    Catalog.Staleness result = Catalog.staleness(GradientPattern.class, actualHash);
    assertEquals(Catalog.Staleness.FRESH, result);
  }

  @Test
  void computeBytesHashIsConsistent() {
    String h1 = Catalog.computeBytesHash(GradientPattern.class);
    String h2 = Catalog.computeBytesHash(GradientPattern.class);
    assertNotNull(h1);
    assertEquals(h1, h2, "hash is cached and stable");
  }

  @Test
  void computeBytesHashReturnsNullForBootstrapLoadedClass() {
    // Bootstrap-loaded classes have a null ClassLoader; must not NPE.
    String hash = Catalog.computeBytesHash(String.class);
    assertNull(hash, "bootstrap-loaded class bytecode is not readable");
  }

  // ── overlay tier shadows classpath entry ────────────────────────────────────

  @Test
  void overlayFilesShadowClasspathEntries() throws IOException {
    Path tempDir = Files.createTempDirectory("catalog-overlay-test");
    try {
      String overlayContent = """
          ---
          class: heronarts.lx.pattern.color.GradientPattern
          kind: pattern
          generatedAt: 2026-01-01T00:00:00Z
          lxVersion: 1.2.1
          tags: test-overlay
          ---

          ## Summary

          Overlay summary sentinel.

          ## Parameter interactions

          Overlay interactions sentinel.

          ## Usage tips

          Overlay tips sentinel.
          """;
      Files.writeString(
          tempDir.resolve("heronarts.lx.pattern.color.GradientPattern.md"),
          overlayContent);

      Catalog.setOverlayDir(tempDir);
      Catalog.CatalogEntry entry = Catalog.locateEntry(lx, GradientPattern.class);
      assertNotNull(entry);
      assertEquals("overlay", entry.source(), "overlay tier wins over class-jar");
      assertEquals("Overlay summary sentinel.", entry.summary());
    } finally {
      Catalog.setOverlayDir(DEFAULT_OVERLAY);
      Files.deleteIfExists(
          tempDir.resolve("heronarts.lx.pattern.color.GradientPattern.md"));
      Files.deleteIfExists(tempDir);
    }
  }

  // ── plugin-jar tier: stock LX classes loaded by LXClassLoader's *parent* ──────
  //
  // In production, Chromatik loads every package jar in one LXClassLoader whose
  // parent loaded heronarts.lx.* — so a stock LX class's own loader can never see
  // catalog/*.md bundled in this jar. A single-classpath test JVM can't reproduce
  // that asymmetry on its own, so these tests fake it with an isolated loader that
  // (like the real parent) cannot see catalog/ resources.

  @Test
  void locateEntryFallsBackToPluginJarWhenClassOwnLoaderCannotSeeIt() throws IOException {
    try (URLClassLoader isolated = new URLClassLoader(new URL[0], null)) {
      Catalog.CatalogEntry entry = Catalog.locateEntry(
          GradientPattern.class.getName(), isolated, Catalog.class.getClassLoader());
      assertNotNull(entry, "plugin-jar tier resolves entries the class's own loader can't see");
      assertEquals("plugin-jar", entry.source());
    }
  }

  @Test
  void locateEntryStillNullForUndocumentedClassViaIsolatedLoader() throws IOException {
    // SinLFO has no catalog entry anywhere — the plugin-jar fallback must not invent one.
    try (URLClassLoader isolated = new URLClassLoader(new URL[0], null)) {
      assertNull(Catalog.locateEntry(
          SinLFO.class.getName(), isolated, Catalog.class.getClassLoader()));
    }
  }

  @Test
  void locateEntryResolvesPluginJarForNullClassLoader() {
    // Bootstrap classes report null from getClassLoader(); must not NPE and must still
    // fall through to the plugin-jar tier.
    Catalog.CatalogEntry entry = Catalog.locateEntry(
        GradientPattern.class.getName(), null, Catalog.class.getClassLoader());
    assertNotNull(entry);
    assertEquals("plugin-jar", entry.source());
  }

  // ── findClass and hasEntry ───────────────────────────────────────────────────

  @Test
  void findClassLocatesRegisteredPattern() {
    Class<?> clazz = Catalog.findClass(lx, GradientPattern.class.getName());
    assertEquals(GradientPattern.class, clazz);
  }

  @Test
  void findClassThrowsForUnknownClass() {
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class, () ->
        Catalog.findClass(lx, "com.example.NonExistentPattern"));
    assertEquals(Resolve.Failure.NOT_FOUND, e.failure, "unchanged failure kind for unregistered classes");
  }

  @Test
  void findClassAcceptsShortName() {
    assertEquals(GradientPattern.class, Catalog.findClass(lx, "GradientPattern"));
    // Display name has the Pattern suffix stripped by LX's generic-superclass convention.
    assertEquals("Gradient", LXComponent.getComponentName(GradientPattern.class),
        "test assumption: display name strips the Pattern suffix");
    assertEquals(GradientPattern.class, Catalog.findClass(lx, "Gradient"));
  }

  @Test
  void findClassAmbiguousAcrossBucketsThrowsListingCandidates() {
    // Both stock classes share the display name "Sound Object" — one in patterns, one in
    // effects — so the ambiguity spans two of the three registry buckets Catalog merges.
    assertEquals("Sound Object", LXComponent.getComponentName(SoundObjectPattern.class));
    assertEquals("Sound Object", LXComponent.getComponentName(SoundObjectEffect.class));

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Catalog.findClass(lx, "Sound Object"));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains(SoundObjectPattern.class.getName()));
    assertTrue(e.getMessage().contains(SoundObjectEffect.class.getName()));

    // Full name still resolves unambiguously despite the display-name collision.
    assertEquals(SoundObjectPattern.class, Catalog.findClass(lx, SoundObjectPattern.class.getName()));
  }

  @Test
  void hasEntryTrueForDocumentedClass() {
    assertTrue(Catalog.hasEntry(lx, GradientPattern.class));
  }

  @Test
  void hasEntryFalseForUndocumentedClass() {
    // SinLFO is a modulator with no catalog entry in the chromatik-mcp jar
    assertFalse(Catalog.hasEntry(lx, SinLFO.class));
  }

  // hasEntry is list_available_*'s per-class check on the engine thread: it must find an
  // entry via a first-hit probe, not the full ranked resolution (which enumerates every
  // visible copy on both loaders and hashes live bytecode).

  @Test
  void hasEntryFastPathFindsPluginJarTierWithoutRanking() throws IOException {
    // Mirrors locateEntryFallsBackToPluginJarWhenClassOwnLoaderCannotSeeIt's setup, but
    // through the boolean seam list_available_* actually calls.
    try (URLClassLoader isolated = new URLClassLoader(new URL[0], null)) {
      assertTrue(Catalog.hasEntry(
          GradientPattern.class.getName(), isolated, Catalog.class.getClassLoader()));
    }
  }

  @Test
  void hasEntryFastPathFalseWhenNoTierHasIt() throws IOException {
    try (URLClassLoader isolated = new URLClassLoader(new URL[0], null)) {
      assertFalse(Catalog.hasEntry(
          SinLFO.class.getName(), isolated, Catalog.class.getClassLoader()));
    }
  }

  @Test
  void noEntryForAbsentOverlayDir() {
    // Point overlay at a nonexistent directory — should fall through to class-jar
    Catalog.setOverlayDir(Path.of("/nonexistent/chromatik-mcp/catalog"));
    try {
      Catalog.CatalogEntry entry = Catalog.locateEntry(lx, GradientPattern.class);
      assertNotNull(entry, "falls back to class-jar when overlay dir absent");
      assertEquals("class-jar", entry.source());
    } finally {
      Catalog.setOverlayDir(DEFAULT_OVERLAY);
    }
  }

  @Test
  void locateEntryNullForUndocumentedModulator() {
    assertNull(Catalog.locateEntry(lx, SinLFO.class));
  }

  // ── regression: tier 3 survives a Chromatik content reload (#121) ───────────
  //
  // Two tests, for a reason worth spelling out: `GradientPattern.class.getClassLoader()`
  // and `Catalog.class.getClassLoader()` are literally the *same* object in a flat Maven
  // test classpath (both are loaded by the JVM's application classloader), and that
  // classloader is never disposed by `reloadContent()`. So a test built entirely on a real
  // `LX` instance and a real stock class — however it's constructed — cannot observe the
  // difference between a *captured* loader reference and a *live* one: both always resolve.
  // `locateEntrySurvivesContentReload` still uses a real `LX` and a real `reloadContent()`
  // call (proving that path is headless-safe end to end, per docs/qa-strategy.md), with an
  // isolated loader standing in for the class's own loader (as in the isolated-loader tests
  // above) to force tier 2 to miss so tier 3 is exercised. `capturedPluginJarLoaderGoesStale`
  // is the test that actually proves the fix: it drives tier 3 through two independent,
  // directly-constructed loaders (own real URLs, no parent, so nothing is masked by
  // delegation) that stand in for "the loader before a reload" and "the loader a reload
  // installs" — mirroring `LXRegistry.reloadContent()` disposing and replacing
  // `this.classLoader` (LXRegistry.java:726, 738) — and shows that routing through a
  // *captured* reference to the first goes dark once it's disposed, while routing through
  // the *current* one (what `lx.registry.getClassLoader()` returns live) keeps resolving.

  @Test
  void locateEntrySurvivesContentReload() throws IOException {
    LX freshLx = new LX(new GridModel(8, 8));
    try (URLClassLoader isolated = new URLClassLoader(new URL[0], null)) {
      Catalog.CatalogEntry before = Catalog.locateEntry(
          GradientPattern.class.getName(), isolated, freshLx.registry.getClassLoader());
      assertNotNull(before, "plugin-jar tier resolves before any reload");
      assertEquals("plugin-jar", before.source());

      freshLx.registry.reloadContent();

      Catalog.CatalogEntry after = Catalog.locateEntry(
          GradientPattern.class.getName(), isolated, freshLx.registry.getClassLoader());
      assertNotNull(after, "plugin-jar tier still resolves after a real content reload");
      assertEquals("plugin-jar", after.source());
    } finally {
      freshLx.dispose();
    }
  }

  @Test
  void capturedPluginJarLoaderGoesStaleButLiveFetchSurvives() throws IOException {
    URL classesDir = Catalog.class.getProtectionDomain().getCodeSource().getLocation();
    URLClassLoader beforeReload = new URLClassLoader(new URL[] {classesDir}, null);
    try (URLClassLoader afterReload = new URLClassLoader(new URL[] {classesDir}, null)) {
      // Sanity check: before any "reload", the captured loader resolves tier 3 on its own
      // (own URLs, no parent) — the same mechanism the old `Catalog.class.getClassLoader()`
      // capture relied on in production.
      assertNotNull(Catalog.locateEntry(GradientPattern.class.getName(), null, beforeReload));

      beforeReload.close(); // what reloadContent() does to the loader it's replacing

      assertNull(
          Catalog.locateEntry(GradientPattern.class.getName(), null, beforeReload),
          "a captured loader reference goes dark once reloadContent() disposes it — #121");

      Catalog.CatalogEntry viaLive =
          Catalog.locateEntry(GradientPattern.class.getName(), null, afterReload);
      assertNotNull(viaLive, "re-fetching the current loader live survives the reload");
      assertEquals("plugin-jar", viaLive.source());
    } finally {
      beforeReload.close();
    }
  }

  // The tests above prove the 3-arg seam handles loader replacement correctly. They don't
  // touch `Catalog.pluginJarLoader(LX)` — the one line (`Catalog.java:103`) that feeds the
  // seam its loader in production. If that line were reverted to a captured reference (the
  // #121 bug), every test above would still pass. This test makes the wiring itself
  // assertable: `pluginJarLoader(lx)` must be the registry's *live* loader, distinct from
  // both the application classloader and its own value from before a reload.
  @Test
  void pluginJarLoaderIsTheLiveRegistryLoader() {
    ClassLoader viaProduction = Catalog.pluginJarLoader(lx);
    assertTrue(viaProduction == lx.registry.getClassLoader(),
        "pluginJarLoader(lx) must be exactly lx.registry.getClassLoader(), not a captured copy");
    assertFalse(viaProduction == Catalog.class.getClassLoader(),
        "pluginJarLoader(lx) must not be the application classloader");

    ClassLoader beforeReload = Catalog.pluginJarLoader(lx);
    lx.registry.reloadContent();
    ClassLoader afterReload = Catalog.pluginJarLoader(lx);

    assertTrue(afterReload == lx.registry.getClassLoader(),
        "pluginJarLoader(lx) must still track the live loader after a content reload");
    assertFalse(afterReload == beforeReload,
        "a content reload must install a new loader instance — the #121 mechanism");
  }

  // ── ranking: accuracy over proximity ────────────────────────────────────────
  //
  // Fixture fqcns below are never real, loadable classes — the ranking codepath never
  // instantiates or verifies the "bytecode" it hashes, it just reads whatever bytes sit at
  // <fqcn-as-path>.class and hashes them. That lets these tests fabricate exact-match /
  // non-match scenarios without a compiled class per case.

  @Test
  void rankingPrefersBytecodeMatchOverNewerNonMatchingCandidate() throws Exception {
    String fqcn = "test.catalog.BytesRankFixture";
    byte[] classBytes = "bytes-for-ranking-test".getBytes(StandardCharsets.UTF_8);
    String liveHash = sha256Hex(classBytes);

    Path classDir = newClassBytesDir(fqcn, classBytes);
    Path matchingDir = newCatalogDir(fqcn,
        fixture(fqcn, liveHash, "2020-01-01T00:00:00Z", "Matches bytecode."));
    Path nonMatchingDir = newCatalogDir(fqcn,
        fixture(fqcn, "d".repeat(64), "2030-01-01T00:00:00Z", "Newer but wrong bytes."));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {
        classDir.toUri().toURL(), matchingDir.toUri().toURL(), nonMatchingDir.toUri().toURL()
    }, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(fqcn, loader, loader);
      assertNotNull(result.winner());
      assertEquals("Matches bytecode.", result.winner().summary(),
          "an exact bytecode match wins even though the other candidate is newer");
      assertEquals(2, result.candidates().size());
      assertEquals("Matches bytecode.", result.candidates().get(0).entry().summary(),
          "ranked list is winner-first");
    } finally {
      deleteRecursively(classDir);
      deleteRecursively(matchingDir);
      deleteRecursively(nonMatchingDir);
    }
  }

  @Test
  void generatedAtBreaksTieWhenNeitherCandidateMatchesBytes() throws Exception {
    String fqcn = "test.catalog.NeitherMatchFixture";
    Path olderDir = newCatalogDir(fqcn, fixture(fqcn, "a".repeat(64), "2020-01-01T00:00:00Z", "Older."));
    Path newerDir = newCatalogDir(fqcn, fixture(fqcn, "b".repeat(64), "2030-01-01T00:00:00Z", "Newer."));
    // No class bytes anywhere on this loader, so the live hash is unresolvable and both
    // candidates report bytesMatch == false: generatedAt alone must decide.
    try (URLClassLoader loader = new URLClassLoader(
        new URL[] {olderDir.toUri().toURL(), newerDir.toUri().toURL()}, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(fqcn, loader, loader);
      assertEquals("Newer.", result.winner().summary());
    } finally {
      deleteRecursively(olderDir);
      deleteRecursively(newerDir);
    }
  }

  @Test
  void generatedAtBreaksTieWhenBothCandidatesMatchBytes() throws Exception {
    String fqcn = "test.catalog.BothMatchFixture";
    byte[] classBytes = "both-candidates-match".getBytes(StandardCharsets.UTF_8);
    String liveHash = sha256Hex(classBytes);

    Path classDir = newClassBytesDir(fqcn, classBytes);
    Path olderDir = newCatalogDir(fqcn, fixture(fqcn, liveHash, "2020-01-01T00:00:00Z", "Older match."));
    Path newerDir = newCatalogDir(fqcn, fixture(fqcn, liveHash, "2030-01-01T00:00:00Z", "Newer match."));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {
        classDir.toUri().toURL(), olderDir.toUri().toURL(), newerDir.toUri().toURL()
    }, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(fqcn, loader, loader);
      assertEquals("Newer match.", result.winner().summary(),
          "both candidates match bytecode identically, so generatedAt is the tiebreak");
    } finally {
      deleteRecursively(classDir);
      deleteRecursively(olderDir);
      deleteRecursively(newerDir);
    }
  }

  @Test
  void malformedCandidateIsSkippedRatherThanAbortingLookup() throws Exception {
    String fqcn = "test.catalog.MalformedFixture";
    Path malformedDir = newCatalogDir(fqcn, "this is not a catalog entry\nno frontmatter markers\n");
    Path validDir = newCatalogDir(fqcn,
        fixture(fqcn, "c".repeat(64), "2025-01-01T00:00:00Z", "Valid entry."));
    // Malformed candidate first, so a naive implementation that bails on the first parse
    // failure would never reach the valid one.
    try (URLClassLoader loader = new URLClassLoader(
        new URL[] {malformedDir.toUri().toURL(), validDir.toUri().toURL()}, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(fqcn, loader, loader);
      assertNotNull(result.winner(), "a malformed candidate must not deny service for a valid one");
      assertEquals("Valid entry.", result.winner().summary());
      assertEquals(1, result.candidates().size(),
          "the malformed candidate is excluded outright, not merely outranked");
    } finally {
      deleteRecursively(malformedDir);
      deleteRecursively(validDir);
    }
  }

  @Test
  void rankingIsDeterministicRegardlessOfEnumerationOrder() throws Exception {
    String fqcn = "test.catalog.DeterminismFixture";
    // Tie on bytesMatch (both false — no class bytes visible) and generatedAt (identical),
    // so only the URL tiebreak can decide: this isolates that enumeration order itself
    // (not the content of the winner) is what must stay stable.
    Path dirA = newCatalogDir(fqcn, fixture(fqcn, "e".repeat(64), "2025-01-01T00:00:00Z", "Candidate A."));
    Path dirB = newCatalogDir(fqcn, fixture(fqcn, "f".repeat(64), "2025-01-01T00:00:00Z", "Candidate B."));
    try {
      URL urlA = dirA.toUri().toURL();
      URL urlB = dirB.toUri().toURL();
      try (URLClassLoader forward = new URLClassLoader(new URL[] {urlA, urlB}, null);
          URLClassLoader reverse = new URLClassLoader(new URL[] {urlB, urlA}, null)) {
        Catalog.Resolution viaForwardOrder = Catalog.resolveCandidates(fqcn, forward, forward);
        Catalog.Resolution viaReverseOrder = Catalog.resolveCandidates(fqcn, reverse, reverse);

        assertEquals(2, viaForwardOrder.candidates().size());
        assertEquals(2, viaReverseOrder.candidates().size());
        assertEquals(viaForwardOrder.winner().summary(), viaReverseOrder.winner().summary(),
            "the winner must not depend on which order the loader enumerated candidates in");
      }
    } finally {
      deleteRecursively(dirA);
      deleteRecursively(dirB);
    }
  }

  @Test
  void overlayWinsOutrightButReportsTheCandidateItShadowed() throws IOException {
    Path tempDir = Files.createTempDirectory("catalog-overlay-candidates-test");
    try {
      String overlayContent = """
          ---
          class: heronarts.lx.pattern.color.GradientPattern
          kind: pattern
          generatedAt: 2026-01-01T00:00:00Z
          lxVersion: 1.2.1
          tags: test-overlay
          ---

          ## Summary

          Overlay summary sentinel.

          ## Parameter interactions

          Overlay interactions sentinel.

          ## Usage tips

          Overlay tips sentinel.
          """;
      Files.writeString(
          tempDir.resolve("heronarts.lx.pattern.color.GradientPattern.md"), overlayContent);

      Catalog.setOverlayDir(tempDir);
      Catalog.Resolution result = Catalog.resolveCandidates(
          GradientPattern.class.getName(), GradientPattern.class.getClassLoader(),
          Catalog.class.getClassLoader());
      assertEquals("overlay", result.winner().source(), "overlay still wins outright, not by ranking");
      assertTrue(result.candidates().size() > 1,
          "overlay reports the class-jar entry it shadowed rather than hiding it");
      assertEquals("overlay", result.candidates().get(0).source(),
          "overlay is always reported first, ranking or not");
      assertEquals(Catalog.Staleness.UNKNOWN, result.candidates().get(0).bytesMatch(),
          "no classBytesSha256 recorded on the hand-written overlay fixture — unknown, "
              + "not a false 'differs'");
    } finally {
      Catalog.setOverlayDir(DEFAULT_OVERLAY);
      Files.deleteIfExists(tempDir.resolve("heronarts.lx.pattern.color.GradientPattern.md"));
      Files.deleteIfExists(tempDir);
    }
  }

  // ── regression: candidate bytesMatch must agree with get_component_doc's per-class
  // staleness verdict, both backed by the one cached hash ──────────────────────────────
  //
  // Before #148, Catalog.rank computed its own liveHash uncached, while GetComponentDoc
  // separately recomputed bytesMatch from Catalog.computeBytesHash(Class) (the fqcn-keyed
  // static cache) — two independent computations that could disagree, most concretely
  // right after a Chromatik content reload replaces a class's loader without invalidating
  // that cache. Routing both through the one hash computed here (via the 4-arg
  // resolveCandidates a real Class enables) makes them the same value by construction.
  // GetComponentDoc's top-level `stale` field now reads candidates().get(0).bytesMatch()
  // directly (rather than a second Catalog.staleness call), so this test also protects
  // the invariant that makes that safe: see the merged-entry assertion below. This also
  // exercises Fix 5's replacement for the old
  // target/test-classes/ planting trick: a throwaway URLClassLoader stands in for a
  // second package jar shipping the same entry name, so nothing touches the build's own
  // classpath (a leftover planted directory there would shadow the real catalog for every
  // later, non-clean test run — see CatalogFormatTest.catalogFiles()).

  @Test
  void candidateBytesMatchAgreesWithGetComponentDocsPerClassStaleness() throws IOException {
    String fqcn = GradientPattern.class.getName();
    Path plantedDir = newCatalogDir(fqcn,
        fixture(fqcn, "0".repeat(64), "1999-01-01T00:00:00Z", "Planted competing entry."));
    try (URLClassLoader plantedLoader =
        new URLClassLoader(new URL[] {plantedDir.toUri().toURL()}, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(
          fqcn, GradientPattern.class.getClassLoader(), plantedLoader, GradientPattern.class);

      assertEquals(2, result.candidates().size());
      assertNotEquals("1999-01-01T00:00:00Z", result.winner().frontmatter().get("generatedAt"),
          "the deliberately old, wrong-bytes planted fixture must lose the ranking");

      Catalog.Candidate real = result.candidates().stream()
          .filter(c -> "class-jar".equals(c.source())).findFirst().orElseThrow();
      Catalog.Candidate planted = result.candidates().stream()
          .filter(c -> "plugin-jar".equals(c.source())).findFirst().orElseThrow();

      assertEquals("1999-01-01T00:00:00Z", planted.entry().frontmatter().get("generatedAt"),
          "the shadowed planted candidate is still reported, not hidden");
      assertEquals(Catalog.Staleness.STALE, planted.bytesMatch(),
          "the planted fixture's all-zero hash cannot match live GradientPattern bytecode");

      // get_component_doc's top-level `stale` field now reads
      // resolution.candidates().get(0).bytesMatch() directly rather than recomputing via
      // Catalog.staleness — correct only if mergeCandidates carries the winning candidate's
      // classBytesSha256 through untouched onto the merged entry. Assert that invariant
      // directly, rather than comparing two calls into the same compareHashes() helper
      // (which agree by construction and would make this assertion a tautology).
      assertEquals(
          real.entry().frontmatter().get("classBytesSha256"),
          result.merged().entry().frontmatter().get("classBytesSha256"),
          "merged entry must carry the winning candidate's classBytesSha256 through unchanged");
    } finally {
      deleteRecursively(plantedDir);
    }
  }

  // ── section-level merging ────────────────────────────────────────────────────
  //
  // Ranking still picks one winner (unchanged, tested above); merging is a graft step
  // applied after that winner is chosen. These fixtures deliberately put the curated
  // prose on the *losing* candidate, mirroring the real GradientPattern scenario this
  // exists to protect: once heronarts/LX#153 ships, an upstream-generated entry wins the
  // ranking on bytecode match or recency, but must not erase this plugin's hand-written
  // parameter-interaction/usage-tip prose.

  @Test
  void mergeGraftsCuratedSectionsFromStaleCandidateOntoFreshWinner() throws Exception {
    String fqcn = "test.catalog.MergeFreshWinsFixture";
    byte[] classBytes = "merge-fresh-wins".getBytes(StandardCharsets.UTF_8);
    String liveHash = sha256Hex(classBytes);

    Path classDir = newClassBytesDir(fqcn, classBytes);
    Path freshDir = newCatalogDir(fqcn, curatedFixture(fqcn, liveHash, "2030-01-01T00:00:00Z",
        "Fresh summary.", null, null, "Fresh interactions.", "Fresh tips."));
    Path staleDir = newCatalogDir(fqcn, curatedFixture(fqcn, "d".repeat(64), "2020-01-01T00:00:00Z",
        "Stale summary.", "parameterInteractions, usageTips", "2026-07-01T00:00:00Z",
        "Stale interactions.", "Stale tips."));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {
        classDir.toUri().toURL(), freshDir.toUri().toURL(), staleDir.toUri().toURL()
    }, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(fqcn, loader, loader);

      assertEquals("Fresh summary.", result.winner().summary(),
          "pre-merge winner is still the bytecode-matched candidate, unchanged");

      Catalog.CatalogEntry merged = result.merged().entry();
      assertEquals("Fresh summary.", merged.summary(), "Summary always tracks the winner's bytecode");
      assertEquals("Stale interactions.", merged.parameterInteractions(),
          "parameterInteractions is grafted from the candidate that curates it");
      assertEquals("Stale tips.", merged.usageTips(),
          "usageTips is grafted from the candidate that curates it");
      assertEquals("parameterInteractions, usageTips", merged.frontmatter().get("curated"),
          "merged frontmatter reports which sections are curated post-merge");
      assertEquals("2026-07-01T00:00:00Z", merged.frontmatter().get("curatedAt"));

      assertEquals(2, result.merged().grafts().size());
      for (Catalog.Graft graft : result.merged().grafts()) {
        assertEquals(catalogFileUrl(staleDir, fqcn), graft.url());
        assertFalse(graft.tied());
      }
    } finally {
      deleteRecursively(classDir);
      deleteRecursively(freshDir);
      deleteRecursively(staleDir);
    }
  }

  @Test
  void singleCandidateResolutionHasNoGrafts() {
    Catalog.Resolution result = Catalog.resolve(lx, GradientPattern.class);
    assertEquals(1, result.candidates().size());
    assertTrue(result.merged().grafts().isEmpty(),
        "a single visible copy has nothing to merge from");
    assertEquals(result.winner(), result.merged().entry(),
        "merging a single candidate is a no-op — merged entry equals the raw winner");
  }

  @Test
  void overlayWinsOutrightWithNoMergeEvenWhenAClasspathCandidateCuratesTheSameSection()
      throws IOException {
    Path tempDir = Files.createTempDirectory("catalog-overlay-merge-test");
    try {
      String overlayContent = """
          ---
          class: heronarts.lx.pattern.color.GradientPattern
          kind: pattern
          generatedAt: 2026-01-01T00:00:00Z
          lxVersion: 1.2.1
          tags: test-overlay
          ---

          ## Summary

          Overlay summary sentinel.

          ## Parameter interactions

          Overlay interactions sentinel.

          ## Usage tips

          Overlay tips sentinel.
          """;
      Files.writeString(
          tempDir.resolve("heronarts.lx.pattern.color.GradientPattern.md"), overlayContent);

      Catalog.setOverlayDir(tempDir);
      Catalog.Resolution result = Catalog.resolveCandidates(
          GradientPattern.class.getName(), GradientPattern.class.getClassLoader(),
          Catalog.class.getClassLoader());

      assertEquals("overlay", result.winner().source());
      assertTrue(result.merged().grafts().isEmpty(),
          "an overlay is an outright override, not a merge input");
      assertEquals("Overlay interactions sentinel.", result.merged().entry().parameterInteractions(),
          "the overlay's own content stands verbatim, even though the shadowed class-jar "
              + "candidate curates parameterInteractions/usageTips");
      assertEquals("Overlay tips sentinel.", result.merged().entry().usageTips());
    } finally {
      Catalog.setOverlayDir(DEFAULT_OVERLAY);
      Files.deleteIfExists(tempDir.resolve("heronarts.lx.pattern.color.GradientPattern.md"));
      Files.deleteIfExists(tempDir);
    }
  }

  @Test
  void mergeReportsATieWhenTwoCandidatesDeclareTheSameCuratedSection() throws Exception {
    String fqcn = "test.catalog.MergeTieFixture";
    byte[] classBytes = "merge-tie-fixture".getBytes(StandardCharsets.UTF_8);
    String liveHash = sha256Hex(classBytes);

    Path classDir = newClassBytesDir(fqcn, classBytes);
    Path freshDir = newCatalogDir(fqcn, curatedFixture(fqcn, liveHash, "2030-01-01T00:00:00Z",
        "Fresh summary.", null, null, "Fresh interactions.", "Fresh tips."));
    Path staleBDir = newCatalogDir(fqcn, curatedFixture(fqcn, "d".repeat(64), "2025-06-01T00:00:00Z",
        "Stale B summary.", "usageTips", "2026-01-01T00:00:00Z", "Stale B interactions.", "Stale B tips."));
    Path staleCDir = newCatalogDir(fqcn, curatedFixture(fqcn, "e".repeat(64), "2020-01-01T00:00:00Z",
        "Stale C summary.", "usageTips", "2020-01-01T00:00:00Z", "Stale C interactions.", "Stale C tips."));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {
        classDir.toUri().toURL(), freshDir.toUri().toURL(), staleBDir.toUri().toURL(), staleCDir.toUri().toURL()
    }, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(fqcn, loader, loader);

      assertEquals("Fresh summary.", result.winner().summary());
      // staleB ranks ahead of staleC (newer generatedAt, both bytecode-mismatched) — that
      // ranking order is what breaks the curated-section tie.
      assertEquals("Stale B tips.", result.merged().entry().usageTips());

      List<Catalog.Graft> usageTipsGrafts = result.merged().grafts().stream()
          .filter(g -> g.section().equals("usageTips")).toList();
      assertEquals(1, usageTipsGrafts.size());
      assertTrue(usageTipsGrafts.get(0).tied(),
          "two candidates both declared usageTips curated — the tie is reported");
      assertEquals(catalogFileUrl(staleBDir, fqcn), usageTipsGrafts.get(0).url(),
          "the ranking order's winner supplies the content and provenance");

      // parameterInteractions was never declared curated by anyone — the fresh winner's
      // own content stands, and no graft is reported for it.
      assertEquals("Fresh interactions.", result.merged().entry().parameterInteractions());
      assertTrue(result.merged().grafts().stream().noneMatch(g -> g.section().equals("parameterInteractions")));
    } finally {
      deleteRecursively(classDir);
      deleteRecursively(freshDir);
      deleteRecursively(staleBDir);
      deleteRecursively(staleCDir);
    }
  }

  @Test
  void curatedSectionNamingAnAbsentBodySectionIsSkippedNotAborted() throws Exception {
    String fqcn = "test.catalog.MergeMalformedCuratedFixture";
    byte[] classBytes = "merge-malformed-curated".getBytes(StandardCharsets.UTF_8);
    String liveHash = sha256Hex(classBytes);

    Path classDir = newClassBytesDir(fqcn, classBytes);
    Path freshDir = newCatalogDir(fqcn, curatedFixture(fqcn, liveHash, "2030-01-01T00:00:00Z",
        "Fresh summary.", null, null, "Fresh interactions.", "Fresh tips."));
    // Declares curated: usageTips but the document has no "## Usage tips" section at all —
    // a malformed entry that must be logged and skipped, not crash or deny the lookup.
    Path malformedDir = newCatalogDir(fqcn, curatedFixture(fqcn, "d".repeat(64), "2020-01-01T00:00:00Z",
        "Malformed summary.", "usageTips", "2026-01-01T00:00:00Z", "Malformed interactions.", null));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {
        classDir.toUri().toURL(), freshDir.toUri().toURL(), malformedDir.toUri().toURL()
    }, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(fqcn, loader, loader);

      assertEquals(2, result.candidates().size(),
          "the malformed curated declaration doesn't exclude the candidate itself from ranking");
      assertEquals("Fresh summary.", result.winner().summary());
      assertEquals("Fresh tips.", result.merged().entry().usageTips(),
          "a curated: usageTips naming an absent section is skipped, not grafted");
      assertTrue(result.merged().grafts().isEmpty());
    } finally {
      deleteRecursively(classDir);
      deleteRecursively(freshDir);
      deleteRecursively(malformedDir);
    }
  }

  @Test
  void unrecognizedCuratedTokenIsLoggedNotSilentlyStripped() throws Exception {
    String fqcn = "test.catalog.MergeUnrecognizedCuratedTokenFixture";
    byte[] classBytes = "merge-unrecognized-curated-token".getBytes(StandardCharsets.UTF_8);
    String liveHash = sha256Hex(classBytes);

    Path classDir = newClassBytesDir(fqcn, classBytes);
    // "summary" is not a curatable token (Summary must stay derivable from source) — this
    // is a malformed declaration that must be logged, the same as the absent-section case.
    Path onlyDir = newCatalogDir(fqcn, curatedFixture(fqcn, liveHash, "2026-01-01T00:00:00Z",
        "Summary.", "summary, usageTips", "2026-01-01T00:00:00Z", "Interactions.", "Tips."));
    PrintStream originalErr = System.err;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {
        classDir.toUri().toURL(), onlyDir.toUri().toURL()
    }, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(fqcn, loader, loader);

      assertEquals("Tips.", result.merged().entry().usageTips());
      assertEquals("usageTips", result.merged().entry().frontmatter().get("curated"),
          "the unrecognized 'summary' token is dropped from the re-served curated list");
    } finally {
      System.setErr(originalErr);
      deleteRecursively(classDir);
      deleteRecursively(onlyDir);
    }
    String logged = captured.toString(StandardCharsets.UTF_8);
    assertTrue(logged.contains("summary"),
        "an unrecognized curated: token must be logged, not silently stripped: " + logged);
  }

  @Test
  void curatedAtReflectsTheServedDeclarerNotTheMaxAcrossLosers() throws Exception {
    String fqcn = "test.catalog.MergeCuratedAtScopeFixture";
    byte[] classBytes = "merge-curated-at-scope".getBytes(StandardCharsets.UTF_8);
    String liveHash = sha256Hex(classBytes);

    Path classDir = newClassBytesDir(fqcn, classBytes);
    Path freshDir = newCatalogDir(fqcn, curatedFixture(fqcn, liveHash, "2030-01-01T00:00:00Z",
        "Fresh summary.", null, null, "Fresh interactions.", "Fresh tips."));
    // Ranked ahead of loserDir (newer generatedAt) and its curatedAt is older than the
    // loser's — the served curatedAt must reflect this candidate's timestamp, not the
    // loser's newer one, since the loser's prose is not what's served.
    Path winnerDir = newCatalogDir(fqcn, curatedFixture(fqcn, "d".repeat(64), "2025-06-01T00:00:00Z",
        "Winner summary.", "usageTips", "2020-01-01T00:00:00Z", "Winner interactions.", "Winner tips."));
    Path loserDir = newCatalogDir(fqcn, curatedFixture(fqcn, "e".repeat(64), "2020-01-01T00:00:00Z",
        "Loser summary.", "usageTips", "2026-01-01T00:00:00Z", "Loser interactions.", "Loser tips."));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {
        classDir.toUri().toURL(), freshDir.toUri().toURL(), winnerDir.toUri().toURL(), loserDir.toUri().toURL()
    }, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(fqcn, loader, loader);

      assertEquals("Winner tips.", result.merged().entry().usageTips(),
          "winnerDir ranks ahead of loserDir and supplies the served prose");
      assertEquals("2020-01-01T00:00:00Z", result.merged().entry().frontmatter().get("curatedAt"),
          "curatedAt must describe the served declarer, not the max across all declarers "
              + "including the loser whose prose was not served");
    } finally {
      deleteRecursively(classDir);
      deleteRecursively(freshDir);
      deleteRecursively(winnerDir);
      deleteRecursively(loserDir);
    }
  }

  @Test
  void curatedSectionWithEmptyHeadingIsSkippedNotGrafted() throws Exception {
    String fqcn = "test.catalog.MergeEmptyCuratedSectionFixture";
    byte[] classBytes = "merge-empty-curated-section".getBytes(StandardCharsets.UTF_8);
    String liveHash = sha256Hex(classBytes);

    Path classDir = newClassBytesDir(fqcn, classBytes);
    Path freshDir = newCatalogDir(fqcn, curatedFixture(fqcn, liveHash, "2030-01-01T00:00:00Z",
        "Fresh summary.", null, null, "Fresh interactions.", "Fresh tips."));
    // Declares curated: usageTips and has a "## Usage tips" heading, but nothing under it —
    // an empty section must be treated like an absent one, not graft "" over the base's
    // real prose.
    Path emptySectionDir = newCatalogDir(fqcn, curatedFixture(fqcn, "d".repeat(64), "2020-01-01T00:00:00Z",
        "Empty-section summary.", "usageTips", "2026-01-01T00:00:00Z", "Empty-section interactions.", ""));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {
        classDir.toUri().toURL(), freshDir.toUri().toURL(), emptySectionDir.toUri().toURL()
    }, null)) {
      Catalog.Resolution result = Catalog.resolveCandidates(fqcn, loader, loader);

      assertEquals(2, result.candidates().size(),
          "the empty curated declaration doesn't exclude the candidate itself from ranking");
      assertEquals("Fresh summary.", result.winner().summary());
      assertEquals("Fresh tips.", result.merged().entry().usageTips(),
          "a curated: usageTips whose heading has no content is skipped, not grafted as \"\"");
      assertTrue(result.merged().grafts().isEmpty());
    } finally {
      deleteRecursively(classDir);
      deleteRecursively(freshDir);
      deleteRecursively(emptySectionDir);
    }
  }

  private static String curatedFixture(String fqcn, String classBytesSha256, String generatedAt,
      String summary, String curated, String curatedAt,
      String parameterInteractions, String usageTips) {
    StringBuilder sb = new StringBuilder();
    sb.append("---\n");
    sb.append("class: ").append(fqcn).append("\n");
    sb.append("kind: pattern\n");
    sb.append("classBytesSha256: ").append(classBytesSha256).append("\n");
    sb.append("generatedAt: ").append(generatedAt).append("\n");
    sb.append("lxVersion: 1.2.1\n");
    if (curated != null) {
      sb.append("curated: ").append(curated).append("\n");
      sb.append("curatedAt: ").append(curatedAt).append("\n");
    }
    sb.append("---\n\n");
    sb.append("## Summary\n\n").append(summary).append("\n\n");
    if (parameterInteractions != null) {
      sb.append("## Parameter interactions\n\n").append(parameterInteractions).append("\n\n");
    }
    if (usageTips != null) {
      sb.append("## Usage tips\n\n").append(usageTips).append("\n\n");
    }
    return sb.toString();
  }

  private static String catalogFileUrl(Path dir, String fqcn) throws IOException {
    return dir.resolve("catalog").resolve(fqcn + ".md").toUri().toURL().toString();
  }

  private static Path newCatalogDir(String fqcn, String content) throws IOException {
    Path dir = Files.createTempDirectory("catalog-rank-test");
    Path catalogDir = Files.createDirectories(dir.resolve("catalog"));
    Files.writeString(catalogDir.resolve(fqcn + ".md"), content);
    return dir;
  }

  private static Path newClassBytesDir(String fqcn, byte[] classBytes) throws IOException {
    Path dir = Files.createTempDirectory("catalog-rank-test-class");
    Path classFile = dir.resolve(fqcn.replace('.', '/') + ".class");
    Files.createDirectories(classFile.getParent());
    Files.write(classFile, classBytes);
    return dir;
  }

  private static String fixture(String fqcn, String classBytesSha256, String generatedAt, String summary) {
    return """
        ---
        class: %s
        kind: pattern
        classBytesSha256: %s
        generatedAt: %s
        lxVersion: 1.2.1
        ---

        ## Summary

        %s

        ## Parameter interactions

        Interactions.

        ## Usage tips

        Tips.
        """.formatted(fqcn, classBytesSha256, generatedAt, summary);
  }

  private static String sha256Hex(byte[] bytes) throws NoSuchAlgorithmException {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
    StringBuilder sb = new StringBuilder(64);
    for (byte b : hash) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (root == null || !Files.exists(root)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          // Best-effort cleanup; leftover temp files don't affect test correctness.
        }
      });
    }
  }
}
