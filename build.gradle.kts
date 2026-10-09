plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    // Shows the DIAG lines from PluginEntriesLockDiagnosticsTest
    testLogging.showStandardStreams = true
    // Gradle version run by TestKit
    systemProperty("testGradleVersion", providers.gradleProperty("testGradleVersion").getOrElse(gradle.gradleVersion))
}
