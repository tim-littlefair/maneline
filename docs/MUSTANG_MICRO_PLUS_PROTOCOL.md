# Mustang Micro Plus: Bluetooth message numbers

The Micro Plus speaks Fender's protobuf protocol of the **"mp" family** (package
`fendermessage.mp`). The definitions, including the envelope with all message
numbers, are embedded in the Fender Tone app's `libmustangdevice.so`. Extracted
2026-11-10 from Fender Tone 5.1.3 (Android, arm64) with
`scripts/extract_fender_protos.py`.

**Checked against the amp (firmware 1.1.11; firmware reply text 1.0.29):**
* Every message number maneline had observed matches the table below. That covers
  requests 4, 7, 8, 14, 22, 24, 25 and 26, replies 4, 5, 6, 12, 18 and 19, and modal
  status SYNC_END.
* Request 12 (`presetDataRequest`) was sent for slot 21. The amp answered with reply
  10 for index 20 ("Echo Dome") and stayed on its preset.

The "ltx" family, which an earlier extraction (spod/fmmp, Fender Tone 4.0.5) published,
uses the same message definitions but **different numbers** (for example ltx 8 =
heartbeat, 9 = load preset). It is not the Micro Plus protocol.

## Frames

Each BLE write or notification is one frame:
`<type> 00 <length> <payload>`, where the payload is at most 255 bytes.

| Type | Meaning |
| --- | --- |
| `35` | single frame, or last frame of a message |
| `33` | first frame of a longer message |
| `34` | continuation |

Long requests use the same frame types, **117 payload bytes per frame**
(`Controller.send_message`).
* **Hold the write lock for the whole message.** Another frame in between (e.g. a
  heartbeat, itself a complete `35` message) ends the message early, and the amp
  silently drops it.
* **Pause 20 ms between frames** (`FRAME_GAP`). The frames are writes without response;
  sent back to back, under 1 ms apart, the amp ignored 4 of 5 auditions. With both
  rules, 5 of 5 arrived (2026-11-10).

## Envelope: `MustangLtxAppMessage`

| Field | Content |
| --- | --- |
| 1 | `toneRequest`: one of the requests below (app → amp) |
| 2 | `ampStatus`: `responseType` (field 1: 0 unsolicited, 1 not last, 2 last), then one of the statuses below (amp → app) |
| 3 | `modalStatus` {context, state}. Contexts: SYNC_BEGIN/END, BACKUP_*, RESTORE_*, TUNER_*, FACTORY_RESTORE_BEGIN/END, TONE_BUSY_* |
| 4 | `loopback` |

### Requests (`toneRequest`, field number = message)

| # | Request | Fields | Notes |
| --- | --- | --- | --- |
| 1 | auditionPresetRequest | compressedPresetJson=1 | load a preset **without saving** |
| 2 | clearPresetRequest | presetIndex=1 | **writes**: empties a slot |
| 3 | connectionStatusRequest | | |
| 4 | currentPresetRequest | | → 4, loaded form of the current preset |
| 5 | currentPresetSaveRequest | presetName=1, presetIndex=2 | **writes** |
| 6 | exitAuditionRequest | | |
| 7 | firmwareVersionRequest | | → 6 |
| 8 | loadPresetRequest | newPresetIndex=1 (0-based) | select a preset (as the buttons do) → 5 |
| 9 | modifiableParameterRequest | | which parameter MODIFY controls → 7 |
| 10 | moveNodeRequest | | |
| 11 | parameterRequest | nodeId=1, parameterId=2 | → 9 |
| 12 | presetDataRequest | presetIndex=1 (0-based) | stored form of any slot, no switching → 10 |
| 13 | presetNameRequest | | |
| 14 | presetNamesRequest | | → 12 |
| 15 | productIdentificationRequest | | → 14 |
| 16 | renamePresetRequest | presetName=1 | **writes** |
| 17 | replaceNodeRequest | nodeIdToReplace=1, fenderIdToReplaceWith=2 | change amp or effect model |
| 18 | savePresetAsRequest | presetIndex=1, compressedPresetJson=2, isLoadPreset=3, presetName=4 | **writes** a whole preset → 13; names of more than 16 characters are silently ignored (no reply, nothing saved) |
| 19 | setParameterModifiableRequest | nodeId=1, parameterId=2 | |
| 20 | setParameterRequest | nodeId=1, parameterId=2, one of float=3 / string=4 / sint32=5 / bool=6 | live edit, not saved |
| 21 | swapPresetRequest | | **writes** |
| 22 | usbAudioGainInfoRequest | | → 18 |
| 23 | usbAudioGainSetRequest | | |
| 24 | heartbeat | | must come at least every 0.5 s |
| 25 | eqRequest | | → 19 |
| 26 | setEqRequest | | |
| 27 | setTapTempoRequest | | |
| 28 | tapTempoRequest | | → 20 |
| 29 | tunerRequest | | → 21 |
| 30 / 31 | setAttenuatorRequest / attenuatorRequest | | → 22 |
| 32 | movePresetRequest | | **writes** |
| 33 / 34 | muteDetectEnableRequest / set… | | → 24 |
| 35 / 36 | inputPadRequest / set… | | → 25 |

### Statuses (`ampStatus`)

| # | Status | # | Status |
| --- | --- | --- | --- |
| 2 | clearPresetStatus | 13 | presetSavedStatus {presetIndex, presetName} |
| 3 | connectionStatus | 14 | productIdentificationStatus |
| 4 | currentPresetDataStatus {compressedPresetJson=1} | 15 | replaceNodeStatus |
| 5 | currentPresetStatus (selected preset) | 17 | unsupportedMessageStatus {status=1} |
| 6 | firmwareVersionStatus | 18 | usbAudioGainInfoStatus |
| 7 | modifiableParameterStatus | 19 | eqStatus |
| 8 | moveNodeStatus | 20 | tapTempoStatus |
| 9 | parameterStatus (same fields as setParameterRequest) | 21 | tunerStatus |
| 10 | presetDataStatus {presetIndex=1, compressedPresetJson=2} | 22–25 | attenuator, movePreset, muteDetect, inputPad |
| 11 / 12 | presetNameStatus / presetNamesStatus | | |

**Error codes** (`unsupportedMessageStatus.status`): 0 UNSUPPORTED, 1 FAILED,
2 INVALID_PARAM, 3 INVALID_NODE_ID, 4 PARAM_OUT_OF_BOUNDS,
5 FACTORY_RESTORE_IN_PROGRESS. The last one is only a state the amp reports. **No
request performs a factory restore**; that is done with buttons at power-on.

## Preset JSON

`compressedPresetJson` is an **LZ4 block** (no frame header, no size prefix) of
Fender's preset JSON (`product_id: "mustang-ltx"`). The JSON contains a graph of five
`dspUnit` nodes: stomp, mod, amp, delay and reverb. Each node has a `FenderId` (e.g.
`ACD_Jcm800GT`) and `dspUnitParameters`. The amp's `volume` is in dB.
`scripts/mmp_lz4.py` decodes and encodes it.

There are two forms of the same preset:
* **Stored** (`presetDataRequest`): exactly as saved, with short decimal numbers,
  `gateDetectorPosition`, and no entries for empty blocks. This is what a restore
  writes back.
* **Loaded** (`currentPresetRequest`): numbers widened from float, and
  bypass/bypassType filled in for every block.

## Backup

`scripts/mmp_backup.py` reads all 100 slots with `presetDataRequest`. It is read-only
and doesn't switch presets. It writes one JSON file per slot and `manifest.json` with
the raw LZ4 blocks to `_work/backups/`, which git does not track. 100 slots take about
a minute.

## Audition: tested 2026-11-10

* **Sending long messages works:** a 1,166-byte compressed preset in 11 frames
  (`33`, `34`…, `35`, 117 payload bytes each, like the amp's own frames) was
  accepted.
* **The amp loads the preset at once,** and the change is audible. A copy of 80S Rock
  with the amp volume at −12 dB instead of −5 dB was clearly quieter. The amp
  answered with currentPresetStatus (5) and currentPresetDataStatus (4) for the
  auditioned preset.
* **`exitAuditionRequest` needs `dummyField = true` (body `08 01`).** An empty
  message is ignored. With the field set, the amp reloads like at startup (replies 4,
  18 and 19). The same probably holds for every request that has a dummyField.
* **Side effect:** the auditioned preset's name ("Audition Test") replaced the name of
  the current slot in the **name list** (request 14). It stayed after exiting and
  after switching away and back. The stored preset itself (request 12) was
  unchanged, byte-identical to the backup.
* **The name list is only memory:** after the amp was switched off and on, the whole name
  list was identical to the backup again ("80S Rock" in slot 19). So an audition never
  changes anything that survives a power cycle. To avoid the temporary name, audition
  with the slot's own name, or audition the slot's stored preset again before exiting.

**A reserved empty slot for auditions works** (tested on slot 100, "Empty"):
* Select it (loadPresetRequest), then audition: the amp confirms slot 100 and loads the
  auditioned tone.
* Further auditions replace each other.
* Selecting a real slot ends the audition and loads that slot unchanged.
* The temporary name only replaces slot 100's entry in the name list (memory only).
* The stored "Empty" preset stays untouched, and your real presets' names never show a
  wrong tone.

## Not yet done

Before any request marked **writes** above is used, a backup must exist. The first
tests belong on an empty slot (72–100), with `auditionPresetRequest` tried before
`savePresetAsRequest`.

## After a firmware update

Firmware updates can change any of the above. With the agent (`mmp_web.py`) running:

    scripts/mmp_check.py BACKUP_DIR --all          # read-only apart from a switch and an audition
    scripts/mmp_check.py BACKUP_DIR --all --write  # also save into slot 99 and put it back

It checks the connection, the firmware version (the agent now reports the handshake's
`firmwareVersionStatus`) against the backup's, the 100 names and presets against the
backup, switching, an audition on slot 100 and, with `--write`, `savePresetAsRequest`.
2026-11-11: all passed on firmware reply 1.0.29.

## Web server API

`scripts/mmp_web.py` (default port 8081) owns the single BLE connection. Next to the
browser page it offers:

| Request | Body / query | Does |
| --- | --- | --- |
| `GET /api/micro-plus` | | state: connected, preset, name, `audition`, 100 preset names |
| `POST /api/micro-plus` | `{"preset": n}` | select preset n (ends an audition) |
| `POST /api/micro-plus/audition` | `{"preset": {…preset JSON…}, "slot": 100}` | play the preset without saving it; with `slot`, select that slot first (a reserved empty one keeps the temporary name away from real presets) |
| `GET /api/micro-plus/preset?slot=n` | | the preset stored in slot n, as JSON (read without selecting it, cached per connection) |

**Rules:**
* POSTs need `Content-Type: application/json` and an `Origin` matching the server.
* Bodies may be up to 64 KB.
* Errors are 400 (invalid request), 403 (other origin) and 503 (amp not connected, or
  it did not confirm).

**Tested 2026-11-10 with the amp:**
* An audition through HTTP takes 0.3–0.6 s.
* 80S Rock with the gate off measured −80.5 dBFS at +0 dB and −74.7 dBFS at +6 dB
  (+5.8 dB). Repeating the reference differed by 0.03 dB.
* Selecting slot 19 afterwards ended the audition.
