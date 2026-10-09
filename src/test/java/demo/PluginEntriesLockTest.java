package demo;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginEntriesLockTest {

    @Test
    void projectDirectoryCanBeDeletedAfterBuild() throws IOException {
        Path dir = Files.createTempDirectory("plugin-entries-lock");
        write(dir.resolve("settings.gradle.kts"), "");
        write(dir.resolve("buildSrc/build.gradle.kts"), """
            plugins {
                `kotlin-dsl`
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation(files("broken.jar"))
            }
            """);
        // Not a jar, so the build fails right after the plugin entries cache is written
        write(dir.resolve("buildSrc/broken.jar"), "not a jar");
        write(dir.resolve("buildSrc/src/main/kotlin/demo.gradle.kts"), "");

        BuildResult result = GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withGradleVersion(System.getProperty("testGradleVersion"))
            .withArguments("help")
            .buildAndFail();
        assertTrue(result.getOutput().contains("Failed to extract plugin metadata"), result.getOutput());

        // On Windows, fails while the daemon still holds the kotlin-dsl-plugin-entries cache files
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
