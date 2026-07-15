package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.Frames;
import lxmcp.render.FrameRaster;

public final class GetFrame implements LxTool {

  private static final int MIN_WIDTH = 64;
  private static final int MAX_WIDTH = 1024;
  private static final int DEFAULT_WIDTH = 256;
  private static final int MIN_GRID = 1;
  private static final int MAX_GRID = 16;
  private static final int DEFAULT_GRID = 3;

  @Override
  public String name() {
    return "get_frame";
  }

  @Override
  public String description() {
    return "Preview the rendered output: reads back the last completed engine frame and returns "
        + "a PNG rendering of the point cloud plus a compact summary (non-black fraction, mean "
        + "brightness, dominant colors, NxN mean-color grid). For tight iteration loops, keep "
        + "token cost down with include_image=false (summary only), a larger grid for a "
        + "text-mode preview, or a small width.";
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
        "Include the PNG rendering (default true); false returns the summary only"));
    properties.put("grid", Schemas.integer(
        "Grid resolution N for the NxN mean-color summary matrix (default " + DEFAULT_GRID + ")",
        MIN_GRID, MAX_GRID));
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
    boolean includeImage = !(args.get("include_image") instanceof Boolean b) || b;

    Frames.FrameSnapshot snap = Frames.capture(lx, bus);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("bus", snap.bus());
    payload.put("view", view.name().toLowerCase(Locale.ROOT));
    payload.putAll(Frames.summarize(snap, view, grid));

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
