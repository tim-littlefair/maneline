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
public class LogbackRollingPolicy<E>
    extends RollingPolicyBase
    implements TriggeringPolicy<E> {


    static String fileNamePatternStr = "../before-session.log";
    static private int txnNumber = 1;
    static private int txnLineNumber = 0;
    static private int txnRolloverNumber = 0;
    static private int txnFileLineLimit = 1000; // ?TODO?: static set accessor?

    static private LogbackRollingPolicy s_instance = null;

    static private void pl(String s) {
        /* System.out.println(s); */
    }
    static public void setFilenamePattern(String fnpStr) {
        pl(">setFilenamePattern " + fileNamePatternStr + "->" + fnpStr);
        assert s_instance != null;
        fileNamePatternStr = fnpStr;
        txnRolloverNumber = 0;
        pl("<setFilenamePattern=" + fnpStr);
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
     */
    @Override
    public void setParent(FileAppender<?> appender) {
        pl(">setParent");
        super.setParent(appender);
        this.parentAppender = appender;
        pl("<setParent");
    }

    @Override
    public void rollover() throws RolloverFailure {
        pl(">rollover");
        txnRolloverNumber++;
        String nextLogPath = String.format(fileNamePatternStr, txnRolloverNumber);
        parentAppender.setFile(nextLogPath);
        pl("<rollover");
    }

    @Override
    public String getActiveFileName() {
        return getParentsRawFileProperty();
    }

    @Override
    public boolean isTriggeringEvent(File activeFile, final E event) {
        return true;
/*
        pl(">isTriggeringEvent");
        if(txnLineNumber==0) {
            ++txnLineNumber;
            pl("<isTriggeringEvent false");
            return true;
        } else if (txnLineNumber==txnFileLineLimit) {
            txnLineNumber=0;
        } else {
            ++txnLineNumber;
        }
        pl("<isTriggeringEvent false");
        return false;
 */
    }
}
