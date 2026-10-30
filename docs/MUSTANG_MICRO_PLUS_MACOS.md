# Mustang Micro Plus on macOS

Read when connecting a Micro Plus over Bluetooth or developing its BLE protocol.

`scripts/mmp_ble.py` is an experimental standalone controller for the first
Micro Plus milestone. It uses Bleak's CoreBluetooth backend on macOS and does
not require balena or the Java/Lua SDK. It is not yet wired into the web UI.
The USB cable can remain connected for audio and power.

## Setup and commands

Use the Python environment created by `scripts/rebuild_sdk.sh` (which installs
Bleak), or create an isolated environment:

```sh
uv venv _work/mmp-venv
uv pip install --python _work/mmp-venv/bin/python 'bleak==3.0.2'
```

Turn on the Micro Plus and close Fender Tone on other devices. macOS may ask
the application running Python for Bluetooth permission. If access is denied,
enable it in System Settings > Privacy & Security > Bluetooth before retrying.

```sh
_work/mmp-venv/bin/python scripts/mmp_ble.py scan
_work/mmp-venv/bin/python scripts/mmp_ble.py status
_work/mmp-venv/bin/python scripts/mmp_ble.py presets
_work/mmp-venv/bin/python scripts/mmp_ble.py preset 72
```

Preset numbers are 1–100, matching the display. Switching changes the active
preset; it does not rewrite saved preset settings. Each command connects,
performs its operation, and disconnects. Long-lived sessions are not supported:
live testing found that notifications stop arriving after an idle period, even
though subsequent switch commands can still reach the amp. A missing response
therefore means the result is unconfirmed, not necessarily that nothing changed.
Reconnect with `status` to check the actual selected preset.

Put `--device ID` before the command to select a particular device using its
ID from `scan`. macOS uses a local CoreBluetooth UUID rather than a Bluetooth
MAC address. Multiple matching devices require an explicit selection.
`--log _work/mmp-ble.jsonl` records timestamped transmitted/received packets
for protocol debugging. Logs may contain preset names and definitions.

## Protocol and verification

The service/characteristic UUIDs, startup sequence and switch command come from
`mmp_helper.py.disabled` and `mmp_startup.gatttool.json`.
The wire protocol uses zero-based preset indices;
the controller converts them to display numbers. Notifications are reassembled
before decoding protobuf messages. A switch succeeds only after the amp reports
the requested preset; a successful Bluetooth write alone is insufficient.

Run the hardware-independent regression tests with:

```sh
_work/mmp-venv/bin/python -m unittest discover -s scripts/tests -v
```

This milestone reads preset names and the selected preset. Editing, saving,
restoring and exporting full preset definitions are outside its scope.

## Live verification

On 30 October 2026 the controller discovered and connected to a real Micro Plus
from macOS, read all 100 preset names and the current selection, and received
acknowledgements for switching from displayed preset 71 to 72 and back to 71.
The tests cover captured switch/selection packets, fragmented preset names,
invalid input, response timeouts and requiring an acknowledgement before
reporting a successful switch. Persistent notification/heartbeat handling is
still experimental and is not exposed by this controller.
