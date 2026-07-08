package pro.gravit.launcher.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class ConnectionResolverTest {
    @Test
    void selectsLowestLatencyHealthyRegionFromBootstrap() throws Exception {
        var cache = new MemoryCache();
        var resolver = new ConnectionResolver(
                (url) -> bootstrap(region("eu", 10), region("ru", 20)),
                (region, timeoutMillis) -> new ConnectionResolver.Health(region.id().equals("ru"), region.id().equals("ru") ? 50 : 300),
                cache);

        var selected = resolver.resolve("https://site.example.test/api/launcher/bootstrap");

        Assertions.assertEquals("ru", selected.region().id());
        Assertions.assertEquals("https://ru.example.test/api", selected.apiUrl());
        Assertions.assertFalse(selected.fromCache());
        Assertions.assertTrue(cache.saved.isPresent());
    }

    @Test
    void usesCachedBootstrapWhenRemoteBootstrapFails() throws Exception {
        var cache = new MemoryCache();
        cache.saved = Optional.of(bootstrap(region("backup", 30)));
        var resolver = new ConnectionResolver(
                (url) -> { throw new java.io.IOException("down"); },
                (region, timeoutMillis) -> new ConnectionResolver.Health(true, 80),
                cache);

        var selected = resolver.resolve("https://site.example.test/api/launcher/bootstrap");

        Assertions.assertEquals("backup", selected.region().id());
        Assertions.assertTrue(selected.fromCache());
    }

    @Test
    void manualRegionFallsBackToAutoWhenUnavailable() throws Exception {
        var cache = new MemoryCache();
        cache.mode = "manual";
        cache.selectedRegionId = "eu";
        var resolver = new ConnectionResolver(
                (url) -> bootstrap(region("eu", 10), region("ru", 20)),
                (region, timeoutMillis) -> new ConnectionResolver.Health(region.id().equals("ru"), 100),
                cache);

        var selected = resolver.resolve("https://site.example.test/api/launcher/bootstrap");

        Assertions.assertEquals("ru", selected.region().id());
        Assertions.assertTrue(selected.fellBackFromManual());
    }

    @Test
    void prefersLastSuccessfulRegionWhenLatencyIsClose() throws Exception {
        var cache = new MemoryCache();
        cache.lastSuccessfulRegionId = "backup";
        var resolver = new ConnectionResolver(
                (url) -> bootstrap(region("eu", 10), region("backup", 20)),
                (region, timeoutMillis) -> new ConnectionResolver.Health(true, region.id().equals("eu") ? 120 : 180),
                cache);

        var selected = resolver.resolve("https://site.example.test/api/launcher/bootstrap");

        Assertions.assertEquals("backup", selected.region().id());
        Assertions.assertEquals("backup", cache.lastSuccessfulRegionId);
    }

    @Test
    void failsWhenBootstrapAndCacheAreUnavailable() {
        var resolver = new ConnectionResolver(
                (url) -> { throw new java.io.IOException("down"); },
                (region, timeoutMillis) -> new ConnectionResolver.Health(false, -1),
                new MemoryCache());

        Assertions.assertThrows(ConnectionResolver.ConnectionUnavailableException.class,
                () -> resolver.resolve("https://site.example.test/api/launcher/bootstrap"));
    }

    private static ConnectionBootstrap bootstrap(ConnectionRegion... regions) {
        return new ConnectionBootstrap(1, "auto", 2500, List.of(regions));
    }

    private static ConnectionRegion region(String id, int priority) {
        return new ConnectionRegion(id, id.toUpperCase(), "https://" + id + ".example.test/api",
                "https://" + id + ".example.test/api/health", priority, true);
    }

    private static class MemoryCache implements ConnectionResolver.Cache {
        Optional<ConnectionBootstrap> saved = Optional.empty();
        String mode = "auto";
        String selectedRegionId;
        String lastSuccessfulRegionId;

        @Override
        public Optional<ConnectionBootstrap> loadBootstrap() {
            return saved;
        }

        @Override
        public void saveBootstrap(ConnectionBootstrap bootstrap) {
            saved = Optional.of(bootstrap);
        }

        @Override
        public String connectionMode() {
            return mode;
        }

        @Override
        public String selectedRegionId() {
            return selectedRegionId;
        }

        @Override
        public String lastSuccessfulRegionId() {
            return lastSuccessfulRegionId;
        }

        @Override
        public void saveLastSuccessfulRegionId(String regionId) {
            lastSuccessfulRegionId = regionId;
        }
    }
}
