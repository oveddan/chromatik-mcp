package chromatikmcp.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;

import chromatikmcp.domain.OscParams;
import chromatikmcp.engine.EngineExecutor;

/**
 * End-to-end proof that {@code GET /osc-params} is reachable over real HTTP, alongside
 * the MCP endpoint on the same embedded Tomcat listener, and returns the flat wire shape
 * {@link OscParams} produces. Mirrors {@code ToolsIntegrationTest}'s drainer-thread
 * pattern: the headless harness never starts {@code lx.engine}, so a background thread
 * stands in for it to complete the blocking {@code EngineExecutor.call(...)} inside
 * {@code OscParamsServlet.doGet}.
 */
@Timeout(60)
class OscParamsServletTest {

  @AutoClose("dispose")
  private static LX lx;
  private static EmbeddedMcpServer server;
  private static final AtomicBoolean draining = new AtomicBoolean(true);
  private static Thread drainer;

  @BeforeAll
  static void setUp() {
    lx = new LX();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.label.setValue("Test Channel");

    drainer = new Thread(() -> {
      while (draining.get()) {
        lx.engine.run();
        try {
          Thread.sleep(2);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }, "test-engine-drainer");
    drainer.start();

    EngineExecutor engineExecutor = new EngineExecutor(lx);
    server = EmbeddedMcpServer.start(
        "Chromatik-MCP", "0.0.1-test", 0, "127.0.0.1", List.of(), null, new ConnectionTracker(),
        Map.of("/osc-params", new OscParamsServlet(lx, engineExecutor)));
  }

  @AfterAll
  static void tearDown() {
    draining.set(false);
    if (drainer != null) {
      try {
        drainer.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void getOscParamsReturnsJsonWithCountAndParams() throws IOException, InterruptedException {
    HttpClient http = HttpClient.newHttpClient();
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/osc-params"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals("application/json;charset=UTF-8", response.headers().firstValue("Content-Type").orElse(null));
    assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty(),
        "no CORS header — the Bitwig bridge proxies server-side, no browser needs it");
    String body = response.body();
    assertTrue(body.contains("\"count\""), "response should carry a count field: " + body);
    assertTrue(body.contains("\"params\""), "response should carry a params array: " + body);
    assertTrue(body.contains("oscAddress"), "at least one param entry should be present: " + body);
  }

  @Test
  void mcpEndpointIsUnaffectedByTheExtraMount() throws IOException, InterruptedException {
    HttpClient http = HttpClient.newHttpClient();
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/mcp"))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    // Not asserting on the MCP protocol response shape here (covered by
    // EmbeddedMcpServerTest) — just that /mcp still routes to the MCP servlet and isn't
    // shadowed or broken by the extra "/osc-params" mount.
    assertFalse(response.statusCode() == 404, "mcp endpoint must still be routed: " + response.body());
  }
}
