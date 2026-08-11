package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.pattern.color.GradientPattern;

/** Domain regressions for issue #200's explicit channel-group lifecycle. */
class ChannelGroupingTest extends HeadlessLxTest {

  @Test
  void groupChannelsUsesExplicitOrderReportsPathChangesAndIsNotUndoable() {
    LX lx = newHeadlessLx();
    LXChannel first = channelWithPattern(lx);
    LXChannel skipped = channelWithPattern(lx);
    LXChannel last = channelWithPattern(lx);
    String firstBefore = first.getCanonicalPath();
    String skippedBefore = skipped.getCanonicalPath();
    String lastBefore = last.getCanonicalPath();
    int originalCount = lx.engine.mixer.channels.size();
    lx.command.clear();

    Channels.GroupChannelsResult result = Channels.groupChannels(
        lx, List.of(lastBefore, firstBefore));

    LXGroup group = result.group();
    assertEquals(originalCount + 1, lx.engine.mixer.channels.size());
    assertSame(group, lx.engine.mixer.channels.get(0),
        "the leftmost selected channel determines group insertion regardless of input order");
    assertEquals(List.of(first, last), group.channels, "existing mixer order is preserved");
    assertSame(group, last.getGroup());
    assertSame(group, first.getGroup());
    assertNull(skipped.getGroup());
    assertTrue(lx.command.isDirty(), "the direct mutation still marks the project dirty");
    assertPathChange(result, first.getId(), firstBefore, first.getCanonicalPath());
    assertPathChange(result, skipped.getId(), skippedBefore, skipped.getCanonicalPath());
    assertTrue(result.oscChanges().stream().noneMatch(change ->
        change.componentId() == last.getId()),
        "a selected channel that keeps the same positional path is not a change");
    assertTrue(result.oscChanges().stream().anyMatch(change ->
        change.componentId() == first.patterns.get(0).getId()),
        "descendant paths move with their channel");

    lx.command.undo();
    assertSame(group, lx.engine.mixer.channels.get(0),
        "explicit-list grouping deliberately creates no undo command");
  }

  @Test
  void groupChannelsValidatesWholeRequestBeforeMutation() {
    LX lx = newHeadlessLx();
    LXChannel first = channelWithPattern(lx);
    LXChannel second = channelWithPattern(lx);
    int before = lx.engine.mixer.channels.size();

    Resolve.ResolveException empty = assertThrows(Resolve.ResolveException.class,
        () -> Channels.groupChannels(lx, List.of()));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, empty.failure);

    Resolve.ResolveException duplicate = assertThrows(Resolve.ResolveException.class,
        () -> Channels.groupChannels(lx,
            List.of(first.getCanonicalPath(), first.getCanonicalPath())));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, duplicate.failure);
    assertEquals(before, lx.engine.mixer.channels.size());
    assertNull(first.getGroup());
    assertNull(second.getGroup());

    LXGroup group = lx.engine.mixer.addGroup(List.of(first));
    String secondPath = second.getCanonicalPath();
    Resolve.ResolveException alreadyGrouped = assertThrows(Resolve.ResolveException.class,
        () -> Channels.groupChannels(lx, List.of(secondPath, first.getCanonicalPath())));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, alreadyGrouped.failure);
    assertEquals(before + 1, lx.engine.mixer.channels.size(),
        "a later invalid member must not create another group");
    assertNull(second.getGroup());

    Resolve.ResolveException groupPath = assertThrows(Resolve.ResolveException.class,
        () -> Channels.groupChannels(lx, List.of(group.getCanonicalPath())));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, groupPath.failure);
  }

  @Test
  void ungroupChannelsDissolvesGroupAndUndoRestoresIt() {
    LX lx = newHeadlessLx();
    LXChannel first = channelWithPattern(lx);
    LXChannel second = channelWithPattern(lx);
    LXGroup group = lx.engine.mixer.addGroup(List.of(first, second));
    int groupId = group.getId();
    String groupPath = group.getCanonicalPath();
    String firstBefore = first.getCanonicalPath();
    lx.command.clear();

    Channels.UngroupChannelsResult result = Channels.ungroupChannels(lx, groupPath);

    assertEquals(groupId, result.groupId());
    assertEquals(groupPath, result.groupPath());
    assertEquals(List.of(first, second), result.channels());
    assertNull(lx.getComponent(groupId));
    assertNull(first.getGroup());
    assertNull(second.getGroup());
    assertPathChange(result.oscChanges(), first.getId(), firstBefore, first.getCanonicalPath());

    lx.command.undo();
    LXGroup restored = assertInstanceOf(LXGroup.class, lx.getComponent(groupId));
    assertEquals(List.of(first, second), restored.channels);
    assertSame(restored, first.getGroup());
    assertSame(restored, second.getGroup());
  }

  @Test
  void ungroupChannelPullsOneMemberOutAndUndoRestoresMembership() {
    LX lx = newHeadlessLx();
    LXChannel first = channelWithPattern(lx);
    LXChannel second = channelWithPattern(lx);
    LXGroup group = lx.engine.mixer.addGroup(List.of(first, second));
    String firstPath = first.getCanonicalPath();
    String secondBefore = second.getCanonicalPath();
    lx.command.clear();

    Channels.UngroupChannelResult result = Channels.ungroupChannel(lx, firstPath);

    assertSame(first, result.channel());
    assertEquals(group.getId(), result.groupId());
    assertEquals(List.of(second), group.channels);
    assertNull(first.getGroup());
    assertSame(first, lx.engine.mixer.channels.get(group.getIndex() + group.channels.size() + 1));
    assertPathChange(result.oscChanges(), second.getId(), secondBefore, second.getCanonicalPath());

    lx.command.undo();
    assertTrue(group.channels.containsAll(List.of(first, second)));
    assertEquals(2, group.channels.size());
    assertSame(group, first.getGroup());
  }

  @Test
  void ungroupRejectsWrongTargetKindsWithoutMutation() {
    LX lx = newHeadlessLx();
    LXChannel channel = channelWithPattern(lx);
    String channelPath = channel.getCanonicalPath();

    Resolve.ResolveException member = assertThrows(Resolve.ResolveException.class,
        () -> Channels.ungroupChannel(lx, channelPath));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, member.failure);

    Resolve.ResolveException whole = assertThrows(Resolve.ResolveException.class,
        () -> Channels.ungroupChannels(lx, channelPath));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, whole.failure);
    assertSame(channel, lx.engine.mixer.channels.get(0));
  }

  private static LXChannel channelWithPattern(LX lx) {
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.addPattern(new GradientPattern(lx));
    return channel;
  }

  private static void assertPathChange(Channels.GroupChannelsResult result, int id,
      String before, String after) {
    assertPathChange(result.oscChanges(), id, before, after);
  }

  private static void assertPathChange(List<PathChange> changes, int id,
      String before, String after) {
    assertTrue(changes.contains(new PathChange(id, before, after)),
        () -> "missing path change for " + id + ": " + before + " -> " + after
            + " in " + changes);
  }
}
