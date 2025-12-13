package net.heretical_camelid.maneline.desktop_app;

import net.heretical_camelid.maneline.lib.DefaultLoggingAgent;
import net.heretical_camelid.maneline.lib.interfaces.ILoggingAgent;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Locale;
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

    Process m_recordProcess;
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
        m_recordProcess = null;
    }

    public String findPCM(String productName, StringBuilder userMessages) {
        if(m_recordProcess!=null) {
            userMessages.append("A recording is already in progress");
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean retval;
        try {
            ProcessBuilder captureDeviceListPB = new ProcessBuilder("arecord", "-l");
            Process proc = captureDeviceListPB.start();
            assert proc.waitFor(10, TimeUnit.SECONDS) == true:
                "Listing audio capture devices: timed out"
            ;
            String errorMessages = new String(
                proc.getErrorStream().readAllBytes(), Charset.defaultCharset()
            );
            String outputMessages = new String(
                proc.getInputStream().readAllBytes(), Charset.defaultCharset()
            );
            if(!outputMessages.isEmpty()) {
                userMessages.append(outputMessages);
                if(outputMessages.lastIndexOf("\n")!=outputMessages.length()-1) {
                    userMessages.append("\n");
                }
            }
            assert errorMessages.isEmpty(): String.format(
                "Listing audio capture devices: exit code %d, error message(s):\n%s",
                proc.exitValue(), errorMessages
            );
            assert proc.exitValue() == 0:
                String.format("Listing audio capture devices: unexpected exit code %d");
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
            m_loggingAgent.logException(e, userMessages);
            return null;
        }
    }

    public boolean beginRecording(String deviceName, String recordedFilename, StringBuilder userMessagesSB) {
        if(m_recordProcess!=null) {
            userMessagesSB.append("A recording is already in progress");
            return false;
        }
        ProcessBuilder recordPB = new ProcessBuilder(
            "ffmpeg", "-f", "alsa", "-i", deviceName, "-acodec", "flac", recordedFilename
        );
        try {
            m_recordProcess = recordPB.start();
            return true;
        }
        catch (IOException e) {
            m_loggingAgent.logException(e, userMessagesSB);
            return false;
        }
    }

    private boolean endRecording(StringBuilder userMessagesSB) {
        if(m_recordProcess==null) {
            userMessagesSB.append("A recording is already in progress");
            return false;
        }
        m_recordProcess.destroy();
        return true;
    }
    // static main method - can do some minimal tests of the class using
    // scripts/run_class_main.sh
    // As the class depends on USB connectivity to a Mustang device,
    // the tests need to be manual.
    // TODO: Abstract the hardware dependency into a mockable wrapper
    //       to enable automated unit tests.

    // Suggested manual tests:
    //
    // condition: LT40S turned on and connected via USB
    // cmdline: scripts/run_class_main.sh desktop_app.AudioRecorder "Mustang LT40S"
    // expected: Exception "... No candidate audio capture devices found"
    // reason: Name in cmdline is missing space between 'LT' and '40S'
    //
    // condition: LT40S turned on connected via USB
    // cmdline: scripts/run_class_main.sh desktop_app.AudioRecorder "Mustang LT 40S"
    // expected: Detection success "... Using audio capture device hw:CARD=M40S,DEV=0"
    //           No file recorded
    // reason: Name in cmdline is correct
    //
    // condition: LT40S turned off and/or not connected via USB
    // cmdline: scripts/run_class_main.sh desktop_app.AudioRecorder "Mustang LT 40S"
    // expected: Exception "... No candidate audio capture devices found"
    // reason: Device not detected because disconnected or turned off
    //
    // condition: LT40S turned on and connected via USB
    // cmdline: scripts/run_class_main.sh desktop_app.AudioRecorder "Mustang LT40S" test1 2
    // expected: Detection success "... Using audio capture device hw:CARD=M40S,DEV=0"
    //           run exits after 2 minutes
    //           file test1.wav recorded and is 2 minutes long
    // reason: Nominal recording allowed to run to limit

    // initial condition: LT40S turned on and connected via USB
    // cmdline: scripts/run_class_main.sh desktop_app.AudioRecorder "Mustang LT40S" test2.wav
    // manual action: ctrl-C in console after ~3 minutes
    // expected: Detection success "... Using audio capture device hw:CARD=M40S,DEV=0"
    //           run exits after ctrl-C
    //           file test2.wav recorded and is ~2 minutes long
    // reason: Nominal recording ended gracefully by console signal

    // initial condition: LT40S turned on and connected via USB
    // cmdline: scripts/run_class_main.sh desktop_app.AudioRecorder "Mustang LT40S" test3.wav
    // manual action: disconnect USB beteen computer and Mustang after ~4 minutes
    // expected: Detection success "... Using audio capture device hw:CARD=M40S,DEV=0"
    //           run exits after ctrl-C
    //           file test3.wav recorded and is ~4 minutes long
    // reason: Nominal recording ended by hardware exception

    public static void main(String[] args) {
        AudioRecorder ar = new AudioRecorder("_work", null);
        StringBuilder findSB = new StringBuilder();
        if(args.length==0 || args[0].equals("--usage")) {
            System.out.println(String.join("\n", new String[]{
                "scripts/run_class_main.sh desktop_app.AudioRecorder { <device_name> { <recording_basename> { <limit_mins> } } }",
                "+ <device_name> is the name of the device to be used for audio capture,",
                "  if not present, the program will emit the current usage message.",
                "+ <recording_basename> is the basename of a file which will receive the recording.",
                "  The file will be in .flac form, with 48000 PCM samples per second, 2 channels,",
                "  sample values will be signed 16 bit integers, the .flac extension will be added ",
                "  to the basename supplied unless that suffix is already present.",
                "  if not present, the program will attempt to detect the device identified by ",
                "  <device_name> but will not attempt a recording.",
                "+ <limit_mins> is a numeric limit on the number of minutes the recording will run for",
                "  unless cancelled using ctrl-C.",
                "  if not present, the recording will run for a default length of 60 minutes or until",
                "  cancelled using ctrl-C."
            }));
            System.exit(1);
        }

        // If we get to here there is at least one parameter, which will be the
        // audio capture device name.
        String acd = ar.findPCM(args[0],findSB);
        System.out.println(String.format("findPCM returned %s",acd));
        System.out.println(String.format("findPCM user messages: \n%s\n", findSB.toString()));
        if(acd==null) {
            System.err.println("No audio capture device found for device_name '%s'");
            System.exit(2);
        }
        if(args.length==1) {
            // No more parameters => no attempt to record
            System.exit(3);
        }

        final String recording_fname;
        if(!args[1].toLowerCase(Locale.ROOT).endsWith(".flac")) {
            recording_fname = args[1] + ".flac";
        } else {
            recording_fname = args[1];
        }
        int limit_mins = MAX_RECORD_MINUTES;
        if(args.length==3) {
            try {
                limit_mins = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println(String.format(
                    "Could not convert '%s' to integer for parameter limit_mins"
                ));
                System.exit(4);
            }
        }

        // If we get to this point, we have:
        // + an audio capture device,
        // + a target filename and
        // + a limit on duration
        StringBuilder recordSB = new StringBuilder();
        ar.beginRecording(acd, recording_fname, recordSB);
        try {
            Thread.sleep(limit_mins * 60 * 1000);
            recordSB.append(String.format("Recording ended due to limit"));
        } catch (InterruptedException e) {
            recordSB.append(String.format("Recording ended InterruptedException"));
        }
        ar.endRecording(recordSB);

    }


}
