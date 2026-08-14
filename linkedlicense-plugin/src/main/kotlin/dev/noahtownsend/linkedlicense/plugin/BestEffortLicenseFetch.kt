package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import kotlin.reflect.KClass

/**
 * The best-guess fallback (README §2.3 "Best-guess fallback"): when a dependency's own
 * primary license field is missing/unmatched but a repository URL is known, fetch that repo's
 * `LICENSE`/`LICENSE.md` at the resolved revision and pattern-match its content against known
 * license texts. Opt-in via `bestEffortLicenseFetch = true`.
 */
object BestEffortLicenseFetch {
    /** `raw.githubusercontent.com` candidate paths tried, in order, for a GitHub repo URL. */
    private val licenseFileNames = listOf("LICENSE", "LICENSE.md", "LICENSE.txt", "LICENSE-MIT", "COPYING")

    private val githubRepoRegex = Regex("""^https?://github\.com/([^/]+)/([^/]+?)(?:\.git)?/?$""")

    /**
     * Builds the `raw.githubusercontent.com` URLs to try for [repoUrl] at [ref], or `null` if
     * [repoUrl] isn't a recognized `github.com` URL. Scoped to GitHub-hosted repos only — an
     * arbitrary git host has no single well-known "raw file" convention the way GitHub's CDN
     * does, so this is a reasonable, testable starting point rather than a general git-host
     * fetcher (README §2.3 permits scoping this).
     */
    fun candidateRawUrls(
        repoUrl: String,
        ref: String,
    ): List<String>? {
        val match = githubRepoRegex.matchEntire(repoUrl.trim()) ?: return null
        val (org, repo) = match.destructured
        return licenseFileNames.map { fileName -> "https://raw.githubusercontent.com/$org/$repo/$ref/$fileName" }
    }

    /**
     * Attempts the fallback for one dependency: tries each candidate raw URL in turn via
     * [fetch] (an injectable fetch function so tests never make real network calls), and
     * pattern-matches the first successfully-fetched, non-blank body against [LicenseTextMatcher].
     * Returns `null` if [repoUrl] isn't fetchable (non-GitHub) or nothing could be fetched/matched.
     */
    fun guessLicense(
        repoUrl: String,
        ref: String,
        fetch: (String) -> String?,
    ): KClass<out License>? {
        val candidates = candidateRawUrls(repoUrl, ref) ?: return null

        for (url in candidates) {
            val body = fetch(url) ?: continue

            if (body.isBlank()) {
                continue
            }

            LicenseTextMatcher.match(body)?.let { return it }
        }

        return null
    }
}

/**
 * Pattern-matches a fetched `LICENSE` file's raw text against distinctive, near-exact phrases
 * from the canonical text of each supported license — a heuristic, not full SPDX detection
 * (README §2.3 explicitly calls this out as reasonable-but-heuristic). Matching is done against
 * a handful of long, low-collision-risk phrases per license rather than the entire canonical
 * text, since real-world LICENSE files commonly substitute in a copyright holder's name/year
 * that a whole-text comparison would break on.
 */
object LicenseTextMatcher {
    /** Collapses all whitespace and punctuation to single spaces, so quoting/punctuation differences between a canonical template and a real-world LICENSE file never break a match. */
    private fun normalize(text: String): String =
        text
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private val markers: List<Pair<KClass<out License>, List<String>>> =
        listOf(
            License.MIT::class to
                listOf(
                    "permission is hereby granted, free of charge, to any person obtaining a copy",
                    "the software is provided \"as is\", without warranty of any kind, express or",
                ),
            License.Apache2::class to
                listOf(
                    "apache license",
                    "version 2.0, january 2004",
                    "licensed under the apache license, version 2.0",
                ),
            License.Apache1_1::class to
                listOf(
                    "apache license, version 1.1",
                ),
            License.Bsd3Clause::class to
                listOf(
                    "redistribution and use in source and binary forms, with or without",
                    "neither the name of",
                    "may be used to endorse or promote products derived from this software",
                ),
            License.Bsd2Clause::class to
                listOf(
                    "redistribution and use in source and binary forms, with or without",
                ),
            License.Isc::class to
                listOf(
                    "permission to use, copy, modify, and/or distribute this software for any",
                    "purpose with or without fee is hereby granted",
                ),
            License.Gpl3::class to
                listOf(
                    "gnu general public license",
                    "version 3, 29 june 2007",
                ),
            License.Gpl2::class to
                listOf(
                    "gnu general public license",
                    "version 2, june 1991",
                ),
            License.Lgpl3::class to
                listOf(
                    "gnu lesser general public license",
                    "version 3, 29 june 2007",
                ),
            License.Lgpl2_1::class to
                listOf(
                    "gnu lesser general public license",
                    "version 2.1, february 1999",
                ),
            License.Mpl2::class to
                listOf(
                    "mozilla public license, v. 2.0",
                ),
            License.Unlicense::class to
                listOf(
                    "this is free and unencumbered software released into the public domain",
                ),
        )

    /**
     * Returns the first license type whose *entire* marker set is found (as substrings) in
     * [text], checked in the declaration order above (most specific/least ambiguous first —
     * e.g. BSD-3-Clause's "neither the name of" marker before the more generic BSD-2-Clause
     * check, since BSD-3 text would otherwise also satisfy BSD-2's weaker marker set).
     */
    fun match(text: String): KClass<out License>? {
        val normalized = normalize(text)

        for ((kClass, requiredMarkers) in markers) {
            if (requiredMarkers.all { normalize(it) in normalized }) {
                return kClass
            }
        }

        return null
    }
}
