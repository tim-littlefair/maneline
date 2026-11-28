// Tests for the probe core in index.html, against simulated amps.
// Run: node --test amp-probe/tests/
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function loadCore() {
  const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
  const code = html.match(/<script id="probe-core">([\s\S]*?)<\/script>/)[1];
  const module = { exports: {} };
  new Function('module', code)(module);
  return module.exports;
}
const P = loadCore();
P.setSpeed(0.002);
const utf8hex = s => Buffer.from(s, 'utf8').toString('hex');

// ---- simulated amps --------------------------------------------------------------------
const PRIVATE_STRINGS = ['AA:BB:CC:11:22:33', 'MyHomeWifi', 'NeighbourNet', '192.168.1.50', 'jane@example.com', 'Jane',
  'Jane Doe', '0f8b1c1e-1111-4222-8333-444455556666'];

function lz4Literal(bytes) {   // a valid LZ4 block made of one literal run
  const out = []; let n = bytes.length;
  out.push((n >= 15 ? 15 : n) << 4);
  if (n >= 15) { n -= 15; while (n >= 255) { out.push(255); n -= 255; } out.push(n); }
  return Uint8Array.from([...out, ...bytes]);
}

class FakeGt {
  constructor(opts = {}) {
    this.slot = 3; this.edited = !!opts.edited; this.sink = null; this.writes = [];
    this.params = { gain: 0.4, volume: -6 };
    this.presets = [1, 2, 3, 4, 5].map(n => ({ slotNumber: n, displayName: 'Preset ' + n, product_id: 'mustang-gt-100',
      preset_id: n === 2 ? '0f8b1c1e-1111-4222-8333-444455556666' : 'id' + n, author: n === 2 ? 'jane@example.com' : 'Fender',
      is_factory_default: n !== 2, isEmpty: n === 5 }));
  }
  emit(name, body) { const b = P.gtMsg(name, body); setTimeout(() => this.sink(b), 0); }
  async write(bytes) {
    this.writes.push(P.hex(bytes));
    const s = new P.Session('gt', null); const m = P.decodeMessage('gt', bytes, new P.Privacy(), {});
    const v = m.value || {};
    switch (m.name) {
      case 'toneSync':
        this.emit('packetBegin', { packetType: 'TONE_SYNC' });
        this.emit('systemStatus', { address: 'AA:BB:CC:11:22:33', firmwareVersion: '3.0.43', wifiState: 'On', btName: 'Mustang GT', productId: 'mustang-gt-100' });
        for (const p of this.presets) this.emit('presetInfoMessage', p);
        this.emit('productIdentificationStatus', { id: 'mustang-gt-100' });
        this.emit('packetEnd', { packetType: 'TONE_SYNC' });
        break;
      case 'syncCurrentPresetToTone':
        this.emit('packetBegin', { packetType: 'SYNC_CURRRENT_PRESET_TONE' });
        this.emit('presetEditedStatus', { presetEdited: this.edited });
        this.emit('presetInfoMessage', this.presets[this.slot - 1]);
        this.emit('nodeStatus', { nodeStatus: [{ nodeId: 'DUBS_LinearGain_1', fenderId: 'DUBS_LinearGain' }, { nodeId: 'DUBS_Twin65_1', fenderId: 'DUBS_Twin65', bypass: true }] });
        for (const [k, x] of Object.entries(this.params)) this.emit('dspUnitParameterStatus', { nodeId: 'DUBS_LinearGain_1', parameterId: k, floatParameter: x });
        this.emit('processorPercentageAvailableStatus', { percent: 36.5 });
        this.emit('packetEnd', { packetType: 'SYNC_CURRRENT_PRESET_TONE' });
        break;
      case 'loadPresetIfValid':
        this.slot = v.slot; this.edited = false;
        this.emit('packetBegin', { packetType: 'LOAD_PRESET_IF_VALID' });
        this.emit('presetInfoMessage', this.presets[this.slot - 1]);
        this.emit('packetEnd', { packetType: 'LOAD_PRESET_IF_VALID' });
        break;
      case 'setDspUnitParameter':
        this.params[v.parameterId] = v.floatParameter; this.edited = true;
        this.emit('presetEditedStatus', { presetEdited: true });
        break;
      case 'getDspUnitParameter':
        this.emit('dspUnitParameterStatus', { nodeId: v.nodeId, parameterId: v.parameterId, floatParameter: this.params[v.parameterId] });
        break;
      case 'retrievePreset': {
        const json = Buffer.from(JSON.stringify({ info: { displayName: 'Preset 3', author: 'Jane Doe', preset_id: 'id3' }, audioGraph: { nodes: [] } }));
        this.emit('packetStartMTU', { numBytes: json.length, id: 7 });
        this.emit('packetDataMTU', { seqNumber: 0, id: 7, data: json.subarray(0, 40) });
        this.emit('packetDataMTU', { seqNumber: 1, id: 7, data: json.subarray(40) });
        this.emit('packetStopMTU', { id: 7 });
        break;
      }
      case 'setlistManagerSync':
        this.emit('setlistStatus', { slot: 1, displayName: 'FAVORITES' }); this.emit('setlistSyncEnd', {}); break;
      case 'btStatusRequest': this.emit('btStatus', { state: 'BLE_AND_BLUETOOTH_STREAMING_ON' }); break;
      case 'loopbackTest': this.emit('loopbackTest', v); break;
      default: break;
    }
    this.emit('networkStatus', { connectionStatus: 'CONNECTED', connectedSSID: 'MyHomeWifi', connectedIP: '192.168.1.50', availableNetworks: [{ ssid: 'NeighbourNet', signalStrength: 3 }] });
  }
}

class FakeFramed {
  constructor(family, opts = {}) {
    this.family = family; this.index = 4; this.dirty = !!opts.dirty; this.sink = null; this.writes = []; this.silent = !!opts.silent;
    this.names = Array.from({ length: 10 }, (_, i) => i === 7 ? 'EMPTY' : 'Name ' + (i + 1));
    this.amp = { gain: 0.5, volume: -5 };
  }
  preset() {
    return { info: { displayName: this.names[this.index], author: 'Jane Doe', preset_id: '0f8b1c1e-1111-4222-8333-444455556666' },
      audioGraph: { nodes: [{ nodeId: 'stomp', FenderId: 'DUBS_Unknown' }, { nodeId: 'amp', FenderId: 'ACD_Twin65GT', dspUnitParameters: { ...this.amp } }] } };
  }
  status(name, body) {
    const env = P.FAMILIES[this.family].envelope;
    const b = P.encodeMsg(env, { ampStatus: { responseType: 'IS_LAST_ACK', [name]: body } }, this.family);
    for (const f of P.toFrames(b)) setTimeout(() => this.sink(f), 0);
  }
  async write(bytes) {
    this.writes.push(P.hex(bytes));
    if (this.silent) return;
    assert.equal(bytes[0], 0x35, 'probe requests fit in one frame');
    const m = P.decodeMessage(this.family, bytes.subarray(3), new P.Privacy(), {});
    if (!m.name.startsWith('request:')) return;
    const name = m.name.slice(8), v = m.value || {};
    const lz = () => ({ compressedPresetJson: lz4Literal(Buffer.from(JSON.stringify(this.preset()))) });
    const nameStatus = this.family === 'mp' ? 'presetNamesStatus' : 'presetNamesStatus';
    switch (name) {
      case 'firmwareVersionRequest': this.status('firmwareVersionStatus', { version: '1.0.29' }); break;
      case 'presetNamesRequest': this.status(nameStatus, { presetName: this.names }); break;
      case 'currentPresetRequest':
        this.status('currentPresetStatus', { presetIndex: this.index, isDirty: this.dirty, dummyField: true });
        this.status('currentPresetDataStatus', lz()); break;
      case 'loadPresetRequest':
        this.index = v.newPresetIndex || 0; this.dirty = false;
        this.status('currentPresetStatus', { presetIndex: this.index, dummyField: true });
        this.status('currentPresetDataStatus', lz()); break;
      case 'presetDataRequest': this.status('presetDataStatus', { presetIndex: v.presetIndex || 0, ...lz() }); break;
      case 'parameterRequest': this.status('parameterStatus', { nodeId: v.nodeId, parameterId: v.parameterId, floatParameter: this.amp[v.parameterId] }); break;
      case 'setParameterRequest': this.amp[v.parameterId] = v.floatParameter; this.dirty = true; break;
      default: break;
    }
  }
}

function attach(fake, family) {
  const s = new P.Session(family, fake);
  fake.sink = b => s.onData(b);
  return s;
}
function report(s, r, family, extra = {}) {
  return P.buildReport(Object.assign({ results: r, session: s, family, serviceUuid: 'x', date: '2026-11-27',
    device: { name: 'Test amp' }, answers: {} }, extra));
}
function assertNoPrivate(text) {
  for (const p of PRIVATE_STRINGS) {
    assert.ok(!text.includes(p), `report contains ${p}`);
    assert.ok(!text.includes(utf8hex(p)), `report contains ${p} as hex`);
  }
}

// ---- tests -----------------------------------------------------------------------------
test('the reduced schema has every request the code sends', () => {
  // build_schema.py keeps only the requests the probes send; the read loops skip a missing
  // name silently, so check the code against the embedded schema here
  const src = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
  const list = name => [...src.match(new RegExp(`const ${name} = \\[([\\s\\S]*?)\\];`))[1].matchAll(/\['?'([A-Za-z][A-Za-z0-9]*)'/g)].map(m => m[1]);
  const quoted = name => [...src.match(new RegExp(`const ${name} = \\[([\\s\\S]*?)\\];`))[1].matchAll(/'([A-Za-z][A-Za-z0-9]*)'/g)].map(m => m[1]);
  const fields = type => new Set(P.SCHEMA.messages[type].map(f => f[1]));
  const gt = fields('gt.FenderMessage'), mp = fields('mp.MustangLtxToneRequest'), ltx = fields('ltx.MustangLtxToneRequest');
  const gtSent = [...src.matchAll(/gtMsg\('([A-Za-z][A-Za-z0-9]*)'/g)].map(m => m[1]).concat(list('GT_EXTRA_READS'));
  const toneSent = [...src.matchAll(/toneMsg\([^,]+,\s*'([A-Za-z][A-Za-z0-9]*)'/g)].map(m => m[1]);
  for (const n of gtSent) assert.ok(gt.has(n), 'gt ' + n);
  for (const n of [...toneSent, ...quoted('MP_READS')]) assert.ok(mp.has(n), 'mp ' + n);
  for (const n of [...toneSent.filter(n => n !== 'usbAudioGainInfoRequest' && n !== 'eqRequest'), ...quoted('LTX_READS')]) assert.ok(ltx.has(n), 'ltx ' + n);
  assert.ok(Object.keys(P.SCHEMA.messages).length < 200);   // reduced, not the app's full set
});

test('GT encodings match the bytes captured from Fender Tone (#148)', () => {
  assert.equal(P.hex(P.gtMsg('toneSync')), 'aa3800');
  assert.equal(P.hex(P.gtMsg('loadPresetIfValid', { slot: 198 })), 'ba190308c601');
  assert.equal(P.hex(P.gtMsg('setDspUnitParameter', { nodeId: 'DUBS_LinearGain_1', parameterId: 'gain', floatParameter: 0.4 })),
    'b2061e0a11' + utf8hex('DUBS_LinearGain_1') + '1204' + utf8hex('gain') + '1dcdcccc3e');
  // a oneof value of zero is still sent
  assert.ok(P.hex(P.gtMsg('setDspUnitParameter', { nodeId: 'a', parameterId: 'b', floatParameter: 0 })).endsWith('1d00000000'));
});

test('Micro Plus encodings match maneline', () => {
  const frame = b => P.hex(P.toFrames(b)[0]);
  assert.equal(frame(P.modal('mp', 'SYNC_BEGIN')), '3500021a00');
  assert.equal(frame(P.modal('mp', 'SYNC_END')), '3500041a020801');
  assert.equal(frame(P.toneMsg('mp', 'firmwareVersionRequest')), '3500040a023a00');
  assert.equal(frame(P.toneMsg('mp', 'presetNamesRequest')), '3500040a027200');
  assert.equal(frame(P.toneMsg('mp', 'currentPresetRequest', { requestPresetData: true })), '3500060a0422020801');
  assert.equal(frame(P.toneMsg('mp', 'heartbeat')), '3500050a03c20100');
  assert.equal(frame(P.toneMsg('mp', 'loadPresetRequest', { newPresetIndex: 71 })), '3500060a0442020847');
});

test('GT read and control test against a simulated GT100', async () => {
  const fake = new FakeGt();
  const s = attach(fake, 'gt'), r = new P.Results();
  await P.gtRead(s, r);
  const st = Object.fromEntries(r.checks.map(c => [c.id, c.status]));
  assert.equal(st.toneSync, 'ok'); assert.equal(st.current, 'ok'); assert.equal(st['x-getparam'], 'ok');
  assert.equal(st['x-retrieve'], 'ok'); assert.equal(st['x-setlistManagerSync'], 'ok');
  assert.equal(r.data.system.firmwareVersion, '3.0.43');
  assert.equal(r.data.system.address, '[removed]');
  assert.equal(r.data.presets.length, 5);
  assert.match(r.data.transfer.summary, /plain JSON/);
  await P.gtControl(s, r);
  const st2 = Object.fromEntries(r.checks.map(c => [c.id, c]));
  assert.equal(st2['c-switch'].status, 'ok', st2['c-switch'].detail);
  assert.equal(st2['c-knob'].status, 'ok', st2['c-knob'].detail);
  assert.equal(fake.slot, 3); assert.equal(fake.params.gain, 0.4); assert.equal(fake.edited, false);
  assert.ok(fake.writes.includes('ba19020804'), 'switched to slot 4');
  const text = report(s, r, 'gt');
  assert.match(text, /mustang-gt-100/);
  assert.match(text, /\| 2 \| Preset 2 \| {2}\| {2}\| mustang-gt-100 \| \[removed\] \|/);
  assert.match(text, /\.\./, 'raw log shows removed bytes');
  assertNoPrivate(text);
});

test('GT control test leaves a preset with unsaved changes alone', async () => {
  const fake = new FakeGt({ edited: true });
  const s = attach(fake, 'gt'), r = new P.Results();
  await P.gtRead(s, r);
  await P.gtControl(s, r);
  assert.equal(r.checks.find(c => c.id === 'c-switch').status, 'skipped');
  assert.equal(r.checks.find(c => c.id === 'x-retrieve').status, 'skipped');
  assert.ok(!fake.writes.some(w => w.startsWith('ba19') || w.startsWith('b206')), 'no select and no parameter write');
});

for (const family of ['mp', 'ltx']) {
  test(`${family}: family check, read and control test`, async () => {
    const fake = new FakeFramed(family);
    const s = attach(fake, family === 'mp' ? 'mp' : 'ltx'), r = new P.Results();
    await P.framedRead(s, r, family);
    s.stopHeartbeat();
    assert.equal(r.data.family, family);
    assert.equal(r.checks.find(c => c.id === 'family').status, 'ok');
    assert.equal(r.checks.find(c => c.id === 'handshake').status, 'ok', r.checks.find(c => c.id === 'handshake').detail);
    assert.equal(r.data.names.length, 10);
    assert.equal(r.data.current.index, 4);
    assert.equal(r.data.presetJson.info.author, '[removed]');
    const beat = P.hex(P.toFrames(P.toneMsg(family, 'heartbeat'))[0]);
    assert.ok(fake.writes.includes(beat), 'heartbeat sent');
    await P.framedControl(s, r);
    s.stopHeartbeat();
    for (const id of ['c-switch', 'c-knob']) { const c = r.checks.find(x => x.id === id); assert.equal(c.status, 'ok', c.detail); }
    assert.equal(fake.index, 4); assert.equal(fake.amp.gain, 0.5); assert.equal(fake.dirty, false);
    const text = report(s, r, family);
    assertNoPrivate(text);
    assert.match(text, /Name 10/);
  });
}

test('an LTX-numbered amp behind the Micro Plus service is recognised by its reply', async () => {
  const fake = new FakeFramed('ltx');
  const s = attach(fake, 'mp'), r = new P.Results();
  await P.framedRead(s, r, 'mp');
  s.stopHeartbeat();
  assert.equal(r.data.family, 'ltx');
  assert.equal(r.checks.find(c => c.id === 'family').status, 'warn');
  // before the reply only requests with the same meaning in both families were sent
  const first = fake.writes.slice(0, 3);
  assert.deepEqual(first, ['3500021a00', '3500040a023a00', '3500040a027200']);
});

test('a silent amp gives "no answer" and sends nothing family-specific', async () => {
  const fake = new FakeFramed('ltx', { silent: true });
  const s = attach(fake, 'ltx'), r = new P.Results();
  await P.framedRead(s, r, 'ltx');
  assert.equal(r.checks.find(c => c.id === 'family').status, 'noanswer');
  assert.equal(fake.writes.length, 3);
  assert.equal(s.heartbeatTimer, null);
});

test('privacy filter catches addresses in free text and JSON', () => {
  const p = new P.Privacy();
  assert.equal(p.scrub('mail me at jane@example.com from 10.0.0.7, amp AA:BB:CC:11:22:33'),
    'mail me at [removed] from [removed], amp [removed]');
  assert.equal(p.scrub('1.0.29.4', 'firmwareVersion'), '1.0.29.4');
  const [v, changed] = p.json({ info: { author: 'Fender', source_id: 'abc', user: 'x' }, nodes: [] });
  assert.ok(changed); assert.equal(v.info.author, 'Fender'); assert.equal(v.info.source_id, 'id-1'); assert.equal(v.info.user, '[removed]');
});

test('the report filters error messages and the device name', () => {
  const r = new P.Results();
  r.check('connect', 'Connect over Bluetooth', 'failed', 'NetworkError: org.bluez.Error.Failed /org/bluez/hci0/dev_AA_BB_CC_11_22_33 (AA:BB:CC:11:22:33)');
  r.check('fw', 'Session start', 'ok', 'firmware 1.0.29.4');
  const st = { results: r, date: '2026-11-27', device: { name: 'Mustang GT at 10.0.0.7' } };
  const text = P.buildReport(st);
  for (const bad of ['AA_BB_CC', 'AA:BB:CC', '10.0.0.7']) assert.ok(!text.includes(bad), bad);
  assert.ok(text.includes('firmware 1.0.29.4'));
  assert.ok(/a hardware address \(MAC\) \(in an error message or device name\)/.test(text));
  assert.ok(st.reportReasons.length >= 2);
});

test('the printed report renders Markdown and escapes everything', () => {
  const md = ['# Report', '', '- Tool: `x` **y**', '', '| Check | Details |', '| --- | --- |', '| A \\| B | <img src=x onerror=alert(1)> |', '',
    '> note <script>', '', '```', '<b>raw</b> | 00 11', '```', '', 'plain text'].join('\n');
  const html = P.mdToHtml(md);
  assert.ok(html.includes('<h1>Report</h1>'));
  assert.ok(html.includes('<li>Tool: <code>x</code> <b>y</b></li>'));
  assert.ok(html.includes('<td>A | B</td>'));
  assert.ok(html.includes('<pre>&lt;b&gt;raw&lt;/b&gt; | 00 11</pre>'));
  assert.ok(html.includes('<blockquote>note &lt;script&gt;</blockquote>'));
  assert.ok(html.includes('<p>plain text</p>'));
  assert.ok(!/<img|<script|<b>raw/.test(html));
});

test('Linux lines find the amp, prefer LE, and the cleanup puts it back, from any shell', () => {
  const { execFileSync } = require('node:child_process');
  const os = require('node:os');
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'amp-')), bin = path.join(home, 'bin');
  fs.mkdirSync(bin);
  // fake BlueZ tools: paired devices from a file, the preference kept in a file, every call logged
  const tool = (name, body) => fs.writeFileSync(path.join(bin, name), '#!/bin/bash\necho "' + name + ' $*" >> "$HOME/calls"\n' + body, { mode: 0o755 });
  tool('bluetoothctl', [
    'case "$1 $2" in',
    '  "devices Paired") cat "$HOME/paired";;',
    '  "info 12:34:56:78:9A:BC") echo "	UUID: Vendor specific (90559580-b707-11ee-acb1-7b7e30f1af54)";;',
    '  *) true;;',
    'esac'].join('\n'));
  tool('busctl', [
    'case "$2" in',
    '  get-property) echo "s \\"$(cat "$HOME/bearer")\\"";;',
    '  set-property) echo "$4 $8" > "$HOME/bearer";;',
    'esac'].join('\n'));
  const env = { HOME: home, PATH: bin + ':' + process.env.PATH };
  const run = (text, shell = 'bash') => execFileSync(shell, ['-c', text], { env, encoding: 'utf8' });
  const read = f => fs.readFileSync(path.join(home, f), 'utf8');
  const saved = () => fs.existsSync(path.join(home, 'amp-check-bearer.txt'));
  const amp = 'Device 12:34:56:78:9A:BC Mustang Micro Plus\nDevice 11:22:33:44:55:66 Jane\'s Phone\n';
  fs.writeFileSync(path.join(home, 'paired'), amp);
  fs.writeFileSync(path.join(home, 'bearer'), 'bredr');

  // the copied text has no single quote or backslash pair inside bash -c '...': fish, zsh and bash keep it as is
  const prep = P.linuxPrepCommands('Mustang Micro Plus');
  const inner = prep.slice(prep.indexOf("'") + 1, prep.lastIndexOf("' amp-check"));
  assert.ok(!inner.includes("'") && !inner.includes('\\\\'));

  let out = run(prep);
  assert.match(out, /Done: the amp \(12:34:56:78:9A:BC\)/);
  assert.equal(read('bearer').trim(), '/org/bluez/hci0/dev_12_34_56_78_9A_BC le');
  assert.equal(read('amp-check-bearer.txt'), '12:34:56:78:9A:BC\nbredr\n');
  assert.match(read('calls'), /bluetoothctl disconnect 12:34:56:78:9A:BC/);
  run(prep, 'sh');   // again, from a POSIX shell: the saved original must survive
  assert.equal(read('amp-check-bearer.txt'), '12:34:56:78:9A:BC\nbredr\n');

  out = run(P.linuxCleanupCommands(), 'sh');
  assert.match(out, /Put back: the amp \(12:34:56:78:9A:BC\) prefers bredr again/);
  assert.equal(read('bearer').trim(), '/org/bluez/hci0/dev_12_34_56_78_9A_BC bredr');
  assert.ok(!saved());
  assert.match(read('calls'), /bluetoothctl connect 12:34:56:78:9A:BC/);
  assert.match(run(P.linuxCleanupCommands()), /Nothing to put back/);

  // another name (renamed, or a different classic name): found by the Fender service instead
  out = run(P.linuxPrepCommands("My Own Amp"));
  assert.match(out, /Done: the amp \(12:34:56:78:9A:BC\)/);
  run(P.linuxCleanupCommands());

  // no Fender amp paired: nothing changes
  fs.writeFileSync(path.join(home, 'paired'), 'Device 11:22:33:44:55:66 Jane\'s Phone\n');
  out = run(P.linuxPrepCommands('Mustang GT100'));
  assert.match(out, /No amp is paired with this computer: nothing to change/);
  assert.ok(!saved());
  fs.rmSync(home, { recursive: true });
});

test('Windows device ids lose the address', () => {
  const p = new P.Privacy();
  assert.equal(p.scrub('BTHENUM\\DEV_123456789ABC | USB\\VID_1ED8&PID_003A&MI_00'), 'BTHENUM\\[removed] | USB\\VID_1ED8&PID_003A&MI_00');
});

test('LZ4 block decoder handles matches', () => {
  // "abcabcabcabc": 3 literals, then a match of 9 at offset 3
  const block = Uint8Array.from([0x35, 0x61, 0x62, 0x63, 0x03, 0x00]);
  assert.equal(Buffer.from(P.lz4Block(block)).toString(), 'abcabcabcabc');
});
