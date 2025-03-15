package net.heretical_camelid.fhau.desktop_app;

public class CommandLineInterface {
    static public void main(String[] args)  {
        String outputPath = null;
        if(args.length>0) {
            outputPath = args[0];
            System.out.println("Output will be generated to " + outputPath);
        }
        DesktopUsbAmpProvider provider = new DesktopUsbAmpProvider(outputPath);
    }
}