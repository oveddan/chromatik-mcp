package chromatikmcp.domain;

import java.util.List;

/**
 * A loaded Agent Skill (agentskills.io): a directory whose root {@code SKILL.md} carries
 * {@code name}/{@code description} YAML frontmatter, plus whatever supporting files it
 * ships. {@link #files()} always contains a {@code "SKILL.md"} entry first, followed by
 * every other file in a stable order — {@link SkillLoader} guarantees both, so nothing
 * downstream (e.g. {@code chromatikmcp.mcp.SkillResources}) needs to re-check.
 *
 * <p>{@code name} and {@code description} are copied out of the frontmatter rather than
 * re-parsed from {@code files().get(0)} by every reader, since {@link SkillLoader} already
 * verified they're present and that {@code name} matches the directory it came from.
 */
public record Skill(String name, String description, List<SkillFile> files) {}
