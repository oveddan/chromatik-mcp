package chromatikmcp.mcp;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import heronarts.lx.LX;

import io.modelcontextprotocol.json.McpJsonDefaults;

import chromatikmcp.domain.OscParams;
import chromatikmcp.engine.EngineExecutor;

/**
 * Plain REST endpoint {@code GET /osc-params}: the full OSC address space of the running
 * engine as JSON, for external OSC senders (the Bitwig OSC bridge's click-to-bind picker,
 * a future VST/M4L device). Deliberately not an MCP tool — callers are simple HTTP
 * clients, not MCP sessions.
 *
 * <p>Read-only and loopback-bound by default (same connector as {@code /mcp}). The Bitwig
 * bridge proxies this endpoint server-side, so no browser needs CORS — no
 * {@code Access-Control-Allow-Origin} header is sent.
 */
public final class OscParamsServlet extends HttpServlet {

  private final LX lx;
  private final EngineExecutor executor;

  public OscParamsServlet(LX lx, EngineExecutor executor) {
    this.lx = lx;
    this.executor = executor;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String body;
    int status;
    try {
      List<Map<String, Object>> params = this.executor.call(() -> OscParams.list(this.lx));
      body = McpJsonDefaults.getMapper()
          .writeValueAsString(Map.of("count", params.size(), "params", params));
      status = HttpServletResponse.SC_OK;
    } catch (RuntimeException | IOException e) {
      String message = (e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage();
      body = McpJsonDefaults.getMapper().writeValueAsString(Map.of("error", message));
      status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(body);
  }
}
