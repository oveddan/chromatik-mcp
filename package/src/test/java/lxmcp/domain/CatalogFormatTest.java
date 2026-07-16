package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;

/**
 * Validates every {@code /catalog/*.md} classpath resource against the format spec
 * in {@code docs/catalog-format.md}: required frontmatter keys, correct field values,
 * hash format, ISO-8601 timestamp, three body sections, and registry resolution.
 *
 * <p>Files are enumerated via the filesystem path of the resources directory —
 * simpler than streaming classpath entries and equivalent for the in-source tree.
 */
class CatalogFormatTest {

  private static final Set<String> VALID_KINDS = Set.of("pattern", "effect", "modulator");

  @AutoClose("dispose")
  private static final LX lx = new LX(new GridModel(8, 8));

  static Stream<Path> catalogFiles() throws IOException {
    URL url = CatalogFormatTest.class.getClassLoader().getResource("catalog");
    assertNotNull(url, "catalog/ resource directory must exist on the classpath");
    Path dir = Path.of(url.getPath());
    return Files.list(dir).filter(p -> p.getFileName().toString().endsWith(".md")).sorted();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("catalogFiles")
  void fileConformsToFormat(Path file) throws IOException {
    String content = Files.readString(file);
    String filename = file.getFileName().toString(); // e.g. heronarts.lx.pattern.color.GradientPattern.md
    String fqcn = filename.substring(0, filename.length() - ".md".length());

    Catalog.CatalogEntry entry = Catalog.parseEntry(content, "class-jar");
    Catalog.Frontmatter fm = entry.frontmatter();

    // class field matches the filename (the only stable identity key)
    String recordedClass = fm.get("class");
    assertNotNull(recordedClass, "frontmatter must have 'class'");
    assertTrue(recordedClass.equals(fqcn),
        "class '" + recordedClass + "' must match filename '" + fqcn + "'");

    // kind is one of the three valid values
    String kind = fm.get("kind");
    assertNotNull(kind, "frontmatter must have 'kind'");
    assertTrue(VALID_KINDS.contains(kind),
        "kind '" + kind + "' must be one of " + VALID_KINDS);

    // classBytesSha256 is a 64-character lowercase hex string
    String hash = fm.get("classBytesSha256");
    assertNotNull(hash, "frontmatter must have 'classBytesSha256'");
    assertTrue(hash.matches("[0-9a-f]{64}"),
        "classBytesSha256 must be 64 lowercase hex chars, got: '" + hash + "'");

    // generatedAt parses as ISO-8601 instant
    String generatedAt = fm.get("generatedAt");
    assertNotNull(generatedAt, "frontmatter must have 'generatedAt'");
    assertDoesNotThrow(() -> Instant.parse(generatedAt),
        "generatedAt '" + generatedAt + "' must be an ISO-8601 instant");

    // All three body sections are present and non-empty
    assertNotNull(entry.summary(), "## Summary section must be present in " + filename);
    assertTrue(!entry.summary().isEmpty(), "summary must not be empty in " + filename);
    assertNotNull(entry.parameterInteractions(),
        "## Parameter interactions section must be present in " + filename);
    assertNotNull(entry.usageTips(),
        "## Usage tips section must be present in " + filename);

    // The recorded class must resolve in the headless LX registry
    assertDoesNotThrow(() -> Catalog.findClass(lx, fqcn),
        "class " + fqcn + " must be registered with LX");
  }
}
