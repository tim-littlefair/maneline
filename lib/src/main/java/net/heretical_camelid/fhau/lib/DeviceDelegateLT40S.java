package net.heretical_camelid.fhau.lib;

class DeviceDelegateLT40S extends DeviceDelegateBase {
    public DeviceDelegateLT40S() {
        m_deviceDescription = "Simulated LT40S";
        m_firmwareVersion = "99.11.13";
        m_presetInfo = PresetInfo.piMixedBag();
    }

    public void startup() {

    }
}
