"""Own one persistent Micro Plus connection and serialize browser commands."""
import asyncio
import time
from mmp_ble import Controller, KEEPALIVE, KEEPALIVE_INTERVAL, NAME, RESPONSE, SERVICE


class MicroPlusSession:
    def __init__(self, device=None, log=None):
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

    def snapshot(self):
        controller = self.controller
        connected = bool(controller and self.client and self.client.is_connected)
        return {'connected': connected, 'phase': self.phase, 'error': self.error,
                'device': NAME, 'connections': self.connections,
                'connected_since': self.connected_since,
                'preset': controller.slot if connected else None,
                'name': controller.status()['name'] if connected else None,
                'presets': [{'preset': i + 1, 'name': name}
                            for i, name in enumerate(controller.names)] if connected else []}

    async def switch(self, slot):
        if type(slot) is not int or not 1 <= slot <= 100:
            raise ValueError('Choose a preset number from 1 to 100.')
        async with self.lock:
            controller = self.controller
            if not controller or not self.client.is_connected:
                raise RuntimeError('The Micro Plus is disconnected. Wait for it to reconnect.')
            try:
                await controller.switch(slot)
                return self.snapshot()
            except Exception:
                self.error = ('The amp did not confirm the selected preset. '
                              'Reconnecting to check which preset is active.')
                self.controller = None
                self.reset.set()
                raise RuntimeError(self.error) from None

    async def run(self):
        from bleak import BleakClient, BleakScanner
        while True:
            self.reset.clear()
            self.phase = 'connecting'
            try:
                device = await BleakScanner.find_device_by_filter(
                    lambda d, advert: (d.name == NAME or advert.local_name == NAME)
                    and (self.device_id is None or d.address == self.device_id),
                    service_uuids=[SERVICE], timeout=10)
                if device is None:
                    raise RuntimeError('Micro Plus not found. Turn it on and close Fender Tone on other devices.')
                # Pin the first selected device across reconnects.
                self.device_id = device.address
                async with BleakClient(device, disconnected_callback=lambda _: self.reset.set()) as client:
                    self.client = client
                    controller = Controller(client, self.log)
                    await client.start_notify(RESPONSE, controller.notify)
                    await controller.initialize()
                    self.controller = controller
                    self.connections += 1
                    self.connected_since = time.time()
                    self.phase = 'connected'
                    self.error = None
                    while not self.reset.is_set():
                        try:
                            await asyncio.wait_for(self.reset.wait(), timeout=KEEPALIVE_INTERVAL)
                        except TimeoutError:
                            if controller.failure:
                                raise controller.failure
                            # Keep-alives must continue while a switch awaits its reply.
                            await controller.send(KEEPALIVE)
            except asyncio.CancelledError:
                raise
            except Exception as error:
                self.error = str(error) or 'The Bluetooth connection was lost.'
            finally:
                self.controller = None
                self.client = None
                self.connected_since = None
                self.phase = 'disconnected'
            await asyncio.sleep(2)
