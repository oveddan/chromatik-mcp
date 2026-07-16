package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.model.LXView;
import heronarts.lx.pattern.color.SolidPattern;
import heronarts.lx.structure.view.LXViewDefinition;

class ViewsTest {

  private LX lx;

  private LX newTaggedLx() {
    List<LXPoint> cube1Points = List.of(new LXPoint(0, 0, 0), new LXPoint(1, 0, 0));
    List<LXPoint> cube2Points = List.of(new LXPoint(0, 1, 0), new LXPoint(1, 1, 0));
    LXModel cube1 = new LXModel(cube1Points, "cube");
    LXModel cube2 = new LXModel(cube2Points, "cube");
    List<LXPoint> allPoints = new ArrayList<>();
    allPoints.addAll(cube1Points);
    allPoints.addAll(cube2Points);
    LXModel root = new LXModel(allPoints, new LXModel[] { cube1, cube2 });
    this.lx = new LX(root);
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
  void describesViewsWithSelectorRoundTripAndLiveMatchFeedback() {
    LX lx = newTaggedLx();
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
    LX lx = newTaggedLx();

    Views.ViewsSnapshot snapshot = Views.describe(lx);
    List<String> tags = snapshot.modelTags().stream().map(Views.TagInfo::tag).toList();
    assertTrue(tags.contains("cube"), "tags: " + tags);
    Views.TagInfo cubeTag = snapshot.modelTags().stream()
        .filter(t -> t.tag().equals("cube")).findFirst().orElseThrow();
    assertEquals(2, cubeTag.count());
  }

  @Test
  void assignmentsListDevicesReferencingAView() {
    LX lx = newTaggedLx();
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
    LX lx = newTaggedLx();
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
    LX lx = newTaggedLx();

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
    LX lx = newTaggedLx();

    Views.ViewInfo info = Views.addView(
        lx, "Nope", "no-such-tag", LXView.Normalization.RELATIVE, LXView.Orientation.GLOBAL);

    assertEquals(0, info.numFixtures());
    assertEquals(0, info.numGroups());
  }

  @Test
  void removeViewRemovesItFromTheEngine() {
    LX lx = newTaggedLx();
    LXViewDefinition view = lx.structure.views.addView();
    view.selector.setValue("cube");

    Views.removeView(lx, view);

    assertTrue(lx.structure.views.views.isEmpty());
  }

  @Test
  void removeViewReassignsADeviceSelectorToWhateverNowOccupiesTheOldIndex() {
    LX lx = newTaggedLx();
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
    LX lx = newTaggedLx();
    lx.structure.views.addView();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.addPattern(new SolidPattern(lx));

    Views.ViewsSnapshot snapshot = Views.describe(lx);
    assertTrue(snapshot.assignments().isEmpty(), "assignments: " + snapshot.assignments());
  }
}
