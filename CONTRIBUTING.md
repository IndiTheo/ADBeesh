# Contributing to ADBeesh

Welcome to ADBeesh! This page will help you set up your environment and contribute to the project.

## Building

The project uses [Bazel](https://bazel.build/) for building. To ensure a consistent build environment, we use **Bazelisk**.

### Setup

1. **Install Bazelisk**: Bazelisk is a wrapper for Bazel that automatically downloads and uses the version specified in the `.bazelversion` file.
   * Follow the [official Bazelisk installation instructions](https://github.com/bazelbuild/bazelisk#installation).
   * In the directions below, `bazel` is assumed to be an alias to `bazelisk`.
2. **Android SDK & NDK**:
   The project requires the Android SDK and the Android NDK. You have two ways to configure these paths:

   #### Option A: Environment Variables (Global)
   Set these in your shell profile (e.g., `.bashrc`, `.zshrc`):
   ```bash
   export ANDROID_HOME=/path/to/your/Android/Sdk
   export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<version>
   ```

   #### Option B: .bazelrc.local (Project-specific)
   This is the recommended way if you want to avoid global environment variables. Create a `.bazelrc.local` file in the project root:
   ```text
   common --repo_env=ANDROID_HOME=/path/to/your/Android/Sdk
   common --repo_env=ANDROID_NDK_HOME=/path/to/your/Android/Sdk/ndk/<version>
   ```

3. **Build**: Run the following command to build the entire project:
   ```bash
   bazel build //:adbeesh
   ```

> [!NOTE]
> The Android NDK is required for Jetpack Compose compilation.


## Lint & Format

The project uses `ktlint` via Bazel for code style enforcement.

### Run Lint
To check for linting and formatting issues, run:
```bash
bazel run //:lint
```

### Run Format
To automatically fix most formatting issues, run:
```bash
bazel run //:format
```

## Testing

To build and instsall the test application:
```bash
bazel build //:test-app
adb install bazel-bin/test-app/test-app.apk
```

## Documentation

The project uses [Dokka](https://kotlinlang.org/docs/dokka-introduction.html) to generate API documentation.

### Build Docs
To generate the documentation archive locally:
```bash
bazel build //:docs
```
The output will be generated as a ZIP file at `bazel-bin/adbeesh-docs.zip`.

### View Docs
To view the generated documentation in your browser:
```bash
unzip bazel-bin/adbeesh-docs.zip -d build/docs
```
Open build/docs/index.html in your preferred browser

## Dependency Management

The project uses `rules_jvm_external` with Bzlmod for managing Maven dependencies. To ensure hermetic and reproducible builds, all dependencies are pinned in lockfiles.

### Lockfiles

- `adbeesh_maven_install.json`: Contains all runtime dependencies (Android, Kotlin, Security, etc.).
- `dokka_install.json`: Contains dependencies specifically for documentation generation.

### Updating Dependencies (Repinning)

If you modify the `maven.install` blocks in `MODULE.bazel` (e.g., adding a new artifact or changing a version), you must update the corresponding lockfile.

To repin the dependencies, run:

```bash
# Repin the main project dependencies
REPIN=1 bazel run @unpinned_adbeesh_maven//:pin

# Repin the Dokka dependencies
REPIN=1 bazel run @unpinned_dokka//:pin
```

This will download the new dependencies, verify their checksums, and update the JSON lockfile. You should then commit the updated lockfile.
