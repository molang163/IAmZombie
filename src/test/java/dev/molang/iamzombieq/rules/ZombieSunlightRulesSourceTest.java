package dev.molang.iamzombieq.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ZombieSunlightRulesSourceTest {
    @Test
    void legacyOverloadsAreCompatibilityBridgesToThePureContextCore() throws IOException {
        String source = SourceScan.stripComments(
                SourceScan.mainJava("dev/molang/iamzombieq/rules/ZombieSunlightRules.java"));
        String floatBridge = SourceScan.compact(SourceScan.methodBody(source, "float randomFloat,"));
        String supplierBridge = SourceScan.compact(SourceScan.methodBody(source, "DoubleSupplier randomFloat,"));
        String contextCore = SourceScan.compact(
                SourceScan.methodBody(source, "isVanillaSunBurnTick(SunBurnContext context)"));
        String contextSource = SourceScan.compact(
                SourceScan.mainJava("dev/molang/iamzombieq/rules/SunBurnContext.java"));

        assertTrue(floatBridge.contains(
                        "returnisVanillaSunBurnTick(newSunBurnContext(monstersBurn,brightness,randomFloat,"
                                + "canSeeSky,inWaterRainOrPowderSnow));"),
                "the float overload should only bridge its named values into the context core");
        assertFalse(floatBridge.contains("->"), "the float bridge should not allocate a supplier lambda");

        int precondition = supplierBridge.indexOf("if(!monstersBurn||!(brightness>0.5F)){returnfalse;}");
        int sample = supplierBridge.indexOf("doublerandomValue=randomFloat.getAsDouble();");
        int delegation = supplierBridge.indexOf(
                "returnisVanillaSunBurnTick(newSunBurnContext(monstersBurn,brightness,randomValue,"
                        + "canSeeSky,inWaterRainOrPowderSnow));");
        assertTrue(precondition >= 0 && sample > precondition && delegation > sample,
                "the supplier bridge should short-circuit, sample once, then delegate to the context core");
        assertEquals(1, SourceScan.countOccurrences(supplierBridge, "randomFloat.getAsDouble()"));
        assertFalse(supplierBridge.contains("(float)"), "the supplier result must remain a double");

        assertTrue(contextCore.contains(
                        "returncontext.monstersBurn()"
                                + "&&context.brightness()>0.5F"
                                + "&&context.randomValue()*30.0F<(context.brightness()-0.4F)*2.0F"
                                + "&&!context.inWaterRainOrPowderSnow()"
                                + "&&context.canSeeSky();"),
                "the context core should retain the exact vanilla formula and evaluation order");
        assertTrue(contextSource.contains(
                        "publicrecordSunBurnContext(booleanmonstersBurn,floatbrightness,doublerandomValue,"
                                + "booleancanSeeSky,booleaninWaterRainOrPowderSnow){}"),
                "the context should name all five primitive sunlight inputs");
        assertFalse(contextSource.contains("net.minecraft"), "the context must not depend on Minecraft types");
        assertFalse(contextSource.contains("net.neoforged"), "the context must not depend on NeoForge types");
    }
}
