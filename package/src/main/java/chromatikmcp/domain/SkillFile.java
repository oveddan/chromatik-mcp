package chromatikmcp.domain;

/**
 * One file within a {@link Skill}, addressed relative to the skill's own directory root
 * ({@code "SKILL.md"}, {@code "references/addressing.md"}, ...) using {@code /} as the
 * separator regardless of host OS — the same convention SEP-2640's {@code skill://} URIs
 * use, so a {@link #relativePath()} concatenates directly onto a skill path.
 */
public record SkillFile(String relativePath, String text) {

  /** The filename every skill's required root file has, per the Agent Skills specification. */
  public static final String SKILL_MD = "SKILL.md";
}
