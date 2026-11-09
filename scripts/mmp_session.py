"""Own one persistent Micro Plus connection and serialize browser commands."""
import asyncio
import time
from mmp_ble import Controller, KEEPALIVE, KEEPALIVE_INTERVAL, NAME, RESPONSE, SCAN_FILTER
from mmp_bluez import make_client

# Longest a switch request waits for the amp to reconnect after an idle disconnect.
# Usually about 3 s (find the amp, connect, handshake); once 14 s, just after Bluetooth
# audio had started streaming. The HTTP handler allows a request 40 s.
WAKE_TIMEOUT = 25


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
        return {'connected': connected, 'phase': self.phase, 'error': self.error,
                'device': NAME, 'connections': self.connections,
                'connected_since': self.connected_since,
                'asleep': self.asleep, 'idle_disconnect': self.idle_disconnect,
                'preset': preset, 'name': name,
                'presets': [{'preset': i + 1, 'name': n} for i, n in enumerate(names)]}

    async def switch(self, slot):
        if type(slot) is not int or not 1 <= slot <= 100:
            raise ValueError('Choose a preset number from 1 to 100.')
        async with self.lock:
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
            try:
                await controller.switch(slot)
                self.last_switch = time.monotonic()
                return self.snapshot()
            except Exception:
                self.error = ('The amp did not confirm the selected preset. '
                              'Reconnecting to check which preset is active.')
                self.controller = None
                self.reset.set()
                raise RuntimeError(self.error) from None

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
        from bleak import BleakScanner
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
                device = await BleakScanner.find_device_by_filter(
                    lambda d, advert: (d.name == NAME or advert.local_name == NAME)
                    and (self.device_id is None or d.address == self.device_id),
                    **SCAN_FILTER, timeout=10)
                if device is None:
                    raise RuntimeError('Micro Plus not found. Turn it on and close Fender Tone on other devices. '
                                       'If it is already on, switch it off and on again: its Bluetooth can '
                                       'stop answering while the amp itself keeps working.')
                # Pin the first selected device across reconnects.
                self.device_id = device.address
                # On Linux with BlueZ's bearer API this holds only the LE link, so
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
                self.connected_since = None
                self.phase = 'asleep' if self.asleep else 'disconnected'
            if not self.asleep:
                await asyncio.sleep(2)
