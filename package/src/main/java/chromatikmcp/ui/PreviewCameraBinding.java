package chromatikmcp.ui;

import heronarts.glx.ui.UI3dContext;

import chromatikmcp.domain.Cameras;

/**
 * Binds {@link Cameras} to Chromatik's live 3D preview, so {@code set_camera} moves the
 * view a person is actually watching rather than a private copy of it.
 *
 * <p>The mapping is one-to-one because {@link Cameras}' angle model was taken from
 * {@code UI3dContext.Camera} in the first place — theta/phi/radius about a center, plus the
 * context-level projection mode and lens. Only the context-level {@code perspective} is
 * written: {@code UI3dContext.draw()} reads that one, not the per-camera copy, which
 * exists to be interpolated during a camera animation.
 *
 * <p>Reads take the camera's parameter values rather than {@code getEye()}/{@code
 * getCenter()}, which report the damped position mid-animation — a viewpoint read back
 * while the camera is still gliding toward it would not be the one the caller just set,
 * and would not be reproducible.
 *
 * <p>Threading: both methods run on the LX engine thread while the UI thread draws. They
 * only set and read {@code LXParameter} values, which is exactly the cross-thread traffic
 * LX's own OSC engine already generates against UI-visible parameters; the preview picks
 * the new target up on its next frame.
 */
final class PreviewCameraBinding implements Cameras.PreviewCamera {

  private final UI3dContext context;

  PreviewCameraBinding(UI3dContext context) {
    this.context = context;
  }

  @Override
  public Cameras.CameraAngle read() {
    UI3dContext.Camera camera = this.context.camera;
    return new Cameras.CameraAngle(
        camera.theta.getValue(),
        camera.phi.getValue(),
        camera.radius.getValue(),
        new Cameras.Vec3(camera.x.getValue(), camera.y.getValue(), camera.z.getValue()),
        projection(),
        this.context.perspective.getValue());
  }

  @Override
  public void apply(Cameras.CameraAngle angle) {
    this.context.projection.setValue(
        (angle.projection() == Cameras.Projection.ORTHOGRAPHIC)
            ? UI3dContext.ProjectionMode.ORTHOGRAPHIC
            : UI3dContext.ProjectionMode.PERSPECTIVE);
    this.context.setPerspective((float) angle.fovDegrees());
    this.context.setCenter(
        (float) angle.target().x(), (float) angle.target().y(), (float) angle.target().z());
    this.context.setTheta(angle.theta());
    this.context.setPhi((float) angle.phi());
    this.context.setRadius((float) angle.radius());
  }

  private Cameras.Projection projection() {
    return (this.context.projection.getEnum() == UI3dContext.ProjectionMode.ORTHOGRAPHIC)
        ? Cameras.Projection.ORTHOGRAPHIC
        : Cameras.Projection.PERSPECTIVE;
  }
}
