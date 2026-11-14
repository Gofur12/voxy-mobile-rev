package dev.brezze.voxymobile.core.config;

/** Resolves master and per-feature toggles without losing the user's saved sub-settings. */
public final class VoxyMobileSettingsPolicy {
    private VoxyMobileSettingsPolicy() {}

    public static Effective resolve(boolean enabled, boolean rendererEnabled, boolean ingestionEnabled) {
        return new Effective(enabled && rendererEnabled, enabled && ingestionEnabled);
    }

    public record Effective(boolean rendererEnabled, boolean ingestionEnabled) {}
}
