package net.heretical_camelid.fhau.lib;

import android.content.Context;

import com.benlypan.usbhid.OnUsbHidDeviceListener;

public interface IAmplifierProvider {
    boolean connect(
            Context context,
            OnUsbHidDeviceListener usbHidDeviceListener,
            StringBuilder sb
    );

    byte[] sendCommandAndReceiveResponse(String commandHexString, StringBuilder sb);

    PresetInfo getPresets(PresetInfo requestedPresets);
}
