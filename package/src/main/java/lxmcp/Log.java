package lxmcp;

import heronarts.lx.LX;

/**
 * Thin wrapper around {@link LX#log}/{@link LX#error} that applies a single {@link #PREFIX}
 * consistently — house style per docs/lx-coding-guidelines.md §9 ("a {@code PREFIX} constant
 * plus {@code log()}/{@code error()} helpers wrapping {@code LX.log}/{@code LX.error}, not
 * inline {@code "[Name] "} literals at every call site").
 */
public final class Log {

  private static final String PREFIX = "[LX-MCP] ";

  private Log() {}

  public static void log(String message) {
    LX.log(PREFIX + message);
  }

  public static void error(String message) {
    LX.error(PREFIX + message);
  }

  public static void error(Throwable x, String message) {
    LX.error(x, PREFIX + message);
  }
}
