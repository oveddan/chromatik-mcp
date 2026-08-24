package chromatikmcp.tools;

import chromatikmcp.domain.Cameras;
import chromatikmcp.domain.PointStyle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import chromatikmcp.ServerStatus;
import chromatikmcp.StreamableHttpTestHarness;
import chromatikmcp.engine.EngineExecutor;
import chromatikmcp.mcp.ConnectionTracker;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.model.GridModel;
import io.modelcontextprotocol.spec.McpSchema;

/** Wire-level coverage for issue #200's group lifecycle tools. */
@Timeout(60)
class ChannelGroupingToolsTest {

  @AutoClose("dispose")
  private static LX lx;
  private static StreamableHttpTestHarness harness;

  @BeforeAll
  static void setUp() {
    lx = new LX(new GridModel(4, 4).reindexPoints());
    ConnectionTracker connectionTracker = new ConnectionTracker();
    GetStatus getStatus = new GetStatus(new ServerStatus(),
        () -> connectionTracker.snapshot(System.currentTimeMillis()));
    harness = StreamableHttpTestHarness.startMcp(
        lx, Tools.specifications(
            lx, new EngineExecutor(lx), getStatus, new Cameras(), new PointStyle()),
        Tools.INSTRUCTIONS,
        connectionTracker);
  }

  @AfterAll
  static void tearDown() {
    if (harness != null) {
      harness.close();
    }
  }

  @Test
  void groupUngroupOneAndDissolveRoundTripOverMcp() {
    LXChannel first = lx.engine.mixer.addChannel();
    LXChannel middle = lx.engine.mixer.addChannel();
    LXChannel last = lx.engine.mixer.addChannel();
    String firstBefore = first.getCanonicalPath();
    String middleBefore = middle.getCanonicalPath();
    String lastBefore = last.getCanonicalPath();

    Map<String, Object> grouped = structured(call("group_channels", Map.of(
        "paths", List.of(lastBefore, firstBefore))));
    String groupPath = assertInstanceOf(String.class, grouped.get("path"));
    int groupId = ((Number) grouped.get("id")).intValue();
    assertInstanceOf(String.class, grouped.get("label"));
    List<Map<String, Object>> groupedMembers = maps(grouped.get("channels"));
    assertEquals(List.of(first.getId(), last.getId()), groupedMembers.stream()
        .map(member -> ((Number) member.get("id")).intValue()).toList());
    assertOscChanges(grouped.get("oscChanges"));

    Map<String, Object> listed = structured(call("list_channels", Map.of()));
    Map<String, Object> groupEntry = channelEntryById(listed, groupId);
    assertEquals("group", groupEntry.get("type"));
    assertEquals(groupPath, groupEntry.get("path"));
    assertEquals(groupPath, channelEntryById(listed, first.getId()).get("group"));
    assertEquals(groupPath, channelEntryById(listed, last.getId()).get("group"));
    assertFalse(channelEntryById(listed, middle.getId()).containsKey("group"));

    Map<String, Object> one = structured(call("ungroup_channel", Map.of(
        "path", last.getCanonicalPath())));
    assertEquals(last.getId(), ((Number) one.get("id")).intValue());
    assertEquals(groupId, ((Number) one.get("groupId")).intValue());
    assertEquals(groupPath, one.get("formerGroupPath"));
    assertOscChangeShape(one.get("oscChanges"));
    assertEquals(null, last.getGroup());

    structured(call("undo", Map.of()));
    assertEquals(groupId, last.getGroup().getId(), "ungroup_channel is command-backed");

    Map<String, Object> dissolved = structured(call("ungroup_channels", Map.of(
        "path", ((LXGroup) lx.getComponent(groupId)).getCanonicalPath())));
    assertEquals(groupId, ((Number) dissolved.get("groupId")).intValue());
    assertEquals(groupPath, dissolved.get("removedGroupPath"));
    assertEquals(2, maps(dissolved.get("channels")).size());
    assertOscChanges(dissolved.get("oscChanges"));
    assertEquals(null, lx.getComponent(groupId));

    structured(call("undo", Map.of()));
    assertNotNull(lx.getComponent(groupId), "ungroup_channels is command-backed");
  }

  @Test
  void groupingValidationMapsToInvalidArgument() {
    McpSchema.CallToolResult empty = call("group_channels", Map.of("paths", List.of()));
    assertEquals(Boolean.TRUE, empty.isError(), "minItems schema rejects an empty selection");

    LXChannel channel = lx.engine.mixer.addChannel();
    McpSchema.CallToolResult duplicate = call("group_channels", Map.of(
        "paths", List.of(channel.getCanonicalPath(), channel.getCanonicalPath())));
    assertErrorCode(duplicate, Result.INVALID_ARGUMENT);

    McpSchema.CallToolResult notGrouped = call("ungroup_channel", Map.of(
        "path", channel.getCanonicalPath()));
    assertErrorCode(notGrouped, Result.INVALID_ARGUMENT);
  }

  @Test
  void directGroupingIsExcludedFromApplyOperations() {
    LXChannel channel = lx.engine.mixer.addChannel();
    McpSchema.CallToolResult response = call("apply_operations", Map.of(
        "operations", List.of(Map.of(
            "tool", "group_channels",
            "args", Map.of("paths", List.of(channel.getCanonicalPath()))))));

    assertErrorCode(response, Result.INVALID_ARGUMENT);
    assertEquals(null, channel.getGroup(), "rejected batch must not mutate the mixer");
  }

  @Test
  void batchRegistryExcludesOnlyDirectGroupingFromTheGroupLifecycle() {
    Map<String, LxTool> batchable = Tools.batchableTools(List.of(
        new GroupChannels(), new UngroupChannel(), new UngroupChannels()));

    assertFalse(batchable.containsKey("group_channels"));
    assertTrue(batchable.containsKey("ungroup_channel"));
    assertTrue(batchable.containsKey("ungroup_channels"));
  }

  private static McpSchema.CallToolResult call(String tool, Map<String, Object> args) {
    return harness.call(tool, args);
  }

  private static Map<String, Object> structured(McpSchema.CallToolResult response) {
    assertEquals(Boolean.FALSE, response.isError());
    return harness.structured(response);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> maps(Object value) {
    return (List<Map<String, Object>>) value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> channelEntryById(Map<String, Object> payload, int id) {
    return ((List<Map<String, Object>>) payload.get("channels")).stream()
        .filter(entry -> ((Number) entry.get("id")).intValue() == id)
        .findFirst()
        .orElseThrow();
  }

  private static void assertOscChanges(Object value) {
    List<Map<String, Object>> changes = maps(value);
    assertFalse(changes.isEmpty());
    assertOscChangeShape(changes);
  }

  private static void assertOscChangeShape(Object value) {
    List<Map<String, Object>> changes = maps(value);
    for (Map<String, Object> change : changes) {
      assertInstanceOf(Number.class, change.get("componentId"));
      assertInstanceOf(String.class, change.get("before"));
      assertInstanceOf(String.class, change.get("after"));
      assertNotEquals(change.get("before"), change.get("after"));
    }
  }

  private static void assertErrorCode(McpSchema.CallToolResult response, String code) {
    assertEquals(Boolean.TRUE, response.isError());
    McpSchema.TextContent text = assertInstanceOf(
        McpSchema.TextContent.class, response.content().get(0));
    assertTrue(text.text().startsWith(code), text.text());
  }
}
