package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import heronarts.lx.LX;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.command.LXCommand;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXMasterBus;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXView;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.structure.view.LXViewDefinition;
import heronarts.lx.structure.view.LXViewEngine;

/**
 * Read-only snapshot of the model view system: named subsets of the model (defined by a
 * tag selector on {@code lx.structure.views}), which devices currently select them, and
 * the model's tag vocabulary those selectors compose from.
 */
public final class Views {

  public record ViewInfo(String path, String label, String selector, boolean enabled,
      boolean priority, String normalization, String orientation, int numGroups,
      int numFixtures, String cuePath) {}

  /**
   * A component's model-view assignment, resolved through LX's own inheritance rule rather
   * than reimplemented here (see {@link #viewRef(LX, LXViewEngine.Selector, LXModel)}).
   * {@code selectorPath} is this component's own settable {@code view} parameter path; it is
   * present when that path can be resolved to a canonical path and null otherwise (e.g. the
   * component or an ancestor is unregistered) — callers omit the key when it is null.
   * {@code selectedLabel}/{@code selectedPath} are null when the selector is on "Default"
   * (inherits from the parent). {@code effectiveLabel}/{@code effectivePath} are null when
   * the component renders to the whole model, which happens when it (and everything it
   * inherits from) is on Default, or when the selected view has no {@code LXView} built for
   * it at all. LX builds an {@code LXView} for a view definition only when the model is
   * non-empty, the view is enabled, and its selector string is non-blank (see
   * {@code LXViewDefinition.rebuild()}); if any of those fails, {@code effectiveLabel}/
   * {@code effectivePath} are null and the component falls back to the whole model. A
   * consequence of this: a view that clears all three gates but whose tag selector currently
   * matches zero fixtures does <strong>not</strong> fall back — {@code rebuild()} still builds
   * a non-null (empty) {@code LXView} in that case, so {@code effectiveLabel}/
   * {@code effectivePath} stay set to it; it just lights nothing, visible via that view's
   * {@code numFixtures}/{@code numGroups} at {@code get_views}.
   */
  public record ViewRef(String selectorPath, String selectedLabel, String selectedPath,
      String effectiveLabel, String effectivePath) {}

  public record AssignmentInfo(String viewPath, String devicePath, String deviceLabel) {}

  public record ViewsSnapshot(List<ViewInfo> views, List<AssignmentInfo> assignments,
      List<Model.TagInfo> modelTags) {}

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

    List<Model.TagInfo> modelTags = Model.collectTags(lx.getModel());

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

  /**
   * Compose a new named model subset, routed through {@code LXCommand.Structure.AddView}
   * for undo. The command's no-arg constructor only creates a bare view — label, selector,
   * normalization and orientation are applied as direct parameter edits afterward, so
   * undoing the add removes the freshly-configured view as a whole (there is nothing left
   * to separately undo). Call on the engine thread.
   */
  public static ViewInfo addView(LX lx, String label, String selector,
      LXView.Normalization normalization, LXView.Orientation orientation) {
    List<LXViewDefinition> views = lx.structure.views.views;
    int before = views.size();
    Commands.perform(lx, new LXCommand.Structure.AddView());
    if (views.size() != before + 1) {
      throw new IllegalStateException("AddView did not add a view");
    }
    LXViewDefinition view = views.get(before);
    view.label.setValue(label);
    view.selector.setValue(selector);
    view.normalization.setValue(normalization);
    view.orientation.setValue(orientation);
    return describeView(view);
  }

  /**
   * Remove a view, routed through {@code LXCommand.Structure.RemoveView} for undo. Undo
   * reconstructs the view (label/selector/normalization/orientation, serialized at remove
   * time) at its original index — but does <strong>not</strong> restore device 'view'
   * selections that pointed at the removed view: see the reassignment caveat below, which
   * applies whether or not the removal is later undone.
   *
   * <p>Devices selecting a <em>surviving</em> view are unaffected: {@code
   * LXViewEngine.removeView} calls {@code updateSelectors()}, which rebuilds every
   * selector's objects/options and then explicitly restores each selector's selection by
   * object identity when the previously-selected view is still in the list. But a device
   * whose 'view' selector pointed at the <em>removed</em> view is <strong>not</strong> reset
   * to Default: since that view is no longer in the list, the identity-restore is skipped,
   * and the selector's stored *index* is only clamped into the shrunk range ({@code
   * DiscreteParameter.setOptions} -> {@code setRange}) — so it silently reassigns to
   * whatever view (or Default) now occupies that index, which is usually a different view,
   * not necessarily Default. Callers should re-check device 'view' assignments (get_views'
   * assignments) after removing a view rather than assume they reset; undoing the removal
   * does not restore those stale selector assignments either.
   */
  public static void removeView(LX lx, LXViewDefinition view) {
    Commands.perform(lx, new LXCommand.Structure.RemoveView(view));
    if (lx.structure.views.views.contains(view)) {
      throw new IllegalStateException("removeView did not remove the view");
    }
  }

  /**
   * The view-assignment snapshot for a channel or group. Two overloads exist because
   * {@link LXAbstractChannel} and {@link LXDeviceComponent} (patterns, effects) share no
   * common supertype exposing both a {@code view} selector and {@code getModelView()}.
   */
  public static ViewRef viewRef(LXAbstractChannel channel) {
    return viewRef(channel.getLX(), channel.view, channel.getModelView());
  }

  /** The view-assignment snapshot for a pattern or effect. See {@link #viewRef(LXAbstractChannel)}. */
  public static ViewRef viewRef(LXDeviceComponent device) {
    return viewRef(device.getLX(), device.view, device.getModelView());
  }

  /**
   * Resolves {@code resolved} (LX's own {@code getModelView()} answer) back to the
   * {@link LXViewDefinition} that produced it by identity — {@code getView()}, not
   * {@code getModelView()}, since a disabled/empty view's {@code getModelView()} also
   * falls back to the whole model and would otherwise match spuriously.
   */
  private static ViewRef viewRef(LX lx, LXViewEngine.Selector selector, LXModel resolved) {
    LXViewDefinition selected = selector.getObject();
    LXViewDefinition effective = null;
    for (LXViewDefinition def : lx.structure.views.views) {
      if (def.getView() == resolved) {
        effective = def;
        break;
      }
    }
    return new ViewRef(
        Resolve.canonicalPathOrNull(selector),
        (selected == null) ? null : selected.getLabel(),
        (selected == null) ? null : Resolve.canonicalPath(selected),
        (effective == null) ? null : effective.getLabel(),
        (effective == null) ? null : Resolve.canonicalPath(effective));
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

}
