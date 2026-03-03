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

def resolve_services(device, client, advert):
    for i in range(1,len(advert.service_uuids)):
        new_service = bleak.backends.service.BleakGATTService(None, i, advert.service_uuids[i-1])
        client.services.services[i] = new_service
        if new_service.uuid == "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54":
            mmp_request_characteristic = bleak.backends.characteristic.BleakGATTCharacteristic(
                None, 0x1b, "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1",
                properties=[],
                max_write_without_response_size=None,
                service=new_service
            )
            new_service.add_characteristic((mmp_request_characteristic))
            client.service.characteristics[0x1b] = mmp_request_characteristic
            client.characteristics[0x1b] = mmp_request_characteristic


async def main3():
    devices_and_adverts = await bleak.BleakScanner.discover(
        return_adv=True
    )
    client = None
    for d_and_a in devices_and_adverts.values():
        d = d_and_a[0]
        a = d_and_a[1]
        if d.name=="Mustang Micro Plus":
            print(d)
            print(a)
            client = bleak.BleakClient(d, pair=True,timeout=20)
            break

    if client:
        await client.connect()
        print(client.is_connected)
        # resolve_services(d, client, a)
        print("Services: ", client.services.services)
        print("Characteristics: ", client.services.characteristics)
        await client.disconnect()

asyncio.run(main3())

