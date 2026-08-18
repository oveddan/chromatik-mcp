package chromatikmcp.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;
import chromatikmcp.effects.ColorizeMultiplyEffect.ColorMode;
import chromatikmcp.effects.ColorizeMultiplyEffect.SourceMode;
import chromatikmcp.effects.ColorizeMultiplyEffect.ThresholdMode;

import heronarts.lx.LX;
import heronarts.lx.ModelBuffer;
import heronarts.lx.color.GradientUtils.BlendMode;
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.color.ColorizeEffect;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;

/**
 * Headless-LX coverage for {@link ColorizeMultiplyEffect}, per the test plan in issue #202.
 *
 * <p><b>Deviation from the issue's literal wording:</b> the issue's test list says a
 * brightness-0 pixel is black "at every depth value." Given the specified formula
 * {@code out = lerp(c, c * lerp, depth)}, that can only hold exactly at {@code depth = 1} —
 * at any {@code depth < 1} the result is a linear blend between the un-multiplied gradient
 * color and black, which is not black unless the blend factor (depth) is 1. This is
 * inherent to the formula, not an implementation bug: {@code depth = 0} is required
 * elsewhere in the issue to reproduce {@code ColorizeEffect} exactly (which does *not*
 * preserve brightness — that's the whole bug this effect exists to fix), so "black at every
 * depth" and "depth = 0 matches ColorizeEffect" cannot both hold. This suite pins the
 * achievable version of the claim: exact black at {@code depth = 1}, and monotonic
 * darkening as depth increases from 0.
 */
class ColorizeMultiplyEffectTest extends HeadlessLxTest {

  @Override
  protected LXModel newModel() {
    // reindexPoints: LXPoint indices come from a JVM-global counter, and the
    // immutable-model LX constructor does not reindex — so a second LX in the same JVM
    // gets points indexed starting past 0, breaking `colors[0]`-style direct pixel writes.
    return new GridModel(3, 1).reindexPoints();
  }

  private static ColorizeMultiplyEffect newEffect(LX lx) {
    ColorizeMultiplyEffect effect = new ColorizeMultiplyEffect(lx);
    effect.setBuffer(new ModelBuffer(lx));
    return effect;
  }

  private static ColorizeEffect newColorizeEffect(LX lx) {
    ColorizeEffect effect = new ColorizeEffect(lx);
    effect.setBuffer(new ModelBuffer(lx));
    return effect;
  }

  private static void setFixedGradient(ColorizeMultiplyEffect effect, int color1, int color2) {
    effect.colorMode.setValue(ColorMode.FIXED);
    effect.color1.setColor(color1);
    effect.color2.setColor(color2);
  }

  private static void setFixedGradient(ColorizeEffect effect, int color1, int color2) {
    effect.colorMode.setValue(ColorizeEffect.ColorMode.FIXED);
    effect.color1.setColor(color1);
    effect.color2.setColor(color2);
  }

  // 1. Brightness-0 pixel -> black, guaranteed at depth = 1, using a gradient whose stop 0
  //    is a saturated (non-black) color -- the core regression this effect exists to fix.
  @Test
  void brightnessZeroIsBlackAtFullDepth() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = newEffect(lx);
    int saturatedRed = LXColor.hsb(0, 100, 100);
    setFixedGradient(effect, saturatedRed, LXColor.hsb(240, 100, 100));
    effect.depth.setValue(1.0);

    int[] colors = effect.getColors();
    colors[0] = 0xff000000; // opaque black source pixel: brightness 0
    effect.loop(0);

    assertEquals(0xff000000, colors[0], "brightness-0 pixel must render black at depth=1");
  }

  // Weaker, achievable version of "at every depth": darkening increases monotonically with
  // depth and is not exact black below depth=1 (see class-level deviation note).
  @Test
  void brightnessZeroDarkensMonotonicallyWithDepth() {
    LX lx = newHeadlessLx();
    int saturatedRed = LXColor.hsb(0, 100, 100);

    int previousBrightness = 256; // above any possible channel value
    for (float depth : new float[] {0f, 0.25f, 0.5f, 0.75f, 1f}) {
      ColorizeMultiplyEffect effect = newEffect(lx);
      setFixedGradient(effect, saturatedRed, LXColor.hsb(240, 100, 100));
      effect.depth.setValue(depth);

      int[] colors = effect.getColors();
      colors[0] = 0xff000000;
      effect.loop(0);

      int red = (colors[0] & LXColor.R_MASK) >>> LXColor.R_SHIFT;
      assertTrue(red <= previousBrightness, "red channel must not increase as depth grows");
      previousBrightness = red;
    }
    assertEquals(0, previousBrightness, "depth=1 must fully darken to black");
  }

  // 2. depth = 0 must match ColorizeEffect configured equivalently (superset property).
  @Test
  void depthZeroMatchesColorizeEffect() {
    LX lx = newHeadlessLx();

    ColorizeMultiplyEffect multiply = newEffect(lx);
    setFixedGradient(multiply, LXColor.hsb(0, 100, 100), LXColor.hsb(200, 80, 90));
    multiply.depth.setValue(0.0);

    ColorizeEffect colorize = newColorizeEffect(lx);
    setFixedGradient(colorize, LXColor.hsb(0, 100, 100), LXColor.hsb(200, 80, 90));

    // Several opaque source pixels spanning the brightness range.
    int[] sourcePixels = {
      0xff000000, // black
      LXColor.hsb(90, 60, 25),
      LXColor.hsb(300, 40, 60),
      // last point overwritten per-iteration below
    };

    int[] multiplyColors = multiply.getColors();
    int[] colorizeColors = colorize.getColors();
    for (int i = 0; i < sourcePixels.length; i++) {
      multiplyColors[i] = sourcePixels[i];
      colorizeColors[i] = sourcePixels[i];
    }

    multiply.loop(0);
    colorize.loop(0);

    assertArrayEquals(colorizeColors, multiplyColors,
        "depth=0 output must match ColorizeEffect exactly");
  }

  // 3. depth = 1 -> output equals gradientColor(b) * b for known brightness values.
  @Test
  void depthOneEqualsGradientColorTimesBrightness() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = newEffect(lx);
    // Pure red -> pure red gradient so the multiplied result is trivially computable:
    // channel = round(255 * brightness).
    setFixedGradient(effect, LXColor.hsb(0, 100, 100), LXColor.hsb(0, 100, 100));
    effect.depth.setValue(1.0);
    effect.source.setValue(SourceMode.BRIGHTNESS);

    // Direct integer gray levels (not derived through LXColor.grayn's truncating scale) so
    // the expected value is exact: lerp = gray/255, and channel = round(255 * lerp) = gray.
    int[] grayLevels = {64, 128, 192, 255};
    int[] colors = effect.getColors();
    for (int i = 0; i < grayLevels.length && i < colors.length; i++) {
      int gray = grayLevels[i];
      colors[i] = 0xff000000 | (gray << 16) | (gray << 8) | gray;
    }
    effect.loop(0);

    for (int i = 0; i < grayLevels.length && i < colors.length; i++) {
      int expectedRed = grayLevels[i];
      int actualRed = (colors[i] & LXColor.R_MASK) >>> LXColor.R_SHIFT;
      assertEquals(expectedRed, actualRed, 1, "channel at gray level " + grayLevels[i]);
    }
  }

  // 4. Threshold gates the low end but never rescales/shifts hues above it.
  @Test
  void thresholdNeverRescalesAboveCutoff() {
    LX lx = newHeadlessLx();

    int color1 = LXColor.hsb(0, 100, 100);
    int color2 = LXColor.hsb(200, 80, 90);
    int brightGray = Math.round(0.6f * 255.9f);
    int sourcePixel = 0xff000000 | (brightGray << 16) | (brightGray << 8) | brightGray; // brightness 0.6

    ColorizeMultiplyEffect lowThreshold = newEffect(lx);
    setFixedGradient(lowThreshold, color1, color2);
    lowThreshold.threshold.setValue(0.2);
    lowThreshold.thresholdMode.setValue(ThresholdMode.LEAVE);
    lowThreshold.getColors()[0] = sourcePixel;
    lowThreshold.loop(0);

    ColorizeMultiplyEffect highThreshold = newEffect(lx);
    setFixedGradient(highThreshold, color1, color2);
    highThreshold.threshold.setValue(0.5);
    highThreshold.thresholdMode.setValue(ThresholdMode.LEAVE);
    highThreshold.getColors()[0] = sourcePixel;
    highThreshold.loop(0);

    assertEquals(lowThreshold.getColors()[0], highThreshold.getColors()[0],
        "a pixel above both thresholds must get the same color regardless of where the threshold sits");
  }

  // 5. Source alpha is preserved on the non-CLEAR path.
  @Test
  void alphaIsPreserved() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = newEffect(lx);
    setFixedGradient(effect, LXColor.hsb(0, 100, 100), LXColor.hsb(200, 80, 90));
    effect.depth.setValue(0.5);

    int semiTransparentGray = (128 << 24) | 0x606060;
    effect.getColors()[0] = semiTransparentGray;
    effect.loop(0);

    int alpha = (effect.getColors()[0] & LXColor.ALPHA_MASK) >>> LXColor.ALPHA_SHIFT;
    assertEquals(128, alpha, "source alpha must be preserved");
  }

  // 6. CLEAR mode yields transparency, not black.
  @Test
  void clearModeYieldsTransparentNotBlack() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = newEffect(lx);
    setFixedGradient(effect, LXColor.hsb(0, 100, 100), LXColor.hsb(200, 80, 90));
    effect.threshold.setValue(0.5);
    effect.thresholdMode.setValue(ThresholdMode.CLEAR);

    int dimGray = Math.round(0.1f * 255.9f);
    int sourcePixel = 0xff000000 | (dimGray << 16) | (dimGray << 8) | dimGray;
    effect.getColors()[0] = sourcePixel;
    effect.loop(0);

    int result = effect.getColors()[0];
    int alpha = (result & LXColor.ALPHA_MASK) >>> LXColor.ALPHA_SHIFT;
    assertEquals(0, alpha, "gated pixel under CLEAR must become transparent");
    assertEquals(sourcePixel & LXColor.RGB_MASK, result & LXColor.RGB_MASK,
        "CLEAR must not blacken RGB, only zero alpha");
  }

  // Registration sanity: the class is a well-formed, registrable LXEffect (list_available_effects
  // relies on LX's jar auto-registration of public non-abstract LXEffect subclasses at runtime;
  // this pins that the class itself satisfies that contract in a headless registry).
  @Test
  void isRegistrableAsAnEffect() {
    LX lx = newHeadlessLx();
    lx.registry.addEffect(ColorizeMultiplyEffect.class);
    assertTrue(lx.registry.effects.contains(ColorizeMultiplyEffect.class));
  }

  @Test
  void blendModeDefaultsToRgb() {
    LX lx = newHeadlessLx();
    ColorizeMultiplyEffect effect = newEffect(lx);
    assertEquals(BlendMode.RGB, effect.blendMode.getEnum());
  }
}
