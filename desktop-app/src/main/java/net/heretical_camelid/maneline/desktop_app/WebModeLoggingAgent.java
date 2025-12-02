package net.heretical_camelid.maneline.desktop_app;

import net.heretical_camelid.maneline.lib.interfaces.LoggingAgentBase;


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;

import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.MDC;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class WebModeLoggingAgent extends LoggingAgentBase {
    private static final Logger m_sessionLogger = (Logger) LoggerFactory.getLogger("SESSION_LOGGER");
    private static final Logger m_transactionLogger = (Logger) LoggerFactory.getLogger("TRANSACTION_LOGGER");

    private static PrintStream m_sessionLog;

    static WebModeLoggingAgent s_instance = null;

    private int m_txnNumber = 0;
    private int m_txnMessageCounter = 0;

    static void setSessionNameStatic(String sessionName) {
        assert s_instance != null;
        s_instance.setSessionName(sessionName);
        FileAppender<ILoggingEvent> sessionAppender =
            (FileAppender) m_sessionLogger.getAppender("SESSION_APPENDER")
        ;
        assert sessionAppender != null;
        m_sessionLogger.detachAppender(sessionAppender);
        sessionAppender.setFile(sessionName + "/session2.log");
        m_sessionLogger.addAppender(sessionAppender);
    }

    static String getSessionNameStatic() {
        assert s_instance != null;
        return s_instance.getSessionName();
    }

    public WebModeLoggingAgent() {
        super();

        // Hopefully this is only instantiated once
        assert s_instance == null;
        s_instance = this;
    }

    @Override
    public void appendToLog(
        String messageToAppend,
        Map<String,String> extraAttributes
    ) {
        assert messageToAppend != null;
        final String messageToAppendWithObject;
        if(extraAttributes!=null) {
            messageToAppendWithObject = String.format(
                "%s object=%s", messageToAppend, extraAttributes
            );
        } else {
            messageToAppendWithObject = messageToAppend;
        }
        if(getTransactionName() != null) {
            LoggingEventBuilder leb = m_transactionLogger.atDebug();
            leb = leb.setMessage(messageToAppend);
            if(extraAttributes!=null) {
                for(String k: extraAttributes.keySet()) {
                    leb = leb.addKeyValue(k,extraAttributes.get(k));
                }
            }
            leb.log();
            ++m_txnMessageCounter;
        } else {
            LoggingEventBuilder leb = m_sessionLogger.atDebug();
            leb = leb.setMessage(messageToAppend);
            if(extraAttributes!=null) {
                for(String k: extraAttributes.keySet()) {
                    leb = leb.addKeyValue(k,extraAttributes.get(k));
                }
            }
            leb.log();
        }
        if(m_sessionLog!=null) {
            m_sessionLog.println(messageToAppendWithObject);
        } else {
            System.out.println(messageToAppendWithObject);
        }
    }

    @Override
    public void setSessionName(String sessionName) {
        super.setSessionName(sessionName);
        try {
            m_sessionLog = new PrintStream(new FileOutputStream(
                sessionName + "/session.log"
            ));
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setTransactionName(String transactionName) {
        assert getSessionName() != null:
            "Session name must be set before setting transaction name"
        ;
        FileAppender<ILoggingEvent> transactionAppender =
            (FileAppender) m_transactionLogger.getAppender("TRANSACTION_APPENDER")
        ;
        assert transactionAppender != null;
        if(transactionName!=null) {
            assert getTransactionName() == null:
                "Transaction opened before previous transaction closed"
            ;
            super.setTransactionName(transactionName);
            ++m_txnNumber;
            m_txnMessageCounter=0;
            m_transactionLogger.detachAppender(transactionAppender);
            transactionAppender.setFile(String.format(
                "txn%03d-%s.log", m_txnNumber, transactionName
            ));
            m_transactionLogger.addAppender(transactionAppender);
            MDC.put("loggerFileName", String.format(
                "%s/txn%03d-%s.log", getSessionName(), m_txnNumber, transactionName
            ));
        } else {
            assert getTransactionName() != null:
                "Transaction closed when not previously open"
            ;
            // String closingLogFileDescription = LogbackRollingPolicy.describeCurrentLogFile();
            super.setTransactionName(null);
            /*
            LogbackRollingPolicy.setFilenamePattern(
                String.format("%s/session3.log",getSessionName())
            );
             */
            if(m_txnMessageCounter>0) {
                appendToLog(String.format(
                    "%d messages logged under txn%03d-%s",
                    m_txnMessageCounter, m_txnNumber, transactionName
                ));
            } else {
                appendToLog(String.format(
                    "No messages logged under txn%03d-%s",
                    m_txnMessageCounter, m_txnNumber, transactionName
                ));
            }
            m_transactionLogger.detachAppender(transactionAppender);
            transactionAppender.setFile("/dev/null");
            m_transactionLogger.addAppender(transactionAppender);
            MDC.put("loggerFileName", "/dev/null");
        }
    }
}
