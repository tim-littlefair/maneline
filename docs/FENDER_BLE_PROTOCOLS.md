# Fender amps: Bluetooth LE protocol families

What the Fender Tone app (Android 5.1.3, arm64) shows about the amps it controls, found
by static analysis only (no amp other than the Mustang Micro Plus was available):

* `scripts/extract_fender_protos.py libmustangdevice.so out/` (no family filter) cuts out
  392 protobuf definitions: families `mp`, `ltx`, `gt`, `lt` and `backup/mp`.
* `scripts/extract_fender_ble_ids.py libmustangdevice.so` lists the BLE UUIDs, which the
  library keeps as 16-byte values (GUID byte order), not as text.
* `assets/resources/tonepanel/mustangdefs.xml` and the strings of `libtonepanel.so` give
  the model names and which component class drives which family.
* JSON documents inside `libmustangdevice.so` (`"productFamily": …`) list the amp and
  effect models each family offers.

The Java part of the app is only a generic Bluetooth layer (`dev.ccl.services.bluetooth`);
the device logic is native code in `libmustangdevice.so`.

Things marked **(inferred)** follow from names and structure, not from a test on the
amp. The probe in `amp-probe/` exists to check them with people who own the amps.

## Families

| Family (proto package) | Models (app strings) | Link | BLE service | Framing | Envelope |
| --- | --- | --- | --- | --- | --- |
| `lt` | Mustang LT25, LT50 (also LT40S, Rumble LT25, which maneline already supports) | USB HID | none | USB HID reports | `FenderMessageLT` |
| `mp` | Mustang Micro Plus | BLE | `90559580-b707-11ee-acb1-7b7e30f1af54` | `33`/`34`/`35` `00` len | `MustangLtxAppMessage` |
| `ltx` | Mustang LTX (`ProtobufProductId`: LTX100, LTX50) | BLE (inferred), maybe also USB | `90559580-b707-11ee-acb1-7b7e30f12dcd` (inferred) | as `mp` (inferred) | `MustangLtxToneMessage` |
| `gt` | Mustang GT40, GT100, GT200, Mustang GTX, Rumble Studio 40, Rumble Stage 800 | BLE (+ Wi-Fi on GT) | `82107b48-5a47-4067-bcc1-319844c8f1e8`, advertised byte-reversed as `e8f1c844-9831-c1bc-6740-475a487b1082` | none (one protobuf message per ATT value) | `FenderMessage` |
| Tone Master Pro | Tone Master Pro | not in this library | – | – | `tonemasterprodefs.xml` only (presets 0–504) |

All three BLE families use the **same two characteristics**:

| Characteristic | UUID | Use |
| --- | --- | --- |
| request | `820a7e34-4e0a-4f90-8520-04ebce35a3a1` | app → amp, write (GT: with response, MMP: without) |
| response | `1017adcc-dcbc-4387-a59f-2546b2ea5bb0` | amp → app, notify |

The service UUID therefore tells the family. The Micro Plus and the LTX service UUIDs
share everything except the last six bytes (both version 1 UUIDs from the same
generator). Assigning `…2dcd` to the LTX is **(inferred)**: it is the only other family
with a Micro-Plus-style envelope, and the library has a separate
"Fender Mustang Hardware Access LTX" device class. The USB ids `1ed8:0036` and
`1ed8:0037` (LT25) sit right after the characteristic UUIDs in the same table.

The mustangdefs template comment says "Mustang GT/GTX/Rumble" share one template (up to
13 signal-chain nodes; LT, LTX and Micro Plus have 5). The model JSON has separate
catalogs `mustang` (GT), `gtx`, `rumble`, `mustang-ltx` and `mustang-micro-plus`.

## Model catalogs per family

| Catalog | Amps | Stomp | Mod | Delay | Reverb | Dynamics | Filters | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `mustang` (GT) | 39 `DUBS_*` | 15 | 15 | 13 | 14 | 7 | 9 | global EQ, global FX assign (also `Mgt7`) |
| `gtx` | 39 `DUBS_*` | 15 | 15 | 13 | 14 | 7 | 9 | limiter, headphone gain, Bluetooth aux attenuation |
| `rumble` | 20 bass amps | 14 | 13 | 13 | 14 | 7 (bass EQs) | 9 | |
| `mustang-ltx` | 25 `ACD_*GT` | 12 | 7 | 5 | 5 | – | – | same models as the Micro Plus guitar amps |
| `mustang-micro-plus` | 25 guitar + 10 bass `ACD_*` | 12 | 7 | 5 | 5 | – | – | |

GT global EQ values (`SetGlobalEQ.preset` string): `off`, `bright-cut`, `bright-boost-0`,
`bright-boost-1`, `bright-boost`, `bright-boost-b`, `low-cut-1`, `low-cut`, `bass-boost`.

## GT family (`fendermessage.gt.FenderMessage`)

No frame header and no request/status split: every message is a `FenderMessage` with
exactly one field set, and the field number is the message type. Field numbers are large,
so the tag takes two bytes, e.g. `aa 38 00` = field 901, empty.

All numbers from Danny's GT100 captures (issue #148, `_work/GT100_BLE_NOTES.md`) match:

| # | Message | Body | Seen in #148 as |
| --- | --- | --- | --- |
| 901 | ToneSync | `hackSoItGens=1` (send empty) | session start |
| 902 | SystemStatus | address=1, firmwareVersion=2, wifiState=3, btName=4, productId=5 | `{BT MAC, "3.0.43", "On", "Mustang GT", "mustang-gt-100"}` |
| 903 | PresetInfoMessage | slotNumber=1, displayName=2, preset_id=3, author=4, timestamp=5, created_at=6, product_id=7, is_factory_default=8, isEmpty=9, source_id=10 | preset list entry |
| 920 | SyncCurrentPresetToTone | value=1 | current preset |
| 407 | LoadPresetIfValid | slot=1, sendToTone=2, setlistIndex=3 | select preset |
| 102 | SetDspUnitParameter | nodeId=1, parameterId=2, float=3 / string=4 / sint32=5 / bool=6 | live parameter |
| 106 | GetDspUnitParameter | nodeId=1, parameterId=2 | (not captured) read one parameter → 203 |
| 201 | NodeStatus | nodeStatus=1 {nodeId=1, fenderId=2, bypass=3} | signal chain |
| 203 | DspUnitParameterStatus | as 102 | parameter |
| 204 | CurrentLoadedPresetIdStatus | currentLoadedPresetId=1 | |
| 209 | ProductIdentificationStatus | id=1 | |
| 212 | ProcessorPercentageAvailableStatus | percent=1 | "17.4, 36.5 …": free DSP in % |
| 214 | PresetEditedStatus | presetEdited=1 | preset modified flag |
| 303 / 304 | PacketBegin / PacketEnd | packetType=1 (enum, e.g. TONE_SYNC=19, SYNC_CURRRENT_PRESET_TONE=23, LOAD_PRESET_IF_VALID=14), packetStatus=2 (SUCCESS, UNKNOWN_ERROR, INVALID_ID) | "19 / 23" begin and end of a reply group |
| 306 | NetworkStatus | connectionStatus=1, connectedSSID=2, connectedIP=3, availableNetworks=4 | the "NO" message about once a second: **Wi-Fi** status, not the tuner |
| 118 | SetGlobalFxBypass | doBypass=1 | |
| 123 | SetGlobalEQ | preset=1 (string) | `{1:"off"}` |
| 500 / 501 / 502 | PacketStartMTU / PacketDataMTU / PacketStopMTU | numBytes, id / seqNumber, id, data / id, crcCheck | chunked transfer |
| 803 / 805 / 825 | SetlistStatus / SetlistManagerSync / SetlistSyncEnd | | setlists |

Other groups in the envelope: tuner (105/205/206/215), master volume (115/119/207/213),
looper (121/124/125), saving/renaming/moving/deleting presets (103, 410–426), backup and
restore over file transfer (425–434), BPM (438–445), footswitch and expression pedal
(600–636), the amp's own display (700–857, the app mirrors the amp UI), Bluetooth and
USB (900 TurnBluetoothStreamingOff, 905/907 passkey, 906 SetBluetoothVolume,
908–911 USB gain, 912–919 BT/BLE status), cloud (1000–1004), keyboard and utility views
(1010–1052), 1100 ImpedanceSwitch. The full list is in the extractor output
(`gt__FenderMessage.proto`).

**Privacy:** 902 carries the amp's Bluetooth address, 306 the Wi-Fi network name, IP
address and nearby networks, 903 the preset author and cloud ids. Anything that collects
traffic for others must remove these.

## LTX family (`fendermessage.ltx.MustangLtxToneMessage`)

Same structure as the Micro Plus (`toneRequest` = 1, `ampStatus` = 2 with
`responseType` = 1, `modalStatus` = 3, `loopback` = 4), **different numbers**:

| # | Request | # | Status |
| --- | --- | --- | --- |
| 1 | auditionPresetRequest | 2 | clearPresetStatus |
| 2 | clearPresetRequest | 3 | connectionStatus |
| 3 | connectionStatusRequest | 4 | currentPresetDataStatus |
| 4 | currentPresetRequest {requestPresetData=1} | 5 | currentPresetStatus {presetIndex, isDirty, isAuditioning} |
| 5 | currentPresetSaveRequest | 6 | firmwareVersionStatus |
| 6 | exitAuditionRequest | 7 | moveNodeStatus |
| 7 | firmwareVersionRequest | 8 | parameterStatus |
| 8 | heartbeat | 9 | presetDataStatus |
| 9 | loadPresetRequest | 10 | presetNameStatus |
| 10 | moveNodeRequest | 11 | presetNamesStatus |
| 11 | parameterRequest | 12 | presetSavedStatus |
| 12 | presetDataRequest | 13 | replaceNodeStatus |
| 13 | presetNameRequest | 14 | unsupportedMessageStatus |
| 14 | presetNamesRequest | 15 | usbAndLineOutGainStatus {valueDB} |
| 15 | renamePresetRequest | 16 | tunerStatus |
| 16 | replaceNodeRequest | 17 | productIdStatus {LTX100=0, LTX50=1} |
| 17 | savePresetAsRequest | 18 | outputPowerStatus {One…Four} |
| 18 | setParameterRequest | 19 | presetModeStatus {Sequential, Bank} |
| 19 | swapPresetRequest | 21 | ftswBrightnessStatus |
| 20 | tunerRequest | 22 | ftswVersionStatus |
| 21 / 22 | usbAndLineOutGainRequest / set | 23 | movePresetStatus |
| 23 | productIdRequest | 24 | ftswUpdateStatus |
| 24 / 25 | outputPowerRequest / set | | |
| 26 / 27 | presetModeRequest / set | | |
| 29 / 32 | ftswBrightnessRequest / set | | |
| 30 | ftswVersionRequest | | |
| 33 | movePresetRequest | | |

**Telling `mp` and `ltx` apart safely:** requests 3, 4, 7 and 14 mean the same in both
families, and the reply to 14 is status 12 on the Micro Plus but 11 on an LTX. A tool
that is not sure should send only those four first. Several other numbers are harmless
in one family and writes in the other (`mp` 15 = product id, `ltx` 15 = rename preset;
`ltx` 23 = product id, `mp` 23 = set USB gain; `ltx` 8 = heartbeat, `mp` 8 = load preset).

## Micro Plus (`mp`)

See [MUSTANG_MICRO_PLUS_PROTOCOL.md](MUSTANG_MICRO_PLUS_PROTOCOL.md).

## Open questions (what `amp-probe` asks the amps)

* GT/GTX/Rumble: which service UUID form shows up in Web Bluetooth/CoreBluetooth/WinRT,
  write type, MTU, whether the replies match the table above, the 500–502 payload format
  (requested with 424 RetrievePreset), which "Sync"/"Request" messages answer.
* LTX: whether `…2dcd` is its service, whether it uses the Micro Plus framing and
  heartbeat, what it answers.
* All: advertised name and manufacturer data per model, USB audio interfaces (playback
  and capture), whether Bluetooth audio and BLE control run side by side.
