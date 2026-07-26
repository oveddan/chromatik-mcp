package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.effect.BlurEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXMasterBus;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.model.LXView;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.color.SolidPattern;
import heronarts.lx.structure.view.LXViewDefinition;

class ViewsTest extends HeadlessLxTest {

  // Two "cube"-tagged and two "sphere"-tagged submodels, so view selectors have distinct
  // fixture sets to match — needed to test a pattern's view overriding its channel's.
  @Override
  protected LXModel newModel() {
    List<LXPoint> cube1Points = List.of(new LXPoint(0, 0, 0), new LXPoint(1, 0, 0));
    List<LXPoint> cube2Points = List.of(new LXPoint(0, 1, 0), new LXPoint(1, 1, 0));
    List<LXPoint> sphere1Points = List.of(new LXPoint(0, 0, 1), new LXPoint(1, 0, 1));
    List<LXPoint> sphere2Points = List.of(new LXPoint(0, 1, 1), new LXPoint(1, 1, 1));
    LXModel cube1 = new LXModel(cube1Points, "cube");
    LXModel cube2 = new LXModel(cube2Points, "cube");
    LXModel sphere1 = new LXModel(sphere1Points, "sphere");
    LXModel sphere2 = new LXModel(sphere2Points, "sphere");
    List<LXPoint> allPoints = new ArrayList<>();
    allPoints.addAll(cube1Points);
    allPoints.addAll(cube2Points);
    allPoints.addAll(sphere1Points);
    allPoints.addAll(sphere2Points);
    return new LXModel(allPoints, new LXModel[] { cube1, cube2, sphere1, sphere2 });
  }

  @Test
  void describesViewsWithSelectorRoundTripAndLiveMatchFeedback() {
    LX lx = newHeadlessLx();
    LXViewDefinition view = lx.structure.views.addView();
    view.label.setValue("Cubes");
    view.selector.setValue("cube");

    Views.ViewsSnapshot snapshot = Views.describe(lx);
    assertEquals(1, snapshot.views().size());
    Views.ViewInfo info = snapshot.views().get(0);
    assertEquals(Resolve.canonicalPath(view), info.path());
    assertEquals("Cubes", info.label());
    assertEquals("cube", info.selector());
    assertTrue(info.enabled());
    assertEquals(2, info.numFixtures(), "selector 'cube' matches both tagged submodels");
    assertEquals(Resolve.canonicalPath(view.cueActive), info.cuePath());
  }

  @Test
  void collectsDistinctModelTagsRecursively() {
    LX lx = newHeadlessLx();

    Views.ViewsSnapshot snapshot = Views.describe(lx);
    List<String> tags = snapshot.modelTags().stream().map(Model.TagInfo::tag).toList();
    assertTrue(tags.contains("cube"), "tags: " + tags);
    Model.TagInfo cubeTag = snapshot.modelTags().stream()
        .filter(t -> t.tag().equals("cube")).findFirst().orElseThrow();
    assertEquals(2, cubeTag.count());
  }

  @Test
  void assignmentsListDevicesReferencingAView() {
    LX lx = newHeadlessLx();
    LXViewDefinition view = lx.structure.views.addView();
    view.selector.setValue("cube");

    LXChannel channel = lx.engine.mixer.addChannel();
    SolidPattern pattern = new SolidPattern(lx);
    channel.addPattern(pattern);
    pattern.view.setValue(view);

    Views.ViewsSnapshot snapshot = Views.describe(lx);
    boolean found = snapshot.assignments().stream()
        .anyMatch(a -> a.viewPath().equals(Resolve.canonicalPath(view))
            && a.devicePath().equals(pattern.getCanonicalPath()));
    assertTrue(found, "assignments: " + snapshot.assignments());
  }

  @Test
  void channelViewAssignmentIsListed() {
    LX lx = newHeadlessLx();
    LXViewDefinition view = lx.structure.views.addView();
    view.selector.setValue("cube");

    LXChannel channel = lx.engine.mixer.addChannel();
    channel.view.setValue(view);

    Views.ViewsSnapshot snapshot = Views.describe(lx);
    boolean found = snapshot.assignments().stream()
        .anyMatch(a -> a.viewPath().equals(Resolve.canonicalPath(view))
            && a.devicePath().equals(channel.getCanonicalPath()));
    assertTrue(found, "assignments: " + snapshot.assignments());
  }

  @Test
  void addViewComposesAndReportsLiveMatchFeedback() {
    LX lx = newHeadlessLx();

    Views.ViewInfo info = Views.addView(
        lx, "Cubes", "cube", LXView.Normalization.ABSOLUTE, LXView.Orientation.GROUP);

    assertEquals(1, lx.structure.views.views.size());
    LXViewDefinition view = lx.structure.views.views.get(0);
    assertEquals(Resolve.canonicalPath(view), info.path());
    assertEquals("Cubes", info.label());
    assertEquals("cube", info.selector());
    assertEquals("absolute", info.normalization());
    assertEquals("group", info.orientation());
    assertEquals(2, info.numFixtures(), "selector 'cube' matches both tagged submodels");
  }

  @Test
  void addViewWithANonMatchingSelectorReportsZeroFixtures() {
    LX lx = newHeadlessLx();

    Views.ViewInfo info = Views.addView(
        lx, "Nope", "no-such-tag", LXView.Normalization.RELATIVE, LXView.Orientation.GLOBAL);

    assertEquals(0, info.numFixtures());
    assertEquals(0, info.numGroups());
  }

  @Test
  void removeViewRemovesItFromTheEngine() {
    LX lx = newHeadlessLx();
    LXViewDefinition view = lx.structure.views.addView();
    view.selector.setValue("cube");

    Views.removeView(lx, view);

    assertTrue(lx.structure.views.views.isEmpty());
  }

  @Test
  void addViewIsUndoable() {
    LX lx = newHeadlessLx();

    Views.addView(lx, "Cubes", "cube", LXView.Normalization.RELATIVE, LXView.Orientation.GLOBAL);
    assertEquals(1, lx.structure.views.views.size());

    lx.command.undo();

    assertTrue(lx.structure.views.views.isEmpty(), "undo removes the added view entirely");
  }

  @Test
  void removeViewIsUndoableAndRestoresLabelAndSelector() {
    LX lx = newHeadlessLx();
    Views.ViewInfo added = Views.addView(
        lx, "Cubes", "cube", LXView.Normalization.ABSOLUTE, LXView.Orientation.GROUP);

    LXViewDefinition view = lx.structure.views.views.get(0);
    Views.removeView(lx, view);
    assertTrue(lx.structure.views.views.isEmpty());

    lx.command.undo();

    assertEquals(1, lx.structure.views.views.size(), "undo reconstructs the view");
    LXViewDefinition restored = lx.structure.views.views.get(0);
    assertEquals("Cubes", restored.getLabel(), "compare by content — undo builds a fresh instance");
    assertEquals("cube", restored.selector.getString());
    assertEquals("absolute", restored.normalization.getEnum().name().toLowerCase());
    assertEquals("group", restored.orientation.getEnum().name().toLowerCase());
  }

  @Test
  void removeViewUndoDoesNotRestoreADevicesStaleSelectorAssignment() {
    LX lx = newHeadlessLx();
    LXViewDefinition view = lx.structure.views.addView();
    view.label.setValue("Cubes");
    view.selector.setValue("cube");

    LXChannel channel = lx.engine.mixer.addChannel();
    channel.view.setValue(view);
    assertSame(view, channel.view.getObject());

    Views.removeView(lx, view);
    // Only view left was removed, so the device's selector index now maps to Default.
    assertNull(channel.view.getObject());

    lx.command.undo();

    assertEquals(1, lx.structure.views.views.size(), "undo reconstructs the view");
    assertNull(channel.view.getObject(),
        "undo restores the view definition, not the stale selector assignment it left behind");
  }

  @Test
  void removeViewReassignsADeviceSelectorToWhateverNowOccupiesTheOldIndex() {
    LX lx = newHeadlessLx();
    LXViewDefinition first = lx.structure.views.addView();
    first.label.setValue("First");
    LXViewDefinition second = lx.structure.views.addView();
    second.label.setValue("Second");

    LXChannel channel = lx.engine.mixer.addChannel();
    channel.view.setValue(second);
    assertSame(second, channel.view.getObject());

    // Removing "First" shifts "Second" down a slot. LXViewEngine.updateSelectors()
    // explicitly restores each selector by object identity when the previously-selected
    // view is still in the list, so a device that had selected the still-alive "Second"
    // keeps its selection across the reindex...
    Views.removeView(lx, first);
    assertSame(second, channel.view.getObject(), "surviving view keeps its selection");

    // ...but there is no such restore for the view that was actually removed: its
    // selector's stored *index* is only clamped into the shrunk range (setOptions ->
    // setRange), so a device selecting it silently reassigns to whatever now sits at that
    // index, rather than resetting to Default.
    LXChannel other = lx.engine.mixer.addChannel();
    other.view.setValue(second);
    Views.removeView(lx, second);
    assertNull(other.view.getObject(), "Second was the last view left, so its index now maps to Default");
  }

  @Test
  void devicesOnDefaultAreOmittedFromAssignments() {
    LX lx = newHeadlessLx();
    lx.structure.views.addView();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.addPattern(new SolidPattern(lx));

    Views.ViewsSnapshot snapshot = Views.describe(lx);
    assertTrue(snapshot.assignments().isEmpty(), "assignments: " + snapshot.assignments());
  }

  // ── viewRef: issue #153, model-view assignment reporting ────────────────────────────

  @Test
  void viewRefOnAChannelWithAnExplicitViewReportsSelectedEqualToEffective() {
    LX lx = newHeadlessLx();
    LXViewDefinition cubes = lx.structure.views.addView();
    cubes.label.setValue("Cubes");
    cubes.selector.setValue("cube");

    LXChannel channel = lx.engine.mixer.addChannel();
    channel.view.setValue(cubes);

    Views.ViewRef ref = Views.viewRef(channel);
    assertEquals(Resolve.canonicalPathOrNull(channel.view), ref.selectorPath());
    assertEquals("Cubes", ref.selectedLabel());
    assertEquals(Resolve.canonicalPath(cubes), ref.selectedPath());
    assertEquals("Cubes", ref.effectiveLabel());
    assertEquals(Resolve.canonicalPath(cubes), ref.effectivePath());
  }

  @Test
  void viewRefOnADefaultPatternInheritsItsChannelsView() {
    // The Highlights/Brando trap, inverted: a pattern left on Default renders to whatever
    // its channel resolves to, even though the pattern's own selector shows nothing set.
    LX lx = newHeadlessLx();
    LXViewDefinition cubes = lx.structure.views.addView();
    cubes.label.setValue("Cubes");
    cubes.selector.setValue("cube");

    LXChannel channel = lx.engine.mixer.addChannel();
    channel.view.setValue(cubes);
    SolidPattern pattern = new SolidPattern(lx);
    channel.addPattern(pattern);

    Views.ViewRef ref = Views.viewRef(pattern);
    assertNull(ref.selectedLabel(), "pattern's own selector is on Default");
    assertNull(ref.selectedPath());
    assertEquals("Cubes", ref.effectiveLabel(), "inherits the channel's view");
    assertEquals(Resolve.canonicalPath(cubes), ref.effectivePath());
  }

  @Test
  void viewRefOnAPatternOverridingItsChannelsViewReportsBothAndTheyDiffer() {
    LX lx = newHeadlessLx();
    LXViewDefinition cubes = lx.structure.views.addView();
    cubes.label.setValue("Cubes");
    cubes.selector.setValue("cube");
    LXViewDefinition spheres = lx.structure.views.addView();
    spheres.label.setValue("Spheres");
    spheres.selector.setValue("sphere");

    LXChannel channel = lx.engine.mixer.addChannel();
    channel.view.setValue(cubes);
    SolidPattern pattern = new SolidPattern(lx);
    channel.addPattern(pattern);
    pattern.view.setValue(spheres);

    Views.ViewRef channelRef = Views.viewRef(channel);
    Views.ViewRef patternRef = Views.viewRef(pattern);

    assertEquals("Cubes", channelRef.effectiveLabel());
    assertEquals("Spheres", patternRef.selectedLabel());
    assertEquals("Spheres", patternRef.effectiveLabel());
    assertNotEquals(channelRef.effectivePath(), patternRef.effectivePath(),
        "pattern's own view overrides its channel's — effective differs from the channel's");
    assertEquals(Resolve.canonicalPath(spheres), patternRef.effectivePath());
  }

  @Test
  void viewRefOnAMasterBusEffectHasNoSelectionAndRendersTheWholeModel() {
    LX lx = newHeadlessLx();
    LXMasterBus master = lx.engine.mixer.masterBus;
    BlurEffect effect = new BlurEffect(lx);
    master.addEffect(effect);

    Views.ViewRef ref = Views.viewRef(effect);
    assertNull(ref.selectedLabel());
    assertNull(ref.selectedPath());
    assertNull(ref.effectiveLabel(), "a master-bus effect on Default inherits the whole model");
    assertNull(ref.effectivePath());
    assertSame(lx.getModel(), effect.getModelView());
  }

  @Test
  void viewRefOnADisabledViewFallsBackToTheWholeModel() {
    // Verified against LX source (LXViewDefinition.rebuild()), not assumed: a *disabled*
    // view's internal LXView is null, so getModelView() falls back to the whole model. A
    // non-disabled view whose selector matches zero fixtures behaves differently — see
    // viewRefOnANonMatchingButEnabledViewDoesNotFallBackToTheWholeModel below.
    LX lx = newHeadlessLx();
    LXViewDefinition disabled = lx.structure.views.addView();
    disabled.label.setValue("Disabled");
    disabled.selector.setValue("cube");
    disabled.enabled.setValue(false);

    LXChannel channel = lx.engine.mixer.addChannel();
    channel.view.setValue(disabled);

    Views.ViewRef ref = Views.viewRef(channel);
    assertEquals("Disabled", ref.selectedLabel());
    assertEquals(Resolve.canonicalPath(disabled), ref.selectedPath());
    assertNull(ref.effectiveLabel(), "a disabled view's LXView is null; LX falls back to the whole model");
    assertNull(ref.effectivePath());
    assertSame(lx.getModel(), channel.getModelView(), "cross-check against LX's own answer");
  }

  @Test
  void viewRefOnANonMatchingButEnabledViewDoesNotFallBackToTheWholeModel() {
    // The surprising half of the case above: LXView.create() returns a non-null LXView.Empty
    // when a selector matches zero fixtures (LXView.java) — it does NOT return null, so
    // getModelView() does NOT fall back to the whole model here. 'effective' stays set to
    // that (empty) view; get_views reports its numFixtures/numGroups as 0.
    LX lx = newHeadlessLx();
    LXViewDefinition empty = lx.structure.views.addView();
    empty.label.setValue("Empty");
    empty.selector.setValue("no-such-tag");

    LXChannel channel = lx.engine.mixer.addChannel();
    channel.view.setValue(empty);

    Views.ViewRef ref = Views.viewRef(channel);
    assertEquals("Empty", ref.selectedLabel());
    assertEquals("Empty", ref.effectiveLabel(), "an empty-but-enabled view does not fall back");
    assertEquals(Resolve.canonicalPath(empty), ref.effectivePath());
    assertNotSame(lx.getModel(), channel.getModelView());
  }

  @Test
  void viewRefEffectivePathAgreesWithLxsOwnGetModelViewAcrossAFixtureProject() {
    // Objective cross-check, not a restatement of the inheritance rule: for every channel,
    // pattern, and effect this builds, resolving 'effectivePath' back through
    // lx.structure.views (or falling back to lx.getModel() when absent) must reproduce
    // exactly what LX's own getModelView() returns. This fails if LX's inheritance rule
    // ever changes, instead of agreeing with a stale copy of it here.
    LX lx = newHeadlessLx();
    LXViewDefinition cubes = lx.structure.views.addView();
    cubes.label.setValue("Cubes");
    cubes.selector.setValue("cube");
    LXViewDefinition spheres = lx.structure.views.addView();
    spheres.label.setValue("Spheres");
    spheres.selector.setValue("sphere");
    LXViewDefinition disabled = lx.structure.views.addView();
    disabled.label.setValue("Disabled");
    disabled.selector.setValue("cube");
    disabled.enabled.setValue(false);

    LXChannel channelA = lx.engine.mixer.addChannel();
    channelA.view.setValue(cubes);
    SolidPattern defaultPattern = new SolidPattern(lx);
    channelA.addPattern(defaultPattern);
    SolidPattern overridingPattern = new SolidPattern(lx);
    channelA.addPattern(overridingPattern);
    overridingPattern.view.setValue(spheres);
    BlurEffect channelEffect = new BlurEffect(lx);
    channelA.addEffect(channelEffect);
    BlurEffect patternEffect = new BlurEffect(lx);
    defaultPattern.addEffect(patternEffect);

    LXChannel channelB = lx.engine.mixer.addChannel();
    channelB.view.setValue(disabled);

    LXChannel channelC = lx.engine.mixer.addChannel(); // Default throughout
    channelC.addPattern(new SolidPattern(lx));

    BlurEffect masterEffect = new BlurEffect(lx);
    lx.engine.mixer.masterBus.addEffect(masterEffect);

    // LXChannel.getModelView() is a separate override that delegates to the group's
    // getModelView() when the channel is grouped and its own selector is on Default —
    // LXAbstractChannel.getModelView() never runs for this case, so it needs its own
    // exercise here rather than relying on the ungrouped channels above.
    LXChannel groupedChild = lx.engine.mixer.addChannel();
    heronarts.lx.mixer.LXGroup group = lx.engine.mixer.addGroup(List.of(groupedChild));
    group.view.setValue(spheres);

    List<LXAbstractChannel> channels =
        List.of(channelA, channelB, channelC, group, groupedChild);
    List<LXPattern> patterns = List.of(defaultPattern, overridingPattern, channelC.patterns.get(0));
    List<LXDeviceComponent> devices = List.of(channelEffect, patternEffect, masterEffect);

    for (LXAbstractChannel channel : channels) {
      assertMatchesLxsOwnAnswer(lx, Views.viewRef(channel), channel.getModelView());
    }
    for (LXPattern pattern : patterns) {
      assertMatchesLxsOwnAnswer(lx, Views.viewRef(pattern), pattern.getModelView());
    }
    for (LXDeviceComponent device : devices) {
      assertMatchesLxsOwnAnswer(lx, Views.viewRef(device), device.getModelView());
    }

    Views.ViewRef groupedChildRef = Views.viewRef(groupedChild);
    assertNull(groupedChildRef.selectedLabel(),
        "the grouped child's own selector is on Default");
    assertNull(groupedChildRef.selectedPath());
    assertEquals("Spheres", groupedChildRef.effectiveLabel(), "inherits the group's view");
    assertEquals(Resolve.canonicalPath(spheres), groupedChildRef.effectivePath());
  }

  private static void assertMatchesLxsOwnAnswer(LX lx, Views.ViewRef ref, LXModel expected) {
    LXModel resolved;
    if (ref.effectivePath() == null) {
      resolved = lx.getModel();
    } else {
      LXViewDefinition def = (LXViewDefinition) Resolve.component(lx, ref.effectivePath());
      resolved = def.getModelView();
    }
    assertSame(expected, resolved, "effectivePath must resolve to exactly what getModelView() returns");
  }
}
