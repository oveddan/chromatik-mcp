package lxmcp.tools;

import java.util.function.Supplier;

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

  /**
   * Success payload plus a lazily-encoded PNG, delivered as MCP ImageContent alongside the
   * structured payload. The seam invokes {@code png} on the HTTP worker thread, after the
   * handler has left the engine thread — so the supplier must close only over immutable
   * data (a detached snapshot), never over live LX state.
   */
  record OkImage<T>(T value, Supplier<byte[]> png) implements Result<T> {}

  record Error<T>(String code, String message) implements Result<T> {}

  static <T> Result<T> ok(T value) {
    return new Ok<>(value);
  }

  static <T> Result<T> okImage(T value, Supplier<byte[]> png) {
    return new OkImage<>(value, png);
  }

  static <T> Result<T> error(String code, String message) {
    return new Error<>(code, message);
  }
}
