#!/usr/bin/env python3
"""Experimental Mustang Micro Plus BLE controller (CoreBluetooth on macOS)."""
import argparse
import asyncio
import json
import sys
import time
from pathlib import Path

SERVICE = '90559580-b707-11ee-acb1-7b7e30f1af54'
REQUEST = '820a7e34-4e0a-4f90-8520-04ebce35a3a1'
RESPONSE = '1017adcc-dcbc-4387-a59f-2546b2ea5bb0'
NAME = 'Mustang Micro Plus'


def varint(value):
    result = bytearray()
    while value >= 128:
        result.append((value & 127) | 128)
        value >>= 7
    result.append(value)
    return bytes(result)


def read_varint(data, offset):
    value = 0
    for shift in range(0, 70, 7):
        if offset >= len(data):
            raise ValueError('Truncated protobuf integer')
        byte = data[offset]
        offset += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, offset
    raise ValueError('Protobuf integer is too long')


def fields(data):
    """Decode only the protobuf wire types used by the captured messages."""
    result = []
    offset = 0
    while offset < len(data):
        tag, offset = read_varint(data, offset)
        number, wire = tag >> 3, tag & 7
        if number == 0:
            raise ValueError('Invalid protobuf field zero')
        if wire == 0:
            value, offset = read_varint(data, offset)
        elif wire in (1, 2, 5):
            if wire == 2:
                length, offset = read_varint(data, offset)
            else:
                length = 8 if wire == 1 else 4
            if offset + length > len(data):
                raise ValueError('Truncated protobuf field')
            value = data[offset:offset + length]
            offset += length
        else:
            raise ValueError(f'Unsupported protobuf wire type {wire}')
        result.append((number, value))
    return result


def packet(payload):
    if len(payload) > 255:
        raise ValueError('Command is too large for one BLE frame')
    return bytes((0x35, 0, len(payload))) + payload


def select_preset(slot):
    if not 1 <= slot <= 100:
        raise ValueError('Preset number must be between 1 and 100')
    # Captured switch 3500060a0442020847 selects displayed preset 72.
    index = b'\x08' + varint(slot - 1)
    command = b'\x42' + varint(len(index)) + index
    return packet(b'\x0a' + varint(len(command)) + command)


class Frames:
    def __init__(self):
        self.buffer = bytearray()

    def feed(self, data):
        if len(data) < 3 or data[0] not in (0x33, 0x34, 0x35) or data[1] != 0:
            self.buffer.clear()
            raise ValueError('Invalid Micro Plus BLE frame header')
        length = data[2]
        if length != len(data) - 3:
            self.buffer.clear()
            raise ValueError('BLE frame length does not match payload')
        if data[0] == 0x33:
            self.buffer.clear()
        elif data[0] == 0x34 and not self.buffer:
            raise ValueError('Continuation without a starting frame')
        self.buffer.extend(data[3:])
        if len(self.buffer) > 65536:
            self.buffer.clear()
            raise ValueError('Micro Plus response exceeds 64 KiB')
        if data[0] != 0x35:
            return None
        message = bytes(self.buffer)
        self.buffer.clear()
        return message


class Controller:
    def __init__(self, client, log):
        self.client = client
        self.log = log
        self.frames = Frames()
        self.slot = None
        self.names = []
        self.changed = asyncio.Event()
        self.failure = None

    def record(self, direction, data):
        if self.log:
            self.log.write(json.dumps({'time': time.time(), 'direction': direction,
                                       'hex': bytes(data).hex()}) + '\n')
            self.log.flush()

    def notify(self, _characteristic, data):
        self.record('rx', data)
        try:
            message = self.frames.feed(data)
            if message is None or not message.startswith(b'\x12'):
                return
            outer = fields(message)
            for number, body in outer:
                if number != 2 or not isinstance(body, bytes):
                    continue
                for kind, value in fields(body):
                    # Captures: 12..08012a..08<zero-based-slot>2001.
                    if kind == 5 and isinstance(value, bytes):
                        content = dict(fields(value))
                        if content.get(4) == 1:
                            self.slot = content.get(1, 0) + 1
                            self.changed.set()
                    elif kind == 12 and isinstance(value, bytes):
                        self.names = [name.decode('utf-8').rstrip() for key, name in fields(value)
                                      if key == 1 and isinstance(name, bytes)]
                        self.changed.set()
        except (ValueError, UnicodeError) as error:
            self.failure = error
            self.changed.set()

    async def send(self, data):
        self.record('tx', data)
        characteristic = self.client.services.get_characteristic(REQUEST)
        if characteristic is None:
            raise RuntimeError('Micro Plus control characteristic was not found')
        response = 'write-without-response' not in characteristic.properties
        await self.client.write_gatt_char(characteristic, data, response=response)

    async def wait_for(self, predicate, timeout=8):
        async with asyncio.timeout(timeout):
            while not predicate():
                self.changed.clear()
                if self.failure:
                    raise self.failure
                if not self.client.is_connected:
                    raise RuntimeError('Micro Plus disconnected')
                await self.changed.wait()

    def status(self):
        return {'preset': self.slot, 'name': self.names[self.slot - 1]
                if self.slot and self.slot <= len(self.names) else None}

    async def switch(self, slot):
        self.slot = None
        await self.send(select_preset(slot))
        await self.wait_for(lambda: self.slot == slot)
        return self.status()


async def run(args):
    from bleak import BleakClient, BleakScanner
    print('Looking for Mustang Micro Plus…', file=sys.stderr, flush=True)
    if args.device:
        device = await BleakScanner.find_device_by_filter(
            lambda device, advert: device.address == args.device
            and (advert.local_name == NAME or device.name == NAME),
            timeout=args.timeout, service_uuids=[SERVICE])
        candidates = [device] if device else []
    else:
        devices = await BleakScanner.discover(timeout=args.timeout, return_adv=True,
                                              service_uuids=[SERVICE])
        candidates = [device for device, advert in devices.values()
                      if advert.local_name == NAME or device.name == NAME]
    if not candidates:
        raise RuntimeError('Micro Plus was not found. Turn it on, close Fender Tone on '
                           'other devices, and try again with the Micro Plus nearby.')
    if args.command == 'scan':
        print(json.dumps([{'name': d.name, 'id': d.address} for d in candidates]))
        return
    if len(candidates) != 1:
        raise RuntimeError('More than one Micro Plus found; select one with --device ID')
    log = None
    if args.log:
        Path(args.log).parent.mkdir(parents=True, exist_ok=True)
        log = open(args.log, 'a', encoding='utf-8')
    try:
        async with BleakClient(candidates[0], timeout=args.timeout) as client:
            print('Connected to Mustang Micro Plus', file=sys.stderr, flush=True)
            controller = Controller(client, log)
            await client.start_notify(RESPONSE, controller.notify)
            # Handshake from mmp_helper.py.disabled and mmp_startup.gatttool.json.
            for hex_data in ('3500021a00', '3500040a023a00', '3500040a027200',
                             '3500060a0422020801', '3500050a03b20100',
                             '3500050a03ca0100', '3500041a020801'):
                await controller.send(bytes.fromhex(hex_data))
                await asyncio.sleep(0.15)
            await controller.wait_for(lambda: controller.slot is not None)
            if args.command == 'presets':
                await controller.wait_for(lambda: bool(controller.names))
                print(json.dumps([{'preset': i + 1, 'name': name}
                                  for i, name in enumerate(controller.names)]))
            elif args.command == 'preset':
                print(json.dumps(await controller.switch(args.slot)))
            else:
                print(json.dumps(controller.status()), flush=True)
    finally:
        if log:
            log.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', help='CoreBluetooth device UUID from scan')
    parser.add_argument('--timeout', type=float, default=15)
    parser.add_argument('--log', help='Append timestamped BLE traffic to a JSONL file')
    commands = parser.add_subparsers(dest='command', required=True)
    for command in ('scan', 'status', 'presets'):
        commands.add_parser(command)
    switch = commands.add_parser('preset')
    switch.add_argument('slot', type=int, choices=range(1, 101), metavar='1–100')
    args = parser.parse_args()
    from bleak.exc import BleakError
    try:
        asyncio.run(run(args))
    except (RuntimeError, ValueError, TimeoutError, BleakError) as error:
        message = str(error).strip()
        if not message:
            message = ('Timed out waiting for a Bluetooth response. Reconnect with '
                       'status to check which preset is selected.')
        print(f'Could not complete the request: {message}',
              file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130
    return 0


if __name__ == '__main__':
    sys.exit(main())
