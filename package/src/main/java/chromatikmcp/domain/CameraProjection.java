package chromatikmcp.domain;

/**
 * Projects model coordinates onto an image plane from a {@link Cameras.CameraAngle} —
 * LX's projection, so the offline raster and Chromatik's on-screen preview frame the same
 * scene the same way.
 *
 * <p>It reproduces what {@code UI3dContext.draw()} hands to the GPU: a look-at basis from
 * eye to target with a fixed {@code +Y} up vector, then either a vertical-FOV perspective
 * frustum (near {@code radius/100}, far {@code radius*100} — LX's default depth of 2 as a
 * power of ten) or an orthographic box half as wide as {@code radius}. It is not a
 * pixel-exact copy of Chromatik's renderer — that is a shader drawing meshes and point
 * sprites — but the camera, framing, and what falls inside the frame do match.
 *
 * <p>Lives in {@code domain} rather than {@code render} because both consumers need it and
 * one of them is domain code: {@link Frames#summarize} buckets points into grid cells
 * through the same projection the PNG is rasterized with, so the numeric summary and the
 * image describe one view.
 *
 * <p>Immutable, so the raster may hold one and run off the engine thread.
 */
public final class CameraProjection implements Frames.GridProjection {

  /** LX's default {@code UI3dContext.depth} of 2, as the power of ten it is used as. */
  private static final double DEPTH_FACTOR = 100;

  /** LX's orthographic far plane, as a multiple of the camera radius. */
  private static final double ORTHOGRAPHIC_DEPTH = 10;

  private final double eyeX;
  private final double eyeY;
  private final double eyeZ;

  // Look-at basis: right, up, forward. Forward points from the eye toward the target.
  private final double rightX;
  private final double rightY;
  private final double rightZ;
  private final double upX;
  private final double upY;
  private final double upZ;
  private final double forwardX;
  private final double forwardY;
  private final double forwardZ;

  private final boolean perspective;
  private final double radius;
  /** Perspective: tan(fov/2), the half-height one unit ahead. Orthographic: the half-height. */
  private final double halfHeight;
  private final double aspect;
  private final double near;
  private final double far;

  /**
   * @param aspect image width divided by height; the vertical field of view is fixed and
   *     the horizontal one widens with it, exactly as LX's preview behaves when resized
   */
  public static CameraProjection of(Cameras.CameraAngle angle, double aspect) {
    if (!(aspect > 0) || !Double.isFinite(aspect)) {
      throw new IllegalArgumentException("aspect must be finite and positive: " + aspect);
    }
    return new CameraProjection(angle, aspect);
  }

  private CameraProjection(Cameras.CameraAngle angle, double aspect) {
    Cameras.Vec3 eye = Cameras.eye(angle);
    this.eyeX = eye.x();
    this.eyeY = eye.y();
    this.eyeZ = eye.z();

    double fx = angle.target().x() - eye.x();
    double fy = angle.target().y() - eye.y();
    double fz = angle.target().z() - eye.z();
    double length = Math.sqrt(fx * fx + fy * fy + fz * fz);
    if (length == 0) {
      // Only reachable at radius 0, which Cameras permits (LX's radius bound starts at 0).
      // Fall back to LX's zero-azimuth direction rather than dividing by zero.
      fx = 0;
      fy = 0;
      fz = 1;
      length = 1;
    }
    this.forwardX = fx / length;
    this.forwardY = fy / length;
    this.forwardZ = fz / length;

    // right = worldUp x forward, with worldUp = (0, 1, 0), which reduces to (fz, 0, -fx).
    // Never degenerate: phi is clamped to ±89, so forward is never parallel to +Y.
    double rx = this.forwardZ;
    double rz = -this.forwardX;
    double rightLength = Math.sqrt(rx * rx + rz * rz);
    this.rightX = rx / rightLength;
    this.rightY = 0;
    this.rightZ = rz / rightLength;

    // up = forward x right, completing a right-handed basis in which +Y is up the image.
    this.upX = this.forwardY * this.rightZ - this.forwardZ * this.rightY;
    this.upY = this.forwardZ * this.rightX - this.forwardX * this.rightZ;
    this.upZ = this.forwardX * this.rightY - this.forwardY * this.rightX;

    this.perspective = angle.projection() == Cameras.Projection.PERSPECTIVE;
    this.radius = angle.radius();
    this.aspect = aspect;
    if (this.perspective) {
      this.halfHeight = Math.tan(Math.toRadians(angle.fovDegrees()) / 2);
      this.near = angle.radius() / DEPTH_FACTOR;
      this.far = angle.radius() * DEPTH_FACTOR;
    } else {
      this.halfHeight = angle.radius() * 0.5 / aspect;
      this.near = 0;
      this.far = angle.radius() * ORTHOGRAPHIC_DEPTH;
    }
  }

  /**
   * Projects one model point into image space.
   *
   * <p>{@code out} receives {@code {u, v, depth}}: {@code u} runs 0→1 left to right and
   * {@code v} 0→1 top to bottom, both allowed outside that range for a point off the sides
   * of the frame, and {@code depth} is the distance ahead of the eye along the view axis —
   * what a painter's-algorithm raster sorts on.
   *
   * @return false when the point falls outside the near/far range (behind the camera, or
   *     beyond its depth of field), in which case {@code out} is untouched
   */
  public boolean project(double x, double y, double z, double[] out) {
    double dx = x - this.eyeX;
    double dy = y - this.eyeY;
    double dz = z - this.eyeZ;
    double depth = dx * this.forwardX + dy * this.forwardY + dz * this.forwardZ;
    if (depth < this.near || depth > this.far) {
      return false;
    }
    double horizontal = dx * this.rightX + dy * this.rightY + dz * this.rightZ;
    double vertical = dx * this.upX + dy * this.upY + dz * this.upZ;

    double ndcX;
    double ndcY;
    if (this.perspective) {
      if (depth <= 0) {
        // near is radius/100, so this only happens at radius 0 — nothing is in front.
        return false;
      }
      ndcY = vertical / (depth * this.halfHeight);
      ndcX = horizontal / (depth * this.halfHeight * this.aspect);
    } else {
      ndcY = vertical / this.halfHeight;
      ndcX = horizontal / (this.halfHeight * this.aspect);
    }
    out[0] = (ndcX + 1) / 2;
    out[1] = (1 - ndcY) / 2;
    out[2] = depth;
    return true;
  }

  /**
   * The depth at which a point should draw at its nominal size — the camera's own orbit
   * radius, so the target plane looks the same as it would orthographically and nearer
   * geometry looms. Zero under orthographic projection, which has no size falloff at all.
   */
  public double referenceDepth() {
    return this.perspective ? this.radius : 0;
  }

  @Override
  public int cell(Frames.FrameSnapshot snapshot, int index, int gridSize) {
    double[] projected = new double[3];
    if (!project(snapshot.x()[index], snapshot.y()[index], snapshot.z()[index], projected)) {
      return -1;
    }
    int col = (int) (projected[0] * gridSize);
    int row = (int) (projected[1] * gridSize);
    if (col < 0 || col >= gridSize || row < 0 || row >= gridSize) {
      return -1;
    }
    return row * gridSize + col;
  }
}
