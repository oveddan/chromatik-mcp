package lxmcp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;

/**
 * The PR-2 engine-thread serialization gate. {@link EngineExecutor} marshals work onto
 * the LX engine thread; this proves the queue serializes FIFO, surfaces results and
 * failures, and that real {@code LXCommand} mutations apply through it. Mirrors the
 * concurrency test shape seeded in {@code docs/spike/qa-strategy.md}.
 *
 * <p>Each test disposes its {@link LX} in {@link #tearDown()}. {@code new LX(...)} starts a
 * non-daemon MIDI device-update thread that, on macOS, contends on a static CoreMIDI lock;
 * without disposal these accumulate across tests and deadlock construction. Disposing keeps
 * at most one alive at a time.
 */
class EngineExecutorTest {

  private LX lx;

  private LX newHeadlessLx() {
    LXModel model = new GridModel(8, 8);
    this.lx = new LX(model);
    return this.lx;
  }

  @AfterEach
  void tearDown() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }

  @Test
  void submitDrainsInFifoOrderOnEngineThread() {
    LX lx = newHeadlessLx();
    EngineExecutor executor = new EngineExecutor(lx);

    int n = 50;
    List<Integer> order = new ArrayList<>();
    List<Future<Integer>> futures = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      final int v = i;
      futures.add(executor.submit(() -> {
        order.add(v);   // no synchronization needed: all tasks run on the engine thread
        return v;
      }));
    }

    // Nothing runs until the engine drains — submit() is non-blocking.
    assertTrue(order.isEmpty(), "tasks must not run before the engine ticks");

    lx.engine.run();

    List<Integer> expected = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      expected.add(i);
    }
    assertEquals(expected, order, "tasks must run in submission order on one thread");
    for (Future<Integer> f : futures) {
      assertTrue(f.isDone(), "every future should complete after the drain");
    }
  }

  @Test
  void submitCapturesResult() throws Exception {
    LX lx = newHeadlessLx();
    EngineExecutor executor = new EngineExecutor(lx);

    Future<Integer> future = executor.submit(() -> 42);
    assertFalse(future.isDone(), "result is not available before the engine ticks");

    lx.engine.run();
    assertEquals(42, future.get(1, TimeUnit.SECONDS));
  }

  @Test
  void submitCapturesFailure() {
    LX lx = newHeadlessLx();
    EngineExecutor executor = new EngineExecutor(lx);

    IllegalStateException boom = new IllegalStateException("boom");
    Future<Object> future = executor.submit(() -> {
      throw boom;
    });

    lx.engine.run();

    ExecutionException thrown = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
    assertSame(boom, thrown.getCause(), "the original failure should be the future's cause");
  }

  @Test
  void appliesLxCommandMutationThroughTheEngine() throws Exception {
    LX lx = newHeadlessLx();
    EngineExecutor executor = new EngineExecutor(lx);

    double original = lx.engine.speed.getValue();
    double target = original / 2.0;

    Future<Double> future = executor.submit(() -> {
      lx.command.perform(new LXCommand.Parameter.SetValue(lx.engine.speed, target));
      return lx.engine.speed.getValue();
    });

    lx.engine.run();
    assertEquals(target, future.get(1, TimeUnit.SECONDS), 1e-9);
    assertEquals(target, lx.engine.speed.getValue(), 1e-9);
  }

  /**
   * The blocking {@code call(...)} path, exercised with a self-managed drainer thread
   * standing in for LX's engine thread (we never call {@code lx.engine.start()}, which the
   * headless harness forbids). The non-blocking tests above are the real serialization
   * gate; this confirms a worker thread can block for a result.
   */
  @Test
  @Timeout(10)
  void callBlocksForResultWhileAnotherThreadDrains() throws Exception {
    LX lx = newHeadlessLx();
    EngineExecutor executor = new EngineExecutor(lx);

    AtomicBoolean draining = new AtomicBoolean(true);
    Thread drainer = new Thread(() -> {
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

    try {
      String result = executor.call(() -> "ok");
      assertEquals("ok", result);
    } finally {
      draining.set(false);
      drainer.join(2_000);
    }
  }
}
