package net.heretical_camelid.maneline.desktop_app;

import net.heretical_camelid.maneline.lib.interfaces.LoggingAgentBase;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;

import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.MDC;
import org.slf4j.Logger;
//import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

class WebModeLogUtility {
    final Logger m_logger;
    final FileAppender m_appender;
    WebModeLogUtility(String loggerNamePrefix) {
        m_logger = LoggerFactory.getLogger(loggerNamePrefix+"_LOGGER");
        m_appender = (FileAppender)((ch.qos.logback.classic.Logger) m_logger).getAppender(
            loggerNamePrefix+"_APPENDER"
        );
    }
}

public class WebModeLoggingAgent extends LoggingAgentBase {
    private static final String SESSION_INFO_LOG_FILE_NAME = "session-info.log";
    private static final String SESSION_DEBUG_LOG_FILE_NAME = "session-debug.log";
    private static final WebModeLogUtility m_sessionLU = new WebModeLogUtility("SESSION");
    private static final WebModeLogUtility m_transactionLU = new WebModeLogUtility("TRANSACTION");

    static WebModeLoggingAgent s_instance = null;

    private int m_txnNumber = 0;
    private int m_txnMessageCounter = 0;

    static void setSessionNameStatic(String sessionName) {
        assert s_instance != null;
        s_instance.setSessionName(sessionName);
        new File(sessionName + "/txns").mkdir();

        m_sessionLU.m_appender.stop();
        m_sessionLU.m_appender.setFile(String.format(
            "%s/%s",sessionName,SESSION_INFO_LOG_FILE_NAME
        ));
        m_sessionLU.m_appender.start();

        // We don't expect debug events to be generated
        // outside transactions, but in case a coding error
        // causes this to happen we define a file to 
        // catch the events.
        m_transactionLU.m_appender.stop();
        m_transactionLU.m_appender.setFile(String.format(
            "%s/",sessionName,SESSION_DEBUG_LOG_FILE_NAME
        ));
        m_transactionLU.m_appender.start();
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
        LoggingEventBuilder leb;
        if(getTransactionName() != null) {
            leb = m_transactionLU.m_logger.atDebug();
            ++m_txnMessageCounter;
        } else {
            leb = m_sessionLU.m_logger.atInfo();
        }
        leb = leb.setMessage(messageToAppend);
        if(extraAttributes!=null) {
            for(String k: extraAttributes.keySet()) {
                leb = leb.addKeyValue(k,extraAttributes.get(k));
            }
        }
        leb.log();
    }

    @Override
    public void setSessionName(String sessionName) {
        super.setSessionName(sessionName);
    }

    @Override
    public void setTransactionName(String transactionName) {
        assert getSessionName() != null:
            "Session name must be set before setting transaction name"
        ;
        String priorTransactionName = getTransactionName();
        m_transactionLU.m_appender.stop();
        if(transactionName!=null) {
            assert priorTransactionName == null:
                "Attempted to start a transaction when one was already in progress"
            ;
            m_transactionLU.m_appender.setFile(String.format(
                "%s/txns/txn%03d-%s.log",getSessionName(), m_txnNumber, transactionName
            ));
        } else {
            assert priorTransactionName != null:
                "Attempted to end a transaction when one was not in progress"
            ;
            // We don't expect debug events to be generated
            // outside transactions, but in case a coding error
            // causes this to happen we define a file to 
            // catch the events.
            m_transactionLU.m_appender.setFile(String.format(
                "%s/",getSessionName(),SESSION_DEBUG_LOG_FILE_NAME
            ));
            m_sessionLU.m_logger.info(String.format(
                "%d debug events generated for transaction txn%03d-%s", 
                m_txnMessageCounter, m_txnNumber, priorTransactionName
            ));
        }
        super.setTransactionName(transactionName);
        if(transactionName!=null) {
            ++m_txnNumber;
            m_txnMessageCounter = 0;
        }
        m_transactionLU.m_appender.start();
    }
}
