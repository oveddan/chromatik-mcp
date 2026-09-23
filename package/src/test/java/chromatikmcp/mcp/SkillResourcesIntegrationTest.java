package chromatikmcp.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.domain.Skill;
import chromatikmcp.domain.SkillLoader;

/**
 * The resources half of SEP-2640 (the MCP Skills extension) served over a real embedded
 * server: {@code EmbeddedMcpServer} is LX-agnostic, and so are the skills it serves, so
 * this needs no headless {@code LX} the way the tool-handler integration tests do.
 */
class SkillResourcesIntegrationTest {

  private static EmbeddedMcpServer server;
  private static McpSyncClient client;
  private static McpSchema.InitializeResult initializeResult;

  @BeforeAll
  static void setUp() {
    List<Skill> skills = SkillLoader.loadAll();
    server = EmbeddedMcpServer.start(
        "Chromatik-MCP", "0.0.1-test", 0, "127.0.0.1", List.of(),
        SkillResources.specifications(skills), null, new ConnectionTracker(), Map.of());

    HttpClientStreamableHttpTransport transport =
        HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
            .endpoint(EmbeddedMcpServer.ENDPOINT)
            .build();
    client = McpClient.sync(transport).build();
    initializeResult = client.initialize();
  }

  @AfterAll
  static void tearDown() {
    if (client != null) {
      client.closeGracefully();
    }
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void initializeAdvertisesResourcesCapability() {
    assertNotNull(initializeResult.capabilities().resources(),
        "a server serving skill:// resources must advertise the resources capability");
  }

  @Test
  void listResourcesReturnsEverySkillFileWithExpectedUrisAndMimeTypes() {
    McpSchema.ListResourcesResult listed = client.listResources();
    Map<String, McpSchema.Resource> byUri = listed.resources().stream()
        .collect(Collectors.toMap(McpSchema.Resource::uri, r -> r));

    assertEquals(
        Set.of(
            "skill://driving-chromatik/SKILL.md",
            "skill://driving-chromatik/references/addressing.md",
            "skill://driving-chromatik/references/composition.md",
            "skill://driving-chromatik/references/error-codes.md",
            "skill://driving-chromatik/references/recipes.md",
            "skill://project-profile/SKILL.md"),
        byUri.keySet());

    for (McpSchema.Resource resource : byUri.values()) {
      assertEquals("text/markdown", resource.mimeType(), resource.uri());
      assertNotNull(resource.description(), resource.uri());
    }

    McpSchema.Resource drivingSkillMd = byUri.get("skill://driving-chromatik/SKILL.md");
    assertEquals("driving-chromatik", drivingSkillMd.name());
  }

  @Test
  void readingDrivingChromatikSkillMdReturnsTheExactFileText() throws IOException {
    Path sourceRoot = findRepoDir("agent-plugin/skills");
    assumeTrue(sourceRoot != null,
        "agent-plugin/skills not found from cwd " + Path.of("").toAbsolutePath()
            + " — skipping outside the full repo checkout");
    String expected = Files.readString(
        sourceRoot.resolve("driving-chromatik").resolve("SKILL.md"));

    McpSchema.ReadResourceResult result = client.readResource(
        new McpSchema.ReadResourceRequest("skill://driving-chromatik/SKILL.md"));
    assertEquals(1, result.contents().size());
    McpSchema.TextResourceContents contents =
        assertInstanceOf(McpSchema.TextResourceContents.class, result.contents().get(0));
    assertEquals("text/markdown", contents.mimeType());
    assertEquals(expected, contents.text());
  }

  @Test
  void readingAnUnknownSkillUriReturnsAnError() {
    assertThrows(McpError.class, () -> client.readResource(
        new McpSchema.ReadResourceRequest("skill://does-not-exist/SKILL.md")));
  }

  @Test
  void emptyResourceListDoesNotAdvertiseResourcesCapability() {
    EmbeddedMcpServer emptyServer = EmbeddedMcpServer.start(
        "Chromatik-MCP", "0.0.1-test", 0, "127.0.0.1", List.of(), List.of(), null,
        new ConnectionTracker(), Map.of());
    try {
      HttpClientStreamableHttpTransport transport =
          HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + emptyServer.port())
              .endpoint(EmbeddedMcpServer.ENDPOINT)
              .build();
      McpSyncClient emptyClient = McpClient.sync(transport).build();
      try {
        McpSchema.InitializeResult emptyResult = emptyClient.initialize();
        assertEquals(null, emptyResult.capabilities().resources(),
            "when no resources are registered, the resources capability should not be advertised");
      } finally {
        emptyClient.closeGracefully();
      }
    } finally {
      emptyServer.stop();
    }
  }

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
