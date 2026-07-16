package lxmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPath;
import heronarts.lx.parameter.LXParameter;

/**
 * Resolves a canonical LX path (the addressing convention from
 * {@code docs/tool-conventions.md}, e.g. {@code /lx/mixer/channel/1/fader}) to a live
 * component or parameter. The prerequisite primitive for every path-taking mutation.
 *
 * <p>Failures are typed {@link ResolveException}s; the tool seam ({@code lxmcp.tools.Tools})
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
    // Empty segments (trailing slash, "//") would escape LXPath.get as an untyped
    // IllegalArgumentException from the engine's path walker.
    if (rooted.startsWith("/lx/")) {
      for (String segment : rooted.substring(4).split("/", -1)) {
        if (segment.isEmpty()) {
          throw new ResolveException(Failure.INVALID_PATH,
              "Path has an empty segment: " + path);
        }
      }
    }
    LXPath object = LXPath.get(lx, rooted);
    if (object == null) {
      throw new ResolveException(Failure.NOT_FOUND, "No object at path: " + path);
    }
    // LX's path walker returns a parameter even when segments remain after it, which
    // would let a typo'd over-long path silently address the parent parameter — fatal
    // once mutations resolve through here. Segment counts match on any full resolution.
    if (segments(object.getCanonicalPath()) != segments(rooted)) {
      throw new ResolveException(Failure.NOT_FOUND,
          "No object at path: " + path + " (resolves only up to " + object.getCanonicalPath() + ")");
    }
    return object;
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
