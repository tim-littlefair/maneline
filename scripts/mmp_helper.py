import asyncio
import sys
import time
import traceback

import bleak

# grep -n -e mtu -e 'tion_client":' -e 'btatt.opcode"' -e 'btatt.handle"' -e 'btatt.value": "3'  _work/25088_151405-153116_AWST.json | more


def detection_callback(device,adv_data):
    global service_uuids
    if adv_data.local_name == 'Mustang Micro Plus':
        print(device)
        print(device.details)
        print(adv_data)


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

def response_callback(_charid, data):
    print(bytes.hex(data))


def mmp_filter(_device, adv):
    # This assumes that the device includes the UART service UUID in the
    # advertising data. This test may need to be adjusted depending on the
    # actual advertising data supplied by the device.
    if "90559580-b707-11ee-acb1-7b7e30f1af54" in adv.service_uuids:
        return True
    return False

def handle_disconnect(_):
    print("Device was disconnected, goodbye.")
    # cancelling all tasks effectively ends the program
    for task in asyncio.all_tasks():
        task.cancel()

async def main4():
    device = await bleak.BleakScanner.find_device_by_name("Mustang Micro Plus")
    if device is None:
        print("Failed to find MMP")
        sys.exit(1)
    else:
        print("Device found:", device)
        pass
    client = bleak.BleakClient(
            device,
            # disconnected_callback=handle_disconnect,
            # pair=True,
    )
    print(client.is_connected)
    if client.is_connected is False:
        await client.connect()
    print(client.is_connected)
    print("Services: ", client.services.services)
    print("Characteristics: ", client.services.characteristics)
    print("Descriptors: ", client.services.descriptors)
    mmp_request_service_handle = 0x001b
    mmp_request_ccc_handle = 0x01c
    mmp_response_service_handle = 0x0017
    mmp_response_ccc_handle = 0x0018
    mmp_response_read_handle = 0x00a
    #mmp_response_ccc = await client.read_gatt_descriptor(mmp_response_ccc_handle)
    #print("MMP response CCC: ", mmp_response_ccc)
    time.sleep(2.0)
    # """
    await client.disconnect()
    print("finally")

try:
    asyncio.run(main4())
except Exception:
    traceback.print_exception(*sys.exc_info())

