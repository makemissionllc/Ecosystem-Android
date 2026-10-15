# Changelog

This file records user-facing Ecosystem Android features and quality-of-life changes. Platform permissions and peer-version requirements are noted where they affect availability.

## 2026-10-15 — Ecosystem 1.36.0 feature and usability update

### Find, connect, and recover

- Pair nearby devices, add a peer by IP address, inspect the active connection and latency, or choose an available link manually.
- Prefer Wi-Fi and fall back to Wi-Fi Direct, a private Shizuku hotspot, or Bluetooth. Tailscale peers can be added by address.
- Check background, notification, clipboard, battery, companion-device, and Shizuku setup from Health. Review disconnects and link switches in the readable connection log; measure recovery and transfer behavior with Connection Lab.
- Use Fresh connection from Quick Settings to clear transient recovery state and reconnect paired devices while keeping pairings, preferences, and clipboard history.

### Share and pick up work

- Send files, links, and text from the Android share sheet or Share screen. Review transfer progress and recent transfers, and queue links while a device is offline.
- Sync clipboard text between trusted paired devices when configured. Clipboard history supports recent items, pins, saved snippets, send/copy/delete actions, and a one-hour pause. Sensitive and duplicate content is filtered.
- Review remote notifications in All or Digest view, send quick or custom replies, and dismiss items from the inbox.
- Return to the last feature you used from the selected device dashboard with Continue where you left off.

### Control and customize paired devices

- Use device dashboard actions for media, touchpad and keyboard input, and configured remote commands.
- Find and control a device with ring, battery status, wake/lock, app launch, volume, and screenshot actions when the peer and Android permissions support them.
- Share a screen through a consent-based mirror session. Cast adds Quick Settings entry, a resizable floating viewer where supported, touch and keyboard controls, and an in-app fallback.
- Apply Work, Movie, Sleep, and Travel scenes. Schedule Sleep or enable the nearby-tablet Work automation. Desk mode combines time, media, notification, battery, and ring actions for a tablet.
- Existing plugins also provide Find My Phone, SMS/MMS, contacts, presentation and drawing input, remote keyboard, and SFTP file browsing when supported and configured.
- Change device name, clipboard and notification behavior, auto-connect, background behavior, and appearance in Settings; plugin-specific options remain available in Advanced settings.

### Guidance, layouts, and efficiency

- Settings now includes versioned What’s new notes and a step-by-step interactive tour with direct links into the app’s main features.
- The device dashboard offers a one-tap return to the last feature opened for that device.
- Star devices in the pairing list or device menu to keep your regulars at the top of the pairing list and navigation drawer. Favorites are saved across app restarts.
- Settings content is centered and width-limited on larger windows, and feature screens use a readable maximum width across phones, tablets, and resized windows.
- Feature screens refresh time-sensitive labels only as often as needed, stop refreshing while not visible, and keep the faster status check only on the active mirror screen.
- Discovery and dashboard connection illustrations now settle into still states instead of redrawing continuously, reducing ongoing UI work while keeping motion for direct user actions.
- Device updates now recompose the existing dashboard instead of replacing its whole UI, preserving scroll position and animation state. Re-selecting an already open device, feature, settings, pairing, or about page avoids reloading its fragment, and the device drawer skips rebuilding when its visible device list has not changed.
- The What’s new page and app tour describe these smoother returns and live updates.
- The device discovery step in the app tour now shows how favorites make frequently used devices easier to reach.
- Setting switches now respond across the full row, and longer descriptions wrap at the normal readable text size instead of shrinking on narrow screens or with larger text. The interactive tour now includes the Settings experience.
- Pairing refreshes now update only device data; connectivity and permission status are checked on screen entry or return from system settings instead of on every peer-list update.
- Phone navigation selection now eases between the mockup's active and inactive pill colors, matching the app's short, interaction-driven motion elsewhere.

### Availability notes

- Automatic clipboard watching, private hotspot fallback, and some screen-power or freeform-window actions need Shizuku.
- Remote input and some device controls depend on Accessibility or keyboard setup. Screen mirroring always asks the source device for consent. Scene actions may need DND, write-settings, or exact-alarm access.
- Android version, paired-peer version, device permissions, and manufacturer background restrictions can limit individual features.
