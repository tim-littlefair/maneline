# Fender amp check

A small tool for owners of Fender amps that the Fender Tone app controls over Bluetooth
(Mustang GT40/GT100/GT200, Mustang GTX, Mustang LTX, Rumble Studio 40, Rumble Stage 800,
and the Mustang Micro Plus for comparison). It connects to the amp, asks it what it can
do, and writes a report that helps the maneline project support the amp. You check the
report and then send it.

* Nothing is sent anywhere automatically.
* The amp is only **read**, plus an optional short test that switches presets and turns
  one knob and puts both back. **Nothing is saved on the amp.**
* The report leaves out the amp's Bluetooth address, Wi-Fi names and addresses, preset
  authors, cloud ids, and anything that looks like an e-mail, IP or hardware address.

## For testers

### Option 1: web page (easiest)

1. Open <https://banzhaf.chickenkiller.com/amp-probe/> in **Chrome**, **Edge** or
   **Chromium** on a Windows, Mac or Linux computer, or in **Chrome** on an Android phone
   or tablet. iPhone and iPad don't work (Apple allows no Bluetooth from web pages, in any
   browser), nor do Safari and Firefox; the page says so and names what works.
2. Switch the amp on and close the Fender Tone app on your phone or tablet (or switch
   their Bluetooth off). The amp talks to only one app at a time.
3. Follow the steps on the page: **Find my amp**, wait for the reading to finish, run or
   skip the control test, answer the questions, then **Show the report**.
4. Read the report. Then **Save report file** and send it by e-mail or on GitHub, as the
   page explains.

The page also works as a downloaded file: **Download this page** (below the privacy note)
saves `fender-amp-check.html`; open it in Chrome or Edge, for example to read it first or
to use it without internet. A downloaded copy says which version it is and links to the
online page for the newest one and for the Python program.

On a Mac, the first time you use it, macOS may ask whether Chrome may use Bluetooth:
click **Allow**.

On Linux, Chrome, Chromium and Edge have Web Bluetooth switched off. Switch it on once in
`chrome://flags/#enable-experimental-web-platform-features` (Enabled, then Relaunch), or
start the browser with `--enable-features=WebBluetooth`; the page says so when it is off.
If the computer is paired with the amp for audio, the amp has to be disconnected and its
`PreferredBearer` set to `le` for the test: otherwise BlueZ connects the classic audio link,
which has no control service, and the page reports the connection but no services. On Linux the
page therefore shows, after the amp is chosen in step 1, a few lines to run in a terminal before
it connects. A web page never learns a device's address, so the lines find the amp by the chosen
name among the paired devices (`bluetoothctl devices Paired`) and keep its address and the old
preference in `~/amp-check-bearer.txt`. BlueZ stores the preference permanently (in
`/var/lib/bluetooth/<adapter>/<amp>/info`); the lines in step 5 restore it from that file and
reconnect the audio. Nothing needs typing. If no paired device has the chosen name (renamed,
or a different classic Bluetooth name), the lines take the paired device that lists a Fender control
service. They are one `bash -c '...'` command without single quotes or backslash pairs inside, so
they paste unchanged into bash, zsh or fish; bash has to be installed. The Python script offers to do this itself and sets
the preference back afterwards (see
[docs/MUSTANG_MICRO_PLUS_LINUX.md](../docs/MUSTANG_MICRO_PLUS_LINUX.md) for the BlueZ
details, including a GATT cache entry that stalls connecting to the Micro Plus).

### Option 2: Python script (more details, for the curious)

This also records Bluetooth details the browser can't see, and the amp's USB-audio and
Bluetooth-audio entries on your computer.

1. Install Python 3.9 or newer from [python.org](https://www.python.org/downloads/)
   (Windows: tick "Add python.exe to PATH" in the installer). Linux usually has it.
2. Open a terminal (Windows: *Terminal* or *Command Prompt*; Mac: *Terminal*) and install
   the Bluetooth library:
   * Windows: `py -m pip install bleak`
   * Mac: `python3 -m pip install bleak`
   * Linux (most distributions refuse a system-wide `pip install`):
     `python3 -m venv amp-venv && amp-venv/bin/pip install bleak`, or the distribution's
     `python3-bleak` package
3. Download [`fender_amp_probe.py`](https://banzhaf.chickenkiller.com/amp-probe/fender_amp_probe.py) (the page links to it too)
   and run it from the folder you saved it in:
   * Windows: `py fender_amp_probe.py` (if an older copy says "Python not found": `py -3 fender_amp_probe.py`)
   * Mac: `python3 fender_amp_probe.py` (allow Terminal to use Bluetooth if asked)
   * Linux: `amp-venv/bin/python fender_amp_probe.py`. It uses bleak on BlueZ, not
     maneline's own BlueZ code. If the computer knows the amp as an audio device, it offers
     to disconnect it and to prefer LE for it during the check (`PreferredBearer`), sets the
     preference back afterwards and offers to reconnect the audio. It also adds the amp's
     `bluetoothctl info` entry (without address and alias) to the report.
4. Answer the questions. The report is saved on your Desktop as
   `fender-amp-report-….md`. Open it, read it, and send it.

## What is checked

See [docs/FENDER_BLE_PROTOCOLS.md](../docs/FENDER_BLE_PROTOCOLS.md) for the protocol
families. Per family:

| Family | Reads | Control test (reversible) |
| --- | --- | --- |
| GT / GTX / Rumble (`gt`) | 2.5 s listening; 901 session start (firmware, product id, all presets); 920 current preset (chain, knob values, edited flag); Bluetooth state, global EQ, global FX assignment, tempo, setlists, loopback; 106 one knob; 424 preset transfer of the current slot (500–502), only without unsaved changes | 407 to another slot and back, then 920; 102 one knob ±0.1 (or ±1 dB) and back, read back with 106, reload the slot to clear the edited flag |
| Micro Plus / LTX (`mp`, `ltx`) | 7 firmware and 14 preset names first (same meaning in both families; the reply number tells which family it really is), then heartbeat and the family's read requests, the stored form of the current preset | load another preset and back; set one amp knob and back, read back, reload to clear the dirty flag |

Both skip the control test when the current preset has unsaved changes. Every byte sent
and received goes into the report's traffic log, with private bytes shown as `..`.

## For maintainers

* **Hosting:** Web Bluetooth needs a secure page (https). The page is the single file
  `index.html`; it links to `fender_amp_probe.py` in the same folder, so publish both
  together. They are served from <https://banzhaf.chickenkiller.com/amp-probe/> (a plain folder on a static https site);
  any static hosting works, for example GitHub Pages. `CONFIG.email` (both files) is the
  address that receives reports; the e-mail button stays hidden while it is empty.
  `CONFIG.issues` points to the upstream issue tracker, `CONFIG.home` (page only) to the
  online page, which a downloaded copy links to.
* **After a Fender Tone update:** extract the definitions again and rebuild the embedded
  schema:

      unzip -j config.arm64_v8a.apk lib/arm64-v8a/libmustangdevice.so
      python -I scripts/extract_fender_protos.py libmustangdevice.so out/
      python -I scripts/extract_fender_ble_ids.py libmustangdevice.so
      python -I amp-probe/build_schema.py out/

  `build_schema.py` embeds only what the probes need, not the app's full set of
  definitions: everything an amp can send back (to decode replies and to find private
  fields) and, of the requests, only those the probes send (its `SENT` lists; a test checks
  them against `index.html`). When the probes start sending another request, add it there.

* **Tests** (simulated GT, Micro Plus and LTX amps; no hardware needed):

      node --test amp-probe/tests/test_core.js
      python3 -m unittest discover -s amp-probe/tests

  `index.html` and `fender_amp_probe.py` implement the same steps; keep them in step.
