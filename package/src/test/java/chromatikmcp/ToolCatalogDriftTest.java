package chromatikmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Fails the build gate when a tool's schema changes without regenerating the docs-site
 * catalog. The committed {@code site/src/data/tools.json} must always match what {@link
 * ToolCatalogDump} would emit right now.
 */
class ToolCatalogDriftTest {

  @Test
  void catalogMatchesCommittedSiteData() throws IOException {
    Path catalogPath = findRepoFile("site/src/data/tools.json");
    assumeTrue(catalogPath != null,
        "site/src/data/tools.json not found from cwd " + Path.of("").toAbsolutePath()
            + " — skipping drift check outside the full repo checkout");

    String committed = Files.readString(catalogPath);
    String current = ToolCatalogJson.catalogJson();
    assertEquals(current, committed,
        "Tool schemas changed — run package/scripts/dump-tool-catalog.sh then "
            + "`npm run tools-ref` in site/ and commit.");
  }

  /**
   * Walks up from {@code user.dir} looking for {@code relativePath}, stopping (and giving up)
   * once a {@code .git} entry is reached without having found it.
   */
  private static Path findRepoFile(String relativePath) {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (dir != null) {
      Path candidate = dir.resolve(relativePath);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      // A worktree's .git is a file (gitdir pointer), not a directory — either counts as
      // the repo root.
      if (Files.exists(dir.resolve(".git"))) {
        return null;
      }
      dir = dir.getParent();
    }
    return null;
  }
}
