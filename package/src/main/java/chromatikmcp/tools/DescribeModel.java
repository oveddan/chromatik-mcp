package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;

import chromatikmcp.domain.Model;

public final class DescribeModel implements LxTool {

  private static final int DEFAULT_DEPTH = 2;
  private static final int MAX_DEPTH = 10;

  @Override
  public String name() {
    return "describe_model";
  }

  @Override
  public String description() {
    return "The model tree: the geometry hierarchy the rig renders onto, from the whole "
        + "installation down to individual points. Each node reports 'tags' (the same "
        + "vocabulary get_views' selectors match against — e.g. a selector 'cube' matches "
        + "every node tagged 'cube'), 'size' (point count), 'pointIndexRange' ([firstIndex, "
        + "lastIndex], the bounding range of this node's point indices in the same global "
        + "color buffer get_frame reads back — NOT a claim that the node owns every index in "
        + "that range; check 'contiguous' before slicing the buffer), 'contiguous' (true when "
        + "the node's indices form an unbroken run equal to 'size', so the range is exactly "
        + "its points; false when they're interleaved with other nodes' indices, as for a "
        + "grid column submodel — both fields are omitted for an empty node), 'bounds'/"
        + "'center' (spatial extent), and 'childCount' (how many submodels sit below, even when "
        + "'children' itself is absent). Pass 'path' (a model node path, as emitted in this "
        + "tool's own 'path' field — not a component canonical path, since LXModel isn't "
        + "addressable through the component tree) to describe a submodel instead of the "
        + "whole installation; omit it for the root, which also reports 'modelName', "
        + "'isStatic', 'totalPoints', 'fixtureCount', and 'tagVocabulary' (every distinct tag "
        + "in the project with its occurrence count). 'depth' (default " + DEFAULT_DEPTH
        + ", max " + MAX_DEPTH + ") bounds how many levels of children are expanded below the "
        + "addressed node — real installations can have thousands of submodels, so an "
        + "unbounded dump would blow a client's context. When 'childCount' is nonzero but "
        + "'children' is missing, depth ran out; re-call with 'path' set to one of the "
        + "reported child paths (or a higher 'depth') to keep descending.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Model node path (as emitted in this tool's own 'path' field); omit for the whole "
            + "installation's root"));
    Map<String, Object> depthSchema = new LinkedHashMap<>();
    depthSchema.put("type", "integer");
    depthSchema.put("description", "Levels of children to expand below the addressed node (default "
        + DEFAULT_DEPTH + ", clamped to " + MAX_DEPTH + "; must be >= 0)");
    properties.put("depth", depthSchema);
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    int depth = DEFAULT_DEPTH;
    if (args.get("depth") instanceof Number n) {
      depth = n.intValue();
    }
    if (depth < 0) {
      return Result.error(Result.INVALID_ARGUMENT, "depth must be >= 0");
    }
    depth = Math.min(depth, MAX_DEPTH);

    LXModel root = lx.getModel();
    Object pathArg = args.get("path");
    LXModel target = root;
    boolean isRoot = true;
    if (pathArg != null) {
      if (!(pathArg instanceof String path)) {
        return Result.error(Result.INVALID_ARGUMENT, "path must be a string");
      }
      LXModel found = Model.findByPath(root, path);
      if (found == null) {
        return Result.error(Result.NOT_FOUND, "no model node at path " + path);
      }
      target = found;
      isRoot = (found == root);
    }

    Map<String, Object> payload = toMap(Model.describeNode(target, depth));
    if (isRoot) {
      Model.RootInfo rootInfo = Model.describeRoot(lx);
      payload.put("modelName", rootInfo.modelName());
      payload.put("isStatic", rootInfo.isStatic());
      payload.put("totalPoints", rootInfo.totalPoints());
      payload.put("fixtureCount", rootInfo.fixtureCount());
      List<Map<String, Object>> tagVocabulary = new ArrayList<>();
      for (Model.TagInfo tag : rootInfo.tagVocabulary()) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tag", tag.tag());
        entry.put("count", tag.count());
        tagVocabulary.add(entry);
      }
      payload.put("tagVocabulary", tagVocabulary);
    }
    return Result.ok(payload);
  }

  private static Map<String, Object> toMap(Model.NodeInfo node) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("path", node.path());
    map.put("tags", node.tags());
    map.put("size", node.size());
    if (node.firstIndex() != null) {
      map.put("pointIndexRange", List.of(node.firstIndex(), node.lastIndex()));
      map.put("contiguous", node.contiguous());
    }
    Map<String, Object> bounds = new LinkedHashMap<>();
    bounds.put("xMin", node.xMin());
    bounds.put("xMax", node.xMax());
    bounds.put("xRange", node.xRange());
    bounds.put("yMin", node.yMin());
    bounds.put("yMax", node.yMax());
    bounds.put("yRange", node.yRange());
    bounds.put("zMin", node.zMin());
    bounds.put("zMax", node.zMax());
    bounds.put("zRange", node.zRange());
    map.put("bounds", bounds);
    Map<String, Object> center = new LinkedHashMap<>();
    center.put("x", node.cx());
    center.put("y", node.cy());
    center.put("z", node.cz());
    map.put("center", center);
    if (!node.metaData().isEmpty()) {
      map.put("metaData", node.metaData());
    }
    map.put("childCount", node.childCount());
    if (node.children() != null) {
      List<Map<String, Object>> children = new ArrayList<>();
      for (Model.NodeInfo child : node.children()) {
        children.add(toMap(child));
      }
      map.put("children", children);
    }
    return map;
  }
}
