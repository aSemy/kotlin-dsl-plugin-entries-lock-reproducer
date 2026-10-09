# `kotlin-dsl-plugin-entries` lock reproducer

Reproduces the `kotlin-dsl-plugin-entries` cache files staying open in the daemon after a TestKit build.

`PluginEntriesLockTest` builds a project whose `buildSrc` contains a precompiled script plugin, then deletes the project directory.
The daemon writes to this cache from a background worker, which holds the files open for up to 200ms after the last write.
Nothing waits for that worker before the build result is returned.
On Windows, a delete that lands in that window fails on:

```
.gradle/<version>/kotlin-dsl-plugin-entries/kotlin-dsl-plugin-entries.lock
.gradle/<version>/kotlin-dsl-plugin-entries/jar-entries.bin
```

The failure depends on timing, so the test repeats 20 times, each with a fresh project directory.
Linux and macOS allow deleting open files, so the test passes there.

## Running

```
./gradlew test                                  # TestKit runs Gradle 9.8.1 (the wrapper version)
./gradlew test -PtestGradleVersion=9.4.0        # before the cache was added
./gradlew test -PtestGradleInstallation=<dir>   # a locally built distribution
```

The workflow in `.github/workflows/reproduce.yml` runs the test on `windows-latest` and `ubuntu-latest`, against Gradle 9.4.0 (before the cache was added in 9.5.0) and 9.8.1.
Only Gradle 9.8.1 on Windows is expected to fail.
GitHub only picks it up when this directory is the root of its own repository.
