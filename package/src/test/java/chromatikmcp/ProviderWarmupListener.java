package chromatikmcp;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Forces the JDK javax.sound provider registry (and, on macOS, the coremidi4j provider
 * class) to fully initialize single-threaded before any test constructs an {@code LX}
 * instance.
 *
 * <p>Root cause: {@code new LX()} starts an async "LXMidiEngine Device Initialization"
 * thread (LXMidiEngine.java:363) that races the test's own main thread inside
 * {@code AudioSystem.getMixerInfo()} / {@code MidiSystem.getMidiDeviceInfo()}. Both paths
 * lazily classload {@code com.sun.media.sound.JSSecurityManager} and
 * {@code uk.co.xfactorylibrarians.coremidi4j.CoreMidiDeviceProvider}, each taking the
 * other's class-init lock in the opposite order — a classic lock-inversion deadlock that
 * wedges the surefire JVM at 0% CPU. Running both lookups once, sequentially, here (before
 * any LX instance exists) means the classes are already initialized and the lock-inversion
 * window never opens.
 */
public class ProviderWarmupListener implements LauncherSessionListener {

  @Override
  public void launcherSessionOpened(LauncherSession session) {
    try {
      javax.sound.midi.MidiSystem.getMidiDeviceInfo();
      javax.sound.sampled.AudioSystem.getMixerInfo();
    } catch (Throwable t) {
      // A soundless/headless machine may throw here (no mixers/devices) — that's fine,
      // the classloading side effect we care about has already happened either way.
    }
  }
}
