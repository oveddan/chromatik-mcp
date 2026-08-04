package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import heronarts.lx.LX;
import heronarts.lx.structure.GridFixture;
import heronarts.lx.structure.LXFixture;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.HeadlessLxTest;
import chromatikmcp.domain.Fixtures;
import chromatikmcp.domain.Resolve;
import chromatikmcp.engine.EngineExecutor;

/** Tool-seam coverage that requires a dynamic fixture structure. */
@Timeout(60)
class MoveFixtureTest extends HeadlessLxTest {

  private LX lx;
  private final AtomicBoolean draining = new AtomicBoolean(true);
  private Thread drainer;

  @BeforeEach
  void setUp() {
    this.lx = track(new LX());
    Fixtures.addFixtureByClass(this.lx, GridFixture.class, null, "First", null);
    Fixtures.addFixtureByClass(this.lx, GridFixture.class, null, "Second", null);
    this.drainer = new Thread(() -> {
      while (this.draining.get()) {
        this.lx.engine.run();
        try {
          Thread.sleep(2);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }, "move-fixture-test-engine-drainer");
    this.drainer.start();
  }

  @AfterEach
  void stopDrainer() throws InterruptedException {
    this.draining.set(false);
    if (this.drainer != null) {
      this.drainer.join(2_000);
    }
  }

  @Test
  void outOfRangeIndexIsInvalidArgumentAndDoesNotMutate() {
    LXFixture first = this.lx.structure.fixtures.get(0);
    LXFixture second = this.lx.structure.fixtures.get(1);
    McpServerFeatures.SyncToolSpecification spec = Tools.specification(
        new MoveFixture(), this.lx, new EngineExecutor(this.lx));

    McpSchema.CallToolResult result = spec.callHandler().apply(null,
        new McpSchema.CallToolRequest("move_fixture", Map.of(
            "path", Resolve.canonicalPath(first),
            "index", this.lx.structure.fixtures.size())));

    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text =
        assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
    assertSame(first, this.lx.structure.fixtures.get(0));
    assertSame(second, this.lx.structure.fixtures.get(1));
  }
}
