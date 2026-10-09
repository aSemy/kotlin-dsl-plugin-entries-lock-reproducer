package demo;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginEntriesLockTest {

    @Test
    void projectDirectoryCanBeDeletedAfterBuild() throws IOException {
        Path dir = Files.createTempDirectory("plugin-entries-lock");

        write(dir.resolve("settings.gradle.kts"), "rootProject.name = \"demo-build\"\n");
        write(dir.resolve("buildSrc/build.gradle.kts"),
            "plugins {\n" +
            "    `kotlin-dsl`\n" +
            "}\n" +
            "repositories {\n" +
            "    mavenCentral()\n" +
            "}\n");
        write(dir.resolve("buildSrc/src/main/kotlin/demo.gradle.kts"), "println(\"demo applied\")\n");
        write(dir.resolve("build.gradle.kts"),
            "plugins {\n" +
            "    id(\"demo\")\n" +
            "}\n");

        BuildResult result = runner(dir).withArguments("help").build();
        assertTrue(result.getOutput().contains("demo applied"));

        List<Path> leftovers = deleteRecursively(dir);
        assertTrue(leftovers.isEmpty(), "Could not delete: " + leftovers);
    }

    private static GradleRunner runner(Path dir) {
        GradleRunner runner = GradleRunner.create().withProjectDir(dir.toFile()).forwardOutput();
        String installation = System.getProperty("testGradleInstallation");
        String version = System.getProperty("testGradleVersion");
        if (installation != null) {
            return runner.withGradleInstallation(new File(installation));
        }
        return runner.withGradleVersion(version != null ? version : GradleVersion.current().getVersion());
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes());
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
                .collect(java.util.stream.Collectors.toList());
        }
    }
}
