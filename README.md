# LinkedLicense

A Kotlin Multiplatform library of open-source license templates, plus an opt-in Gradle
plugin that scans your project's dependency graph and generates the license catalog for
you — so you never have to hand-type another `NOTICE` entry.

- **Targets**: Android, iOS (x64 / arm64 / simulator arm64), JVM, JS, Wasm.
- **License (of this library's own code)**: MIT. See [`LICENSE`](./LICENSE).
- **Group / artifact**: `dev.noahtownsend:linkedlicense`.

This README is the contract for both halves of the library — the runtime `License` API and
the build-time scanning plugin — written before either was implemented.

## 1. The `License` API (runtime)

`dev.noahtownsend.linkedlicense.License` is an `abstract class`. A closed, built-in set of
subtypes covers the common licenses; you may also subclass it yourself (see
[§4 Custom licenses in code](#4-custom-licenses-in-code)).

Every `License` exposes:

```kotlin
abstract class License(
    open val elementLicensed: String,
    open val author: String,
    open val url: String? = null,
) {
    abstract val licenseText: String
    open val isCopyleft: Boolean get() = false
}
```

### Built-in types

| Type | Constructor params (beyond `elementLicensed`/`author`/`url`) | `isCopyleft` |
|---|---|---|
| `License.MIT` | `year: String` | `false` |
| `License.Apache1_1` | — | `false` |
| `License.Apache2` | — | `false` |
| `License.Bsd2Clause` | `year: String` | `false` |
| `License.Bsd3Clause` | `year: String` | `false` |
| `License.Isc` | `year: String` | `false` |
| `License.Gpl2` | — | `true` |
| `License.Gpl3` | — | `true` |
| `License.Lgpl2_1` | — | `true` |
| `License.Lgpl3` | — | `true` |
| `License.Mpl2` | — | `true` |
| `License.Ofl` | — | `false` |
| `License.Unlicense` | — | `false` |
| `License.Cc0` | — | `false` |
| `License.CreativeCommons` | `variant: CcVariant`, `version: String` | `false`* |
| `License.OpenGovernmentLicence` | `jurisdiction: String`, `version: String` | `false` |
| `License.PublicDomain` | — | `false` |
| `License.UsGovernmentPublicDomain` | — | `false` |
| `License.Mapbox` | defaults provided | `false` |
| `License.Custom` | `text: String` | `false` (escape hatch for anything not listed above) |

\* `CreativeCommons` variants with `NC`/`ND`/`SA` terms carry redistribution *conditions*,
but none require releasing your own source code the way GPL-family licenses do, so they're
not classified as copyleft for the purposes of [§3.5](#35-copyleft-guard-on-by-default).

`CcVariant` is `BY | BY_SA | BY_ND | BY_NC | BY_NC_SA | BY_NC_ND`.

`isCopyleft` is `true` for `Gpl2`, `Gpl3`, `Lgpl2_1`, `Lgpl3`, `Mpl2` — anything with
reciprocal/share-alike source-disclosure obligations. LGPL and MPL are *weak* copyleft
(obligations apply per-file/per-library, not to your whole program) versus GPL's *strong*
copyleft (obligations apply to the whole combined work); both default to failing the build
under the copyleft guard below, since either can require you to release source you didn't
intend to.

### Example

```kotlin
val licenses = listOf(
    License.MIT(elementLicensed = "Kotlin", author = "JetBrains", year = "2011"),
    License.Apache2(elementLicensed = "Ktor", author = "JetBrains"),
    License.CreativeCommons(
        variant = CcVariant.BY_SA,
        version = "4.0",
        elementLicensed = "Wikimedia Commons Media",
        author = "Wikimedia Contributors",
    ),
)
```

## 2. The Gradle plugin (build-time, opt-in)

Applying the plugin does **not** happen automatically by depending on the library — it's a
separate, explicit step:

```kotlin
// build.gradle.kts
plugins {
    id("dev.noahtownsend.linkedlicense") version "<version>"
}
```

This registers one task per Kotlin source set: **`generateLicenseCatalog`** (e.g.
`generateCommonMainLicenseCatalog`).

### 2.1 What the task does

1. Resolves the source set's runtime/compile classpath **transitively** — the full
   dependency graph, not just what you declared directly. License and notice obligations
   attach to everything you ship, including indirect dependencies.
2. **Dedupes by resolved coordinate** (`group:artifact:version`). A coordinate reached by
   multiple paths in the graph (shared transitive deps, diamond dependencies, or a
   transitive dep that's also one of your direct dependencies) is processed exactly once
   and appears exactly once in the output.
3. For each unique coordinate, fetches its POM and reads `<licenses><license>` entries.
4. Matches the license name/URL against a table of common SPDX identifiers and known name
   variants (e.g. `"The Apache Software License, Version 2.0"` → `Apache2`).
5. Anything that can't be matched, and has no override (§3), **fails the build** — see
   §3.3. Anything copyleft, and not allow-listed, also fails the build by default — see
   §3.5.
6. Copies required notices into `THIRD-PARTY-NOTICES` — see §3.4.
7. Once every dependency is resolved (matched, overridden, or ignored) and no failure was
   triggered, generates `GeneratedLicenses.kt` (an object exposing `all: List<License>`)
   into `build/generated/linkedlicense/<sourceSet>/`, registered as an extra Kotlin source
   directory so it compiles like ordinary code:

   ```kotlin
   // build/generated/linkedlicense/commonMain/.../GeneratedLicenses.kt
   object GeneratedLicenses {
       val all: List<License> = listOf(/* ... */)
   }
   ```

   Merge it with your own hand-curated entries as needed:

   ```kotlin
   val allLicenses = GeneratedLicenses.all + listOf(License.Mapbox())
   ```

### 2.2 Why this is opt-in and per-project

The plugin only runs for a project that explicitly applies it, and only ever emits entries
for what's actually resolved in *that* project's own dependency graph. Depending on
`dev.noahtownsend:linkedlicense` alone — without applying the plugin — pulls in only the
`License` types; nothing is scanned, generated, or bundled automatically, and no consumer
is ever forced to ship or display license text for dependencies they don't have.

## 3. Configuring the plugin

```kotlin
linkedLicense {
    overridesFile = file("linkedlicense.toml") // default shown
    copyRequiredNotices = true                 // default shown
    failOnCopyleft = true                      // default shown
}
```

### 3.1 The override file (`linkedlicense.toml`)

Overrides live in their own version-catalog-shaped TOML file, not inline in
`build.gradle.kts` — reviewable and diffable in PRs the same way `libs.versions.toml`
already is.

```toml
[overrides]
"com.mapbox.maps:android" = { license = "Custom", elementLicensed = "Mapbox Maps", author = "Mapbox", text = "..." }
"libs.okio" = { license = "Apache2" } # resolved via the version catalog, see 3.2

[ignored]
"com.example:internal-tool" = "Vendored fork, not redistributed; excluded from the catalog."

[copyleft-allowed]
"org.gnu:some-lib" = "Used only at build time, never linked into a shipped artifact."

[license-policy]
allow = ["MIT", "Apache2", "Bsd2Clause", "Bsd3Clause", "Isc"]
block = ["Gpl3"]
```

- **`[overrides]`** pins an exact coordinate to a specific built-in `License` type (or a
  [custom one](#4-custom-licenses-in-code), via a `custom:` reference) — for POMs with no
  `<licenses>` block, an ambiguous/wrong block, or dependencies that aren't Maven artifacts
  at all (vendor terms, government open-data licenses, bundled fonts).
- **`[ignored]`** excludes a coordinate from the catalog entirely. A reason string is
  required — it's an audit trail, not just a switch.
- **`[copyleft-allowed]`** allow-lists a coordinate past the copyleft guard (§3.5). A reason
  string is required.
- **`[license-policy]`** — see §3.6, a *license-type*-level allow/block list (as opposed to
  the coordinate-level tables above).
- `[overrides]`/`[ignored]` are checked *before* auto-matching. Anything neither
  auto-matched nor covered by one of these three tables fails the build.

### 3.2 Version-catalog addressing

A coordinate key in any of the three tables may be either a raw `group:artifact` string, or
a `libs.*`-style alias resolved against your own `libs.versions.toml` via Gradle's
`VersionCatalogsExtension` (e.g. `"libs.koin"`). Using the alias means the override stays
correct automatically if the underlying coordinate changes but the catalog alias doesn't,
instead of silently going stale.

### 3.3 Fail-on-unknown (always on)

A dependency that can't be auto-matched and has no `[overrides]`/`[ignored]` entry fails
`generateLicenseCatalog`. The task collects *every* unresolved coordinate before failing, so
you fix them all in one pass instead of one build cycle at a time. There is no setting to
disable this outright — unlicensed/unknown dependencies should never silently ship. If you
need to bypass it for one specific build (not recommended), skip the task directly:
`./gradlew build -x generateLicenseCatalog`.

### 3.4 Required-notice copying (on by default)

Some licenses obligate you to carry forward specific notice text on redistribution, beyond
just reproducing the license body — Apache 2.0's `NOTICE` file clause is the common case
(also e.g. BSD-3-Clause's non-endorsement notice, MPL's file-level notices). For every
resolved dependency that ships a `NOTICE`/`NOTICE.txt` file in its artifact, that file's
contents are copied into a generated `THIRD-PARTY-NOTICES` file at your project root.

Set `copyRequiredNotices = false` to opt out. This is independent of the copyleft guard
below — most Apache-licensed (permissive) dependencies carry a NOTICE obligation without
being copyleft.

### 3.5 Copyleft guard (on by default)

When a matched or overridden dependency resolves to a license with `isCopyleft == true`
(GPL2/GPL3/LGPL2.1/LGPL3/MPL2 among the built-ins), `generateLicenseCatalog` fails the build
— same aggregated-list-of-offenders style as §3.3 — unless that coordinate has a
`[copyleft-allowed]` entry, or you set `failOnCopyleft = false` project-wide.

### 3.6 License allow/block list (opt-in, empty by default)

`[license-policy]` restricts which *license types* (not individual coordinates) are
acceptable at all, independent of the copyleft guard:

```toml
[license-policy]
allow = ["MIT", "Apache2"]   # optional
block = ["Gpl3", "AGPL3"]    # optional
```

- **If `allow` is non-empty**, only dependencies whose resolved license type is in `allow`
  pass; everything else fails the build, regardless of `block`.
- **If `allow` is empty or absent** (the default), every license type passes *except* those
  listed in `block` — i.e. with no allow list defined, everything not on the blocklist is
  allowed.
- Entries reference license types by the same identifier used in `[overrides]`'s `license =
  "..."` field (a built-in type name like `"MIT"`/`"Gpl3"`, or a `custom:...` symbol
  reference for your own subclasses).
- Violations are collected and reported the same aggregated way as §3.3/§3.5 — every
  offending coordinate and its license type in one failure, not one per build.
- This runs in addition to, not instead of, the copyleft guard (§3.5): a GPL dependency
  still needs a `[copyleft-allowed]` entry (or `failOnCopyleft = false`) even if you've also
  blocked/allowed it here, and vice versa. The two checks exist for different reasons —
  copyleft is a legal-risk default; `[license-policy]` is your own project's explicit
  policy — so both apply independently.

## 4. Custom licenses in code

`License` is `abstract`, not `sealed` — your own codebase can declare real subclasses (with
their own `licenseText` logic, not just static text) for anything `License.Custom`'s
data-holding shape doesn't fit: a company-internal license, or a template with logic beyond
simple placeholder substitution.

```kotlin
object MyCompanyLicense : License(
    elementLicensed = "Acme Internal SDK",
    author = "Acme Corp",
) {
    override val licenseText: String get() = "..."
}
```

Reference it from `linkedlicense.toml` by fully-qualified symbol name, prefixed `custom:`:

```toml
[overrides]
"com.acme:internal-sdk" = { license = "custom:com.acme.licenses.MyCompanyLicense" }
```

`generateLicenseCatalog` emits a direct reference/import to that symbol in
`GeneratedLicenses.kt` rather than trying to instantiate anything itself — your compiled
code is the source of truth for what it does.

## 5. Publishing this library (maintainer notes)

Published to Maven Central under the `dev.noahtownsend` namespace via the
[Central Portal](https://central.sonatype.com), using
[`com.vanniktech.maven.publish`](https://github.com/vanniktech/gradle-maven-publish-plugin).

One-time setup (only the namespace owner can do this):

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

## 6. Using this library before it's published

While the first Maven Central release is pending, consume this repo via a Gradle composite
build:

```kotlin
// settings.gradle.kts of the consuming project
includeBuild("../LinkedLicense")
```

Switch to the real coordinate (`dev.noahtownsend:linkedlicense:<version>`) once a release is
confirmed live, and drop the composite build.
