plugins {
    `java-gradle-plugin`
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

val functionalTest by tasks.registering(Test::class) {
    description = "Runs the functional tests (Gradle TestKit)."
    group = "verification"
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.test {
    useJUnitPlatform()
}

tasks.check {
    dependsOn(functionalTest)
}
