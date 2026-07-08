package lxmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.command.LXCommand;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.modulation.LXTriggerModulation;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * Mutations on modulation engines — the global one ({@code lx.engine.modulation}, the
 * Chromatik side panel) or a device's own ({@code LXDeviceComponent.modulation}, inside a
 * pattern/effect chain) — routed through LXCommand for undo. Call on the engine thread.
 */
public final class Modulators {

  private Modulators() {}

  public record ModulatorInfo(String path, int id, String label, String className,
      boolean running, String oscAddress) {}

  public record ModulationInfo(String path, int id, String sourcePath, String targetPath,
      double range, String polarity, String rangePath) {}

  public record TriggerInfo(String path, int id, String sourcePath, String targetPath) {}

  /** Read-only snapshot of one engine's live modulators and wirings. */
  public record EngineInfo(String path, List<ModulatorInfo> modulators,
      List<ModulationInfo> modulations, List<TriggerInfo> triggers) {}

  /** Snapshot {@code engine}'s modulators and wirings; call on the engine thread. */
  public static EngineInfo listEngine(LX lx, LXModulationEngine engine) {
    List<ModulatorInfo> modulators = new ArrayList<>();
    for (LXModulator modulator : engine.modulators) {
      modulators.add(new ModulatorInfo(
          modulator.getCanonicalPath(),
          modulator.getId(),
          modulator.getLabel(),
          modulator.getClass().getName(),
          modulator.isRunning(),
          modulator.getOscAddress()));
    }
    List<ModulationInfo> modulations = new ArrayList<>();
    for (LXCompoundModulation modulation : engine.modulations) {
      modulations.add(new ModulationInfo(
          modulation.getCanonicalPath(),
          modulation.getId(),
          modulation.source.getCanonicalPath(),
          modulation.target.getCanonicalPath(),
          modulation.range.getValue(),
          modulation.polarity.getEnum().name(),
          modulation.range.getCanonicalPath()));
    }
    List<TriggerInfo> triggers = new ArrayList<>();
    for (LXTriggerModulation trigger : engine.triggers) {
      triggers.add(new TriggerInfo(
          trigger.getCanonicalPath(),
          trigger.getId(),
          trigger.source.getCanonicalPath(),
          trigger.target.getCanonicalPath()));
    }
    return new EngineInfo(engine.getCanonicalPath(), modulators, modulations, triggers);
  }

  /**
   * Resolve a modulator class name against the LX registry. Never {@code Class.forName}:
   * only registered, instantiable modulator types are addressable.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH for an unregistered name.
   */
  public static Class<? extends LXModulator> resolveModulatorClass(LX lx, String className) {
    for (Class<? extends LXModulator> clazz : lx.registry.modulators) {
      if (clazz.getName().equals(className)) {
        return clazz;
      }
    }
    throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
        "Unknown modulator type: " + className + " (see list_available_modulators)");
  }

  /**
   * Resolve a scope path to its modulation engine: {@code null} means the global engine;
   * a device path (pattern/effect) yields the device's own engine; a modulation engine's
   * own path ({@code /lx/modulation} for global) is accepted directly — the only way to
   * host a device-sourced wiring in the global engine, which LX permits.
   */
  public static LXModulationEngine resolveEngine(LX lx, String scopePath) {
    if (scopePath == null) {
      return lx.engine.modulation;
    }
    LXComponent component = Resolve.component(lx, scopePath);
    if (component instanceof LXModulationEngine engine) {
      return engine;
    }
    if (component instanceof LXDeviceComponent device) {
      return device.modulation;
    }
    throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
        "Not a device or modulation engine at path: " + scopePath
            + " (found " + component.getClass().getSimpleName() + ")");
  }

  /** Choose the engine hosting a wiring: an explicit scope wins; else inferred from the source. */
  public static LXModulationEngine selectEngine(LX lx, String scopePath, LXParameter source) {
    return (scopePath != null) ? resolveEngine(lx, scopePath) : inferEngine(lx, source);
  }

  /**
   * Infer the engine a wiring should land in from its source parameter: a modulator knob
   * inside a device chain carries its device engine in its parent chain; anything else
   * (a global knob, an ordinary parameter) falls back to the global engine.
   */
  public static LXModulationEngine inferEngine(LX lx, LXParameter source) {
    LXComponent component = source.getParent();
    while (component != null) {
      if (component instanceof LXModulationEngine engine) {
        return engine;
      }
      component = component.getParent();
    }
    return lx.engine.modulation;
  }

  /**
   * Add a modulator of {@code kind} to {@code engine}. The kind must carry the LX
   * annotation matching the engine's scope ({@code @LXModulator.Global} for the global
   * engine, {@code @LXModulator.Device} for a device engine) — the command itself does not
   * enforce this, and an out-of-scope modulator is unsupported state the Chromatik UI
   * never creates.
   */
  public static LXModulator addModulator(LX lx, LXModulationEngine engine, Class<? extends LXModulator> kind) {
    if (engine.getParent() instanceof LXDeviceComponent device) {
      if (!kind.isAnnotationPresent(LXModulator.Device.class)) {
        throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
            kind.getName() + " is not available in device chains (no @LXModulator.Device); "
                + "omit scope to add it to the global engine");
      }
    } else if (!kind.isAnnotationPresent(LXModulator.Global.class)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          kind.getName() + " is not available globally (no @LXModulator.Global); "
              + "pass a device path as scope");
    }
    List<LXModulator> modulators = engine.modulators;
    int before = modulators.size();
    lx.command.perform(new LXCommand.Modulation.AddModulator(engine, kind));
    if (modulators.size() != before + 1) {
      throw new IllegalStateException("AddModulator did not add a " + kind.getName());
    }
    return modulators.get(before);
  }

  /**
   * Wire a continuous modulation from {@code source} onto {@code target} in {@code engine}.
   * Scope is pre-validated here because {@code LXParameterModulation}'s constructor
   * registers the graph edge <em>before</em> its own scope check — an LX-side rejection
   * would leave a stale edge behind and wipe the undo history.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH when an end is out of scope or LX
   *     rejects the wiring (circular dependency).
   */
  public static LXCompoundModulation wireModulation(LX lx, LXModulationEngine engine,
      LXNormalizedParameter source, LXCompoundModulation.Target target) {
    requireInScope(engine, source, "Source");
    requireInScope(engine, target, "Target");
    List<LXCompoundModulation> modulations = engine.modulations;
    int before = modulations.size();
    performWiring(lx, new LXCommand.Modulation.AddModulation(engine, source, target),
        source, target);
    return modulations.get(before);
  }

  /**
   * Wire a trigger modulation (boolean pulse) from {@code source} onto {@code target} in
   * {@code engine}. Same scope pre-validation as {@link #wireModulation}.
   */
  public static LXTriggerModulation wireTrigger(LX lx, LXModulationEngine engine,
      BooleanParameter source, BooleanParameter target) {
    requireInScope(engine, source, "Source");
    requireInScope(engine, target, "Target");
    List<LXTriggerModulation> triggers = engine.triggers;
    int before = triggers.size();
    performWiring(lx, new LXCommand.Modulation.AddTrigger(engine, source, target),
        source, target);
    return triggers.get(before);
  }

  /** Remove a continuous modulation; its engine rides along in {@code modulation.scope}. */
  public static void removeModulation(LX lx, LXCompoundModulation modulation) {
    Commands.perform(lx, new LXCommand.Modulation.RemoveModulation(modulation.scope, modulation));
  }

  /** Remove a trigger modulation; its engine rides along in {@code trigger.scope}. */
  public static void removeTrigger(LX lx, LXTriggerModulation trigger) {
    Commands.perform(lx, new LXCommand.Modulation.RemoveTrigger(trigger.scope, trigger));
  }

  private static void performWiring(LX lx, LXCommand command,
      LXParameter source, LXParameter target) {
    if (!Commands.applied(lx, command)) {
      // Scope and types were pre-validated, so the realistic constructor failure left is
      // a circular dependency; the constructor throws before mutating engine state.
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Modulation " + source.getCanonicalPath() + " -> " + target.getCanonicalPath()
              + " was rejected by LX — most likely a circular dependency");
    }
  }

  private static void requireInScope(LXModulationEngine engine, LXParameter parameter, String role) {
    // Mirrors LXParameterModulation.checkScope: the parameter's parent chain must pass
    // through the engine's parent (LXEngine for global, the device for device engines).
    LXComponent domain = engine.getParent();
    LXComponent component = parameter.getParent();
    while (component != null) {
      if (component == domain) {
        return;
      }
      component = component.getParent();
    }
    throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
        role + " " + parameter.getCanonicalPath() + " is outside the modulation scope of "
            + domain.getCanonicalPath()
            + " — both ends must live inside the device; pass scope /lx/modulation to "
            + "host the wiring in the global engine instead");
  }
}
