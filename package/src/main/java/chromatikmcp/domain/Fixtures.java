package chromatikmcp.domain;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.command.LXCommand;
import heronarts.lx.model.LXModel;
import heronarts.lx.output.LXBufferOutput;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.LXFixture;
import heronarts.lx.structure.LXProtocolFixture;

/**
 * Read-only snapshot of the fixture layer: the physical wiring beneath the model tree
 * ({@link Model}/{@code describe_model}) — each fixture's geometry transform, output
 * protocol wiring, and (for {@link JsonFixture}) its declared parameters.
 */
public final class Fixtures {

  public record TransformInfo(double x, double y, double z,
      double yaw, double pitch, double roll, double scale) {}

  /**
   * {@code protocol}/{@code byteOrder} report the raw LX enum constant name (e.g.
   * {@code ARTNET}, {@code RGB}) rather than a lower-cased label — these are wiring
   * identifiers, not UI copy. {@code universe}/{@code channel}/{@code port} are already
   * resolved per-protocol (e.g. {@code universe} is {@code artNetUniverse} for ARTNET/SACN
   * but {@code ddpDataOffset} for DDP) — see {@link #protocolPort}/{@link #protocolUniverse}/
   * {@link #protocolChannel}.
   */
  public record OutputInfo(String protocol, String host, int port, int universe, int channel,
      String byteOrder, boolean reverse) {}

  /** Present only for a {@link JsonFixture}: its {@code error}/{@code warning} state. */
  public record JsonInfo(String fixturePath, boolean error, String errorMessage,
      List<String> warnings) {}

  /** One parameter the {@code .lxf} file declares (a {@code JsonFixture}-only knob). */
  public record JsonParameterInfo(String name, String label, String type, Object value) {}

  /**
   * {@code childCount} is the number of subfixtures (real {@link LXFixture} children — see
   * {@link #children}); {@code submodelCount} is the number of {@link LXModel} nodes the
   * fixture's own model groups its points into (e.g. a {@code GridFixture}'s per-row/per-column
   * submodels). These are genuinely different things — a {@code GridFixture} has 0 subfixtures
   * but several submodels — so both are reported rather than conflated.
   */
  /**
   * {@code modelAvailable} is {@code false} only when {@code fixture.getModel()} is
   * currently {@code null} — a deactivated fixture (see {@code LXFixture.deactivate}) has no
   * built model until it is reactivated and the structure regenerates ({@code
   * LXStructure.regenerateModel} skips {@code toModel()} for deactivated fixtures). When
   * {@code false}, {@code tags} falls back to {@code fixture.tagList} (the {@code .lxf}-declared
   * subset, never null) and {@code submodelCount} reports 0.
   */
  public record FixtureInfo(String path, int id, int index, String label, String type,
      int size, Integer firstIndex, Integer lastIndex, boolean enabled, boolean deactivate,
      double brightness, List<String> tags, int childCount, int submodelCount,
      boolean modelAvailable, TransformInfo transform, OutputInfo output, JsonInfo json) {}

  /** One node in a subfixture tree; {@code children} is {@code null} when the walk stopped
   * before descending (depth exhausted), mirroring {@link Model.NodeInfo}. */
  public record FixtureNode(FixtureInfo info, List<FixtureNode> children) {}

  /** {@code subfixturesAvailable} is {@code false} only when the reflective {@link #children}
   * accessor failed to initialize — a degraded run, surfaced rather than silently zeroed. */
  public record FixturesSnapshot(String modelName, boolean isStatic, int totalPoints,
      String outputError, boolean subfixturesAvailable, List<FixtureInfo> fixtures) {}

  private Fixtures() {}

  /** Call on the engine thread; the returned records are safe to read anywhere. */
  public static FixturesSnapshot describe(LX lx) {
    List<FixtureInfo> fixtures = new ArrayList<>();
    for (LXFixture fixture : lx.structure.fixtures) {
      fixtures.add(describeFixture(fixture));
    }
    // outputError defaults to null (unset), not "" — normalize so the wire payload always
    // carries a string, and "empty when clean" (per the tool description) is unambiguous.
    String outputError = lx.structure.outputError.getString();
    return new FixturesSnapshot(
        lx.structure.modelName.getString(),
        lx.structure.isStatic.isOn(),
        lx.getModel().size,
        (outputError == null) ? "" : outputError,
        subfixturesAvailable(),
        fixtures);
  }

  /** Describe a single fixture. Call on the engine thread. */
  public static FixtureInfo describeFixture(LXFixture fixture) {
    int size = fixture.totalSize();
    Integer firstIndex = null;
    Integer lastIndex = null;
    if (size > 0) {
      firstIndex = fixture.getIndexBufferOffset();
      lastIndex = firstIndex + size - 1;
    }
    LXModel model = fixture.getModel();
    return new FixtureInfo(
        Resolve.canonicalPath(fixture),
        fixture.getId(),
        fixture.getIndex(),
        fixture.getLabel(),
        typeOf(fixture),
        size,
        firstIndex,
        lastIndex,
        fixture.enabled.isOn(),
        fixture.deactivate.isOn(),
        fixture.brightness.getValue(),
        // model.tags is the *effective* tag list (fixture.tagList — only a .lxf "tags"
        // declaration populates it — plus any tokens from the user-editable fixture.tags
        // string field, see Fixtures#setTags); fixture.tagList alone would silently miss
        // every tag set_fixture_tags writes. When the model isn't currently built (deactivated
        // fixture), fall back to tagList rather than crash.
        (model != null) ? List.copyOf(model.tags) : fixture.tagList,
        children(fixture).size(),
        (model != null) ? model.children.length : 0,
        model != null,
        new TransformInfo(
            fixture.x.getValue(), fixture.y.getValue(), fixture.z.getValue(),
            fixture.yaw.getValue(), fixture.pitch.getValue(), fixture.roll.getValue(),
            fixture.scale.getValue()),
        (fixture instanceof LXProtocolFixture protocolFixture) ? describeOutput(protocolFixture) : null,
        (fixture instanceof JsonFixture jsonFixture) ? describeJson(jsonFixture) : null);
  }

  /**
   * Describe {@code fixture} and, while {@code depth} remains, its subfixtures recursively.
   * Mirrors {@link Model#describeNode(LXModel, int)} so {@code get_fixture} and
   * {@code describe_model} behave consistently. Call on the engine thread.
   */
  public static FixtureNode describeTree(LXFixture fixture, int depth) {
    List<FixtureNode> children = null;
    if (depth > 0) {
      children = new ArrayList<>();
      for (LXFixture child : children(fixture)) {
        children.add(describeTree(child, depth - 1));
      }
    }
    return new FixtureNode(describeFixture(fixture), children);
  }

  /**
   * The fixture's own direct child model nodes (e.g. a {@code GridFixture}'s per-row/
   * per-column submodels) — distinct from {@link #children}, which are subfixtures, not
   * model nodes. Empty when the fixture's model isn't currently built (see {@code
   * modelAvailable} on {@link FixtureInfo}). Call on the engine thread.
   */
  public static List<Model.NodeInfo> submodels(LXFixture fixture) {
    LXModel model = fixture.getModel();
    if (model == null) {
      return List.of();
    }
    List<Model.NodeInfo> submodels = new ArrayList<>();
    for (LXModel child : model.children) {
      submodels.add(Model.describeNode(child, 0));
    }
    return submodels;
  }

  /**
   * One fixture's DECLARED/DERIVED output wiring — see {@link #outputMap}. For an
   * {@link LXProtocolFixture} with a protocol other than {@code NONE}, {@code protocol}
   * through {@code numChannels} report the same values {@link #describeOutput} computes,
   * plus {@code estimatedUniverseSpan} (universe-based protocols only — ARTNET/SACN/KINET;
   * {@code null} for OPC/DDP, which use a data-length model instead of universes). For a
   * protocol of {@code NONE}, only {@code protocol} is set — the fixture emits nothing of
   * its own. {@code directOutputCount}/{@code outputsNote} are set instead of (or alongside)
   * the protocol fields when the fixture (typically a {@link JsonFixture}) declares raw
   * {@code outputsDirect} datagrams, whose universe/channel LX exposes no public accessor
   * for — see the class-level note on {@link #outputMap}.
   */
  public record OutputMapEntry(String path, String type, Integer firstIndex, Integer lastIndex,
      String protocol, String host, Integer port, Integer universe, Integer channel,
      String byteOrder, Integer numChannels, List<Integer> estimatedUniverseSpan,
      Integer directOutputCount, String outputsNote,
      int childCount, List<OutputMapEntry> children) {}

  /** {@code note} restates, in the wire payload itself, the estimate/collision caveats
   * documented on {@link #outputMap} — so a client reading only the JSON (not the tool
   * description) still sees them. */
  public record OutputMapSnapshot(String outputError, List<OutputMapEntry> fixtures, String note) {}

  private static final int OUTPUT_MAP_MAX_DEPTH = 10;

  private static final String OUTPUT_MAP_NOTE =
      "This is a DECLARED/DERIVED map, not LX's resolved output allocation: LXStructureOutput's "
          + "actual per-packet layout (generatedOutputs/Packet) is private and not readable "
          + "through any public API. Per-fixture protocol/universe/channel fields are read "
          + "straight from the fixture's own parameters; estimatedUniverseSpan approximates a "
          + "single contiguous DMX segment per fixture by mirroring LX's universe-overflow math "
          + "(LXStructureOutput.checkUniverseOverflow) — it ignores serpentine wiring, segment "
          + "stride, and cross-fixture packet packing, so it can diverge from LX's real "
          + "allocation on complex rigs. outputError, when non-empty, is a real collision LX "
          + "itself computed (lx.structure.outputError) and should be trusted over the estimate.";

  private static final String OUTPUTS_DIRECT_NOTE =
      "This fixture's output wiring is declared inside its .lxf file as direct output "
          + "datagrams (outputsDirect) rather than the protocol/universe/channel parameters "
          + "LXProtocolFixture exposes — LX has no public accessor for a direct output's "
          + "resolved universe/channel, so only the declared output count is reported here; no "
          + "universe/channel is estimated for it.";

  /**
   * The declared/derived output wiring for {@code root} (or every top-level fixture when
   * {@code root} is {@code null}) and its subfixture tree, depth-limited like {@link
   * #describeTree} (real rigs can be hundreds of nodes deep). See the field docs on {@link
   * OutputMapEntry} and the caveats in {@link #OUTPUT_MAP_NOTE}. Call on the engine thread.
   */
  public static OutputMapSnapshot outputMap(LX lx, LXFixture root) {
    List<LXFixture> roots = (root != null) ? List.of(root) : lx.structure.fixtures;
    List<OutputMapEntry> fixtures = new ArrayList<>();
    for (LXFixture fixture : roots) {
      fixtures.add(outputMapEntry(fixture, OUTPUT_MAP_MAX_DEPTH));
    }
    String outputError = lx.structure.outputError.getString();
    return new OutputMapSnapshot(
        (outputError == null) ? "" : outputError, fixtures, OUTPUT_MAP_NOTE);
  }

  private static OutputMapEntry outputMapEntry(LXFixture fixture, int depth) {
    int totalSize = fixture.totalSize();
    Integer firstIndex = null;
    Integer lastIndex = null;
    if (totalSize > 0) {
      firstIndex = fixture.getIndexBufferOffset();
      lastIndex = firstIndex + totalSize - 1;
    }

    // LXFixture.size() (own point count, excluding subfixtures) is `protected` — unreachable
    // from here, same gap `describeFixture` works around by reporting `totalSize()` instead.
    // totalSize() = own size + every child's totalSize() (LXFixture.java:1285), so subtracting
    // the children's totalSize() back out recovers the own-point count publicly.
    List<LXFixture> kids = children(fixture);
    int ownSize = totalSize;
    for (LXFixture child : kids) {
      ownSize -= child.totalSize();
    }

    String protocol = null;
    String host = null;
    Integer port = null;
    Integer universe = null;
    Integer channel = null;
    String byteOrder = null;
    Integer numChannels = null;
    List<Integer> estimatedUniverseSpan = null;

    if (fixture instanceof LXProtocolFixture protocolFixture) {
      LXFixture.Protocol proto = protocolFixture.protocol.getEnum();
      protocol = proto.name();
      if (proto != LXFixture.Protocol.NONE) {
        host = protocolFixture.host.getString();
        port = protocolPort(protocolFixture);
        universe = protocolUniverse(protocolFixture);
        channel = protocolChannel(protocolFixture);
        LXBufferOutput.ByteOrder order = protocolFixture.byteOrder.getEnum();
        byteOrder = order.name();
        numChannels = ownSize * order.getNumBytes();
        if (isUniverseBasedProtocol(proto)) {
          estimatedUniverseSpan = estimateUniverseSpan(proto, universe, channel, numChannels);
        }
      }
    }

    Integer directOutputCount = null;
    String outputsNote = null;
    if (fixture instanceof JsonFixture || !fixture.outputsDirect.isEmpty()) {
      directOutputCount = fixture.outputsDirect.size();
      outputsNote = OUTPUTS_DIRECT_NOTE;
    }

    List<OutputMapEntry> childEntries = null;
    if (depth > 0) {
      childEntries = new ArrayList<>();
      for (LXFixture child : kids) {
        childEntries.add(outputMapEntry(child, depth - 1));
      }
    }

    return new OutputMapEntry(
        Resolve.canonicalPath(fixture),
        typeOf(fixture),
        firstIndex,
        lastIndex,
        protocol,
        host,
        port,
        universe,
        channel,
        byteOrder,
        numChannels,
        estimatedUniverseSpan,
        directOutputCount,
        outputsNote,
        kids.size(),
        childEntries);
  }

  private static boolean isUniverseBasedProtocol(LXFixture.Protocol protocol) {
    return protocol == LXFixture.Protocol.ARTNET
        || protocol == LXFixture.Protocol.SACN
        || protocol == LXFixture.Protocol.KINET;
  }

  /**
   * Mirrors {@code LXStructureOutput.checkUniverseOverflow}: {@code maxChannels} channels
   * fill a universe (indices {@code [0, maxChannels)}); the {@code numChannels}-th channel
   * from {@code startChannel} lands {@code (startChannel + numChannels - 1) / maxChannels}
   * universes past {@code startUniverse}. Assumes one contiguous segment starting exactly at
   * {@code startChannel} — the simplification {@link #OUTPUT_MAP_NOTE} discloses.
   */
  private static List<Integer> estimateUniverseSpan(
      LXFixture.Protocol protocol, int startUniverse, int startChannel, int numChannels) {
    if (numChannels <= 0) {
      return List.of(startUniverse, startUniverse);
    }
    int lastChannelOffset = startChannel + numChannels - 1;
    int endUniverse = startUniverse + (lastChannelOffset / protocol.maxChannels);
    return List.of(startUniverse, endUniverse);
  }

  // Reflective accessor for LXFixture.children (structure/LXFixture.java:406), which is
  // `protected final`. Subfixtures are attached via child.setParent(this)
  // (LXFixture.java:577), which does NOT register them in LXComponent.children or
  // LXComponent.childArrays — so without this, subfixtures are neither enumerable nor
  // path-resolvable through any public API. This is the only reflective code in the
  // project; deleted (one-line change to call the new accessor) once upstream adds a
  // public `getChildren()` (filed alongside heronarts/LX#152/#153).
  private static final Field CHILDREN_FIELD;

  static {
    Field field;
    try {
      field = LXFixture.class.getDeclaredField("children");
      field.setAccessible(true);
    } catch (NoSuchFieldException | SecurityException | InaccessibleObjectException e) {
      field = null;
    }
    CHILDREN_FIELD = field;
  }

  /** {@code true} unless the reflective accessor above failed to initialize. */
  public static boolean subfixturesAvailable() {
    return CHILDREN_FIELD != null;
  }

  /**
   * The subfixtures directly beneath {@code fixture} (e.g. a {@code JsonFixture}'s
   * {@code .lxf}-declared {@code components}). Never throws across a tool boundary: returns
   * an empty list if the reflective accessor is unavailable or the read fails.
   */
  @SuppressWarnings("unchecked")
  public static List<LXFixture> children(LXFixture fixture) {
    if (CHILDREN_FIELD == null) {
      return List.of();
    }
    try {
      return List.copyOf((List<LXFixture>) CHILDREN_FIELD.get(fixture));
    } catch (IllegalAccessException e) {
      return List.of();
    }
  }

  /**
   * {@code true} when a STRICT ancestor of {@code fixture} is a {@link JsonFixture} — i.e.
   * {@code fixture} is a subfixture (or deeper descendant) of a {@code .lxf}-loaded fixture.
   * {@code fixture} itself being a {@code JsonFixture} does NOT count: a top-level
   * {@code .lxf} fixture's own JSON parameters (e.g. {@code nodeSpacing}) are configurable,
   * but not via a canonical path — {@code JsonFixture} never registers them as addressable
   * {@code LXParameter}s, so they're reached by name through the fixture-editing tool, not
   * {@code set_parameter}. Its descendants' parameter values, by contrast, ARE addressable
   * parameters, computed from {@code $expr} and recomputed on every reload, so writes to
   * them are rejected elsewhere (see {@code Parameters.set}).
   */
  public static boolean isJsonDerived(LXFixture fixture) {
    LXComponent parent = fixture.getParent();
    while (parent != null) {
      if (parent instanceof JsonFixture) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  /** {@code undoEntries} counts the undo-stack entries the write produced: 1 when every
   * edited parameter was numeric/boolean (all batched into a single {@code ArrangeFixtures}),
   * plus 1 per string parameter (no batched string command exists). {@code values} is the
   * re-read result for every name in the request, in the order requested. {@code
   * shadowedJsonParams} lists names, if any, that were written to the registered geometry
   * parameter of that name while a same-named {@code .lxf}-declared parameter (legal for any
   * name except {@code instances}/{@code instance}) went untouched — see {@link #resolveByName}. */
  public record SetParamsResult(int undoEntries, Map<String, Object> values,
      List<String> shadowedJsonParams) {}

  /**
   * Set several of {@code fixture}'s parameters — registered ones (reachable via {@code
   * fixture.getParameter(name)}) and, for a {@link JsonFixture}, its {@code .lxf}-declared
   * ones (reachable only via {@link JsonFixture#getJsonParameters()}, by {@code name}, since
   * they carry no canonical path) — in one call. Validation is atomic: every name is
   * resolved and every value coerced <strong>before</strong> anything is written, so an
   * unknown name or a type mismatch on any entry leaves the fixture untouched. The write
   * phase is not atomic, though: numeric/boolean edits are always batched into a single
   * {@code LXCommand.Structure.ArrangeFixtures} and performed first (one undo entry),
   * regardless of the caller's map order, followed by one individual {@code
   * LXCommand.Parameter.SetString} per string edit (no batched string command exists). If a
   * {@code SetString} fails partway through a mixed numeric+string call, the earlier writes
   * stay applied — and {@code LXCommandEngine.perform} clears the <strong>entire</strong>
   * undo/redo stack on any command failure, not just the failing entry. Call on the engine
   * thread.
   *
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} for a {@code fixture} that is a
   *     subfixture of a {@code JsonFixture} (see {@link #isJsonDerived}), an unknown
   *     parameter name, or a value that doesn't match the resolved parameter's type
   */
  public static SetParamsResult setParams(LX lx, LXFixture fixture, Map<String, Object> params) {
    if (isJsonDerived(fixture)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          Resolve.canonicalPath(fixture) + " is computed from its .lxf file and will be "
              + "overwritten on reload — edit the .lxf and call reload_fixtures instead");
    }
    if (params.isEmpty()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH, "params must not be empty");
    }

    JsonFixture jsonFixture = (fixture instanceof JsonFixture jf) ? jf : null;

    List<Parameters.Coerced> numericOrBooleanEdits = new ArrayList<>();
    List<Parameters.Coerced> stringEdits = new ArrayList<>();
    List<String> shadowedJsonParams = new ArrayList<>();
    for (Map.Entry<String, Object> entry : params.entrySet()) {
      String name = entry.getKey();
      LXParameter parameter = resolveByName(fixture, jsonFixture, name);
      if (parameter == fixture.getParameter(name) && jsonFixture != null
          && declaresJsonParameter(jsonFixture, name)) {
        // resolveByName's registered-wins precedence silently leaves the same-named .lxf
        // parameter untouched — surface it rather than let the caller assume it was written.
        shadowedJsonParams.add(name);
      }
      Parameters.Coerced coerced = Parameters.classify(parameter, entry.getValue());
      if (coerced.kind() == Parameters.Kind.STRING) {
        stringEdits.add(coerced);
      } else {
        numericOrBooleanEdits.add(coerced);
      }
    }

    int undoEntries = 0;
    if (!numericOrBooleanEdits.isEmpty()) {
      LXCommand.Structure.ArrangeFixtures arrange = new LXCommand.Structure.ArrangeFixtures();
      for (Parameters.Coerced edit : numericOrBooleanEdits) {
        if (edit.parameter() instanceof BooleanParameter b) {
          arrange.add(b, (Boolean) edit.value());
        } else if (edit.kind() == Parameters.Kind.DISCRETE) {
          arrange.add(edit.parameter(), (double) (Integer) edit.value());
        } else {
          arrange.add(edit.parameter(), (Double) edit.value());
        }
      }
      Commands.perform(lx, arrange);
      ++undoEntries;
    }
    for (Parameters.Coerced edit : stringEdits) {
      Commands.perform(lx, new LXCommand.Parameter.SetString(
          (StringParameter) edit.parameter(), (String) edit.value()));
      ++undoEntries;
    }

    Map<String, JsonParameterInfo> jsonValues = (jsonFixture == null)
        ? Map.of()
        : jsonParameters(jsonFixture).stream()
            .collect(Collectors.toMap(JsonParameterInfo::name, info -> info));
    Map<String, Object> values = new LinkedHashMap<>();
    for (String name : params.keySet()) {
      LXParameter registered = fixture.getParameter(name);
      values.put(name, (registered != null)
          ? Parameters.describe(registered).value()
          : jsonValues.get(name).value());
    }
    return new SetParamsResult(undoEntries, values, shadowedJsonParams);
  }

  private static LXParameter resolveByName(LXFixture fixture, JsonFixture jsonFixture, String name) {
    LXParameter registered = fixture.getParameter(name);
    if (registered != null) {
      return registered;
    }
    if (jsonFixture != null) {
      for (JsonFixture.ParameterDefinition definition : jsonFixture.getJsonParameters()) {
        if (definition.name.equals(name)) {
          return definition.parameter;
        }
      }
    }
    throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
        "No parameter named '" + name + "' on " + Resolve.canonicalPath(fixture)
            + " (checked registered parameters"
            + ((jsonFixture != null) ? " and .lxf-declared parameters" : "") + ")");
  }

  private static boolean declaresJsonParameter(JsonFixture jsonFixture, String name) {
    for (JsonFixture.ParameterDefinition definition : jsonFixture.getJsonParameters()) {
      if (definition.name.equals(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Set a fixture's model tags, routed through {@code LXCommand.Parameter.SetString} on
   * {@code fixture.tags} (the user-editable "Tag" string field — distinct from {@code
   * tagList}, which only a {@code .lxf}'s own {@code "tags"} declaration populates) for
   * undo. Every token is validated against {@code LXModel.Tag.VALID_TAG_REGEX}
   * <strong>before</strong> writing — {@code LXFixture.getModelTags} silently drops any
   * token that fails this regex, and silently restores the fixture's default tags if every
   * token fails, so an unvalidated write can look successful while quietly breaking
   * view-selector addressing. Call on the engine thread.
   *
   * @return the fixture's resulting effective model tags ({@code fixture.getModel().tags}
   *     — {@code tags} plus any {@code tagList} from a {@code .lxf} declaration), re-read
   *     after the write
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} for a {@code fixture} that is a
   *     subfixture of a {@code JsonFixture}, an empty tag list, an empty token, or a token
   *     that fails {@code LXModel.Tag.VALID_TAG_REGEX}
   */
  public static List<String> setTags(LX lx, LXFixture fixture, List<String> tags) {
    if (isJsonDerived(fixture)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          Resolve.canonicalPath(fixture) + " is computed from its .lxf file and will be "
              + "overwritten on reload — edit the .lxf and call reload_fixtures instead");
    }
    if (tags.isEmpty()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "tags must not be empty — an empty tag list would silently restore the fixture's "
              + "default tags (LXFixture.getModelTags)");
    }
    for (String tag : tags) {
      if (tag.isEmpty() || !LXModel.Tag.isValid(tag)) {
        throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
            "Invalid tag token: '" + tag + "' — tags must match " + LXModel.Tag.VALID_TAG_REGEX
                + " (LXFixture silently drops invalid tokens rather than rejecting them, so "
                + "this call rejects up front instead)");
      }
    }
    Commands.perform(lx, new LXCommand.Parameter.SetString(fixture.tags, String.join(" ", tags)));
    LXModel model = fixture.getModel();
    return (model != null) ? List.copyOf(model.tags) : fixture.tagList;
  }

  /** One entry from {@code lx.registry.jsonFixtureErrors} — a {@code .lxf} that failed to
   * parse (syntax/I-O error), distinct from a per-fixture {@link JsonInfo} load error. */
  public record JsonTypeError(String path, String type, String exception) {}

  /** {@code jsonTypes}/{@code errors} come from a fresh {@code lx.registry.reloadJsonFixtures()}
   * walk of the Fixtures folder; {@code fixtures} is every top-level fixture's state after
   * {@code lx.structure.reload()} re-reads every instantiated {@code JsonFixture} from disk. */
  public record ReloadResult(List<String> jsonTypes, List<JsonTypeError> errors, List<FixtureInfo> fixtures) {}

  /**
   * Pick up {@code .lxf} edits made on disk (with an external tool — nothing watches the
   * Fixtures folder). {@code lx.registry.reloadJsonFixtures()} refreshes the available
   * fixture *type* list; {@code lx.structure.reload()} then reloads every instantiated
   * top-level {@code JsonFixture} and regenerates the model exactly once — the only batched
   * regeneration path in LX. Not undoable. Call on the engine thread.
   */
  public static ReloadResult reload(LX lx) {
    lx.registry.reloadJsonFixtures();
    lx.structure.reload();

    List<String> jsonTypes = new ArrayList<>();
    for (var jsonType : lx.registry.jsonFixtures) {
      jsonTypes.add(jsonType.type);
    }
    List<FixtureInfo> fixtures = new ArrayList<>();
    for (LXFixture fixture : lx.structure.fixtures) {
      fixtures.add(describeFixture(fixture));
    }
    return new ReloadResult(jsonTypes, describeJsonTypeErrors(lx), fixtures);
  }

  /** One {@code .lxf} type {@code lx.registry.jsonFixtures} advertises. */
  public record JsonTypeInfo(String type, boolean isVisible) {}

  /** What {@code add_fixture} can instantiate, and where {@code .lxf} files live on disk. */
  public record AvailableFixtures(List<String> classes, List<JsonTypeInfo> jsonTypes,
      List<JsonTypeError> errors, String fixturesDirectory) {}

  /**
   * What {@code add_fixture} can instantiate, and where {@code .lxf} files live on disk (an
   * agent writing its own fixture file needs this path). Call on the engine thread.
   */
  public static AvailableFixtures describeAvailable(LX lx) {
    List<String> classes = new ArrayList<>();
    for (Class<? extends LXFixture> clazz : lx.registry.fixtures) {
      classes.add(clazz.getSimpleName());
    }
    List<JsonTypeInfo> jsonTypes = new ArrayList<>();
    for (var jsonType : lx.registry.jsonFixtures) {
      jsonTypes.add(new JsonTypeInfo(jsonType.type, jsonType.isVisible));
    }
    return new AvailableFixtures(classes, jsonTypes, describeJsonTypeErrors(lx),
        lx.getMediaFolder(LX.Media.FIXTURES).getAbsolutePath());
  }

  private static List<JsonTypeError> describeJsonTypeErrors(LX lx) {
    List<JsonTypeError> errors = new ArrayList<>();
    for (var error : lx.registry.jsonFixtureErrors) {
      errors.add(new JsonTypeError(error.path, error.type,
          (error.exception == null) ? null : error.exception.toString()));
    }
    return errors;
  }

  /**
   * Resolve a fixture class by simple or full name against {@code lx.registry.fixtures} —
   * the set {@code list_available_fixtures} advertises as {@code classes}.
   *
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} (mapped to {@code
   *     invalid_argument}) for an unknown or ambiguous name
   */
  public static Class<? extends LXFixture> resolveFixtureClass(LX lx, String className) {
    return Resolve.resolveClassName(lx.registry.fixtures, className, Resolve.Failure.TYPE_MISMATCH,
        "Unknown fixture class: " + className + " (see list_available_fixtures)");
  }

  /**
   * Validate {@code fixtureType} against {@code lx.registry.jsonFixtures} before an
   * {@code AddFixture(String)} command — that command instantiates a {@link JsonFixture}
   * unconditionally, even for an unknown type (it fails later, inside the .lxf load, in a
   * way {@code perform()} would swallow), so this must run first.
   *
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} for an unknown {@code .lxf} type
   */
  public static void requireKnownJsonFixtureType(LX lx, String fixtureType) {
    for (var jsonType : lx.registry.jsonFixtures) {
      if (jsonType.type.equals(fixtureType)) {
        return;
      }
    }
    throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
        "Unknown fixture type: " + fixtureType + " (see list_available_fixtures)");
  }

  /**
   * {@code lx.structure.addFixture/moveFixture/removeFixture} throw {@code
   * IllegalStateException} when the structure is in static-model mode ({@code
   * LXStructure.checkStaticModel}) — an exception {@code perform()} would otherwise swallow
   * (clearing the undo/redo stack) and report generically. Every fixture-lifecycle
   * primitive checks this first so the failure surfaces as a plain {@code invalid_argument}.
   */
  private static void requireDynamicStructure(LX lx) {
    if (lx.structure.isStatic.isOn()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "cannot modify fixtures: the structure is in static-model mode");
    }
  }

  /** Clamps a caller-supplied insert index (or {@code fallback} when omitted) into [0, count]. */
  private static int clampInsertIndex(int count, Integer index, int fallback) {
    int target = (index == null) ? fallback : index;
    return Math.max(0, Math.min(target, count));
  }

  /**
   * Add a fixture by registered class, routed through {@code LXCommand.Structure.AddFixture}
   * for undo. {@code label}/{@code params} (registered parameters only — reached by {@code
   * fixture.getParameter(name)}) are applied as direct parameter writes AFTER the add
   * completes, mirroring {@link Views#addView}: there is nothing left for a separate undo
   * entry to cover, so one Cmd-Z removes the fully-configured fixture. If a param name is
   * unknown or a value's type doesn't match, the just-added fixture is rolled back (via
   * {@code lx.command.undo()}) before the typed failure is thrown, so a rejected call leaves
   * the structure exactly as it was. Call on the engine thread.
   *
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH}: static-model structure, unknown
   *     param name, or a param value that doesn't match its parameter's type
   */
  public static FixtureInfo addFixtureByClass(LX lx, Class<? extends LXFixture> fixtureClass,
      Integer index, String label, Map<String, Object> params) {
    requireDynamicStructure(lx);
    List<LXFixture> fixtures = lx.structure.fixtures;
    int effectiveIndex = clampInsertIndex(fixtures.size(), index, fixtures.size());
    int before = fixtures.size();
    Commands.perform(lx, new LXCommand.Structure.AddFixture(fixtureClass, effectiveIndex));
    if (fixtures.size() != before + 1) {
      throw new IllegalStateException("AddFixture did not add a " + fixtureClass.getName());
    }
    return configureAdded(lx, fixtures.get(effectiveIndex), label, params);
  }

  /**
   * Add a {@link JsonFixture} by {@code .lxf} type, routed through {@code
   * LXCommand.Structure.AddFixture(String)} for undo — that constructor always appends at
   * the end of the fixture list (it has no index parameter), so there is no way to place a
   * {@code .lxf} fixture at a specific position in a single command; callers wanting a
   * non-append position must add it here, then reposition with {@code move_fixture}. {@code
   * label}/{@code params} apply the same as {@link #addFixtureByClass}. Call on the engine
   * thread.
   *
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH}: static-model structure, unknown
   *     {@code .lxf} type, unknown param name, or a param value type mismatch
   */
  public static FixtureInfo addFixtureByType(LX lx, String fixtureType, String label,
      Map<String, Object> params) {
    requireDynamicStructure(lx);
    requireKnownJsonFixtureType(lx, fixtureType);
    List<LXFixture> fixtures = lx.structure.fixtures;
    int before = fixtures.size();
    Commands.perform(lx, new LXCommand.Structure.AddFixture(fixtureType));
    if (fixtures.size() != before + 1) {
      throw new IllegalStateException("AddFixture did not add fixture type " + fixtureType);
    }
    LXFixture fixture = fixtures.get(before);
    return configureAdded(lx, fixture, label, params);
  }

  private static FixtureInfo configureAdded(
      LX lx, LXFixture fixture, String label, Map<String, Object> params) {
    try {
      if (label != null) {
        fixture.label.setValue(label);
      }
      if (params != null && !params.isEmpty()) {
        applyRegisteredParamsDirectly(fixture, params);
      }
    } catch (RuntimeException e) {
      lx.command.undo();
      throw e;
    }
    return describeFixture(fixture);
  }

  /**
   * Write several of {@code fixture}'s REGISTERED parameters directly (no {@code LXCommand}
   * — the caller, {@link #configureAdded}, folds this into the surrounding add's single undo
   * entry). Unlike {@link #setParams}, this never reaches a {@code JsonFixture}'s
   * {@code .lxf}-declared parameters (those only matter once the fixture is configured after
   * the fact, via {@code set_fixture_params}) and never batches into {@code
   * ArrangeFixtures} (there is no separate undo entry to batch for). Validation is atomic:
   * every name is resolved and every value coerced before anything is written.
   */
  private static void applyRegisteredParamsDirectly(LXFixture fixture, Map<String, Object> params) {
    List<Parameters.Coerced> edits = new ArrayList<>();
    for (Map.Entry<String, Object> entry : params.entrySet()) {
      String name = entry.getKey();
      LXParameter parameter = fixture.getParameter(name);
      if (parameter == null) {
        throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
            "No parameter named '" + name + "' on " + Resolve.canonicalPath(fixture)
                + " (checked registered parameters only — set_fixture_params reaches "
                + ".lxf-declared parameters too)");
      }
      edits.add(Parameters.classify(parameter, entry.getValue()));
    }
    for (Parameters.Coerced edit : edits) {
      switch (edit.kind()) {
        case STRING -> ((StringParameter) edit.parameter()).setValue((String) edit.value());
        case BOOLEAN -> ((BooleanParameter) edit.parameter()).setValue((Boolean) edit.value());
        case DISCRETE -> ((DiscreteParameter) edit.parameter()).setValue((Integer) edit.value());
        case NUMERIC -> edit.parameter().setValue((Double) edit.value());
      }
    }
  }

  /**
   * Remove a fixture, routed through {@code LXCommand.Structure.RemoveFixture} for undo.
   * Call on the engine thread.
   *
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} for a static-model structure
   */
  public static void removeFixture(LX lx, LXFixture fixture) {
    requireDynamicStructure(lx);
    List<LXFixture> fixtures = lx.structure.fixtures;
    int before = fixtures.size();
    Commands.perform(lx, new LXCommand.Structure.RemoveFixture(fixture));
    if (fixtures.size() != before - 1 || fixtures.contains(fixture)) {
      throw new IllegalStateException("RemoveFixture did not remove the fixture");
    }
  }

  /**
   * Reposition a fixture within {@code lx.structure.fixtures}, routed through {@code
   * LXCommand.Structure.MoveFixture} for undo. {@code index} is clamped into the valid
   * [0, count-1] range — {@code LXStructure.moveFixture} has no such guard and would throw
   * an unchecked {@code IndexOutOfBoundsException} that {@code perform()} would swallow.
   * Call on the engine thread.
   *
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} for a static-model structure
   */
  public static FixtureInfo moveFixture(LX lx, LXFixture fixture, int index) {
    requireDynamicStructure(lx);
    int count = lx.structure.fixtures.size();
    int target = Math.max(0, Math.min(index, count - 1));
    Commands.perform(lx, new LXCommand.Structure.MoveFixture(fixture, target));
    if (fixture.getIndex() != target) {
      throw new IllegalStateException("MoveFixture did not move the fixture to " + target);
    }
    return describeFixture(fixture);
  }

  /**
   * Clone {@code source} into a new fixture, routed through {@code
   * LXCommand.Structure.AddFixture(Class, JsonObject, int)} for undo — the UI's clipboard
   * path, minus the clipboard itself. Serialized with {@code stripIds=true}: {@code
   * LXComponent.load} re-registers any id present in the JSON, and the source fixture's id
   * is still live, so a same-id clone would collide (or, mid-project-load, silently remap)
   * — a fresh id is what every other add primitive here produces anyway. {@code
   * source.enabled} (output-enabled) is stripped so a duplicate never starts transmitting
   * silently, matching the UI's own duplicate-fixture behavior. {@code index} defaults to
   * right after {@code source} (matching the UI); explicit indices are clamped into
   * [0, count]. Call on the engine thread.
   *
   * @throws Resolve.ResolveException {@code TYPE_MISMATCH} for a static-model structure
   */
  public static FixtureInfo duplicateFixture(LX lx, LXFixture source, Integer index) {
    requireDynamicStructure(lx);
    JsonObject obj = LXSerializable.Utils.toObject(lx, source, true);
    LXSerializable.Utils.stripParameter(obj, source.enabled);

    List<LXFixture> fixtures = lx.structure.fixtures;
    int effectiveIndex = clampInsertIndex(fixtures.size(), index, source.getIndex() + 1);
    int before = fixtures.size();
    Commands.perform(lx,
        new LXCommand.Structure.AddFixture(source.getClass(), obj, effectiveIndex));
    if (fixtures.size() != before + 1) {
      throw new IllegalStateException("AddFixture did not duplicate the fixture");
    }
    return describeFixture(fixtures.get(effectiveIndex));
  }

  private static String typeOf(LXFixture fixture) {
    if (fixture instanceof JsonFixture jsonFixture) {
      String fixturePath = jsonFixture.getFixturePath();
      return fixturePath.isEmpty() ? fixture.getClass().getSimpleName() : fixturePath;
    }
    return fixture.getClass().getSimpleName();
  }

  private static OutputInfo describeOutput(LXProtocolFixture fixture) {
    return new OutputInfo(
        fixture.protocol.getEnum().name(),
        fixture.host.getString(),
        protocolPort(fixture),
        protocolUniverse(fixture),
        protocolChannel(fixture),
        fixture.byteOrder.getEnum().name(),
        fixture.reverse.isOn());
  }

  private static JsonInfo describeJson(JsonFixture fixture) {
    return new JsonInfo(
        fixture.getFixturePath(),
        fixture.error.isOn(),
        fixture.errorMessage.getString(),
        List.copyOf(fixture.warnings));
  }

  /**
   * The knobs a {@code JsonFixture}'s {@code .lxf} file declares via its
   * {@code "parameters"} block. Unlike a registered fixture parameter, these are built as
   * bare {@code new StringParameter(...)}/etc. and never passed through {@code
   * addParameter} (structure/JsonFixture.java:401-500), so they have <strong>no canonical
   * path</strong> — {@code Resolve} cannot reach them, and {@code getCanonicalPath()} on
   * one directly yields a bogus {@code "/null"}. They are addressed by {@code name} only,
   * via {@code set_fixture_params}, never {@code set_parameter}. Call on the engine
   * thread.
   */
  public static List<JsonParameterInfo> jsonParameters(JsonFixture fixture) {
    List<JsonParameterInfo> parameters = new ArrayList<>();
    for (JsonFixture.ParameterDefinition definition : fixture.getJsonParameters()) {
      Object value = switch (definition.type) {
        case STRING -> definition.stringParameter.getString();
        case STRING_SELECT -> definition.stringSelectParameter.getObject();
        case INT -> definition.intParameter.getValuei();
        case FLOAT -> definition.floatParameter.getValue();
        case BOOLEAN -> definition.booleanParameter.isOn();
      };
      parameters.add(new JsonParameterInfo(
          definition.name, definition.label, definition.type.name(), value));
    }
    return parameters;
  }

  // LXProtocolFixture.getProtocolPort/Universe/Channel/Priority() and
  // resolveHostAddress() carry exactly this per-protocol mapping, but are `protected` —
  // unreachable from outside heronarts.lx.structure. Replicated here against the public
  // parameters only (LXProtocolFixture.java:143-217 mirrors this logic).

  private static int protocolPort(LXProtocolFixture fixture) {
    LXFixture.Protocol protocol = fixture.protocol.getEnum();
    return switch (protocol) {
      case OPC -> fixture.port.getValuei();
      default -> protocol.defaultPort;
    };
  }

  private static int protocolUniverse(LXProtocolFixture fixture) {
    return switch (fixture.protocol.getEnum()) {
      case ARTNET, SACN -> fixture.artNetUniverse.getValuei();
      case DDP -> fixture.ddpDataOffset.getValuei();
      case KINET -> fixture.kinetPort.getValuei();
      case OPC -> fixture.opcChannel.getValuei();
      default -> 0;
    };
  }

  private static int protocolChannel(LXProtocolFixture fixture) {
    return switch (fixture.protocol.getEnum()) {
      case ARTNET, SACN, KINET -> fixture.dmxChannel.getValuei();
      case OPC -> fixture.opcOffset.getValuei();
      default -> 0;
    };
  }
}
