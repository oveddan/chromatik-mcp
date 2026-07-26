package chromatikmcp.domain;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * Resolution has two stages: an overlay dir (machine-local override, wins outright if
 * present) and, failing that, every {@code catalog/<fqcn>.md} visible from the class's own
 * classloader or the shared plugin-jar loader (re-resolved live from the {@link LX} registry
 * on every call) — collected and ranked by accuracy (bytecode match, then recency, then a
 * deterministic tiebreak), not by which loader happened to report it first. See
 * {@code docs/catalog-format.md} for the full spec.
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

  /**
   * One resolution candidate considered for a class: where it was found ({@code source},
   * {@code url}), what it parsed to, and whether its recorded {@code classBytesSha256}
   * matches the live bytecode ({@link #rank} decided this once, against the same hash it
   * ranked with — callers read it here rather than recomputing it against a separately
   * cached hash, which is how the two could previously disagree). Every candidate that
   * parses successfully is reported, whether or not it won.
   */
  public record Candidate(String source, String url, CatalogEntry entry, Staleness bytesMatch) {}

  /**
   * A body section grafted onto the merged entry from a candidate other than the ranked
   * winner (or, when {@code tied} is true, a section two or more candidates each declared
   * {@code curated} for — reported even when the ranking order happens to resolve the tie
   * in the winner's own favor, since the tie itself is worth surfacing). {@code section} is
   * the {@code curated:} frontmatter token ({@code parameterInteractions} or {@code
   * usageTips}); {@code source}/{@code url} identify the candidate the grafted body came
   * from, same shape as {@link Candidate#source()}/{@link Candidate#url()}.
   */
  public record Graft(String section, String source, String url, boolean tied, boolean base) {}

  /**
   * The entry actually served after section-level merging: {@link #entry()}'s {@code
   * Summary} always comes from the ranked winner (or the overlay) unchanged — only {@code
   * Parameter interactions} and {@code Usage tips} are ever eligible for a graft, since
   * {@code curated:} never names {@code Summary} (docs/catalog-format.md). {@link
   * #grafts()} lists only the sections that actually came from (or were contested between)
   * a candidate other than the base; a section the base itself curates without contest
   * grafts onto itself, which is a no-op and is not reported. An overlay entry is never
   * merged into — it wins outright as an explicit override, so its own content stands
   * verbatim and {@link #grafts()} is always empty in that case.
   */
  public record Merged(CatalogEntry entry, List<Graft> grafts) {}

  /**
   * The outcome of resolving a class's catalog entry: the ranked (or overlay) winner
   * unmerged ({@link #winner()}, same value the pre-merge resolution always returned),
   * every candidate considered ({@link #candidates()}, winner first, then the rest ranked
   * most-significant first, each candidate's own parsed entry — never merged), and the
   * entry actually meant to be served ({@link #merged()}, the winner with any
   * candidate-declared curated section grafted on top). When an overlay is present it is
   * the winner by outright override, not by ranking, so it may be the least-accurate
   * element by the bytecode/recency criteria below; the overlay case is the only reason
   * "winner first" and "ranked first" can differ. {@code candidates} has more than one
   * element only when more than one copy of the entry was actually visible.
   */
  public record Resolution(CatalogEntry winner, List<Candidate> candidates, Merged merged) {}

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
   * Locates the catalog entry for {@code clazz}: the overlay file if present (verbatim, no
   * merge), otherwise the winner of {@link #resolve(LX, Class)}'s ranking with any
   * candidate-declared curated section grafted on top ({@link Resolution#merged()}). See
   * {@link #resolve(LX, Class)} to also see every candidate considered and which sections
   * were grafted from where.
   */
  public static CatalogEntry locateEntry(LX lx, Class<?> clazz) {
    return resolve(lx, clazz).merged().entry();
  }

  /**
   * Locates the catalog entry for {@code clazz} plus every candidate considered along the
   * way, ranked, plus the merged entry actually meant to be served. {@code winner()} is the
   * pre-merge ranked/overlay winner; {@code merged().entry()} is the same value {@link
   * #locateEntry(LX, Class)} returns.
   *
   * <p>Passes {@code clazz} through to the bytecode hash so it lands in the same
   * per-class cache {@link #computeBytesHash(Class)} (and therefore {@code get_component_doc}'s
   * top-level staleness fields) already use — ranking and the reported {@code bytesMatch}
   * can no longer disagree with each other, since both are the one cached value.
   */
  public static Resolution resolve(LX lx, Class<?> clazz) {
    return resolveCandidates(clazz.getName(), clazz.getClassLoader(), pluginJarLoader(lx), clazz);
  }

  /**
   * The plugin-jar fallback loader, re-fetched live from the registry on every call so it
   * survives a Chromatik content reload. Extracted so tests can assert the production wiring
   * feeds {@link #locateEntry(LX, Class)} the live loader rather than a captured one.
   */
  static ClassLoader pluginJarLoader(LX lx) {
    return lx.registry.getClassLoader();
  }

  /**
   * Package-private seam: {@code classLoader} is the documented class's own loader,
   * {@code pluginJarLoader} is the shared plugin-jar loader. Tests pass a {@code classLoader}
   * that cannot see {@code catalog/} to exercise the plugin-jar path — the case a
   * single-classpath test JVM never reproduces on its own. Thin wrapper over
   * {@link #resolveCandidates(String, ClassLoader, ClassLoader)} for callers that only want
   * the winner.
   */
  static CatalogEntry locateEntry(String fqcn, ClassLoader classLoader, ClassLoader pluginJarLoader) {
    return resolveCandidates(fqcn, classLoader, pluginJarLoader).merged().entry();
  }

  /**
   * Extended seam: same three inputs as {@link #locateEntry(String, ClassLoader, ClassLoader)},
   * but returns every candidate considered, ranked, not just the winner.
   *
   * <p>Resolution: the overlay file wins outright if present (an explicit machine-local
   * override), otherwise every visible {@code catalog/<fqcn>.md} copy — from both loaders,
   * deduplicated by URL — is parsed and ranked by {@link #rank}. A candidate that fails to
   * parse is logged and skipped; it cannot deny service for a valid candidate elsewhere.
   *
   * <p>No {@link Class} is available on this seam (tests exercise fqcns that were never
   * loaded), so the live bytecode hash backing each candidate's {@link Candidate#bytesMatch}
   * is computed uncached, once per call, against {@code classLoader} — see
   * {@link #computeBytesHash(String, ClassLoader)}. Production always has a real
   * {@link Class} and routes through {@link #resolveCandidates(String, ClassLoader,
   * ClassLoader, Class)} instead, which caches correctly (keyed by the class's own fqcn +
   * loader, same as {@link #computeBytesHash(Class)}).
   */
  static Resolution resolveCandidates(String fqcn, ClassLoader classLoader, ClassLoader pluginJarLoader) {
    return resolveCandidates(fqcn, classLoader, pluginJarLoader, null);
  }

  /**
   * The seam {@link #resolve(LX, Class)} delegates to once a real {@link Class} is known.
   * Package-private (rather than private) so tests can exercise the correctly-cached
   * production hash path with an otherwise-controlled {@code pluginJarLoader} — a real,
   * already-catalogued class plus a throwaway loader standing in for a second package jar,
   * the case a single-classpath test JVM can't reproduce with real duplicate resources.
   */
  static Resolution resolveCandidates(
      String fqcn, ClassLoader classLoader, ClassLoader pluginJarLoader, Class<?> clazzForHash) {
    String filename = fqcn + ".md";
    // Computed once, from whichever source is available, and threaded onto every
    // candidate below — the single value both ranking and the reported audit trail use,
    // so the two can never disagree the way a handler-side recomputation could (#Fix 1).
    String liveHash = clazzForHash != null
        ? computeBytesHash(clazzForHash)
        : computeBytesHash(fqcn, classLoader);

    // Overlay: machine-local, always wins outright if present
    Path overlay = overlayDir.resolve(filename);
    Candidate overlayCandidate = null;
    if (Files.exists(overlay)) {
      try {
        String content = Files.readString(overlay, StandardCharsets.UTF_8);
        CatalogEntry entry = parseEntry(content, "overlay");
        overlayCandidate =
            new Candidate("overlay", overlay.toUri().toString(), entry, bytesMatch(entry, liveHash));
      } catch (IOException e) {
        LX.error(e, "Unreadable catalog overlay file: " + overlay);
        // Unreadable overlay falls through to the classloader tiers
      } catch (IllegalArgumentException e) {
        LX.error(e, "Malformed catalog overlay entry: " + overlay);
      }
    }

    // Every copy visible from the class's own loader (tagged "class-jar") and the shared
    // plugin-jar loader (tagged "plugin-jar", i.e. every package jar Chromatik loads into one
    // LXClassLoader — not just this one). Deduplicated by URL: the two loaders overlap
    // whenever the class's own loader *is* the shared loader (content-package classes), and
    // delegation can otherwise surface the same physical resource from both.
    Set<String> seenUrls = new LinkedHashSet<>();
    List<Candidate> found = new ArrayList<>();
    collectCandidates(classLoader, filename, "class-jar", seenUrls, found, liveHash);
    collectCandidates(pluginJarLoader, filename, "plugin-jar", seenUrls, found, liveHash);

    List<Candidate> ranked = rank(found);

    if (overlayCandidate != null) {
      List<Candidate> all = new ArrayList<>(ranked.size() + 1);
      all.add(overlayCandidate);
      all.addAll(ranked);
      // Overlay wins outright as an explicit override — it is never merged into.
      return new Resolution(
          overlayCandidate.entry(), all, new Merged(overlayCandidate.entry(), List.of()));
    }

    CatalogEntry winner = ranked.isEmpty() ? null : ranked.get(0).entry();
    return new Resolution(winner, ranked, mergeCandidates(ranked));
  }

  /**
   * The two sections a {@code curated:} list may name (never {@code Summary} — see
   * {@link Merged}), paired with the {@link CatalogEntry} accessor that reads it. Order
   * here is the order sections are considered and, incidentally, the order
   * {@code docs/catalog-format.md}'s frontmatter example lists them in.
   */
  private enum CuratableSection {
    PARAMETER_INTERACTIONS("parameterInteractions", CatalogEntry::parameterInteractions),
    USAGE_TIPS("usageTips", CatalogEntry::usageTips);

    final String key;
    final java.util.function.Function<CatalogEntry, String> accessor;

    CuratableSection(String key, java.util.function.Function<CatalogEntry, String> accessor) {
      this.key = key;
      this.accessor = accessor;
    }
  }

  /**
   * The full set of valid {@code curated:} tokens, single-sourced from {@link
   * CuratableSection} so a test's own copy of this vocabulary can't drift from the one the
   * runtime actually grafts on. Package-private: only {@code CatalogFormatTest} needs it.
   */
  static Set<String> curatableSectionKeys() {
    Set<String> keys = new LinkedHashSet<>();
    for (CuratableSection section : CuratableSection.values()) {
      keys.add(section.key);
    }
    return keys;
  }

  /**
   * Grafts curated sections onto the ranked winner: for each {@link CuratableSection}, every
   * candidate (in ranked order, base included) that names it in {@code curated:} *and*
   * actually contains that section is a "declarer"; the first declarer in ranked order
   * supplies the section's final content. A candidate naming a section it doesn't contain is
   * malformed — logged via {@link LX#error} and excluded from the declarer list, not
   * aborting the whole lookup (consistent with how a malformed candidate file is handled
   * elsewhere in this class). A {@link Graft} is only recorded when the winning declarer
   * isn't the base, or when two or more candidates declared the same section (a tie the
   * ranking order resolved silently, which is still worth reporting) — a section the base
   * curates uncontested grafts onto itself and is not reported.
   */
  private static Merged mergeCandidates(List<Candidate> ranked) {
    if (ranked.isEmpty()) {
      return new Merged(null, List.of());
    }
    Candidate base = ranked.get(0);
    CatalogEntry baseEntry = base.entry();

    String parameterInteractions = baseEntry.parameterInteractions();
    String usageTips = baseEntry.usageTips();
    List<Graft> grafts = new ArrayList<>();
    Set<String> curatedKeys = new LinkedHashSet<>();
    String latestCuratedAt = null;

    Set<String> knownSectionKeys = curatableSectionKeys();
    for (Candidate candidate : ranked) {
      for (String token : curatedKeys(candidate.entry().frontmatter())) {
        if (!knownSectionKeys.contains(token)) {
          LX.error("Malformed catalog entry: " + candidate.url() + " curated: names "
              + "unrecognized section '" + token + "'; ignoring it");
        }
      }
    }

    for (CuratableSection section : CuratableSection.values()) {
      List<Candidate> declarers = new ArrayList<>();
      for (Candidate candidate : ranked) {
        if (!curatedKeys(candidate.entry().frontmatter()).contains(section.key)) {
          continue;
        }
        String sectionBody = section.accessor.apply(candidate.entry());
        if (sectionBody == null || sectionBody.isBlank()) {
          LX.error("Malformed catalog entry: " + candidate.url() + " declares curated section '"
              + section.key + "' but the section is "
              + (sectionBody == null ? "absent" : "empty") + "; skipping the graft");
          continue;
        }
        declarers.add(candidate);
      }
      if (declarers.isEmpty()) {
        continue; // base's own content (curated or not) stands as-is
      }

      Candidate winner = declarers.get(0);
      boolean tied = declarers.size() > 1;
      String content = section.accessor.apply(winner.entry());
      switch (section) {
        case PARAMETER_INTERACTIONS -> parameterInteractions = content;
        case USAGE_TIPS -> usageTips = content;
      }
      curatedKeys.add(section.key);
      // Only the winner's curatedAt is pooled: it's the only prose actually served for this
      // section. A losing declarer's curatedAt describes text that isn't in the response.
      String winnerCuratedAt = winner.entry().frontmatter().get("curatedAt");
      if (winnerCuratedAt != null
          && (latestCuratedAt == null || winnerCuratedAt.compareTo(latestCuratedAt) > 0)) {
        latestCuratedAt = winnerCuratedAt;
      }
      if (winner != base || tied) {
        grafts.add(new Graft(section.key, winner.source(), winner.url(), tied, winner == base));
      }
    }

    Map<String, String> mergedFrontmatter = new LinkedHashMap<>(baseEntry.frontmatter().values());
    if (curatedKeys.isEmpty()) {
      mergedFrontmatter.remove("curated");
      mergedFrontmatter.remove("curatedAt");
    } else {
      mergedFrontmatter.put("curated", String.join(", ", curatedKeys));
      if (latestCuratedAt != null) {
        mergedFrontmatter.put("curatedAt", latestCuratedAt);
      } else {
        mergedFrontmatter.remove("curatedAt");
      }
    }

    CatalogEntry mergedEntry = new CatalogEntry(
        new Frontmatter(mergedFrontmatter),
        baseEntry.summary(),
        parameterInteractions,
        usageTips,
        baseEntry.source());
    return new Merged(mergedEntry, grafts);
  }

  /** Parses a {@code curated:} frontmatter value into its comma-separated section tokens. */
  private static Set<String> curatedKeys(Frontmatter frontmatter) {
    String raw = frontmatter.get("curated");
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    Set<String> keys = new LinkedHashSet<>();
    for (String token : raw.split(",")) {
      String trimmed = token.trim();
      if (!trimmed.isEmpty()) {
        keys.add(trimmed);
      }
    }
    return keys;
  }

  /** Enumerates every {@code catalog/<filename>} resource visible from {@code loader}. */
  private static void collectCandidates(
      ClassLoader loader, String filename, String source,
      Set<String> seenUrls, List<Candidate> out, String liveHash) {
    if (loader == null) {
      return;
    }
    Enumeration<URL> resources;
    try {
      resources = loader.getResources("catalog/" + filename);
    } catch (IOException e) {
      LX.error(e, "Failed to enumerate catalog " + source + " resources: catalog/" + filename);
      return;
    }
    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();
      String key = url.toString();
      if (!seenUrls.add(key)) {
        continue; // same physical resource already captured via the other loader
      }
      try (InputStream in = url.openStream()) {
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        CatalogEntry entry = parseEntry(content, source);
        out.add(new Candidate(source, key, entry, bytesMatch(entry, liveHash)));
      } catch (IOException e) {
        LX.error(e, "Unreadable catalog " + source + " resource: " + url);
      } catch (IllegalArgumentException e) {
        LX.error(e, "Malformed catalog " + source + " entry: " + url);
      }
    }
  }

  /**
   * Ranks candidates most-significant first: an exact bytecode match beats everything (the
   * entry actually generated against the code now running), then the newest
   * {@code generatedAt} (missing treated as oldest), then a deterministic tiebreak on the
   * candidate's URL — enumeration order is never the deciding factor. Each candidate's
   * {@link Candidate#bytesMatch} was already decided when it was built (see
   * {@link #resolveCandidates(String, ClassLoader, ClassLoader, Class)}), so ranking only
   * reads it — this is the sole place the ordering criteria are applied, not a place that
   * (re)computes accuracy.
   */
  private static List<Candidate> rank(List<Candidate> candidates) {
    if (candidates.size() < 2) {
      return candidates;
    }
    List<Candidate> ranked = new ArrayList<>(candidates);
    ranked.sort(
        Comparator
            .comparing((Candidate c) -> c.bytesMatch() == Staleness.FRESH, Comparator.reverseOrder())
            .thenComparing((Candidate c) -> generatedAtOf(c), Comparator.reverseOrder())
            .thenComparing(Candidate::url));
    return ranked;
  }

  /**
   * Whether {@code entry}'s recorded {@code classBytesSha256} matches {@code liveHash}.
   * {@link Staleness#UNKNOWN} when either hash is absent — no recorded hash (e.g. a
   * hand-written overlay entry) and unreadable live bytecode are both "can't tell", not
   * "differs".
   */
  private static Staleness bytesMatch(CatalogEntry entry, String liveHash) {
    return compareHashes(entry.frontmatter().get("classBytesSha256"), liveHash);
  }

  private static Staleness compareHashes(String recordedHash, String liveHash) {
    if (recordedHash == null || recordedHash.isEmpty() || liveHash == null) {
      return Staleness.UNKNOWN;
    }
    return recordedHash.equals(liveHash) ? Staleness.FRESH : Staleness.STALE;
  }

  private static String generatedAtOf(Candidate candidate) {
    String generatedAt = candidate.entry().frontmatter().get("generatedAt");
    return generatedAt == null ? "" : generatedAt;
  }

  /**
   * True if a catalog entry exists for {@code clazz} at any resolution tier, without paying
   * the cost of resolving every candidate. {@code list_available_*} calls this once per
   * registered class on the engine thread (docs/tool-conventions.md) — it must stay a cheap
   * first-hit probe, not the full accuracy resolution {@link #resolve(LX, Class)} does (which
   * enumerates every visible copy on both loaders and hashes live bytecode once ranking is
   * needed). Existence doesn't care which copy is most accurate, so none of that applies here.
   */
  public static boolean hasEntry(LX lx, Class<?> clazz) {
    return hasEntry(clazz.getName(), clazz.getClassLoader(), pluginJarLoader(lx));
  }

  static boolean hasEntry(String fqcn, ClassLoader classLoader, ClassLoader pluginJarLoader) {
    String filename = fqcn + ".md";

    Path overlay = overlayDir.resolve(filename);
    if (Files.exists(overlay)) {
      try {
        parseEntry(Files.readString(overlay, StandardCharsets.UTF_8), "overlay");
        return true;
      } catch (IOException | IllegalArgumentException e) {
        // Unreadable/malformed overlay falls through to the classloader tiers, same as
        // full resolution.
      }
    }

    return firstVisibleEntryParses(classLoader, filename)
        || firstVisibleEntryParses(pluginJarLoader, filename);
  }

  /**
   * True if the first {@code catalog/<filename>} resource {@code loader} reports parses
   * successfully. Deliberately {@code getResourceAsStream} (first hit), not {@code
   * getResources} (every hit, the full classpath walk {@link #resolveCandidates} needs for
   * ranking) — existence needs only one valid copy, not all of them.
   */
  private static boolean firstVisibleEntryParses(ClassLoader loader, String filename) {
    if (loader == null) {
      return false;
    }
    try (InputStream in = loader.getResourceAsStream("catalog/" + filename)) {
      if (in == null) {
        return false;
      }
      parseEntry(new String(in.readAllBytes(), StandardCharsets.UTF_8), "probe");
      return true;
    } catch (IOException | IllegalArgumentException e) {
      return false;
    }
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
    return compareHashes(recordedHash, computeBytesHash(clazz));
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

    String result = computeBytesHash(fqcn, clazz.getClassLoader());
    if (result == null) {
      unreadableCache.add(fqcn);
    } else {
      bytesHashCache.put(fqcn, result);
    }
    return result;
  }

  /**
   * SHA-256 of the bytecode {@code loader} serves for {@code fqcn}, read the same way
   * {@link #computeBytesHash(Class)} does. Deliberately uncached, unlike the per-class
   * variant — but this is no longer ranking's hash source. Production always has a real
   * {@link Class} and calls {@link #computeBytesHash(Class)} instead (see
   * {@link #resolveCandidates(String, ClassLoader, ClassLoader, Class)}), which caches
   * correctly because {@code classLoader} reaching that path is always
   * {@code clazz.getClassLoader()} — the same loader the cache is keyed against. This
   * overload now only backs the {@code (String, ClassLoader, ClassLoader)} test seam, which
   * tests call with several distinct loaders against the same fqcn on purpose; caching by
   * fqcn alone there would leak a hash computed against one loader into a lookup against
   * another.
   */
  private static String computeBytesHash(String fqcn, ClassLoader loader) {
    if (loader == null) {
      return null;
    }
    String resourcePath = fqcn.replace('.', '/') + ".class";
    try (InputStream in = loader.getResourceAsStream(resourcePath)) {
      if (in == null) {
        return null;
      }
      return sha256Hex(in.readAllBytes());
    } catch (IOException e) {
      return null;
    }
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder sb = new StringBuilder(64);
      for (byte b : hash) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is a JVM-mandated MessageDigest algorithm; this cannot happen.
      throw new IllegalStateException("SHA-256 unavailable", e);
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
