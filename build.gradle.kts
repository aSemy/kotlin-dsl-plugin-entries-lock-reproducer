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
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
    // Gradle version run by TestKit; defaults to the version running this build.
    // Pass -PtestGradleInstallation=<dir> to check a locally built distribution instead.
    providers.gradleProperty("testGradleVersion").orNull?.let { systemProperty("testGradleVersion", it) }
    providers.gradleProperty("testGradleInstallation").orNull?.let { systemProperty("testGradleInstallation", it) }

    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "4")
}
