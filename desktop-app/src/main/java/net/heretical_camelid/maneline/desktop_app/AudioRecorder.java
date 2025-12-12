package net.heretical_camelid.maneline.desktop_app;

import net.heretical_camelid.maneline.lib.DefaultLoggingAgent;
import net.heretical_camelid.maneline.lib.interfaces.ILoggingAgent;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioRecorder {
    // This class imposes a maximum duration for recordings
    private static final int MAX_RECORD_MINUTES = 60;

    // Before a recording is allowed to start, there will be
    // a check that there is enough space in the filesystem
    // containing the session directory for a maximum
    // duration recording.
    // On the LT40S, the native recording format is:
    // 48000 samples per second x 2 channels,
    // with signed 2 byte/16 bit samples
    private static final int BYTES_PER_RECORD_MINUTE = 48000*2*2*60;
    private static final int MIN_FREE_BYTES_AT_RECORD_START = (
        BYTES_PER_RECORD_MINUTE * MAX_RECORD_MINUTES
    );

    final String m_sessionDirectory;
    final ILoggingAgent m_loggingAgent;

    public AudioRecorder(
        String sessionDirectory,
        ILoggingAgent loggingAgent
    ) {
        m_sessionDirectory = sessionDirectory;
        if(loggingAgent!=null) {
            m_loggingAgent = loggingAgent;
        } else {
            m_loggingAgent = new DefaultLoggingAgent();
        }
    }

    public String findPCM(String productName, StringBuilder userMessages) {
        StringBuilder sb = new StringBuilder();
        boolean retval;
        try {
            ProcessBuilder captureDeviceListPB = new ProcessBuilder("arecord", "-l");
            Process proc = captureDeviceListPB.start();
            assert proc.waitFor(10, TimeUnit.SECONDS) == true:
                "Listing audio capture devices: timed out"
            ;
            assert proc.exitValue() == 0:
                String.format("Listing audio capture devices: unexpected exit code %d");
            String errorMessages = new String(
                proc.getErrorStream().readAllBytes(), Charset.defaultCharset()
            );
            String outputMessages = new String(
                proc.getInputStream().readAllBytes(), Charset.defaultCharset()
            );
            if(!outputMessages.isEmpty()) {
                sb.append(outputMessages);
                if(outputMessages.lastIndexOf("\n")!=outputMessages.length()-1) {
                    sb.append("\n");
                }
            }
            assert errorMessages.isEmpty():
                "Listing audio capture devices: error message(s):\n" + errorMessages
            ;
            Pattern cardLinePattern = Pattern.compile("card (\\d+): (\\w+) \\[([^]]+)\\], device (\\d+)");
            ArrayList<String> deviceCandidates = new ArrayList<>();
            for(String line: outputMessages.split("\n")) {
                Matcher lineMatcher = cardLinePattern.matcher(line);
                if (lineMatcher.find()) {
                    assert lineMatcher.groupCount()==4: String.format(
                        "line '%s' matches %d groups (expected 4)",
                        line, lineMatcher.groupCount()
                    );
                    if(lineMatcher.group(3).equals(productName)) {
                        deviceCandidates.add(String.format(
                            "hw:CARD=%s,DEV=%s",
                            lineMatcher.group(2), lineMatcher.group(4)
                        ));
                    }
                }
            }
            assert deviceCandidates.size()<=1: String.format(
                "Multiple candidate audio capture devices discovered: %s",
                String.join(", ", deviceCandidates)
            );
            assert deviceCandidates.size()==1 : (
               "No candidate audio capture devices found"
            );

            // If we get to here, there is exactly one audio capture device found
            String successMessage = "Using audio capture device " + deviceCandidates.get(0);
            m_loggingAgent.appendToLog(successMessage);
            userMessages.append(successMessage);
            return deviceCandidates.get(0);
        }
        catch (IOException | InterruptedException | AssertionError e) {
            HashMap<String,String> exceptionDetails = new HashMap<String,String>();
            exceptionDetails.put("exceptionType", e.getClass().getCanonicalName());
            exceptionDetails.put("exceptionMessage", e.getMessage());
            for(StackTraceElement ste:e.getStackTrace()) {
                if(ste.getClassName().startsWith("net.heretical_camelid")) {
                    exceptionDetails.put("thrownFrom", String.format(
                        "%s:%d", ste.getFileName(), ste.getLineNumber()
                    ));
                    break;
                }
            }
            m_loggingAgent.appendToLog(
                "Exception caught in AudioRecorder.findPCM(...)",
                exceptionDetails
            );
            userMessages.append(e.getMessage());
            return null;
        }
    }

    // static main method - can do a minimal test of the class using
    // scripts/run_class_main.sh

    // Suggested manual tests:
    //
    // condition: LT40S turned on and connected via USB
    // cmdline: scripts/run_class_main.sh desktop_app.AudioRecorder "Mustang LT40S"
    // expected: Exception "... No candidate audio capture devices found"
    // reason: Name in cmdline is missing space between 'LT' and '40S'
    //
    // condition: LT40S turned on connected via USB
    // cmdline: scripts/run_class_main.sh desktop_app.AudioRecorder "Mustang LT 40S"
    // expected: Success "... Using audio capture device hw:CARD=M40S,DEV=0"
    // reason: Name in cmdline is correct
    //
    // condition: LT40S turned off and/or not connected via USB
    // cmdline: scripts/run_class_main.sh desktop_app.AudioRecorder "Mustang LT 40S"
    // expected: Exception "... No candidate audio capture devices found"
    // reason: Device not detected because disconnected or turned off
    //
    public static void main(String[] args) {
        AudioRecorder ar = new AudioRecorder("_work", null);
        StringBuilder findSB = new StringBuilder();
        String acd = ar.findPCM(args[0],findSB);
        System.out.println(String.format("findPCM returned %s",acd));
        System.out.println(String.format("findPCM user messages: \n%s\n", findSB.toString()));
    }
}
