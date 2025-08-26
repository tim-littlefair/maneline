package net.heretical_camelid.maneline.lib.delegates;

/**
 * TODO:
 * Implementations of this abstract base class will be defined at
 * so that logic specific to either LT40S or MMP can be kept
 * outside the publicly visible class.
 */
abstract class DeviceDelegateBase {
    public String m_deviceDescription;
    public String m_firmwareVersion;
    abstract void startup();
}
