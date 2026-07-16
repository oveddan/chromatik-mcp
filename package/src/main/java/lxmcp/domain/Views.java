package lxmcp.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXMasterBus;
import heronarts.lx.model.LXModel;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.structure.view.LXViewDefinition;

/**
 * Read-only snapshot of the model view system: named subsets of the model (defined by a
 * tag selector on {@code lx.structure.views}), which devices currently select them, and
 * the model's tag vocabulary those selectors compose from.
 */
public final class Views {

  public record ViewInfo(String path, String label, String selector, boolean enabled,
      boolean priority, String normalization, String orientation, int numGroups,
      int numFixtures, String cuePath) {}

  public record AssignmentInfo(String viewPath, String devicePath, String deviceLabel) {}

  public record TagInfo(String tag, int count) {}

  public record ViewsSnapshot(List<ViewInfo> views, List<AssignmentInfo> assignments,
      List<TagInfo> modelTags) {}

  private Views() {}

  /** Call on the engine thread; the returned records are safe to read anywhere. */
  public static ViewsSnapshot describe(LX lx) {
    List<LXViewDefinition> defs = lx.structure.views.views;

    List<ViewInfo> views = new ArrayList<>();
    for (LXViewDefinition def : defs) {
      views.add(describeView(def));
    }

    List<AssignmentInfo> assignments = new ArrayList<>();
    for (LXAbstractChannel channel : lx.engine.mixer.channels) {
      addAssignment(assignments, channel.view.getObject(), channel);
      if (channel instanceof LXChannel c) {
        for (LXPattern pattern : c.patterns) {
          addAssignment(assignments, pattern.view.getObject(), pattern);
          addDeviceEffects(assignments, pattern.getEffects());
        }
      }
      addDeviceEffects(assignments, channel.getEffects());
    }
    LXMasterBus master = lx.engine.mixer.masterBus;
    addDeviceEffects(assignments, master.getEffects());

    List<TagInfo> modelTags = collectTags(lx.getModel());

    return new ViewsSnapshot(views, assignments, modelTags);
  }

  private static void addDeviceEffects(List<AssignmentInfo> assignments, List<LXEffect> effects) {
    for (LXEffect effect : effects) {
      addAssignment(assignments, effect.view.getObject(), effect);
    }
  }

  private static void addAssignment(
      List<AssignmentInfo> assignments, LXViewDefinition selected, LXDeviceComponent device) {
    if (selected != null) {
      assignments.add(new AssignmentInfo(Resolve.canonicalPath(selected), device.getCanonicalPath(),
          device.getLabel()));
    }
  }

  private static void addAssignment(
      List<AssignmentInfo> assignments, LXViewDefinition selected, LXAbstractChannel channel) {
    if (selected != null) {
      assignments.add(new AssignmentInfo(Resolve.canonicalPath(selected), channel.getCanonicalPath(),
          channel.getLabel()));
    }
  }

  private static ViewInfo describeView(LXViewDefinition def) {
    return new ViewInfo(
        Resolve.canonicalPath(def),
        def.getLabel(),
        def.selector.getString(),
        def.enabled.isOn(),
        def.priority.isOn(),
        def.normalization.getEnum().name().toLowerCase(Locale.ROOT),
        def.orientation.getEnum().name().toLowerCase(Locale.ROOT),
        def.numGroups.getValuei(),
        def.numFixtures.getValuei(),
        Resolve.canonicalPath(def.cueActive));
  }

  /** Distinct tags across the loaded model, collected recursively, with occurrence counts. */
  private static List<TagInfo> collectTags(LXModel model) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    collectTags(model, counts);
    List<TagInfo> tags = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      tags.add(new TagInfo(entry.getKey(), entry.getValue()));
    }
    return tags;
  }

  private static void collectTags(LXModel model, Map<String, Integer> counts) {
    for (String tag : model.tags) {
      counts.merge(tag, 1, Integer::sum);
    }
    for (LXModel child : model.children) {
      collectTags(child, counts);
    }
  }
}
