# `kotlin-dsl-plugin-entries` lock reproducer

On Windows, deleting a TestKit project directory after the build fails on:

```
.gradle/<version>/kotlin-dsl-plugin-entries/kotlin-dsl-plugin-entries.lock
.gradle/<version>/kotlin-dsl-plugin-entries/jar-entries.bin
```

## Cause

The daemon writes to this cache from a background worker.
After its last write, the worker keeps the cache lock, and both files, for another 200ms in case more writes arrive.
Gradle never closes the cache at the end of the build, so nothing waits for the worker before the build result is returned.

If the build finishes within that window, TestKit returns while the files are still held, and on Windows the delete fails.

## Reproducer

`PluginEntriesLockTest` puts a file that isn't a jar on the classpath of a `buildSrc` with a precompiled script plugin.
Reading plugin entries from it fails right after the cache is written, so the build finishes inside the window.
The test then deletes the project directory.

Linux and macOS allow deleting open files, so the test only fails on Windows.

## Running

- `./gradlew test` - TestKit runs the wrapper's Gradle version
- `./gradlew test -PtestGradleVersion=9.8.1` - another Gradle version

The workflow in `.github/workflows/reproduce.yml` runs the test on `windows-latest` against Gradle 9.4.1 (before the cache was added in 9.5.0) and 9.8.1.
