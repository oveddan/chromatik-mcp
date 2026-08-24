package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;

import chromatikmcp.HeadlessLxTest;

class PointStyleTest extends HeadlessLxTest {

  public enum LedStyle { LENS1, CIRCLE, SQUARE }

  @Test
  void headlessHasNoInventedPointStyleState() {
    LX lx = newHeadlessLx();
    PointStyle pointStyle = new PointStyle();

    Resolve.ResolveException getFailure = assertThrows(
        Resolve.ResolveException.class, pointStyle::get);
    assertEquals(Resolve.Failure.TYPE_MISMATCH, getFailure.failure);
    Resolve.ResolveException setFailure = assertThrows(
        Resolve.ResolveException.class, () -> pointStyle.set("pointSize", 8));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, setFailure.failure);
    assertEquals(64, lx.getModel().size, "the domain primitive works against headless LX");
  }

  @Test
  void boundPreviewRoundTripsAndEnumAcceptsOptionName() {
    newHeadlessLx();
    PointStyle pointStyle = new PointStyle();
    FakePreview preview = new FakePreview();
    pointStyle.bindPreview(preview);

    PointStyle.Setting size = pointStyle.set("pointSize", 7.5);
    assertEquals(7.5, ((Number) size.parameter().value()).doubleValue(), 1e-9);
    assertEquals(7.5, ((Number) setting(pointStyle.get(), "pointSize")
        .parameter().value()).doubleValue(), 1e-9);

    PointStyle.Setting led = pointStyle.set("ledStyle", "CIRCLE");
    assertEquals(1, led.parameter().value());
    assertEquals("CIRCLE", led.parameter().formatted());
  }

  @Test
  void unknownSettingIsTypedInvalidArgument() {
    newHeadlessLx();
    PointStyle pointStyle = new PointStyle();
    pointStyle.bindPreview(new FakePreview());

    Resolve.ResolveException failure = assertThrows(
        Resolve.ResolveException.class, () -> pointStyle.set("starburst", 0));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, failure.failure);
  }

  private static PointStyle.Setting setting(List<PointStyle.Setting> settings, String name) {
    return settings.stream().filter(setting -> name.equals(setting.name())).findFirst().orElseThrow();
  }

  private static final class FakePreview implements PointStyle.PreviewPointStyle {
    private final Map<String, LXParameter> settings = new LinkedHashMap<>();

    private FakePreview() {
      this.settings.put("pointSize", new BoundedParameter("Point Size", 3, .1, 100000));
      this.settings.put("ledStyle", new EnumParameter<>("LED Style", LedStyle.LENS1));
    }

    @Override
    public List<PointStyle.Setting> read() {
      return this.settings.entrySet().stream()
          .map(entry -> PointStyle.describe(entry.getKey(), entry.getValue()))
          .toList();
    }

    @Override
    public PointStyle.Setting set(String name, Object value) {
      LXParameter parameter = this.settings.get(name);
      if (parameter == null) {
        throw Resolve.invalidArgument("Unknown point-style setting '" + name + "'");
      }
      return PointStyle.apply(name, parameter, value);
    }
  }
}
