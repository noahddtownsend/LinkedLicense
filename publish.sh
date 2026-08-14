#!/usr/bin/env bash
# Reads secrets from local.properties (gitignored) and exports them as the real environment
# variables Gradle actually reads at process-start time, then runs Gradle with those set.
#
# This exists because Gradle snapshots its project-properties source before settings.gradle.kts
# even runs, so there is no reliable way to bridge local.properties -> Gradle properties from
# within a build script for the *current* invocation - only real env vars, set before the JVM
# starts, work. Usage:
#   ./publish.sh :linkedlicense-plugin:publishToMavenCentral
#   ./publish.sh publishToMavenCentral   (root module)
#   ./publish.sh :linkedlicense-plugin:publishPlugins   (Gradle Plugin Portal)

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [ ! -f local.properties ]; then
    echo "local.properties not found - nothing to load." >&2
    exit 1
fi

get_prop() {
    # Reads a simple "key=value" line from local.properties (no multi-line/escaped values).
    sed -n "s/^$1=//p" local.properties | tail -1
}

MAVEN_CENTRAL_USERNAME="$(get_prop mavenCentralUsername)"
MAVEN_CENTRAL_PASSWORD="$(get_prop mavenCentralPassword)"
SIGNING_KEY_PASSWORD="$(get_prop signingInMemoryKeyPassword)"
SIGNING_KEY_ID="$(get_prop signingInMemoryKeyId)"
SIGNING_KEY_FILE_REL="$(get_prop signingInMemoryKeyFile)"
GRADLE_PUBLISH_KEY_VALUE="$(get_prop gradle.publish.key)"
GRADLE_PUBLISH_SECRET_VALUE="$(get_prop gradle.publish.secret)"

export ORG_GRADLE_PROJECT_mavenCentralUsername="$MAVEN_CENTRAL_USERNAME"
export ORG_GRADLE_PROJECT_mavenCentralPassword="$MAVEN_CENTRAL_PASSWORD"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$SIGNING_KEY_PASSWORD"
export ORG_GRADLE_PROJECT_signingInMemoryKeyId="$SIGNING_KEY_ID"

if [ -n "$SIGNING_KEY_FILE_REL" ] && [ -f "$SIGNING_KEY_FILE_REL" ]; then
    export ORG_GRADLE_PROJECT_signingInMemoryKey
    ORG_GRADLE_PROJECT_signingInMemoryKey="$(cat "$SIGNING_KEY_FILE_REL")"
fi

if [ -n "$GRADLE_PUBLISH_KEY_VALUE" ]; then
    export GRADLE_PUBLISH_KEY="$GRADLE_PUBLISH_KEY_VALUE"
fi
if [ -n "$GRADLE_PUBLISH_SECRET_VALUE" ]; then
    export GRADLE_PUBLISH_SECRET="$GRADLE_PUBLISH_SECRET_VALUE"
fi

exec gradle "$@"
