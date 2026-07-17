package chromatikmcp.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.pattern.LXPattern;

/**
 * Catalog primitives: locate, parse, and assess staleness of semantic documentation
 * entries for LX patterns, effects, and modulators.
 *
 * <p>Entries are markdown files with flat frontmatter and three fixed body sections.
 * Resolution follows a three-tier order (overlay dir → class's own classloader jar →
 * absent); see {@code docs/catalog-format.md} for the full spec.
 */
public final class Catalog {

  // Overlay dir — injectable via setOverlayDir for tests
  private static volatile Path overlayDir =
      Path.of(System.getProperty("user.home"), ".chromatik-mcp", "catalog");

  // Per-class bytecode hash caches (classes don't hot-swap at runtime)
  private static final Map<String, String> bytesHashCache = new ConcurrentHashMap<>();
  private static final Set<String> unreadableCache = ConcurrentHashMap.newKeySet();

  /** Flat frontmatter parsed from a catalog entry (key: value scalars). */
  public record Frontmatter(Map<String, String> values) {
    public String get(String key) {
      return values.get(key);
    }
  }

  /**
   * A parsed catalog entry: frontmatter plus the three fixed body sections.
   * Sections are trimmed; null means the heading was absent in the file.
   */
  public record CatalogEntry(
      Frontmatter frontmatter,
      String summary,
      String parameterInteractions,
      String usageTips,
      String source) {}

  private Catalog() {}

  // Package-private: lets tests inject a temp overlay dir without reflection
  static void setOverlayDir(Path dir) {
    overlayDir = dir;
  }

  /**
   * Resolves {@code className} across all three LX registry buckets (patterns, effects,
   * modulators): full class name, or a short name ({@code getSimpleName()} / display name)
   * unique across all three buckets combined — a short name ambiguous across buckets (or
   * within one) is rejected rather than silently picked. Throws
   * {@link Resolve.ResolveException}(NOT_FOUND) if not registered at all,
   * {@link Resolve.ResolveException}(TYPE_MISMATCH) if the short name is ambiguous.
   */
  public static Class<? extends LXComponent> findClass(LX lx, String className) {
    List<Class<? extends LXComponent>> all = new ArrayList<>();
    for (Class<? extends LXPattern> c : lx.registry.patterns) {
      all.add(c);
    }
    for (Class<? extends LXEffect> c : lx.registry.effects) {
      all.add(c);
    }
    for (Class<? extends LXModulator> c : lx.registry.modulators) {
      all.add(c);
    }
    return Resolve.resolveClassName(all, className, Resolve.Failure.NOT_FOUND,
        "Unknown class: " + className
            + " (not registered as a pattern, effect, or modulator)");
  }

  /**
   * Locates the catalog entry for {@code clazz} via three-tier resolution:
   * <ol>
   *   <li>~/.chromatik-mcp/catalog/&lt;fqcn&gt;.md (overlay — wins if present)</li>
   *   <li>clazz.getClassLoader().getResourceAsStream("catalog/&lt;fqcn&gt;.md") (class's jar)</li>
   *   <li>absent → returns null (class is undocumented)</li>
   * </ol>
   */
  public static CatalogEntry locateEntry(Class<?> clazz) {
    String filename = clazz.getName() + ".md";

    // Tier 1: overlay file (machine-local, always wins)
    Path overlay = overlayDir.resolve(filename);
    if (Files.exists(overlay)) {
      try {
        String content = Files.readString(overlay, StandardCharsets.UTF_8);
        return parseEntry(content, "overlay");
      } catch (IOException e) {
        LX.error(e, "Unreadable catalog overlay file: " + overlay);
        // Unreadable overlay falls through to tier 2
      }
    }

    // Tier 2: class's own classloader — finds entries in the class's jar and in the
    // chromatik-mcp jar (stock LX entries ship there; the classloader resolves both)
    try (InputStream in = clazz.getClassLoader().getResourceAsStream("catalog/" + filename)) {
      if (in != null) {
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return parseEntry(content, "class-jar");
      }
    } catch (IOException e) {
      LX.error(e, "Unreadable catalog class-jar resource: catalog/" + filename);
    }

    return null;
  }

  /** Returns true if an entry exists at any resolution tier, false otherwise. */
  public static boolean hasEntry(Class<?> clazz) {
    return locateEntry(clazz) != null;
  }

  /** Staleness of a catalog entry relative to the current class bytecode. */
  public enum Staleness { FRESH, STALE, UNKNOWN }

  /**
   * Staleness of a catalog entry relative to the current class bytecode.
   *
   * @return {@link Staleness#FRESH} (bytecode identical — entry is trustworthy),
   *         {@link Staleness#STALE} (bytecode differs — regenerate), or
   *         {@link Staleness#UNKNOWN} (no recorded hash, or bytecode unreadable).
   */
  public static Staleness staleness(Class<?> clazz, String recordedHash) {
    if (recordedHash == null || recordedHash.isEmpty()) return Staleness.UNKNOWN;
    String current = computeBytesHash(clazz);
    if (current == null) return Staleness.UNKNOWN;
    return current.equals(recordedHash) ? Staleness.FRESH : Staleness.STALE;
  }

  /**
   * SHA-256 of the class's own bytecode, cached per class. Returns null if the
   * bytecode resource is unreadable. Package-private so tests can pre-compute
   * a hash for fixture entries.
   */
  public static String computeBytesHash(Class<?> clazz) {
    String fqcn = clazz.getName();
    if (unreadableCache.contains(fqcn)) return null;
    String cached = bytesHashCache.get(fqcn);
    if (cached != null) return cached;

    String resourcePath = fqcn.replace('.', '/') + ".class";
    try (InputStream in = clazz.getClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        unreadableCache.add(fqcn);
        return null;
      }
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
      StringBuilder sb = new StringBuilder(64);
      for (byte b : hash) sb.append(String.format("%02x", b));
      String result = sb.toString();
      bytesHashCache.put(fqcn, result);
      return result;
    } catch (IOException | NoSuchAlgorithmException e) {
      unreadableCache.add(fqcn);
      return null;
    }
  }

  /**
   * Parses a catalog markdown file: flat frontmatter between {@code ---} markers,
   * then body split on the three fixed section headings.
   * Package-private so tests can verify round-trip fidelity.
   */
  static CatalogEntry parseEntry(String content, String source) {
    String[] lines = content.split("\n", -1);
    int i = 0;

    // Skip leading blank lines, then expect opening ---
    while (i < lines.length && lines[i].isBlank()) i++;
    if (i >= lines.length || !lines[i].trim().equals("---")) {
      throw new IllegalArgumentException("Missing opening --- in catalog entry");
    }
    i++;

    // Flat key: value frontmatter (no nesting, no YAML library)
    Map<String, String> fm = new LinkedHashMap<>();
    while (i < lines.length && !lines[i].trim().equals("---")) {
      String line = lines[i++];
      int colon = line.indexOf(':');
      if (colon > 0) {
        fm.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
      }
    }
    if (i < lines.length) i++; // skip closing ---

    StringBuilder bodyBuf = new StringBuilder();
    while (i < lines.length) {
      bodyBuf.append(lines[i++]).append('\n');
    }
    String body = bodyBuf.toString();

    return new CatalogEntry(
        new Frontmatter(fm),
        extractSection(body, "## Summary", "## Parameter interactions"),
        extractSection(body, "## Parameter interactions", "## Usage tips"),
        extractSection(body, "## Usage tips", null),
        source);
  }

  /** Extracts the trimmed content between two headings; null if the heading is absent. */
  private static String extractSection(String body, String heading, String nextHeading) {
    // Prepend \n so a heading at position 0 is also matched by "\n## " search
    String normalized = "\n" + body;
    String search = "\n" + heading;
    int headingPos = normalized.indexOf(search);
    if (headingPos < 0) return null;

    int lineEnd = normalized.indexOf('\n', headingPos + search.length());
    if (lineEnd < 0) return "";
    int contentStart = lineEnd + 1; // first char after the heading line

    int end = normalized.length();
    if (nextHeading != null) {
      int nextPos = normalized.indexOf("\n" + nextHeading, contentStart);
      if (nextPos >= 0) end = nextPos;
    }

    // Subtract 1 to map back to offsets in the original body (without the prepended \n)
    int bodyStart = contentStart - 1;
    int bodyEnd = end - 1;
    return body.substring(Math.max(0, bodyStart), Math.min(body.length(), bodyEnd)).trim();
  }
}
