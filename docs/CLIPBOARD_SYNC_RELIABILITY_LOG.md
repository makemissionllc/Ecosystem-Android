# Clipboard sync reliability investigation and change log

This is the prompt-by-prompt record for the OnePlus 15 ↔ Xiaomi Pad 8 Pro clipboard reliability work. Append an entry after each prompt in this task, including code changes, commits, builds, tests, and device findings. The broader app guide is [APP_AND_WORK_LOG.md](APP_AND_WORK_LOG.md).

## 2026-10-15 — Step 1: static investigation

**Scope:** OnePlus 15 sends to Xiaomi Pad 8 Pro (HyperOS 3); Pad-to-phone still works. Read-only review requested before implementing diagnostics or fixes. No app source code was changed and no device state was modified in this step.

### Receive path on the Pad

1. A connected link reads a packet and calls `BaseLink.packetReceived`; `Device.onPacketReceived` increments packet counters and dispatches to the loaded clipboard plugin.
2. `ClipboardPlugin.onPacketReceived` reads the text and delivery ID, then calls `ClipboardSyncManager.applyIncoming`.
3. The manager filters blank/sensitive content and, for Ecosystem IDs, disabled or paused sync. It records history and a durable pending incoming row, then checks delivery IDs, the in-memory content-hash cooldown, and `ClipboardListener.currentContent`.
4. If those checks allow it, `ClipboardListener.setText` updates its in-memory cache and posts `ClipboardManager.setPrimaryClip` to the main thread when needed.
5. The manager marks the delivery ID seen and removes its pending row after `setText` returns. The plugin then sends an acknowledgement for any accepted item with an ID. The actual system clipboard write is not awaited or checked before the acknowledgement.

### Places an item can be dropped or falsely treated as delivered

- **Before plugin dispatch:** a silent/stalled receive socket yields no clipboard packet. The LAN reader continues after `SocketTimeoutException`; it does not track last packet time or disconnect on inactivity. `Device.isReachable` is only `links.isNotEmpty()`, so an idle/half-open link can still look connected. Overlong and empty packet lines are also skipped without a clipboard-specific diagnostic. Unknown packet types and plugin exceptions do log warnings/errors.
- **Plugin filters:** blank text, sensitive content, disabled/paused automatic sync, and stale/unknown connect packets return without an acknowledgement or a reason log. The connect packet with timestamp `0` is explicitly ignored.
- **Duplicate delivery ID:** an ID in the persisted last-500 seen list is treated as accepted, acknowledged, and not written again. The list is size-bounded but has no age expiry.
- **Content-hash cooldown:** the same incoming text within the 5-second `remoteHashes` cooldown is acknowledged without a write. Entries are pruned only when another local/incoming clipboard event checks the map; normally the time window expires, but it uses wall-clock time. A clock rollback can extend the suppression window.
- **Stale in-memory clipboard cache:** `ClipboardListener.currentContent == text` is treated as already applied and acknowledged. That cache is not read back from Android’s system clipboard, so it can be stale if a prior write did not take effect or HyperOS changed the clipboard while the app was suspended.
- **Clipboard write:** `setText` updates `currentContent` before the posted main-thread runnable runs. If the manager is not initialized when the runnable executes, it silently does nothing. If the system write is delayed, suppressed, or throws, the caller has already returned; the incoming row is removed and the sender can receive an ACK anyway. No write-success result is propagated.
- **ACK:** sending the ACK is asynchronous and its result is not inspected. If it is lost, the sender retries; however, the Pad’s seen-ID check can ACK that retry without attempting the clipboard write again.
- **Sender-side filter worth logging:** `last_local_hash` is persistent and has no TTL; the same local text is suppressed until another different local text replaces it. Sender events are also intentionally skipped for blank/sensitive/remote text, disabled/paused sync, inactive Shizuku watcher, disallowed MIME type, or an untrusted Wi-Fi condition.
- **Persistent queue parsing:** malformed persisted JSON falls back to an empty list without a diagnostic, which can hide queued state. The pending incoming queue itself does not block later items.

### Suspect assessment

| Suspect | What the code allows |
|---|---|
| **a) Clipboard write / Shizuku service** | **Yes.** The receive write is not verified before the item is marked complete and ACKed. Shizuku is used for background clipboard observation on the sending side, not for the Pad’s system clipboard write. User-service disconnection normally schedules a retry after 1 second, and binder receipt calls `refresh`; however, a failed `start()` has no scheduled retry, and binder death only clears state until Shizuku returns or another caller refreshes. There is no periodic watcher health check. |
| **b) Loop prevention / dedupe** | **Yes, as a false-positive suppression risk.** The remote hash cooldown is 5 seconds; incoming IDs persist for the last 500 entries without time expiry; and `last_local_hash` persists without a TTL. Most importantly, stale `currentContent` and a failed prior write can make a later same-content item look delivered. |
| **c) Connected-looking link with no receive activity** | **Yes.** Reachability means at least one link object exists. LAN read timeouts are ignored and there is no receiving-side heartbeat/last-packet timeout that removes a silent link. |
| **d) Old item / ACK blocking newer items** | **Mostly no in the clipboard outbox:** `flushPendingClipboard` iterates all items, and each item has its own in-flight retry interval (15 seconds); an old unacknowledged clipboard item does not stop later clipboard items from being considered. **A shared transport queue can still head-of-line block:** `Device` sends through one per-device coroutine, and `sendPacketBlocking` may wait for a suitable link while later packets remain queued. A single main-thread retry schedule also depends on the app process/looper running. |
| **e) HyperOS freezes/kills background work** | **Yes, as an external possibility.** The connection service is foreground and `START_STICKY`, and optional Shizuku keep-alive protections exist, but those do not guarantee HyperOS will schedule the process or Shizuku user service. There is no clipboard-specific receive watchdog to detect that work has stopped. |

### Initial assessment and plan (waiting for approval)

The strongest code-level failure mode is **false delivery acknowledgement before the system clipboard write completes**. A failed/no-op write can be made unretryable by the persisted seen-ID list and stale `currentContent`. A stale receive link and a dead Shizuku watcher are also plausible and need runtime evidence; static inspection cannot establish which occurred on the Pad.

1. **Step 2 after “go”:** add temporary, content-free stage logs on both devices with item ID, actual incoming transport, stage result/drop reason, and ACK result. Add Shizuku binder/user-service and link last-packet health snapshots every 30 seconds. Keep a local ring buffer capped at 200 lines and show it on Health. Reproduce with a benign unique marker while preserving the failure state; collect USB `adb logcat` before any restart.
2. **Reproduction procedure:** confirm both devices are paired with automatic clipboard sync enabled; leave the Pad in the reported background/screen-off condition; copy a unique non-sensitive marker on the OnePlus. If it fails, do not restart either app/device, toggle Shizuku, or reconnect Wi-Fi. Capture `adb logcat -d -v time` over USB first, then open Health to read the ring buffer. Record the timestamp and whether the sender had a pending delivery.
3. **Step 3 after the logs identify the cause:** fix only the demonstrated failure path, add the requested stuck-loop-prevention unit test, and implement bounded delivery recovery (retry after 5 seconds, retry once after resetting/reconnecting the clipboard service/link). Add the Health “Clipboard receiving is stuck” state and one-tap service reset. Keep diagnostics useful but non-noisy; run `assembleDebug` and `testDebugUnitTest`, verify both clipboard directions and unrelated plugin paths, and make one focused commit for this step.

**Current status:** Step 1 review and this log entry are complete. No diagnostics, runtime reproduction, or app-code fix has started. Waiting for the user to say **go** before Step 2.
