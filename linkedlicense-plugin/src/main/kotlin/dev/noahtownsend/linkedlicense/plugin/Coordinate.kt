package dev.noahtownsend.linkedlicense.plugin

/** A resolved Maven coordinate. */
data class Coordinate(
    val group: String,
    val artifact: String,
    val version: String,
) {
    /** `group:artifact`, the key shape used in `linkedlicense.toml`. */
    val moduleId: String get() = "$group:$artifact"

    override fun toString(): String = "$group:$artifact:$version"
}
