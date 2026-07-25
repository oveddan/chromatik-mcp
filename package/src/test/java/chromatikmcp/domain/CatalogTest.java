package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
