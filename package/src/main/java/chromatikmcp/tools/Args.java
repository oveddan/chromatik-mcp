package chromatikmcp.tools;

import java.util.Map;

import chromatikmcp.domain.Resolve;
import chromatikmcp.domain.Resolve.ResolveException;

/**
 * Required/optional argument parsing shared by every tool handler's {@code handle(...)},
 * so the same missing/mis-typed-argument check isn't hand-rolled per call site. Throws
 * {@link ResolveException} (not a plain {@code RuntimeException}) so these failures map to
 * {@code invalid_argument} at the {@link Tools} seam, exactly like a {@link Resolve} failure.
 * Always builds the exception via {@link Resolve#invalidArgument(String)} — never
 * {@code TYPE_MISMATCH}'s siblings — because argument parsing here has no basis for deciding
 * a path wasn't found or was malformed; only {@link Resolve}'s own resolution logic does that.
 */
final class Args {

  private Args() {}

  /** Required string argument, e.g. a canonical path. */
  static String requireString(Map<String, Object> args, String name) {
    if (!(args.get(name) instanceof String value)) {
      throw Resolve.invalidArgument("Required string argument: " + name);
    }
    return value;
  }

  /**
   * Required integer argument. Rejects a non-integral number (e.g. {@code 1.5}) rather than
   * silently truncating it — a caller computing an index as {@code count/2} deserves
   * {@code invalid_argument}, not a mutation at the wrong position. Also rejects a value that
   * is integral but outside {@code int} range (e.g. a {@code Long} produced by Jackson for a
   * JSON integer literal past {@code Integer.MAX_VALUE}, or {@code Double.POSITIVE_INFINITY},
   * which passes an integrality check via {@code rint} but is not representable as an
   * {@code int}) — {@code Number.intValue()} narrows such values by taking the low 32 bits
   * (for a {@code Long}) or saturating (for a {@code Double}) rather than failing, which would
   * silently produce the wrong index.
   */
  static int requireInt(Map<String, Object> args, String name) {
    if (!(args.get(name) instanceof Number number)) {
      throw Resolve.invalidArgument("Required integer argument: " + name);
    }
    return intValue(number, "Required integer argument: " + name);
  }

  /**
   * Optional integer argument: {@code defaultValue} if absent, else the same strict
   * validation as {@link #requireInt}.
   */
  static int optionalInt(Map<String, Object> args, String name, int defaultValue) {
    Object value = args.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Number number)) {
      throw Resolve.invalidArgument("Optional integer argument: " + name);
    }
    return intValue(number, "Optional integer argument: " + name);
  }

  private static int intValue(Number number, String message) {
    double d = number.doubleValue();
    if (d != Math.rint(d) || Double.isInfinite(d)) {
      throw Resolve.invalidArgument(message);
    }
    if (d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
      throw Resolve.invalidArgument(message + " (must be within int range)");
    }
    return number.intValue();
  }

  /** Optional string argument: {@code null} if absent, else must be a string. */
  static String optionalString(Map<String, Object> args, String name) {
    return optionalString(args, name, name + " must be a string");
  }

  /** Optional string argument with a caller-supplied message for the wrong-type case. */
  static String optionalString(Map<String, Object> args, String name, String message) {
    Object value = args.get(name);
    if (value != null && !(value instanceof String)) {
      throw Resolve.invalidArgument(message);
    }
    return (String) value;
  }
}
