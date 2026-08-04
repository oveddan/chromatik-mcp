package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.command.LXCommand;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.modulation.LXParameterModulation;
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

  /** The moved modulator plus every affected component whose canonical path changed. */
  public record ModulatorMoveResult(LXModulator modulator, List<PathChange> oscChanges) {}

  /** Read-only snapshot of one engine's live modulators and wirings. */
  public record EngineInfo(String path, List<ModulatorInfo> modulators,
      List<ModulationInfo> modulations, List<TriggerInfo> triggers) {}

  /** Snapshot {@code engine}'s modulators and wirings; call on the engine thread. */
  public static EngineInfo listEngine(LX lx, LXModulationEngine engine) {
    List<ModulatorInfo> modulators = new ArrayList<>();
    for (LXModulator modulator : engine.modulators) {
      modulators.add(new ModulatorInfo(
          Resolve.canonicalPathOrNull(modulator),
          modulator.getId(),
          modulator.getLabel(),
          modulator.getClass().getName(),
          modulator.isRunning(),
          modulator.getOscAddress()));
    }
    List<ModulationInfo> modulations = new ArrayList<>();
    for (LXCompoundModulation modulation : engine.modulations) {
      modulations.add(new ModulationInfo(
          Resolve.canonicalPathOrNull(modulation),
          modulation.getId(),
          Resolve.canonicalPathOrNull(modulation.source),
          Resolve.canonicalPathOrNull(modulation.target),
          modulation.range.getValue(),
          modulation.polarity.getEnum().name(),
          Resolve.canonicalPathOrNull(modulation.range)));
    }
    List<TriggerInfo> triggers = new ArrayList<>();
    for (LXTriggerModulation trigger : engine.triggers) {
      triggers.add(new TriggerInfo(
          Resolve.canonicalPathOrNull(trigger),
          trigger.getId(),
          Resolve.canonicalPathOrNull(trigger.source),
          Resolve.canonicalPathOrNull(trigger.target)));
    }
    return new EngineInfo(Resolve.canonicalPathOrNull(engine), modulators, modulations, triggers);
  }

  /**
   * Resolve a modulator class name against the LX registry. Never {@code Class.forName}:
   * only registered, instantiable modulator types are addressable. Accepts the full class
   * name, or the short name ({@code getSimpleName()} / display name) advertised by
   * {@code list_available_modulators}.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH for an unregistered or ambiguous name.
   */
  public static Class<? extends LXModulator> resolveModulatorClass(LX lx, String className) {
    return Resolve.resolveClassName(lx.registry.modulators, className, Resolve.Failure.TYPE_MISMATCH,
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
    Commands.perform(lx, new LXCommand.Modulation.AddModulator(engine, kind));
    if (modulators.size() != before + 1) {
      throw new IllegalStateException("AddModulator did not add a " + kind.getName());
    }
    return modulators.get(before);
  }

  /**
   * Move a modulator to a new 0-based index within its global or device-local engine.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if the modulator is not owned by a
   *     modulation engine or the index is out of range
   */
  public static ModulatorMoveResult moveModulator(LX lx, String path, int toIndex) {
    LXModulator modulator = Resolve.component(lx, path, LXModulator.class);
    if (!(modulator.getParent() instanceof LXModulationEngine engine)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Not a movable modulator: " + path + " (parent is not a modulation engine)");
    }
    int size = engine.modulators.size();
    if (toIndex < 0 || toIndex >= size) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Modulator index " + toIndex + " out of range [0," + (size - 1) + "] on "
              + engine.getCanonicalPath());
    }
    LinkedHashMap<Integer, String> before = new LinkedHashMap<>();
    for (LXModulator sibling : engine.modulators) {
      snapshotPaths(sibling, before);
    }
    Commands.perform(lx, new LXCommand.Modulation.MoveModulator(engine, modulator, toIndex));
    if (modulator.getIndex() != toIndex || engine.modulators.get(toIndex) != modulator) {
      throw new IllegalStateException("MoveModulator did not move " + path + " to index " + toIndex);
    }
    List<PathChange> changes = new ArrayList<>();
    for (Map.Entry<Integer, String> entry : before.entrySet()) {
      LXComponent component = lx.getComponent(entry.getKey());
      if (component != null && !component.getCanonicalPath().equals(entry.getValue())) {
        changes.add(new PathChange(
            entry.getKey(), entry.getValue(), component.getCanonicalPath()));
      }
    }
    return new ModulatorMoveResult(modulator, changes);
  }

  private static void snapshotPaths(LXComponent component, Map<Integer, String> snapshot) {
    if (snapshot.putIfAbsent(component.getId(), component.getCanonicalPath()) != null) {
      return;
    }
    for (LXComponent child : component.children.values()) {
      snapshotPaths(child, snapshot);
    }
    for (List<? extends LXComponent> children : component.childArrays.values()) {
      for (LXComponent child : children) {
        if (child != null) {
          snapshotPaths(child, snapshot);
        }
      }
    }
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
    return wireModulation(lx, engine, source, target, null);
  }

  /**
   * As {@link #wireModulation(LX, LXModulationEngine, LXNormalizedParameter,
   * LXCompoundModulation.Target)}, additionally applying an initial {@code range} (depth) so
   * the wiring is not inert until a separate set_parameter call; {@code null} leaves the
   * range at its default (0 — no effect).
   */
  public static LXCompoundModulation wireModulation(LX lx, LXModulationEngine engine,
      LXNormalizedParameter source, LXCompoundModulation.Target target, Double range) {
    requireInScope(engine, source, "Source");
    requireInScope(engine, target, "Target");
    List<LXCompoundModulation> modulations = engine.modulations;
    int before = modulations.size();
    performWiring(lx, new LXCommand.Modulation.AddModulation(engine, source, target),
        source, target);
    LXCompoundModulation modulation = modulations.get(before);
    if (range != null) {
      setModulationRange(lx, modulation, range);
    }
    return modulation;
  }

  /**
   * Apply an initial modulation depth so a fresh wiring has an immediate effect.
   * {@code modulation.range} is a {@code CompoundParameter} bounded to [-1, 1] and clamps
   * silently, so an out-of-range {@code range} is not a failure — the caller reads back
   * the applied (possibly clamped) value from the returned modulation.
   */
  public static void setModulationRange(LX lx, LXCompoundModulation modulation, double range) {
    Commands.perform(lx, new LXCommand.Parameter.SetValue(modulation.range, range));
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

  /**
   * Remove a modulator. {@code RemoveModulator} is an LX {@code RemoveComponent}, so any
   * wirings (modulations/triggers) sourced from or targeting the modulator are removed
   * along with it, all undoable as one command.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if the modulator's parent is not a
   *     modulation engine — unsupported state the Chromatik UI never creates.
   */
  public static void removeModulator(LX lx, LXModulator modulator) {
    if (!(modulator.getParent() instanceof LXModulationEngine engine)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Not a removable modulator: " + modulator.getCanonicalPath()
              + " (parent is not a modulation engine)");
    }
    Commands.perform(lx, new LXCommand.Modulation.RemoveModulator(engine, modulator));
    if (engine.modulators.contains(modulator)) {
      throw new IllegalStateException("RemoveModulator did not remove " + modulator.getCanonicalPath());
    }
  }

  /** Remove a trigger modulation; its engine rides along in {@code trigger.scope}. */
  public static void removeTrigger(LX lx, LXTriggerModulation trigger) {
    Commands.perform(lx, new LXCommand.Modulation.RemoveTrigger(trigger.scope, trigger));
  }

  /**
   * Remove a modulation wiring of either kind, dispatching on its concrete subtype.
   *
   * @return "modulation" or "trigger", naming the kind removed
   */
  public static String remove(LX lx, LXParameterModulation modulation) {
    if (modulation instanceof LXCompoundModulation compound) {
      removeModulation(lx, compound);
      return "modulation";
    }
    LXTriggerModulation trigger = (LXTriggerModulation) modulation;
    removeTrigger(lx, trigger);
    return "trigger";
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
