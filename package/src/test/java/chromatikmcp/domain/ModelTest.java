package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.structure.GridFixture;

class ModelTest extends HeadlessLxTest {

  // root -> [cube1 -> [face], cube2], three levels deep so depth-limiting and submodel
  // addressing both have something to exercise.
  @Override
  protected LXModel newModel() {
    List<LXPoint> facePoints = List.of(new LXPoint(0, 0, 0));
    LXModel face = new LXModel(facePoints, "face");

    List<LXPoint> cube1OwnPoints = List.of(new LXPoint(1, 0, 0));
    List<LXPoint> cube1Points = new ArrayList<>(cube1OwnPoints);
    cube1Points.addAll(facePoints);
    LXModel cube1 = new LXModel(cube1Points, new LXModel[] { face }, "cube");

    List<LXPoint> cube2Points = List.of(new LXPoint(0, 1, 0), new LXPoint(1, 1, 0));
    LXModel cube2 = new LXModel(cube2Points, "cube");

    List<LXPoint> allPoints = new ArrayList<>();
    allPoints.addAll(cube1Points);
    allPoints.addAll(cube2Points);
    return new LXModel(allPoints, new LXModel[] { cube1, cube2 }).reindexPoints();
  }

  @Test
  void describesRootNodeShape() {
    LX lx = newHeadlessLx();

    Model.NodeInfo root = Model.describeNode(lx.getModel(), 2);
    assertEquals(lx.getModel().getPath(), root.path());
    assertEquals(4, root.size());
    assertEquals(0, root.firstIndex());
    assertEquals(3, root.lastIndex());
    assertEquals(2, root.childCount());
    assertEquals(2, root.children().size(), "depth 2 expands both cube children");
  }

  @Test
  void depthZeroOmitsChildrenButReportsChildCount() {
    LX lx = newHeadlessLx();

    Model.NodeInfo root = Model.describeNode(lx.getModel(), 0);
    assertEquals(2, root.childCount());
    assertNull(root.children(), "depth exhausted: no children list, but childCount still reported");
  }

  @Test
  void depthLimitsGrandchildrenButNotChildren() {
    LX lx = newHeadlessLx();

    Model.NodeInfo root = Model.describeNode(lx.getModel(), 1);
    Model.NodeInfo cube1 = root.children().stream()
        .filter(n -> n.childCount() == 1)
        .findFirst().orElseThrow();
    assertEquals(1, cube1.childCount());
    assertNull(cube1.children(), "depth exhausted one level down");
  }

  @Test
  void emptyNodeOmitsPointIndexRange() {
    List<LXPoint> facePoints = List.of();
    LXModel empty = new LXModel(facePoints, "empty");
    Model.NodeInfo info = Model.describeNode(empty, 0);
    assertEquals(0, info.size());
    assertNull(info.firstIndex());
    assertNull(info.lastIndex());
    assertNull(info.contiguous());
  }

  @Test
  void findByPathResolvesASubmodelByItsOwnGetPath() {
    LX lx = newHeadlessLx();
    LXModel root = lx.getModel();
    LXModel cube1 = root.children[0];

    LXModel found = Model.findByPath(root, cube1.getPath());

    assertSame(cube1, found);
  }

  @Test
  void findByPathReturnsNullForUnknownPath() {
    LX lx = newHeadlessLx();

    assertNull(Model.findByPath(lx.getModel(), "/no/such/path"));
  }

  @Test
  void describeRootReportsProjectLevelFacts() {
    LX lx = newHeadlessLx();

    Model.RootInfo info = Model.describeRoot(lx);
    assertEquals(4, info.totalPoints());
    assertTrue(info.tagVocabulary().stream().anyMatch(t -> t.tag().equals("cube") && t.count() == 2));
    assertTrue(info.tagVocabulary().stream().anyMatch(t -> t.tag().equals("face") && t.count() == 1));
  }

  @Test
  void stridedColumnSubmodelReportsNonContiguousBoundingRange() {
    LX lx = track(new LX());
    LXCommand.Structure.AddFixture command = new LXCommand.Structure.AddFixture(GridFixture.class);
    int before = lx.structure.fixtures.size();
    lx.command.perform(command);
    if (lx.structure.fixtures.size() != before + 1) {
      throw new IllegalStateException("AddFixture did not add a fixture");
    }
    GridFixture fixture = (GridFixture) lx.structure.fixtures.get(before);
    fixture.numRows.setValue(4);
    fixture.numColumns.setValue(3);

    // GridFixture.toSubmodels() registers row submodels first, then column submodels, as
    // children of the fixture's own model node.
    LXModel gridModel = fixture.getModel();
    LXModel row = gridModel.children[0];
    LXModel column = gridModel.children[fixture.numRows.getValuei()];

    Model.NodeInfo rowInfo = Model.describeNode(row, 0);
    assertEquals(3, rowInfo.size(), "a row has numColumns points");
    assertEquals(Boolean.TRUE, rowInfo.contiguous());
    assertEquals(rowInfo.size(), rowInfo.lastIndex() - rowInfo.firstIndex() + 1);

    Model.NodeInfo columnInfo = Model.describeNode(column, 0);
    assertEquals(4, columnInfo.size(), "a column has numRows points");
    assertEquals(Boolean.FALSE, columnInfo.contiguous());
    assertTrue(columnInfo.lastIndex() - columnInfo.firstIndex() + 1 > columnInfo.size(),
        "column indices are strided, so the bounding range is wider than the point count");
  }

  @Test
  void collectTagsMovedFromViewsStillMatchesGetViewsShape() {
    LX lx = newHeadlessLx();

    List<Model.TagInfo> tags = Model.collectTags(lx.getModel());
    Views.ViewsSnapshot snapshot = Views.describe(lx);

    assertEquals(tags, snapshot.modelTags(), "Views.describe delegates to the same walk");
  }
}
