package chromatikmcp;

import org.junit.jupiter.api.AfterEach;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;

/**
 * Shared headless-LX fixture. Each test constructs its own {@link LX} via
 * {@link #newHeadlessLx()}; the instance is disposed after each test. {@code new LX(...)}
 * starts a non-daemon MIDI device-update thread that, on macOS, contends on a static
 * CoreMIDI lock; without disposal these accumulate across tests and deadlock construction.
 * Disposing keeps at most one alive at a time.
 *
 * <p>Classes that need one shared LX for the whole class (server-backed integration tests,
 * read-only catalog walks) can't extend this per-test fixture; they mark their static LX
 * field {@code @AutoClose("dispose")} instead so JUnit enforces the same disposal.
 */
public abstract class HeadlessLxTest {

  private LX lx;

  protected LX newHeadlessLx() {
    return track(new LX(newModel()));
  }

  /** Registers an externally constructed LX (e.g. with custom flags) for after-test disposal. */
  protected LX track(LX lx) {
    this.lx = lx;
    return lx;
  }

  /** Override to customize the model (e.g. {@code FramesTest} reindexes points). */
  protected LXModel newModel() {
    return new GridModel(8, 8);
  }

  @AfterEach
  final void disposeLx() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }
}
