# ADBeesh

**Wireless Debugging pairing workflow for Android apps.**

![ADBeesh Logo Image](assets/images/logo.svg)

ADBeesh is a library that allows an Android app to establish a local ADB connection 
to the device via Wireless Debugging, providing a bridge for privileged command execution as the shell
(2000) user. ADBeesh does the heavy lifting of ADB key management and UI interaction to let your
app simply request a pairing and guide the user to perform the pairing workflow.


## Quick Answers

#### Why does ADBeesh exist?

ADBeesh is simply a convenience layer, a UI workflow that simplifies ADB pairing for 
power-user apps. It exists to simplify a workflow every developer had to implement to get a direct
ADB shell.

#### How is ADBeesh different from Shizuku's ADB?

Shizuku is a frontend app whose ADB mode allows running ADB privileged code. To do so, the user has to pair the app
with the device. ADBeesh implements exactly this step in a generic fashion, allowing developers to make
Shizuku-like apps themselves.

#### How does pairing work?

It's not different from a regular ADB pairing workflow. While a foreground service keeps the app alive, the user
is directed to Wireless Debugging workflow, initiate a pairing and submit the code through a notification.

#### How customizable is it?

The workflow is customizable, meaning the developer can tailor parts of it to their needs, for example get the code in another way,
customize instructions, control the ADB certificate subject, replace notifications with toast pop-ups and more.
While many things are customizable, some are not. The library is opinionated and scoped to do exactly one thing,
meaning you can't launch a space shuttle with it (and you shouldn't). If a feature you need is missing, you're welcome
to open an issue/PR.

---

> [!WARNING]
> Proper security audit of the library hasn't been conducted yet.
> Pairing keys are currently stored in encrypted shared preferences (`androidx.security:security-crypto`)
> which may change in the future. The security of ADB implementation depends on the upstream Kadb project.
> Use at your own risk.

---


## 📖 Documentation

Public API documentation is available [here](https://indidevs.gitlab.io/adbeesh)


## 📦 Add Dependency

[![Maven Central](https://img.shields.io/maven-central/v/com.indidevs.android/adbeesh)](https://central.sonatype.com/artifact/com.indidevs.android/adbeesh)

### Gradle
```kotlin
dependencies {
    implementation("com.indidevs.android:adbeesh:LATEST_VERSION")
}
```

### Maven
Add this to your `pom.xml` dependencies block:

```xml
<dependency>
    <groupId>com.indidevs.android</groupId>
    <artifactId>adbeesh</artifactId>
    <version>LATEST_VERSION</version>
</dependency>
```

### Bazel
Load the `maven` package rules in `MODULE.bazel` and specify the artifact:

```py
bazel_dep(name = "rules_jvm_external", version = "X.Y")
maven = use_extension("@rules_jvm_external//:extensions.bzl", "maven")
maven.install(
    name = "maven", 
    artifacts = [
        "com.indidevs.android:adbeesh:LATEST_VERSION",
    ],
)
use_repo(maven, "maven")
```

Specify dependencies wherever required:

```py
deps = ["@maven//:com_indidevs_android_adbeesh"],
```

---


## Requirements
- **Android 11+ (API 30)**: Required for Wireless Debugging pairing protocol.
- **Permissions**: ADBeesh requires several permissions (declared in manifest) to handle networking and foreground services.
    - `INTERNET`: For ADB TLS connection.
    - `FOREGROUND_SERVICE`: To keep pairing alive.
    - `FOREGROUND_SERVICE_CONNECTED_DEVICE`: For Android 14+ workflow.
    - `POST_NOTIFICATIONS`: For Android 13+ pairing notification (request at runtime if using `NotificationCodeProvider`).
    - `CHANGE_WIFI_MULTICAST_STATE`: For reliable mDNS discovery.


## Usage

### Register Service (`AndroidManifest.xml`)
ADBeesh uses a foreground service to manage the pairing notification.
```xml
<service
    android:name="com.indidevs.android.adbeesh.pairing.AdbPairingService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false" />
```

### Configuration
The core ADB identity and handshake parameters are configured via `AdbSessionConfig`.

```kotlin
val sessionConfig = AdbSessionConfig.Builder()
    .identityName("MyApp")
    .build()
```

> [!TIP]
> **See docs:**
> - [AdbSessionConfig](https://indidevs.gitlab.io/adbeesh/adbeesh/com.indidevs.android.adbeesh.config/-adb-session-config/index.html): Core settings for the ADB session and cryptographic handshake.

### Notification Customization
ADBeesh uses a foreground service to manage the pairing workflow. You can customize the visual appearance using the `NotificationFlowConfig` DSL.

```kotlin
val flowConfig = NotificationFlowConfig.Builder()
    // Configure the Android Notification Channel
    .channel(id = "my_app_channel", name = "MyApp Pairing")
    // Global Branding: Applies to all stages by default
    .common {
        title = "ADB Pairing MyApp"
        smallIconRes = R.drawable.ic_adb
        settingsButtonText = "OPEN SETTINGS"
    }
    // Overrides: Tweak specific parts of the flow
    .searching {
        text = "Searching for Wireless Debugging..."
    }
    .awaitingCode { update ->
        text = "Device found on port ${update.port}. Enter pairing code:"
        inputLabel = "Wi-Fi pairing code"
    }
    .success {
        title = "Connected!"
        text = "Pairing successful."
    }
    .build()
```

> [!TIP]
> **See docs:**
> - [NotificationFlowConfig](https://indidevs.gitlab.io/adbeesh/adbeesh/com.indidevs.android.adbeesh.notifications/-notification-flow-config/index.html): Visual settings for the pairing notification flow.
> - [NotificationConfig](https://indidevs.gitlab.io/adbeesh/adbeesh/com.indidevs.android.adbeesh.notifications/-notification-config/index.html): Individual notification states (titles, text, and buttons).

### Pairing
A pairing handle represents a persistent identity. Pairings are persisted by an `id` (storage key). If the device is not yet paired, trigger the `AdbPairingWorkflow`.
You can provide your own UI for entering the 6-digit code by implementing `AdbPairingCodeProvider` or handle lifecycle events via `AdbPairingCallback`.

```kotlin
val pairing = AdbPairing.get(context, id = "main", config = sessionConfig)

val workflow = AdbPairingWorkflow.Builder()
    .withCodeProvider(NotificationCodeProvider(context, flowConfig))
    .addCallback(NotificationCallback(context, flowConfig))
    
    // Optional: Add your own custom listeners
    .addCallback(object : AdbPairingCallback {
        override fun onPairingStarted(sessionId: String) {
            println("Started searching for device")
        }
        override fun onPortFound(sessionId: String, port: Int) {
            println("Found candidate port: $port")
        }
    })
    .build()

// In a ViewModel or LifecycleOwner
lifecycleScope.launch {
    try {
        pairing.ensurePaired(workflow)
    } catch (e: AdbException) { }
}
```

> [!TIP]
> **See docs:**
> - [AdbPairingWorkflow](https://indidevs.gitlab.io/adbeesh/adbeesh/com.indidevs.android.adbeesh.pairing/-adb-pairing-workflow/index.html): Orchestrates the pairing process.
> - [AdbPairingCodeProvider](https://indidevs.gitlab.io/adbeesh/adbeesh/com.indidevs.android.adbeesh.pairing/-adb-pairing-code-provider/index.html): Interface for requesting the pairing code.
> - [AdbPairingCallback](https://indidevs.gitlab.io/adbeesh/adbeesh/com.indidevs.android.adbeesh.pairing/-adb-pairing-callback/index.html): Lifecycle events for the pairing process.

### Open a Session
Once paired, you can open a session and execute commands.
The shell execution returns an `AdbShellResult`.
and any library errors (timeouts, auth failures) are thrown as an `AdbException`.

```kotlin
lifecycleScope.launch {
    try {
        pairing.openSession(timeoutMs = 5000).use { session ->
            if (session.isAlive()) {
                val result = session.shell("pm grant com.myapp android.permission.WRITE_SECURE_SETTINGS")
                if (result.isSuccess) {
                    println("Output: ${result.output}")
                }

                // Access underlying Kadb instance for advanced features (push/pull/install)
                val kadb = session.kadb
                kadb.install(apkFile)
            }
        }
    } catch (e: AdbException) { }
}
```

> [!TIP]
> **See docs:**
> - [AdbShellResult](https://indidevs.gitlab.io/adbeesh/adbeesh/com.indidevs.android.adbeesh.runtime/-adb-shell-result/index.html): The results of shell commands, including exit codes and output.
> - [AdbException](https://indidevs.gitlab.io/adbeesh/adbeesh/com.indidevs.android.adbeesh.exceptions/-adb-exception/index.html): Library-specific exceptions.

### Monitor
The pairing lifecycle can be monitored via the `status` Flow, which emits `AdbPairingStatus` values.

```kotlin
lifecycleScope.launch {
    pairing.status.collect { status ->
        when(status) {
            AdbPairingStatus.IDLE           -> println("Ready to start")
            AdbPairingStatus.DISCOVERING    -> println("Searching")
            AdbPairingStatus.AWAITING_CODE  -> println("Enter code in notification")
            AdbPairingStatus.PAIRING        -> println("Handshaking")
            AdbPairingStatus.PAIRED         -> println("Ready!")
            AdbPairingStatus.ERROR          -> println("Something went wrong")
        }
    }
}
```

> [!TIP]
> **See docs:**
> [AdbPairingStatus](https://indidevs.gitlab.io/adbeesh/adbeesh/com.indidevs.android.adbeesh.pairing/-adb-pairing-status/index.html): Enum representing the possible states of a pairing handle.

### Unpair
To forget the identity and delete stored keys:
```kotlin
pairing.unpair()
```


## Advanced Customization

### Custom Code Provider
You can implement your own UI for entering the pairing code by implementing `AdbPairingCodeProvider`
and handle lifecycle events via `AdbPairingCallback`.

```kotlin
class MyUiProvider : AdbPairingCodeProvider {
    override suspend fun provideCode(sessionId: String, port: Int): String? {
        // Show your own dialog/activity and return the 6-digit code
        return getCodeFromUserWithMyUI()
    }
}

class MyUiCallback : AdbPairingCallback {
    override fun onPairingStarted(sessionId: String) {
        // Called when discovery starts
    }

    override fun onPortFound(sessionId: String, port: Int) {
        // Called when a candidate port is found
    }

    override fun onPairingHandshakeStarted(sessionId: String) {
        // Called when the cryptographic handshake starts
    }

    override fun onPairingSuccess(sessionId: String) {
        // Called when pairing succeeds
    }

    override fun onPairingError(sessionId: String, error: String) {
        // Called when pairing fails
    }
}
```

```kotlin
val workflow = AdbPairingWorkflow.Builder()
    .withCodeProvider(MyUiProvider())
    .addCallback(MyUiCallback())
    .build()

lifecycleScope.launch {
    pairing.ensurePaired(workflow)
}
```


## License

ADBeesh is licensed under the [MIT License](LICENSE).

This project uses components licensed under the Apache License 2.0 (including a fork of [Kadb](https://github.com/flyfishxu/Kadb)). 
See the [NOTICE](NOTICE) and [LICENSE-APACHE](LICENSE-APACHE) files for full attribution and license details.


## Acknowledgements

- [flyfishxu / Kadb](https://github.com/flyfishxu/Kadb)

