package chromatikmcp.mcp;

import java.util.Map;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;

/**
 * Wraps the SDK's default {@link JsonSchemaValidator}, rewording its one hardcoded
 * failure message so it names the schema actually being checked (issue #115).
 *
 * <p>{@code DefaultJsonSchemaValidator.validate(...)} (SDK artifact {@code
 * mcp-json-jackson3:2.0.0-RC1}) hardcodes {@code "Validation failed: structuredContent
 * does not match tool outputSchema. Validation errors: ..."} for every failure it
 * reports — the same {@code validate()} method backs both the SDK's real output-schema
 * check and {@code ToolInputValidator}'s check of a tool call's arguments against its
 * declared {@code inputSchema}. Since no tool in this codebase declares an {@code
 * outputSchema} (see {@code docs/tool-conventions.md}), every failure this validator can
 * produce here is an input-argument failure — an unrecognized or missing argument comes
 * back blaming "outputSchema" and "structuredContent", steering an agent into debugging
 * the wrong side of the call.
 *
 * <p>This wrapper delegates all real validation to the SDK's default instance and only
 * rewrites that one hardcoded phrase, preserving the underlying validation errors
 * verbatim. It is a stopgap for the RC's wording, not a reimplementation of validation —
 * delete it at the SDK GA bump (tracked as a follow-up in {@code docs/build-plan.md},
 * targeted for PR-6) once upstream fixes the wording or gives input/output failures
 * distinct messages.
 */
public final class RewordingJsonSchemaValidator implements JsonSchemaValidator {

  private static final String OUTPUT_SCHEMA_WORDING =
      "structuredContent does not match tool outputSchema";
  private static final String INPUT_SCHEMA_WORDING =
      "arguments do not match the tool's input schema";

  private final JsonSchemaValidator delegate;

  public RewordingJsonSchemaValidator(JsonSchemaValidator delegate) {
    this.delegate = delegate;
  }

  @Override
  public ValidationResponse validate(Map<String, Object> schema, Object structuredContent) {
    ValidationResponse response = this.delegate.validate(schema, structuredContent);
    if (response.valid() || response.errorMessage() == null
        || !response.errorMessage().contains(OUTPUT_SCHEMA_WORDING)) {
      return response;
    }
    String reworded =
        response.errorMessage().replace(OUTPUT_SCHEMA_WORDING, INPUT_SCHEMA_WORDING);
    return ValidationResponse.asInvalid(reworded + acceptedArgumentsClause(schema));
  }

  /**
   * The SDK never surfaces the failing tool's name to {@code errorMessage()} (it's passed
   * only to the SDK's own SLF4J logger), so this can't say "for tool X" — but the input
   * {@code schema} passed to {@code validate()} is the tool's own {@code inputSchema}, so
   * its {@code properties} keys are the accepted argument names. Only emits the clause when
   * the schema closes the set ({@code additionalProperties} present and {@code false}),
   * the same way the missing-{@code properties} case already degrades. Degrades to an empty
   * clause (rather than throwing) if the schema is malformed, so validation itself never
   * fails.
   */
  private static String acceptedArgumentsClause(Map<String, Object> schema) {
    Object additionalProperties = schema.get("additionalProperties");
    if (!(additionalProperties instanceof Boolean) || (Boolean) additionalProperties) {
      return "";
    }

    Object properties = schema.get("properties");
    if (!(properties instanceof Map<?, ?> propertiesMap) || propertiesMap.isEmpty()) {
      return "";
    }
    StringBuilder names = new StringBuilder();
    for (Object key : propertiesMap.keySet()) {
      if (names.length() > 0) {
        names.append(", ");
      }
      names.append(key);
    }
    return ". Accepted arguments: " + names + ".";
  }

  @Override
  public ValidationResponse validateSchema(Map<String, Object> schema) {
    return this.delegate.validateSchema(schema);
  }
}
