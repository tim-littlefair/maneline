package net.heretical_camelid.maneline.lib;

public class FhauLibException extends RuntimeException {
    // Integer constants prefixed FHAU_STATUS_LIB_ are suggested exit values
    // to be passed back to the OS in an exception.  If the lib is running within an
    // app on an OS which accepts process exit status, the process will exit
    // with that status.
    // Values 50-59 are reserved for well-understood conditions raised within
    // package n.h_c.f.lib.

    // value 50 is reserved as a placeholder conditions which are unexpected
    // and require investigation
    public static final int FHAU_EXIT_STATUS_LIB_OTHER = 50;

    // values 51-59 are reserved for conditions which are expected and
    // appropriate actions have been described.
    public static final int FHAU_EXIT_STATUS_LIB_DIRECTORY_CREATION_ERROR = 51;
    public static final int FHAU_EXIT_STATUS_LIB_FILE_CREATION_ERROR = 52;
    public static final int FHAU_EXIT_STATUS_LIB_FILE_WRITE_ERROR = 53;


    final int m_exitStatus;
    public FhauLibException(String message, int exitStatus) {
        super(message);
        m_exitStatus = exitStatus;
    }

    public int getExitStatus() {
        return m_exitStatus;
    }
}
