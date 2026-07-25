package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Frames;
import chromatikmcp.render.FrameRaster;

public final class GetFrame implements LxTool {

  private static final int MIN_WIDTH = 64;
  private static final int MAX_WIDTH = 1024;
  private static final int DEFAULT_WIDTH = 256;
  private static final int MIN_GRID = 1;
  private static final int MAX_GRID = 16;
  private static final int DEFAULT_GRID = 3;
  private static final int MIN_LIT_THRESHOLD = 0;
  private static final int MAX_LIT_THRESHOLD = 255;
  private static final int LIT_THRESHOLD_DEFAULT = Frames.LIT_THRESHOLD;

  @Override
  public String name() {
    return "get_frame";
  }

  @Override
  public String description() {
    return "See what the model is rendering by reading the composited output buffer. "
        + "Pass include_image=true to get an actual PNG image of the current frame — use "
        + "this whenever you need to visually inspect the render (e.g. confirming a "
        + "pattern/effect change looks right, debugging the mapping, or answering 'what "
        + "does this look like'). The API always returns a cheap numeric summary "
        + "(non-black fraction, lit fraction, mean brightness, dominant colors, and an "
        + "NxN mean-color grid) — the PNG is additional when requested. nonBlackFraction "
        + "counts any pixel with a nonzero channel, so near-black residuals (e.g. a "
        + "#101010 blur tail) inflate it even though they read as dark. litFraction "
        + "excludes those residuals: it counts only pixels whose max channel exceeds "
        + "litThreshold (default " + LIT_THRESHOLD_DEFAULT + ", ~10% of full scale — a "
        + "documented heuristic, not perceptual luminance; raise it to make litFraction "
        + "stricter) and is the field to use when judging negative space or whether an "
        + "area actually reads as dark. litThreshold=0 makes litFraction equal to "
        + "nonBlackFraction (max > 0 is the nonBlack condition); litThreshold=255 makes "
        + "litFraction always 0.0, since no channel can exceed the maximum. Image content "
        + "is token-expensive, so default to "
        + "the numeric summary and only request the PNG when actually looking at the "
        + "picture matters. Supports orthographic front/top/side views and main/cue/aux "
        + "output buses.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("view", Schemas.enumString(
        "Orthographic view plane (default front: x/y as seen from the front; top: x/z; side: z/y)",
        List.of("front", "top", "side")));
    properties.put("width", Schemas.integer(
        "Image width in pixels (default " + DEFAULT_WIDTH + "); height follows the model's aspect ratio",
        MIN_WIDTH, MAX_WIDTH));
    properties.put("bus", Schemas.enumString(
        "Which composited buffer to read (default main)", List.of("main", "cue", "aux")));
    properties.put("include_image", Schemas.bool(
        "Include the PNG rendering (default false — image content is token-expensive; "
            + "request it explicitly when you need to see the frame)"));
    properties.put("grid", Schemas.integer(
        "Grid resolution N for the NxN mean-color summary matrix (default " + DEFAULT_GRID + ")",
        MIN_GRID, MAX_GRID));
    properties.put("litThreshold", Schemas.integer(
        "Max-channel cutoff (0-255) a pixel must exceed to count toward litFraction "
            + "(default " + LIT_THRESHOLD_DEFAULT + "). Raising it makes litFraction stricter. "
            + "0 makes litFraction equal nonBlackFraction; 255 makes litFraction always 0.0.",
        MIN_LIT_THRESHOLD, MAX_LIT_THRESHOLD));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Frames.View view;
    Frames.Bus bus;
    try {
      // Locale.ROOT: a Turkish default locale turns "side" into "SİDE" and breaks valueOf.
      view = Frames.View.valueOf(stringArg(args, "view", "front").toUpperCase(Locale.ROOT));
      bus = Frames.Bus.valueOf(stringArg(args, "bus", "main").toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return Result.error(Result.INVALID_ARGUMENT, "view must be front|top|side, bus main|cue|aux");
    }
    int width = intArg(args, "width", DEFAULT_WIDTH, MIN_WIDTH, MAX_WIDTH);
    int grid = intArg(args, "grid", DEFAULT_GRID, MIN_GRID, MAX_GRID);
    boolean includeImage = args.get("include_image") instanceof Boolean b && b;

    int litThreshold = intArg(args, "litThreshold", LIT_THRESHOLD_DEFAULT, MIN_LIT_THRESHOLD, MAX_LIT_THRESHOLD);

    Frames.FrameSnapshot snap = Frames.capture(lx, bus);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("bus", snap.bus());
    payload.put("view", view.name().toLowerCase(Locale.ROOT));
    payload.putAll(Frames.summarize(snap, view, grid, litThreshold));

    if (!includeImage) {
      return Result.ok(payload);
    }
    payload.put("imageWidth", width);
    payload.put("imageHeight", FrameRaster.height(snap, view, width));
    // The PNG is encoded by the seam on the HTTP worker thread, after this handler has
    // left the engine thread; the supplier closes only over the detached snapshot.
    return Result.okImage(payload, () -> FrameRaster.png(snap, view, width));
  }

  private static String stringArg(Map<String, Object> args, String key, String fallback) {
    return (args.get(key) instanceof String s && !s.isEmpty()) ? s : fallback;
  }

  private static int intArg(Map<String, Object> args, String key, int fallback, int min, int max) {
    // JSON numerics may arrive as Integer, Long, or Double depending on the client.
    int value = (args.get(key) instanceof Number n) ? n.intValue() : fallback;
    return Math.max(min, Math.min(max, value));
  }
}
