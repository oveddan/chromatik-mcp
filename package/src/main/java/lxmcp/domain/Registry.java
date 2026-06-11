package lxmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;

/**
 * Read-only view of the LX registry: the pattern/effect/modulator classes available to
 * instantiate. Without these, the class argument to a future add_pattern/add_effect/
 * add_modulator call is unguessable.
 */
public final class Registry {

  public record ComponentType(String className, String name, String category, List<String> tags) {}

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
      result.add(new ComponentType(
          clazz.getName(),
          LXComponent.getComponentName(clazz),
          LXComponent.getCategory(clazz),
          (tags == null) ? List.of() : List.copyOf(tags)));
    }
    return result;
  }
}
