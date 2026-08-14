plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm")
}

group = "dev.noahtownsend"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    plugins {
        create("linkedLicense") {
            id = "dev.noahtownsend.linkedlicense"
            implementationClass = "dev.noahtownsend.linkedlicense.plugin.LinkedLicensePlugin"
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
