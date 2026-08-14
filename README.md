# LinkedLicense

A Kotlin Multiplatform library of open-source license templates, plus an opt-in Gradle
plugin that scans your project's dependency graph and generates the license catalog for
you — so you never have to hand-type another `NOTICE` entry.

- **Targets**: Android, iOS (x64 / arm64 / simulator arm64), JVM, JS, Wasm.
- **License (of this library's own code)**: MIT. See [`LICENSE`](./LICENSE).
- **Group / artifact**: `dev.noahtownsend:linkedlicense`.

## Quickstart

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.noahtownsend:linkedlicense:<version>")
}
```

That alone gets you the `License` API (§1) for hand-writing entries yourself — no scanning,
no build-time tooling, nothing generated. If you also want your dependency graph scanned and
a catalog generated automatically, apply the plugin too (§2):

```kotlin
plugins {
    id("dev.noahtownsend.linkedlicense") version "<version>"
}
```

Then, in code:

```kotlin
// Manual: construct License instances yourself
val licenses = listOf(
    License.MIT(elementLicensed = "Kotlin", author = "JetBrains", year = "2011"),
)

// Or, with the plugin applied: use what generateLicenseCatalog already built for you
val licenses = GeneratedLicenses.all
```

To show them in a Compose Multiplatform UI with zero custom screen code, add
`dev.noahtownsend:linkedlicense-compose` (§5) and drop in the ready-made component:

```kotlin
LicensesButton(licenses = licenses)
```

That's a translated "Licenses" row that opens a full-screen, expandable list on tap — the
whole feature, one line. See the rest of this README for every configuration knob once you
need one.

## Disclaimer

LinkedLicense is a **tool that helps you track third-party license obligations** — it is not
a legal service, and using it does not by itself guarantee compliance with any license,
anywhere. Automated scanning can miss dependencies, misread ambiguous or malformed license
metadata, or match against the wrong template; best-guess detection (§2.3) is explicitly
heuristic; the built-in license *templates* are provided for convenience and are not a
substitute for reading the actual license governing a given dependency. **You, the consumer
of this library, remain solely responsible for your project's license compliance** —
reviewing what this library generates, correcting or overriding anything wrong (§3.1), and
making your own legal judgment calls (consulting counsel where appropriate) — the same way
you would be responsible for compliance whether or not any tooling was involved. This
library and its authors accept no liability for compliance failures, misidentified licenses,
or omissions, consistent with the "AS IS", no-warranty terms of the [MIT license](./LICENSE)
this project itself is released under.

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
    open val kind: Kind = Kind.DEPENDENCY,
) {
    abstract val licenseText: String
    open val copyleftStrength: CopyleftStrength get() = CopyleftStrength.NONE
    val isCopyleft: Boolean get() = copyleftStrength != CopyleftStrength.NONE

    enum class Kind { DEPENDENCY, ASSET }
    enum class CopyleftStrength { NONE, WEAK, STRONG }
}
```

`copyleftStrength` replaces the old plain `isCopyleft: Boolean` flag with the weak/strong
distinction described below — `isCopyleft` is now a derived, non-overridable convenience
(`true` whenever `copyleftStrength != NONE`), so existing code checking `license.isCopyleft`
keeps working unchanged; only subclasses that need to *declare* copyleft status override
`copyleftStrength` instead.

`kind` distinguishes an actual code dependency (the default) from a bundled non-dependency
asset — a dataset, image, font, or similar — that carries its own license/attribution but
was never resolved off a Gradle dependency graph. It's a constructor parameter on the base
class, not a per-subtype thing: the same `License.MIT`, say, can represent either an MIT
library dependency or an MIT-licensed font depending on how you construct it. Consuming UI (e.g. [`linkedlicense-compose`, §5](#5-compose-multiplatform-ui-components-optional))
can use it to group or label entries separately ("Dependencies" vs. "Data & Assets"). See
[§3.7](#37-non-dependency-assets) for how the scanning plugin lets you declare these
alongside scanned dependencies without hand-merging two lists yourself.

### Built-in types

| Type | Constructor params (beyond `elementLicensed`/`author`/`url`) | `copyleftStrength` |
|---|---|---|
| `License.MIT` | `year: String` | `NONE` |
| `License.Apache1_1` | — | `NONE` |
| `License.Apache2` | — | `NONE` |
| `License.Bsd2Clause` | `year: String` | `NONE` |
| `License.Bsd3Clause` | `year: String` | `NONE` |
| `License.Isc` | `year: String` | `NONE` |
| `License.Gpl2` | — | `STRONG` |
| `License.Gpl3` | — | `STRONG` |
| `License.Lgpl2_1` | — | `WEAK` |
| `License.Lgpl3` | — | `WEAK` |
| `License.Mpl2` | — | `WEAK` |
| `License.Ofl` | — | `NONE` |
| `License.Unlicense` | — | `NONE` |
| `License.Cc0` | — | `NONE` |
| `License.CreativeCommons` | `variant: CcVariant`, `version: CcVersion` | `NONE`* |
| `License.OpenGovernmentLicence` | `jurisdiction: String`, `version: OglVersion` | `NONE` |
| `License.PublicDomain` | — | `NONE` |
| `License.UsGovernmentPublicDomain` | — | `NONE` |
| `License.CopyrightExpired` | `jurisdiction: String? = null` | `NONE` |
| `License.Odbl` | — | `NONE`\*\* |
| `License.Custom` | `text: String`, `licenseName: String? = null` | `NONE` (escape hatch for anything not listed above) |

\* `CreativeCommons` variants with `NC`/`ND`/`SA` terms carry redistribution *conditions*,
but none require releasing your own source code the way GPL-family licenses do, so they're
not classified as copyleft for the purposes of [§3.5](#35-copyleft-guard-on-by-default).

\*\* `Odbl` (Open Data Commons Open Database License 1.0) is a share-alike license for
*databases*, not source code — used for e.g. OpenStreetMap data. Classified the same way as
CC BY-SA above, for the same reason: it has redistribution conditions but doesn't require
releasing your own software source.

`CcVariant` is `BY | BY_SA | BY_ND | BY_NC | BY_NC_SA | BY_NC_ND`. `CcVersion` is `V1_0 |
V2_0 | V2_5 | V3_0 | V4_0`. `OglVersion` is `V1_0 | V2_0 | V3_0` (the UK Open Government
Licence's three published versions). These are closed, known sets, so they're modeled as
enums rather than free-form version strings — a typo like `"4.O"` (letter O) fails to
compile instead of silently generating a broken `creativecommons.org` URL at runtime.

Three built-in types cover different reasons something is in the public domain — don't use
them interchangeably: `PublicDomain` is a voluntary dedication (the rights holder waived
them, CC0-style); `UsGovernmentPublicDomain` is for US federal government works, exempt from
copyright by statute (17 U.S.C. § 105); `CopyrightExpired` is for works whose copyright term
has simply run out under applicable law — nobody dedicated or exempted anything, the
protection period ended. Copyright terms vary by jurisdiction (commonly life-of-the-author-
plus-50-or-70-years, but not universally), so `CopyrightExpired` takes an optional
`jurisdiction` param to state which jurisdiction's term you're relying on; leave it `null`
if you're asserting the work is public domain more broadly (e.g. clearly pre-1900 with no
plausible live copyright anywhere).

`copyleftStrength` is non-`NONE` for `Gpl2`/`Gpl3` (`STRONG`) and `Lgpl2_1`/`Lgpl3`/`Mpl2`
(`WEAK`) — anything with reciprocal/share-alike source-disclosure obligations. LGPL and MPL
are *weak* copyleft (obligations apply per-file/per-library, not to your whole program)
versus GPL's *strong* copyleft (obligations apply to the whole combined work); both default
to failing the build under the copyleft guard below, since either can require you to release
source you didn't intend to — but the guard lets you configure weak-copyleft handling
separately from strong (§3.5).

`License.Custom`'s optional `licenseName` names the license itself (e.g. `"Acme EULA"`),
distinct from `elementLicensed` (what's *being* licensed, e.g. `"Acme SDK"`) — it becomes
`shortName` when provided (falling back to the literal string `"Custom"` when omitted), so a
custom entry can show a real label in UI instead of a generic placeholder.

### Example

```kotlin
val licenses = listOf(
    License.MIT(elementLicensed = "Kotlin", author = "JetBrains", year = "2011"),
    License.Apache2(elementLicensed = "Ktor", author = "JetBrains"),
    License.CreativeCommons(
        variant = CcVariant.BY_SA,
        version = CcVersion.V4_0,
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
`generateCommonMainLicenseCatalog`). In a Kotlin Multiplatform project, every platform
source set (`jvmMain`, `androidMain`, `iosMain`, …) has its own resolvable classpath and
gets scanned independently. `commonMain` isn't itself tied to one platform's dependency
graph, so its catalog is the **union** of every platform target's resolved coordinates —
this is deliberately the more expensive, more correct option: a dependency that only exists
on one target (e.g. an iOS-only binding) still shows up, rather than being silently missed
because some other target happened to be picked as "the" reference classpath. Every fail-fast
check (§3.3, §3.5, §3.6) applies to that unioned set.

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
5. Anything that can't be matched, and has no override (§3), **fails the build** by default — see
   §3.3. Anything copyleft, and not allow-listed, also **fails the build** by default — see
   §3.5.
6. Copies required notices into `THIRD-PARTY-NOTICES` — see §3.4.
7. Once every dependency is resolved (matched, overridden, or ignored) and no failure was
   triggered, generates `GeneratedLicenses.kt` (an object exposing `all: List<License>`)
   into `build/generated/linkedlicense/<sourceSet>/`, registered as an extra Kotlin source
   directory so it compiles like ordinary code — **package-qualified per source set**
   (`dev.noahtownsend.linkedlicense.generated.<sourceSet lowercased>`, e.g. `...generated.
   commonmain`, `...generated.jvmmain`), not just directory-qualified:

   ```kotlin
   // build/generated/linkedlicense/commonMain/.../GeneratedLicenses.kt
   package dev.noahtownsend.linkedlicense.generated.commonmain

   object GeneratedLicenses {
       val all: List<License> = listOf(/* ... */)
   }
   ```

   This matters even though each source set's generated file lives in its own build
   directory: Kotlin's default hierarchy template merges `commonMain` sources into every
   platform target's own compilation, so a `jvmMain` compile sees *both* `commonMain`'s
   generated file and `jvmMain`'s own — two identically-packaged, identically-named `object
   GeneratedLicenses` declarations in the same compilation is a redeclaration error. Distinct
   packages avoid that; import the specific one you want (`commonmain` for the cross-platform
   union, or a specific platform's own narrower catalog).

   Merge it with your own hand-curated entries as needed:

   ```kotlin
   import dev.noahtownsend.linkedlicense.generated.commonmain.GeneratedLicenses

   val allLicenses = GeneratedLicenses.all + listOf(
       License.Custom(
           elementLicensed = "Mapbox Maps",
           author = "Mapbox",
           url = "https://www.mapbox.com/about/maps/",
           text = "...",
           licenseName = "Mapbox ToS",
       ),
   )
   ```

### 2.2 Why this is opt-in and per-project

The plugin only runs for a project that explicitly applies it, and only ever emits entries
for what's actually resolved in *that* project's own dependency graph. Depending on
`dev.noahtownsend:linkedlicense` alone — without applying the plugin — pulls in only the
`License` types; nothing is scanned, generated, or bundled automatically, and no consumer
is ever forced to ship or display license text for dependencies they don't have.

### 2.3 Ecosystem coverage: npm, CocoaPods, SPM

A Kotlin Multiplatform project can pull in dependencies outside Gradle/Maven entirely —
npm packages for `js`/`wasmJs` targets, CocoaPods pods and Swift Package Manager packages
for iOS interop. `generateLicenseCatalog` scans all three, folded into the same per-source-
set pipeline (§2) and the same `linkedlicense.toml` overrides (§3.1), with the same
fail-on-unknown/copyleft/policy checks — not a separate bolted-on tool with its own rules.

- **npm** (`jsMain`/`wasmJsMain` source sets with declared npm dependencies): resolved via
  the generated `package.json`/lockfile, each package's own `package.json` `license` field
  matched through the same SPDX-name table used for Maven POMs. Its `repository` field
  (a git URL) also feeds the best-guess fallback below when `license` is missing/unmatched.
- **CocoaPods** (when the `kotlin-cocoapods` plugin is applied): resolved via `Podfile.lock`,
  each pod's `.podspec`'s `license` field (string or `{ type, file, text }` object) matched
  the same way. Its `source`/`homepage` fields likewise feed the fallback below.
- **SPM**: `Package.resolved` only pins a git URL + revision — there's no license field
  anywhere in that chain to read, unlike npm/CocoaPods/Maven, so it *only* ever has the
  fallback below to go on (never a primary field match).

#### Best-guess fallback (opt-in, off by default)

Maven POMs, npm's `package.json`, and CocoaPods' podspecs all *usually* carry a
machine-readable `license` field, but not always — and SPM never does. In every one of
those cases, if a repository/source URL is available (Maven's POM `<scm>`, npm's
`repository`, a podspec's `source`/`homepage`, or SPM's `Package.resolved` URL directly),
`bestEffortLicenseFetch = true` (default `false`) makes the plugin fetch that repo's root
at the resolved revision, look for a `LICENSE`/`LICENSE.md` file, and pattern-match its
content against known license texts.

This is opt-in because it adds network I/O and a new failure mode (repo unreachable) to
what's otherwise an offline, deterministic build step, and heuristic text-matching can
misfire in ways structured-field parsing can't — so every dependency resolved this way:

- **Emits a build warning** naming the coordinate and the guessed license, every build,
  so a best-guess entry is never silently indistinguishable from an authoritative
  field-matched one.
- Can have that warning silenced per-coordinate via a `[suppress-best-guess-warnings]`
  table in `linkedlicense.toml` (same ecosystem-prefixed key format as everywhere else) —
  for cases you've manually verified the guess is correct and don't want repeated noise on
  every build. This suppresses the warning only; the guessed license itself is still used
  and still subject to the copyleft guard (§3.5) and `[license-policy]` (§3.6) like any
  other entry.

```toml
[suppress-best-guess-warnings]
"spm:https://github.com/apple/swift-log" = "Verified 2026-08-14: Apache-2.0 LICENSE at tag 1.5.3."
```

(A reason string is required, same audit-trail pattern as `[ignored]`/`[copyleft-allowed]`.)

Every dependency from any of these three ecosystems gets an override-table key prefixed
with its ecosystem, in the *same* `[overrides]`/`[ignored]`/`[copyleft-allowed]`/
`[license-policy]` tables Maven coordinates already use (§3.1) — one file, one mental model:

```toml
[overrides]
"npm:left-pad" = { license = "MIT" }
"cocoapods:Alamofire" = { license = "MIT" }
"spm:https://github.com/apple/swift-log" = { license = "Apache2" }
```

## 3. Configuring the plugin

```kotlin
linkedLicense {
    overridesFile = file("linkedlicense.toml") // default shown
    copyRequiredNotices = true                 // default shown
    failOnCopyleft = true                      // default shown, see §3.5
    failOnSoftCopyleft = null                  // default shown, see §3.5 — null follows failOnCopyleft
    failOnUnknown = true                       // default shown
    bestEffortLicenseFetch = false             // default shown, see §2.3
}
```

### 3.1 The override file (`linkedlicense.toml`)

Overrides live in their own version-catalog-shaped TOML file.

```toml
[overrides]
"com.mapbox.maps:android" = { license = "Custom", elementLicensed = "Mapbox Maps", author = "Mapbox", text = "...", licenseName = "Mapbox ToS" }
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

#### Auto-generated on first run

If `overridesFile` doesn't exist yet, `generateLicenseCatalog` creates it before doing
anything else, fully scaffolded rather than empty — every table this file supports
(`[overrides]`, `[ignored]`, `[copyleft-allowed]`, `[license-policy]`, `[assets]` (§3.7),
`[suppress-best-guess-warnings]` (§2.3)) is present with a header comment explaining it and
a commented-out example line showing the correct shape, so you never have to go back to
this README to remember the syntax for a table you haven't needed yet:

```toml
# generated by linkedlicense — see https://github.com/noahddtownsend/LinkedLicense#3-configuring-the-plugin

[overrides]
# "group:artifact" = { license = "Apache2" }

[ignored]
# "group:artifact" = "reason"

[copyleft-allowed]
# "group:artifact" = "reason"

[license-policy]
# allow = ["MIT", "Apache2"]
# block = ["Gpl3"]

[assets]
# "asset-id" = { license = "MIT", elementLicensed = "...", author = "...", year = "..." }

[suppress-best-guess-warnings]
# "npm:package-name" = "reason"
```

This only fires when the file is genuinely absent — an existing file, even an empty one, is
never touched or rewritten by the plugin.

### 3.2 Version-catalog addressing

A coordinate key in any of the three tables may be either a raw `group:artifact` string, or
a `libs.*`-style alias resolved against your own `libs.versions.toml` via Gradle's
`VersionCatalogsExtension` (e.g. `"libs.koin"`). Using the alias means the override stays
correct automatically if the underlying coordinate changes but the catalog alias doesn't,
instead of silently going stale.

### 3.3 Fail-on-unknown (on by default)

A dependency that can't be auto-matched and has no `[overrides]`/`[ignored]` entry fails
`generateLicenseCatalog`. The task collects *every* unresolved coordinate before failing, so
you fix them all in one pass instead of one build cycle at a time. Unlicensed/unknown
dependencies should never silently ship, so this defaults to on; set `failOnUnknown = false`
if you need `GeneratedLicenses.kt` to still generate with unmatched dependencies simply
omitted (not recommended — you lose the guarantee that every shipped dependency is
accounted for). This is separate from `-x generateLicenseCatalog`, which skips the whole
task rather than relaxing this one check.

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

`failOnCopyleft` governs strong copyleft (`copyleftStrength == STRONG`, e.g. GPL) and is
also the *default* for weak copyleft (`copyleftStrength == WEAK`, e.g. LGPL/MPL) when you
haven't said anything more specific. To treat weak copyleft differently from strong, set
`failOnSoftCopyleft` (`Boolean?`, default `null`):

```kotlin
linkedLicense {
    failOnCopyleft = true          // governs strong copyleft; the fallback for weak copyleft
    failOnSoftCopyleft = false     // explicit override for weak copyleft only — takes
                                    // precedence over failOnCopyleft whenever it's non-null
}
```

- `failOnSoftCopyleft = null` (the default): weak-copyleft dependencies are governed by
  `failOnCopyleft`, same as strong ones — no behavior change from a single unified setting.
- `failOnSoftCopyleft = false`: weak-copyleft dependencies never fail the build regardless of
  `failOnCopyleft`, while strong copyleft still does (unless `failOnCopyleft` is also
  `false`).
- `failOnSoftCopyleft = true`: weak-copyleft dependencies always fail the build (subject to
  `[copyleft-allowed]` same as anything else) even if `failOnCopyleft = false` has turned the
  guard off for strong copyleft.
- `[copyleft-allowed]` is the same escape hatch for both strengths — it's a per-coordinate
  override, not a strength-specific one.

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

### 3.7 Non-dependency assets

Not everything that needs attribution is a Gradle dependency — bundled fonts, datasets,
images, and similar assets carry licenses/attribution too, but there's nothing to resolve
off the dependency graph for them. Declare these in `linkedlicense.toml` instead of hand-
merging a second list into `GeneratedLicenses.all` yourself:

```toml
[assets]
"mwgg-airports-db" = { license = "MIT", elementLicensed = "Airports JSON Database", author = "Martin Weyer (mwgg)", year = "2018", url = "https://github.com/mwgg/Airports" }
"cinzel-decorative-font" = { license = "Ofl", elementLicensed = "Cinzel Decorative Font", author = "Matt Tindal", url = "https://fonts.google.com/specimen/Cinzel+Decorative" }
```

- The key is an arbitrary asset identifier you choose (not a `group:artifact` coordinate —
  there's no dependency behind it to look one up from). The value shape matches
  `[overrides]` entries: a built-in `license` type name (or a `custom:` reference) plus that
  type's constructor arguments.
- Every `[assets]` entry is included in `GeneratedLicenses.kt` unconditionally, tagged
  `kind = License.Kind.ASSET` (see §1) — there's no matching/fail-on-unknown step for these,
  since you're supplying the license directly rather than asking the plugin to infer it.
- `[license-policy]` (§3.6) and the copyleft guard (§3.5) still apply to `[assets]` entries
  the same as scanned dependencies — a GPL-licensed dataset needs the same
  `[copyleft-allowed]` treatment a GPL-licensed library would.

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

## 5. Compose Multiplatform UI components (optional)

A separate module, `dev.noahtownsend:linkedlicense-compose` (package
`dev.noahtownsend.linkedlicense.compose`), provides ready-made Compose Multiplatform
components so you don't have to build a licenses screen by hand. All three are
theme-agnostic — they read colors/typography from whatever `MaterialTheme` you already wrap
them in and never apply one of their own — and each builds on the one before it:

1. **`LicensesList(licenses: List<License>, modifier: Modifier = Modifier)`** — a scrollable,
   sorted-by-author list where each entry expands/collapses to reveal its full
   `licenseText`.
2. **`LicensesDialog(licenses: List<License>, onDismissRequest: () -> Unit, modifier: Modifier = Modifier)`**
   — a full-screen dialog wrapping `LicensesList`, with an "X" close button. Independently
   usable: drive its visibility from your own state/trigger without touching element 3.
3. **`LicensesButton(licenses: List<License>, modifier: Modifier = Modifier)`** — the full
   solution: a row/text labeled "Licenses" (translated — see below) that manages its own
   dialog-visibility state and shows `LicensesDialog` on tap.

```kotlin
// Full solution
LicensesButton(licenses = GeneratedLicenses.all)

// Or drive the dialog yourself
var showLicenses by remember { mutableStateOf(false) }
if (showLicenses) {
    LicensesDialog(licenses = GeneratedLicenses.all, onDismissRequest = { showLicenses = false })
}
```

The "Licenses" label ships translated via Compose Multiplatform resources across roughly
40 locales.
