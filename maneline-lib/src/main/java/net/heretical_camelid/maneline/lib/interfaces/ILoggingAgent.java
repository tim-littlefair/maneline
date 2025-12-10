package net.heretical_camelid.maneline.lib.interfaces;

import java.util.Map;

/**
 * The interface below is intended to define an interface for
 * logging.
 */
public interface ILoggingAgent {
    void setSessionName(String sessionName);
    void setTransactionName(String transactionName);
    void clearLog();
    void appendToLog(String messageToAppend, Map<String,String> extraAttributes);
    void appendToLog(String messageToAppend);

    public static void main(String[] args) {
        System.out.println("No tests for ILoggingAgent");
    }

    Object getTransactionName();
}

