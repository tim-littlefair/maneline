"""Own one persistent Micro Plus connection and serialize browser commands."""
import asyncio
import json
import time
# AuditionController is the BLE Controller plus audition and stored-preset reads.
from mmp_audition import AuditionController as Controller
from mmp_ble import KEEPALIVE, KEEPALIVE_INTERVAL, NAME, RESPONSE, SCAN_FILTER, SERVICE
from mmp_bluez import find_device, make_client, not_found

# Longest a switch request waits for the amp to reconnect after an idle disconnect.
# Usually about 3 s (find the amp, connect, handshake); once 14 s, just after Bluetooth
# audio had started streaming. The HTTP handler allows a request 40 s.
WAKE_TIMEOUT = 25
# A preset JSON document is about 5 KB; anything much larger is not a preset.
MAX_PRESET_JSON = 64 * 1024
MAX_NAME = 16               # longest preset name the amp saves


def check_preset(preset):
    """Raise ValueError unless preset looks like a Fender preset JSON document."""
    if not isinstance(preset, dict) or not isinstance(preset.get('info', {}), dict):
        raise ValueError('Send the preset as a JSON object.')
    nodes = preset.get('audioGraph', {}).get('nodes') if isinstance(preset.get('audioGraph'), dict) else None
    if not isinstance(nodes, list) or not any(isinstance(n, dict) and n.get('nodeId') == 'amp' for n in nodes):
        raise ValueError('The preset needs an audioGraph with an amp node.')
    if len(json.dumps(preset)) > MAX_PRESET_JSON:
        raise ValueError('The preset is too large.')


class MicroPlusSession:
    def __init__(self, device=None, log=None, idle_disconnect=None):
        self.device_id = device
        self.log = log
        self.controller = None
        self.client = None
        self.error = None
        self.phase = 'connecting'
        self.connections = 0
        self.connected_since = None
        self.lock = asyncio.Lock()
        self.reset = asyncio.Event()
        # Battery saving (off by default): after idle_disconnect seconds without a preset
        # switch, and unless Bluetooth audio is streaming to the amp, close the connection
        # and reconnect on the next switch. The heartbeat alone keeps the amp's radio busy.
        self.idle_disconnect = idle_disconnect
        self.asleep = False
        self.waking = False
        self.wake = asyncio.Event()
        self.ready = asyncio.Event()
        self.last_switch = time.monotonic()
        self.next_audio_check = 0
        self.names = []           # shown while asleep or waking
        self.last_preset = None
        self.auditioned = None    # {'name', 'slot'} while an audition is playing
        self.stored = {}          # slot -> stored preset JSON, for this connection

    def snapshot(self):
        controller = self.controller
        connected = bool(controller and self.client and self.client.is_connected)
        if connected:
            names, preset, name = controller.names, controller.slot, controller.status()['name']
        elif self.asleep or self.waking:
            names, preset = self.names, self.last_preset
            name = names[preset - 1] if preset and preset <= len(names) else None
        else:
            names, preset, name = [], None, None
        auditioned = self.auditioned if connected and self.auditioned and \
            self.auditioned['slot'] == preset else None
        if auditioned:
            name = auditioned['name']
        return {'connected': connected, 'phase': self.phase, 'error': self.error,
                'device': NAME, 'connections': self.connections,
                'connected_since': self.connected_since,
                'asleep': self.asleep, 'idle_disconnect': self.idle_disconnect,
                'preset': preset, 'name': name, 'audition': auditioned,
                'firmware': getattr(controller, 'firmware', None) if connected else None,
                'presets': [{'preset': i + 1, 'name': n} for i, n in enumerate(names)]}

    async def _connected(self):
        """The controller, after waking the amp if needed. Call with self.lock held."""
        if self.idle_disconnect is not None and not self.ready.is_set():
            # Asleep, or already reconnecting: wake up and wait for the handshake.
            self.wake.set()
            try:
                await asyncio.wait_for(self.ready.wait(), WAKE_TIMEOUT)
            except TimeoutError:
                raise RuntimeError(self.error or 'The Micro Plus did not reconnect in time. '
                                   'Try again.') from None
        controller = self.controller
        if not controller or not self.client.is_connected:
            raise RuntimeError('The Micro Plus is disconnected. Wait for it to reconnect.')
        return controller

    async def switch(self, slot):
        if type(slot) is not int or not 1 <= slot <= 100:
            raise ValueError('Choose a preset number from 1 to 100.')
        async with self.lock:
            controller = await self._connected()
            try:
                await controller.switch(slot)
                self.auditioned = None          # selecting a preset ends an audition
                self.last_switch = time.monotonic()
                return self.snapshot()
            except Exception:
                self.error = ('The amp did not confirm the selected preset. '
                              'Reconnecting to check which preset is active.')
                self.controller = None
                self.reset.set()
                raise RuntimeError(self.error) from None

    async def audition(self, preset, slot=None):
        """Play a preset without saving it, optionally on a chosen (reserved) slot.
        Nothing is written to the amp's preset memory."""
        check_preset(preset)
        if slot is not None and (type(slot) is not int or not 1 <= slot <= 100):
            raise ValueError('Choose a slot from 1 to 100.')
        async with self.lock:
            controller = await self._connected()
            if slot is not None and controller.slot != slot:
                try:
                    await controller.switch(slot)
                except Exception:
                    raise RuntimeError('The amp did not confirm the slot for the audition.') from None
            try:
                confirmed = await controller.audition(preset)
            except (RuntimeError, TimeoutError) as error:
                raise RuntimeError(str(error) or 'The amp did not confirm the audition.') from None
            self.auditioned = {'name': preset.get('info', {}).get('displayName', ''),
                               'slot': confirmed}
            self.last_switch = time.monotonic()
            return self.snapshot()

    async def save(self, slot, preset, name=None):
        """WRITE a preset into a slot (it is not selected). Returns the preset as the
        amp stored it, read back. Make a backup first (mmp_backup.py)."""
        check_preset(preset)
        if type(slot) is not int or not 1 <= slot <= 100:
            raise ValueError('Choose a slot from 1 to 100.')
        name = (name if name is not None else preset.get('info', {}).get('displayName', '')).strip()
        if not name or len(name) > MAX_NAME:
            # a longer name is silently ignored by the amp: no confirmation, nothing saved
            # (firmware 1.0.29, 2026-11-10: 16 characters saved, 20 not)
            raise ValueError('Give the preset a name of 1 to {} characters.'.format(MAX_NAME))
        preset = json.loads(json.dumps(preset))
        preset.setdefault('info', {})['displayName'] = name     # the stored name and the list's agree
        async with self.lock:
            controller = await self._connected()
            self.stored.pop(slot, None)
            try:
                stored = await controller.save(slot, preset, name)
            except (RuntimeError, TimeoutError) as error:
                raise RuntimeError(str(error) or 'The amp did not confirm the save.') from None
            self.stored[slot] = stored
            self.last_switch = time.monotonic()
            return {'slot': slot, 'name': name, 'preset': stored}

    async def stored_preset(self, slot):
        """The preset stored in slot, as JSON (cached for this connection)."""
        if type(slot) is not int or not 1 <= slot <= 100:
            raise ValueError('Choose a slot from 1 to 100.')
        async with self.lock:
            if slot in self.stored:
                return self.stored[slot]
            controller = await self._connected()
            try:
                preset = await controller.stored_preset(slot)
            except (RuntimeError, TimeoutError) as error:
                raise RuntimeError(str(error) or 'The amp did not send the preset.') from None
            self.stored[slot] = preset
            return preset

    async def _idle_long_enough(self, client):
        """True when the connection should close to save the amp's battery."""
        if self.idle_disconnect is None or self.lock.locked():
            return False          # disabled, or a switch is waiting for its confirmation
        now = time.monotonic()
        if now - self.last_switch < self.idle_disconnect or now < self.next_audio_check:
            return False
        self.next_audio_check = now + 1        # ask BlueZ at most once a second
        streaming = getattr(client, 'audio_streaming', None)   # absent with Bleak (macOS)
        if streaming and await streaming():
            return False
        return not self.lock.locked()          # a switch may have started meanwhile

    async def run(self):
        while True:
            if self.asleep:
                self.phase = 'asleep'
                await self.wake.wait()
                self.asleep = False
                self.waking = True
            self.wake.clear()
            self.reset.clear()
            self.phase = 'connecting'
            try:
                device = await find_device(NAME, self.device_id, timeout=10, service=SERVICE,
                                           **SCAN_FILTER)
                if device is None:
                    raise RuntimeError(not_found(
                        'Micro Plus not found. Turn it on and close Fender Tone on other devices. '
                        'If it is already on, switch it off and on again: its Bluetooth can '
                        'stop answering while the amp itself keeps working.'))
                # Pin the first selected device across reconnects.
                self.device_id = device.address
                # On Linux this holds only the LE link (BlueZ's bearer API), so
                # Bluetooth audio to the amp keeps playing across connects and exits.
                client = await make_client(device, disconnected_callback=lambda _: self.reset.set())
                async with client:
                    self.client = client
                    controller = Controller(client, self.log)
                    await client.start_notify(RESPONSE, controller.notify)
                    await controller.initialize()
                    self.controller = controller
                    self.connections += 1
                    self.connected_since = time.time()
                    self.phase = 'connected'
                    self.error = None
                    self.waking = False
                    self.last_switch = time.monotonic()    # the idle time starts now
                    self.ready.set()
                    while not self.reset.is_set():
                        try:
                            await asyncio.wait_for(self.reset.wait(), timeout=KEEPALIVE_INTERVAL)
                        except TimeoutError:
                            if controller.failure:
                                raise controller.failure
                            # Keep-alives must continue while a switch awaits its reply.
                            await controller.send(KEEPALIVE)
                            if await self._idle_long_enough(client):
                                self.names = list(controller.names)
                                self.last_preset = controller.slot
                                self.asleep = True
                                # From now on a switch waits for a new connection
                                # instead of using this one while it closes.
                                self.ready.clear()
                                self.controller = None
                                break
            except asyncio.CancelledError:
                raise
            except Exception as error:
                self.error = str(error) or 'The Bluetooth connection was lost.'
                self.waking = False
            finally:
                self.ready.clear()
                self.controller = None
                self.client = None
                self.auditioned = None
                self.stored = {}
                self.connected_since = None
                self.phase = 'asleep' if self.asleep else 'disconnected'
            if not self.asleep:
                await asyncio.sleep(2)
