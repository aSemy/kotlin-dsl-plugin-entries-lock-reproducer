# `kotlin-dsl-plugin-entries` lock reproducer

Reproduces the `kotlin-dsl-plugin-entries` cache files staying open in the daemon after a TestKit build.

The issue reports that on Windows, deleting the project directory after the build fails on:

```
.gradle/<version>/kotlin-dsl-plugin-entries/kotlin-dsl-plugin-entries.lock
.gradle/<version>/kotlin-dsl-plugin-entries/jar-entries.bin
```

## Cause

The daemon writes to this cache from a background worker.
After its last write, the worker keeps the cache lock, and both files, for another 200ms in case more writes arrive.
Gradle never closes the cache at the end of the build, so nothing waits for the worker before the build result is returned.

If the build finishes within that window, TestKit returns while the files are still held.
On Windows the delete then fails.
A build that fails right after the cache step hits the window every time.
A build that keeps running after the cache step usually doesn't.

## Tests

`PluginEntriesLockTest` builds projects whose `buildSrc` applies `kotlin-dsl` and contains a precompiled script plugin.
After each build, it:

1. checks that the plugin entries cache was used (Gradle 9.5.0 and later)
2. tries to take each `.lock` file under `.gradle` with `FileChannel.tryLock()`.
   A lock still held by the daemon fails this on any OS, not only on Windows.
3. deletes the project directory.
   Linux and macOS allow deleting open files, so this step only fails on Windows.

| Test                                                                                                                                      | Scenario                                                                                     | Gradle 9.8.1  |
|-------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|---------------|
| `brokenJarOnBuildSrcClasspathInMinimalBuild`                                                                                              | a jar on the buildSrc classpath can't be read, so the build fails right after the cache step | lock held     |
| `brokenJarOnBuildSrcClasspath`                                                                                                            | the same, with many Kotlin ecosystem plugins and 10 subprojects                              | lock held     |
| `successfulBuild`, `taskFails`, `subprojectConfigurationFails`, `subprojectScriptDoesNotCompile`, `precompiledScriptPluginDoesNotCompile` | the build keeps running after the cache step                                                 | lock released |

Each test is repeated, and tests run 4 at a time (see `junit-platform.properties`), each with a fresh project directory.

## Running

- `./gradlew test` - TestKit runs the wrapper's Gradle version
- `./gradlew test -PtestGradleVersion=9.8.1` - a released version
- `./gradlew test -PtestGradleInstallation=<dir>` - a locally built distribution

The workflow in `.github/workflows/reproduce.yml` runs the tests on `windows-latest` and `ubuntu-latest`, against Gradle 9.4.1 (before the cache was added in 9.5.0) and 9.8.1.
GitHub only picks it up when this directory is the root of its own repository.
