# Publishing (maintainer notes)

Published to Maven Central under the `dev.noahtownsend` namespace via the
[Central Portal](https://central.sonatype.com), using
[`com.vanniktech.maven.publish`](https://github.com/vanniktech/gradle-maven-publish-plugin).

## One-time setup (only the namespace owner can do this)

1. Create a Sonatype Central Portal account.
2. Verify the `dev.noahtownsend` namespace via domain verification — add the DNS TXT record
   Central Portal gives you to `noahtownsend.dev`'s DNS.
3. Generate a GPG key pair; publish the public key to a keyserver; ASCII-armor the private
   key for the `SIGNING_KEY` secret below.
4. Add these secrets to this repo's Settings → Secrets and variables → Actions:
   - `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` — Central Portal token (not the
     legacy OSSRH user/pass).
   - `SIGNING_KEY` / `SIGNING_PASSWORD` — GPG key from step 3.
5. Cut a `vX.Y.Z` release tag. `.github/workflows/publish.yml` runs
   `./gradlew publishAndReleaseToMavenCentral`.

## Using this library before it's published

While the first Maven Central release is pending, consume this repo via a Gradle composite
build:

```kotlin
// settings.gradle.kts of the consuming project
includeBuild("../LinkedLicense")
```

Switch to the real coordinate (`dev.noahtownsend:linkedlicense:<version>`) once a release is
confirmed live, and drop the composite build.
