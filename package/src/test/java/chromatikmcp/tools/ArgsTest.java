package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.domain.Resolve.Failure;
import chromatikmcp.domain.Resolve.ResolveException;

/**
 * Unit coverage for the shared required/optional argument parsing every tool handler's
 * {@code handle(...)} calls into. The exact message strings asserted here are the
 * user-visible wire text (see {@link Tools} mapping {@link ResolveException} to
 * {@code invalid_argument}), so a wording change here is a wire-shape change.
 */
class ArgsTest {

  @Test
  void requireStringMissingKeyThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.requireString(Map.of(), "path"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
    assertEquals("Required string argument: path", e.getMessage());
  }

  @Test
  void requireStringNonStringThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.requireString(Map.of("path", 42), "path"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
    assertEquals("Required string argument: path", e.getMessage());
  }

  @Test
  void requireStringValidReturnsValue() {
    assertEquals("/lx/mixer", Args.requireString(Map.of("path", "/lx/mixer"), "path"));
  }

  @Test
  void requireIntMissingKeyThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.requireInt(Map.of(), "index"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
    assertEquals("Required integer argument: index", e.getMessage());
  }

  @Test
  void requireIntNonNumberThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.requireInt(Map.of("index", "1"), "index"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
    assertEquals("Required integer argument: index", e.getMessage());
  }

  @Test
  void requireIntFractionalThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.requireInt(Map.of("index", 1.5), "index"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
    assertEquals("Required integer argument: index", e.getMessage());
  }

  @Test
  void requireIntIntegralDoubleIsAccepted() {
    // JSON has no integer type, so a client sending 2 may arrive as Double 2.0 — this
    // must behave identically to an actual int.
    assertEquals(2, Args.requireInt(Map.of("index", 2.0), "index"));
  }

  @Test
  void requireIntIntIsAccepted() {
    assertEquals(2, Args.requireInt(Map.of("index", 2), "index"));
  }

  @Test
  void requireIntOutOfRangeLongThrows() {
    // A JSON integer literal past Integer.MAX_VALUE arrives as a Long; Long.intValue()
    // narrows by taking the low 32 bits (4294967297L -> 1), not by clamping or failing.
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.requireInt(Map.of("index", 4294967297L), "index"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void requireIntPositiveInfinityThrows() {
    // rint(Infinity) == Infinity, so the integrality check alone doesn't catch this.
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.requireInt(Map.of("index", Double.POSITIVE_INFINITY), "index"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void requireIntNegativeInfinityThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.requireInt(Map.of("index", Double.NEGATIVE_INFINITY), "index"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void requireIntNaNThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.requireInt(Map.of("index", Double.NaN), "index"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void requireIntJustPastIntMaxThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.requireInt(Map.of("index", (long) Integer.MAX_VALUE + 1), "index"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void requireIntIntMaxIsAccepted() {
    assertEquals(Integer.MAX_VALUE, Args.requireInt(Map.of("index", Integer.MAX_VALUE), "index"));
  }

  @Test
  void requireIntIntMinIsAccepted() {
    assertEquals(Integer.MIN_VALUE, Args.requireInt(Map.of("index", Integer.MIN_VALUE), "index"));
  }

  @Test
  void requireIntInRangeLongIsAccepted() {
    assertEquals(42, Args.requireInt(Map.of("index", 42L), "index"));
  }

  @Test
  void optionalIntAbsentReturnsDefault() {
    assertEquals(-1, Args.optionalInt(Map.of(), "index", -1));
  }

  @Test
  void optionalIntNullValueReturnsDefault() {
    Map<String, Object> args = new HashMap<>();
    args.put("index", null);
    assertEquals(-1, Args.optionalInt(args, "index", -1));
  }

  @Test
  void optionalIntNonNumberThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.optionalInt(Map.of("index", "1"), "index", -1));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void optionalIntFractionalThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.optionalInt(Map.of("index", 1.5), "index", -1));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void optionalIntOutOfRangeLongThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.optionalInt(Map.of("index", 4294967297L), "index", -1));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void optionalIntPositiveInfinityThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.optionalInt(Map.of("index", Double.POSITIVE_INFINITY), "index", -1));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void optionalIntNegativeInfinityThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.optionalInt(Map.of("index", Double.NEGATIVE_INFINITY), "index", -1));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void optionalIntNaNThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.optionalInt(Map.of("index", Double.NaN), "index", -1));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void optionalIntJustPastIntMaxThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.optionalInt(Map.of("index", (long) Integer.MAX_VALUE + 1), "index", -1));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void optionalIntIntMaxIsAccepted() {
    assertEquals(Integer.MAX_VALUE,
        Args.optionalInt(Map.of("index", Integer.MAX_VALUE), "index", -1));
  }

  @Test
  void optionalIntIntMinIsAccepted() {
    assertEquals(Integer.MIN_VALUE,
        Args.optionalInt(Map.of("index", Integer.MIN_VALUE), "index", -1));
  }

  @Test
  void optionalIntInRangeLongIsAccepted() {
    assertEquals(42, Args.optionalInt(Map.of("index", 42L), "index", -1));
  }

  @Test
  void optionalIntIntegralDoubleIsAccepted() {
    assertEquals(2, Args.optionalInt(Map.of("index", 2.0), "index", -1));
  }

  @Test
  void optionalStringAbsentReturnsNull() {
    assertNull(Args.optionalString(Map.of(), "label"));
  }

  @Test
  void optionalStringNullValueReturnsNull() {
    Map<String, Object> args = new HashMap<>();
    args.put("label", null);
    assertNull(Args.optionalString(args, "label"));
  }

  @Test
  void optionalStringWrongTypeThrows() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.optionalString(Map.of("label", 42), "label"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
    assertEquals("label must be a string", e.getMessage());
  }

  @Test
  void optionalStringValidReturnsValue() {
    assertEquals("my label", Args.optionalString(Map.of("label", "my label"), "label"));
  }

  @Test
  void optionalStringWithMessageAbsentReturnsNull() {
    assertNull(Args.optionalString(Map.of(), "label", "custom message"));
  }

  @Test
  void optionalStringWithMessageNullValueReturnsNull() {
    Map<String, Object> args = new HashMap<>();
    args.put("label", null);
    assertNull(Args.optionalString(args, "label", "custom message"));
  }

  @Test
  void optionalStringWithMessageWrongTypeThrowsCustomMessage() {
    ResolveException e = assertThrows(ResolveException.class,
        () -> Args.optionalString(Map.of("label", 42), "label", "custom message"));
    assertEquals(Failure.TYPE_MISMATCH, e.failure);
    assertEquals("custom message", e.getMessage());
  }

  @Test
  void optionalStringWithMessageValidReturnsValue() {
    assertEquals("my label",
        Args.optionalString(Map.of("label", "my label"), "label", "custom message"));
  }
}
