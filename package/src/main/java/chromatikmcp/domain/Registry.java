package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.modulator.LXModulator;

/**
 * Read-only view of the LX registry: the pattern/effect/modulator classes available to
 * instantiate. Without these, the class argument to a future add_pattern/add_effect/
 * add_modulator call is unguessable.
 */
public final class Registry {

  /** {@code global}/{@code device} are the modulator scope annotations; null for patterns/effects. */
  public record ComponentType(String className, String name, String category, List<String> tags,
      Boolean global, Boolean device, Class<? extends LXComponent> clazz) {}

  private Registry() {}

  public static List<ComponentType> patterns(LX lx) {
    return describe(lx, lx.registry.patterns);
  }

  public static List<ComponentType> effects(LX lx) {
    return describe(lx, lx.registry.effects);
  }

  public static List<ComponentType> modulators(LX lx) {
    return describe(lx, lx.registry.modulators);
  }

  private static List<ComponentType> describe(LX lx, List<? extends Class<? extends LXComponent>> classes) {
    List<ComponentType> result = new ArrayList<>(classes.size());
    for (Class<? extends LXComponent> clazz : classes) {
      // UI-hidden classes shouldn't be advertised to agents as instantiable either.
      if (clazz.isAnnotationPresent(LXComponent.Hidden.class)) {
        continue;
      }
      List<String> tags = lx.registry.getTags(clazz);
      // add_modulator gates on these annotations — advertise them so clients can predict
      // which scopes a modulator accepts instead of probing.
      boolean modulator = LXModulator.class.isAssignableFrom(clazz);
      result.add(new ComponentType(
          clazz.getName(),
          LXComponent.getComponentName(clazz),
          LXComponent.getCategory(clazz),
          (tags == null) ? List.of() : List.copyOf(tags),
          modulator ? clazz.isAnnotationPresent(LXModulator.Global.class) : null,
          modulator ? clazz.isAnnotationPresent(LXModulator.Device.class) : null,
          clazz));
    }
    return result;
  }
}
