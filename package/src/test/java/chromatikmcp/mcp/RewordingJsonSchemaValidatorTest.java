package chromatikmcp.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson3.DefaultJsonSchemaValidator;

/**
 * Unit tests against the SDK's real default validator ({@code DefaultJsonSchemaValidator}
 * has a public no-arg constructor, so it's usable standalone without the SDK's
 * ServiceLoader/thread-context-classloader machinery {@link EmbeddedMcpServer} otherwise
 * has to work around).
 */
class RewordingJsonSchemaValidatorTest {

  private static final Map<String, Object> CLOSED_PROPERTIES_SCHEMA = Map.of(
      "type", "object",
      "properties", Map.of("scope", Map.of("type", "string")),
      "additionalProperties", false);

  private static final Map<String, Object> OPEN_PROPERTIES_SCHEMA = Map.of(
      "type", "object",
      "properties", Map.of("scope", Map.of("type", "string"))
      // additionalProperties is missing (defaults to true), so the schema is open
  );

  @Test
  void rewordsOutputSchemaWordingToNameTheInputSchema() {
    JsonSchemaValidator wrapped = new RewordingJsonSchemaValidator(new DefaultJsonSchemaValidator());

    var response = wrapped.validate(CLOSED_PROPERTIES_SCHEMA, Map.of("path", "/lx/mixer"));

    assertFalse(response.valid());
    assertFalse(response.errorMessage().contains("outputSchema"),
        "rewritten message must not mention outputSchema: " + response.errorMessage());
    assertTrue(response.errorMessage().contains("arguments do not match the tool's input schema"),
        response.errorMessage());
    assertTrue(response.errorMessage().contains("'path'"),
        "the underlying validation error (naming the offending property) is preserved: "
            + response.errorMessage());
  }

  @Test
  void rewordedMessageListsTheSchemasAcceptedArguments() {
    // Issue #115 asked for a self-correcting message that names what IS accepted, not just
    // what wasn't. The tool name itself isn't available at this seam (the SDK never passes
    // it into errorMessage()), but the input schema's own `properties` keys are.
    JsonSchemaValidator wrapped = new RewordingJsonSchemaValidator(new DefaultJsonSchemaValidator());

    var response = wrapped.validate(CLOSED_PROPERTIES_SCHEMA, Map.of("path", "/lx/mixer"));

    assertFalse(response.valid());
    assertTrue(response.errorMessage().contains("scope"),
        "rewritten message must list the schema's accepted argument names: "
            + response.errorMessage());
    assertTrue(response.errorMessage().contains(". Accepted arguments:"),
        "message must have separating punctuation before Accepted arguments clause: "
            + response.errorMessage());
  }

  @Test
  void doesNotTouchAPassingValidation() {
    JsonSchemaValidator wrapped = new RewordingJsonSchemaValidator(new DefaultJsonSchemaValidator());

    var response = wrapped.validate(CLOSED_PROPERTIES_SCHEMA, Map.of("scope", "/lx/mixer"));

    assertTrue(response.valid());
  }

  @Test
  void unwrappedDefaultValidatorStillProducesTheOutputSchemaWordingThisWrapperFixes() {
    // Non-vacuity: confirms the wrapper is doing something — the raw SDK behavior this
    // class exists to fix is still reproducible directly against the delegate. Asserts
    // the exact literal production's replace() matches on (not just a loose substring
    // like "outputSchema"), so an SDK wording change fails this test loudly and points
    // straight at the constant that needs updating, instead of leaving the reword silently
    // stopped firing.
    JsonSchemaValidator raw = new DefaultJsonSchemaValidator();

    var response = raw.validate(CLOSED_PROPERTIES_SCHEMA, Map.of("path", "/lx/mixer"));

    assertFalse(response.valid());
    assertTrue(
        response.errorMessage().contains("structuredContent does not match tool outputSchema"),
        "raw SDK wording (pre-fix) must contain the exact phrase production matches on: "
            + response.errorMessage());
  }

  @Test
  void passesThroughUnrelatedFailureMessagesUnchanged() {
    JsonSchemaValidator wrapped = new RewordingJsonSchemaValidator(new DefaultJsonSchemaValidator());

    // Malformed JSON text as structuredContent fails during parsing (a distinct message
    // prefix, "Error parsing tool JSON Schema: ") rather than the "does not match tool
    // outputSchema" path — that message has nothing to do with the wording this wrapper
    // fixes and must pass through verbatim.
    var response = wrapped.validate(CLOSED_PROPERTIES_SCHEMA, "{not valid json");

    assertFalse(response.valid());
    assertFalse(response.errorMessage().contains("outputSchema"));
    assertFalse(response.errorMessage().contains("input schema"),
        "an unrelated failure must not be rewritten: " + response.errorMessage());
  }

  @Test
  void validateSchemaDelegatesUnchanged() {
    JsonSchemaValidator wrapped = new RewordingJsonSchemaValidator(new DefaultJsonSchemaValidator());

    var response = wrapped.validateSchema(CLOSED_PROPERTIES_SCHEMA);

    assertTrue(response.valid());
  }

  @Test
  void doesNotListAcceptedArgumentsWhenSchemaIsOpen() {
    // A schema without additionalProperties: false allows extra properties, so naming
    // a closed set of "accepted arguments" is inaccurate. The clause is omitted even
    // if the schema has a properties map, the same way it degrades when properties is
    // missing. Invalid 'scope' type triggers validation error.
    JsonSchemaValidator wrapped = new RewordingJsonSchemaValidator(new DefaultJsonSchemaValidator());

    var response = wrapped.validate(OPEN_PROPERTIES_SCHEMA, Map.of("scope", 123));

    assertFalse(response.valid());
    assertFalse(response.errorMessage().contains("Accepted arguments"),
        "open schema must not list accepted arguments, as the schema allows extra properties: "
            + response.errorMessage());
  }
}
