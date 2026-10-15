# Ecosystem Android: app guide and work record

**Workspace snapshot:** 15 October 2026
**Project:** Ecosystem personal device link, based on KDE Connect for Android

This document describes the app as it exists in the current checkout and records the product and implementation work completed in this collaboration. It is a snapshot, not a claim that every brainstormed feature is implemented or that every local change has been committed. For the connection test procedure, see [Connection Lab](connection-lab.md). For the mockup screen behavior, see [Remaining mockup screens](mockup-implementation.md).

## 1. What the app does

Ecosystem links a phone, tablet, and other paired devices. It discovers nearby peers, securely pairs them, maintains a background connection, and exposes features through the KDE Connect packet/plugin system. The Android app retains the mature KDE Connect features and adds Ecosystem-specific transport fallback, reliability tools, background setup, clipboard sync, device controls, scenes, and mockup-driven screens.

The primary experience is:

1. Add or discover another device.
2. Approve pairing on both devices.
3. Open its dashboard to see connection state and available features.
4. Use the feature cards, device menu, share sheet, or navigation bar to send content or control the paired device.

Connection and control features require the other device to be paired. New Ecosystem control and mirror actions require an updated Ecosystem app on both ends. Standard KDE Connect features can still interoperate with compatible KDE Connect peers when the peer advertises those plugins.

## 2. App identity and build

| Setting | Current value |
|---|---|
| Android namespace | `org.kde.kdeconnect_tp` |
| Release application ID | `com.ecosystem.personal` |
| Debug application ID | `com.ecosystem.personal.debug` |
| Version | `1.36.0` (`versionCode` 13600) |
| Minimum Android API | 23 |
| Compile / target API | 37 / 37 |
| Bytecode target | Java 11 |
| UI and app technologies | Kotlin, Jetpack Compose, ViewBinding, Fragments, AIDL, KSP |

The project uses the Android Gradle Plugin and Kotlin versions pinned in `gradle/libs.versions.toml`. This checkout was built with the bundled/local JDK 21 while the app continues to target Java 11 bytecode. The `debug` build has a separate package ID and a `-debug` version suffix.

## 3. Product surfaces

### Pairing, devices, and navigation

- Device discovery and the two-sided pairing flow are provided by the KDE Connect core.
- The main activity hosts the selected device or a pairing/settings/share screen.
- Phone layouts use the compact bottom navigation. Tablet layouts keep the device drawer/sidebar and use a wider content area.
- Device dashboards expose plugin cards and shortcuts for Clipboard, Notifications, Share, Media, Connection, Find and control, Scenes, Mirror, Desk mode, and Touchpad where supported.
- Settings are grouped into device, connection, app, and advanced sections. Advanced settings still lead to the underlying KDE Connect settings screens.
- Feature screens use directional fade/slide transitions. They enter with a short 260 ms movement and fade; returning to a device screen reverses the direction. The first screen on app launch does not animate.

### Connection and fallback

The app can keep several eligible links to the same peer and chooses a route by priority. Current route order is:

| Link | Priority | Role and current limits |
|---|---:|---|
| Wi-Fi / LAN | 500 | Primary local route. Uses KDE Connect LAN discovery and TLS links. |
| Wi-Fi Direct | 400 | Nearby fallback using a peer-to-peer group; Android nearby Wi-Fi permissions may be required. |
| Private hotspot | 300 | Shizuku-assisted hotspot route for compatible devices. Requires Shizuku authorization and device support. |
| Bluetooth | 200 | Small-packet fallback. Bulk/payload packets are deferred until a faster route returns; eligible small packets are limited to 16 KiB. |
| Tailscale | 100 | LAN protocol over a configured tailnet address. Peer addresses and tailnet access must be set up by the user. |
| Other | 0 | Generic fallback route. |

Automatic selection prefers the best currently available link. Per-device manual selection is also available. The Connection screen shows actual available/active transports and reports measured echo round-trip time; it does not invent throughput values for links it has not tested. Reconnection is rate-limited, route changes and disconnect causes are logged, and large transfers over a mobile-data Tailscale route are blocked in Travel mode when they exceed 10 MiB.

### Existing KDE Connect features retained

The project continues to include the upstream plugin families present under `src/main/java/org/kde/kdeconnect/plugins/`:

- Battery and connectivity reporting.
- Clipboard transfer, contacts, and notifications.
- Find My Phone and remote-device discovery.
- Input devices, mouse/touchpad, remote keyboard, digitizer, and presentation controls.
- Media player control and media receiver.
- Run Command, SFTP, file/URL sharing, SMS, and telephony/call events.
- System volume and notification forwarding.

Feature availability depends on the peer, Android version, permissions, enabled plugins, and (for some actions) OEM restrictions.

### Sharing and files

The existing share flow accepts text, links, and files through the Android share sheet and file picker, supports transfer progress/history and offline URL queuing, and sends to a selected paired device. The upstream SFTP plugin provides remote file browsing where configured. Drag-and-drop UI and a shared filesystem folder are still backlog items; a file picker or SFTP connection is not the same as a shared folder.

### Clipboard

The current clipboard work includes:

- Background text synchronization in both directions through the Shizuku clipboard watcher, with a manual send path when automatic watching is unavailable.
- Trusted-device/network checks, sensitive-content filtering, blank/echo rejection, a deduplicated delivery queue, retry throttling, and delivery acknowledgements.
- A persistent text history with deduplication, pinned snippets, copy/send/delete actions, and up to 100 recent unpinned items. Pinned items are retained separately.
- A global sync toggle and a one-hour pause that survives process restarts.
- Saved snippets through the clipboard screen.

History intentionally stores text only. Image/file history, per-app rules, undo-send, automatic clear, typing-based paste, and smart actions such as opening a link in Maps are not implemented in this pass.

### Notifications and messages

The new Notifications screen can opt into receiving notifications, group them by app in Digest mode, show individual notifications, send text and quick replies using the notification's reply token, and dismiss a remote notification. Its in-app feed is bounded to 200 entries and lives in memory. Android notification permission is required on Android 13 and newer. Existing KDE Connect notification, SMS, and telephony plugins remain available separately; the new reply UI does not replace those plugins.

### Find and control

The Find and control screen exposes current battery status, ring, wake/lock, screenshot, volume, app launching, and last connection information. Actions use the peer's Ecosystem control plugin, accessibility service, or Shizuku user service as appropriate. The “last seen” display is the last connection timestamp/log; it is not GPS location tracking.

### Mirror and remote input

Mirror is a consent-based, source-side screen share. The source shows a foreground notification with a stop action. The viewer supports normalized tap/swipe input, back/home/recent controls, rotation, keyboard entry, optional playback-audio capture, and optional physical screen-off through Shizuku. Projection consent is always handled on the source device.

The current implementation is a native JPEG preview limited to 720 pixels wide and up to five frames per second. It is a practical prototype, not the full scrcpy/H.264 60-fps pipeline. Audio capture follows Android's playback-capture policy and may be unavailable for protected or opted-out media. Display power, app launch, rotation, and audio behavior vary by Android release and OEM.

The Touchpad screen reuses the app's established mouse gesture and keyboard input engines. Its touchpad/keyboard tabs and left/right click controls send input to the selected peer. Remote input on the receiving device requires its accessibility service or remote keyboard configuration.

### Scenes and Desk mode

The current scene set is Work, Movie, Sleep, and Travel. A scene applies the supported combination of focus/DND, dark appearance, media volume, brightness, transfer policy, and reconnect behavior locally and sends the scene request to the paired device. Each device checks its own permission state. Sleep can be scheduled for 23:00 with an exact alarm; Work can trigger when a paired tablet becomes reachable over a local route. Automations are subject to Android alarm, DND, write-settings, and background restrictions.

Desk mode is a tablet-oriented dashboard with live time/date, connection and battery status, media/artwork/play-pause, notification summary, and ring action. It expands to the tablet window and hides the side drawer while active.

## 4. Background reliability and diagnostics

`BackgroundService` and the KDE Connect core maintain discovery and paired links. Additional work in this checkout includes:

- A Keep Running preference and background keep-alive support, including battery-optimization guidance and companion-device support on eligible Android versions.
- Companion pairing/presence hooks and Shizuku user services for operations Android does not expose to an ordinary app.
- A health check for connection, notification, clipboard, Shizuku, companion, battery-exemption, and selected OEM background settings.
- A plain-language connection/disconnection/switch log, plus retry/recovery handling.
- A Connection Lab screen and `scripts/connection-lab.sh` two-device USB test harness. It records connection notice/recovery, selected route, clipboard pending/lost/duplicate counts, delay, transfer throughput, battery change, and route-switch reason in TSV/logcat output.

Connection Lab scenarios and prerequisites are documented in [docs/connection-lab.md](connection-lab.md). The harness can automate some Wi-Fi toggles, but router cycling, moving devices, guest-network setup, and several airplane-mode steps remain manual.

## 5. Screen map

| Screen | Entry point | Main behavior |
|---|---|---|
| Device dashboard | Select a paired device | Connection graphic, plugin cards, and device actions. |
| Clipboard history | Dashboard Clipboard card or device menu | Sync status/pause, pinned and recent text, snippets, copy/send/delete. |
| Notifications | Dashboard Notifications card or phone navigation | All/Digest views, quick/custom reply, dismiss. |
| Connection | Tap connection graphic or menu | Available/active routes, automatic/manual preference, rediscovery, latency ping. |
| Find and control | Device menu | Battery, ring, lock/wake, screenshot, volume, apps, last-seen time. |
| Scenes | Device menu or phone navigation | Work/Movie/Sleep/Travel selection and supported automations. |
| Mirror | Device menu | Ask the source for permission, then view/control its screen. |
| Desk mode | Device menu | Tablet dashboard for time, media, notifications, battery, and ring. |
| Touchpad | Device menu | Existing gesture engine with mockup-matched touchpad and keyboard tabs. |
| Health / Disconnect log / Shizuku / Connection Lab | Settings/background tools | Setup, troubleshooting, and diagnostics. |
| Share | Share sheet, navigation, or device card | Send links, text, and files with transfer progress/history. |

The new screens share neutral light/dark surfaces, grouped rows, pill buttons, outline icons, and responsive columns. System bars, Android font scaling, and permission prompts still follow the device configuration, so exact pixel placement varies by device and OS.

## 6. Architecture and source map

```text
src/main/java/org/kde/kdeconnect/
  BackgroundService.kt, KdeConnect.kt, Device.kt, NetworkPacket.kt
  backends/                 link providers, route selection, discovery
  plugins/                  KDE Connect plugin implementations
    clipboard/               clipboard protocol, watcher, history
    ecosystem/               Ecosystem control and mirror protocol/services
    receivenotifications/    incoming notification capture/reply feed
  ui/
    MainActivity.kt          app shell and fragment navigation
    DeviceFragment.kt        selected-device dashboard/actions
    EcosystemSettingsFragment.kt
    features/                clipboard, notifications, connection, control,
                             scenes, mirror, and desk Compose screens
    background/              health, keep-alive, Shizuku, logs, lab, recovery
    compose/                 shared and upstream Compose screens
src/main/res/                 strings, icons, layouts, and screen animations
src/main/aidl/                typed Shizuku/user-service interfaces
src/test/                     unit and Robolectric tests
Graphics/Mockups/              phone/tablet light and dark designs
docs/                          project guides and test procedures
scripts/                       Connection Lab command-line harness
```

The app shell and device dashboard combine Fragments, Compose, and existing View-based feature activities. `Device` owns peer/link state and packet routing. `KdeConnect` coordinates the manager, plugins, and lifecycle. Plugins declare supported packet types and perform feature-level work. AIDL interfaces are used for narrowly scoped Shizuku services; the remote-control protocol uses typed actions rather than arbitrary command execution.

The Ecosystem plugin currently defines paired-device request/response messages for status, ping, volume, accessibility navigation, locking, apps, launch/text entry, scenes, screenshots, and mirror controls. Mirror frames and audio use separate packet types. The source validates pairing and consent, bounds frame size/dimensions, and stops capture/audio on revocation or disconnect.

## 7. Android permissions and setup

Permissions are requested when the related feature is enabled, rather than being equivalent to automatic access. Common setup points include:

- **Nearby devices / Wi-Fi and Bluetooth:** discovery, connection, and fallback transports.
- **Notifications:** notification forwarding/receiving and Android 13+ notification display.
- **Accessibility or remote keyboard:** remote input, screenshots/actions, and some control operations.
- **Shizuku:** hotspot, clipboard background watcher, and selected display/power operations. Shizuku must be installed/launched and the user service authorized.
- **Media projection:** the source user must approve each mirror session. Optional audio capture may ask for microphone/audio capture permission.
- **DND access, write-settings, exact alarm:** scene actions and Sleep scheduling.
- **Battery unrestricted / companion setup:** improved background reliability; actual behavior remains OEM-dependent.
- **SMS, contacts, calls, storage, camera, location:** only for the corresponding upstream/plugin feature.

The manifest declares platform capabilities and permissions; runtime grants, device-owner policies, OEM controls, and Android version determine what is usable. See the in-app Health screen and Shizuku setup guide for device-specific checks.

## 8. Implementation record

This collaboration has covered both the existing transport/reliability work in the checkout and the newer UI/features. The following work is present in the current source tree:

### Base app and reliability work

- Rebranded the Android application ID to `com.ecosystem.personal` (debug suffix: `.debug`) while retaining the KDE Connect namespace and protocol foundation.
- Added prioritized transport selection, automatic/manual per-device route handling, Tailscale route classification, Wi-Fi Direct fallback, Shizuku hotspot fallback, and a small-packet Bluetooth guard.
- Added link-switch/disconnect diagnostics, connection recovery throttling, device statistics, and Connection Lab instrumentation/scripts.
- Added Shizuku setup and health checks, companion/background keep-alive support, and user-facing background troubleshooting.
- Updated the phone navigation bar, tablet/sidebar behavior, settings entry points, share/media/dashboard presentation, and feature icons/mockup assets.
- Added text clipboard sync safeguards, queuing/acknowledgements, history, pinning, snippets, and pause controls.
- Added notification history/feed handling, reply and dismiss paths, and mockup screens for the new feature set.
- Added Ecosystem peer controls, mirror consent/service, scenes, tablet Desk mode, remote touchpad layout, and screen navigation transitions.

### Mockup screens completed in this pass

- Phone: Clipboard history, Notifications, Connection, Find and control, Scenes, and Touchpad.
- Tablet: Mirror and Desk mode.
- Navigation routes were added from device cards, the connection graphic, the device overflow menu, and phone bottom navigation where appropriate.
- Screen controls are connected to the relevant plugin/device APIs and permission flows. A control that depends on Shizuku, accessibility, media projection, DND, or Android runtime permission reports or requests that dependency.
- Feature replacement transitions use short directional fade/slide/scale animations, with reverse direction when returning to the device dashboard.

### Quick Settings and Cast follow-up — 2026-10-15

- Added the **Fresh connection** Quick Settings tile and Health/device-menu entry. It clears transient connection and clipboard delivery state, restarts Shizuku-backed services, reconnects paired devices, and preserves pairing, trusted devices, clipboard history, pins, and preferences. This tile is committed as `e504d3be`.
- Added the **Cast** Quick Settings tile, a phone-side two-way picker, and a separate resizable mirror window with touch, swipe/scroll, back, home, recents, rotation, keyboard entry, and stop controls.
- The Pad can ask a phone to share using the existing mirror request, consent activity, foreground notification, and frame/audio packet types. Phone-originated casts use a new optional `castWindow` control action to open the Pad viewer; the existing mirror frame protocol and pairing flow are unchanged.
- Cast tries Shizuku shell freeform launch first, records normal-resizable fallback status in Health, and posts a high-priority tap-to-open notification when an incoming request cannot open a window from the background. Window bounds are saved for the next launch. A viewer heartbeat stops capture after the viewer disappears, and a stale stream closes its window.
- Health includes tile setup instructions and reports freeform fallback. Android 13+ can show the system add-tile prompt; older releases show manual Quick Settings instructions.
- Verification on 2026-10-15: `assembleDebug` and `testDebugUnitTest` passed. The debug APK installed over USB on the OnePlus. The phone was dozing and the Pad was not attached, so live UI, pairing, QS tile, mirroring, clipboard, and tablet tests were not completed; no wake/lock command was sent.

### Brainstormed feature status

“Implemented” below means the relevant feature exists in this checkout; it does not guarantee identical behavior on every Android/OEM version. “Partial” means a useful core or prerequisite exists, while the complete idea is still outstanding.

| Area | Implemented or partially implemented | Still planned / outside this pass |
|---|---|---|
| Connection | Wi-Fi first; Wi-Fi Direct, hotspot, and Bluetooth fallbacks; manual/automatic links; active link display; latency ping/test; Companion support; Tailscale addresses; Travel-mode large-transfer guard. | NFC/QR pairing flow; automatic away-from-home VPN setup; richer travel automation and route testing across every transport in one guided run. |
| Clipboard | Background text sync both ways; text history; pinning; snippets; sync toggle; pause for one hour; sensitive/duplicate filtering. | Image/file history, per-app rules, quick tile, undo-send, smart paste, URL cleanup/plain-text mode controls, shortcuts, auto-clear, typing paste, OTP workflow. |
| Mirroring/control | Consent-based screen mirror; floating/resizable Cast window with optional Shizuku freeform launch; touch/swipes/scroll, back/home/recent, keyboard entry, optional phone audio, screenshot, volume, app launch, ring, wake/lock, Shizuku screen power/rotation. | scrcpy-class low-latency video, webcam/camera remote, voice input, polished phone-app launcher and hardware key/volume routing. |
| Sharing/files | Android share sheet, links/text/files, transfer state/history, URL queue, SFTP where enabled. | “Send to Pad” naming/flows beyond current share target, drag-and-drop, shared folder, automatic screenshots/photos, file browser polish, resumable transfer UX, image shrinking, one-time shares, scan-to-PDF/OCR, nightly backup, shared notepad. |
| Notifications/calls/messages | Existing notification/SMS/telephony plugins; new notification inbox, All/Digest, quick/custom reply, dismiss. | Rich app snooze and priority people, digest scheduling, deduped call vibration/video pause policy, complete SMS/call dashboard and call-on-phone flow. |
| Handoff/sync | Existing media remote; device battery/connection state and scene application. | Continue page/video/music handoff, shared dark mode/brightness/night light/wallpaper, Wi-Fi passwords, map destinations, shared timers/reminders, cross-device search. |
| Scenes/automation | Work/Movie/Sleep/Travel scene actions; daily Sleep alarm; nearby local-tablet Work rule; Fresh connection and Cast Quick Settings tiles; widgets/quick settings groundwork only where provided by upstream. | Full place/time/proximity rule builder, shared focus synchronization polish, dedicated widgets/bubble/desk automation, shake/volume/NFC/edge-drop shortcuts. |
| Shizuku | Setup guide, authorization checks, background clipboard/hotspot/display helpers, health diagnostics, freeform Cast window launch. | Reboot auto-start reliability on all OEMs, broad remote Wi-Fi/Bluetooth/airplane controls, APK installer, quick-command editor, Shizuku live log viewer. |
| Find/protect/health | Ring, battery, last connection time, mutual pairing approval, connection log, health checks. | GPS last-seen location, battery sharing beyond current status, near-device unlock, fingerprint app lock, storage/duplicate-cleanup view, weekly summary. |
| Polish | New-device and Shizuku guidance, UI refresh, light/dark mockups, feature icons, accessibility-dependent interactions. | Settings backup/restore, comprehensive accessibility audit, user accent/sound/haptic editor, remaining planned onboarding and polish. |

## 9. Build, install, and verification

Set `JAVA_HOME` to a usable JDK 21 installation if Gradle cannot find Java. The Android project still compiles application code to Java 11 bytecode.

```sh
./gradlew --offline assembleDebug
./gradlew testDebugUnitTest
./gradlew installDebug
```

If Gradle dependencies are not cached, omit `--offline` where network access is available. The debug APK is written to:

```text
build/outputs/apk/debug/kdeconnect-android-debug.apk
```

The focused verification run for the mockup feature work completed 17 tests with no failures: 5 clipboard history tests, 4 remote notification tests, and 8 device tests. The debug APK also built successfully after adding the screen transition animations. Unit tests cover storage/routing behavior; they do not grant Android permissions or fully test projection/audio/OEM background behavior.

The UI was opened on attached phone/tablet devices during the implementation, including the home and clipboard screens. The devices disconnected before the final APK could be installed for a final end-to-end pass. Privileged mirror capture, audio, power controls, and scene permission flows therefore still need device-specific validation.

## 10. Known limits and working-tree notes

- The mirror preview is bounded JPEG at up to 5 fps, not scrcpy/H.264 60 fps.
- Clipboard history stores text only. Last-seen is a connection timestamp, not a location.
- Several controls need a current paired peer, matching Ecosystem protocol support, and Android grants on one or both devices.
- Android and OEM background policies can interrupt keep-alive behavior even when the app's health screen gives the right setup guidance.
- Many brainstorming items in the table are not implemented. This document marks those as planned/partial rather than implying they are available.
- The `screensharing/` directory may contain local user work; do not include it in feature commits unless explicitly requested.
- The legacy `fastlane/Appfile` package name should be checked before a store release because it may still identify the upstream KDE Connect package.

## 11. License and lineage

This is a KDE Connect Android fork. The repository includes the upstream GPL licensing files and third-party dependency notices. Preserve the applicable source and attribution notices when changing or distributing the app. Check `COPYING`, `LICENSES/`, and the generated dependency license report for the exact distribution requirements.
