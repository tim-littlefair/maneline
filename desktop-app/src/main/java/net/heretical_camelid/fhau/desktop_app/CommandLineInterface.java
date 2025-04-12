package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.interfaces.ILoggingAgent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
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

    static void showCopyrightAndNoWarranty() {
        try {
            String copyrightNoWarrantyMessage = new String(
                CommandLineInterface.class.getResourceAsStream(
                    "/assets/copyright_and_warranty.txt"
                ).readAllBytes(),
                Charset.defaultCharset()
            );
            System.out.println(
                copyrightNoWarrantyMessage.replace(
                    "%NO_WARRANTY_DETAILS%",
                    String.join("\n",
              "Run the program with the argument --disclaimer to " +
                        "see the warranty disclaimer in full."
                    )
                ).replace(
                    "%VERSION%",
                    CommandLineInterface.class.getPackage().getImplementationVersion()
                ).replace(
                    "%APP_FORMAT%",
                    "Command Line Interface (CLI) application"
                ).replace(
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
                )
            );
        } catch (IOException e) {
            System.err.println("CommandLineInterface.showCopyrightAndNoWarranty(): failed to read asset file");
            System.exit(1);
        }
    }

    static boolean s_argParamShowDisclaimer = true;
    static boolean s_argParamInteractive = false;
    static String s_argParamOutput = null;

    static  ArrayList<String> handleArgs(ArrayList<String> argsAL) {
        Iterator<ArrayList<String>> argsIter;
        if(argsAL.contains("--interactive")) {
            s_argParamInteractive = true;
            argsAL.remove("--interactive");
        }
        return argsAL;
    }

    static public void main(String[] args)  {
        ArrayList<String> argsAL = new ArrayList<>(List.of(args));
        showCopyrightAndNoWarranty();
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
}