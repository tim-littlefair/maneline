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

