---
name: integrate-linkedlicense
description: Integrate LinkedLicense (dev.noahtownsend:linkedlicense) into a Kotlin/Kotlin Multiplatform Gradle project — add the runtime library, opt-in the Gradle plugin that scans the dependency graph and generates a license catalog, resolve build failures from unknown/copyleft dependencies, and wire up the optional Compose UI. Use whenever a user asks to add/wire up/set up license attribution, NOTICE generation, or open-source license compliance tooling in a Kotlin or Compose Multiplatform project, or mentions "linkedlicense".
---

# Integrating LinkedLicense into a consumer project

This skill drives integration of **this repo's own library** (LinkedLicense) into some
other target Gradle project — not development of LinkedLicense itself. Confirm which
repo/module you're integrating into before editing anything; work in the consumer
project's files, not this one, unless the user is explicitly working on LinkedLicense
itself.

Current published version: check `build.gradle.kts` in this repo (`version = "..."`) for
the latest — as of writing it's `0.9.3`. Prefer asking the user which version to pin, or
using the newest tag/release if you can check Maven Central, rather than guessing.

## Step 1 — Establish scope with the user

Ask (or infer from the target repo) two things before writing any Gradle:

1. **Runtime only, or scanning too?** Depending on `dev.noahtownsend:linkedlicense` alone
   gives the `License` API for hand-written entries. Applying the Gradle plugin
   (`dev.noahtownsend.linkedlicense`) additionally scans the dependency graph and
   generates `GeneratedLicenses.kt` at build time. Most integrations want both — default
   to both unless told otherwise.
2. **Is there a licenses UI need?** If the target has a Compose Multiplatform UI and wants
   a ready-made "Licenses" screen, also add `dev.noahtownsend:linkedlicense-compose`
   (§Step 6 below).

## Step 2 — Add the runtime dependency

In the target module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.noahtownsend:linkedlicense:<version>")
}
```

For KMP projects using `sourceSets`, add it to `commonMain.dependencies` unless the user
only needs it on one platform.

## Step 3 — Apply the plugin (if scanning is wanted)

```kotlin
plugins {
    id("dev.noahtownsend.linkedlicense") version "<version>"
}
```

This registers one `generateLicenseCatalog` task per Kotlin source set (e.g.
`generateJvmMainLicenseCatalog`, `generateCommonMainLicenseCatalog`, `generateDebugLicenseCatalog`).
Nothing else is required to get a first run — running the task (or a normal build) will:

- Auto-create `linkedlicense.toml` at the project root if it doesn't exist, fully
  scaffolded with commented examples for every table.
- Resolve each source set's transitive classpath, dedupe by coordinate, match each
  dependency's license via its POM (recursively resolving parent POMs for inherited licenses
  and metadata, plus npm/CocoaPods/SPM metadata where relevant — see README §2.3), and
  generate `GeneratedLicenses.kt` under `build/generated/linkedlicense/<sourceSet>/`.
- **Fail the build by default** on any dependency it can't match (`failOnUnknown = true`)
  or that resolves to a copyleft license (`failOnCopyleft = true`). This is expected on
  first run for most real projects — go to Step 4.

Only configure the `linkedLicense { }` extension block if the defaults need changing
(README §3 has the full option list: `overridesFile`, `copyRequiredNotices`,
`failOnCopyleft`, `failOnSoftCopyleft`, `failOnUnknown`, `bestEffortLicenseFetch`, `autoPopulate`).
Don't add it speculatively.

## Step 4 — First build: run the task and resolve failures

Run the relevant `generateLicenseCatalog` task(s) (or a full build) and read the failure
output — it aggregates *every* offending coordinate in one pass, not one per rebuild.

For each offending coordinate, in the generated/target `linkedlicense.toml`:

- **Unknown/unmatched license** → add an `[overrides]` entry pinning the correct built-in
  `License` type (e.g. `license = "Apache2"`, `"MIT"`, `"Mit0"`, `"Bsd3Clause"`, `"Epl1"`, `"Cddl1"`, `"Cddl1_1"`, `"Agpl3"`, etc., or `custom:fully.qualified.Symbol` for a project-specific subclass — see
  README §4), *or* an `[ignored]` entry with a reason string if it genuinely shouldn't
  ship in the catalog (e.g. a build-time-only or vendored/internal dependency).
- **Field-specific overrides (author, element name, url)** → you can override specific metadata
  without overriding the license type:
  ```toml
  [overrides]
  "com.squareup.okhttp3:okhttp" = { author = "Square, Inc." }
  ```
  Non-overridden fields are automatically populated from the POM (or best-effort fetch). To disable
  auto-population for an entry, set `autoPopulate = false`.
- **Copyleft license (GPL/AGPL/LGPL/MPL/EPL/CDDL)** → this is a real legal-risk signal, not just
  friction. Do not blanket-disable the guard without the user's explicit sign-off. Prefer
  a scoped `[copyleft-allowed]` entry with a genuine reason (e.g. "build-time only, never
  linked into shipped artifact"). Only set `failOnCopyleft = false` /
  `failOnSoftCopyleft = false` project-wide if the user explicitly wants that.
- Coordinate keys may reference a `libs.versions.toml` alias (`"libs.okio"`) instead of a
  raw `"group:artifact"` string if the project uses a version catalog — prefer the alias
  form when one exists, so overrides don't go stale if the coordinate changes.

Re-run the task after each edit until it succeeds. Do not repeatedly loosen
`failOnUnknown`/`failOnCopyleft` as a shortcut to "fix" failures — the whole point of the
plugin is that every shipped dependency's license is accounted for; only relax those flags
if the user explicitly asks to.

## Step 5 — Wire generated output into code

```kotlin
import dev.noahtownsend.linkedlicense.generated.<sourceset>.GeneratedLicenses
// e.g. ...generated.commonmain.GeneratedLicenses for the cross-platform union,
// or ...generated.jvmmain.GeneratedLicenses for a platform-specific narrower catalog.

val licenses = GeneratedLicenses.all
```

Note the package is lowercased-source-set-qualified (`commonmain`, `jvmmain`, …) — this
is deliberate, so `commonMain`'s and `jvmMain`'s generated files don't collide when
Kotlin's default hierarchy template merges `commonMain` into a platform compilation.
`commonMain`'s catalog is the *union* across all platform targets' resolved dependencies,
not one platform's view.

Merge with hand-written entries as needed:

```kotlin
val allLicenses = GeneratedLicenses.all + listOf(
    License.MIT(elementLicensed = "Something Vendored", author = "...", year = "2024"),
)
```

If the target has non-dependency assets needing attribution (bundled fonts, datasets),
add them to `linkedlicense.toml`'s `[assets]` table (README §3.7) instead of hand-merging
a second list — they flow into `GeneratedLicenses.all` automatically, tagged
`kind = License.Kind.ASSET`.

## Step 6 — Optional: Compose Multiplatform UI

If the target is a Compose Multiplatform project and wants a ready-made licenses screen:

```kotlin
dependencies {
    implementation("dev.noahtownsend:linkedlicense-compose:<version>")
}
```

```kotlin
LicensesButton(licenses = GeneratedLicenses.all)
```

That's the full solution (translated "Licenses" row → full-screen expandable list). Use
`LicensesDialog`/`LicensesList` directly instead if the target wants to drive
visibility/layout itself (README §5).

## Step 7 — Verify

- Full build/`generateLicenseCatalog` succeeds with no unresolved/copyleft failures (or
  only ones the user explicitly accepted).
- `GeneratedLicenses.kt` exists under `build/generated/linkedlicense/<sourceSet>/` and
  compiles as part of the target's normal build (it's registered as an extra source dir
  automatically — don't add it manually).
- If `copyRequiredNotices` wasn't disabled, check `THIRD-PARTY-NOTICES` was created/updated
  at the target project's root.
- `linkedlicense.toml` reads cleanly — every override/ignore/copyleft-allow entry has a
  real reason string, not a placeholder.

## Common pitfalls

- Applying only the library dependency and expecting scanning — scanning requires the
  plugin applied separately (README §2.2, deliberately opt-in and per-project).
- Forgetting `commonMain`'s catalog is a *union* across platforms, not "whichever platform
  built first" — a dependency exclusive to one target still appears in `commonMain`'s
  generated catalog.
- Silencing `failOnUnknown`/`failOnCopyleft` globally to make a build pass instead of
  triaging the actual offending coordinates — defeats the tool's purpose and should only
  happen with explicit user intent.
- Using `bestEffortLicenseFetch = true` without reading the resulting build warnings — it's
  heuristic (fetches a repo, pattern-matches license text) and every guess is flagged; per
  README §2.3, verify and then suppress the warning per-coordinate rather than ignoring it.
