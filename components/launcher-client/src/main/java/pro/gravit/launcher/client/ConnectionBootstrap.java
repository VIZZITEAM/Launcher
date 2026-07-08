package pro.gravit.launcher.client;

import java.util.List;

public record ConnectionBootstrap(
        int version,
        String defaultMode,
        int healthTimeoutMs,
        List<ConnectionRegion> regions) {
    public List<ConnectionRegion> enabledRegions() {
        if (regions == null) {
            return List.of();
        }
        return regions.stream()
                .filter(ConnectionRegion::enabled)
                .filter(ConnectionRegion::isValid)
                .toList();
    }

    public int effectiveHealthTimeoutMs() {
        return healthTimeoutMs > 0 ? healthTimeoutMs : 2500;
    }
}
