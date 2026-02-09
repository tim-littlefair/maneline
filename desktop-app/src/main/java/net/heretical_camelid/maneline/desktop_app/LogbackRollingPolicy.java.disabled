// package net.heretical_camelid.fhau.desktop_app;
package net.heretical_camelid.maneline.desktop_app;

import java.io.File;
import java.io.StringBufferInputStream;

import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingPolicyBase;
import ch.qos.logback.core.rolling.RolloverFailure;
import ch.qos.logback.core.rolling.TriggeringPolicy;

/**
 * For the FHAU web capability, we are using the SL4J
 * framework to capture a single JSON file per log
 * message.
 * The setFilenamePattern static method on this class
 * allows us to group messages into directories by
 * session, and within sessions into transactions by
 * filename prefix.
 */

/*
    static String fileNamePatternStr = "../before-session.log";
    static private int txnNumber = 0;
    static private int txnLineNumber = 0;
    static private int txnRolloverNumber = 0;
    static private int txnFileLineLimit = 1000; // ?TODO?: static set accessor?

    static private LogbackRollingPolicy s_instance = null;

    static private void pl(String s) {
        // /*
        System.out.println(s);
        // */ /*
    }
    static public void setFilenamePattern(String fnpStr) {
        if(fnpStr==null) {
            fnpStr = "/dev/null";
        } else {
            ++txnNumber;
        }
        pl(">setFilenamePattern " + fileNamePatternStr + "->" + fnpStr);
        assert s_instance != null;
        fileNamePatternStr = fnpStr;
        txnRolloverNumber = 0;
        pl("<setFilenamePattern=" + fnpStr);
        s_instance.rollover();
    }

    static public String describeCurrentLogFile() {
        assert s_instance != null;
        final String retval;
        if(txnLineNumber>0) {
            retval = String.format(
                "Log file %s contains %d events",
                s_instance.parentAppender.getFile(), txnLineNumber
            );
        } else {
            retval = "Nothing logged for transaction " + s_instance.parentAppender.getFile();
        }
        return retval;
    }

    FileAppender<?> parentAppender;


    public LogbackRollingPolicy() {
        super();
        pl(">LogbackRollingPolicy");
        assert s_instance == null;
        s_instance = this;
        pl("<LogbackRollingPolicy");
    }

    /**
     * This class requires access to the appender object
     * so that it can set the filename
     * @param appender
     * /
    @Override
    public void setParent(FileAppender<?> appender) {
        pl(">setParent");
        super.setParent(appender);
        this.parentAppender = appender;
        pl("<setParent");
    }

    @Override
    public void rollover() throws RolloverFailure {
        pl(">rollover from " + getActiveFileName());
        ++txnRolloverNumber;
        String nextLogPath = String.format(fileNamePatternStr, txnNumber, txnRolloverNumber);
        parentAppender.setFile(nextLogPath);
        pl("<rollover to " + getActiveFileName());
    }

    @Override
    public String getActiveFileName() {
        return getParentsRawFileProperty();
    }
*/


public class LogbackRollingPolicy<E>
    extends RollingPolicyBase
    implements TriggeringPolicy<E> {

    boolean m_triggerOnNextEvent;
    String m_filenamePatternStr;
    private int m_txnNumber;
    private int m_txnEvents;

    public LogbackRollingPolicy() {
        m_txnNumber = 0;
        m_txnEvents = 0;
        m_filenamePatternStr = null;
        m_triggerOnNextEvent = false;
    }

    public void setFilenamePattern(String filenamePatternStr) {
        m_filenamePatternStr = filenamePatternStr;
        if(filenamePatternStr.contains("%")) {
            m_txnEvents = 0;
            ++m_txnNumber;
        }
        m_triggerOnNextEvent = true;
    }

    @Override 
    public String getActiveFileName() {
        return String.format(m_filenamePatternStr,m_txnNumber++);
    }

    @Override
    public boolean isTriggeringEvent(File activeFile, final E event) {
        ++m_txnEvents;
        if(m_triggerOnNextEvent) {
            m_triggerOnNextEvent = false;
            return true;
        }
        return false;
    }

    @Override
    public void rollover() {

    }
}
