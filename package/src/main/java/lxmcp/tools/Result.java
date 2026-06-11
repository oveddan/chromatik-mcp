package lxmcp.tools;

/**
 * Tagged result for tool handlers: expected failures are values, not exceptions, so
 * nothing crosses the MCP boundary as a stack trace. Unexpected exceptions are mapped to
 * {@link #INTERNAL} at the seam in {@link Tools}.
 */
public sealed interface Result<T> {

  /** Stable error codes, shared across tools so agent clients can dispatch on them. */
  String NOT_FOUND = "not_found";
  String INVALID_ARGUMENT = "invalid_argument";
  String INTERNAL = "internal";

  record Ok<T>(T value) implements Result<T> {}

  record Error<T>(String code, String message) implements Result<T> {}

  static <T> Result<T> ok(T value) {
    return new Ok<>(value);
  }

  static <T> Result<T> error(String code, String message) {
    return new Error<>(code, message);
  }
}
