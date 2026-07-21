package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;

/**
 * Read-only snapshot of the model tree: the geometry hierarchy the rig renders onto. Every
 * {@link LXModel} node (root, or any submodel reachable via {@code children}) carries points,
 * bounds, and tags; this module walks that tree and flattens it into depth-limited,
 * detached records safe to read off the engine thread.
 */
public final class Model {

  public record TagInfo(String tag, int count) {}

  /**
   * One model node, depth-limited: {@code children} is {@code null} when the walk stopped
   * before descending (depth exhausted), even though {@code childCount} still reports how
   * many children exist. {@code firstIndex}/{@code lastIndex} are {@code null} for an empty
   * node ({@code size == 0}), as is {@code contiguous}. {@code contiguous} is {@code true}
   * when the node's point indices form an unbroken run (so {@code [firstIndex, lastIndex]}
   * is exactly the node's points, in order); it's {@code false} for strided submodels (e.g.
   * a grid column) whose indices are interleaved with siblings' within that range.
   */
  public record NodeInfo(String path, List<String> tags, int size, Integer firstIndex, Integer lastIndex,
      Boolean contiguous, float xMin, float xMax, float xRange, float yMin, float yMax, float yRange,
      float zMin, float zMax, float zRange, float cx, float cy, float cz,
      Map<String, String> metaData, int childCount, List<NodeInfo> children) {}

  public record RootInfo(String modelName, boolean isStatic, int totalPoints, int fixtureCount,
      List<TagInfo> tagVocabulary) {}

  private Model() {}

  /**
   * Find the submodel whose {@link LXModel#getPath()} equals {@code path}, searching the
   * whole tree beneath {@code root} (a submodel's path is only meaningful relative to the
   * root it was addressed from, so the search always starts there). Returns {@code null}
   * when nothing matches.
   */
  public static LXModel findByPath(LXModel root, String path) {
    if (root.getPath().equals(path)) {
      return root;
    }
    for (LXModel child : root.children) {
      LXModel found = findByPath(child, path);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /** Describe {@code model} and, while {@code depth} remains, its children recursively. */
  public static NodeInfo describeNode(LXModel model, int depth) {
    List<NodeInfo> children = null;
    if (depth > 0) {
      children = new ArrayList<>();
      for (LXModel child : model.children) {
        children.add(describeNode(child, depth - 1));
      }
    }
    Integer firstIndex = null;
    Integer lastIndex = null;
    Boolean contiguous = null;
    if (model.size > 0) {
      firstIndex = model.points[0].index;
      lastIndex = model.points[model.size - 1].index;
      contiguous = (lastIndex - firstIndex + 1) == model.size;
    }
    return new NodeInfo(
        model.getPath(),
        List.copyOf(model.tags),
        model.size,
        firstIndex,
        lastIndex,
        contiguous,
        model.xMin, model.xMax, model.xRange,
        model.yMin, model.yMax, model.yRange,
        model.zMin, model.zMax, model.zRange,
        model.cx, model.cy, model.cz,
        Map.copyOf(model.metaData),
        model.children.length,
        children);
  }

  /** Root-only facts: the addressed node's siblings-of-scope, not repeated per submodel. */
  public static RootInfo describeRoot(LX lx) {
    LXModel root = lx.getModel();
    return new RootInfo(
        lx.structure.modelName.getString(),
        lx.structure.isStatic.isOn(),
        root.size,
        lx.structure.fixtures.size(),
        collectTags(root));
  }

  /** Distinct tags across the loaded model, collected recursively, with occurrence counts. */
  public static List<TagInfo> collectTags(LXModel model) {
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
