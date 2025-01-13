package net.heretical_camelid.fhau.lib;

public class PresetRecord {

    public enum PresetState {
        /**
         * The application interested in the preset has requested it
         * from the provider.
         */
        REQUESTED,
        /**
         * The provider is providing details of a preset supported by the
         * amplifier.
         */
        RETURNED,
        /**
         * The application has tentatively accepted the offered preset.
         * This might apply if the provider returns a preset with the right
         * name but different hash from the one desired by the application.
         * The application's UX may require a confirmation from the user
         * before allowing a tentatively accepted preset to be used.
         */
        TENTATIVE,
        /**
         * The application has accepted the offered preset unconditionally.
         */
        ACCEPTED,
        /**
         * The application has rejected the offered preset unconditionally.
         */
        REJECTED,
        /**
         * The provider can indicate that a requested preset is not available.
         */
        NOT_AVAILABLE
    };

    public PresetState m_state = null;
    public String m_name = null;
    public int m_slotNumber = 0;
    public String m_hash = null;

    public PresetRecord(String name, int slotNumber) {
        m_state = PresetState.REQUESTED;
        m_name = name;
        m_slotNumber = slotNumber;
    }

    public static void main(String[] args) {
        System.out.println("TODO: tests for PresetRecord");
    }
}
