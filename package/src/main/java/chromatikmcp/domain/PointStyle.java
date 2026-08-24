package chromatikmcp.domain;

import java.util.List;

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;

/**
 * The point-rendering parameters of Chromatik's live 3D preview.
 *
 * <p>No state is held here when the binding is absent: the headless {@code FrameRaster}
 * ignores these parameters, while UIPointCloud already persists its own state with the UI
 * project. A shadow copy would therefore claim to control a renderer that never reads it.
 */
public final class PointStyle {

  public record Setting(String name, Parameters.ParameterInfo parameter) {}

  /**
   * Binding to the preview-only GLX state, installed by the UI companion once Chromatik's
   * main preview exists. Both methods run on the LX engine thread while the UI thread draws.
   * Reading and writing LX parameters across those threads is the same traffic LX's own OSC
   * engine generates against UI-visible parameters.
   */
  public interface PreviewPointStyle {
    List<Setting> read();

    Setting set(String name, Object value);
  }

  private volatile PreviewPointStyle preview;

  public void bindPreview(PreviewPointStyle preview) {
    this.preview = preview;
  }

  public void unbindPreview() {
    this.preview = null;
  }

  public List<Setting> get() {
    return requirePreview().read();
  }

  public Setting set(String name, Object value) {
    if (name == null || name.isBlank()) {
      throw Resolve.invalidArgument("Point-style setting name must be a non-blank string");
    }
    return requirePreview().set(name, value);
  }

  private PreviewPointStyle requirePreview() {
    PreviewPointStyle bound = this.preview;
    if (bound == null) {
      throw Resolve.invalidArgument(
          "Preview point style is unavailable headless: it only controls Chromatik's live "
              + "3D preview, and get_frame uses an independent raster that does not reflect "
              + "these settings");
    }
    return bound;
  }

  /** Builds the shared parameter snapshot for one unregistered preview setting. */
  public static Setting describe(String name, LXParameter parameter) {
    return new Setting(name, Parameters.describeUnregistered(parameter));
  }

  /**
   * Applies the same value coercion as {@code set_parameter}, directly rather than through
   * {@code LXCommand}: UIPointCloud is not an LXComponent and has no command-addressable path.
   */
  public static Setting apply(String name, LXParameter parameter, Object value) {
    Parameters.requireWritable(parameter);
    Parameters.Coerced coerced = Parameters.classify(parameter, value);
    switch (coerced.kind()) {
      case STRING -> ((StringParameter) coerced.parameter()).setValue((String) coerced.value());
      case BOOLEAN ->
          ((BooleanParameter) coerced.parameter()).setValue((Boolean) coerced.value());
      case DISCRETE ->
          ((DiscreteParameter) coerced.parameter()).setValue((Integer) coerced.value());
      case NUMERIC -> coerced.parameter().setValue((Double) coerced.value());
    }
    return describe(name, parameter);
  }
}
