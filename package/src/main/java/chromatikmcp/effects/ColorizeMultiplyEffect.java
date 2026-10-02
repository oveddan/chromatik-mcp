/**
 * Self-contained by design: this class's permanent home is the Apotheneum repo
 * (apotheneum.doved.effects). It lives here only because chromatik-mcp has a headless-LX
 * test harness and a build gate that Apotheneum currently lacks — porting it should be a
 * file move, not a rewrite. Keep it free of chromatik-mcp-specific dependencies (tools/,
 * domain/, MCP plumbing). See GitHub issue #202.
 */
package chromatikmcp.effects;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.GradientUtils.BlendFunction;
import heronarts.lx.color.GradientUtils.BlendMode;
import heronarts.lx.color.GradientUtils.ColorStops;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

/**
 * Colorizes a pixel by a gradient indexed on its own brightness, then scales the looked-up
 * color back down by that same brightness — so a pixel at brightness 0 renders black
 * regardless of what color sits at gradient position 0. {@code heronarts.lx.effect.color
 * .ColorizeEffect} performs only the lookup and discards the source brightness, which is
 * what makes "off" pixels in a monochrome source light up under a full-color gradient. This
 * is a fresh {@link LXEffect} built on {@code GradientUtils} rather than a subclass of
 * {@code ColorizeEffect}, whose {@code run()} is not shaped for overriding the one line that
 * differs.
 */
@LXCategory(LXCategory.COLOR)
@LXComponent.Description("Colorizes by brightness-indexed gradient lookup, then re-scales by that same brightness so darks stay dark")
public class ColorizeMultiplyEffect extends LXEffect {

  public interface SourceFunction {
    public float getLerpFactor(int argb);
  }

  public enum SourceMode {
    BRIGHTNESS("Brightness", (argb) -> LXColor.b(argb) * .01f),
    LUMINOSITY("Luminosity", (argb) -> LXColor.luminosity(argb) * .01f);

    private final String name;
    public final SourceFunction lerp;

    private SourceMode(String name, SourceFunction function) {
      this.name = name;
      this.lerp = function;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  public enum ColorMode {
    FIXED("Fixed"),
    RELATIVE("Relative"),
    LINKED("Linked"),
    PALETTE("Palette");

    public final String label;

    private ColorMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  /**
   * Unlike {@code ColorizeEffect.FilterMode}, there is no rescale here — LEAVE/BLACK/CLEAR
   * only decide what happens to a gated pixel. See the no-rescale note on {@link #threshold}.
   */
  public enum ThresholdMode {
    LEAVE("Leave"),
    BLACK("Black"),
    CLEAR("Clear");

    public final String label;

    private ThresholdMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<SourceMode> source =
    new EnumParameter<SourceMode>("Source", SourceMode.BRIGHTNESS)
    .setDescription("Determines the source of the color mapping");

  public final EnumParameter<BlendMode> blendMode =
    new EnumParameter<BlendMode>("Blend Mode", BlendMode.RGB)
    .setDescription("Determines the mode of gradient interpolation between color stops");

  public final EnumParameter<ColorMode> colorMode =
    new EnumParameter<ColorMode>("Color Mode", ColorMode.FIXED)
    .setDescription("Which source the colors come from");

  public final ColorParameter color1 =
    new ColorParameter("Color 1", 0xff000000)
    .setDescription("The first color that is mapped from");

  public final ColorParameter color2 =
    new ColorParameter("Color 2", 0xffffffff)
    .setDescription("The second color that is mapped to");

  public final CompoundParameter gradientHue =
    new CompoundParameter("H-Offset", 0, -360, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Amount of hue gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter gradientSaturation =
    new CompoundParameter("S-Offset", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Amount of saturation gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter gradientBrightness =
    new CompoundParameter("B-Offset", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Amount of brightness gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter linkedHue =
    new CompoundParameter("H-Linked", 0, -360, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Amount of hue gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter linkedSaturation =
    new CompoundParameter("S-Linked", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Amount of saturation gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter linkedBrightness =
    new CompoundParameter("B-Linked", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Amount of brightness gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final DiscreteParameter paletteIndex =
    new LXPalette.IndexSelector("Index")
    .setDescription("Which index at the palette to start from");

  public final DiscreteParameter paletteStops =
    new DiscreteParameter("Stops", LXSwatch.MAX_COLORS, 2, LXSwatch.MAX_COLORS + 1)
    .setDescription("How many color stops to use in the palette");

  public final BooleanParameter paletteInvert =
    new BooleanParameter("Invert", false)
    .setDescription("Invert the direction of the palette gradient");

  public final CompoundParameter paletteDepth =
    new CompoundParameter("Depth", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Depth of palette generation");

  /**
   * Superset knob: {@code out = lerp(c, c * lerp, depth)}. depth=0 is byte-for-byte
   * ColorizeEffect's lookup-only behavior (the known bug this effect exists to fix); depth=1
   * is the full brightness-preserving multiply. Defaulted to 1 — the multiply behavior is
   * the reason this class exists, so an agent adding it should get that by default and dial
   * back toward classic colorize only if a project specifically wants the flatter look.
   */
  public final CompoundParameter depth =
    new CompoundParameter("Depth", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Blends between plain colorize (0) and full brightness-preserving multiply (1)");

  public final CompoundParameter amount =
    new CompoundParameter("Amount", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Crossfade of colorization against the original source");

  public final CompoundParameter threshold =
    new CompoundParameter("Threshold", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Gates the low end of the source brightness; never rescales values above it");

  public final EnumParameter<ThresholdMode> thresholdMode =
    new EnumParameter<ThresholdMode>("Threshold Mode", ThresholdMode.LEAVE)
    .setDescription("How to treat pixels beneath the threshold");

  private final ColorStops colorStops = new ColorStops();

  public ColorizeMultiplyEffect(LX lx) {
    super(lx);
    addParameter("source", this.source);
    addParameter("gradientHue", this.gradientHue);
    addParameter("gradientSaturation", this.gradientSaturation);
    addParameter("gradientBrightness", this.gradientBrightness);
    addParameter("colorMode", this.colorMode);
    addParameter("blendMode", this.blendMode);
    addParameter("color1", this.color1);
    addParameter("color2", this.color2);
    addParameter("paletteIndex", this.paletteIndex);
    addParameter("paletteStops", this.paletteStops);
    addParameter("paletteInvert", this.paletteInvert);
    addParameter("paletteDepth", this.paletteDepth);
    addParameter("primaryHue", this.linkedHue);
    addParameter("primarySaturation", this.linkedSaturation);
    addParameter("primaryBrightness", this.linkedBrightness);
    addParameter("depth", this.depth);
    addParameter("amount", this.amount);
    addParameter("threshold", this.threshold);
    addParameter("thresholdMode", this.thresholdMode);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.colorMode ||
        p == this.color1 ||
        p == this.gradientHue ||
        p == this.gradientSaturation ||
        p == this.gradientBrightness ||
        p == this.linkedHue ||
        p == this.linkedSaturation ||
        p == this.linkedBrightness) {
      // Done onParameterChanged (not just in run()) so the UI swatch indicator updates
      // whether or not this effect is currently active, matching ColorizeEffect.
      setGradientColor();
    }
  }

  private void setGradientColor() {
    if (this.colorMode.getEnum() == ColorMode.RELATIVE) {
      this.color2.brightness.setValue(this.color1.brightness.getValue() + this.gradientBrightness.getValue());
      this.color2.saturation.setValue(this.color1.saturation.getValue() + this.gradientSaturation.getValue());
      this.color2.hue.setValue((360 + this.color1.hue.getValue() + this.gradientHue.getValue()) % 360);
    } else if (this.colorMode.getEnum() == ColorMode.LINKED) {
      LXDynamicColor swatchColor = getSwatchColor();
      this.color1.brightness.setValue(swatchColor.getBrightness() + this.linkedBrightness.getValue());
      this.color1.saturation.setValue(swatchColor.getSaturation() + this.linkedSaturation.getValue());
      this.color1.hue.setValue((360 + swatchColor.getHue() + this.linkedHue.getValue()) % 360);

      this.color2.brightness.setValue(swatchColor.getBrightness() + this.gradientBrightness.getValue());
      this.color2.saturation.setValue(swatchColor.getSaturation() + this.gradientSaturation.getValue());
      this.color2.hue.setValue((360 + swatchColor.getHue() + this.gradientHue.getValue()) % 360);
    }
  }

  public LXDynamicColor getSwatchColor() {
    return this.lx.engine.palette.getSwatchColor(this.paletteIndex.getValuei() - 1);
  }

  private void setColorStops() {
    switch (this.colorMode.getEnum()) {
    default:
    case FIXED:
      this.colorStops.stops[0].set(this.color1);
      this.colorStops.stops[1].set(this.color2);
      this.colorStops.setNumStops(2);
      break;

    case LINKED:
      LXDynamicColor swatchColor = getSwatchColor();
      this.colorStops.stops[0].set(swatchColor,
        this.linkedHue.getValuef(),
        this.linkedSaturation.getValuef(),
        this.linkedBrightness.getValuef()
      );
      this.colorStops.stops[1].set(swatchColor,
        this.gradientHue.getValuef(),
        this.gradientSaturation.getValuef(),
        this.gradientBrightness.getValuef()
      );
      this.colorStops.setNumStops(2);
      break;

    case RELATIVE:
      this.colorStops.stops[0].set(this.color1);
      this.colorStops.stops[1].set(this.color1,
        this.gradientHue.getValuef(),
        this.gradientSaturation.getValuef(),
        this.gradientBrightness.getValuef()
      );
      this.colorStops.setNumStops(2);
      break;

    case PALETTE:
      this.colorStops.setPaletteGradient(this.lx.engine.palette, this.paletteIndex.getValuei() - 1, this.paletteStops.getValuei());
      break;
    }
  }

  /** Scales the RGB channels of {@code rgb} by {@code amount}, ignoring/discarding alpha bits. */
  private static int scaleRgb(int rgb, float amount) {
    int r = LXUtils.constrain(Math.round(((rgb & LXColor.R_MASK) >>> LXColor.R_SHIFT) * amount), 0, 255);
    int g = LXUtils.constrain(Math.round(((rgb & LXColor.G_MASK) >>> LXColor.G_SHIFT) * amount), 0, 255);
    int b = LXUtils.constrain(Math.round((rgb & LXColor.B_MASK) * amount), 0, 255);
    return (r << LXColor.R_SHIFT) | (g << LXColor.G_SHIFT) | b;
  }

  /** Per-channel lerp between two RGB-only (alpha-ignored) packed values. */
  private static int lerpRgb(int rgb1, int rgb2, float lerp) {
    int r = LXUtils.lerpi((rgb1 & LXColor.R_MASK) >>> LXColor.R_SHIFT, (rgb2 & LXColor.R_MASK) >>> LXColor.R_SHIFT, lerp);
    int g = LXUtils.lerpi((rgb1 & LXColor.G_MASK) >>> LXColor.G_SHIFT, (rgb2 & LXColor.G_MASK) >>> LXColor.G_SHIFT, lerp);
    int b = LXUtils.lerpi(rgb1 & LXColor.B_MASK, rgb2 & LXColor.B_MASK, lerp);
    return (r << LXColor.R_SHIFT) | (g << LXColor.G_SHIFT) | b;
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    setGradientColor();
    setColorStops();

    enabledAmount *= this.amount.getValue();
    if (enabledAmount == 0) {
      return;
    }

    final SourceFunction sourceFunction = this.source.getEnum().lerp;
    final BlendFunction blendFunction = this.blendMode.getEnum().function;
    final float thresholdValue = this.threshold.getValuef();
    final ThresholdMode thresholdMode = this.thresholdMode.getEnum();
    final float depthAmount = this.depth.getValuef();
    final float enabledAmountf = (float) enabledAmount;

    for (LXPoint p : model.points) {
      int i = p.index;
      int argb = colors[i];
      int sourceRgb = argb & LXColor.RGB_MASK;
      int alpha = argb & LXColor.ALPHA_MASK;

      float lerp = sourceFunction.getLerpFactor(argb);

      int targetRgb;
      int targetAlpha = alpha;

      if (lerp < thresholdValue) {
        switch (thresholdMode) {
          case LEAVE -> {
            continue;
          }
          case BLACK -> targetRgb = 0;
          case CLEAR -> {
            // Fade to transparent rather than stamping black, so lower layers can show
            // through; RGB is left as-is since alpha 0 means it won't be seen anyway.
            targetRgb = sourceRgb;
            targetAlpha = 0;
          }
          default -> targetRgb = sourceRgb;
        }
      } else {
        // Deliberate divergence from ColorizeEffect: no LXUtils.ilerpf rescale of `lerp`
        // against the threshold here. Rescaling would remap every hue above the threshold
        // whenever the threshold moves, defeating the point of a predictable gate — this
        // effect gates the low end only and leaves the gradient index untouched above it.
        int c = this.colorStops.getColor(lerp, blendFunction);
        int multiplied = scaleRgb(c, lerp);
        targetRgb = lerpRgb(c, multiplied, depthAmount);
      }

      int finalRgb = (enabledAmount >= 1) ? targetRgb : lerpRgb(sourceRgb, targetRgb, enabledAmountf);
      int finalAlphaByte = (enabledAmount >= 1)
        ? (targetAlpha >>> LXColor.ALPHA_SHIFT)
        : Math.round(LXUtils.lerpf(alpha >>> LXColor.ALPHA_SHIFT, targetAlpha >>> LXColor.ALPHA_SHIFT, enabledAmountf));

      colors[i] = (finalAlphaByte << LXColor.ALPHA_SHIFT) | (finalRgb & LXColor.RGB_MASK);
    }
  }
}
