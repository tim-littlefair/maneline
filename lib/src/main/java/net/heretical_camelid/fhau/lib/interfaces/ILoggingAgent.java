package net.heretical_camelid.fhau.lib.interfaces;

/**
 * The interface below is intended to define an interface for
 * logging.
 */
public interface ILoggingAgent {
    void clearLog();
    void appendToLog(String messageToAppend, Object extraObject);
    void appendToLog(String messageToAppend);

    public static void main(String[] args) {
        System.out.println("No tests for ILoggingAgent");
    }
}

