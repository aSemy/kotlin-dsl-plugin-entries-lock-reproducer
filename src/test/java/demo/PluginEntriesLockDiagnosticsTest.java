package demo;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Same build as {@link PluginEntriesLockTest}, but reports what happens to the cache files right after the build
 * instead of failing. One line per repetition, prefixed with "DIAG".
 */
class PluginEntriesLockDiagnosticsTest {

    @RepeatedTest(20)
    void reportCacheFileStateAfterBuild(RepetitionInfo repetition) throws IOException, InterruptedException {
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
        write(dir.resolve("buildSrc/broken.jar"), "not a jar");
        write(dir.resolve("buildSrc/src/main/kotlin/demo.gradle.kts"), "");

        String gradleVersion = System.getProperty("testGradleVersion");
        long buildStart = System.nanoTime();
        BuildResult result = GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withGradleVersion(gradleVersion)
            .withArguments("help")
            .buildAndFail();
        long returned = System.nanoTime();
        assertTrue(result.getOutput().contains("Failed to extract plugin metadata"), result.getOutput());

        Path cacheDir = dir.resolve(".gradle/" + gradleVersion + "/kotlin-dsl-plugin-entries");
        Path lockFile = cacheDir.resolve("kotlin-dsl-plugin-entries.lock");
        Path entriesFile = cacheDir.resolve("jar-entries.bin");

        String lockState = lockState(lockFile);
        String deleteLock = tryDelete(lockFile);
        String deleteEntries = tryDelete(entriesFile);
        long checked = System.nanoTime();

        // If a delete failed, keep retrying to see how long the files stay held
        String freedAfter = "-";
        if (deleteLock.startsWith("FAILED") || deleteEntries.startsWith("FAILED")) {
            freedAfter = "still held after 10000 ms";
            while (millisSince(returned) < 10_000) {
                Thread.sleep(10);
                if (!tryDelete(lockFile).startsWith("FAILED") && !tryDelete(entriesFile).startsWith("FAILED")) {
                    freedAfter = millisSince(returned) + " ms after the build returned";
                    break;
                }
            }
        }

        System.out.printf(
            "DIAG run %2d: build took %d ms, checked %d ms after it returned | lock: %s | delete lock file: %s | delete jar-entries.bin: %s | freed: %s%n",
            repetition.getCurrentRepetition(), (returned - buildStart) / 1_000_000, (checked - returned) / 1_000_000,
            lockState, deleteLock, deleteEntries, freedAfter
        );
    }

    private static String lockState(Path lockFile) {
        if (!Files.exists(lockFile)) {
            return "no lock file";
        }
        try (FileChannel channel = FileChannel.open(lockFile, READ, WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                return "HELD";
            }
            lock.release();
            return "free";
        } catch (IOException e) {
            return "cannot open (" + e + ")";
        }
    }

    private static String tryDelete(Path file) {
        try {
            return Files.deleteIfExists(file) ? "ok" : "absent";
        } catch (IOException e) {
            return "FAILED (" + e + ")";
        }
    }

    private static long millisSince(long nanoTime) {
        return (System.nanoTime() - nanoTime) / 1_000_000;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
