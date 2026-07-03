package lxmcp.domain;

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
