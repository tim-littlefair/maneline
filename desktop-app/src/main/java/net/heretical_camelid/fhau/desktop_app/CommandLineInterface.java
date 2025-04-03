package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.interfaces.ILoggingAgent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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

    static public void main(String[] args)  {
        String outputPath = null;
        ArrayList<String> argsAL = new ArrayList<String>(List.of(args));
        boolean doInteractive = false;
        if(argsAL.contains("--interactive")) {
            doInteractive = true;
            argsAL.remove("--interactive");
        }
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