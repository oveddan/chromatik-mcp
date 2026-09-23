package chromatikmcp.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads every Agent Skill (agentskills.io) bundled under {@code chromatikmcp/skills/},
 * once at startup, into an immutable in-memory model — never re-read per request, so a jar
 * replaced on disk under a running Chromatik can't serve half-old, half-new skill content
 * mid-session (see the stale-jar/hot-reload trap this project has hit before with catalog
 * resources).
 *
 * <p>Resolution is anchored to this class's own {@code CodeSource} — where the JVM actually
 * loaded {@code SkillLoader.class} from — rather than a {@code ClassLoader.getResource(...)}
 * lookup. A classpath resource lookup takes whichever entry resolves first, and reading each
 * file back with a second, independent lookup could then serve bytes from a different jar
 * than the one that was enumerated (a stale or duplicate chromatik-mcp jar on the classpath;
 * {@code Catalog} enumerates and ranks candidates for exactly this reason). Anchoring to our
 * own code source and reading every file from that one opened {@link JarFile} (or directory)
 * guarantees the served text is the text of the jar the running code actually came from.
 *
 * <p>A skill is a top-level directory directly under {@code chromatikmcp/skills/} containing
 * a {@code SKILL.md} at its root; every file beneath that directory (including nested
 * subdirectories such as {@code references/}) belongs to it. Nested skills (a second {@code
 * SKILL.md} in a descendant directory) are not supported — neither shipped skill needs one,
 * and SEP-2640's nested-skill semantics (fresh consent per activation) would need real design
 * work this PR doesn't do.
 */
public final class SkillLoader {

  private static final String ROOT = "chromatikmcp/skills";

  /** Agent Skills naming rule: lowercase alphanumeric segments joined by single hyphens. */
  private static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

  /** One {@code /}-separated path segment of a skill file. */
  private static final Pattern PATH_SEGMENT_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

  /** A YAML block-scalar indicator (`>`, `|`, optionally chomped with `-`/`+`). */
  private static final Pattern BLOCK_SCALAR_INDICATOR = Pattern.compile("^[|>][+-]?$");

  private SkillLoader() {}

  /** Loads every skill from the code source {@code SkillLoader.class} was itself loaded from. */
  public static List<Skill> loadAll() {
    URL location = SkillLoader.class.getProtectionDomain().getCodeSource().getLocation();
    return loadFrom(toPath(location));
  }

  /**
   * Package-private seam: {@code codeSourceRoot} is a jar file or an unpacked classes
   * directory (as {@code target/classes} is under Maven), matching what {@link #loadAll()}
   * resolves in production and in tests respectively. Exposed so tests can point it at a
   * hand-built temp jar without needing a custom {@code ClassLoader}.
   */
  static List<Skill> loadFrom(Path codeSourceRoot) {
    Map<String, String> filesByRelativePath = Files.isRegularFile(codeSourceRoot)
        ? readAllUnderJar(codeSourceRoot)
        : readAllUnderDirectory(codeSourceRoot.resolve(ROOT));

    Map<String, Map<String, String>> filesBySkill = new HashMap<>();
    for (Map.Entry<String, String> entry : filesByRelativePath.entrySet()) {
      String relativePath = entry.getKey();
      int slash = relativePath.indexOf('/');
      if (slash < 0) {
        continue; // a file directly under the skills root, not inside a skill dir
      }
      String skillName = relativePath.substring(0, slash);
      String fileRelativePath = relativePath.substring(slash + 1);
      filesBySkill.computeIfAbsent(skillName, k -> new LinkedHashMap<>())
          .put(fileRelativePath, entry.getValue());
    }

    if (filesBySkill.isEmpty()) {
      throw new IllegalStateException(
          "No skill directories found under " + ROOT + " from code source " + codeSourceRoot
              + " — check that package/pom.xml's <resources> packaged agent-plugin/skills in.");
    }

    List<Skill> skills = new ArrayList<>();
    for (Map.Entry<String, Map<String, String>> entry : filesBySkill.entrySet()) {
      skills.add(buildSkill(entry.getKey(), entry.getValue()));
    }
    skills.sort(Comparator.comparing(Skill::name));
    return List.copyOf(skills);
  }

  /**
   * Builds one {@link Skill} from its already-read files ({@code relativePath -> text}).
   * Package-private and decoupled from code-source enumeration so tests can exercise
   * frontmatter and naming validation directly with a hand-built map, without needing real
   * classpath or jar fixtures.
   */
  static Skill buildSkill(String skillName, Map<String, String> filesByRelativePath) {
    if (!SKILL_NAME_PATTERN.matcher(skillName).matches()) {
      throw new IllegalStateException(
          "Skill directory name '" + skillName + "' is not a valid Agent Skills name "
              + "(expected " + SKILL_NAME_PATTERN.pattern() + ")");
    }

    String skillMdText = filesByRelativePath.get(SkillFile.SKILL_MD);
    if (skillMdText == null) {
      throw new IllegalStateException(
          "Skill '" + skillName + "' has no " + SkillFile.SKILL_MD + " at its root (" + ROOT
              + "/" + skillName + "/" + SkillFile.SKILL_MD
              + " is required by the Agent Skills specification)");
    }

    Map<String, String> frontmatter = parseFrontmatter(skillMdText, skillName);
    String declaredName = frontmatter.get("name");
    if (declaredName == null || declaredName.isBlank()) {
      throw new IllegalStateException(
          SkillFile.SKILL_MD + " for '" + skillName + "' is missing the required 'name' "
              + "frontmatter field");
    }
    if (!declaredName.equals(skillName)) {
      throw new IllegalStateException(
          SkillFile.SKILL_MD + " frontmatter name '" + declaredName + "' does not match its "
              + "directory name '" + skillName + "' (" + ROOT + "/" + skillName + "/"
              + SkillFile.SKILL_MD + ") — the Agent Skills specification requires them to agree");
    }
    String description = frontmatter.get("description");
    if (description == null || description.isBlank()) {
      throw new IllegalStateException(
          SkillFile.SKILL_MD + " for '" + skillName + "' is missing the required "
              + "'description' frontmatter field");
    }

    List<SkillFile> files = new ArrayList<>();
    for (String relativePath : orderedPaths(filesByRelativePath.keySet())) {
      validateRelativePath(skillName, relativePath);
      files.add(new SkillFile(relativePath, filesByRelativePath.get(relativePath)));
    }
    return new Skill(skillName, description, List.copyOf(files));
  }

  /** {@code SKILL.md} first, then every other file in alphabetical order. */
  private static List<String> orderedPaths(java.util.Set<String> relativePaths) {
    List<String> sorted = new ArrayList<>(relativePaths);
    sorted.sort((a, b) -> {
      boolean aRoot = a.equals(SkillFile.SKILL_MD);
      boolean bRoot = b.equals(SkillFile.SKILL_MD);
      if (aRoot != bRoot) {
        return aRoot ? -1 : 1;
      }
      return a.compareTo(b);
    });
    return sorted;
  }

  /**
   * A file path must be {@code /}-separated segments of {@code [A-Za-z0-9._-]+}, excluding
   * {@code .}/{@code ..} (which that character class alone would otherwise admit as a path
   * traversal), so {@code chromatikmcp.mcp.SkillResources} can build {@code skill://} URIs by
   * plain concatenation without re-validating.
   */
  private static void validateRelativePath(String skillName, String relativePath) {
    for (String segment : relativePath.split("/", -1)) {
      if (segment.equals(".") || segment.equals("..")
          || !PATH_SEGMENT_PATTERN.matcher(segment).matches()) {
        throw new IllegalStateException(
            "Skill '" + skillName + "' file path '" + relativePath + "' has an invalid "
                + "segment '" + segment + "' (expected /-separated [A-Za-z0-9._-]+ segments)");
      }
    }
  }

  /**
   * Flat {@code key: value} frontmatter between a leading and trailing {@code ---} line, in
   * the same spirit as {@code chromatikmcp.domain.Catalog}'s catalog entries but not shared
   * with it: this parser is deliberately stricter (an unterminated block, a continuation line,
   * or a YAML block-scalar value are all load failures here, where {@code Catalog} tolerates
   * an unterminated block), and AGENTS.md's extraction rule wants a third caller before that
   * divergence gets papered over with a shared helper.
   */
  private static Map<String, String> parseFrontmatter(String content, String skillName) {
    String[] lines = content.split("\n", -1);
    int i = 0;
    while (i < lines.length && lines[i].isBlank()) {
      i++;
    }
    if (i >= lines.length || !lines[i].trim().equals("---")) {
      throw new IllegalStateException(
          SkillFile.SKILL_MD + " for '" + skillName + "' is missing YAML frontmatter "
              + "(expected a leading '---' line)");
    }
    i++;

    Map<String, String> frontmatter = new LinkedHashMap<>();
    boolean closed = false;
    while (i < lines.length) {
      String line = lines[i++];
      if (line.trim().equals("---")) {
        closed = true;
        break;
      }
      if (line.isBlank()) {
        continue;
      }
      int colon = line.indexOf(':');
      if (colon <= 0 || Character.isWhitespace(line.charAt(0))) {
        throw new IllegalStateException(
            SkillFile.SKILL_MD + " for '" + skillName + "' has a malformed frontmatter line "
                + "(expected 'key: value' at column 0, no wrapped continuations): " + line);
      }
      String key = line.substring(0, colon).trim();
      String value = line.substring(colon + 1).trim();
      if (BLOCK_SCALAR_INDICATOR.matcher(value).matches()) {
        throw new IllegalStateException(
            SkillFile.SKILL_MD + " for '" + skillName + "' field '" + key + "' uses a YAML "
                + "block-scalar indicator ('" + value + "'), which this flat frontmatter parser "
                + "does not support — use a single-line value instead");
      }
      frontmatter.put(key, value);
    }
    if (!closed) {
      throw new IllegalStateException(
          SkillFile.SKILL_MD + " for '" + skillName + "' has an unterminated frontmatter block "
              + "(no closing '---' line)");
    }
    return frontmatter;
  }

  /**
   * Reads every file under {@code skillsDir} (an unpacked directory, as {@code
   * target/classes/chromatikmcp/skills} is under Maven), keyed by its path relative to
   * {@code skillsDir}. An absent directory reads as empty rather than failing here — {@link
   * #loadFrom} reports that as part of its single "no skills found" check, alongside the
   * jar branch's equally-legitimate empty result.
   */
  private static Map<String, String> readAllUnderDirectory(Path skillsDir) {
    if (!Files.isDirectory(skillsDir)) {
      return Map.of();
    }
    Map<String, String> filesByRelativePath = new LinkedHashMap<>();
    try (Stream<Path> walk = Files.walk(skillsDir)) {
      for (Path path : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
        String relativePath = toUnixPath(skillsDir.relativize(path));
        filesByRelativePath.put(relativePath, Files.readString(path, StandardCharsets.UTF_8));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read skills under " + skillsDir, e);
    }
    return filesByRelativePath;
  }

  /**
   * Reads every {@code chromatikmcp/skills/**} entry from {@code jarPath} in one pass —
   * enumeration and content both come from this one opened {@link JarFile}, per this class's
   * doc — keyed by its path relative to {@code chromatikmcp/skills/}. Works whether or not
   * the jar carries explicit directory entries for {@code chromatikmcp/}, {@code
   * chromatikmcp/skills/}, etc.: only file entries are read, and directory entries (if
   * present) are skipped rather than relied on.
   */
  private static Map<String, String> readAllUnderJar(Path jarPath) {
    Map<String, String> filesByRelativePath = new LinkedHashMap<>();
    String prefix = ROOT + "/";
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
          continue;
        }
        String relativePath = entry.getName().substring(prefix.length());
        try (InputStream in = jarFile.getInputStream(entry)) {
          filesByRelativePath.put(relativePath, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read skills from jar " + jarPath, e);
    }
    return filesByRelativePath;
  }

  private static String toUnixPath(Path relative) {
    return relative.toString().replace(java.io.File.separatorChar, '/');
  }

  static Path toPath(URL url) {
    try {
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Malformed code source URL: " + url, e);
    }
  }
}
