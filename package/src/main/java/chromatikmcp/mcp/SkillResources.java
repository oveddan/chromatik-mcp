package chromatikmcp.mcp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.domain.Skill;
import chromatikmcp.domain.SkillFile;

/**
 * Turns loaded {@link Skill}s into MCP {@code SyncResourceSpecification}s, one per file,
 * addressed per SEP-2640 (the MCP Skills extension): {@code skill://<name>/<relativePath>}.
 * This project has no path prefix ahead of the skill name (see SEP-2640's "Resource
 * Mapping" — a bare skill name is a valid one-segment {@code <skill-path>}), so the URI for
 * a skill's root file is always {@code skill://<name>/SKILL.md}.
 *
 * <p>Every served file here is static content read once at startup ({@link
 * chromatikmcp.domain.SkillLoader}), so every resource is registered with a fixed {@code
 * TextResourceContents} closed over at build time — the read handler never re-reads
 * anything.
 *
 * <p>Deliberately out of scope (see the PR that added this class): the SEP-2640 {@code
 * skills/list} / {@code skills/get} methods and the {@code extensions} capability. The MCP
 * Java SDK 2.0.0-RC1 has no seam for either — {@code ServerCapabilities} is a fixed record
 * and request handlers are private — so this class only does the transport-level half:
 * serving the files as plain resources. A client can still read every skill by URI, which
 * is the baseline SEP-2640 guarantees regardless of {@code skills/list} support.
 */
public final class SkillResources {

  private SkillResources() {}

  /** Builds one resource specification per file across every skill in {@code skills}. */
  public static List<McpServerFeatures.SyncResourceSpecification> specifications(
      List<Skill> skills) {
    List<McpServerFeatures.SyncResourceSpecification> specs = new ArrayList<>();
    for (Skill skill : skills) {
      for (SkillFile file : skill.files()) {
        specs.add(specification(skill, file));
      }
    }
    return List.copyOf(specs);
  }

  private static McpServerFeatures.SyncResourceSpecification specification(
      Skill skill, SkillFile file) {
    String uri = "skill://" + skill.name() + "/" + file.relativePath();
    boolean isSkillMd = file.relativePath().equals(SkillFile.SKILL_MD);
    String mimeType = mimeTypeFor(file.relativePath());
    String description = isSkillMd
        ? skill.description()
        : "Reference file for the " + skill.name() + " skill";
    // SKILL.md's name is the skill's own name (SEP-2640's Resource Metadata rule: it
    // equals the final <skill-path> segment); a supporting file's name is its path
    // relative to the skill root, which disambiguates same-named files under different
    // subdirectories better than a bare basename would.
    String name = isSkillMd ? skill.name() : file.relativePath();
    long sizeBytes = file.text().getBytes(StandardCharsets.UTF_8).length;

    McpSchema.Resource resource = McpSchema.Resource.builder(uri, name)
        .description(description)
        .mimeType(mimeType)
        .size(sizeBytes)
        .build();

    McpSchema.TextResourceContents contents =
        new McpSchema.TextResourceContents(uri, mimeType, file.text());
    McpSchema.ReadResourceResult result = new McpSchema.ReadResourceResult(List.of(contents));

    return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> result);
  }

  private static String mimeTypeFor(String relativePath) {
    // Every file here is served as TextResourceContents, so the fallback stays a text
    // type — application/octet-stream would misdescribe text as opaque binary.
    return relativePath.endsWith(".md") ? "text/markdown" : "text/plain";
  }
}
