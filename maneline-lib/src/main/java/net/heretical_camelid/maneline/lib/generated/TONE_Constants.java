package net.heretical_camelid.maneline.lib.generated;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;


class TONE_DspParameter {

}
class TONE_DspModule {
    final String m_genericName;
    Map<String,String> m_familySpecificNames;
    Map<String,TONE_DspParameter> m_parameters;
    TONE_DspModule(String genericName) {
        m_genericName = genericName;
        m_familySpecificNames = new HashMap<>();
        m_parameters = new HashMap<>();
    }
    TONE_DspModule addNameVariant(String productFamilyName, String moduleNameForFamily) {
        // This function is invoked in generated code, it is easier to
        // generate invocation with a zero length (or maybe in future null)
        // parameter value than to suppress invocation entirely for models
        // which do not support a specific generic-named module
        if(moduleNameForFamily!=null && moduleNameForFamily.isEmpty()) {
            m_familySpecificNames.put(productFamilyName, moduleNameForFamily);
        }
        return this;
    }
}
public class TONE_Constants {

    // The following prefixes and suffixes are considered suitable for
    // removing from the FenderId names of DSP modules defined in Fender
    // TONE JSON.  Removing these makes the module names shorter without
    // losing much meaning, and eliminates naming convention differences
    // (related to DUBS_, ACD_ prefixes and the GT suffix)
    // between similar or identical modules on different model ranges.
    final private static String[] _REMOVABLE_PREFIXES_AND_SUFFIXES = {
        "^DUBS_Mustang", "^DUBS_Fender", "^DUBS_",
        "^ACD_Mustang", "^ACD_Fender", "^ACD_", "GT$",
        "Reverb$", "PitchShifter$"
    };

    static Map<String, TONE_DspModule> _DSP_MODULES = new TreeMap<>();

    static public String fenderIdToGenericModuleName(String familyDspModuleName) {
        String dspModuleName = familyDspModuleName;
        for(String s: _REMOVABLE_PREFIXES_AND_SUFFIXES) {
            dspModuleName = dspModuleName.replaceAll(s,"");
        }
        return dspModuleName;
    }

    static TONE_DspModule register_TONE_DspModule(TONE_DspModule newModule) {
        _DSP_MODULES.put(newModule.m_genericName, newModule);
        return newModule;
    }

    static {

        register_TONE_DspModule(
            new TONE_DspModule("Ac30Tb")
                .addNameVariant("a","b")
                .addNameVariant("c","d")
        );

        /* registerModule generated entries begin */
        register_TONE_DspModule(
            new TONE_DspModule("Ac30Tb") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Ac30Tb")
                .addNameVariant("mustang-ltx","ACD_Ac30TbGT")
                .addNameVariant("mustang-gtx","DUBS_Ac30Tb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Acoustasonic") 
                .addNameVariant("rumble-lt","DUBS_Acoustasonic")
                .addNameVariant("mustang-lt","DUBS_Acoustasonic")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Acoustasonic")
        );
        register_TONE_DspModule(
            new TONE_DspModule("AcousticSim") 
                .addNameVariant("rumble-lt","DUBS_AcousticSim")
                .addNameVariant("mustang-lt","DUBS_AcousticSim")
                .addNameVariant("mustang-ltx","ACD_AcousticSimGT")
                .addNameVariant("mustang-gtx","DUBS_AcousticSim")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Ambient") 
                .addNameVariant("rumble-lt","DUBS_AmbientReverb")
                .addNameVariant("mustang-lt","DUBS_AmbientReverb")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_AmbientReverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Ampeg66B15") 
                .addNameVariant("rumble-lt","DUBS_Ampeg66B15")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Arena") 
                .addNameVariant("rumble-lt","DUBS_ArenaReverb")
                .addNameVariant("mustang-lt","DUBS_ArenaReverb")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_ArenaReverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("BE100") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_BE100")
                .addNameVariant("mustang-ltx","ACD_BE100GT")
                .addNameVariant("mustang-gtx","DUBS_BE100")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Bandmaster57") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Bandmaster57")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Bandmaster57")
        );
        register_TONE_DspModule(
            new TONE_DspModule("BassGraphicEQ7") 
                .addNameVariant("rumble-lt","DUBS_BassGraphicEQ7")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Bassbreaker15") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Bassbreaker15")
                .addNameVariant("mustang-ltx","ACD_Bassbreaker15GT")
                .addNameVariant("mustang-gtx","DUBS_Bassbreaker15")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Bassbreaker15High") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Bassbreaker15High")
                .addNameVariant("mustang-ltx","ACD_Bassbreaker15HighGT")
                .addNameVariant("mustang-gtx","DUBS_Bassbreaker15High")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Bassbreaker15Med") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Bassbreaker15Med")
                .addNameVariant("mustang-ltx","ACD_Bassbreaker15MedGT")
                .addNameVariant("mustang-gtx","DUBS_Bassbreaker15Med")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Bassman300") 
                .addNameVariant("rumble-lt","DUBS_Bassman300")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Bassman59") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Bassman59")
                .addNameVariant("mustang-ltx","ACD_Bassman59GT")
                .addNameVariant("mustang-gtx","DUBS_Bassman59")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Bassman59Bass") 
                .addNameVariant("rumble-lt","DUBS_Bassman59Bass")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("BassmanTV") 
                .addNameVariant("rumble-lt","DUBS_BassmanTV")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("BigFuzz") 
                .addNameVariant("rumble-lt","DUBS_BigFuzz")
                .addNameVariant("mustang-lt","DUBS_BigFuzz")
                .addNameVariant("mustang-ltx","ACD_BigFuzzGT")
                .addNameVariant("mustang-gtx","DUBS_BigFuzz")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Blackbox") 
                .addNameVariant("rumble-lt","DUBS_Blackbox")
                .addNameVariant("mustang-lt","DUBS_Blackbox")
                .addNameVariant("mustang-ltx","ACD_Blackbox")
                .addNameVariant("mustang-gtx","DUBS_Blackbox")
        );
        register_TONE_DspModule(
            new TONE_DspModule("BluesJrIV") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_BluesJrIV")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_BluesJrIV")
        );
        register_TONE_DspModule(
            new TONE_DspModule("BoilerPlateMono") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","ACD_BoilerPlateMono")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("BoilerPlateReverse") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","ACD_BoilerPlateReverse")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("BoilerPlateTapeLite") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","ACD_BoilerPlateTapeLite")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("BrownDeluxe61") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_BrownDeluxe61")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_BrownDeluxe61")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Champ57") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Champ57")
                .addNameVariant("mustang-ltx","ACD_Champ57GT")
                .addNameVariant("mustang-gtx","DUBS_Champ57")
        );
        register_TONE_DspModule(
            new TONE_DspModule("ChorusSine") 
                .addNameVariant("rumble-lt","DUBS_ChorusSine")
                .addNameVariant("mustang-lt","DUBS_ChorusSine")
                .addNameVariant("mustang-ltx","ACD_ChorusSine")
                .addNameVariant("mustang-gtx","DUBS_ChorusSine")
        );
        register_TONE_DspModule(
            new TONE_DspModule("ChorusTriangle") 
                .addNameVariant("rumble-lt","DUBS_ChorusTriangle")
                .addNameVariant("mustang-lt","DUBS_ChorusTriangle")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_ChorusTriangle")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Chromatic") 
                .addNameVariant("rumble-lt","DUBS_ChromaticPitchShifter")
                .addNameVariant("mustang-lt","DUBS_ChromaticPitchShifter")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_ChromaticPitchShifter")
        );
        register_TONE_DspModule(
            new TONE_DspModule("ChromeGate") 
                .addNameVariant("rumble-lt","DUBS_ChromeGate")
                .addNameVariant("mustang-lt","DUBS_ChromeGate")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_ChromeGate")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Compressor") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Compressor")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Compressor")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Compressor2") 
                .addNameVariant("rumble-lt","DUBS_Compressor2")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("CompressorSimple") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","ACD_CompressorSimple")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("DR103") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_DR103")
                .addNameVariant("mustang-ltx","ACD_DR103GT")
                .addNameVariant("mustang-gtx","DUBS_DR103")
        );
        register_TONE_DspModule(
            new TONE_DspModule("DR103Bass") 
                .addNameVariant("rumble-lt","DUBS_DR103Bass")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Deluxe57") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Deluxe57")
                .addNameVariant("mustang-ltx","ACD_Deluxe57GT")
                .addNameVariant("mustang-gtx","DUBS_Deluxe57")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Deluxe65") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Deluxe65")
                .addNameVariant("mustang-ltx","ACD_Deluxe65GT")
                .addNameVariant("mustang-gtx","DUBS_Deluxe65")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Diatonic") 
                .addNameVariant("rumble-lt","DUBS_DiatonicPitchShifter")
                .addNameVariant("mustang-lt","DUBS_DiatonicPitchShifter")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_DiatonicPitchShifter")
        );
        register_TONE_DspModule(
            new TONE_DspModule("DualShowman") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_DualShowman")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_DualShowman")
        );
        register_TONE_DspModule(
            new TONE_DspModule("DualShowmanBass") 
                .addNameVariant("rumble-lt","DUBS_DualShowmanBass")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("DuckingDelay") 
                .addNameVariant("rumble-lt","DUBS_DuckingDelay")
                .addNameVariant("mustang-lt","DUBS_DuckingDelay")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_DuckingDelay")
        );
        register_TONE_DspModule(
            new TONE_DspModule("EcFilter") 
                .addNameVariant("rumble-lt","DUBS_EcFilter")
                .addNameVariant("mustang-lt","DUBS_EcFilter")
                .addNameVariant("mustang-ltx","ACD_EcFilter")
                .addNameVariant("mustang-gtx","DUBS_EcFilter")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Echoplex") 
                .addNameVariant("rumble-lt","DUBS_Echoplex")
                .addNameVariant("mustang-lt","DUBS_Echoplex")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Echoplex")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Evh3") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Evh3")
                .addNameVariant("mustang-ltx","ACD_Evh3GT")
                .addNameVariant("mustang-gtx","DUBS_Evh3")
        );
        register_TONE_DspModule(
            new TONE_DspModule("EvhFlanger") 
                .addNameVariant("rumble-lt","DUBS_EvhFlanger")
                .addNameVariant("mustang-lt","DUBS_EvhFlanger")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_EvhFlanger")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Excelsior") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Excelsior")
                .addNameVariant("mustang-ltx","ACD_ExcelsiorGT")
                .addNameVariant("mustang-gtx","DUBS_Excelsior")
        );
        register_TONE_DspModule(
            new TONE_DspModule("FiveBandEq1") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_MustangFiveBandEq1")
                .addNameVariant("mustang-ltx","ACD_MustangFiveBandEq1")
                .addNameVariant("mustang-gtx","DUBS_MustangFiveBandEq1")
        );
        register_TONE_DspModule(
            new TONE_DspModule("FlangerTriangle") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","ACD_FlangerTriangle")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Fuzz") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Fuzz")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Fuzz")
        );
        register_TONE_DspModule(
            new TONE_DspModule("GK800RB") 
                .addNameVariant("rumble-lt","DUBS_GK800RB")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Ga15") 
                .addNameVariant("rumble-lt","DUBS_Ga15Reverb")
                .addNameVariant("mustang-lt","DUBS_Ga15Reverb")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Ga15Reverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Ga15Rvt") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Ga15Rvt")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Ga15Rvt")
        );
        register_TONE_DspModule(
            new TONE_DspModule("GraphicEQ7Wide") 
                .addNameVariant("rumble-lt","DUBS_GraphicEQ7Wide")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("GreenRussianBmp") 
                .addNameVariant("rumble-lt","DUBS_GreenRussianBmp")
                .addNameVariant("mustang-lt","DUBS_GreenRussianBmp")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_GreenRussianBmp")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Greenbox") 
                .addNameVariant("rumble-lt","DUBS_Greenbox")
                .addNameVariant("mustang-lt","DUBS_Greenbox")
                .addNameVariant("mustang-ltx","ACD_Greenbox")
                .addNameVariant("mustang-gtx","DUBS_Greenbox")
        );
        register_TONE_DspModule(
            new TONE_DspModule("JCChorus") 
                .addNameVariant("rumble-lt","DUBS_JCChorus")
                .addNameVariant("mustang-lt","DUBS_JCChorus")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_JCChorus")
        );
        register_TONE_DspModule(
            new TONE_DspModule("JCClean") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_JCClean")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_JCClean")
        );
        register_TONE_DspModule(
            new TONE_DspModule("JCVib") 
                .addNameVariant("rumble-lt","DUBS_JCVib")
                .addNameVariant("mustang-lt","DUBS_JCVib")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_JCVib")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Jcm800") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Jcm800")
                .addNameVariant("mustang-ltx","ACD_Jcm800GT")
                .addNameVariant("mustang-gtx","DUBS_Jcm800")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Jubilee") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Jubilee")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Jubilee")
        );
        register_TONE_DspModule(
            new TONE_DspModule("JubileeClip") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_JubileeClip")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_JubileeClip")
        );
        register_TONE_DspModule(
            new TONE_DspModule("JubileeLead") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_JubileeLead")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_JubileeLead")
        );
        register_TONE_DspModule(
            new TONE_DspModule("LargeHall") 
                .addNameVariant("rumble-lt","DUBS_LargeHallReverb")
                .addNameVariant("mustang-lt","DUBS_LargeHallReverb")
                .addNameVariant("mustang-ltx","ACD_FenderLargeHall")
                .addNameVariant("mustang-gtx","DUBS_LargeHallReverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("LargeModulatedHall") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","ACD_FenderLargeModulatedHall")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("LargeOverdrive") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","ACD_LargeOverdrive")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("LargePlate") 
                .addNameVariant("rumble-lt","DUBS_LargePlate")
                .addNameVariant("mustang-lt","DUBS_LargePlate")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_LargePlate")
        );
        register_TONE_DspModule(
            new TONE_DspModule("LargeRoom") 
                .addNameVariant("rumble-lt","DUBS_LargeRoomReverb")
                .addNameVariant("mustang-lt","DUBS_LargeRoomReverb")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_LargeRoomReverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("LinearGain") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_LinearGain")
                .addNameVariant("mustang-ltx","ACD_LinearGainGT")
                .addNameVariant("mustang-gtx","DUBS_LinearGain")
        );
        register_TONE_DspModule(
            new TONE_DspModule("MarkIICClassA") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_MarkIICClassA")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_MarkIICClassA")
        );
        register_TONE_DspModule(
            new TONE_DspModule("MarkIICClassAB") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_MarkIICClassAB")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_MarkIICClassAB")
        );
        register_TONE_DspModule(
            new TONE_DspModule("MemoryMan") 
                .addNameVariant("rumble-lt","DUBS_MemoryMan")
                .addNameVariant("mustang-lt","DUBS_MemoryMan")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_MemoryMan")
        );
        register_TONE_DspModule(
            new TONE_DspModule("ModDelay") 
                .addNameVariant("rumble-lt","DUBS_ModDelay")
                .addNameVariant("mustang-lt","DUBS_ModDelay")
                .addNameVariant("mustang-ltx","ACD_ModDelay")
                .addNameVariant("mustang-gtx","DUBS_ModDelay")
        );
        register_TONE_DspModule(
            new TONE_DspModule("ModLargeHall") 
                .addNameVariant("rumble-lt","DUBS_ModLargeHallReverb")
                .addNameVariant("mustang-lt","DUBS_ModLargeHallReverb")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_ModLargeHallReverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("ModSmallHall") 
                .addNameVariant("rumble-lt","DUBS_ModSmallHallReverb")
                .addNameVariant("mustang-lt","DUBS_ModSmallHallReverb")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_ModSmallHallReverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("ModernBassOverdrive") 
                .addNameVariant("rumble-lt","DUBS_ModernBassOverdrive")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("MonoDelay") 
                .addNameVariant("rumble-lt","DUBS_MonoDelay")
                .addNameVariant("mustang-lt","DUBS_MonoDelay")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_MonoDelay")
        );
        register_TONE_DspModule(
            new TONE_DspModule("MonoEchoFilter") 
                .addNameVariant("rumble-lt","DUBS_MonoEchoFilter")
                .addNameVariant("mustang-lt","DUBS_MonoEchoFilter")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_MonoEchoFilter")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Monster") 
                .addNameVariant("rumble-lt","DUBS_Monster")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("MultitapDelay") 
                .addNameVariant("rumble-lt","DUBS_MultitapDelay")
                .addNameVariant("mustang-lt","DUBS_MultitapDelay")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_MultitapDelay")
        );
        register_TONE_DspModule(
            new TONE_DspModule("MythicDrive") 
                .addNameVariant("rumble-lt","DUBS_MythicDrive")
                .addNameVariant("mustang-lt","DUBS_MythicDrive")
                .addNameVariant("mustang-ltx","ACD_MythicDrive")
                .addNameVariant("mustang-gtx","DUBS_MythicDrive")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Octobot") 
                .addNameVariant("rumble-lt","DUBS_Octobot")
                .addNameVariant("mustang-lt","DUBS_Octobot")
                .addNameVariant("mustang-ltx","ACD_Octobot")
                .addNameVariant("mustang-gtx","DUBS_Octobot")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Or120") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Or120")
                .addNameVariant("mustang-ltx","ACD_Or120GT")
                .addNameVariant("mustang-gtx","DUBS_Or120")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Or120Bass") 
                .addNameVariant("rumble-lt","DUBS_Or120Bass")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Orangebox") 
                .addNameVariant("rumble-lt","DUBS_Orangebox")
                .addNameVariant("mustang-lt","DUBS_Orangebox")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Orangebox")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Overdrive") 
                .addNameVariant("rumble-lt","DUBS_Overdrive")
                .addNameVariant("mustang-lt","DUBS_Overdrive")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Overdrive")
        );
        register_TONE_DspModule(
            new TONE_DspModule("PEQ") 
                .addNameVariant("rumble-lt","DUBS_MustangPEQ")
                .addNameVariant("mustang-lt","DUBS_MustangPEQ")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_MustangPEQ")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Phaser") 
                .addNameVariant("rumble-lt","DUBS_Phaser")
                .addNameVariant("mustang-lt","DUBS_Phaser")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Phaser")
        );
        register_TONE_DspModule(
            new TONE_DspModule("PhaserFender") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","ACD_PhaserFender")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("PhaserP90") 
                .addNameVariant("rumble-lt","DUBS_PhaserP90")
                .addNameVariant("mustang-lt","DUBS_PhaserP90")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_PhaserP90")
        );
        register_TONE_DspModule(
            new TONE_DspModule("PingPongDelay") 
                .addNameVariant("rumble-lt","DUBS_PingPongDelay")
                .addNameVariant("mustang-lt","DUBS_PingPongDelay")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_PingPongDelay")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Plexi87") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Plexi87")
                .addNameVariant("mustang-ltx","ACD_Plexi87GT")
                .addNameVariant("mustang-gtx","DUBS_Plexi87")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Plexi87Bass") 
                .addNameVariant("rumble-lt","DUBS_Plexi87Bass")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Princeton65") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Princeton65")
                .addNameVariant("mustang-ltx","ACD_Princeton65GT")
                .addNameVariant("mustang-gtx","DUBS_Princeton65")
        );
        register_TONE_DspModule(
            new TONE_DspModule("RamsHeadBmp") 
                .addNameVariant("rumble-lt","DUBS_RamsHeadBmp")
                .addNameVariant("mustang-lt","DUBS_RamsHeadBmp")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_RamsHeadBmp")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Ranger") 
                .addNameVariant("rumble-lt","DUBS_Ranger")
                .addNameVariant("mustang-lt","DUBS_Ranger")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Ranger")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Rect2") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Rect2")
                .addNameVariant("mustang-ltx","ACD_Rect2GT")
                .addNameVariant("mustang-gtx","DUBS_Rect2")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Redhead") 
                .addNameVariant("rumble-lt","DUBS_Redhead")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("ReverseDelay") 
                .addNameVariant("rumble-lt","DUBS_ReverseDelay")
                .addNameVariant("mustang-lt","DUBS_ReverseDelay")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_ReverseDelay")
        );
        register_TONE_DspModule(
            new TONE_DspModule("RingModulator") 
                .addNameVariant("rumble-lt","DUBS_RingModulator")
                .addNameVariant("mustang-lt","DUBS_RingModulator")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_RingModulator")
        );
        register_TONE_DspModule(
            new TONE_DspModule("RoundFuzz") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_RoundFuzz")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_RoundFuzz")
        );
        register_TONE_DspModule(
            new TONE_DspModule("RumbleV2") 
                .addNameVariant("rumble-lt","DUBS_RumbleV2")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("RumbleV3") 
                .addNameVariant("rumble-lt","DUBS_RumbleV3")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SVT") 
                .addNameVariant("rumble-lt","DUBS_SVT")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SevenBandEq") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_MustangSevenBandEq")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_MustangSevenBandEq")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Shimmer") 
                .addNameVariant("rumble-lt","DUBS_ShimmerReverb")
                .addNameVariant("mustang-lt","DUBS_ShimmerReverb")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_ShimmerReverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Silvertone") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Silvertone")
                .addNameVariant("mustang-ltx","ACD_SilvertoneGT")
                .addNameVariant("mustang-gtx","DUBS_Silvertone")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SimpleCompressor") 
                .addNameVariant("rumble-lt","DUBS_SimpleCompressor")
                .addNameVariant("mustang-lt","DUBS_SimpleCompressor")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_SimpleCompressor")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SineFlanger") 
                .addNameVariant("rumble-lt","DUBS_SineFlanger")
                .addNameVariant("mustang-lt","DUBS_SineFlanger")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_SineFlanger")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SineTremolo") 
                .addNameVariant("rumble-lt","DUBS_SineTremolo")
                .addNameVariant("mustang-lt","DUBS_SineTremolo")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_SineTremolo")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SmallHall") 
                .addNameVariant("rumble-lt","DUBS_SmallHallReverb")
                .addNameVariant("mustang-lt","DUBS_SmallHallReverb")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_SmallHallReverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SmallPlate") 
                .addNameVariant("rumble-lt","DUBS_SmallPlate")
                .addNameVariant("mustang-lt","DUBS_SmallPlate")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_SmallPlate")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SmallRoom") 
                .addNameVariant("rumble-lt","DUBS_SmallRoomReverb")
                .addNameVariant("mustang-lt","DUBS_SmallRoomReverb")
                .addNameVariant("mustang-ltx","ACD_FenderSmallRoom")
                .addNameVariant("mustang-gtx","DUBS_SmallRoomReverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SpaceEcho") 
                .addNameVariant("rumble-lt","DUBS_SpaceEcho")
                .addNameVariant("mustang-lt","DUBS_SpaceEcho")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_SpaceEcho")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Spring63") 
                .addNameVariant("rumble-lt","DUBS_FenderSpring63")
                .addNameVariant("mustang-lt","DUBS_FenderSpring63")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_FenderSpring63")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Spring65") 
                .addNameVariant("rumble-lt","DUBS_Spring65")
                .addNameVariant("mustang-lt","DUBS_Spring65")
                .addNameVariant("mustang-ltx","ACD_Spring65")
                .addNameVariant("mustang-gtx","DUBS_Spring65")
        );
        register_TONE_DspModule(
            new TONE_DspModule("StepFilter") 
                .addNameVariant("rumble-lt","DUBS_StepFilter")
                .addNameVariant("mustang-lt","DUBS_StepFilter")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_StepFilter")
        );
        register_TONE_DspModule(
            new TONE_DspModule("StereoEchoFilter") 
                .addNameVariant("rumble-lt","DUBS_StereoEchoFilter")
                .addNameVariant("mustang-lt","DUBS_StereoEchoFilter")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_StereoEchoFilter")
        );
        register_TONE_DspModule(
            new TONE_DspModule("StereoTapeDelay") 
                .addNameVariant("rumble-lt","DUBS_StereoTapeDelay")
                .addNameVariant("mustang-lt","DUBS_StereoTapeDelay")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_StereoTapeDelay")
        );
        register_TONE_DspModule(
            new TONE_DspModule("StudioPreampBass") 
                .addNameVariant("rumble-lt","DUBS_StudioPreampBass")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("StudioTubePreamp") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_StudioTubePreamp")
                .addNameVariant("mustang-ltx","ACD_StudioTubePreampGT")
                .addNameVariant("mustang-gtx","DUBS_StudioTubePreamp")
        );
        register_TONE_DspModule(
            new TONE_DspModule("StudioTubePreampBass") 
                .addNameVariant("rumble-lt","DUBS_StudioTubePreampBass")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Super") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_SuperReverb")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_SuperReverb")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SuperBassman") 
                .addNameVariant("rumble-lt","DUBS_SuperBassman")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SuperBassmanVintage") 
                .addNameVariant("rumble-lt","DUBS_SuperBassmanVintage")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("SuperSonic") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_SuperSonic")
                .addNameVariant("mustang-ltx","ACD_SuperSonicGT")
                .addNameVariant("mustang-gtx","DUBS_SuperSonic")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Sustain") 
                .addNameVariant("rumble-lt","DUBS_Sustain")
                .addNameVariant("mustang-lt","DUBS_Sustain")
                .addNameVariant("mustang-ltx","ACD_Sustain")
                .addNameVariant("mustang-gtx","DUBS_Sustain")
        );
        register_TONE_DspModule(
            new TONE_DspModule("TapeDelayLite") 
                .addNameVariant("rumble-lt","DUBS_TapeDelayLite")
                .addNameVariant("mustang-lt","DUBS_TapeDelayLite")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_TapeDelayLite")
        );
        register_TONE_DspModule(
            new TONE_DspModule("TouchWah") 
                .addNameVariant("rumble-lt","DUBS_TouchWah")
                .addNameVariant("mustang-lt","DUBS_TouchWah")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_TouchWah")
        );
        register_TONE_DspModule(
            new TONE_DspModule("TremoloHarmonic") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_TremoloHarmonic")
                .addNameVariant("mustang-ltx","ACD_TremoloHarmonic")
                .addNameVariant("mustang-gtx","DUBS_TremoloHarmonic")
        );
        register_TONE_DspModule(
            new TONE_DspModule("TremoloSine") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","ACD_TremoloSine")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("TriangleFlanger") 
                .addNameVariant("rumble-lt","DUBS_TriangleFlanger")
                .addNameVariant("mustang-lt","DUBS_TriangleFlanger")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_TriangleFlanger")
        );
        register_TONE_DspModule(
            new TONE_DspModule("TubeDriver") 
                .addNameVariant("rumble-lt","DUBS_TubeDriver")
                .addNameVariant("mustang-lt","DUBS_TubeDriver")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_TubeDriver")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Twin57") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Twin57")
                .addNameVariant("mustang-ltx","ACD_Twin57GT")
                .addNameVariant("mustang-gtx","DUBS_Twin57")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Twin65") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Twin65")
                .addNameVariant("mustang-ltx","ACD_Twin65GT")
                .addNameVariant("mustang-gtx","DUBS_Twin65")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Uberschall") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_Uberschall")
                .addNameVariant("mustang-ltx","ACD_UberschallGT")
                .addNameVariant("mustang-gtx","DUBS_Uberschall")
        );
        register_TONE_DspModule(
            new TONE_DspModule("UniVibe") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_UniVibe")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_UniVibe")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Unknown") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","")
                .addNameVariant("mustang-ltx","DUBS_Unknown")
                .addNameVariant("mustang-gtx","")
        );
        register_TONE_DspModule(
            new TONE_DspModule("VariFuzz") 
                .addNameVariant("rumble-lt","DUBS_VariFuzz")
                .addNameVariant("mustang-lt","DUBS_VariFuzz")
                .addNameVariant("mustang-ltx","ACD_VariFuzz")
                .addNameVariant("mustang-gtx","DUBS_VariFuzz")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Vibratone") 
                .addNameVariant("rumble-lt","DUBS_Vibratone")
                .addNameVariant("mustang-lt","DUBS_Vibratone")
                .addNameVariant("mustang-ltx","ACD_Vibratone")
                .addNameVariant("mustang-gtx","DUBS_Vibratone")
        );
        register_TONE_DspModule(
            new TONE_DspModule("VibroKing") 
                .addNameVariant("rumble-lt","")
                .addNameVariant("mustang-lt","DUBS_VibroKing")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_VibroKing")
        );
        register_TONE_DspModule(
            new TONE_DspModule("VintageTremolo") 
                .addNameVariant("rumble-lt","DUBS_VintageTremolo")
                .addNameVariant("mustang-lt","DUBS_VintageTremolo")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_VintageTremolo")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Wah") 
                .addNameVariant("rumble-lt","DUBS_Wah")
                .addNameVariant("mustang-lt","DUBS_Wah")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Wah")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Whammy") 
                .addNameVariant("rumble-lt","DUBS_Whammy")
                .addNameVariant("mustang-lt","DUBS_Whammy")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Whammy")
        );
        register_TONE_DspModule(
            new TONE_DspModule("WhammyDetune") 
                .addNameVariant("rumble-lt","DUBS_WhammyDetune")
                .addNameVariant("mustang-lt","DUBS_WhammyDetune")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_WhammyDetune")
        );
        register_TONE_DspModule(
            new TONE_DspModule("WhammyHarmony") 
                .addNameVariant("rumble-lt","DUBS_WhammyHarmony")
                .addNameVariant("mustang-lt","DUBS_WhammyHarmony")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_WhammyHarmony")
        );
        register_TONE_DspModule(
            new TONE_DspModule("Yellowbox") 
                .addNameVariant("rumble-lt","DUBS_Yellowbox")
                .addNameVariant("mustang-lt","DUBS_Yellowbox")
                .addNameVariant("mustang-ltx","")
                .addNameVariant("mustang-gtx","DUBS_Yellowbox")
        );
        /* registerModule generated entries end */

        /* registerModuleParam generated entries begin */
        /* registerModuleParam generated entries end */
    }
}
