# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Khai RC: a personal Android home-control app (Kotlin, Jetpack Compose, Material 3). It controls devices over phone IR, Wake-on-LAN, Tuya Cloud (Rạng Đông OC09 power strip), and a self-built ESP32-S3 Hub reached over pinned HTTPS. User-facing strings, README and docs are in Vietnamese. Keep new UI text in Vietnamese to match.

The project values stable control and minimal changes. A change to one transport must not alter the behavior of modules that already work on real hardware (IR fan/Daikin, WOL, Tuya).

## Commands

The environment is Windows (PowerShell). Use the Gradle wrapper:

```powershell
.\gradlew.bat :app:assembleDebug                      # APK -> app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :feature:tuya:testDebugUnitTest
.\gradlew.bat :feature:ac:testDebugUnitTest
.\gradlew.bat :core:network:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest
# Single test class / method
.\gradlew.bat :core:network:testDebugUnitTest --tests "com.khaiit.kremote.core.network.HubHttpsToshibaControlsClientTest"
.\gradlew.bat :feature:ac:testDebugUnitTest --tests "*ToshibaAcEncoderTest.someMethod"
git diff --check                                       # run before committing
```

If an AGP task name differs, list tasks with `.\gradlew.bat :<module>:tasks --all` instead of editing Gradle by guesswork. Versions come from `gradle/libs.versions.toml` (AGP 9.x, Kotlin 2.2, compile/target SDK 37, minSdk 26, Java 11). Unit tests are plain JUnit 4 with hand-written fakes. There is no mocking library.

## Architecture

Gradle modules (registered in `settings.gradle.kts`):

- `:core:ir`: `IrTransmitter` interface plus `AndroidIrTransmitter` (ConsumerIrManager).
- `:core:network`: Wake-on-LAN sender plus all ESP32 Hub clients (`HubClient` for plain HTTP on LAN, and the `HubHttps*` clients).
- `:feature:fan`, `:feature:ac` (Daikin + Toshiba IR encoders/controllers/cards), `:feature:wol`, `:feature:tuya`: these are self-contained. Each has a Controller, a Compose `*DeviceCard`, and a settings/state store.
- `:app`: `MainActivity` is the composition root. It constructs every controller manually (no DI framework) and passes them to `HomeScreen`, which has tabs (Tất cả / IR / Mạng) and only decides which cards to show. Hub-related controllers and cards live directly in `:app` (package `com.khaiit.kremote`), not in a feature module.

Common patterns:

- Controllers expose Compose state (`var state by mutableStateOf(...)`, `private set`). Async controllers own a `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` and are `AutoCloseable`; `MainActivity.onDestroy` closes them. Refresh-on-foreground is triggered from `MainActivity.onStart`.
- Network/cloud APIs are `fun interface`s or interfaces (`ToshibaHttpsControlsApi`, `MilifeReadApi`, `HubConfigStore`, `ToshibaAcStateStore`, etc.) so controllers can be unit-tested with fakes.
- Results are sealed types that separate **Sent/Verified**, **Rejected** (server explicitly refused, so nothing happened), and **Uncertain** (timeout, bad response, or redirect, so the command may or may not have executed).

### ESP32 Hub (HTTPS)

- The Hub endpoint (`HubHttpsEndpoint`: host, port, SHA-256 cert fingerprint) is stored in `HttpsProbePreferences` (`HubHttpsProbeCard.kt`). The API key and LAN IPv4 (`HubConfig`) are stored separately, encrypted in `HubSettingsStore` (Android Keystore AES-GCM, prefs `hub_secure_v1`).
- TLS trusts only the user-pinned self-signed leaf via `PinnedSelfSignedTrustManager`. The default hostname verifier stays on. Never add trust-all managers or permissive `HostnameVerifier`s, never fall back to HTTP, and never follow redirects.
- Toshiba AC in the UI is currently controlled through the Hub (`ToshibaHttpsControlsController` → `POST /api/v1/commands`), using a fixed command vocabulary built as literal JSON (no user input enters request bodies). The phone-IR `ToshibaAcDeviceCard` still exists in `:feature:ac`, but `HomeScreen` doesn't show it. Both share `ToshibaAcSettings` as the state store.
- The plain-HTTP LAN `HubClient` (`core/network/HubClient.kt`) has no UI wired to it any more. It is still used by its unit tests and by `HubIntegrationTest`, and `HubConfig`/`HubEndpoint` defined there are used everywhere.
- MiLife is read-only (a single GET for status).
- See `docs/HUB_HTTPS_ANDROID_STAGE1.md` for the probe contract (`GET /api/v1/health`, expected JSON fields) and how to compute the fingerprint.

### Tuya OC09

Flow: `TuyaOc09DeviceCard` → `TuyaOc09Controller` → `TuyaCloudOc09Repository` (REST, HMAC-SHA256 signing in `TuyaRequestSigner`, Singapore endpoint) → Tuya Cloud.

- Physical outlet → DP mapping is reversed and has been verified on the real device: outlets 1–5 = `switch_5`..`switch_1`, USB = `switch_6`. Master power is one POST containing all six switches. There is no `switch` DP.
- Credentials are encrypted in `TuyaCredentialStore` (prefs `tuya_oc09_secure`). Custom outlet labels are kept separately in `TuyaOc09LabelStore` (`tuya_oc09_labels`). The access token is cached in RAM only.

## Invariants (follow when changing control code)

- **One deliberate press = at most one command.** Never auto-retry POST/IR commands. Only read-only GETs may retry (Tuya: one retry after token codes `1010/1011/1012/1400`).
- **Don't report success from a POST alone.** Tuya re-reads cloud state to verify. Hub/IR state is an *estimate*, and the UI labels it that way. On an Uncertain result, the Hub Toshiba controller sets `needsManualCheck` and blocks further commands until the user confirms. Keep this gating.
- Local state is persisted only after a validated acknowledgement.
- Secrets (Tuya client ID/secret/device ID, Hub API key) are never hardcoded, never pre-filled in settings dialogs, and are redacted in `toString()`. There is a test that checks this (`TuyaCredentialPrivacyTest`). Encrypted prefs are excluded in `backup_rules.xml` and `data_extraction_rules.xml`. Update those files whenever you add a new secure prefs file.
- Network calls check `ACCESS_LOCAL_NETWORK` on SDK ≥ 37 (see the `networkAllowed` lambdas in `MainActivity`).
