package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXLoopTask;
import heronarts.lx.LXSerializable;
import heronarts.lx.model.LXModel;

/**
 * The 3D viewpoint the model is looked at from: one live orbit camera plus a set of named
 * angles that persist with the project.
 *
 * <p>The angle model is LX's own, not an invention here — {@code UI3dContext.Camera} is an
 * orbit rig, so the eye sits on a sphere of {@code radius} around {@code target} at azimuth
 * {@code theta} and elevation {@code phi} (both degrees), with a fixed {@code +Y} up vector.
 * Matching LX exactly is the point: the same angle drives Chromatik's on-screen preview and
 * the offline {@code get_frame} raster, so a human and an agent discussing one render are
 * looking at the same thing. The eye formula below is LX's:
 *
 * <pre>
 *   eye = target + (r·cosφ·sinθ, r·sinφ, −r·cosφ·cosθ)
 * </pre>
 *
 * <p>Bounds are LX's too ({@link #PHI_MIN}/{@link #PHI_MAX} etc.) and are applied here as
 * well as by the parameters, so a headless runtime with no preview attached clamps
 * identically to one with a preview. {@code phi} stops at ±89° because LX's look-at
 * degenerates when the eye is directly over the target and the up vector goes parallel to
 * the view direction.
 *
 * <p>This is a stateful domain object, not a static utility: it owns the named-angle map
 * and is registered with {@link LX#registerExternal} under {@link #EXTERNAL_KEY} so the
 * angles ride along in the project file. Every method is synchronized — project save/load
 * can run off the engine thread that tool handlers use.
 */
public final class Cameras implements LXSerializable {

  /** Project-file key under {@code externals} for the named-angle store. */
  public static final String EXTERNAL_KEY = "chromatikMcpCameras";

  /**
   * Reserved name meaning "the viewpoint right now" wherever a saved name is accepted
   * ({@code get_frame}'s {@code camera}). Rejected as a name to save under, so the word
   * never means two things at one call site.
   */
  public static final String CURRENT = "current";

  public static final double PHI_MIN = -89;
  public static final double PHI_MAX = 89;
  public static final double RADIUS_MIN = 0;
  public static final double RADIUS_MAX = 1e8;
  public static final double FOV_MIN = 15;
  public static final double FOV_MAX = 150;
  public static final double TARGET_ABS_MAX = 1e8;

  /** LX's own {@code UI3dContext} defaults, used when there is no model to frame. */
  private static final double DEFAULT_RADIUS = 120;
  private static final double DEFAULT_FOV = 60;

  public enum Projection {
    PERSPECTIVE,
    ORTHOGRAPHIC;

    public String wire() {
      return name().toLowerCase(Locale.ROOT);
    }

    /** @throws Resolve.ResolveException TYPE_MISMATCH on anything else */
    public static Projection parse(String wire) {
      for (Projection projection : values()) {
        if (projection.wire().equals(wire)) {
          return projection;
        }
      }
      throw Resolve.invalidArgument("projection must be perspective|orthographic: " + wire);
    }
  }

  public record Vec3(double x, double y, double z) {}

  /**
   * A viewpoint in LX's orbit convention. {@code theta}/{@code phi}/{@code fovDegrees} are
   * degrees; {@code fovDegrees} is the vertical field of view (LX's {@code perspective}
   * parameter) and is carried but unused in {@link Projection#ORTHOGRAPHIC}, where
   * {@code radius} alone sets the framing.
   */
  public record CameraAngle(double theta, double phi, double radius, Vec3 target,
      Projection projection, double fovDegrees) {}

  /**
   * A camera as reported back to a caller: the angle plus its derived eye position, and
   * whether it is the one a person is actually looking at. {@code livePreview} is false in
   * a headless runtime (or before Chromatik's UI has come up), where the angle is held
   * here and only {@code get_frame} observes it.
   */
  public record CameraView(CameraAngle angle, Vec3 eye, boolean livePreview) {}

  public record SavedCamera(String name, CameraAngle angle) {}

  /** {@code replaced} distinguishes overwriting a name from adding one. */
  public record SaveResult(SavedCamera camera, boolean replaced) {}

  /** LX's three camera-animation curves; the live UI defaults to sinusoidal. */
  public enum AnimationEase {
    SINUSOIDAL,
    QUADRATIC,
    CUBIC;

    public String wire() {
      return name().toLowerCase(Locale.ROOT);
    }

    /** @throws Resolve.ResolveException TYPE_MISMATCH on anything else */
    public static AnimationEase parse(String wire) {
      for (AnimationEase ease : values()) {
        if (ease.wire().equals(wire)) {
          return ease;
        }
      }
      throw Resolve.invalidArgument(
          "ease must be sinusoidal|quadratic|cubic: " + wire);
    }

    private double shape(double basis) {
      return switch (this) {
        case SINUSOIDAL -> .5 - .5 * Math.cos(basis * Math.PI);
        case QUADRATIC -> (basis < .5)
            ? 2 * basis * basis
            : 1 - 2 * (1 - basis) * (1 - basis);
        case CUBIC -> (basis < .5)
            ? 4 * basis * basis * basis
            : 1 - 4 * (1 - basis) * (1 - basis) * (1 - basis);
      };
    }
  }

  /**
   * Immutable description of one move plus its thread-safe completion signal. Awaiting is
   * for an HTTP worker thread, never the engine thread that advances the move.
   */
  public static final class CameraAnimation {

    private final CameraAngle from;
    private final CameraAngle to;
    private final int durationMs;
    private final AnimationEase ease;
    private final CompletableFuture<CameraView> completion = new CompletableFuture<>();

    private CameraAnimation(
        CameraAngle from, CameraAngle to, int durationMs, AnimationEase ease) {
      this.from = from;
      this.to = to;
      this.durationMs = durationMs;
      this.ease = ease;
    }

    public CameraAngle from() {
      return this.from;
    }

    public CameraAngle to() {
      return this.to;
    }

    public int durationMs() {
      return this.durationMs;
    }

    public AnimationEase ease() {
      return this.ease;
    }

    public CameraView await() {
      try {
        return this.completion.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while awaiting camera animation", e);
      } catch (ExecutionException e) {
        throw new IllegalStateException("Camera animation failed", e.getCause());
      }
    }
  }

  /**
   * Binding to Chromatik's running 3D preview, implemented in {@code chromatikmcp.ui}
   * against {@code UI3dContext} and installed once the UI is up. Absent headless — which
   * is the whole reason this is an interface rather than a direct reference: nothing in
   * {@code domain} may load a {@code heronarts.glx.*} class.
   *
   * <p>Both methods are called on the LX engine thread while the UI thread draws; they
   * read and write LX parameters, which is the same cross-thread traffic LX's own OSC
   * engine generates against UI-visible parameters.
   */
  public interface PreviewCamera {
    CameraAngle read();

    void apply(CameraAngle angle);
  }

  private final Map<String, CameraAngle> saved = new LinkedHashMap<>();

  /**
   * The viewpoint when no preview is bound. Null until first read, then lazily framed on
   * the model, and reset to null on project load so the next read reframes on the new one.
   */
  private CameraAngle detached;

  private volatile PreviewCamera preview;

  /** Non-null only while its loop task is installed on the engine. */
  private AnimationLoop animation;

  public void bindPreview(PreviewCamera preview) {
    this.preview = preview;
  }

  public void unbindPreview() {
    this.preview = null;
  }

  /** The current viewpoint: the live preview's if one is bound, else the detached one. */
  public synchronized CameraView current(LX lx) {
    PreviewCamera bound = this.preview;
    if (bound != null) {
      return view(normalize(bound.read()), true);
    }
    if (this.detached == null) {
      this.detached = frameModel(lx.getModel());
    }
    return view(this.detached, false);
  }

  /**
   * Moves the viewpoint. The returned view is read back after the move rather than echoed
   * from the request: {@link #normalize} clamps out-of-range angles, and a bound preview
   * clamps again through its own parameters.
   */
  public synchronized CameraView apply(LX lx, CameraAngle angle) {
    if (this.animation != null) {
      throw Resolve.invalidArgument(
          "A camera animation is in flight; wait for animate_camera to finish before "
              + "starting another camera move");
    }
    return applyStep(angle);
  }

  /**
   * Starts a camera move on the LX loop and returns immediately. Callers await the returned
   * handle off the engine thread; waiting on the engine thread would deadlock the loop that
   * advances it.
   */
  public synchronized CameraAnimation animate(
      LX lx, CameraAngle to, int durationMs, AnimationEase ease) {
    if (durationMs <= 0) {
      throw Resolve.invalidArgument("durationMs must be greater than 0");
    }
    if (this.animation != null) {
      throw Resolve.invalidArgument(
          "A camera animation is already in flight; wait for it to finish before starting "
              + "another");
    }
    CameraAnimation move = new CameraAnimation(
        current(lx).angle(), normalize(to), durationMs, ease);
    AnimationLoop loop = new AnimationLoop(lx, move);
    this.animation = loop;
    lx.engine.addLoopTask(loop);
    return move;
  }

  /** Whether the current camera is between animation endpoints right now. */
  public synchronized boolean isAnimating() {
    return this.animation != null;
  }

  private CameraView applyStep(CameraAngle angle) {
    CameraAngle normalized = normalize(angle);
    PreviewCamera bound = this.preview;
    if (bound == null) {
      this.detached = normalized;
      return view(normalized, false);
    }
    bound.apply(normalized);
    // Kept in step with what the preview actually took, so UI teardown leaves the last
    // observed viewpoint behind rather than dropping back to a stale one.
    CameraAngle readBack = normalize(bound.read());
    this.detached = readBack;
    return view(readBack, true);
  }

  private final class AnimationLoop implements LXLoopTask {

    private final LX lx;
    private final CameraAnimation move;
    private double elapsedMs;

    private AnimationLoop(LX lx, CameraAnimation move) {
      this.lx = lx;
      this.move = move;
    }

    @Override
    public void loop(double deltaMs) {
      synchronized (Cameras.this) {
        if (animation != this) {
          this.lx.engine.removeLoopTask(this);
          return;
        }
        try {
          this.elapsedMs = Math.min(
              this.move.durationMs(), this.elapsedMs + Math.max(0, deltaMs));
          double basis = this.elapsedMs / this.move.durationMs();
          CameraView view = (basis >= 1)
              ? applyStep(this.move.to())
              : applyStep(interpolate(
                  this.move.from(), this.move.to(), basis, this.move.ease()));
          if (basis < 1) {
            return;
          }
          animation = null;
          this.lx.engine.removeLoopTask(this);
          this.move.completion.complete(view);
        } catch (RuntimeException | Error failure) {
          animation = null;
          this.lx.engine.removeLoopTask(this);
          this.move.completion.completeExceptionally(failure);
          throw failure;
        }
      }
    }
  }

  /** Stores the angle under {@code name}, replacing any angle already saved under it. */
  public synchronized SaveResult save(String name, CameraAngle angle) {
    String key = requireName(name);
    CameraAngle normalized = normalize(angle);
    boolean replaced = this.saved.put(key, normalized) != null;
    return new SaveResult(new SavedCamera(key, normalized), replaced);
  }

  public synchronized List<SavedCamera> list() {
    List<SavedCamera> cameras = new ArrayList<>(this.saved.size());
    for (Map.Entry<String, CameraAngle> entry : this.saved.entrySet()) {
      cameras.add(new SavedCamera(entry.getKey(), entry.getValue()));
    }
    return cameras;
  }

  /** @throws Resolve.ResolveException NOT_FOUND if no angle is saved under {@code name} */
  public synchronized CameraView recall(LX lx, String name) {
    return apply(lx, lookup(name));
  }

  /** @throws Resolve.ResolveException NOT_FOUND if no angle is saved under {@code name} */
  public synchronized CameraAngle lookup(String name) {
    CameraAngle angle = this.saved.get(requireName(name));
    if (angle == null) {
      throw new Resolve.ResolveException(Resolve.Failure.NOT_FOUND,
          "No saved camera named '" + name + "' (list_cameras reports the saved names)");
    }
    return angle;
  }

  /** @throws Resolve.ResolveException NOT_FOUND if no angle is saved under {@code name} */
  public synchronized SavedCamera remove(String name) {
    String key = requireName(name);
    CameraAngle angle = this.saved.remove(key);
    if (angle == null) {
      throw new Resolve.ResolveException(Resolve.Failure.NOT_FOUND,
          "No saved camera named '" + name + "' (list_cameras reports the saved names)");
    }
    return new SavedCamera(key, angle);
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw Resolve.invalidArgument("Camera name must be a non-blank string");
    }
    String trimmed = name.trim();
    if (CURRENT.equals(trimmed)) {
      throw Resolve.invalidArgument(
          "'" + CURRENT + "' is reserved: it already means the live viewpoint wherever a "
              + "camera name is accepted. Pick another name.");
    }
    return trimmed;
  }

  private static CameraView view(CameraAngle angle, boolean livePreview) {
    return new CameraView(angle, eye(angle), livePreview);
  }

  /** LX's eye formula, verbatim (see the class javadoc). */
  public static Vec3 eye(CameraAngle angle) {
    double theta = Math.toRadians(angle.theta());
    double phi = Math.toRadians(angle.phi());
    double cosPhi = Math.cos(phi);
    double radius = angle.radius();
    return new Vec3(
        angle.target().x() + radius * cosPhi * Math.sin(theta),
        angle.target().y() + radius * Math.sin(phi),
        angle.target().z() - radius * cosPhi * Math.cos(theta));
  }

  /**
   * The inverse: an eye position and a look-at point expressed as an orbit angle. Exact
   * against {@link #eye} before clamping — a caller asking to look straight down comes
   * back at phi −89, having been moved a degree off the pole.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if the eye sits on the target, which
   *     names no direction at all
   */
  public static CameraAngle fromEye(
      Vec3 eye, Vec3 target, Projection projection, double fovDegrees) {
    double dx = eye.x() - target.x();
    double dy = eye.y() - target.y();
    double dz = eye.z() - target.z();
    double radius = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (radius == 0) {
      throw Resolve.invalidArgument(
          "eye and target are the same point; a camera at its own target has no view direction");
    }
    double phi = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, dy / radius))));
    // atan2(dx, -dz), not the usual atan2(z, x): LX's zero azimuth looks from -Z toward
    // +Z, which is what makes theta=0/phi=0 the same viewpoint as the 'front' ortho plane.
    double theta = Math.toDegrees(Math.atan2(dx, -dz));
    return normalize(new CameraAngle(theta, phi, radius, target, projection, fovDegrees));
  }

  /**
   * An angle that frames the whole model: centered on it, far enough out that its bounding
   * box fits at the default 60° lens. Falls back to LX's own default radius for a model
   * with no extent (empty project, or a single point).
   */
  public static CameraAngle frameModel(LXModel model) {
    Vec3 target = new Vec3(
        model.xMin + model.xRange / 2,
        model.yMin + model.yRange / 2,
        model.zMin + model.zRange / 2);
    double diagonal = Math.sqrt(
        model.xRange * model.xRange
            + model.yRange * model.yRange
            + model.zRange * model.zRange);
    double radius = (diagonal > 0) ? diagonal : DEFAULT_RADIUS;
    return normalize(
        new CameraAngle(0, 0, radius, target, Projection.PERSPECTIVE, DEFAULT_FOV));
  }

  /** Applies LX's parameter bounds: theta wraps, everything else clamps. */
  public static CameraAngle normalize(CameraAngle angle) {
    return new CameraAngle(
        wrapDegrees(angle.theta()),
        clamp(angle.phi(), PHI_MIN, PHI_MAX),
        clamp(angle.radius(), RADIUS_MIN, RADIUS_MAX),
        new Vec3(
            clamp(angle.target().x(), -TARGET_ABS_MAX, TARGET_ABS_MAX),
            clamp(angle.target().y(), -TARGET_ABS_MAX, TARGET_ABS_MAX),
            clamp(angle.target().z(), -TARGET_ABS_MAX, TARGET_ABS_MAX)),
        angle.projection(),
        clamp(angle.fovDegrees(), FOV_MIN, FOV_MAX));
  }

  /**
   * LX {@code UI3dContext.Camera.lerp} semantics, including shortest-arc theta and the
   * live UI's default 50% blend between linear time and the selected ease curve.
   */
  public static CameraAngle interpolate(
      CameraAngle fromAngle, CameraAngle toAngle, double basis, AnimationEase ease) {
    CameraAngle from = normalize(fromAngle);
    CameraAngle to = normalize(toAngle);
    double linearBasis = clamp(basis, 0, 1);
    double easedBasis = lerp(linearBasis, ease.shape(linearBasis), .5);

    double fromTheta = from.theta();
    double toTheta = to.theta();
    if (Math.abs(fromTheta - toTheta) > 180) {
      if (fromTheta < toTheta) {
        fromTheta += 360;
      } else {
        toTheta += 360;
      }
    }
    double theta = lerp(fromTheta, toTheta, easedBasis) % 360;
    Vec3 fromTarget = from.target();
    Vec3 toTarget = to.target();
    return new CameraAngle(
        theta,
        lerp(from.phi(), to.phi(), easedBasis),
        lerp(from.radius(), to.radius(), easedBasis),
        new Vec3(
            lerp(fromTarget.x(), toTarget.x(), easedBasis),
            lerp(fromTarget.y(), toTarget.y(), easedBasis),
            lerp(fromTarget.z(), toTarget.z(), easedBasis)),
        (linearBasis >= 1) ? to.projection() : from.projection(),
        lerp(from.fovDegrees(), to.fovDegrees(), easedBasis));
  }

  private static double lerp(double from, double to, double basis) {
    return from + (to - from) * basis;
  }

  private static double wrapDegrees(double degrees) {
    double wrapped = degrees % 360;
    return (wrapped < 0) ? wrapped + 360 : wrapped;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private static final String KEY_CAMERAS = "cameras";
  private static final String KEY_NAME = "name";
  private static final String KEY_THETA = "theta";
  private static final String KEY_PHI = "phi";
  private static final String KEY_RADIUS = "radius";
  private static final String KEY_TARGET_X = "targetX";
  private static final String KEY_TARGET_Y = "targetY";
  private static final String KEY_TARGET_Z = "targetZ";
  private static final String KEY_PROJECTION = "projection";
  private static final String KEY_FOV = "fovDegrees";

  @Override
  public synchronized void save(LX lx, JsonObject obj) {
    JsonArray cameras = new JsonArray();
    for (Map.Entry<String, CameraAngle> entry : this.saved.entrySet()) {
      CameraAngle angle = entry.getValue();
      JsonObject camera = new JsonObject();
      camera.addProperty(KEY_NAME, entry.getKey());
      camera.addProperty(KEY_THETA, angle.theta());
      camera.addProperty(KEY_PHI, angle.phi());
      camera.addProperty(KEY_RADIUS, angle.radius());
      camera.addProperty(KEY_TARGET_X, angle.target().x());
      camera.addProperty(KEY_TARGET_Y, angle.target().y());
      camera.addProperty(KEY_TARGET_Z, angle.target().z());
      camera.addProperty(KEY_PROJECTION, angle.projection().wire());
      camera.addProperty(KEY_FOV, angle.fovDegrees());
      cameras.add(camera);
    }
    obj.add(KEY_CAMERAS, cameras);
  }

  /**
   * Replaces the whole store, including on the empty object LX passes when a project has
   * no {@code chromatikMcpCameras} external — opening a project must not leave the
   * previous project's angles behind. The detached viewpoint is dropped for the same
   * reason: the incoming project has its own model to frame.
   *
   * <p>A malformed entry is skipped with a log line rather than failing the load: a
   * project that opens without its camera list is recoverable, one that refuses to open is
   * not.
   */
  @Override
  public synchronized void load(LX lx, JsonObject obj) {
    this.saved.clear();
    this.detached = null;
    if (!obj.has(KEY_CAMERAS) || !obj.get(KEY_CAMERAS).isJsonArray()) {
      return;
    }
    for (JsonElement element : obj.getAsJsonArray(KEY_CAMERAS)) {
      try {
        JsonObject camera = element.getAsJsonObject();
        String name = requireName(camera.get(KEY_NAME).getAsString());
        this.saved.put(name, normalize(new CameraAngle(
            camera.get(KEY_THETA).getAsDouble(),
            camera.get(KEY_PHI).getAsDouble(),
            camera.get(KEY_RADIUS).getAsDouble(),
            new Vec3(
                camera.get(KEY_TARGET_X).getAsDouble(),
                camera.get(KEY_TARGET_Y).getAsDouble(),
                camera.get(KEY_TARGET_Z).getAsDouble()),
            Projection.parse(camera.get(KEY_PROJECTION).getAsString()),
            camera.get(KEY_FOV).getAsDouble())));
      } catch (RuntimeException e) {
        LX.error(e, "[Chromatik-MCP] Skipping malformed saved camera: " + element);
      }
    }
  }
}
