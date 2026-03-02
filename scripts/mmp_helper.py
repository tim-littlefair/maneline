import asyncio
import sys

import bleak

async def main(device):
    async with bleak.BleakClient(device, pair=True) as client:
        print(client.name)
        print(device.details)
        print(device.details.keys())
        for i in device.details['props'].get('UUIDs'):
            try:
                desc = await client.read_gatt_descriptor(i)
                print(f"descriptor {i}: {desc}")
            except:
                print(sys.exc_info())
        print("Services found: ", len(client.services.descriptors))
        for d in client.services.descriptors:
            print("Service", d)

def detection_callback(device,adv_data):
    global service_uuids
    if adv_data.local_name == 'Mustang Micro Plus':
        print(device)
        print(device.details)
        print(adv_data)

async def main2():
    device = await bleak.BleakScanner.find_device_by_name(
        "Mustang Micro Plus",
        detection_callback=detection_callback
    )
    print("Device found by scanner:", device)
    await main(device)

async def main3():
    devices = await bleak.BleakScanner.discover(detection_callback=detection_callback)
    for d in devices:
        if d.name=="Mustang Micro Plus":
            print(d)
            client = bleak.BleakClient(d, pair=True,timeout=20)
            await client.connect()
            print(client.is_connected)
            print("Services: ", client.services.services)
            print("Characteristics: ", client.services.characteristics)
            await client.disconnect()

asyncio.run(main3())

