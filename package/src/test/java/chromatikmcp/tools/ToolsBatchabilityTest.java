package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import heronarts.lx.LX;

class ToolsBatchabilityTest {

  private record StubTool(String name, boolean readOnly, boolean batchable) implements LxTool {
    @Override
    public String description() {
      return name;
    }

    @Override
    public Map<String, Object> inputSchema() {
      return Schemas.noArgs();
    }

    @Override
    public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
      return Result.ok(Map.of());
    }
  }

  @Test
  void batchRegistryRequiresMutationAndExplicitBatchability() {
    LxTool mutation = new StubTool("mutation", false, true);
    LxTool readOnlyClaimingBatchability = new StubTool("read", true, true);
    LxTool nonBatchableMutation = new StubTool("history", false, false);

    Map<String, LxTool> batchable = Tools.batchableTools(
        List.of(mutation, readOnlyClaimingBatchability, nonBatchableMutation));

    assertEquals(Map.of("mutation", mutation), batchable);
  }
}
