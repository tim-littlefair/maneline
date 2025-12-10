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
            assert m_transactionName == null: String.format(
                "Attempt to set transaction name to %s when transaction %s is already in progress",
                transactionName, m_transactionName
            );
            m_transactionName = transactionName;
        } else {
            assert m_transactionName != null: String.format(
                "Attempt to set transaction name to null when no transaction is in progress",
                transactionName, m_transactionName
            );
            m_transactionName = null;
        }
    }

    @Override
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
