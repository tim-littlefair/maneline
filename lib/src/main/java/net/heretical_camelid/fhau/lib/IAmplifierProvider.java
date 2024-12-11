package net.heretical_camelid.fhau.lib;

public interface IAmplifierProvider {
    boolean connect(
        StringBuilder sb
    );

    byte[] sendCommandAndReceiveResponse(String commandHexString, StringBuilder sb);

    PresetInfo getPresets(PresetInfo requestedPresets);
}
