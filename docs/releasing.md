# Releasing

Cutting a release is one action: **merge a PR that bumps `<version>` in `package/pom.xml`.**

`.github/workflows/release.yml` runs on every push to `main`. It reads the pom version
and, if `v<version>` isn't already tagged, runs the full gate (`mvn package` +
`scripts/verify-load.sh`), tags the merge commit, and creates the GitHub Release with
both `chromatik-mcp.jar` and `chromatik-mcp-<version>.jar`.

Pushes where the version is unchanged (already tagged) are a no-op, so ordinary merges
cost one cheap version check.

## Version conventions

`main`'s pom always holds a plain release version — `0.1.0`, not `0.1.0-SNAPSHOT`. A
`-SNAPSHOT` suffix is treated as "not a release" and skipped, so it's a way to park an
in-progress version, not the steady state.

The consequence to know: between releases, main builds report the last released version
in `get_status`'s `serverVersion`. Use `buildTime` to tell a rolling build apart from
the release itself.

## The two download URLs

| URL | serves |
|---|---|
| `releases/latest/download/chromatik-mcp.jar` | newest **release** — what the install docs point at |
| `releases/download/latest/chromatik-mcp.jar` | rolling `latest` prerelease, rebuilt on every main push touching `package/` (`publish-jar.yml`) |

The confusable path order is load-bearing. `releases/latest/download/` is GitHub's alias
for the newest non-prerelease; `releases/download/latest/` names a tag that happens to be
called `latest`. The rolling build is marked prerelease so the alias skips it.

## Manual tags

Pushing a `v*` tag by hand still works and is honored as-is, for backfilling or
re-cutting a release. Tag the exact commit rather than relying on `HEAD` — a local `main`
is routinely behind, since merges land via the GitHub API:

```sh
git fetch origin main
git tag v0.1.1 <sha> && git push origin v0.1.1
```

Note that the tag is only created after the gate passes, so a failed release leaves no
tag behind to block a retry.
