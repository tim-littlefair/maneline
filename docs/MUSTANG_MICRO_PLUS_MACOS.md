# Mustang Micro Plus on macOS

Read when connecting a Micro Plus over Bluetooth, running its browser controls,
or developing its BLE protocol.

Maneline can read all 100 preset names, display the current selection, and switch
presets on a Micro Plus using Bleak's CoreBluetooth backend. The USB cable can
remain connected for audio and power. Preset editing, saving, restoring and full
preset-definition export are not implemented for this device.

## Setup

Use Python 3.11 or later with Bleak installed. The Python environment created by
`scripts/rebuild_sdk.sh` includes Bleak, or create an isolated environment:

```sh
uv venv _work/mmp-venv
uv pip install --python _work/mmp-venv/bin/python 'bleak==3.0.2'
```

Turn on the Micro Plus and close Fender Tone on other devices. macOS may ask
the application running Python for Bluetooth permission. If access is denied,
enable it in System Settings > Privacy & Security > Bluetooth before retrying.

## Browser controls

```sh
bash scripts/run_web_cli.sh --micro-plus
```

Open `http://localhost:8081`. This launch mode serves the Micro Plus page from
`web-app/web_ui`, using the existing Maneline styles. It owns one persistent BLE
connection shared by all browser clients; browser refreshes and preset clicks do
not reconnect the amplifier. The original Java/Lua USB mode is still launched
with `scripts/run_web_cli.sh` without `--micro-plus`.

The page displays the current preset, highlights its button, and lets you filter
presets by number or name. Selecting a preset waits for the amp to confirm the
change. The display also follows preset changes received from the device.
Switching presets does not rewrite saved preset settings.

If the amplifier disconnects, the page clears its current selection and disables
switching until the connection recovers. The server retries automatically, using
the device ID from the first connection. It does not retry an unconfirmed
switch: it reconnects and reads the actual current selection instead.

Use `--device ID` to select a specific Micro Plus and `--port NUMBER` to change
the HTTP port. If using another Python environment, set `MANELINE_PYTHON` to its
interpreter. The server binds to `0.0.0.0`; localhost and this Mac's hostname and
`.local` hostname are accepted. For another hostname or a numeric LAN address,
add `--allow-host HOSTNAME_OR_IP` (repeatable). Use this on a trusted network;
there is no user login. Browser writes require a matching Origin and Host.

`--idle-disconnect SECONDS` saves the amp's battery: after that long without a
preset switch the server disconnects, and the next switch reconnects first,
which takes a few seconds. See [MUSTANG_MICRO_PLUS_LINUX.md](MUSTANG_MICRO_PLUS_LINUX.md#saving-the-amps-battery).

The API is `GET /api/micro-plus` for connection state, selected preset, names and
connection count, and `POST /api/micro-plus` with JSON `{"preset": 72}` to switch.
Preset numbers are 1–100, matching the display. Invalid numbers return HTTP 400;
an unavailable connection or unconfirmed switch returns HTTP 503. A failed
confirmation does not establish that the amplifier stayed on its previous preset.
Traffic logs are appended to `_work/mmp-web.jsonl` and can include preset data.
Stop the server before using any other BLE controller, including the commands
below, so only one program holds the connection.

## One-shot command-line controls

```sh
_work/mmp-venv/bin/python scripts/mmp_ble.py scan
_work/mmp-venv/bin/python scripts/mmp_ble.py status
_work/mmp-venv/bin/python scripts/mmp_ble.py presets
_work/mmp-venv/bin/python scripts/mmp_ble.py preset 72
```

These commands connect, perform one operation and disconnect. Put `--device ID`
before the command to select the ID returned by `scan`. On macOS this is a local
CoreBluetooth UUID, not a Bluetooth MAC address. Multiple matching devices require
an explicit selection. `--log _work/mmp-ble.jsonl` records timestamped BLE traffic.

## Persistent connection and verification

The service/characteristic UUIDs, startup sequence and switch command come from
`mmp_helper.py.disabled` and `mmp_startup.gatttool.json`. The wire protocol uses
zero-based preset indices; the controller converts them to display numbers.
Notifications are reassembled before decoding protobuf messages.

On the tested firmware (1.1.11), one-second keep-alives allowed the connection
to remain open while notifications stopped. Sending the captured heartbeat
`3500050a03c20100` every 250 ms kept notifications working after idle periods.
The original Linux experiment specifies a 500 ms interval. Maneline uses 250 ms
to leave scheduling margin and continues sending while a switch awaits its
acknowledgement. Commands share a write lock, while whole switch operations are
serialized separately. This interval is verified behavior, not a documented
Fender timing guarantee.

On 30 October 2026 live testing on macOS read all 100 names, held a single BLE
connection for several minutes, and switched displayed preset 71 to 72 and back
from the browser without reconnecting. The amplifier was restored to preset 71.
Disconnect handling is also covered by simulated lifecycle tests.

```sh
_work/mmp-venv/bin/python -m unittest discover -s scripts/tests -v
```

Tests cover captured packets, fragmented names, invalid input, acknowledgement
requirements, keep-alives during pending switches, clearing disconnected state,
HTTP validation, same-origin writes and static-file access restrictions.

## Further observations

These were seen on 1–2 November 2026 with the same amplifier and firmware (1.1.11),
while editing presets over the same Bluetooth connection. Only reading and switching
are implemented here.

**Replies and button presses.** Field 1 of a message from the amp says why it was
sent. A reply to a request has 1 or 2 there; the captured switch confirmation has 1,
and the replies to the startup reads below have 2. A message the amp sends because
someone pressed one of its buttons leaves the field out. This matches the response
types in Fender's message definitions for the Mustang LT amps (unsolicited 0, not the
last reply 1, last reply 2), although the Micro Plus numbers its messages differently.

**Startup requests.** The read-only startup sequence asks for these, by the number
inside field 1 of each request. "Reply" is the number inside field 2 of the answer.

| Request | Reply | What it is |
| --- | --- | --- |
| 7 | 6 | Firmware version, as text (`1.1.11`) |
| 14 | 12 | Names of the 100 presets |
| 4 | 4 | The current preset's settings, as compressed JSON |
| 22 | 18 | A float, -6.0, then the number 1. Possibly a gain; not known |
| 25 | 19 | The EQ setting chosen with the amp's EQ buttons, counted from 0 for EQ1 Flat |
| 24 | none | The heartbeat |

Request 26, with the same content as reply 19 (`0801 10nn`, or just `0801` for Flat),
sets the EQ, and the amp answers with reply 19. Pressing the EQ buttons sends reply 19
unasked. Pressing TAP changes the delay's time setting, which arrives as a changed
parameter.

**Trying unknown requests is risky.** One of the amp's error codes is "factory restore
in progress", so some request number restores the factory state, and which one is not
known. Back up the presets before sending anything new.

**The amp's Bluetooth can stop answering.** Twice the amp stopped answering every
request while the link still looked connected; once it happened after a browser tab had
been idle in the background for half an hour. Its buttons and sound kept working. After
disconnecting, a scan no longer found it, until it was switched off and on. If an
unconfirmed switch leads to "Micro Plus not found" while the amp is on, that is the
likely cause.

**After switching the amp off and on**, the first preset switch got no answer once, and
reconnecting finished it, about 10 seconds in all. Later switches were quick.

**Before experimenting**, see Fender's user guide (pages 7–8) for the power light's
colours and the button combinations at power-on: PRESET minus starts a firmware update
over USB, MODIFY minus with SAVE resets the amp to factory settings and forgets paired
devices, and SAVE with TAP resets presets 1–80. Slow green flashing is normal: the
battery is over half full and no USB cable is connected.
