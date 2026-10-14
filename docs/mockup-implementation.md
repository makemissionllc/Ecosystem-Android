# Remaining mockup screens

The device dashboard opens clipboard history and notifications from their cards. Tap the connection graphic for connection details. The device overflow menu also opens Find and control, Scenes, Mirror, Desk mode, and Use as touchpad.

The UI uses the mockups' neutral light/dark surfaces, pill controls, outline icons, grouped rows, and two-column action cards. Tablet mirroring keeps the sidebar; Desk mode fills the window. Phone notifications and scenes keep the bottom navigation; the other detail screens hide it. Android system bars and accessibility font scaling still follow device settings.

## Implemented behavior

- Clipboard: private persistent text history, deduplication, pinned snippets, copy/send/delete, automatic-sync toggle, and a one-hour pause that survives process restarts. Sensitive clips are excluded. Recent history is limited to 100 entries; pinned items are retained.
- Notifications: live incoming notifications, cancellation, preexisting notification import, per-app digest counts, text replies and quick replies using the sender's reply token, and remote dismissal. Receiving is opt-in and requires notification permission. The in-memory inbox is bounded to 200 entries.
- Touchpad: the existing gesture and keyboard engines now sit inside the mockup layout. The tabs switch between touchpad and keyboard, and the buttons send actual left/right clicks. Android receivers need remote-input accessibility or the remote keyboard enabled.
- Connection: actual available/active transports, persisted automatic selection or manual transport selection, rediscovery, and a measured echo round trip. The test measures the current route; it does not fabricate speed or latency for unavailable links.
- Find and control: live battery, ringing, accessibility-backed lock and screenshot, Shizuku-backed wake and app launching, volume control, app list, and last connection time/log. Android may restrict which apps are launchable.
- Scenes: Work enables focus; Movie enables dark mode and lowers media volume; Sleep enables focus, dark mode, mute, and dimming; Travel enables mobile-data limits and rediscovery/hotspot fallback. The paired device receives the same scene through the new Ecosystem protocol. Each end checks its own permissions. Daily Sleep uses an exact alarm; desk Work triggers when a tablet becomes reachable on a local route. Transfers above 10 MB over a mobile-data Tailscale route are rejected in Travel mode.
- Mirror: screen-sharing approval on the source device, live native screen preview with rotation updates, normalized touch/swipe input, back/home/recent controls, remote rotation, keyboard entry, optional playback audio, Shizuku physical display power, and stop/cleanup. The source has a foreground notification with a stop action. Audio capture follows Android's media-app capture policy. Display power, rotation, and background app launch report failures when the device or permissions do not support them.
- Desk mode: live clock/date, paired-device connection and battery, current media/artwork/play-pause, incoming notification summary, and ring action.

## Limits and verification

Both devices need the updated Android app for the new Ecosystem control and mirror packet types. Existing KDE Connect features remain usable with peers that do not advertise those capabilities.

The mirror implementation currently uses a 720-pixel-wide JPEG preview capped at five frames per second, with a bounded PCM audio queue. It is not a scrcpy/H.264 60-fps implementation. Clipboard image/file history, last-seen location, and the wider brainstormed feature list are outside this mockup implementation.

The debug APK builds successfully. Focused tests cover clipboard retention/deduplication, sensitive and paused delivery rejection, notification updates/cancellation/reply tokens, and existing device behavior. Device checks should exercise the source-side permission flows before treating privileged mirroring and automation as verified on a specific OEM.
