package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPath;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.structure.LXFixture;
import heronarts.lx.structure.LXStructure;

/**
 * Resolves a canonical LX path (the addressing convention from
 * {@code docs/tool-conventions.md}, e.g. {@code /lx/mixer/channel/1/fader}) to a live
 * component or parameter. The prerequisite primitive for every path-taking mutation.
 *
 * <p>Failures are typed {@link ResolveException}s; the tool seam ({@code chromatikmcp.tools.Tools})
 * maps them to {@code Result.error} wire codes, so handlers and downstream primitives can
 * resolve-and-use without unwrapping.
 */
public final class Resolve {

  public enum Failure {
    /** Nothing exists at the path. */
    NOT_FOUND,
    /** Something exists, but not of the requested kind — or a supplied value's type
     * doesn't match the resolved parameter. Both map to {@code invalid_argument}. */
    TYPE_MISMATCH,
    /** The path is malformed (empty, or not rooted at /lx). */
    INVALID_PATH;
  }

  public static final class ResolveException extends RuntimeException {
    public final Failure failure;

    ResolveException(Failure failure, String message) {
      super(message);
      this.failure = failure;
    }
  }

  private Resolve() {}

  /** Resolve to a parameter; call on the engine thread. */
  public static LXParameter parameter(LX lx, String path) {
    LXPath object = get(lx, path);
    if (!(object instanceof LXParameter parameter)) {
      throw new ResolveException(Failure.TYPE_MISMATCH,
          "Not a parameter at path: " + path + " (found " + kind(object) + ")");
    }
    return parameter;
  }

  /** Resolve to a component; call on the engine thread. */
  public static LXComponent component(LX lx, String path) {
    LXPath object = get(lx, path);
    if (!(object instanceof LXComponent component)) {
      throw new ResolveException(Failure.TYPE_MISMATCH,
          "Not a component at path: " + path + " (found " + kind(object) + ")");
    }
    return component;
  }

  /** Resolve to a component of a specific type; call on the engine thread. */
  public static <T extends LXComponent> T component(LX lx, String path, Class<T> type) {
    LXComponent component = component(lx, path);
    if (!type.isInstance(component)) {
      throw new ResolveException(Failure.TYPE_MISMATCH,
          "Not a " + type.getSimpleName() + " at path: " + path
              + " (found " + component.getClass().getSimpleName() + ")");
    }
    return type.cast(component);
  }

  /**
   * Resolve a registered class by full class name, {@code Class.getSimpleName()}, or the
   * display name ({@code LXComponent.getComponentName}) that {@code list_available_*}
   * tools advertise — in that priority order. A full-name match always wins, even when a
   * short name would also match. Otherwise, exactly one short-name match wins; more than
   * one is ambiguous.
   *
   * @throws ResolveException {@code unknownFailure} for zero matches ({@code unknownMessage}
   *     — callers keep their existing failure kind and wording for this case) or
   *     TYPE_MISMATCH for more than one short-name match (message lists the full names of
   *     all candidates).
   */
  public static <T extends LXComponent> Class<? extends T> resolveClassName(
      Iterable<Class<? extends T>> registry, String query, Failure unknownFailure,
      String unknownMessage) {
    for (Class<? extends T> clazz : registry) {
      if (clazz.getName().equals(query)) {
        return clazz;
      }
    }
    List<Class<? extends T>> candidates = new ArrayList<>();
    for (Class<? extends T> clazz : registry) {
      if (clazz.getSimpleName().equals(query) || LXComponent.getComponentName(clazz).equals(query)) {
        candidates.add(clazz);
      }
    }
    if (candidates.size() == 1) {
      return candidates.get(0);
    }
    if (candidates.size() > 1) {
      StringBuilder names = new StringBuilder();
      for (Class<? extends T> clazz : candidates) {
        if (names.length() > 0) {
          names.append(", ");
        }
        names.append(clazz.getName());
      }
      throw new ResolveException(Failure.TYPE_MISMATCH,
          "Ambiguous type name: " + query + " matches [" + names
              + "] — use a full class name");
    }
    throw new ResolveException(unknownFailure, unknownMessage);
  }

  private static LXPath get(LX lx, String path) {
    if (path == null || path.isEmpty()) {
      throw new ResolveException(Failure.INVALID_PATH, "Path must not be empty");
    }
    String rooted = (path.charAt(0) == '/') ? path : "/" + path;
    if (!rooted.equals("/lx") && !rooted.startsWith("/lx/")) {
      throw new ResolveException(Failure.INVALID_PATH,
          "Canonical paths are rooted at /lx (got: " + path + ")");
    }
    String[] segments = rooted.startsWith("/lx/") ? rooted.substring(4).split("/", -1) : new String[0];
    // Empty segments (trailing slash, "//") would escape LXPath.get as an untyped
    // IllegalArgumentException from the engine's path walker.
    for (String segment : segments) {
      if (segment.isEmpty()) {
        throw new ResolveException(Failure.INVALID_PATH,
            "Path has an empty segment: " + path);
      }
    }
    LXPath object;
    if (segments.length >= 1 && segments[0].equals("structure")) {
      // LXPath.get(...) only roots at lx.engine — the structure tree (views, fixtures,
      // outputs, …) is unreachable through it by design, so walk lx.structure directly.
      object = (segments.length == 1)
          ? lx.structure
          : walk(lx.structure, List.of(segments).subList(1, segments.length).toArray(new String[0]), 0);
    } else {
      object = LXPath.get(lx, rooted);
    }
    if (object == null) {
      throw new ResolveException(Failure.NOT_FOUND, "No object at path: " + path);
    }
    // LX's path walker returns a parameter even when segments remain after it, which
    // would let a typo'd over-long path silently address the parent parameter — fatal
    // once mutations resolve through here. Segment counts match on any full resolution.
    String resolvedPath = canonicalPath(object);
    if (segments(resolvedPath) != segments(rooted)) {
      throw new ResolveException(Failure.NOT_FOUND,
          "No object at path: " + path + " (resolves only up to " + resolvedPath + ")");
    }
    return object;
  }

  /**
   * The canonical MCP-facing path for {@code object}: {@code LXPath.getCanonicalPath()},
   * rooted at {@code /lx}. Structure-tree objects (lx.structure and its descendants —
   * views, fixtures, outputs, …) are not parented under {@code lx.engine}, so their raw
   * {@code getCanonicalPath()} omits the {@code /lx} root that every other tool-facing
   * path carries (docs/tool-conventions.md); this normalizes that one exception. Domain
   * code building payloads for structure-tree objects should call this rather than
   * {@code getCanonicalPath()} directly.
   */
  public static String canonicalPath(LXPath object) {
    String raw = object.getCanonicalPath();
    return (raw.equals("/lx") || raw.startsWith("/lx/")) ? raw : "/lx" + raw;
  }

  /**
   * {@code object.getCanonicalPath()}, or {@code null} when {@code object} or any ancestor in
   * its parent chain was never path-registered ({@code getPath() == null}). Unregistered
   * objects (e.g. a read-only derived parameter that's never {@code addParameter}'d, or an
   * unregistered ancestor further up the chain) make {@code getCanonicalPath()} build a
   * literal {@code "null"} segment (LXPath.java:85-93) rather than failing — snapshot builders
   * call this instead of the raw method so payloads omit the key rather than emit that bogus
   * path (docs/tool-conventions.md's "omit the key, never emit null" rule). All current
   * callers address {@code lx.engine} descendants, where the raw path already carries the
   * {@code /lx} root; unlike {@link #canonicalPath(LXPath)}, this does not apply that method's
   * structure-tree root normalization.
   */
  public static String canonicalPathOrNull(LXPath object) {
    if (object.getPath() == null) {
      return null;
    }
    for (LXComponent parent = object.getParent(); parent != null; parent = parent.getParent()) {
      if (parent.getPath() == null) {
        return null;
      }
    }
    return object.getCanonicalPath();
  }

  /**
   * Walks a component tree using its public {@code children}/{@code childArrays}/
   * {@code getParameter} accessors — a from-outside-the-package equivalent of
   * {@code LXComponent.path(String[], int)} (package-private, LXComponent.java:961), needed
   * because the structure tree (lx.structure.views, .fixtures, .outputs, …) is unreachable
   * through {@code LXPath.get}, which only roots at {@code lx.engine}.
   */
  private static LXPath walk(LXComponent component, String[] parts, int index) {
    if (index < 0 || index >= parts.length) {
      return null;
    }
    String key = parts[index];
    LXParameter parameter = component.getParameter(key);
    if (parameter != null) {
      if (parameter instanceof AggregateParameter && index < parts.length - 1) {
        LXParameter sub = component.getParameter(key + "/" + parts[index + 1]);
        if (sub != null) {
          return sub;
        }
      }
      return parameter;
    }
    LXComponent child = component.children.get(key);
    if (child != null) {
      return (index == parts.length - 1) ? child : walk(child, parts, index + 1);
    }
    if (component instanceof LXStructure structure && "fixture".equals(key)) {
      // LXStructure never calls addArray("fixture", ...) — only LXViewEngine registers a
      // child array ("view") — so structure.fixtures is otherwise unreachable through
      // childArrays. Index into it directly, same 1-indexed convention as below.
      return walkIndexed(structure.fixtures, parts, index);
    }
    if (component instanceof LXFixture fixture && "fixture".equals(key)) {
      // Subfixtures (a JsonFixture's .lxf-declared components, recursively) aren't in
      // LXComponent.children/childArrays either — see Fixtures.children.
      return walkIndexed(Fixtures.children(fixture), parts, index);
    }
    List<? extends LXComponent> array = component.childArrays.get(key);
    if (array != null) {
      return walkIndexed(array, parts, index);
    }
    return null;
  }

  private static LXPath walkIndexed(List<? extends LXComponent> array, String[] parts, int index) {
    int nextIndex = index + 1;
    if (nextIndex < parts.length) {
      try {
        // Path indices are 1-indexed (OSC/UI convention) — see LXComponent.path().
        int arrIndex = Integer.parseInt(parts[nextIndex]) - 1;
        if (arrIndex >= 0 && arrIndex < array.size()) {
          LXComponent arrayChild = array.get(arrIndex);
          if (nextIndex == parts.length - 1) {
            return arrayChild;
          }
          return (arrayChild != null) ? walk(arrayChild, parts, nextIndex + 1) : null;
        }
      } catch (NumberFormatException nfx) {
        return null;
      }
    }
    return null;
  }

  private static int segments(String rooted) {
    int count = 0;
    for (int i = 0; i < rooted.length(); ++i) {
      if (rooted.charAt(i) == '/') {
        ++count;
      }
    }
    return count;
  }

  private static String kind(LXPath object) {
    return object.getClass().getSimpleName();
  }
}
