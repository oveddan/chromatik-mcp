package heronarts.lx.midi;

import java.lang.reflect.Field;
import java.util.List;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

import heronarts.lx.LX;

/**
 * Test-only synthetic MIDI input. Tests that exercise input routing flags must not reach for
 * {@code lx.engine.midi.inputs.get(0)}: what lands there is whatever hardware the developer
 * has plugged in, and LX instantiates a control surface for any input it recognizes — which
 * opens the device and leaves {@code input.enabled} already on. This class registers an input
 * backed by a device that talks to nothing, so the list is deterministic on every machine.
 *
 * <p>Lives in {@code heronarts.lx.midi} because {@link LXMidiInput}'s constructor is
 * package-private. LX exposes no API for injecting a device — real ones arrive only from the
 * engine's own discovery thread — so registration reflects on the engine's backing list.
 */
public final class SyntheticMidiInput {

  private SyntheticMidiInput() {}

  /**
   * Append a synthetic input to {@code lx.engine.midi.inputs} and return it. Safe against
   * hardware racing in: the engine appends discovered devices from an {@code addTask} that
   * only runs while the engine loop is running, which headless tests never start.
   */
  public static LXMidiInput register(LX lx, String name) {
    LXMidiEngine engine = lx.engine.midi;
    LXMidiInput input = new LXMidiInput(engine, new NullDevice(name));
    backingInputList(engine).add(input);
    return input;
  }

  @SuppressWarnings("unchecked")
  private static List<LXMidiInput> backingInputList(LXMidiEngine engine) {
    try {
      Field field = LXMidiEngine.class.getDeclaredField("mutableInputs");
      field.setAccessible(true);
      return (List<LXMidiInput>) field.get(engine);
    } catch (ReflectiveOperationException x) {
      throw new IllegalStateException(
          "LXMidiEngine.mutableInputs is gone — update SyntheticMidiInput for this LX version", x);
    }
  }

  /** A {@link MidiDevice} that opens, transmits nothing, and closes. */
  private static final class NullDevice implements MidiDevice {

    private final Info info;
    private boolean open = false;

    private NullDevice(String name) {
      this.info = new Info(name, "chromatik-mcp", "Synthetic test MIDI input", "1.0") {};
    }

    @Override
    public Info getDeviceInfo() {
      return this.info;
    }

    @Override
    public void open() {
      this.open = true;
    }

    @Override
    public void close() {
      this.open = false;
    }

    @Override
    public boolean isOpen() {
      return this.open;
    }

    @Override
    public long getMicrosecondPosition() {
      return -1;
    }

    @Override
    public int getMaxReceivers() {
      return 0;
    }

    @Override
    public int getMaxTransmitters() {
      return -1;
    }

    @Override
    public Receiver getReceiver() throws MidiUnavailableException {
      throw new MidiUnavailableException("Synthetic input has no receiver");
    }

    @Override
    public List<Receiver> getReceivers() {
      return List.of();
    }

    @Override
    public Transmitter getTransmitter() {
      return new NullTransmitter();
    }

    @Override
    public List<Transmitter> getTransmitters() {
      return List.of();
    }
  }

  /** A transmitter that never delivers a message. */
  private static final class NullTransmitter implements Transmitter {

    private Receiver receiver;

    @Override
    public void setReceiver(Receiver receiver) {
      this.receiver = receiver;
    }

    @Override
    public Receiver getReceiver() {
      return this.receiver;
    }

    @Override
    public void close() {}
  }
}
