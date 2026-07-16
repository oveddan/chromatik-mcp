package lxmcp.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for {@link ConnectionTracker} against hand-rolled dynamic-proxy fakes of the
 * servlet API (no mocking library is on the classpath) — only the methods the filter
 * actually calls are wired; everything else returns a type-appropriate default.
 */
class ConnectionTrackerTest {

  private static HttpServletRequest fakeRequest(String method, Map<String, String> headers) {
    InvocationHandler handler = (proxy, invokedMethod, args) -> {
      switch (invokedMethod.getName()) {
        case "getMethod":
          return method;
        case "getHeader":
          return headers.get((String) args[0]);
        default:
          return defaultReturn(invokedMethod.getReturnType());
      }
    };
    return (HttpServletRequest) Proxy.newProxyInstance(
        ConnectionTrackerTest.class.getClassLoader(),
        new Class<?>[] {HttpServletRequest.class},
        handler);
  }

  private static ServletResponse fakeResponse() {
    InvocationHandler handler =
        (proxy, invokedMethod, args) -> defaultReturn(invokedMethod.getReturnType());
    return (ServletResponse) Proxy.newProxyInstance(
        ConnectionTrackerTest.class.getClassLoader(), new Class<?>[] {ServletResponse.class}, handler);
  }

  /** A chain that does nothing — the sync-request case. */
  private static FilterChain noopChain() {
    return (req, res) -> {};
  }

  /**
   * A request whose {@code isAsyncStarted()}/{@code getAsyncContext()} reflect the
   * caller-supplied mutable flag/holder — needed because the real filter reads these
   * *after* {@code chain.doFilter} runs.
   */
  private static HttpServletRequest asyncAwareRequest(
      String method, Map<String, String> headers, boolean[] asyncStarted, AsyncContext context) {
    InvocationHandler handler = (proxy, invokedMethod, args) -> {
      switch (invokedMethod.getName()) {
        case "getMethod":
          return method;
        case "getHeader":
          return headers.get((String) args[0]);
        case "isAsyncStarted":
          return asyncStarted[0];
        case "getAsyncContext":
          return context;
        default:
          return defaultReturn(invokedMethod.getReturnType());
      }
    };
    return (HttpServletRequest) Proxy.newProxyInstance(
        ConnectionTrackerTest.class.getClassLoader(),
        new Class<?>[] {HttpServletRequest.class},
        handler);
  }

  private static Object defaultReturn(Class<?> returnType) {
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == int.class) {
      return 0;
    }
    if (returnType == long.class) {
      return 0L;
    }
    return null;
  }

  @Test
  void syncPostBumpsLastActivity() throws IOException, ServletException {
    ConnectionTracker tracker = new ConnectionTracker();

    tracker.doFilter(fakeRequest("POST", Map.of()), fakeResponse(), noopChain());

    assertTrue(tracker.snapshot(System.currentTimeMillis()).lastActivityMs() > 0);
  }

  @Test
  void asyncRequestIncrementsActiveStreamsAndOnCompleteDecrements() throws IOException, ServletException {
    ConnectionTracker tracker = new ConnectionTracker();
    boolean[] asyncStarted = {false};
    AtomicReference<AsyncListener> capturedListener = new AtomicReference<>();
    InvocationHandler contextHandler = (proxy, invokedMethod, args) -> {
      if ("addListener".equals(invokedMethod.getName())) {
        capturedListener.set((AsyncListener) args[0]);
        return null;
      }
      return defaultReturn(invokedMethod.getReturnType());
    };
    AsyncContext context = (AsyncContext) Proxy.newProxyInstance(
        ConnectionTrackerTest.class.getClassLoader(), new Class<?>[] {AsyncContext.class}, contextHandler);
    ServletRequest request = asyncAwareRequest("GET", Map.of(), asyncStarted, context);
    FilterChain chain = (req, res) -> asyncStarted[0] = true;

    tracker.doFilter(request, fakeResponse(), chain);

    assertEquals(1, tracker.snapshot(System.currentTimeMillis()).activeStreams());
    assertTrue(tracker.snapshot(System.currentTimeMillis()).connected());

    capturedListener.get().onComplete(null);

    assertEquals(0, tracker.snapshot(System.currentTimeMillis()).activeStreams());
  }

  @Test
  void errorThenContainerCompleteDecrementsOnlyOnce() throws IOException, ServletException {
    ConnectionTracker tracker = new ConnectionTracker();
    boolean[] asyncStarted = {false};
    AtomicReference<AsyncListener> capturedListener = new AtomicReference<>();
    InvocationHandler contextHandler = (proxy, invokedMethod, args) -> {
      if ("addListener".equals(invokedMethod.getName())) {
        capturedListener.set((AsyncListener) args[0]);
        return null;
      }
      return defaultReturn(invokedMethod.getReturnType());
    };
    AsyncContext context = (AsyncContext) Proxy.newProxyInstance(
        ConnectionTrackerTest.class.getClassLoader(), new Class<?>[] {AsyncContext.class}, contextHandler);
    ServletRequest request = asyncAwareRequest("GET", Map.of(), asyncStarted, context);
    FilterChain chain = (req, res) -> asyncStarted[0] = true;

    tracker.doFilter(request, fakeResponse(), chain);
    assertEquals(1, tracker.snapshot(System.currentTimeMillis()).activeStreams());

    // Simulates a client killed mid-stream: onError fires first (must NOT decrement —
    // it only bumps activity), then per the servlet spec the container calls
    // complete() itself, firing onComplete on the same listener (the one, real
    // decrement).
    capturedListener.get().onError(null);
    assertEquals(1, tracker.snapshot(System.currentTimeMillis()).activeStreams(),
        "onError alone must not decrement — onComplete is the only guaranteed signal");

    capturedListener.get().onComplete(null);
    assertEquals(0, tracker.snapshot(System.currentTimeMillis()).activeStreams(),
        "net exactly one decrement across onError + the container's onComplete");
  }

  @Test
  void snapshotWindowDecay() throws IOException, ServletException {
    ConnectionTracker tracker = new ConnectionTracker();
    tracker.doFilter(fakeRequest("POST", Map.of()), fakeResponse(), noopChain());
    long lastActivity = tracker.snapshot(System.currentTimeMillis()).lastActivityMs();

    assertTrue(tracker.snapshot(lastActivity + 59_000).connected(), "within the 60s window");
    assertFalse(tracker.snapshot(lastActivity + 61_000).connected(), "past the 60s window");
  }

  @Test
  void activeStreamsAlwaysConnectedRegardlessOfWindow() throws IOException, ServletException {
    ConnectionTracker tracker = new ConnectionTracker();
    boolean[] asyncStarted = {false};
    AtomicReference<AsyncListener> capturedListener = new AtomicReference<>();
    InvocationHandler contextHandler = (proxy, invokedMethod, args) ->
        defaultReturn(invokedMethod.getReturnType());
    AsyncContext context = (AsyncContext) Proxy.newProxyInstance(
        ConnectionTrackerTest.class.getClassLoader(), new Class<?>[] {AsyncContext.class}, contextHandler);
    ServletRequest request = asyncAwareRequest("GET", Map.of(), asyncStarted, context);
    FilterChain chain = (req, res) -> asyncStarted[0] = true;

    tracker.doFilter(request, fakeResponse(), chain);
    long lastActivity = tracker.snapshot(System.currentTimeMillis()).lastActivityMs();

    assertTrue(tracker.snapshot(lastActivity + 120_000).connected(),
        "an open stream stays connected regardless of the activity window");
  }
}
