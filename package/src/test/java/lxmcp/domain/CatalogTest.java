package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
      Path.of(System.getProperty("user.home"), ".lx-mcp", "catalog");

  @AfterAll
  static void tearDown() {
    // Always restore default overlay dir in case a test leaves it pointing elsewhere
    Catalog.setOverlayDir(DEFAULT_OVERLAY);
  }

  // ── frontmatter / section parse round-trip ──────────────────────────────────

  @Test
  void parsesGradientPatternEntry() {
    Catalog.CatalogEntry entry = Catalog.locateEntry(GradientPattern.class);
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
      Catalog.CatalogEntry entry = Catalog.locateEntry(GradientPattern.class);
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
    assertTrue(Catalog.hasEntry(GradientPattern.class));
  }

  @Test
  void hasEntryFalseForUndocumentedClass() {
    // SinLFO is a modulator with no catalog entry in the lx-mcp jar
    assertFalse(Catalog.hasEntry(SinLFO.class));
  }

  @Test
  void noEntryForAbsentOverlayDir() {
    // Point overlay at a nonexistent directory — should fall through to class-jar
    Catalog.setOverlayDir(Path.of("/nonexistent/lx-mcp/catalog"));
    try {
      Catalog.CatalogEntry entry = Catalog.locateEntry(GradientPattern.class);
      assertNotNull(entry, "falls back to class-jar when overlay dir absent");
      assertEquals("class-jar", entry.source());
    } finally {
      Catalog.setOverlayDir(DEFAULT_OVERLAY);
    }
  }

  @Test
  void locateEntryNullForUndocumentedModulator() {
    assertNull(Catalog.locateEntry(SinLFO.class));
  }
}
