package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import chromatikmcp.domain.Cameras;

/**
 * Wire serializer for a 3D viewpoint. Every camera tool emits the angle through
 * {@link #angleToMap}, so {@code get_camera}, {@code set_camera}, {@code recall_camera} and
 * the {@code list_cameras} entries cannot drift apart on field names.
 *
 * <p>Values are rounded to {@link #DECIMALS} places: the orbit-to-eye trigonometry
 * otherwise reports a camera dead ahead as {@code 1.47e-14} off center, which reads as
 * meaningful precision when it is float noise.
 */
public final class CameraPayload {

  private static final int DECIMALS = 4;
  private static final double SCALE = Math.pow(10, DECIMALS);

  private CameraPayload() {}

  /** The angle plus its derived eye position — the shape every camera payload embeds. */
  public static Map<String, Object> angleToMap(Cameras.CameraAngle angle) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("projection", angle.projection().wire());
    payload.put("fovDegrees", round(angle.fovDegrees()));
    payload.put("theta", round(angle.theta()));
    payload.put("phi", round(angle.phi()));
    payload.put("radius", round(angle.radius()));
    payload.put("target", vec3ToMap(angle.target()));
    payload.put("eye", vec3ToMap(Cameras.eye(angle)));
    return payload;
  }

  public static Map<String, Object> toMap(Cameras.CameraView view) {
    Map<String, Object> payload = angleToMap(view.angle());
    payload.put("livePreview", view.livePreview());
    return payload;
  }

  public static Map<String, Object> toMap(Cameras.SavedCamera saved) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", saved.name());
    payload.putAll(angleToMap(saved.angle()));
    return payload;
  }

  public static Map<String, Object> vec3ToMap(Cameras.Vec3 vector) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("x", round(vector.x()));
    payload.put("y", round(vector.y()));
    payload.put("z", round(vector.z()));
    return payload;
  }

  private static double round(double value) {
    return Math.round(value * SCALE) / SCALE;
  }
}
