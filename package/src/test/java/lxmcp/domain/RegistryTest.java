package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.GridModel;
import heronarts.lx.pattern.color.GradientPattern;

class RegistryTest {

  /** The expectation the production filter implements: registered minus @Hidden. */
  private static long visibleCount(List<? extends Class<? extends LXComponent>> classes) {
    return classes.stream().filter(c -> !c.isAnnotationPresent(LXComponent.Hidden.class)).count();
  }

  private LX lx;

  private LX newHeadlessLx() {
    this.lx = new LX(new GridModel(8, 8));
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
