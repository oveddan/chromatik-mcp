#!/usr/bin/env bash
# Verify that the compiler baseline can consume every pinned Heron Arts artifact. LX, GLX,
# and GLXStudio do not necessarily share a class-file target even when their versions match.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
POM="$REPO_ROOT/package/pom.xml"
WORK_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/heronarts-bytecode.XXXXXX")"
trap 'rm -rf "$WORK_ROOT"' EXIT

LX_VERSION="$(sed -n 's/.*<lx.version>\([^<]*\)<\/lx.version>.*/\1/p' "$POM")"
COMPILER_RELEASE="$(sed -n \
  's/.*<maven.compiler.release>\([^<]*\)<\/maven.compiler.release>.*/\1/p' "$POM")"
[[ -n "$LX_VERSION" && -n "$COMPILER_RELEASE" ]] || {
  echo "FAIL: could not read LX version and compiler release from $POM" >&2
  exit 1
}

MAVEN_ARGS=(-q -B -f "$POM")
if [[ -n "${MAVEN_REPO_LOCAL:-}" ]]; then
  MAVEN_ARGS+=("-Dmaven.repo.local=$MAVEN_REPO_LOCAL")
fi
mvn "${MAVEN_ARGS[@]}" dependency:copy-dependencies \
  -DincludeGroupIds=com.heronarts \
  -DincludeArtifactIds=lx,glx,glxstudio \
  -DincludeScope=provided \
  -DoutputDirectory="$WORK_ROOT/jars"

artifact_release() {
  local artifact="$1" class_file="$2" jar_file bytes major release
  jar_file="$WORK_ROOT/jars/$artifact-$LX_VERSION.jar"
  [[ -f "$jar_file" ]] || { echo "FAIL: missing published artifact $jar_file" >&2; exit 1; }
  read -r -a bytes <<<"$(unzip -p "$jar_file" "$class_file" | od -An -t u1 -N 8)"
  [[ "${#bytes[@]}" -eq 8 ]] || {
    echo "FAIL: missing representative class $class_file in $artifact-$LX_VERSION.jar" >&2
    exit 1
  }
  major=$((bytes[6] * 256 + bytes[7]))
  release=$((major - 44))
  echo "$artifact-$LX_VERSION.jar: class major $major (Java $release)"
  if (( release > COMPILER_RELEASE )); then
    echo "FAIL: $artifact requires Java $release, compiler release is $COMPILER_RELEASE" >&2
    exit 1
  fi
}

artifact_release lx heronarts/lx/LX.class
artifact_release glx heronarts/glx/ui/UI2dContainer.class
artifact_release glxstudio heronarts/lx/studio/LXStudio.class
echo "OK: all pinned Heron Arts artifacts are compatible with Java $COMPILER_RELEASE"
