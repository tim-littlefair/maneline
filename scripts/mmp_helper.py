import asyncio
import bleak

async def main(device):
    async with bleak.BleakClient(device) as client:
        print(client.name)
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

asyncio.run(main2())


"""
 ['00001101-0000-1000-8000-00805f9b34fb', '0000110b-0000-1000-8000-00805f9b34fb', '0000110d-0000-1000-8000-00805f9b34fb', '00001200-0000-1000-8000-00805f9b34fb', '00001800-0000-1000-8000-00805f9b34fb', '90559580-b707-11ee-acb1-7b7e30f1af54']
 ['00001101-0000-1000-8000-00805f9b34fb', '0000110b-0000-1000-8000-00805f9b34fb', '0000110d-0000-1000-8000-00805f9b34fb', '00001200-0000-1000-8000-00805f9b34fb', '00001800-0000-1000-8000-00805f9b34fb', '90559580-b707-11ee-acb1-7b7e30f1af54']

"""