package net.heretical_camelid.maneline.lib.interfaces;

public abstract class LoggingAgentBase implements ILoggingAgent {
    private String m_sessionName = null;
    private String m_transactionName = null;

    public void setSessionName(String sessionName) {
        assert m_sessionName == null:
            "LoggingAgentBase::setSessionName(...) has already been called"
        ;
        m_sessionName = sessionName;
        appendToLog(String.format("Session name is %s",sessionName));
    }

    public String getSessionName() {
        return m_sessionName;
    }

    @Override
    public void setTransactionName(String transactionName) {
        assert m_sessionName != null: "Transaction name should not be set before session name";
        if(transactionName!=null) {
            appendToLog(String.format("Start of transaction %s",transactionName));
            m_transactionName = transactionName;
        } else {
            String endingTransactionName = m_transactionName;
            m_transactionName = null;
            appendToLog(String.format("End of transaction %s",endingTransactionName));
        }
    }

    public String getTransactionName() {
        return m_transactionName;
    }
    @Override
    public void clearLog() {
        // This implementation does nothing but
        // will be overridden in the class(es)
        // which adapt the log for immediate display
        // on a UI.
    }

    @Override
    public void appendToLog(String messageToAppend) {
        this.appendToLog(messageToAppend,null);
    }
}
