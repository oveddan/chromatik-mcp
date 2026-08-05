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

  /**
   * Required number argument. Rejects NaN/Infinity — a non-finite value can only come from
   * a caller-side computation bug, and letting it through would corrupt engine state (e.g.
   * a NaN normalized automation value) rather than fail the call.
   */
  static double requireDouble(Map<String, Object> args, String name) {
    if (!(args.get(name) instanceof Number number)
        || !Double.isFinite(number.doubleValue())) {
      throw Resolve.invalidArgument("Required finite number argument: " + name);
    }
    return number.doubleValue();
  }

  /** Required boolean argument. */
  static boolean requireBoolean(Map<String, Object> args, String name) {
    if (!(args.get(name) instanceof Boolean value)) {
      throw Resolve.invalidArgument("Required boolean argument: " + name);
    }
    return value;
  }

  /** Optional boolean argument: {@code defaultValue} if absent, else must be a boolean. */
  static boolean optionalBoolean(Map<String, Object> args, String name, boolean defaultValue) {
    Object value = args.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Boolean b)) {
      throw Resolve.invalidArgument("Optional boolean argument: " + name);
    }
    return b;
  }

  /**
   * Optional bounded number: {@code null} if absent. Out-of-range is rejected rather than
   * clamp-echoed: the bounded aspects using this are unit-scaled (a normalized value, a
   * shaping factor), so an out-of-range number almost always means the caller sent a RAW
   * parameter value — a silent clamp would look like success at the wrong value.
   */
  static Double optionalBoundedNumber(Map<String, Object> args, String name, double min, double max) {
    Object value = args.get(name);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
      throw Resolve.invalidArgument("Optional number argument: " + name);
    }
    double d = number.doubleValue();
    if (d < min || d > max) {
      throw Resolve.invalidArgument(name + " must be within [" + min + ", " + max + "]: " + d);
    }
    return d;
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

  /**
   * Required object argument (e.g. a cursor spec for {@code Cursors.parse}). The SDK has
   * already schema-validated the value's shape; this recovers the typed map.
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> requireMap(Map<String, Object> args, String name) {
    if (!(args.get(name) instanceof Map<?, ?> value)) {
      throw Resolve.invalidArgument("Required object argument: " + name);
    }
    return (Map<String, Object>) value;
  }

  /** Optional object argument: {@code null} if absent, else must be an object. */
  @SuppressWarnings("unchecked")
  static Map<String, Object> optionalMap(Map<String, Object> args, String name) {
    Object value = args.get(name);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Map<?, ?> map)) {
      throw Resolve.invalidArgument("Optional object argument: " + name);
    }
    return (Map<String, Object>) map;
  }
}
