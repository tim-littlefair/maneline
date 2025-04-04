package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.interfaces.IAmpProvider;
import net.heretical_camelid.fhau.lib.interfaces.IDeviceTransport;

import java.util.HashMap;

import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static net.heretical_camelid.fhau.android_app.MainActivity.appendToLog;

class DeviceTransportUsbHid implements IDeviceTransport {
    UsbHidDevice m_usbHidDevice;
    final static int TIMEOUT_MS = 500;

    DeviceTransportUsbHid(UsbHidDevice hidDevice) {
        m_usbHidDevice = null;
    }

    void setUsbHidDevice(UsbHidDevice uhd) {
        m_usbHidDevice = uhd;
    }

    @Override
    public int read(byte[] packetBuffer) {
        final int NUM_ATTEMPTS=3;
        final int MSEC_BETWEEN_ATTEMPTS=100;
        for(int i=0; i<NUM_ATTEMPTS; ++i) {
            try {
                byte[] bytesRead = m_usbHidDevice.read(packetBuffer.length,TIMEOUT_MS);
                if(bytesRead==null) {
                    return 0;
                } else {
                    System.arraycopy(bytesRead, 0, packetBuffer, 0, bytesRead.length);
                    return bytesRead.length;
                }
            }
            catch(IllegalArgumentException e) {
                try {
                    Thread.sleep(MSEC_BETWEEN_ATTEMPTS);
                } catch (InterruptedException ex) {
                    // Do nothing
                }
            }
        }
        return 0;
    }

    @Override
    public int write(byte[] commandBytes) {
        m_usbHidDevice.write(commandBytes);
        return commandBytes.length;
    }
    @Override
    public String getLastErrorMessage() {
        return "not available";
    }

    IAmpProvider.ProviderState_e attemptUsbHidConnection(AndroidUsbAmpProvider provider) {
        if(provider.m_usbDevice==null)
        {
            UsbDevice usbDevice = null;
            HashMap<String, UsbDevice> usbDeviceMap = provider.m_usbManager.getDeviceList();
            if (usbDeviceMap == null) {
                appendToLog("device map not received");
                provider.m_state = IAmpProvider.ProviderState_e.PROVIDER_NO_APPLICABLE_DEVICE;
                return provider.m_state;
            } else if (usbDeviceMap.size() == 0) {
                appendToLog("device map empty");
                provider.m_state = IAmpProvider.ProviderState_e.PROVIDER_NO_APPLICABLE_DEVICE;
                return provider.m_state;
            } else {
                for (String deviceName : usbDeviceMap.keySet()) {
                    usbDevice = usbDeviceMap.get(deviceName);
                    if (usbDevice.getVendorId() != 0x1ed8) {
                        appendToLog(String.format(
                            "non-FMIC device found with vid=%04x pid=%04x",
                            usbDevice.getVendorId(), usbDevice.getProductId()
                        ));
                        usbDevice = null;
                        continue;
                    } else {
                        appendToLog(String.format(
                            "FMIC device found with vid=%04x pid=%04x",
                            usbDevice.getVendorId(), usbDevice.getProductId()
                        ));
                        appendToLog(
                            "FMIC product name: " + usbDevice.getProductName()
                        );
                        provider.m_usbDevice = usbDevice;
                        break;
                    }
                }
                if(provider.m_usbDevice==null) {
                    appendToLog(
                        "Device map did not contain any applicable devices"
                    );
                    provider.m_state = IAmpProvider.ProviderState_e.PROVIDER_NO_APPLICABLE_DEVICE;
                    return provider.m_state;
                }
            }
            assert provider.m_usbDevice!=null;
            if(provider.m_usbHidDevice==null) {
                if (!provider.m_usbManager.hasPermission(provider.m_usbDevice)) {
                    appendToLog(
                        "Requesting permission to connect to device"
                    );
                    provider.m_deviceTransportUsbHid.registerForPermissionIntent(provider);
                    provider.m_usbManager.requestPermission(provider.m_usbDevice, provider.m_permissionIntent);
                    provider.m_state = IAmpProvider.ProviderState_e.PROVIDER_CONNECTING_TO_DEVICE;
                    return provider.m_state;
                } else {
                    appendToLog("Permission to connect to device already held");
                    provider.m_state = IAmpProvider.ProviderState_e.PROVIDER_CONNECTING_TO_DEVICE;
                }

                if(provider.m_state == IAmpProvider.ProviderState_e.PROVIDER_CONNECTING_TO_DEVICE) {
                    try {
                        // If we are still in the function at this point,
                        // we must have permission, so we try to connect
                        UsbDeviceConnection cxn = provider.m_usbManager.openDevice(provider.m_usbDevice);
                        if (cxn != null) {
                            m_usbHidDevice = UsbHidDevice.factory(
                                provider.m_mainActivity,
                                provider.m_usbDevice.getVendorId(), provider.m_usbDevice.getProductId()
                            );
                            if (m_usbHidDevice == null) {
                                m_usbHidDevice.open(provider.m_mainActivity, provider);
                                appendToLog("No USB HID device found");
                                provider.m_state = IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
                                return provider.m_state;
                            }
                            m_usbHidDevice.open(provider.m_mainActivity, null);
                            provider.m_protocol.setDeviceTransport(this);
                            provider.m_connectionSucceeded = true;
                            provider.m_state = IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_SUCCEEDED;
                        } else {
                            provider.m_state = IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
                        }
                    } catch (Exception e) {
                        appendToLog("Exception caught - see logcat for details");
                        System.out.println(e.toString());
                        e.printStackTrace(System.out);
                        provider.m_state = IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
                    }
                } else {
                    appendToLog("attemptConnection failed, state=" + provider.m_state);
                }
            } else {
                appendToLog("attemptConnection: m_usbHidDevice non-null, state=" + provider.m_state);
            }
        }
        return provider.m_state;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    void registerForPermissionIntent(AndroidUsbAmpProvider provider) {
        if (provider.m_usbReceiver == null) {
            provider.m_usbReceiver = new UsbBroadcastReceiver();
            provider.m_usbReceiver.setProvider(provider);
            provider.m_permissionIntent = PendingIntent.getBroadcast(
                provider.m_mainActivity, 0,
                new Intent(AndroidUsbAmpProvider.ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            );
            IntentFilter filter = new IntentFilter(AndroidUsbAmpProvider.ACTION_USB_PERMISSION);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            filter.addAction(UsbManager.EXTRA_PERMISSION_GRANTED);
            appendToLog("Registering for permission intent");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                provider.m_mainActivity.registerReceiver(provider.m_usbReceiver, filter, RECEIVER_NOT_EXPORTED);
            } else {
                provider.m_mainActivity.registerReceiver(provider.m_usbReceiver, filter);
            }
            appendToLog("Registered for permission intent");
        } else {
            appendToLog("Already registered for permission intent");
        }
    }
}
