package net.heretical_camelid.fhau.lib;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import com.benlypan.usbhid.OnUsbHidDeviceListener;
import com.benlypan.usbhid.UsbHidDevice;

public class AndroidUsbProvider {

    public void connect(Context context, String[] commandHexStrings, StringBuilder sb) {
        // Upstream UsbHid project searched for this VID/PID
        // UsbHidDevice device = UsbHidDevice.factory(this, 0x0680, 0x0180);
        // This fork searches for any device associated with the Fender VID
        UsbHidDevice device = UsbHidDevice.factory(context, 0x0, 0x0);
        if (device == null) {
            sb.append("No device found\n");
            return;
        }
        UsbDevice usbDevice = device.getUsbDevice();
        sb.append(String.format(
                "Device found: id=%d sn=%s vid=%04x pid=%04x pname=%s\n",
                device.getDeviceId(),
                device.getSerialNumber(),
                usbDevice.getVendorId(),
                usbDevice.getProductId(),
                usbDevice.getProductName()
        ));
        device.open(context, new OnUsbHidDeviceListener() {
            @Override
            public void onUsbHidDeviceConnected(UsbHidDevice device) {
                sb.append("Device HID connection succeeded\n");

                for (int i = 0; i < commandHexStrings.length; ++i) {
                    device.write(ByteArrayTranslator.hexToBytes(commandHexStrings[i]));
                    byte[] responseBytes = device.read(64);
                    sb.append("Sent " + commandHexStrings[i] + "\n");
                    sb.append("Received " + ByteArrayTranslator.bytesToHex(responseBytes) + "\n");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        sb.append("sleep interrupted\n");
                    }
                }
            }

            @Override
            public void onUsbHidDeviceConnectFailed(UsbHidDevice device) {
                sb.append("Device HID connection failed\n");
            }
        });
    }
}
