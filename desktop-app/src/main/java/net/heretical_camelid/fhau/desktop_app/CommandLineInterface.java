package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.interfaces.ILoggingAgent;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.lang.Package.getPackage;

public class CommandLineInterface implements ILoggingAgent {
    static void doInteractive(DesktopUsbAmpProvider provider) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        boolean continueAcceptingCommands=true;
        while(continueAcceptingCommands) {
            try {
                System.out.println("Command? ");
                String line=br.readLine();
                String[] lineWords = line.split(" ");
                if(lineWords[0].equals("exit")) {
                    System.out.println("Exit requested");
                    continueAcceptingCommands=false;
                } else if(lineWords[0].equals("preset")) {
                        int slotIndex = Integer.parseInt(lineWords[1]);
                        provider.switchPreset(slotIndex);
                } else {
                    System.out.println("Failed to parse line: "+line);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }


        }
    }

    @Override
    public void clearLog() {

    }

    @Override
    public void setLevel(int loggingLevel) {

    }

    @Override
    public void appendToLog(int loggingLevel, String messageToAppend) {
        System.out.println(messageToAppend);
    }


    static boolean s_argParamShowDisclaimer = true;
    static boolean s_argParamInteractive = false;
    static String s_argParamOutput = null;
    static final String DISCLAIMER_ACCEPTANCE_RECORD_FILENAME = ".fhau_disclaimer_accepted_until";
    static final int DISCLAIMER_ACCEPTANCE_DURATION_DAYS = 30;

    static public void main(String[] args)  {
        ArrayList<String> argsAL = new ArrayList<>(List.of(args));
        showCopyrightAndNoWarranty();
        doDisclaimerAcceptedCheck();
        if(s_argParamShowDisclaimer==true) {
            showDisclaimerAndPromptForAcceptance();
            System.exit(0);
        }

        String outputPath = null;
        boolean doInteractive = false;
        if(argsAL.size()>0) {
            outputPath = argsAL.get(0);
            System.out.println("Output will be generated to " + outputPath);
        }
        DesktopUsbAmpProvider provider = new DesktopUsbAmpProvider(outputPath);
        provider.startProvider();
        CommandLineInterface cli = new CommandLineInterface();
        if(doInteractive) {
            cli.doInteractive(provider);
        }
        provider.stopProvider();
    }

    static void showCopyrightAndNoWarranty() {
        HashMap<String,String> substitutions = new HashMap<>();
        substitutions.put(
            "%NO_WARRANTY_DETAILS%",
            String.join("\n",
                "Run the program with the argument --disclaimer to " +
                    "see the warranty disclaimer in full."
            )
        );
        substitutions.put(
            "%VERSION%",
            CommandLineInterface.class.getPackage().getImplementationVersion()
        );
        substitutions.put(
            "%APP_FORMAT%",
            "Command Line Interface (CLI) application"
        );
        substitutions.put(
            "%COPYING_DETAILS%",
            String.join(
                "\n",
                "The source code of FHAU is licensed under the GNU Public License version 2 or, at your ",
                "option, any later version of the GPL, and is available from: ",
                "https://github.com/tim-littlefair/feral-horse-amp-utils.",
                "",
                "The executable forms of FHAU depend upon and include copies of libraries ",
                "by other authors published under a range of open source licenses.",
                ""
            )
        );
        showMessageWithSubstitutions(
            "/assets/copyright_and_warranty.txt",
            substitutions
        );
    }


    static void doDisclaimerAcceptedCheck() {
        try {
            InputStream disclaimerAcceptedUntilFIS = new FileInputStream(
                DISCLAIMER_ACCEPTANCE_RECORD_FILENAME
            );
            LocalDate disclaimerAcceptedUntilDate = LocalDate.parse(
                new String(
                    disclaimerAcceptedUntilFIS.readAllBytes(),
                    Charset.defaultCharset()
                ),
                DateTimeFormatter.BASIC_ISO_DATE
            );
            if(LocalDate.now().compareTo(disclaimerAcceptedUntilDate)>0) {
                // The disclaimer has not been displayed and accepted
                // recently, so show it again
                s_argParamShowDisclaimer=true;
            } else if(
                LocalDate.now().plusDays(
                    DISCLAIMER_ACCEPTANCE_DURATION_DAYS
                ).compareTo(disclaimerAcceptedUntilDate)<0
            ) {
                // The program will ignore any attempt to make
                // acceptance last more than DISCLAIMER_ACCEPTANCE_DURATION_DAYS
                // by manually editing DISCLAIMER_ACCEPTANCE_RECORD_FILENAME
                s_argParamShowDisclaimer=true;
            } else {
                // The disclaimer has been displayed and accepted
                // recently
                s_argParamShowDisclaimer=false;
            }
        } catch (FileNotFoundException e) {
            // Expected behaviour on first run or if disclaimer is displayed
            // and not accepted.
            s_argParamShowDisclaimer=true;
        } catch (DateTimeParseException e) {
            System.err.println("Parse error checking for disclaimer acceptance -");
            System.err.println("Disclaimer will be displayed and must be accepted again");
            s_argParamShowDisclaimer=true;
        } catch (IOException e) {
            // File exists but there is a permission error or other runtime problem
            // Not sure how to handle this so let it propagate to the process
            throw new RuntimeException(e);
        }
    }

    static  ArrayList<String> inspectArgs(ArrayList<String> argsAL) {
        // The return value will contain any arguments which have not been
        // inspected and processed fully
        ArrayList<String> retval = new ArrayList<>();
        for(int i=0; i<argsAL.size(); ++i) {
            String arg=argsAL.get(i);
            if(arg.equals("--interactive")) {
                s_argParamInteractive = true;
            } else if(arg.equals("--disclaimer")) {
                s_argParamShowDisclaimer = true;
            } else if(arg.startsWith("--output=")) {
                s_argParamOutput = arg.replace("--output=","");
            } else {
                retval.add(arg);
            }
        }
        return retval;
    }

    private static void showDisclaimerAndPromptForAcceptance() {
        HashMap<String,String> substitutions = new HashMap<>();
        showMessageWithSubstitutions(
            "/assets/warranty_disclaimer.txt",
            substitutions
        );
    }

    static void showMessageWithSubstitutions(
        String messageFilename,
        HashMap<String,String> substitutions
    ) {
        try {
            String substitutedMessage = new String(
                CommandLineInterface.class.getResourceAsStream(
                    messageFilename
                ).readAllBytes(),
                Charset.defaultCharset()
            );
            if (substitutions != null) {
                for (String k : substitutions.keySet()) {
                    substitutedMessage = substitutedMessage.replace(
                        k, substitutions.get(k)
                    );
                }
            }
            System.out.println(substitutedMessage);
        } catch (IOException e) {
            System.err.println(
                "CommandLineInterface.showCopyrightAndNoWarranty(): failed to read asset file"
            );
            System.exit(1);
        }
    }
}