package chromatikmcp.mcp;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import chromatikmcp.ConnectionSnapshot;

/**
 * LX-agnostic servlet {@link Filter} that observes MCP client activity on the embedded
 * Tomcat listener: open SSE streams and last-request timestamp. Mounted at {@code /*} by
 * {@link EmbeddedMcpServer} so every request (GET stream, POST call) passes through it.
 *
 * <p>Intentionally has no knowledge of LX or MCP message shapes — it only reads whether
 * the request went async — so it's unit testable without a running server.
 */
public final class ConnectionTracker implements Filter {

  /** A stream/session within this window of the last activity counts as "connected". */
  public static final long CONNECTED_WINDOW_MS = 60_000;

  private final AtomicInteger activeStreams = new AtomicInteger(0);
  private final AtomicLong lastActivityMs = new AtomicLong(0);

  /** Snapshot as of {@code nowMs} (caller-supplied for testability). */
  public ConnectionSnapshot snapshot(long nowMs) {
    int streams = this.activeStreams.get();
    long lastActivity = this.lastActivityMs.get();
    boolean connected = streams > 0
        || (lastActivity != 0 && (nowMs - lastActivity) < CONNECTED_WINDOW_MS);
    return new ConnectionSnapshot(streams, lastActivity, connected);
  }

  /**
   * Allocation-free equivalent of {@code snapshot(nowMs).connected()}, for the per-frame
   * engine loop task — {@link #snapshot} allocates a record and is reserved for the
   * on-demand {@code get_status} path.
   */
  public boolean isConnected(long nowMs) {
    int streams = this.activeStreams.get();
    long lastActivity = this.lastActivityMs.get();
    return streams > 0 || (lastActivity != 0 && (nowMs - lastActivity) < CONNECTED_WINDOW_MS);
  }

  /** Allocation-free equivalent of {@code snapshot(nowMs).lastActivityMs()}. */
  public long lastActivityMs() {
    return this.lastActivityMs.get();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    this.lastActivityMs.set(System.currentTimeMillis());

    chain.doFilter(request, response);

    if (request.isAsyncStarted()) {
      this.activeStreams.incrementAndGet();
      try {
        request.getAsyncContext().addListener(new StreamListener());
      } catch (IllegalStateException e) {
        // The async context already completed between the isAsyncStarted() check and
        // addListener() — undo the increment rather than leaking a phantom stream.
        this.activeStreams.decrementAndGet();
      }
    }
  }

  /**
   * Per the servlet spec, if no listener calls {@code complete()}/{@code dispatch()}
   * during {@code onTimeout}/{@code onError}, the container itself calls {@code
   * complete()} afterward, which fires {@code onComplete} on the same listener. So
   * {@code onComplete} is the only guaranteed-exactly-once completion signal — decrement
   * there only, or an errored/timed-out stream double-decrements and the counter goes
   * negative. {@code onTimeout}/{@code onError} just record activity.
   *
   * <p>{@code onStartAsync} fires when the async cycle is restarted (e.g. {@code
   * request.startAsync()} called again on the same request); listeners are NOT carried
   * forward across that restart per the servlet spec, so this listener must re-register
   * itself on the new {@link jakarta.servlet.AsyncContext} to keep observing completion.
   */
  private final class StreamListener implements AsyncListener {
    @Override
    public void onComplete(AsyncEvent event) {
      ConnectionTracker.this.activeStreams.decrementAndGet();
      ConnectionTracker.this.lastActivityMs.set(System.currentTimeMillis());
    }

    @Override
    public void onTimeout(AsyncEvent event) {
      ConnectionTracker.this.lastActivityMs.set(System.currentTimeMillis());
    }

    @Override
    public void onError(AsyncEvent event) {
      ConnectionTracker.this.lastActivityMs.set(System.currentTimeMillis());
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
      event.getAsyncContext().addListener(this);
    }
  }
}
