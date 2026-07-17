package chromatikmcp.domain;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;

/** Shared perform-and-verify for command-backed mutation primitives. */
final class Commands {

  private Commands() {}

  /**
   * Perform {@code command} and verify it committed; throws if it did not.
   * Use {@link #applied} instead when the caller maps the failure to a typed error.
   */
  static void perform(LX lx, LXCommand command) {
    if (!applied(lx, command)) {
      throw new IllegalStateException(command.getClass().getSimpleName() + " did not apply");
    }
  }

  /**
   * Perform {@code command} and report whether it committed. perform() swallows command
   * failures and clears the undo stack (docs/tool-conventions.md); the committed command
   * sitting on top of the stack is the success detector.
   */
  static boolean applied(LX lx, LXCommand command) {
    lx.command.perform(command);
    return lx.command.getUndoCommand() == command;
  }
}
