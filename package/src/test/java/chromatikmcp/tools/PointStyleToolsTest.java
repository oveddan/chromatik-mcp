package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import heronarts.lx.LX;

import chromatikmcp.HeadlessLxTest;
import chromatikmcp.domain.PointStyle;

class PointStyleToolsTest extends HeadlessLxTest {

  @Test
  void bothToolsReturnUnavailableResultHeadless() {
    LX lx = newHeadlessLx();
    PointStyle pointStyle = new PointStyle();

    Result.Error<Map<String, Object>> get = assertInstanceOf(Result.Error.class,
        Tools.invoke(new GetPointStyle(pointStyle), lx, Map.of()));
    assertEquals(Result.INVALID_ARGUMENT, get.code());
    assertTrue(get.message().contains("unavailable headless"));

    Result.Error<Map<String, Object>> set = assertInstanceOf(Result.Error.class,
        Tools.invoke(new SetPointStyle(pointStyle), lx,
            Map.of("setting", "sparkleAmount", "value", 0)));
    assertEquals(Result.INVALID_ARGUMENT, set.code());
    assertTrue(set.message().contains("unavailable headless"));
  }
}
