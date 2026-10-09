package demo;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.RepeatedTest;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginEntriesLockTest {

    private static final List<String> SUBPROJECTS = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");

    // Each test is repeated, and tests run concurrently (see junit-platform.properties), in case the failure depends on timing

    @RepeatedTest(10)
    void successfulBuild() throws IOException {
        Path dir = createProject();

        BuildResult result = runner(dir).withArguments("help", "--parallel").build();
        assertTrue(result.getOutput().contains("demo applied"));

        assertProjectDirectoryCanBeDeleted(dir, true);
    }

    @RepeatedTest(5)
    void taskFails() throws IOException {
        Path dir = createProject();
        append(dir.resolve("build.gradle.kts"), """
            tasks.register("failing") {
                doLast { throw GradleException("expected task failure") }
            }
            """);

        BuildResult result = runner(dir).withArguments("failing", "--parallel").buildAndFail();
        assertTrue(result.getOutput().contains("expected task failure"));

        assertProjectDirectoryCanBeDeleted(dir, true);
    }

    @RepeatedTest(5)
    void subprojectConfigurationFails() throws IOException {
        Path dir = createProject();
        append(dir.resolve("j/build.gradle.kts"), """
            throw GradleException("expected configuration failure")
            """);

        BuildResult result = runner(dir).withArguments("help", "--parallel").buildAndFail();
        assertTrue(result.getOutput().contains("expected configuration failure"));

        assertProjectDirectoryCanBeDeleted(dir, true);
    }

    @RepeatedTest(5)
    void subprojectScriptDoesNotCompile() throws IOException {
        Path dir = createProject();
        append(dir.resolve("j/build.gradle.kts"), """
            this does not compile
            """);

        BuildResult result = runner(dir).withArguments("help", "--parallel").buildAndFail();
        assertTrue(result.getOutput().contains("Script compilation error"));

        assertProjectDirectoryCanBeDeleted(dir, true);
    }

    @RepeatedTest(5)
    void precompiledScriptPluginDoesNotCompile() throws IOException {
        Path dir = createProject();
        append(dir.resolve("buildSrc/src/main/kotlin/demo.gradle.kts"), """
            this does not compile
            """);

        BuildResult result = runner(dir).withArguments("help", "--parallel").buildAndFail();
        assertTrue(result.getOutput().contains("demo.gradle.kts"));

        assertProjectDirectoryCanBeDeleted(dir, true);
    }

    @RepeatedTest(5)
    void brokenJarOnBuildSrcClasspath() throws IOException {
        Path dir = createProject();
        // Not a zip file, so reading plugin entries from it throws while the cache computes the entry
        write(dir.resolve("buildSrc/libs/broken.jar"), "not a jar");
        append(dir.resolve("buildSrc/build.gradle.kts"), """
            dependencies {
                implementation(files("libs/broken.jar"))
            }
            """);

        // Whether the build fails is not the point here, only whether the cache is released
        BuildResult result = runner(dir).withArguments("help", "--parallel").run();
        System.out.println("Broken jar build " + (result.getOutput().contains("BUILD SUCCESSFUL") ? "succeeded" : "failed"));

        assertProjectDirectoryCanBeDeleted(dir, false);
    }

    @RepeatedTest(5)
    void brokenJarOnBuildSrcClasspathInMinimalBuild() throws IOException {
        Path dir = Files.createTempDirectory("plugin-entries-lock");
        write(dir.resolve("settings.gradle.kts"), """
            rootProject.name = "demo-build"
            """);
        write(dir.resolve("buildSrc/build.gradle.kts"), """
            plugins {
                `kotlin-dsl`
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation(files("libs/broken.jar"))
            }
            """);
        write(dir.resolve("buildSrc/libs/broken.jar"), "not a jar");
        write(dir.resolve("buildSrc/src/main/kotlin/demo.gradle.kts"), """
            println("demo applied")
            """);

        BuildResult result = runner(dir).withArguments("help").buildAndFail();
        assertTrue(result.getOutput().contains("Failed to extract plugin metadata"));

        assertProjectDirectoryCanBeDeleted(dir, false);
    }

    private static Path createProject() throws IOException {
        Path dir = Files.createTempDirectory("plugin-entries-lock");

        write(dir.resolve("settings.gradle.kts"), """
            rootProject.name = "demo-build"
            include(%s)
            """.formatted(SUBPROJECTS.stream().map(name -> '"' + name + '"').collect(Collectors.joining(", "))));
        write(dir.resolve("buildSrc/build.gradle.kts"), """
            plugins {
                `kotlin-dsl`
            }
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
            dependencies {
                // Puts plugin jars with real plugin descriptors on the classpath scanned by the cache
                implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
                implementation("org.jetbrains.kotlin:kotlin-serialization:2.2.0")
                implementation("org.jetbrains.kotlin:kotlin-allopen:2.2.0")
                implementation("org.jetbrains.kotlin:kotlin-noarg:2.2.0")
                implementation("org.jetbrains.kotlin:kotlin-power-assert:2.2.0")
                implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.0.0")
                implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.1")
                implementation("org.jetbrains.kotlinx:binary-compatibility-validator:0.17.0")
                implementation("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.29.0")
                implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.2.0-2.0.2")
                implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
                implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.1")
                implementation("com.vanniktech:gradle-maven-publish-plugin:0.33.0")
            }
            """);
        write(dir.resolve("buildSrc/src/main/kotlin/demo.gradle.kts"), """
            println("demo applied")
            """);
        write(dir.resolve("build.gradle.kts"), """
            plugins {
                id("demo")
            }
            """);
        for (String project : SUBPROJECTS) {
            write(dir.resolve(project + "/build.gradle.kts"), """
                plugins {
                    id("demo")
                    kotlin("jvm")
                    id("org.jetbrains.kotlinx.kover")
                }
                """);
        }
        return dir;
    }

    private static void assertProjectDirectoryCanBeDeleted(Path dir, boolean cacheExpected) throws IOException {
        // The cache was added in Gradle 9.5.0
        if (cacheExpected && GradleVersion.version(gradleVersion()).compareTo(GradleVersion.version("9.5.0")) >= 0) {
            Path cacheDir = dir.resolve(".gradle/" + gradleVersion() + "/kotlin-dsl-plugin-entries");
            assertTrue(Files.isRegularFile(cacheDir.resolve("jar-entries.bin")), "Plugin entries cache was not used");
        }

        // An OS file lock still held by the daemon fails on any OS, not only on Windows
        List<Path> heldLocks = locksHeldByOtherProcesses(dir.resolve(".gradle"));
        assertTrue(heldLocks.isEmpty(), "Locks still held after the build: " + heldLocks);

        List<Path> leftovers = deleteRecursively(dir);
        assertTrue(leftovers.isEmpty(), "Could not delete: " + leftovers);
    }

    private static GradleRunner runner(Path dir) {
        GradleRunner runner = GradleRunner.create().withProjectDir(dir.toFile()).forwardOutput();
        String installation = System.getProperty("testGradleInstallation");
        if (installation != null) {
            return runner.withGradleInstallation(new File(installation));
        }
        return runner.withGradleVersion(gradleVersion());
    }

    /**
     * The Gradle version run by TestKit. With -PtestGradleInstallation, set -PtestGradleVersion to match it.
     */
    private static String gradleVersion() {
        String version = System.getProperty("testGradleVersion");
        return version != null ? version : GradleVersion.current().getVersion();
    }

    private static List<Path> locksHeldByOtherProcesses(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".lock"))
                .filter(PluginEntriesLockTest::isLockHeld)
                .collect(Collectors.toList());
        }
    }

    private static boolean isLockHeld(Path lockFile) {
        try (FileChannel channel = FileChannel.open(lockFile, READ, WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                return true;
            }
            lock.release();
            return false;
        } catch (IOException e) {
            // On Windows, a file held open by another process cannot be opened at all
            System.err.println(e);
            return true;
        }
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes());
    }

    private static void append(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(), StandardOpenOption.APPEND);
    }

    /**
     * Deletes everything it can and returns the paths that could not be deleted.
     */
    private static List<Path> deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.sorted(Comparator.reverseOrder())
                .filter(path -> {
                    try {
                        Files.delete(path);
                        return false;
                    } catch (FileSystemException e) {
                        System.err.println(e);
                        return true;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                // Parent directories fail as "not empty"; report only the locked files
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
        }
    }
}
