import asyncio
import sys
import time
import traceback

import bleak

UUID128_MMP_CONTROL_SERVICE = "90559580-b707-11ee-acb1-7b7e30f1af54"
UUID128_MMP_REQUEST_CHARACTERISTIC = "820a7e34-4e0a-4f90-8520-04ebce35a3a1"
UUID128_MMP_RESPONSE_CHARACTERISTIC = "1017adcc-dcbc-4387-a59f-2546b2ea5bb0",
UUID128_MMP_RESPONSE_CHARACTERISTIC2 = "1017adccdcbc4387a59f2546b2ea5bb0",
UUID16_TYPE_2800 = "2800"
UUID16_TYPE_2802 = "2802"
UUID16_TYPE_2902 = "2902"

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

def dump(client):
    for svc_handle in sorted(client.services.services.keys()):
        s = client.services.services[svc_handle]
        print(f"service: {s}")
        for c in s.characteristics:
            print(f"   char: {c} {c.properties}")
            for d in c.descriptors:
                print(f"    dsc: {d}")
    print(client)

async def do_start_notify(client):
    """
    17751:          "bthci_acl.src.name": "JA",
    17763:          "btatt.opcode": "0x12",
    17769:          "btatt.handle": "0x0018",
    17771:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
    17772:            "btatt.characteristic_uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0",
    17773:            "btatt.uuid16": "0x2902"
    17775:          "btatt.characteristic_configuration_client": "0x0001",
    17904:          "bthci_acl.src.name": "Mustang Micro Plus",
    17916:          "btatt.opcode": "0x13",
    17922:          "btatt.handle": "0x0018",
    17924:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
    17925:            "btatt.characteristic_uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0",
    17926:            "btatt.uuid16": "0x2902"

    17969:          "bthci_acl.src.name": "Mustang Micro Plus",
    17981:          "btatt.opcode": "0x1b",
    17987:          "btatt.handle": "0x0017",
    17989:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
    17990:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
    17992:          "btatt.value": "35:00:41:04:0b:4e:00:0f:9c:00:05:0b:27:00:0f:9b:00:04:09:a1:01:0f:32:01:05:0b:4b:00:0f:98:00:04:09:4b:00:51:31:7d:7d:5d:7d:92:06:79:69:66:69:61:62:6c:65:74:03:00:05:08:04:f7:06:80:49:64:22:3a:22:2
    2:7d:7d"
    """
    start_notify_outcome = await client.start_notify(
        client.services.characteristics.get(0x17),q
        # UUID128_MMP_RESPONSE_CHARACTERISTIC2,
        response_callback,
        bluez=bleak.args.bluez.BlueZNotifyArgs(use_start_notify=True)
    )
    print("start_notify outcome: ", start_notify_outcome)

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
            pair=True,
    )
    if client.is_connected is False:
        print("Waiting to connect")
        await client.connect()
    print(client.is_connected)

    # dump(client)

    await asyncio.sleep(0.1)
    await do_start_notify(client)
    await asyncio.sleep(10.0)


    """
    Service/Characteristic discovery (difficult to reproduce exactly)
    13444:          "bthci_acl.src.name": "JA",
    13456:          "btatt.opcode": "0x10",
    13464:          "btatt.uuid16": "0x2800"
    13992:          "bthci_acl.src.name": "Mustang Micro Plus",
    14004:          "btatt.opcode": "0x11",
    14012:            "btatt.handle": "0x0001",
    14014:            "btatt.uuid16": "0x1801"
    14017:            "btatt.handle": "0x0007",
    14019:              "btatt.service_uuid16": "0x1801"
    14022:            "btatt.uuid16": "0x1800"
    14024:          "btatt.uuid16": "0x2800",
    """

    """
    14066:          "bthci_acl.src.name": "JA",
    14078:          "btatt.opcode": "0x10",
    14086:          "btatt.uuid16": "0x2800"
    14250:          "bthci_acl.src.name": "Mustang Micro Plus",
    14262:          "btatt.opcode": "0x11",
    14270:            "btatt.handle": "0x000c",
    14272:              "btatt.service_uuid16": "0x1800"
    14275:            "btatt.uuid128": "a5:a5:00:5b:02:00:23:9b:e1:11:02:d1:00:11:00:00"
    14278:            "btatt.handle": "0x0015",
    14280:              "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5"
    14283:            "btatt.uuid128": "54:af:f1:30:7e:7b:b1:ac:ee:11:07:b7:80:95:55:90"
    14285:          "btatt.uuid16": "0x2800",
    """
    response1 = client.services.get_characteristic(UUID16_TYPE_2800)
    print(response1)

    try:
        await client.read_gatt_char(UUID16_TYPE_2802)
    except bleak.BleakCharacteristicNotFoundError:
        print("expected error reported")
        pass

    #mmp_response_ccc = await client.start_notify(
    #    client.services.characteristics[22], response_callback
    #)
    # print("MMP response CCC: ", mmp_response_ccc)
    # """

    print("Waiting for responses")
    await asyncio.sleep(10.0)
    await client.disconnect()
    print("finally")

try:
    asyncio.run(main4())
except Exception:
    traceback.print_exception(*sys.exc_info())




"""

Extract from Wireshark BT capture from running FenderTone mobile for Android:

python.venv) tim@tim-OptiPlex-7450-AIO:~/github/maneline$ grep -n -e src.name -e "btatt.[a-z_]*uuid" -e mtu -e 'tion_client":' -e 'btatt.opcode"' -e 'btatt.handle"' -e 'btatt.value": "3'  _work/25088_151405-153116_AWST.json | more
13279:          "bthci_acl.src.name": "Mustang Micro Plus",
13291:          "btatt.opcode": "0x02",
13297:          "btatt.client_rx_mtu": "128"
13338:          "bthci_acl.src.name": "JA",
13350:          "btatt.opcode": "0x03",
13356:          "btatt.server_rx_mtu": "128",
13444:          "bthci_acl.src.name": "JA",
13456:          "btatt.opcode": "0x10",
13464:          "btatt.uuid16": "0x2800"
13992:          "bthci_acl.src.name": "Mustang Micro Plus",
14004:          "btatt.opcode": "0x11",
14012:            "btatt.handle": "0x0001",
14014:            "btatt.uuid16": "0x1801"
14017:            "btatt.handle": "0x0007",
14019:              "btatt.service_uuid16": "0x1801"
14022:            "btatt.uuid16": "0x1800"
14024:          "btatt.uuid16": "0x2800",

14066:          "bthci_acl.src.name": "JA",
14078:          "btatt.opcode": "0x10",
14086:          "btatt.uuid16": "0x2800"
14250:          "bthci_acl.src.name": "Mustang Micro Plus",
14262:          "btatt.opcode": "0x11",
14270:            "btatt.handle": "0x000c",
14272:              "btatt.service_uuid16": "0x1800"
14275:            "btatt.uuid128": "a5:a5:00:5b:02:00:23:9b:e1:11:02:d1:00:11:00:00"
14278:            "btatt.handle": "0x0015",
14280:              "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5"
14283:            "btatt.uuid128": "54:af:f1:30:7e:7b:b1:ac:ee:11:07:b7:80:95:55:90"
14285:          "btatt.uuid16": "0x2800",

14327:          "bthci_acl.src.name": "JA",
14339:          "btatt.opcode": "0x08",
14347:          "btatt.uuid16": "0x2802"
14426:          "bthci_acl.src.name": "Mustang Micro Plus",
14438:          "btatt.opcode": "0x01",
14450:          "btatt.handle": "0x0001",
14452:            "btatt.uuid16": "0x1801"
14455:          "btatt.uuid16": "0x2802"

14496:          "bthci_acl.src.name": "JA",
14508:          "btatt.opcode": "0x08",
14516:          "btatt.uuid16": "0x2803"
14595:          "bthci_acl.src.name": "Mustang Micro Plus",
14607:          "btatt.opcode": "0x09",
14615:            "btatt.handle": "0x0002",
14617:              "btatt.service_uuid16": "0x1801",
14618:              "btatt.uuid16": "0x2803"
14631:            "btatt.handle": "0x0003",
14633:              "btatt.service_uuid16": "0x1801"
14635:            "btatt.uuid16": "0x2a05"
14638:            "btatt.handle": "0x0005",
14640:              "btatt.service_uuid16": "0x1801",
14641:              "btatt.characteristic_uuid16": "0x2a05",
14642:              "btatt.uuid16": "0x2803"
14655:            "btatt.handle": "0x0006",
14657:              "btatt.service_uuid16": "0x1801",
14658:              "btatt.characteristic_uuid16": "0x2a05"
14660:            "btatt.uuid16": "0x2b29"
14662:          "btatt.uuid16": "0x2803",

14704:          "bthci_acl.src.name": "JA",
14716:          "btatt.opcode": "0x08",
14724:          "btatt.uuid16": "0x2803"
14910:          "bthci_acl.src.name": "Mustang Micro Plus",
14922:          "btatt.opcode": "0x01",
14934:          "btatt.handle": "0x0006",
14936:            "btatt.service_uuid16": "0x1801",
14937:            "btatt.uuid16": "0x2b29"
14940:          "btatt.uuid16": "0x2803"

14981:          "bthci_acl.src.name": "JA",
14993:          "btatt.opcode": "0x04",
15079:          "bthci_acl.src.name": "Mustang Micro Plus",
15091:          "btatt.opcode": "0x05",
15097:          "btatt.uuid_format": "0x01",
15099:            "btatt.handle": "0x0004",
15101:              "btatt.service_uuid16": "0x1801",
15102:              "btatt.characteristic_uuid16": "0x2a05"
15104:            "btatt.uuid16": "0x2902"

15147:          "bthci_acl.src.name": "JA",
15159:          "btatt.opcode": "0x08",
15167:          "btatt.uuid16": "0x2802"
15246:          "bthci_acl.src.name": "Mustang Micro Plus",
15258:          "btatt.opcode": "0x01",
15270:          "btatt.handle": "0x0007",
15272:            "btatt.uuid16": "0x1800"
15275:          "btatt.uuid16": "0x2802"

15316:          "bthci_acl.src.name": "JA",
15328:          "btatt.opcode": "0x08",
15336:          "btatt.uuid16": "0x2803"
15415:          "bthci_acl.src.name": "Mustang Micro Plus",
15427:          "btatt.opcode": "0x09",
15435:            "btatt.handle": "0x0008",
15437:              "btatt.service_uuid16": "0x1800",
15438:              "btatt.uuid16": "0x2803"
15451:            "btatt.handle": "0x0009",
15453:              "btatt.service_uuid16": "0x1800"
15455:            "btatt.uuid16": "0x2a00"
15458:            "btatt.handle": "0x000a",
15460:              "btatt.service_uuid16": "0x1800",
15461:              "btatt.characteristic_uuid16": "0x2a00",
15462:              "btatt.uuid16": "0x2803"
15475:            "btatt.handle": "0x000b",
15477:              "btatt.service_uuid16": "0x1800",
15478:              "btatt.characteristic_uuid16": "0x2a00"
15480:            "btatt.uuid16": "0x2a01"
15482:          "btatt.uuid16": "0x2803",
15524:          "bthci_acl.src.name": "JA",
15536:          "btatt.opcode": "0x08",
15544:          "btatt.uuid16": "0x2803"
15623:          "bthci_acl.src.name": "Mustang Micro Plus",
15635:          "btatt.opcode": "0x01",
15647:          "btatt.handle": "0x000b",
15649:            "btatt.service_uuid16": "0x1800",
15650:            "btatt.uuid16": "0x2a01"
15653:          "btatt.uuid16": "0x2803"

15694:          "bthci_acl.src.name": "JA",
15706:          "btatt.opcode": "0x08",
15714:          "btatt.uuid16": "0x2802"
15793:          "bthci_acl.src.name": "Mustang Micro Plus",
15805:          "btatt.opcode": "0x01",
15817:          "btatt.handle": "0x000c",
15819:            "btatt.uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5"
15822:          "btatt.uuid16": "0x2802"

15863:          "bthci_acl.src.name": "JA",
15875:          "btatt.opcode": "0x08",
15883:          "btatt.uuid16": "0x2803"
15962:          "bthci_acl.src.name": "Mustang Micro Plus",
15974:          "btatt.opcode": "0x09",
15982:            "btatt.handle": "0x000d",
15984:              "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5",
15985:              "btatt.uuid16": "0x2803"
15998:            "btatt.handle": "0x000e",
16000:              "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5"
16002:            "btatt.uuid128": "a5:a5:00:5b:02:00:23:9b:e1:11:02:d1:01:11:00:00"
16005:            "btatt.handle": "0x000f",
16007:              "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5",
16008:              "btatt.characteristic_uuid128": "00:00:11:01:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5",
16009:              "btatt.uuid16": "0x2803"
16022:            "btatt.handle": "0x0010",
16024:              "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5",
16025:              "btatt.characteristic_uuid128": "00:00:11:01:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5"
16027:            "btatt.uuid128": "a5:a5:00:5b:02:00:23:9b:e1:11:02:d1:02:11:00:00"
16030:            "btatt.handle": "0x0012",
16032:              "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5",
16033:              "btatt.characteristic_uuid128": "00:00:11:02:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5",
16034:              "btatt.uuid16": "0x2803"
16047:            "btatt.handle": "0x0013",
16049:              "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5",
16050:              "btatt.characteristic_uuid128": "00:00:11:02:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5"
16052:            "btatt.uuid128": "a5:a5:00:5b:02:00:23:9b:e1:11:02:d1:03:11:00:00"
16054:          "btatt.uuid16": "0x2803",

16096:          "bthci_acl.src.name": "JA",
16108:          "btatt.opcode": "0x08",
16116:          "btatt.uuid16": "0x2803"
16195:          "bthci_acl.src.name": "Mustang Micro Plus",
16207:          "btatt.opcode": "0x01",
16219:          "btatt.handle": "0x0013",
16221:            "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5",
16222:            "btatt.uuid128": "00:00:11:03:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5"
16225:          "btatt.uuid16": "0x2803"

16266:          "bthci_acl.src.name": "JA",
16278:          "btatt.opcode": "0x04",
16364:          "bthci_acl.src.name": "Mustang Micro Plus",
16376:          "btatt.opcode": "0x05",
16382:          "btatt.uuid_format": "0x01",
16384:            "btatt.handle": "0x0011",
16386:              "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5",
16387:              "btatt.characteristic_uuid128": "00:00:11:02:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5"
16389:            "btatt.uuid16": "0x2902"
16432:          "bthci_acl.src.name": "JA",
16444:          "btatt.opcode": "0x04",
16530:          "bthci_acl.src.name": "Mustang Micro Plus",
16542:          "btatt.opcode": "0x05",
16548:          "btatt.uuid_format": "0x01",
16550:            "btatt.handle": "0x0014",
16552:              "btatt.service_uuid128": "00:00:11:00:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5",
16553:              "btatt.characteristic_uuid128": "00:00:11:03:d1:02:11:e1:9b:23:00:02:5b:00:a5:a5"
16555:            "btatt.uuid16": "0x2902"

16598:          "bthci_acl.src.name": "JA",
16610:          "btatt.opcode": "0x08",
16618:          "btatt.uuid16": "0x2802"
16697:          "bthci_acl.src.name": "Mustang Micro Plus",
16709:          "btatt.opcode": "0x01",
16721:          "btatt.handle": "0x0015",
16723:            "btatt.uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54"
16726:          "btatt.uuid16": "0x2802"

16767:          "bthci_acl.src.name": "JA",
16779:          "btatt.opcode": "0x08",
16787:          "btatt.uuid16": "0x2803"
16866:          "bthci_acl.src.name": "Mustang Micro Plus",
16878:          "btatt.opcode": "0x09",
16886:            "btatt.handle": "0x0016",
16888:              "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
16889:              "btatt.uuid16": "0x2803"
16902:            "btatt.handle": "0x0017",
16904:              "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54"
16906:            "btatt.uuid128": "b0:5b:ea:b2:46:25:9f:a5:87:43:bc:dc:cc:ad:17:10"
16909:            "btatt.handle": "0x001a",
16911:              "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
16912:              "btatt.characteristic_uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0",
16913:              "btatt.uuid16": "0x2803"
16926:            "btatt.handle": "0x001b",
16928:              "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
16929:              "btatt.characteristic_uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
16931:            "btatt.uuid128": "a1:a3:35:ce:eb:04:20:85:90:4f:0a:4e:34:7e:0a:82"
16933:          "btatt.uuid16": "0x2803",

16975:          "bthci_acl.src.name": "JA",
16987:          "btatt.opcode": "0x08",
16995:          "btatt.uuid16": "0x2803"
17074:          "bthci_acl.src.name": "Mustang Micro Plus",
17086:          "btatt.opcode": "0x01",
17098:          "btatt.handle": "0x001b",
17100:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
17101:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
17104:          "btatt.uuid16": "0x2803"

17145:          "bthci_acl.src.name": "JA",
17157:          "btatt.opcode": "0x04",
17243:          "bthci_acl.src.name": "Mustang Micro Plus",
17255:          "btatt.opcode": "0x05",
17261:          "btatt.uuid_format": "0x01",
17263:            "btatt.handle": "0x0018",
17265:              "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
17266:              "btatt.characteristic_uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
17268:            "btatt.uuid16": "0x2902"
17271:            "btatt.handle": "0x0019",
17273:              "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
17274:              "btatt.characteristic_uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
17276:            "btatt.uuid16": "0x2901"

17319:          "bthci_acl.src.name": "JA",
17331:          "btatt.opcode": "0x04",
17417:          "bthci_acl.src.name": "Mustang Micro Plus",
17429:          "btatt.opcode": "0x05",
17435:          "btatt.uuid_format": "0x01",
17437:            "btatt.handle": "0x001c",
17439:              "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
17440:              "btatt.characteristic_uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
17442:            "btatt.uuid16": "0x2902"
17445:            "btatt.handle": "0x001d",
17447:              "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
17448:              "btatt.characteristic_uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
17450:            "btatt.uuid16": "0x2901"

17493:          "bthci_acl.src.name": "JA",
17505:          "btatt.opcode": "0x04",
17591:          "bthci_acl.src.name": "Mustang Micro Plus",
17603:          "btatt.opcode": "0x01",
17615:          "btatt.handle": "0x001e",
17617:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
17618:            "btatt.characteristic_uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"

17751:          "bthci_acl.src.name": "JA",
17763:          "btatt.opcode": "0x12",
17769:          "btatt.handle": "0x0018",
17771:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
17772:            "btatt.characteristic_uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0",
17773:            "btatt.uuid16": "0x2902"
17775:          "btatt.characteristic_configuration_client": "0x0001",
17904:          "bthci_acl.src.name": "Mustang Micro Plus",
17916:          "btatt.opcode": "0x13",
17922:          "btatt.handle": "0x0018",
17924:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
17925:            "btatt.characteristic_uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0",
17926:            "btatt.uuid16": "0x2902"

17969:          "bthci_acl.src.name": "Mustang Micro Plus",
17981:          "btatt.opcode": "0x1b",
17987:          "btatt.handle": "0x0017",
17989:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
17990:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
17992:          "btatt.value": "35:00:41:04:0b:4e:00:0f:9c:00:05:0b:27:00:0f:9b:00:04:09:a1:01:0f:32:01:05:0b:4b:00:0f:98:00:04:09:4b:00:51:31:7d:7d:5d:7d:92:06:79:69:66:69:61:62:6c:65:74:03:00:05:08:04:f7:06:80:49:64:22:3a:22:2
2:7d:7d"

18033:          "bthci_acl.src.name": "JA",
18045:          "btatt.opcode": "0x52",
18051:          "btatt.handle": "0x001b",
18053:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
18054:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
18056:          "btatt.value": "35:00:02:1a:00"
18097:          "bthci_acl.src.name": "JA",
18109:          "btatt.opcode": "0x52",
18115:          "btatt.handle": "0x001b",
18117:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
18118:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
18120:          "btatt.value": "35:00:04:0a:02:3a:00"

18237:          "bthci_acl.src.name": "JA",
18249:          "btatt.opcode": "0x52",
18255:          "btatt.handle": "0x001b",
18257:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
18258:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
18260:          "btatt.value": "35:00:04:0a:02:72:00"

18301:          "bthci_acl.src.name": "JA",
18313:          "btatt.opcode": "0x52",
18319:          "btatt.handle": "0x001b",
18321:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
18322:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
18324:          "btatt.value": "35:00:06:0a:04:22:02:08:01"

18441:          "bthci_acl.src.name": "JA",
18453:          "btatt.opcode": "0x52",
18459:          "btatt.handle": "0x001b",
18461:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
18462:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
18464:          "btatt.value": "35:00:05:0a:03:b2:01:00"

18543:          "bthci_acl.src.name": "Mustang Micro Plus",
18555:          "btatt.opcode": "0x1b",
18561:          "btatt.handle": "0x0017",
18563:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
18564:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
18566:          "btatt.value": "35:00:0e:12:0c:08:02:32:08:0a:06:31:2e:30:2e:32:39"

18607:          "bthci_acl.src.name": "JA",
18619:          "btatt.opcode": "0x52",
18625:          "btatt.handle": "0x001b",
18627:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
18628:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
18630:          "btatt.value": "35:00:05:0a:03:ca:01:00"
18671:          "bthci_acl.src.name": "JA",
18683:          "btatt.opcode": "0x52",
18689:          "btatt.handle": "0x001b",
18691:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
18692:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
18694:          "btatt.value": "35:00:04:1a:02:08:01"
18773:          "bthci_acl.src.name": "Mustang Micro Plus",
18858:          "bthci_acl.src.name": "Mustang Micro Plus",
18870:          "btatt.opcode": "0x1b",
18876:          "btatt.handle": "0x0017",
18878:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
18879:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
18881:          "btatt.value": "33:00:75:12:f8:08:08:02:62:f3:08:0a:0d:44:72:69:76:65:6e:20:44:65:6c:75:78:65:0a:0d:53:74:61:64:69:75:6d:20:4c:65:61:64:20:0a:0f:53:77:69:72:6c:69:6e:67:20:45:63:68:6f:65:73:0a:0a:4d:65:74:61:6c:2
0:32:30:30:30:0a:09:42:42:31:35:20:53:6f:6c:6f:0a:08:48:65:79:20:4a:69:6d:69:0a:09:54:77:69:6e:20:54:72:65:6d:0a:0b:56:69:62:72:61:20:44:72:69:76:65:0a:0a:55:62:65"
18922:          "bthci_acl.src.name": "Mustang Micro Plus",
18969:          "bthci_acl.src.name": "Mustang Micro Plus",
18981:          "btatt.opcode": "0x1b",
18987:          "btatt.handle": "0x0017",
18989:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
18990:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
18992:          "btatt.value": "34:00:75:72:20:4d:65:74:61:6c:0a:07:4f:63:74:6f:62:6f:74:0a:10:48:61:72:6d:6f:6e:69:63:20:56:69:62:72:61:74:6f:0a:06:46:42:45:31:30:30:0a:0d:4c:61:76:65:6e:64:65:72:20:52:61:69:6e:0a:10:41:63:6f:7
5:73:74:69:63:20:47:74:72:20:53:69:6d:0a:0c:53:74:65:72:65:6f:20:44:65:6c:61:79:0a:0b:54:77:65:65:64:20:53:75:67:61:72:0a:0d:57:69:6c:64:20:4e:6f:63:74:75:72:6e:65"
19033:          "bthci_acl.src.name": "Mustang Micro Plus",
19080:          "bthci_acl.src.name": "Mustang Micro Plus",
19092:          "btatt.opcode": "0x1b",
19098:          "btatt.handle": "0x0017",
19100:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
19101:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
19103:          "btatt.value": "34:00:75:0a:08:37:30:53:20:52:6f:63:6b:0a:08:38:30:53:20:52:6f:63:6b:0a:08:39:30:53:20:52:6f:63:6b:0a:09:45:63:68:6f:20:44:6f:6d:65:0a:06:42:72:6f:6b:65:6e:0a:0b:42:72:69:74:20:43:6f:6c:6f:75:72:0
a:0a:53:75:70:65:72:20:4c:69:76:65:0a:0d:46:69:72:65:64:20:55:70:20:4c:65:61:64:0a:09:59:65:61:72:20:32:32:39:30:0a:0c:42:6f:6f:73:74:65:64:20:42:72:69:74:0a:0c:50"
19144:          "bthci_acl.src.name": "Mustang Micro Plus",
19191:          "bthci_acl.src.name": "Mustang Micro Plus",
19203:          "btatt.opcode": "0x1b",
19209:          "btatt.handle": "0x0017",
19211:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
19212:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
19214:          "btatt.value": "34:00:75:69:67:73:20:43:61:6e:20:46:6c:79:0a:0b:54:77:65:65:64:20:42:6c:75:65:73:0a:08:42:72:69:74:20:50:6f:70:0a:0d:43:6f:75:6e:74:72:79:20:54:77:61:6e:67:0a:06:53:75:72:66:79:20:0a:09:50:61:77:6
e:20:4b:69:6e:67:0a:08:42:42:31:35:20:4c:6f:77:0a:08:42:42:31:35:20:4d:69:64:0a:07:42:42:31:35:20:48:69:0a:08:42:75:63:6b:61:72:6f:6f:0a:0a:42:6c:75:65:73:20:31:39"
19255:          "bthci_acl.src.name": "Mustang Micro Plus",
19302:          "bthci_acl.src.name": "Mustang Micro Plus",
19314:          "btatt.opcode": "0x1b",
19320:          "btatt.handle": "0x0017",
19322:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
19323:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
19325:          "btatt.value": "34:00:75:35:31:0a:0b:44:65:72:65:6b:20:43:68:61:6d:70:0a:0a:50:6f:70:20:43:68:6f:72:75:73:0a:0a:4a:61:7a:7a:20:43:6c:65:61:6e:0a:0a:53:75:70:65:72:20:42:75:72:6e:0a:0d:44:69:72:74:79:20:46:6c:61:6
e:67:65:72:0a:0e:43:72:61:6e:6b:65:64:20:50:72:69:6e:63:65:0a:08:53:75:73:74:61:69:6e:64:0a:0c:46:75:7a:7a:79:20:44:65:6c:75:78:65:0a:08:53:70:6f:6f:6e:66:75:6c:0a"
19366:          "bthci_acl.src.name": "Mustang Micro Plus",
19413:          "bthci_acl.src.name": "Mustang Micro Plus",
19425:          "btatt.opcode": "0x1b",
19431:          "btatt.handle": "0x0017",
19433:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
19434:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
19436:          "btatt.value": "34:00:75:0e:36:35:20:54:77:69:6e:20:52:65:76:65:72:62:0a:0d:36:35:20:44:6c:78:20:52:65:76:65:72:62:0a:0f:36:35:20:50:72:69:6e:63:20:52:65:76:65:72:62:0a:08:35:37:20:43:68:61:6d:70:0a:0a:35:37:20:4
4:6c:78:20:46:61:74:0a:0c:42:61:73:73:6d:61:6e:20:42:65:65:66:0a:0b:53:69:6c:76:65:72:20:54:6f:6e:65:0a:0e:42:72:69:74:20:53:74:6b:20:43:6c:65:61:6e:0a:07:44:49:20"
19477:          "bthci_acl.src.name": "Mustang Micro Plus",
19524:          "bthci_acl.src.name": "Mustang Micro Plus",
19536:          "btatt.opcode": "0x1b",
19542:          "btatt.handle": "0x0017",
19544:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
19545:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
19547:          "btatt.value": "34:00:75:46:75:6e:6b:0a:0b:53:75:6e:20:53:65:73:73:69:6f:6e:0a:07:35:37:20:54:77:69:6e:0a:06:50:68:61:73:65:64:0a:0d:49:72:69:73:68:20:41:6e:74:68:65:6d:73:0a:0b:53:74:75:64:69:6f:20:50:72:65:20:0
a:0c:41:6e:67:72:79:20:52:6f:64:65:6e:74:0a:0d:42:6c:6b:20:48:6f:6c:65:20:56:69:62:65:0a:0c:53:69:6e:67:6c:65:20:53:63:6f:6f:70:0a:0b:45:61:72:6c:79:20:45:64:64:69"
19588:          "bthci_acl.src.name": "Mustang Micro Plus",
19635:          "bthci_acl.src.name": "Mustang Micro Plus",
19647:          "btatt.opcode": "0x1b",
19653:          "btatt.handle": "0x0017",
19655:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
19656:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
19658:          "btatt.value": "34:00:75:65:0a:0d:56:69:6e:74:61:67:65:20:54:68:72:6f:62:0a:0d:4c:6f:75:64:20:41:73:20:4c:65:65:64:73:0a:0c:45:76:69:6c:20:42:61:73:73:6d:61:6e:0a:0f:46:69:6c:74:65:72:65:64:20:45:63:68:6f:65:73:0
a:0c:52:6f:63:6b:20:61:20:42:69:6c:6c:79:0a:0a:4d:65:74:61:6c:6c:69:63:20:61:0a:0b:43:72:61:7a:79:20:54:72:61:69:6e:0a:0e:48:6f:74:65:6c:20:43:61:6c:69:66:6f:72:6e"
19699:          "bthci_acl.src.name": "Mustang Micro Plus",
19746:          "bthci_acl.src.name": "Mustang Micro Plus",
19758:          "btatt.opcode": "0x1b",
19764:          "btatt.handle": "0x0017",
19766:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
19767:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
19769:          "btatt.value": "34:00:75:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:10:6d:75:72:64:72:75:73:20:66:6c:61:6
9:6c:69:6e:67:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a"
19810:          "bthci_acl.src.name": "Mustang Micro Plus",
19822:          "btatt.opcode": "0x1b",
19828:          "btatt.handle": "0x0017",
19830:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
19831:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
19833:          "btatt.value": "35:00:5e:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0
a:05:45:6d:70:74:79:0a:05:45:6d:70:74:79:0a:10:45:6d:70:74:79:20:20:20:20:20:20:20:20:20:20:20"
19874:          "bthci_acl.src.name": "JA",
19886:          "btatt.opcode": "0x52",
19892:          "btatt.handle": "0x001b",
19894:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
19895:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
19897:          "btatt.value": "35:00:05:0a:03:c2:01:00"
19976:          "bthci_acl.src.name": "Mustang Micro Plus",
19988:          "btatt.opcode": "0x1b",
19994:          "btatt.handle": "0x0017",
19996:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
19997:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
19999:          "btatt.value": "35:00:0a:12:08:08:01:2a:04:08:46:20:01"
20040:          "bthci_acl.src.name": "Mustang Micro Plus",
20087:          "bthci_acl.src.name": "Mustang Micro Plus",
20099:          "btatt.opcode": "0x1b",
20105:          "btatt.handle": "0x0017",
20107:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
20108:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
20110:          "btatt.value": "33:00:75:12:e0:08:08:02:22:db:08:0a:d8:08:f1:06:7b:22:6e:6f:64:65:54:79:70:65:22:3a:22:70:72:65:73:65:74:22:2c:14:00:28:49:64:12:00:d0:76:65:72:73:69:6f:6e:22:3a:22:31:2e:31:22:00:b1:75:6d:49:6e:7
0:75:74:73:22:3a:32:0e:00:35:4f:75:74:0f:00:f0:03:69:6e:66:6f:22:3a:7b:22:64:69:73:70:6c:61:79:4e:61:6d:5e:00:c3:4d:65:74:61:6c:6c:69:63:20:61:22:2c:6b:00:20:5f:69"
20151:          "bthci_acl.src.name": "Mustang Micro Plus",
20198:          "bthci_acl.src.name": "Mustang Micro Plus",
20210:          "btatt.opcode": "0x1b",
20216:          "btatt.handle": "0x0017",
20218:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
20219:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
20221:          "btatt.value": "34:00:75:65:00:f2:4e:65:63:35:30:33:30:33:66:2d:33:62:31:32:2d:34:61:63:32:2d:38:64:30:39:2d:37:35:61:35:33:33:38:31:38:62:63:37:22:2c:22:61:75:74:68:6f:72:22:3a:22:30:30:62:62:35:64:32:34:2d:38:3
3:39:39:2d:34:32:62:37:2d:62:63:32:34:2d:34:36:30:34:63:64:65:34:65:63:39:66:22:2c:22:73:6f:75:72:63:65:63:00:13:6d:7c:00:f3:19:61:2d:36:37:34:37:31:36:37:36:33:22"
20262:          "bthci_acl.src.name": "Mustang Micro Plus",
20309:          "bthci_acl.src.name": "Mustang Micro Plus",
20321:          "btatt.opcode": "0x1b",
20327:          "btatt.handle": "0x0017",
20329:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
20330:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
20332:          "btatt.value": "34:00:75:2c:22:74:69:6d:65:73:74:61:6d:70:22:3a:30:2e:30:2c:22:63:72:65:61:74:65:64:5f:61:74:11:00:63:70:72:6f:64:75:63:a7:00:f1:1c:6d:75:73:74:61:6e:67:2d:6c:74:78:22:2c:22:69:73:5f:66:61:63:74:6
f:72:79:5f:64:65:66:61:75:6c:74:22:3a:74:72:75:65:2c:22:62:70:6d:3f:00:10:7d:b3:00:80:64:69:6f:47:72:61:70:68:0f:01:00:6a:01:42:73:22:3a:5b:0a:00:01:60:01:50:73:74"
20373:          "bthci_acl.src.name": "Mustang Micro Plus",
20420:          "bthci_acl.src.name": "Mustang Micro Plus",
20432:          "btatt.opcode": "0x1b",
20438:          "btatt.handle": "0x0017",
20440:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
20441:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
20443:          "btatt.value": "34:00:75:6f:6d:70:4f:01:06:85:01:60:64:73:70:55:6e:69:86:01:61:46:65:6e:64:65:72:28:00:f4:05:41:43:44:5f:4c:61:72:67:65:4f:76:65:72:64:72:69:76:65:22:2c:2a:00:a0:50:61:72:61:6d:65:74:65:72:73:65:0
0:c0:62:79:70:61:73:73:22:3a:66:61:6c:73:8d:00:01:0f:00:03:e0:01:30:50:6f:73:58:00:50:6c:65:76:65:6c:a3:00:21:36:30:01:00:f0:00:32:33:38:34:31:38:35:37:39:2c:22:67"
20484:          "bthci_acl.src.name": "Mustang Micro Plus",
20531:          "bthci_acl.src.name": "Mustang Micro Plus",
20543:          "btatt.opcode": "0x1b",
20549:          "btatt.handle": "0x0017",
20551:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
20552:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
20554:          "btatt.value": "34:00:75:61:69:6e:1a:00:40:37:35:39:38:5e:01:f0:02:39:38:32:34:35:32:33:39:2c:22:6c:6f:77:74:6f:6e:65:1d:00:f4:06:34:39:38:33:31:36:34:39:36:36:31:30:36:34:31:35:2c:22:6d:69:64:1d:00:f4:08:30:39:3
7:36:34:33:30:39:39:37:32:35:32:34:36:34:33:2c:22:68:69:67:68:1f:00:f7:04:33:37:36:36:37:39:39:38:36:37:31:35:33:31:36:38:7d:7d:2c:0f:01:3f:6d:6f:64:0d:01:10:cf:44"
20595:          "bthci_acl.src.name": "Mustang Micro Plus",
20642:          "bthci_acl.src.name": "Mustang Micro Plus",
20654:          "btatt.opcode": "0x1b",
20660:          "btatt.handle": "0x0017",
20662:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
20663:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
20665:          "btatt.value": "34:00:75:55:42:53:5f:55:6e:6b:6e:6f:77:6e:07:01:26:0a:79:00:00:04:02:0f:86:01:13:6f:45:76:68:33:47:54:77:00:05:40:76:6f:6c:75:ed:02:f0:04:2d:31:33:2e:38:30:31:38:38:35:36:30:34:38:35:38:33:39:38:5
d:01:32:74:65:50:63:03:f3:01:3a:22:73:75:70:65:72:22:2c:22:63:61:62:73:69:6d:a1:01:60:34:78:31:32:67:22:2a:00:02:87:01:f1:04:35:30:31:36:38:33:34:37:33:35:38:37:30"
20706:          "bthci_acl.src.name": "Mustang Micro Plus",
20753:          "bthci_acl.src.name": "Mustang Micro Plus",
20765:          "btatt.opcode": "0x1b",
20771:          "btatt.handle": "0x0017",
20773:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
20774:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
20776:          "btatt.value": "34:00:75:33:36:31:2c:22:62:dd:01:50:30:2e:35:37:39:01:00:82:38:33:33:31:30:36:39:39:84:01:00:19:00:21:31:35:d4:01:f0:01:30:35:39:36:30:34:36:34:34:38:2c:22:74:72:65:62:1b:00:20:36:38:33:00:f0:01:3
9:39:37:36:31:35:38:31:34:32:2c:22:62:69:61:73:1a:00:f4:02:35:2c:22:73:61:67:22:3a:31:2c:22:6d:61:73:74:65:72:c8:00:00:35:00:02:50:00:80:37:31:35:32:35:35:37:34:15"
20817:          "bthci_acl.src.name": "Mustang Micro Plus",
20864:          "bthci_acl.src.name": "Mustang Micro Plus",
20876:          "btatt.opcode": "0x1b",
20882:          "btatt.handle": "0x0017",
20884:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
20885:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
20887:          "btatt.value": "34:00:75:03:51:65:73:65:6e:63:0b:02:13:36:6f:00:8a:39:35:33:36:37:34:33:32:56:01:5f:64:65:6c:61:79:58:01:10:0f:d1:01:40:6f:72:65:76:65:72:62:7c:00:57:d2:5d:2c:22:63:6f:6e:6e:65:63:74:69:6f:6e:e5:0
3:50:69:6e:70:75:74:93:03:00:f8:03:01:c6:03:04:10:01:c1:69:6e:64:65:78:22:3a:30:7d:2c:22:6f:37:05:09:26:00:05:bb:00:05:27:00:00:24:03:0f:4e:00:0e:1f:31:4e:00:13:11"
20928:          "bthci_acl.src.name": "Mustang Micro Plus",
20975:          "bthci_acl.src.name": "Mustang Micro Plus",
20987:          "btatt.opcode": "0x1b",
20993:          "btatt.handle": "0x0017",
20995:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
20996:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
20998:          "btatt.value": "34:00:75:31:a3:01:0e:4e:00:0e:76:00:0f:9d:00:02:01:fd:01:00:45:04:0f:9d:00:0c:0c:4f:00:0f:9e:00:04:0c:4f:00:0f:9e:00:05:02:19:04:05:74:00:0f:9b:00:02:02:c4:03:05:24:00:0f:35:01:03:09:49:00:0f:95:0
0:04:09:49:00:0f:92:00:05:09:25:00:0f:92:00:04:0d:f0:01:0f:94:00:03:09:4b:00:0f:94:00:04:0b:4b:00:0f:96:00:05:0c:50:01:0f:99:00:04:04:77:06:0f:2d:01:0c:0c:4e:00:0f"
21039:          "bthci_acl.src.name": "JA",
21051:          "btatt.opcode": "0x52",
21057:          "btatt.handle": "0x001b",
21059:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
21060:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
21062:          "btatt.value": "35:00:05:0a:03:c2:01:00"
21141:          "bthci_acl.src.name": "Mustang Micro Plus",
21153:          "btatt.opcode": "0x1b",
21159:          "btatt.handle": "0x0017",
21161:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
21162:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
21164:          "btatt.value": "35:00:46:9c:00:04:0b:4e:00:0f:9c:00:05:0b:27:00:0f:9b:00:04:09:a1:01:0f:32:01:05:0b:4b:00:0f:98:00:04:09:4b:00:f9:02:31:7d:7d:5d:7d:2c:22:6d:6f:64:69:66:69:61:62:6c:65:74:03:00:92:08:04:47:07:80:4
9:64:22:3a:22:22:7d:7d"
21205:          "bthci_acl.src.name": "Mustang Micro Plus",
21217:          "btatt.opcode": "0x1b",
21223:          "btatt.handle": "0x0017",
21225:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
21226:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
21228:          "btatt.value": "35:00:0e:12:0c:08:02:92:01:07:0d:00:00:80:3f:10:01"
21269:          "bthci_acl.src.name": "Mustang Micro Plus",
21281:          "btatt.opcode": "0x1b",
21287:          "btatt.handle": "0x0017",
21289:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
21290:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
21292:          "btatt.value": "35:00:09:12:07:08:02:9a:01:02:08:01"
21333:          "bthci_acl.src.name": "JA",
21345:          "btatt.opcode": "0x52",
21351:          "btatt.handle": "0x001b",
21353:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
21354:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
21356:          "btatt.value": "35:00:05:0a:03:c2:01:00"
21542:          "bthci_acl.src.name": "JA",
21554:          "btatt.opcode": "0x52",
21560:          "btatt.handle": "0x001b",
21562:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
21563:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
21565:          "btatt.value": "35:00:05:0a:03:c2:01:00"
21644:          "bthci_acl.src.name": "JA",
21656:          "btatt.opcode": "0x52",
21662:          "btatt.handle": "0x001b",
21664:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
21665:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
21667:          "btatt.value": "35:00:05:0a:03:c2:01:00"
21746:          "bthci_acl.src.name": "JA",
21758:          "btatt.opcode": "0x52",
21764:          "btatt.handle": "0x001b",
21766:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
21767:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
21769:          "btatt.value": "35:00:05:0a:03:c2:01:00"
21848:          "bthci_acl.src.name": "JA",
21860:          "btatt.opcode": "0x52",
21866:          "btatt.handle": "0x001b",
21868:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
21869:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
21871:          "btatt.value": "35:00:06:0a:04:42:02:08:47"
21950:          "bthci_acl.src.name": "Mustang Micro Plus",
21962:          "btatt.opcode": "0x1b",
21968:          "btatt.handle": "0x0017",
21970:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
21971:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
21973:          "btatt.value": "35:00:0a:12:08:08:01:2a:04:08:47:20:01"
22014:          "bthci_acl.src.name": "Mustang Micro Plus",
22061:          "bthci_acl.src.name": "Mustang Micro Plus",
22073:          "btatt.opcode": "0x1b",
22079:          "btatt.handle": "0x0017",
22081:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
22082:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
22084:          "btatt.value": "33:00:75:12:88:09:08:02:22:83:09:0a:80:09:f1:06:7b:22:6e:6f:64:65:54:79:70:65:22:3a:22:70:72:65:73:65:74:22:2c:14:00:28:49:64:12:00:d0:76:65:72:73:69:6f:6e:22:3a:22:31:2e:31:22:00:b1:75:6d:49:6e:7
0:75:74:73:22:3a:32:0e:00:35:4f:75:74:0f:00:f0:03:69:6e:66:6f:22:3a:7b:22:64:69:73:70:6c:61:79:4e:61:6d:5e:00:d3:43:72:61:7a:79:20:54:72:61:69:6e:22:2c:6c:00:20:5f"
22125:          "bthci_acl.src.name": "Mustang Micro Plus",
22172:          "bthci_acl.src.name": "Mustang Micro Plus",
22184:          "btatt.opcode": "0x1b",
22190:          "btatt.handle": "0x0017",
22192:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
22193:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
22195:          "btatt.value": "34:00:75:69:66:00:f2:4e:61:34:66:36:63:36:61:37:2d:31:37:37:39:2d:34:61:33:35:2d:62:32:66:35:2d:39:35:61:36:33:30:37:61:35:64:38:39:22:2c:22:61:75:74:68:6f:72:22:3a:22:30:30:62:62:35:64:32:34:2d:3
8:33:39:39:2d:34:32:62:37:2d:62:63:32:34:2d:34:36:30:34:63:64:65:34:65:63:39:66:22:2c:22:73:6f:75:72:63:65:63:00:10:63:7d:00:20:2d:74:7d:00:f3:19:2d:31:37:33:37:30"
22236:          "bthci_acl.src.name": "Mustang Micro Plus",
22283:          "bthci_acl.src.name": "Mustang Micro Plus",
22295:          "btatt.opcode": "0x1b",
22301:          "btatt.handle": "0x0017",
22303:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
22304:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
22306:          "btatt.value": "34:00:75:37:30:37:35:37:22:2c:22:74:69:6d:65:73:74:61:6d:70:22:3a:30:2e:30:2c:22:63:72:65:61:74:65:64:5f:61:74:11:00:63:70:72:6f:64:75:63:aa:00:f1:1d:6d:75:73:74:61:6e:67:2d:6c:74:78:22:2c:22:69:7
3:5f:66:61:63:74:6f:72:79:5f:64:65:66:61:75:6c:74:22:3a:66:61:6c:73:65:2c:22:62:70:6d:40:00:10:7d:b7:00:80:64:69:6f:47:72:61:70:68:14:01:00:6f:01:42:73:22:3a:5b:0a"
22347:          "bthci_acl.src.name": "Mustang Micro Plus",
22394:          "bthci_acl.src.name": "Mustang Micro Plus",
22406:          "btatt.opcode": "0x1b",
22412:          "btatt.handle": "0x0017",
22414:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
22415:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
22417:          "btatt.value": "34:00:75:00:01:65:01:50:73:74:6f:6d:70:54:01:06:8a:01:60:64:73:70:55:6e:69:8b:01:61:46:65:6e:64:65:72:28:00:b0:41:43:44:5f:42:6c:61:63:6b:62:6f:7e:00:03:24:00:a0:50:61:72:61:6d:65:74:65:72:73:5f:0
0:66:62:79:70:61:73:73:87:00:01:0f:00:03:df:01:30:50:6f:73:52:00:11:6f:aa:01:f0:0e:6c:65:76:65:6c:22:3a:2d:32:38:2e:34:35:30:30:30:30:37:36:32:39:33:39:34:35:33:2c"
22458:          "bthci_acl.src.name": "Mustang Micro Plus",
22505:          "bthci_acl.src.name": "Mustang Micro Plus",
22517:          "btatt.opcode": "0x1b",
22523:          "btatt.handle": "0x0017",
22525:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
22526:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
22528:          "btatt.value": "34:00:75:22:67:a3:01:61:3a:30:2e:33:34:39:01:00:e1:34:30:33:39:35:33:35:35:2c:22:74:6f:6e:65:d8:00:f7:04:39:31:38:33:36:37:33:35:36:30:36:31:39:33:35:34:7d:7d:2c:d2:00:3f:6d:6f:64:d0:00:10:b0:44:5
5:42:53:5f:55:6e:6b:6e:6f:77:12:02:0f:d0:00:23:0a:79:00:00:c8:01:02:bf:02:03:f4:00:03:55:00:0e:49:01:7f:52:65:63:74:32:47:54:78:00:05:40:76:6f:6c:75:b6:02:30:2d:31"
22569:          "bthci_acl.src.name": "Mustang Micro Plus",
22616:          "bthci_acl.src.name": "Mustang Micro Plus",
22628:          "btatt.opcode": "0x1b",
22634:          "btatt.handle": "0x0017",
22636:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
22637:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
22639:          "btatt.value": "34:00:75:32:16:02:52:67:61:74:65:50:1e:03:f3:00:3a:22:68:69:67:68:22:2c:22:63:61:62:73:69:6d:68:00:70:34:78:31:32:67:32:22:2a:00:02:3c:01:f1:04:31:39:37:32:37:38:39:31:36:38:33:35:37:38:34:39:2c:2
2:62:99:01:f0:07:30:2e:32:30:30:36:38:30:32:37:30:37:39:31:30:35:33:37:37:2c:22:6d:a4:02:f0:0a:30:2e:34:30:31:33:36:30:35:34:31:35:38:32:31:30:37:35:34:2c:22:74:72"
22680:          "bthci_acl.src.name": "Mustang Micro Plus",
22727:          "bthci_acl.src.name": "Mustang Micro Plus",
22739:          "btatt.opcode": "0x1b",
22745:          "btatt.handle": "0x0017",
22747:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
22748:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
22750:          "btatt.value": "34:00:75:65:62:1b:00:1e:36:4f:00:30:69:61:73:1a:00:f4:02:35:2c:22:73:61:67:22:3a:31:2c:22:6d:61:73:74:65:72:bb:00:f0:03:30:2e:35:30:35:33:39:32:30:31:34:39:38:30:33:31:36:32:cd:02:51:65:73:65:6e:6
3:c4:01:fa:02:34:39:36:35:39:38:36:33:31:31:34:33:35:36:39:39:35:4b:01:5f:64:65:6c:61:79:96:02:10:0f:c6:01:40:6f:72:65:76:65:72:62:7c:00:10:42:41:43:44:5f:21:03:9f"
22791:          "bthci_acl.src.name": "JA",
22803:          "btatt.opcode": "0x52",
22809:          "btatt.handle": "0x001b",
22811:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
22812:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
22814:          "btatt.value": "35:00:05:0a:03:c2:01:00"
22855:          "bthci_acl.src.name": "Mustang Micro Plus",
22940:          "bthci_acl.src.name": "Mustang Micro Plus",
22952:          "btatt.opcode": "0x1b",
22958:          "btatt.handle": "0x0017",
22960:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
22961:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
22963:          "btatt.value": "34:00:75:53:6d:61:6c:6c:52:6f:6f:6d:d1:01:05:0f:19:03:10:60:77:65:74:6c:76:6c:39:01:f0:07:36:34:36:32:35:38:35:33:33:30:30:30:39:34:36:2c:22:64:65:63:61:79:1a:00:0f:a3:01:00:41:64:77:65:6c:36:00:f
1:09:33:39:37:39:35:39:31:37:32:37:32:35:36:37:37:35:2c:22:64:69:66:66:75:73:6e:01:07:3d:03:0c:28:00:f2:00:7d:7d:5d:2c:22:63:6f:6e:6e:65:63:74:69:6f:6e:1e:04:10:69"
23004:          "bthci_acl.src.name": "Mustang Micro Plus",
23051:          "bthci_acl.src.name": "Mustang Micro Plus",
23063:          "btatt.opcode": "0x1b",
23069:          "btatt.handle": "0x0017",
23071:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
23072:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
23074:          "btatt.value": "34:00:75:5e:05:31:22:3a:7b:80:01:01:ff:03:02:55:03:94:69:6e:64:65:78:22:3a:30:7d:c9:03:09:24:00:02:00:03:05:24:00:00:95:03:0f:49:00:0c:1f:31:49:00:10:11:31:1a:02:0e:49:00:0b:6e:00:0f:92:00:02:04:4
7:02:0f:94:00:0c:09:4b:00:0f:94:00:04:0b:4b:00:0f:96:00:05:0b:27:00:0f:98:00:04:05:64:02:0f:99:00:0c:0b:4e:00:0f:9b:00:04:0c:4e:00:0f:9c:00:05:0c:28:00:0f:9d:00:04"
23115:          "bthci_acl.src.name": "Mustang Micro Plus",
23127:          "btatt.opcode": "0x1b",
23133:          "btatt.handle": "0x0017",
23135:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
23136:            "btatt.uuid128": "10:17:ad:cc:dc:bc:43:87:a5:9f:25:46:b2:ea:5b:b0"
23138:          "btatt.value": "35:00:6e:01:a7:03:1f:74:9d:00:0f:0c:4f:00:0f:9e:00:04:0c:4f:00:0f:9e:00:05:0c:28:00:0f:9e:00:04:04:b0:06:0f:3a:01:0c:0c:4e:00:0f:9d:00:04:0b:4e:00:0f:9c:00:05:0b:27:00:0f:9b:00:04:0b:24:03:0f:00:0
3:03:0b:4b:00:0f:98:00:04:09:4b:00:f9:02:31:7d:7d:5d:7d:2c:22:6d:6f:64:69:66:69:61:62:6c:65:76:03:00:cf:08:04:86:07:80:49:64:22:3a:22:22:7d:7d"
23179:          "bthci_acl.src.name": "JA",
23191:          "btatt.opcode": "0x52",
23197:          "btatt.handle": "0x001b",
23199:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
23200:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
23202:          "btatt.value": "35:00:05:0a:03:c2:01:00"
23281:          "bthci_acl.src.name": "JA",
23293:          "btatt.opcode": "0x52",
23299:          "btatt.handle": "0x001b",
23301:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
23302:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
23304:          "btatt.value": "35:00:05:0a:03:c2:01:00"
23383:          "bthci_acl.src.name": "JA",
23395:          "btatt.opcode": "0x52",
23401:          "btatt.handle": "0x001b",
23403:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
23404:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
23406:          "btatt.value": "35:00:05:0a:03:c2:01:00"
23485:          "bthci_acl.src.name": "JA",
23497:          "btatt.opcode": "0x52",
23503:          "btatt.handle": "0x001b",
23505:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
23506:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
23508:          "btatt.value": "35:00:05:0a:03:c2:01:00"
23587:          "bthci_acl.src.name": "JA",
23599:          "btatt.opcode": "0x52",
23605:          "btatt.handle": "0x001b",
23607:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
23608:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
23610:          "btatt.value": "35:00:05:0a:03:c2:01:00"
23689:          "bthci_acl.src.name": "JA",
23701:          "btatt.opcode": "0x52",
23707:          "btatt.handle": "0x001b",
23709:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
23710:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
23712:          "btatt.value": "35:00:05:0a:03:c2:01:00"
23791:          "bthci_acl.src.name": "JA",
23803:          "btatt.opcode": "0x52",
23809:          "btatt.handle": "0x001b",
23811:            "btatt.service_uuid128": "90:55:95:80:b7:07:11:ee:ac:b1:7b:7e:30:f1:af:54",
23812:            "btatt.uuid128": "82:0a:7e:34:4e:0a:4f:90:85:20:04:eb:ce:35:a3:a1"
23814:          "btatt.value": "35:00:05:0a:03:c2:01:00"
23893:          "bthci_acl.src.name": "JA",
23905:          "btatt.opcode": "0x52",
23911:          "btatt.handle": "0x001b",
"""