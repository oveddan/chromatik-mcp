package chromatikmcp.domain;

/**
 * A component's canonical-path change caused by a move. Keyed by stable component id,
 * never by list position, since position is exactly what a move changes.
 */
public record PathChange(int componentId, String before, String after) {}
