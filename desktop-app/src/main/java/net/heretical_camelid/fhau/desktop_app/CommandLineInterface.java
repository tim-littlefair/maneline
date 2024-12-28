package net.heretical_camelid.fhau.desktop_app;



public class CommandLineInterface {
    static final String[] commandHexStrings = new String[]{
            "35:09:08:00:8a:07:04:08:00:10:00",
            "35:07:08:00:b2:06:02:08:01",
            // Next 60 + 1 commands get
            // the JSON descriptions of the 60 stored
            // and 1 active presets
            // We can't handle these until we have support
            // for multi-frame responses
            //"35:07:08:00:ca:06:02:08:01",
            //"35:07:08:00:ca:06:02:08:02",
            //..
            "35:07:08:00:f2:03:02:08:01",
            "35:07:08:00:d2:06:02:08:01",
            "35:07:08:00:e2:06:02:08:01",
            "35:07:08:00:d2:0c:02:08:01",
            "35:09:08:00:8a:07:04:08:01:10:00",
            /*
            "35:07:08:00:8a:02:02:08:02",
            "35:07:08:00:8a:02:02:08:03",
            "35:07:08:00:8a:02:02:08:04",
            "35:07:08:00:8a:02:02:08:01",
            "35:07:08:00:8a:02:02:08:02",
             */
    };

    static public void main(String[] args)  {
        DesktopUsbAmpProvider provider = new DesktopUsbAmpProvider(System.out);
        StringBuilder logSb;

        logSb = new StringBuilder();

        boolean statusOk = provider.connect(logSb);
        System.out.println(logSb.toString());

        for (String commandHexString : commandHexStrings) {
            if(statusOk!=true) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted sleep!");
            }
            logSb = new StringBuilder();
            byte[] bytesReceived = provider.sendCommandAndReceiveResponse(commandHexString, logSb);
            System.out.println(logSb.toString());
            statusOk = (bytesReceived!=null);
        }
        System.exit(0);
    }
}