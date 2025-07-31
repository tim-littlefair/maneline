package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.FhauLibException;
import net.heretical_camelid.fhau.lib.interfaces.ILoggingAgent;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class CommandLineInterface {
    // Integer constants with names prefixed FHAU_STATUS_ are
    // used as the OS exit status

    // Values 50-59 are reserved for conditions raised within package n.h_c.f.lib
    // and are defined in FhauLibException.java

    // Values 90-99 are reserved for the desktop app and are defined here
    private static final int FHAU_STATUS_UNHANDLED_PARAMETERS = 91;
    private static final int FHAU_STATUS_INTERACTIVE_INPUT_FAILURE = 92;
    private static final int FHAU_STATUS_DISCLAIMER_ACCEPT_FILE_PERMISSION_ERROR = 93;

    private static PrintStream s_cliLogStream = null;
    private static boolean s_webMode = false;

    static void doInteractive(DesktopUsbAmpProvider provider) {
        boolean continueAcceptingCommands=true;
        Scanner commandScanner = new Scanner(System.in);
        System.out.println("Command? ");
        while(continueAcceptingCommands && commandScanner.hasNextLine()) {
            final String line = commandScanner.nextLine();;
            try {
                if(line.length()==0) {
                    continue;
                }
                String[] lineWords = line.split(" ");
                if(lineWords[0].equals("exit") || lineWords[0].equals("quit")) {
                    System.out.println("Exit requested");
                    continueAcceptingCommands=false;
                } else if(lineWords[0].equals("preset")) {
                    int slotIndex = Integer.parseInt(lineWords[1]);
                    provider.switchPreset(slotIndex);
                } else if(lineWords[0].equals("help")) {
                    showInteractiveHelp();
                } else {
                    System.out.println("Failed to parse line: "+line);
                }
            }
            catch (NumberFormatException e) {
                System.out.println("Failed to parse expected integer in command line: " + line);
            }
            System.out.println("Command? ");
        }
    }

    static boolean s_argParamForceDisclaimer = false;
    static boolean s_argParamNoDisclaimer = false;
    static boolean s_argParamInteractive = false;
    static String s_argParamOutputDir = null;
    static final String DISCLAIMER_ACCEPTANCE_RECORD_FILENAME = ".fhau_disclaimer_accepted_until";
    static final int DISCLAIMER_ACCEPTANCE_DURATION_DAYS = 30;

    static public void main(String[] args)  {
        try {
            ArrayList<String> argsAL = inspectArgs(new ArrayList<>(List.of(args)));
            showCopyrightAndNoWarranty();
            boolean shouldShowDisclaimer = doDisclaimerAcceptedCheck(s_argParamForceDisclaimer);
            if (shouldShowDisclaimer == true) {
                showDisclaimerAndPromptForAcceptance();
                System.exit(0);
            }

            if (argsAL.size() > 0) {
                System.err.println(
                    "The following parameter(s) were not recognized: " + String.join(", ", argsAL)
                );
                // TODO:
                //  https://github.com/tim-littlefair/feral-horse-amp-utils/issues/10
                //  Display usage message
                System.exit(FHAU_STATUS_UNHANDLED_PARAMETERS);
            }

            DesktopUsbAmpProvider provider = new DesktopUsbAmpProvider(s_webMode, s_argParamOutputDir);
            provider.startProvider();
            CommandLineInterface cli = new CommandLineInterface();
            if (s_argParamInteractive) {
                cli.doInteractive(provider);
            }
            provider.stopProvider();
        }
        catch(FhauLibException e) {
            e.printStackTrace();
            System.exit(e.getExitStatus());
        }
    }

    static void showUsage() {
        String cmdLine = ProcessHandle.current().info().commandLine().get();
        if(cmdLine!=null) {
            // For the usage message we want to see the parts of the command line
            // which invoked the JVM and the name of the .jar file, but nothing else
            // The command line probably includes '--usage' or '--help'
            // and may include other options.
            int dotJarOffset = cmdLine.toLowerCase().indexOf(".jar");
            if (dotJarOffset != -1) {
                cmdLine = cmdLine.substring(0, dotJarOffset + ".jar".length());
            } else {
                cmdLine = null;
            }
        }

        if(cmdLine==null) {
            // Exact command line not available - take a stab at it.
            cmdLine = "java -jar fhauDesktopCLI.jar";
        }
        HashMap<String,String> substitutions = new HashMap<>();
        substitutions.put("%PROG%",cmdLine);
        showMessageWithSubstitutions(
            "/assets/dtcli_usage.txt",
            substitutions
        );
    }

    private static void showInteractiveHelp() {
        showMessageWithSubstitutions(
            "/assets/dtcli_interactive_help.txt",
            null
        );
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


    static boolean doDisclaimerAcceptedCheck(boolean forceDisclaimer) {
        if(s_argParamNoDisclaimer==true) {
            // Disclaimer has been implicitly accepted
            // for this run only by the specification
            // of argument --no-disclaimer on the command line
            return false;
        }
        boolean shouldDisplayDisclaimer = true;
        try {
            File disclaimerAcceptedFile = new File(
                DISCLAIMER_ACCEPTANCE_RECORD_FILENAME
            );
            if(forceDisclaimer) {
                // If the acceptance record exists, delete it,
                // then continue
                if(!disclaimerAcceptedFile.exists()) {
                    // The file does not exist, so the disclaimer will
                    // be displayed anyway
                    return true;
                } else if(disclaimerAcceptedFile.delete()) {
                    // The file existed but has been deleted, so the
                    // disclaimer will be displayed until the user accepts it
                    return true;
                } else {
                    System.out.println(
                        "Unable to delete file " +
                         DISCLAIMER_ACCEPTANCE_RECORD_FILENAME
                    );
                    System.out.println(
                        "Delete this file or make it deletable to enable re-display and \n" +
                        "re-acceptance of disclaimer"
                    );
                    System.exit(FHAU_STATUS_DISCLAIMER_ACCEPT_FILE_PERMISSION_ERROR);
                }
            } else {
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
                disclaimerAcceptedUntilFIS.close();
                if (LocalDate.now().compareTo(disclaimerAcceptedUntilDate) > 0) {
                    // The disclaimer has not been displayed and accepted
                    // recently, so show it again
                    return true;
                } else if (
                    LocalDate.now().plusDays(
                        DISCLAIMER_ACCEPTANCE_DURATION_DAYS
                    ).compareTo(disclaimerAcceptedUntilDate) < 0
                ) {
                    // The program will ignore any attempt to make
                    // acceptance last more than DISCLAIMER_ACCEPTANCE_DURATION_DAYS
                    // by manually editing DISCLAIMER_ACCEPTANCE_RECORD_FILENAME
                    return true;
                } else {
                    // The disclaimer has been displayed and accepted
                    // recently
                    return false;
                }
            }
        } catch (FileNotFoundException e) {
            // Expected behaviour on first run or if disclaimer is displayed
            // and not accepted.
            return true;
        } catch (DateTimeParseException e) {
            System.err.println("Parse error checking for disclaimer acceptance -");
            System.err.println("Disclaimer will be displayed and must be accepted again");
            return true;
        } catch (IOException e) {
            System.out.println(
                "Unable to process file " +
                    DISCLAIMER_ACCEPTANCE_RECORD_FILENAME
            );
            System.out.println(
                "Delete this file or make it readable and writeable to enable re-display and \n" +
                    "re-acceptance of disclaimer"
            );
            System.exit(FHAU_STATUS_DISCLAIMER_ACCEPT_FILE_PERMISSION_ERROR);
        }
        // Is this reachable?
        return true;
    }

    static  ArrayList<String> inspectArgs(ArrayList<String> argsAL) {
        // The return value will contain any arguments which have not been
        // inspected and processed fully
        ArrayList<String> retval = new ArrayList<>();
        for(int i=0; i<argsAL.size(); ++i) {
            String arg=argsAL.get(i);
            if(arg.equals("--help") || arg.equals("--usage")) {
                showUsage();
                System.exit(0);
            } else if(arg.equals("--interactive")) {
                s_argParamInteractive = true;
            } else if(arg.equals("--disclaimer")) {
                s_argParamForceDisclaimer = true;
                s_argParamNoDisclaimer = false;
            } else if(arg.equals("--no-disclaimer")) {
                s_argParamNoDisclaimer = true;
                s_argParamForceDisclaimer = false;
            } else if(arg.startsWith("--web=")) {
                assert s_argParamOutputDir == null;
                s_webMode = true;
                s_argParamNoDisclaimer = true;
                s_argParamOutputDir = arg.replace("--web=","");
                s_argParamInteractive = true;
            } else if(arg.startsWith("--output=")) {
                assert s_argParamOutputDir == null;
                s_argParamOutputDir = arg.replace("--output=","");
            } else {
                retval.add(arg);
            }
        }

        return retval;
    }

    private static void showDisclaimerAndPromptForAcceptance() {
        HashMap<String,String> substitutions = new HashMap<>();
        substitutions.put(
                "%HOW_TO_DISABLE_DISCLAIMER%",
                String.join(
                    "\n",
                    "Having read the disclaimer above, do you accept the risks ",
                    "of running this software?",
                    "Type 'yes' to accept, anything else to decline."
                )
        );
        showMessageWithSubstitutions(
            "/assets/warranty_disclaimer.txt",
            substitutions
        );
        try {
            String inputLine = null;
            inputLine = new BufferedReader(
                new InputStreamReader(System.in)
            ).readLine();
            if(inputLine.contains("yes")) {
                String nextDisclaimerDisplayDate = LocalDate.now().plusDays(
                    DISCLAIMER_ACCEPTANCE_DURATION_DAYS
                ).format(DateTimeFormatter.BASIC_ISO_DATE);
                FileOutputStream disclaimerAcceptedUntilFOS = new FileOutputStream(
                    DISCLAIMER_ACCEPTANCE_RECORD_FILENAME
                );
                disclaimerAcceptedUntilFOS.write(nextDisclaimerDisplayDate.getBytes());
                disclaimerAcceptedUntilFOS.close();
                System.out.println(
                    String.join("\n",
                        "You have accepted the disclaimer, please run this program ",
                        "again to connect to your FMIC device.",
                        "The disclaimer will need to be accepted again after " + nextDisclaimerDisplayDate,
                        ""
                    )
                );
            } else {
                System.out.println(
                    String.join("\n",
                        "You have not accepted the disclaimer.  This program will ",
                        "not attempt to connect to your FMIC device until you do.",
                        ""
                    )
                );
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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