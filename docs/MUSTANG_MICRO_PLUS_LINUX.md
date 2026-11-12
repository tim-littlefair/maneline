# Mustang Micro Plus on Linux

Read when controlling a Micro Plus from Linux, especially while it plays Bluetooth
audio from the same computer. The protocol and the commands are the same as in
[MUSTANG_MICRO_PLUS_MACOS.md](MUSTANG_MICRO_PLUS_MACOS.md); this page covers what
BlueZ needs.

Tested on 9 November 2026 with firmware 1.0.29, BlueZ 5.87, an Intel AX201
adapter and Python 3.13. Since 11 November 2026 the scripts need no Bleak on Linux,
only `dbus-fast`; that change is tested without an amp so far.

## Audio and control at the same time

The Micro Plus is a dual-mode device. Audio from the computer uses classic
Bluetooth (BR/EDR, A2DP); presets are controlled through a GATT service on
Bluetooth LE. Both can run at once. Presets switch in 0.1–0.2 s while audio
keeps playing, and closing the controls leaves audio connected.

Bleak alone cannot do this on BlueZ. It connects and disconnects whole devices
(`Device1.Connect`, `Device1.Disconnect`), and the disconnect drops audio too. Bleak
sends it when a client disconnects, when an `async with` block ends and when
`asyncio.run()` shuts down. While audio is connected, Bleak also considers the
device connected already and never brings up LE.

`scripts/mmp_bluez.py` therefore talks to BlueZ directly, through its per-bearer
interface `org.bluez.Bearer.LE1`. It connects and disconnects only the LE link,
and watches that link rather than the device. `mmp_ble.py` says so:

```
Connected to Mustang Micro Plus over LE only; Bluetooth audio stays connected
```

On Linux the scripts do not use Bleak at all. The only Python package they need
is `dbus-fast` (Debian 13: `python3-dbus-fast`). They also do not scan: the amp
has to be paired anyway (below), so they ask BlueZ for the paired device that
offers the amp's control service (`90559580-b707-11ee-acb1-7b7e30f1af54` among
the device's `UUIDs`), whatever the device is called; a paired device named
"Mustang Micro Plus" also counts, for the case that BlueZ does not list its
services. `mmp_ble.py scan` lists the paired amps. With more than one, pass
`--device` with the Bluetooth address: `mmp_ble.py` insists on it, the other
scripts and the browser server take the first amp BlueZ lists. A paired amp is known to BlueZ while it is switched
off, so an amp that is off shows as "does not answer" after the connection
timeout rather than as "not found".

The interface appeared in BlueZ 5.84, but its `Connect` and `Disconnect` were empty
until 5.86 (`src/bearer.c`: "TODO", no reply), so 5.86 is the first version that works.
Without the bearer interface (BlueZ older than 5.84 or experimental interfaces
off) the scripts stop and say what BlueZ needs. They no longer fall back to
Bleak, which would drop audio on every disconnect.

## One-time BlueZ setup

1. Enable the experimental D-Bus interfaces, which include `Bearer.LE1`. In
   `/etc/bluetooth/main.conf`, section `[General]`:

   ```
   Experimental = true
   ```

   Then `sudo systemctl restart bluetooth`.

2. Pair over LE as well as classic Bluetooth. If the amp was paired for audio
   only, BlueZ connects it over classic Bluetooth, which has no control service.
   Pair over LE once by preferring LE while pairing:

   ```sh
   DEV=/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF      # your amp's address
   busctl --system set-property org.bluez $DEV org.bluez.Device1 PreferredBearer s le
   bluetoothctl pair AA:BB:CC:DD:EE:FF             # Just Works, no PIN
   busctl --system set-property org.bluez $DEV org.bluez.Device1 PreferredBearer s bredr
   ```

   `bluetoothctl info` should then show both `BREDR.Bonded: yes` and
   `LE.Bonded: yes`. Leaving the preference on `bredr` keeps a plain "Connect"
   (for example from a desktop Bluetooth applet) connecting audio. Maneline opens
   the LE link itself.

3. Remove the Client Supported Features characteristic from BlueZ's GATT cache.
   On connecting, BlueZ writes to characteristic `0x2b29` (handle `0x0006`), and
   the amp never answers. ATT allows one outstanding request, so everything
   after it stalls: subscribing fails with `GATT Protocol Error: Unlikely Error`
   and BlueZ drops the link after 30 s. BlueZ writes it whenever the cached
   database contains it, so delete that line with bluetoothd stopped:

   ```sh
   F=/var/lib/bluetooth/<adapter>/cache/AA:BB:CC:DD:EE:FF
   sudo systemctl stop bluetooth
   sudo cp -p $F $F.bak
   sudo sed -i '/^0005=2803:0006:0a:00002b29-/d' $F
   sudo systemctl start bluetooth
   ```

   If BlueZ discovers the services again, for example after unpairing, repeat
   this.

## Scanning

On the tested adapter, scanning with a service-UUID discovery filter made BlueZ
reset the adapter within a second (`No discovery started`). The scripts
therefore filter by service UUID only on macOS and find the amp by name on
Linux.

## Saving the amp's battery

While connected, the amp's radio wakes every 45 ms for the LE link (connection
interval 45 ms, latency 0), and the heartbeat must arrive at least every 0.5 s or
the amp stops replying. Measured on firmware 1.0.29 with 20 s of idle: a 1 s, 2 s
or 5 s heartbeat, or none, left switches unconfirmed although the amp still
switched; 0.5 s and 0.25 s kept every switch confirmed. A heartbeat burst after
replies have stopped did not bring them back, only a new connection did.

The browser server can therefore disconnect when idle:

```sh
bash scripts/run_web_cli.sh --micro-plus --idle-disconnect 30
```

After 30 s without a preset switch it closes the connection, unless Bluetooth
audio is streaming to the amp. Next to a running A2DP stream (about 360 kbit/s)
the LE link costs little, so it stays up for instant switching. With audio
connected but silent, or not connected at all, the LE link is a large share of
what the radio does. The page keeps showing the presets and the last known one;
choosing a preset reconnects first. That took 2.7–3.4 s in tests, including
while audio streamed. Once, just after streaming had started, it took 14 s.
On macOS the audio state is not available, so the server disconnects whenever
it is idle.

## Notes for applications

- Bring up the control link with `Bearer.LE1.Connect()` and close it with
  `Bearer.LE1.Disconnect()`; never `Device1.Disconnect()`.
- `Bearer.LE1.Connect()` returns once the radio link exists. BlueZ's GATT client
  is ready about a second later, while the previous session's characteristic
  objects are still exported. `Device1.ServicesResolved` may already be true from
  the audio connection. `LeBearerClient` waits until a read of the GAP Device
  Name succeeds before subscribing or writing.
- Send the heartbeat every 250 ms while connected; without it the amp stops
  confirming preset changes.
- The browser server treats SIGTERM like Ctrl-C, so stopping it as a service
  also closes the LE link cleanly. A killed process leaves the LE link up;
  the next connection adopts it.
- A notification fragment without its start is now dropped instead of failing
  every later request.
- Only one program on a computer can control the amp at a time. Two would both
  receive every notification twice, corrupting multi-frame messages such as the
  preset names. If another program is already subscribed, `LeBearerClient`
  stops with an error and leaves that program's LE link alone.

## Checking

```sh
python -m unittest discover -s scripts/tests -v
bluetoothctl info AA:BB:CC:DD:EE:FF | grep -E '(BREDR|LE)\.(Bonded|Connected)'
busctl --system get-property org.bluez $DEV org.bluez.Device1 PreferredBearer
```

To capture what happens on the radio: `sudo btmon -w trace.btsnoop`, read it with
`btmon -r trace.btsnoop`. To toggle bluetoothd's debug log:
`sudo kill -USR2 $(systemctl show -p MainPID --value bluetooth)`, then
`journalctl -u bluetooth`.
