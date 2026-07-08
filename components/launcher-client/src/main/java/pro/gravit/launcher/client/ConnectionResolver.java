package pro.gravit.launcher.client;

import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;

public class ConnectionResolver {
    private final BootstrapClient bootstrapClient;
    private final HealthChecker healthChecker;
    private final Cache cache;

    public ConnectionResolver(BootstrapClient bootstrapClient, HealthChecker healthChecker, Cache cache) {
        this.bootstrapClient = bootstrapClient;
        this.healthChecker = healthChecker;
        this.cache = cache;
    }

    public Selection resolve(String bootstrapUrl) throws Exception {
        BootstrapSource source = loadBootstrap(bootstrapUrl);
        var regions = source.bootstrap().enabledRegions();
        if (regions.isEmpty()) {
            throw new ConnectionUnavailableException("No launcher connection regions available");
        }
        String mode = safe(cache.connectionMode());
        String selectedRegionId = safe(cache.selectedRegionId());
        boolean manual = "manual".equalsIgnoreCase(mode) && !selectedRegionId.isBlank();
        if (manual) {
            Optional<SelectionCandidate> selected = regions.stream()
                    .filter((region) -> region.id().equals(selectedRegionId))
                    .map((region) -> check(region, source.bootstrap().effectiveHealthTimeoutMs()))
                    .filter(SelectionCandidate::healthy)
                    .findFirst();
            if (selected.isPresent()) {
                return finish(selected.get(), source.fromCache(), false);
            }
        }
        SelectionCandidate candidate = regions.stream()
                .map((region) -> check(region, source.bootstrap().effectiveHealthTimeoutMs()))
                .filter(SelectionCandidate::healthy)
                .min(candidateComparator())
                .orElseThrow(() -> new ConnectionUnavailableException("No reachable launcher connection regions"));
        return finish(candidate, source.fromCache(), manual);
    }

    private BootstrapSource loadBootstrap(String bootstrapUrl) throws Exception {
        try {
            ConnectionBootstrap bootstrap = bootstrapClient.fetch(bootstrapUrl);
            validateBootstrap(bootstrap);
            cache.saveBootstrap(bootstrap);
            return new BootstrapSource(bootstrap, false);
        } catch (Exception e) {
            Optional<ConnectionBootstrap> cached = cache.loadBootstrap();
            if (cached.isPresent()) {
                validateBootstrap(cached.get());
                return new BootstrapSource(cached.get(), true);
            }
            throw new ConnectionUnavailableException("Launcher bootstrap is unavailable and no cached regions exist");
        }
    }

    private void validateBootstrap(ConnectionBootstrap bootstrap) throws IOException {
        if (bootstrap == null || bootstrap.enabledRegions().isEmpty()) {
            throw new IOException("Launcher bootstrap does not contain enabled regions");
        }
    }

    private Selection finish(SelectionCandidate candidate, boolean fromCache, boolean fellBackFromManual) {
        cache.saveLastSuccessfulRegionId(candidate.region().id());
        return new Selection(candidate.region(), candidate.region().apiUrl(), fromCache, fellBackFromManual);
    }

    private SelectionCandidate check(ConnectionRegion region, int timeoutMillis) {
        try {
            Health health = healthChecker.check(region, timeoutMillis);
            return new SelectionCandidate(region, health.available(), health.latencyMillis());
        } catch (Exception ignored) {
            return new SelectionCandidate(region, false, Long.MAX_VALUE);
        }
    }

    private Comparator<SelectionCandidate> candidateComparator() {
        String lastSuccessful = safe(cache.lastSuccessfulRegionId());
        return Comparator
                .comparingLong((SelectionCandidate candidate) ->
                        candidate.region().id().equals(lastSuccessful) ? Math.max(0, candidate.latencyMillis() - 100) : candidate.latencyMillis())
                .thenComparingInt((candidate) -> candidate.region().priority())
                .thenComparing((candidate) -> candidate.region().id());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public interface BootstrapClient {
        ConnectionBootstrap fetch(String bootstrapUrl) throws Exception;
    }

    public interface HealthChecker {
        Health check(ConnectionRegion region, int timeoutMillis) throws Exception;
    }

    public interface Cache {
        Optional<ConnectionBootstrap> loadBootstrap();

        void saveBootstrap(ConnectionBootstrap bootstrap);

        String connectionMode();

        String selectedRegionId();

        String lastSuccessfulRegionId();

        void saveLastSuccessfulRegionId(String regionId);
    }

    public record Health(boolean available, long latencyMillis) {
    }

    public record Selection(ConnectionRegion region, String apiUrl, boolean fromCache, boolean fellBackFromManual) {
    }

    private record BootstrapSource(ConnectionBootstrap bootstrap, boolean fromCache) {
    }

    private record SelectionCandidate(ConnectionRegion region, boolean healthy, long latencyMillis) {
    }

    public static class ConnectionUnavailableException extends IOException {
        public ConnectionUnavailableException(String message) {
            super(message);
        }
    }
}
