package net.heretical_camelid.fhau.lib.registries;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PresetRecordTest {
    @org.junit.Before
    public void setUp() throws Exception {
    }

    @org.junit.After
    public void tearDown() throws Exception {
    }

    @Test
    public void test01_displayName() {
        PresetRecord dnTestPR = presetRecord_EMPTY();
        System.out.println(String.format(
            "Preset created with name '%s'",dnTestPR.displayName()
        ));
        Assert.assertEquals(PRESET_NAME_EMPTY,dnTestPR.displayName());
    }

    @Test
    public void test02_ampName() {
        PresetRecord anTestPR = presetRecord_FENDER_CLEAN();
        System.out.println(String.format(
            "Preset created with name '%s' using amplifier '%s'",
            anTestPR.displayName(), anTestPR.ampName()
        ));
        Assert.assertEquals(AMP_NAME_TWIN65, "DUBS_" + anTestPR.ampName());
    }

    @Test
    public void test03_audioHash() {
        // Audio hashes extracted from suites/01-General.preset_suite.json
        // generated during desktop CLI run
        PresetRecord testPR1 = presetRecord_EMPTY();
        System.out.println(String.format(
            "Audio hash for preset '%s' is '%s'",
            testPR1.displayName(), testPR1.audioHash()
        ));
        Assert.assertEquals("b4ed-d311",testPR1.audioHash());

        PresetRecord testPR2 = presetRecord_FENDER_CLEAN();
        System.out.println(String.format(
            "Audio hash for preset '%s' is '%s'",
            testPR2.displayName(), testPR2.audioHash()
        ));
        Assert.assertEquals("adbd-ead9",testPR2.audioHash());
    }

    @Test
    public void test04_effects() {
        // Effect strings extracted from suites/01-General.preset_suite.json
        // generated during desktop CLI run
        // NB the EffectsLevelOfDetails param values MODULES_ONLY and
        // PARAMETERS only are exercised by the audioHash() algorithm
        // so they aren't retested here.
        PresetRecord testPR1 = presetRecord_EMPTY();
        System.out.println(String.format(
            "Effect string for preset '%s' is:\n<<<\n%s\n>>>",
            testPR1.displayName(),
            testPR1.effects(PresetRecord.EffectsLevelOfDetails.MODULES_AND_PARAMETERS)
        ));
        Assert.assertEquals(
            "amp:LinearGain(bass:0.5,cabsimType:none,gain:0.5,gateDetectorPosition:jack,gatePreset:off,mid:0.5,treb:0.5,volume:0.0)",
            testPR1.effects(PresetRecord.EffectsLevelOfDetails.MODULES_AND_PARAMETERS)
        );

        PresetRecord testPR2 = presetRecord_FENDER_CLEAN();
        System.out.println(String.format(
            "Effect string for preset '%s' is:\n<<<\n%s\n>>>",
            testPR2.displayName(),
            testPR2.effects(PresetRecord.EffectsLevelOfDetails.MODULES_AND_PARAMETERS)
        ));
        Assert.assertEquals(
            String.join("\n",
                "stomp:SimpleCompressor(type:medium,bypass:false,bypassType:Post)",
                "amp:Twin65(bass:0.555556,bias:0.5,bright:true,cabsimType:65twn,gain:0.337255,gateDetectorPosition:jack,gatePreset:off,mid:0.5,sag:match,treb:0.342816,volume:-11.12674)",
                "reverb:Spring65(decay:0.388889,diffuse:1.0,dwell:0.28889,wetLvl:0.5,bypass:false,bypassType:Pre,tone:1)"
            ),
            testPR2.effects(PresetRecord.EffectsLevelOfDetails.MODULES_AND_PARAMETERS)
        );
    }

    @Test
    public void test05_shortInfo() {
        // PresetRecordBuilder does not yet support
        // setting fields other than displayName
        // For the moment we aren't worried about this method
        // (but we may care more about the fields covered when
        // we start sending JSON to devices)
        PresetRecord testPR1 = presetRecord_EMPTY();
        System.out.println(String.format(
            "Short info for preset '%s' is '%s'",
            testPR1.displayName(),
            testPR1.shortInfo()
        ));
        Assert.assertEquals(
            "no author, no source_id, product_id:null, is_factory_default:true",
            testPR1.shortInfo()
        );
    }

    // The static functions making up the remainder of this test class
    // enable building of instances of PresetRecord which closely match
    // some of the testable behaviour of the LT40S presets called 'EMPTY'
    // and 'FENDER CLEAN'.
    // The focus of the tests which are enabled is on ensuring that tests
    // are sensitive to changes which impact the audioHash() return value
    // as this function is important to the registry classes provided
    // to manage presets and suites.

    public static final String PRESET_NAME_EMPTY = "EMPTY           ";
    public static PresetRecord presetRecord_EMPTY() {
        return new PresetRecordBuilder()
            .setDisplayName(PRESET_NAME_EMPTY)
            .setAmpName("DUBS_LinearGain")
            .setDspUnitParameter(2, "bass", 0.5)
            .setDspUnitParameter(2, "cabsimType", "none")
            .setDspUnitParameter(2, "gain", 0.5)
            .setDspUnitParameter(2, "gateDetectorPosition", "jack")
            .setDspUnitParameter(2, "gatePreset", "off")
            .setDspUnitParameter(2, "mid", 0.5)
            .setDspUnitParameter(2, "treb", 0.5)
            .setDspUnitParameter(2, "volume", 0.0)
            .build();
    }

    public static final String PRESET_NAME_FENDER_CLEAN = "FENDER  CLEAN   ";
    public static final String AMP_NAME_TWIN65 = "DUBS_Twin65";
    public static final String DELAY_EFFECT_NAME_SPRING65 = "DUBS_Spring65";
    public static PresetRecord presetRecord_FENDER_CLEAN() {
        return new PresetRecordBuilder()
            .setDisplayName(PRESET_NAME_FENDER_CLEAN)
            .setEffectName(0, "DUBS_SimpleCompressor")
            .setDspUnitParameter(0, "type", "medium")
            .setDspUnitParameter(0, "bypass", false)
            .setDspUnitParameter(0, "bypassType", "Post")
            .setAmpName(AMP_NAME_TWIN65)
            .setDspUnitParameter(2, "bass", 0.555556)
            .setDspUnitParameter(2, "bias", 0.5)
            .setDspUnitParameter(2, "bright", true)
            .setDspUnitParameter(2, "cabsimType", "65twn")
            .setDspUnitParameter(2, "gain", 0.337255)
            .setDspUnitParameter(2, "gateDetectorPosition", "jack")
            .setDspUnitParameter(2, "gatePreset", "off")
            .setDspUnitParameter(2, "mid", 0.5)
            .setDspUnitParameter(2, "sag", "match")
            .setDspUnitParameter(2, "treb", 0.342816)
            .setDspUnitParameter(2, "volume", -11.12674)
            .setEffectName(4, DELAY_EFFECT_NAME_SPRING65)
            .setDspUnitParameter(4, "decay", 0.388889)
            .setDspUnitParameter(4, "diffuse", 1.0)
            .setDspUnitParameter(4, "dwell", 0.28889)
            .setDspUnitParameter(4, "wetLvl", 0.5)
            .setDspUnitParameter(4, "bypass", false)
            .setDspUnitParameter(4, "bypassType", "Pre")
            .setDspUnitParameter(4, "tone", "1")
            .build();
    }

}

