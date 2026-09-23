package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the loader against the real {@code agent-plugin/skills} content packaged into
 * {@code target/classes} by {@code package/pom.xml}'s resource declaration, the code-source
 * seam ({@link SkillLoader#loadFrom}) against hand-built temp directories and jars, and the
 * frontmatter/naming validation seam ({@link SkillLoader#buildSkill}) directly, with
 * hand-built fixtures for the malformed cases real content never exercises.
 */
class SkillLoaderTest {

  @Test
  void loadAllFindsExactlyTheTwoShippedSkills() {
    List<Skill> skills = SkillLoader.loadAll();
    Set<String> names = skills.stream().map(Skill::name).collect(Collectors.toSet());
    assertEquals(Set.of("driving-chromatik", "project-profile"), names);
  }

  @Test
  void drivingChromatikHasSkillMdPlusFourReferences() {
    Skill skill = skillNamed("driving-chromatik");
    Set<String> relativePaths =
        skill.files().stream().map(SkillFile::relativePath).collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "SKILL.md",
            "references/addressing.md",
            "references/composition.md",
            "references/error-codes.md",
            "references/recipes.md"),
        relativePaths);
    assertEquals("SKILL.md", skill.files().get(0).relativePath(),
        "SKILL.md orders first among a skill's files");
  }

  @Test
  void projectProfileHasOnlySkillMd() {
    Skill skill = skillNamed("project-profile");
    assertEquals(List.of("SKILL.md"),
        skill.files().stream().map(SkillFile::relativePath).toList());
  }

  @Test
  void agentsDirIsNeverPackaged() {
    for (Skill skill : SkillLoader.loadAll()) {
      for (SkillFile file : skill.files()) {
        assertTrue(!file.relativePath().contains("agents/"),
            "agents/ is Codex plugin metadata, excluded from the jar by package/pom.xml: "
                + skill.name() + "/" + file.relativePath());
      }
    }
  }

  @Test
  void namesAndDescriptionsMatchFrontmatter() {
    Skill driving = skillNamed("driving-chromatik");
    assertEquals("driving-chromatik", driving.name());
    assertEquals(
        "House rules for driving a live Chromatik (LX Studio) lighting show over the "
            + "chromatik-mcp MCP server — connecting and recovering from restarts, "
            + "canonical-path addressing, timeout and error-code semantics, the shared undo "
            + "history and which mutations aren't undoable, consulting component docs before "
            + "reasoning about a pattern, and the mutate/look/adjust verification loop. Use "
            + "whenever calling chromatik MCP tools against a running instance.",
        driving.description());

    Skill profile = skillNamed("project-profile");
    assertEquals("project-profile", profile.name());
    assertEquals(
        "The format for a project profile — a derived record of how a specific live "
            + "Chromatik project actually uses patterns, effects, modulation, color, and "
            + "external control — and the rules for deriving one from a survey pass. Use when "
            + "writing or reading a profile produced by /chromatik-learn, when deciding "
            + "whether a survey finding is durable structure or momentary live state, or when "
            + "deciding whether an observation in one is strong enough to promote into a "
            + "shipped catalog entry.",
        profile.description());
  }

  @Test
  void fileTextMatchesSourceByteForByte() throws IOException {
    Path sourceRoot = findRepoDir("agent-plugin/skills");
    assumeTrue(sourceRoot != null,
        "agent-plugin/skills not found from cwd " + Path.of("").toAbsolutePath()
            + " — skipping outside the full repo checkout");

    for (Skill skill : SkillLoader.loadAll()) {
      for (SkillFile file : skill.files()) {
        Path sourceFile = sourceRoot.resolve(skill.name()).resolve(file.relativePath());
        String expected = Files.readString(sourceFile);
        assertEquals(expected, file.text(),
            "packaged text must match " + sourceFile + " byte for byte");
      }
    }
  }

  @Test
  void loadFromThrowsWhenTheSkillsDirectoryIsMissingEntirely(@TempDir Path tempDir) {
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.loadFrom(tempDir));
    assertTrue(thrown.getMessage().contains("No skill directories"));
  }

  @Test
  void loadFromThrowsWhenTheSkillsDirectoryIsPresentButEmpty(@TempDir Path tempDir)
      throws IOException {
    Files.createDirectories(tempDir.resolve("chromatikmcp/skills"));
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.loadFrom(tempDir));
    assertTrue(thrown.getMessage().contains("No skill directories"));
  }

  @Test
  void loadFromReadsAJarWithExplicitDirectoryEntries(@TempDir Path tempDir) throws IOException {
    assertJarSkillLoadsCorrectly(writeTestJar(tempDir, "with-dirs.jar", true));
  }

  @Test
  void loadFromReadsAJarWithoutExplicitDirectoryEntries(@TempDir Path tempDir) throws IOException {
    assertJarSkillLoadsCorrectly(writeTestJar(tempDir, "without-dirs.jar", false));
  }

  @Test
  void loadFromHandlesJarPathWithSpaceHashAndPercentEncoding(@TempDir Path tempDir)
      throws IOException {
    Path dirWithSpecialChars = tempDir.resolve("dir with space #1 100%");
    Files.createDirectories(dirWithSpecialChars);
    Path jarPath = writeTestJar(dirWithSpecialChars, "chromatik mcp.jar", false);

    // Test URL→Path round-trip: percent-encoding bugs live here. toUri() encodes
    // the space, #, and %, so this verifies they round-trip correctly.
    Path roundTripped = SkillLoader.toPath(jarPath.toUri().toURL());
    assertEquals(jarPath, roundTripped,
        "URL→Path round-trip must preserve paths with special characters");

    // Both the original path and round-tripped path must load the skill correctly.
    assertJarSkillLoadsCorrectly(jarPath);
    assertJarSkillLoadsCorrectly(roundTripped);
  }

  private static void assertJarSkillLoadsCorrectly(Path jarPath) {
    List<Skill> skills = SkillLoader.loadFrom(jarPath);
    assertEquals(1, skills.size());
    Skill skill = skills.get(0);
    assertEquals("jar-skill", skill.name());
    assertEquals("a skill loaded from a jar", skill.description());
    assertEquals(Set.of("SKILL.md", "references/x.md"),
        skill.files().stream().map(SkillFile::relativePath).collect(Collectors.toSet()));
  }

  private static Path writeTestJar(Path dir, String fileName, boolean includeDirEntries)
      throws IOException {
    Path jarPath = dir.resolve(fileName);
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
      if (includeDirEntries) {
        writeDirEntry(jar, "chromatikmcp/");
        writeDirEntry(jar, "chromatikmcp/skills/");
        writeDirEntry(jar, "chromatikmcp/skills/jar-skill/");
        writeDirEntry(jar, "chromatikmcp/skills/jar-skill/references/");
      }
      writeFileEntry(jar, "chromatikmcp/skills/jar-skill/SKILL.md",
          "---\nname: jar-skill\ndescription: a skill loaded from a jar\n---\nbody\n");
      writeFileEntry(jar, "chromatikmcp/skills/jar-skill/references/x.md", "reference content");
    }
    return jarPath;
  }

  private static void writeDirEntry(JarOutputStream jar, String name) throws IOException {
    jar.putNextEntry(new JarEntry(name));
    jar.closeEntry();
  }

  private static void writeFileEntry(JarOutputStream jar, String name, String content)
      throws IOException {
    jar.putNextEntry(new JarEntry(name));
    jar.write(content.getBytes(StandardCharsets.UTF_8));
    jar.closeEntry();
  }

  @Test
  void buildSkillThrowsWhenSkillMdIsMissing() {
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.buildSkill("no-root-file", Map.of("references/notes.md", "hi")));
    assertTrue(thrown.getMessage().contains("no-root-file"));
    assertTrue(thrown.getMessage().contains("SKILL.md"));
  }

  @Test
  void buildSkillThrowsWhenFrontmatterIsMissing() {
    Map<String, String> files = Map.of("SKILL.md", "# Just a heading, no frontmatter\n");
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.buildSkill("no-frontmatter", files));
    assertTrue(thrown.getMessage().contains("frontmatter"));
  }

  @Test
  void buildSkillThrowsWhenFrontmatterIsUnterminated() {
    Map<String, String> files = Map.of(
        "SKILL.md", "---\nname: unterminated\ndescription: no closing marker\n# body\n");
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.buildSkill("unterminated", files));
    assertTrue(thrown.getMessage().contains("unterminated"));
  }

  @Test
  void buildSkillThrowsOnAWrappedContinuationLine() {
    Map<String, String> files = Map.of("SKILL.md",
        "---\nname: wrapped\ndescription: a description that\n  continues on the next line\n"
            + "---\nbody\n");
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.buildSkill("wrapped", files));
    assertTrue(thrown.getMessage().contains("column 0"));
  }

  @Test
  void buildSkillThrowsOnAYamlBlockScalarValue() {
    Map<String, String> files = Map.of("SKILL.md",
        "---\nname: block-scalar\ndescription: >-\n  folded text\n---\nbody\n");
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.buildSkill("block-scalar", files));
    assertTrue(thrown.getMessage().contains("block-scalar indicator"));
  }

  @Test
  void buildSkillThrowsWhenNameDisagreesWithDirectory() {
    Map<String, String> files = Map.of(
        "SKILL.md", "---\nname: wrong-name\ndescription: a skill\n---\nbody\n");
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.buildSkill("actual-dir-name", files));
    assertTrue(thrown.getMessage().contains("wrong-name"));
    assertTrue(thrown.getMessage().contains("actual-dir-name"));
  }

  @Test
  void buildSkillThrowsWhenDescriptionIsMissing() {
    Map<String, String> files = Map.of(
        "SKILL.md", "---\nname: no-description\n---\nbody\n");
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.buildSkill("no-description", files));
    assertTrue(thrown.getMessage().contains("description"));
  }

  @Test
  void buildSkillThrowsOnInvalidNamesAndPathSegments() {
    IllegalStateException nameThrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.buildSkill("Not_Valid", Map.of()));
    assertTrue(nameThrown.getMessage().contains("Not_Valid"));

    IllegalStateException pathThrown = assertThrows(IllegalStateException.class,
        () -> SkillLoader.buildSkill("valid-name", Map.of(
            "SKILL.md", "---\nname: valid-name\ndescription: x\n---\nbody\n",
            "../escape.md", "sneaky")));
    assertTrue(pathThrown.getMessage().contains("../escape.md"));
  }

  @Test
  void buildSkillSucceedsWithValidMinimalFrontmatter() {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("SKILL.md", "---\nname: ok-skill\ndescription: does a thing\n---\nbody\n");
    files.put("references/notes.md", "some notes");
    Skill skill = SkillLoader.buildSkill("ok-skill", files);
    assertEquals("ok-skill", skill.name());
    assertEquals("does a thing", skill.description());
    assertEquals(List.of("SKILL.md", "references/notes.md"),
        skill.files().stream().map(SkillFile::relativePath).toList());
  }

  private static Skill skillNamed(String name) {
    return SkillLoader.loadAll().stream()
        .filter(skill -> skill.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No skill named " + name));
  }

  /**
   * Walks up from {@code user.dir} looking for {@code relativePath} as a directory, stopping
   * (and giving up) once a {@code .git} entry is reached without having found it. Mirrors
   * {@code ToolCatalogDriftTest.findRepoFile} for a directory instead of a single file.
   */
  private static Path findRepoDir(String relativePath) {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (dir != null) {
      Path candidate = dir.resolve(relativePath);
      if (Files.isDirectory(candidate)) {
        return candidate;
      }
      if (Files.exists(dir.resolve(".git"))) {
        return null;
      }
      dir = dir.getParent();
    }
    return null;
  }
}
