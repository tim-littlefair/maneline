package net.heretical_camelid.maneline.lib.delegates;

class DeviceDelegateLT40S extends DeviceDelegateBase {
    public DeviceDelegateLT40S() {
        m_deviceDescription = "Simulated LT40S";
        m_firmwareVersion = "99.11.13";
        // m_presetInfo = SimulatorAmpProvider.IVisitor.piMixedBag();
    }

    public void startup() {

    }

    public static void main(String[] args) {
        System.out.println("TODO: tests for DeviceDelegateLT40S");
    }
}
