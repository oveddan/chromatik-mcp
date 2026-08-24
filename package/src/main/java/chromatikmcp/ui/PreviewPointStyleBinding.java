package chromatikmcp.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.glx.ui.component.UIPointCloud;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.studio.ui.preview.UIPreviewWindow;

import chromatikmcp.domain.PointStyle;
import chromatikmcp.domain.Resolve;

/** Binds the point-style tools to the settings that render Chromatik's main preview. */
final class PreviewPointStyleBinding implements PointStyle.PreviewPointStyle {

  private final Map<String, LXParameter> settings = new LinkedHashMap<>();

  PreviewPointStyleBinding(UIPreviewWindow preview) {
    // The primary preview constructs the global point cloud (global == self); auxiliary
    // windows point back to it and may opt into local parameters via useCustomParams. The
    // MCP surface intentionally targets the global instance so every write visibly changes
    // the main preview and does not expose a second window-selection knob.
    UIPointCloud pointCloud = preview.pointCloud.global;
    this.settings.put("pointSize", pointCloud.pointSize);
    this.settings.put("feather", pointCloud.feather);
    this.settings.put("sparkleAmount", pointCloud.sparkleAmount);
    this.settings.put("sparkleCurve", pointCloud.sparkleCurve);
    this.settings.put("sparkleRotate", pointCloud.sparkleRotate);
    this.settings.put("contrast", pointCloud.contrast);
    this.settings.put("gammaPow", pointCloud.gammaPow);
    this.settings.put("directionalDispersion", pointCloud.directionalDispersion);
    this.settings.put("directionalContrast", pointCloud.directionalContrast);
    this.settings.put(
        "directionalShowNormalsLength", pointCloud.directionalShowNormalsLength);
    this.settings.put("gammaFloor", pointCloud.gammaFloor);
    this.settings.put("alphaRef", pointCloud.alphaRef);
    this.settings.put("depthTest", pointCloud.depthTest);
    this.settings.put("directionalShowNormals", pointCloud.directionalShowNormals);
    this.settings.put("ledStyle", pointCloud.ledStyle);
    this.settings.put("directional", pointCloud.directional);
  }

  @Override
  public List<PointStyle.Setting> read() {
    return this.settings.entrySet().stream()
        .map(entry -> PointStyle.describe(entry.getKey(), entry.getValue()))
        .toList();
  }

  @Override
  public PointStyle.Setting set(String name, Object value) {
    LXParameter parameter = this.settings.get(name);
    if (parameter == null) {
      throw Resolve.invalidArgument(
          "Unknown point-style setting '" + name + "' — valid settings: "
              + String.join(", ", this.settings.keySet()));
    }
    return PointStyle.apply(name, parameter, value);
  }
}
