package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chromatikmcp.domain.Cameras;
import chromatikmcp.domain.Resolve;

/**
 * The camera argument grammar, shared by every tool that accepts a viewpoint so the two
 * spellings of it — orbit ({@code theta}/{@code phi}/{@code radius}) and position
 * ({@code eye}) — are parsed and documented in exactly one place.
 *
 * <p>Every field is optional and defaults to the camera being edited, so a caller can nudge
 * one axis without restating the rest.
 */
final class CameraArgs {

  static final String ORBIT_SUMMARY =
      "The camera orbits a look-at point: 'target' is that point, 'radius' the distance out "
          + "to the eye, 'theta' the azimuth in degrees (0 looks from -Z toward +Z, the same "
          + "viewpoint as get_frame's 'front' plane; increasing theta swings right) and 'phi' "
          + "the elevation in degrees (positive looks down from above, negative looks up from "
          + "below). Up is always +Y. Give 'eye' instead to place the camera by absolute "
          + "position — mutually exclusive with theta/phi/radius, and converted to the same "
          + "orbit angle, which the response reports back. Values out of LX's range are "
          + "clamped (phi to ±89°, fovDegrees to 15-150) and theta wraps, so read the "
          + "response rather than assuming the request landed verbatim.";

  private CameraArgs() {}

  /** The optional camera-field properties, merged into a tool's own input schema. */
  static Map<String, Object> schemaProperties() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("theta", Schemas.number(
        "Azimuth in degrees around +Y; 0 looks from -Z toward +Z. Wraps into [0, 360)."));
    properties.put("phi", Schemas.number(
        "Elevation in degrees from the XZ plane: positive looks down at the target, "
            + "negative looks up at it. Clamped to LX's ±89 (the look-at degenerates at "
            + "the poles), so 'straight up' is phi -89."));
    properties.put("radius", Schemas.number(
        "Distance from the eye to the target, in model units. Also sets the framing in "
            + "orthographic projection, where the visible width is radius."));
    properties.put("target", vec3Schema(
        "The look-at point in model coordinates (LX's camera center)."));
    properties.put("eye", vec3Schema(
        "Absolute camera position in model coordinates, as an alternative to "
            + "theta/phi/radius — pass it with 'target' to aim. Rejected alongside "
            + "theta/phi/radius."));
    properties.put("projection", Schemas.enumString(
        "'perspective' (a real lens — near geometry looms, which is what makes an interior "
            + "viewpoint read as interior) or 'orthographic' (parallel, no foreshortening; "
            + "good for a structural read of the model).",
        List.of("perspective", "orthographic")));
    properties.put("fovDegrees", Schemas.number(
        "Vertical field of view in degrees for perspective projection (LX's 'perspective' "
            + "lens control): 15 is a long lens, 150 an extreme wide angle. Clamped to "
            + "15-150. Carried but unused in orthographic projection."));
    return properties;
  }

  private static Map<String, Object> vec3Schema(String description) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("x", Schemas.number("X coordinate in model units"));
    properties.put("y", Schemas.number("Y coordinate in model units"));
    properties.put("z", Schemas.number("Z coordinate in model units"));
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("description", description + " All three components are required.");
    schema.put("properties", properties);
    schema.put("required", List.of("x", "y", "z"));
    schema.put("additionalProperties", false);
    return schema;
  }

  private static final List<String> FIELDS =
      List.of("theta", "phi", "radius", "target", "eye", "projection", "fovDegrees");

  /** Whether the arguments name any camera field at all. */
  static boolean present(Map<String, Object> args) {
    for (String field : FIELDS) {
      if (args.get(field) != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Applies whichever camera fields the caller supplied on top of {@code base}, leaving the
   * rest as they were.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH on a mistyped field, on mixing
   *     {@code eye} with the orbit fields, or on an eye that sits on its own target
   */
  static Cameras.CameraAngle merge(Map<String, Object> args, Cameras.CameraAngle base) {
    Cameras.Projection projection = base.projection();
    String projectionArg = Args.optionalString(args, "projection");
    if (projectionArg != null) {
      projection = Cameras.Projection.parse(projectionArg);
    }
    double fov = optionalDouble(args, "fovDegrees", base.fovDegrees());
    Cameras.Vec3 target = optionalVec3(args, "target", base.target());

    Map<String, Object> eye = Args.optionalMap(args, "eye");
    if (eye == null) {
      return new Cameras.CameraAngle(
          optionalDouble(args, "theta", base.theta()),
          optionalDouble(args, "phi", base.phi()),
          optionalDouble(args, "radius", base.radius()),
          target,
          projection,
          fov);
    }
    for (String orbitField : List.of("theta", "phi", "radius")) {
      if (args.get(orbitField) != null) {
        throw Resolve.invalidArgument(
            "eye and " + orbitField + " both set the camera position — pass one or the other");
      }
    }
    return Cameras.fromEye(vec3(eye, "eye"), target, projection, fov);
  }

  private static Cameras.Vec3 optionalVec3(
      Map<String, Object> args, String name, Cameras.Vec3 fallback) {
    Map<String, Object> value = Args.optionalMap(args, name);
    return (value == null) ? fallback : vec3(value, name);
  }

  private static Cameras.Vec3 vec3(Map<String, Object> value, String name) {
    return new Cameras.Vec3(
        component(value, name, "x"), component(value, name, "y"), component(value, name, "z"));
  }

  private static double component(Map<String, Object> value, String name, String axis) {
    if (!(value.get(axis) instanceof Number number) || !Double.isFinite(number.doubleValue())) {
      throw Resolve.invalidArgument(
          "Required finite number argument: " + name + "." + axis);
    }
    return number.doubleValue();
  }

  private static double optionalDouble(Map<String, Object> args, String name, double fallback) {
    Object value = args.get(name);
    if (value == null) {
      return fallback;
    }
    if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
      throw Resolve.invalidArgument("Optional finite number argument: " + name);
    }
    return number.doubleValue();
  }
}
