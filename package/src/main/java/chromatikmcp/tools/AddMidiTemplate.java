package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Midi;

public final class AddMidiTemplate implements LxTool {

  @Override
  public String name() {
    return "add_midi_template";
  }

  @Override
  public String description() {
    return "Add a registered MIDI template to the project. Pass its full or simple class "
        + "name, template name, or expected MIDI device name as 'class' — for example "
        + "heronarts.lx.midi.template.AkaiMPD218, AkaiMPD218, Akai MPD218, or MPD218. "
        + "LX automatically selects a matching connected input/output when available. "
        + "Returns the new template in list_midi_templates' shape; inspect its path with "
        + "list_parameters to discover controls for wire_modulator/wire_trigger. Undoable "
        + "in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("class", Schemas.string(
        "Registered template class, template name, or MIDI device name (e.g. AkaiMPD218, "
            + "Akai MPD218, or MPD218)"));
    return Schemas.object(properties, List.of("class"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String type = Args.requireString(args, "class");
    return Result.ok(ListMidiTemplates.toMap(
        Midi.addTemplate(lx, Midi.resolveTemplateClass(lx, type))));
  }
}
