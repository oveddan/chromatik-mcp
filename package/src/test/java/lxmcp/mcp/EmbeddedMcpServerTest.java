package lxmcp.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * The PR-1a go/no-go gate: start the embedded MCP server in-process on an ephemeral
 * port, connect a real MCP client over streamable-HTTP, and complete the {@code
 * initialize} handshake. Passing proves the official Java MCP SDK embeds inside a
 * long-running JVM and serves MCP over HTTP — the core feasibility question.
 */
class EmbeddedMcpServerTest {

  @Test
  void initializeHandshakeReturnsServerInfo() {
    EmbeddedMcpServer server = EmbeddedMcpServer.start("LX-MCP", "0.0.1-test", 0);
    assertTrue(server.port() > 0, "server should bind an ephemeral port");
    try {
      HttpClientStreamableHttpTransport transport =
          HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
              .endpoint(EmbeddedMcpServer.ENDPOINT)
              .build();
      McpSyncClient client = McpClient.sync(transport).build();
      try {
        McpSchema.InitializeResult result = client.initialize();
        assertNotNull(result, "initialize() should return a result");
        assertNotNull(result.serverInfo(), "initialize result should carry server info");
        assertEquals("LX-MCP", result.serverInfo().name());
      } finally {
        client.closeGracefully();
      }
    } finally {
      server.stop();
    }
  }

  /**
   * PR-2 deliverable: the server advertises the tools capability, so a connected client's
   * {@code tools/list} succeeds. With no tools registered it returns an empty list (the
   * first real tool lands in PR-3). This is the over-the-wire proof of the capability.
   */
  @Test
  void listToolsReturnsEmptyOverHttp() {
    EmbeddedMcpServer server = EmbeddedMcpServer.start("LX-MCP", "0.0.1-test", 0, List.of());
    try {
      HttpClientStreamableHttpTransport transport =
          HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
              .endpoint(EmbeddedMcpServer.ENDPOINT)
              .build();
      McpSyncClient client = McpClient.sync(transport).build();
      try {
        client.initialize();
        McpSchema.ListToolsResult tools = client.listTools();
        assertNotNull(tools, "listTools() should return a result");
        assertTrue(tools.tools().isEmpty(), "no tools are registered in PR-2");
      } finally {
        client.closeGracefully();
      }
    } finally {
      server.stop();
    }
  }
}
