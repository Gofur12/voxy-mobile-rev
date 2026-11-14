package dev.brezze.voxymobile.core.config;

public final class VoxyMobileSettingsPolicySmokeTest {
    public static void main(String[] args) {
        var all = VoxyMobileSettingsPolicy.resolve(true, true, true);
        check(all.rendererEnabled() && all.ingestionEnabled(), "enabled master permits configured features");
        var paused = VoxyMobileSettingsPolicy.resolve(false, true, true);
        check(!paused.rendererEnabled() && !paused.ingestionEnabled(), "master switch pauses both features");
        var partial = VoxyMobileSettingsPolicy.resolve(true, false, true);
        check(!partial.rendererEnabled() && partial.ingestionEnabled(), "renderer can be disabled independently");
        var ingestOff = VoxyMobileSettingsPolicy.resolve(true, true, false);
        check(ingestOff.rendererEnabled() && !ingestOff.ingestionEnabled(), "terrain capture can be disabled independently");
        System.out.println("VoxyMobileSettingsPolicySmokeTest: PASS (4 assertions)");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
