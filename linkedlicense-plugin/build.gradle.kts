import com.vanniktech.maven.publish.GradlePublishPlugin

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.vanniktechMavenPublish)
    alias(libs.plugins.gradlePluginPublish)
}

// Dogfooding note: applying `dev.noahtownsend.linkedlicense` to this module (the one that
// builds the plugin) was tried, resolving it from the local functionalTestRepo the same way
// linkedlicense-compose does (see settings.gradle.kts). It configures, but fails at task-graph
// realization with a classloader `ClassCastException` on `KotlinCompile` - the plugin's
// `compileOnly` binding against `kotlin-gradle-plugin` (see the dependency comment below)
// expects to bind to the *consuming* project's own KGP classes on Gradle's shared plugin
// classloader, but here the "consuming" project is the same one whose `org.jetbrains.kotlin.jvm`
// application built the plugin jar in the first place, so the two KGP class copies collide.
// This is genuinely self-referential/circular in a way linkedlicense-compose isn't (that module
// doesn't build the plugin), so per the dogfooding task's own guidance it's skipped rather than
// hacked around - the plugin's only real dependency (tomlj) is Apache-2.0, documented by hand
// here instead: https://github.com/tomlj/tomlj is Apache License 2.0 per its published POM.
// kotlin-gradle-plugin itself is `compileOnly` below, never shipped, so needs no entry.

group = "dev.noahtownsend"
version = "0.9.4"

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    // Required by com.gradle.plugin-publish for Plugin Portal publishing.
    website = "https://github.com/noahddtownsend/LinkedLicense"
    vcsUrl = "https://github.com/noahddtownsend/LinkedLicense"

    plugins {
        create("linkedLicense") {
            id = "dev.noahtownsend.linkedlicense"
            implementationClass = "dev.noahtownsend.linkedlicense.plugin.LinkedLicensePlugin"
            displayName = "LinkedLicense Gradle Plugin"
            description = "Scans your project's dependency graph and generates a license catalog for you."
            tags = listOf("licenses", "compliance", "kotlin-multiplatform")
        }
    }
}

// Publishes this plugin's `pluginMaven` publication to Maven Central via the Central Portal,
// on top of the java-gradle-plugin/maven-publish setup above (unrelated to, and additional to,
// the `functionalTest` local-repo publishing below - that wiring is untouched). Credentials and
// signing come from environment variables only - see the root build script's `mavenPublishing`
// block for the full convention notes.
mavenPublishing {
    // Both java-gradle-plugin and com.gradle.plugin-publish are applied to this module, so the
    // vanniktech plugin's auto-detection needs an explicit hint - GradlePublishPlugin (rather
    // than the plain GradlePlugin) is what it docs as correct for that combination.
    configure(GradlePublishPlugin())
    publishToMavenCentral()

    // See the root build script's `mavenPublishing` block for why this is conditional - this
    // module's `functionalTest` task (below) depends on local-repo publish tasks that must keep
    // working without any signing credentials present.
    if (project.findProperty("signingInMemoryKey") != null) {
        signAllPublications()
    }

    pom {
        name = "LinkedLicense Gradle Plugin"
        description =
            "A Kotlin Multiplatform library of open-source license templates, plus an opt-in " +
                "Gradle plugin that scans your project's dependency graph and generates the " +
                "license catalog for you."
        url = "https://github.com/noahddtownsend/LinkedLicense"

        licenses {
            license {
                name = "MIT"
                url = "https://github.com/noahddtownsend/LinkedLicense/blob/main/LICENSE"
            }
        }

        developers {
            developer {
                id = "noahddtownsend"
                name = "Noah Townsend"
                email = "noah@noahtownsend.com"
            }
        }

        scm {
            connection = "scm:git:git://github.com/noahddtownsend/LinkedLicense.git"
            developerConnection = "scm:git:git://github.com/noahddtownsend/LinkedLicense.git"
            url = "https://github.com/noahddtownsend/LinkedLicense"
        }
    }
}

sourceSets {
    create("functionalTest") {
        kotlin.srcDir("src/functionalTest/kotlin")
        resources.srcDir("src/functionalTest/resources")
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

val functionalTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

dependencies {
    implementation(project(":"))
    implementation(libs.tomlj)

    // compileOnly, not implementation: this plugin must never bundle its own copy of KGP
    // classes into its jar. At runtime it needs to bind against whichever KGP classes the
    // *consuming* project's own `plugins { id("org.jetbrains.kotlin.multiplatform") }` (or
    // `.jvm`) provides - Gradle keeps those on a shared plugin classloader for plugins
    // co-applied via the `plugins {}` DSL. Referencing KGP types (e.g.
    // KotlinMultiplatformExtension) without this dependency to compile against previously
    // threw NoClassDefFoundError/ClassCastException at apply time.
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(gradleTestKit())
    testImplementation("io.mockk:mockk:1.13.13")

    functionalTestImplementation(libs.kotlin.test)
    functionalTestImplementation(libs.kotlin.test.junit5)
    functionalTestImplementation(gradleTestKit())
    functionalTestImplementation(project(":"))
}

// Kotlin Multiplatform functional tests need the plugin under test to interoperate at runtime
// with a real `kotlin("multiplatform")` plugin application in the fixture project - i.e. they
// need to prove the compileOnly classloader fix (see the dependencies block above) actually
// works. GradleRunner.withPluginClasspath() (used by every other functional test here) injects
// the plugin under test via a separate mechanism (PluginUnderTestMetadata) that does NOT get
// the same shared-classpath treatment Gradle gives plugins resolved together through the normal
// `plugins {}` DSL, so it doesn't exercise that interop at all - a KMP fixture applying the
// plugin that way throws NoClassDefFoundError even though the fix genuinely works for a real
// consumer. Publishing to a local repo and having KMP fixtures resolve the plugin from there via
// ordinary `plugins { id(...) version ... }` resolution matches real-world usage instead.
// Shared with the root project (`build.gradle.kts`), which publishes into the same directory:
// `implementation(project(":"))` above publishes as a dependency on the root module's own
// coordinates (Gradle Module Metadata resolves the actual `jvm` variant via that root module's
// "available-at" pointer at consumption time), so a fixture resolving this plugin from a repo
// needs the root project's `jvm`/`kotlinMultiplatform` publications sitting in that same repo
// too - see the root build script's comment for the full explanation.
val functionalTestRepo = rootProject.layout.buildDirectory.dir("functionalTestRepo")

publishing {
    repositories {
        maven {
            name = "functionalTest"
            url = uri(functionalTestRepo)
        }
    }
}

val functionalTest by tasks.registering(Test::class) {
    description = "Runs the functional tests (Gradle TestKit)."
    group = "verification"
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    dependsOn("publishAllPublicationsToFunctionalTestRepository")
    // Only the root project's `jvm` and `kotlinMultiplatform` publications - not "publish all",
    // which would also require an Android SDK/iOS toolchain to build the android/iOS/js/wasmJs
    // targets just to run functional tests. See the root build script's comment.
    dependsOn(":publishJvmPublicationToFunctionalTestRepository")
    dependsOn(":publishKotlinMultiplatformPublicationToFunctionalTestRepository")
    systemProperty("linkedlicense.functionalTestRepo", functionalTestRepo.get().asFile.absolutePath)
    systemProperty("linkedlicense.version", version.toString())
}

tasks.test {
    useJUnitPlatform()
}

tasks.check {
    dependsOn(functionalTest)
}
