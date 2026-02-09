package net.heretical_camelid.maneline.desktop_app;


import java.io.IOException;
import java.util.logging.FileHandler;

public class WebModeLogAppender extends FileHandler {
    private WebModeLogAppender(String filename) throws IOException {
        super(filename);
        System.out.println("wmla.ctor");
    }
}
