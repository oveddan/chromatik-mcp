package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.pattern.color.GradientPattern;

class RegistryTest extends HeadlessLxTest {

  /** The expectation the production filter implements: registered minus @Hidden. */
  private static long visibleCount(List<? extends Class<? extends LXComponent>> classes) {
    return classes.stream().filter(c -> !c.isAnnotationPresent(LXComponent.Hidden.class)).count();
  }

  @Test
  void enumeratesRegisteredPatternClasses() {
    LX lx = newHeadlessLx();
    List<Registry.ComponentType> patterns = Registry.patterns(lx);
    assertEquals(visibleCount(lx.registry.patterns), patterns.size());
    assertTrue(
        patterns.stream().anyMatch(t -> t.className().equals(GradientPattern.class.getName())),
        "built-in GradientPattern should be registered");
    for (Registry.ComponentType type : patterns) {
      assertNotNull(type.className());
      assertFalse(type.name().isEmpty(), "every entry has a display name");
      assertNotNull(type.category());
      assertNotNull(type.tags());
    }
  }

  @Test
  void effectsAndModulatorsMatchRegistrySizes() {
    LX lx = newHeadlessLx();
    assertEquals(visibleCount(lx.registry.effects), Registry.effects(lx).size());
    assertEquals(visibleCount(lx.registry.modulators), Registry.modulators(lx).size());
    assertFalse(Registry.effects(lx).isEmpty(), "LX registers built-in effects");
    assertFalse(Registry.modulators(lx).isEmpty(), "LX registers built-in modulators");
  }
}
