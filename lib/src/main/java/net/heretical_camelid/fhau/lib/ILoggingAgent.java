package net.heretical_camelid.fhau.lib;

/**
 * The interface below is intended to define an interface for
 * logging.
 */
public interface ILoggingAgent {
    void clearLog();
    void setLevel(int loggingLevel);

    void appendToLog(int loggingLevel, String messageToAppend);
}

/**
 * The package-private class below is available
 * as a default implementation of the interface above for
 * clients which pass a null as the loggingAgent parameter
 * to the AmpManager constructor.
 */
class DefaultLoggingAgent implements ILoggingAgent {
    public void clearLog() { }
    public void setLevel(int loggingLevel) { }
    public void appendToLog(int loggingLevel, String messageToAppend) {
        System.out.println(messageToAppend);
    }
}
