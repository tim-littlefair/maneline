package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.ILoggingAgent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CommandLineInterface implements ILoggingAgent {
    static void doInteractive(DesktopUsbAmpProvider provider) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            try {
                System.out.println("Command? ");
                String line=br.readLine();
                String[] lineWords = line.split(" ");
                if(lineWords[0]=="preset") {
                    int slotIndex = Integer.parseInt(lineWords[2]);
                    provider.switchPreset(slotIndex);
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
        if(args.length>0) {
            outputPath = args[0];
            System.out.println("Output will be generated to " + outputPath);
        }
        DesktopUsbAmpProvider provider = new DesktopUsbAmpProvider(outputPath);
        if(args.length>1 && args[1].equals("--interactive")) {
            CommandLineInterface cli = new CommandLineInterface();
            cli.doInteractive(provider);
        }
    }
}