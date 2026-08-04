package chromatikmcp.engine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import heronarts.lx.LX;

/**
 * Marshals work onto the LX engine thread.
 *
 * <p>LX mutations must run on the single engine thread; under multi-agent fan-out,
 * parallel MCP requests arrive on independent Tomcat worker threads. This is the one
 * place that serializes them: every unit of work is enqueued via
 * {@code lx.engine.addTask(...)} (the only thread-safe entry point, {@code
 * LXEngine.java:846}) and drained, in order, at the top of the next engine cycle
 * ({@code LXEngine.java:1088}). Tool handlers wrap their domain-primitive call in
 * {@link #call(Supplier)} so the mutation happens on the engine thread and the result
 * comes back to the worker thread.
 *
 * <p>{@link #submit(Supplier)} is the non-blocking surface — it returns a {@link Future}
 * that completes when the engine drains the task — and is what tests exercise headlessly
 * by draining manually with {@code lx.engine.run()}. {@link #call(Supplier)} adds a
 * blocking await for live callers and must not be invoked from the engine thread itself
 * (it would wait for a drain that can't happen).
 */
public final class EngineExecutor {

  /**
   * Default bound for {@link #call(Supplier)} — and the only timeout in play: the MCP
   * SDK's servlet disables Tomcat's async timeout ({@code setTimeout(0)}). On timeout,
   * queued work is cancelled; work already claimed by the engine thread cannot be safely
   * interrupted and may still complete.
   */
  public static final long DEFAULT_TIMEOUT_MS = 30_000;

  private final LX lx;

  public EngineExecutor(LX lx) {
    this.lx = lx;
  }

  /**
   * Enqueue {@code work} to run on the engine thread; the returned future completes with
   * its result (or exceptionally with whatever it threw) once the engine drains the task.
   * Does not block.
   */
  public <T> Future<T> submit(Supplier<T> work) {
    EngineTask<T> task = new EngineTask<>(work);
    this.lx.engine.addTask(task);
    return task;
  }

  /** {@link #call(Supplier, long, TimeUnit)} with the {@link #DEFAULT_TIMEOUT_MS} bound. */
  public <T> T call(Supplier<T> work) {
    return call(work, DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /**
   * Run {@code work} on the engine thread and block (up to {@code timeout}) for its result.
   * For live callers on worker threads; never call this from the engine thread.
   *
   * <p>An exception thrown by {@code work} is rethrown to the caller (runtime/error
   * unwrapped from the engine task); a timeout or interruption surfaces as an unchecked
   * exception so it can be mapped to {@code Result.error} at the tool seam.
   */
  public <T> T call(Supplier<T> work, long timeout, TimeUnit unit) {
    Future<T> task = submit(work);
    try {
      return task.get(timeout, unit);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      if (cause instanceof Error err) {
        throw err;
      }
      throw new IllegalStateException("Engine task failed", cause);
    } catch (TimeoutException e) {
      boolean cancelled = task.cancel(false);
      String outcome = cancelled
          ? "; queued task was cancelled"
          : "; task already started and may still complete";
      throw new IllegalStateException(
          "Engine task timed out after " + timeout + " " + unit + outcome, e);
    } catch (InterruptedException e) {
      task.cancel(false);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while awaiting engine task", e);
    }
  }

  /** {@link #call(Supplier)} for work with no return value. */
  public void execute(Runnable work) {
    call(() -> {
      work.run();
      return null;
    });
  }

  private static final class EngineTask<T> extends CompletableFuture<T> implements Runnable {

    private enum State { QUEUED, RUNNING, DONE, CANCELLED }

    private final Supplier<T> work;
    private final AtomicReference<State> state = new AtomicReference<>(State.QUEUED);

    private EngineTask(Supplier<T> work) {
      this.work = work;
    }

    @Override
    public void run() {
      if (!this.state.compareAndSet(State.QUEUED, State.RUNNING)) {
        return;
      }
      try {
        complete(this.work.get());
      } catch (Throwable t) {
        completeExceptionally(t);
      } finally {
        this.state.set(State.DONE);
      }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (!this.state.compareAndSet(State.QUEUED, State.CANCELLED)) {
        return false;
      }
      return super.cancel(false);
    }
  }
}
