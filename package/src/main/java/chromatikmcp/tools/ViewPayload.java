package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import chromatikmcp.domain.Views;

/** Wire serializer for a named model view. */
public final class ViewPayload {

  private ViewPayload() {}

  public static Map<String, Object> toMap(Views.ViewInfo view) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", view.path());
    payload.put("label", view.label());
    payload.put("selector", view.selector());
    payload.put("enabled", view.enabled());
    payload.put("priority", view.priority());
    payload.put("normalization", view.normalization());
    payload.put("orientation", view.orientation());
    payload.put("numGroups", view.numGroups());
    payload.put("numFixtures", view.numFixtures());
    payload.put("cuePath", view.cuePath());
    return payload;
  }
}
