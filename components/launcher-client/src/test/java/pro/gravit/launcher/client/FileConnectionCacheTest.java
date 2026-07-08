package pro.gravit.launcher.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class FileConnectionCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsBootstrapAndLastSuccessfulRegion() {
        Path cacheFile = tempDir.resolve("connection-cache.json");
        var cache = new FileConnectionCache(cacheFile);

        cache.saveBootstrap(bootstrap(region("eu")));
        cache.saveConnectionMode("manual", "eu");
        cache.saveLastSuccessfulRegionId("eu");

        var restored = new FileConnectionCache(cacheFile);

        Assertions.assertTrue(restored.loadBootstrap().isPresent());
        Assertions.assertEquals("eu", restored.loadBootstrap().get().regions().getFirst().id());
        Assertions.assertEquals("EU", restored.loadBootstrap().get().regions().getFirst().countryCode());
        Assertions.assertTrue(restored.loadBootstrap().get().regions().getFirst().recommended());
        Assertions.assertFalse(restored.loadBootstrap().get().regions().getFirst().fallbackOnly());
        Assertions.assertEquals("manual", restored.connectionMode());
        Assertions.assertEquals("eu", restored.selectedRegionId());
        Assertions.assertEquals("eu", restored.lastSuccessfulRegionId());
    }

    @Test
    void writesVersionedCacheFile() throws Exception {
        Path cacheFile = tempDir.resolve("connection-cache.json");
        var cache = new FileConnectionCache(cacheFile);

        cache.saveConnectionMode("manual", "eu");

        String json = java.nio.file.Files.readString(cacheFile);
        Assertions.assertTrue(json.contains("\"schemaVersion\":1"));
    }

    @Test
    void brokenCacheFileFallsBackToDefaults() throws Exception {
        Path cacheFile = tempDir.resolve("connection-cache.json");
        java.nio.file.Files.writeString(cacheFile, "{");

        var cache = new FileConnectionCache(cacheFile);

        Assertions.assertTrue(cache.loadBootstrap().isEmpty());
        Assertions.assertEquals("auto", cache.connectionMode());
        Assertions.assertNull(cache.selectedRegionId());
    }

    private static ConnectionBootstrap bootstrap(ConnectionRegion... regions) {
        return new ConnectionBootstrap(1, "auto", 2500, List.of(regions));
    }

    private static ConnectionRegion region(String id) {
        return new ConnectionRegion(id, id.toUpperCase(), "https://" + id + ".example.test/api",
                "https://" + id + ".example.test/api/health", 10, true,
                "Region " + id, "EU", true, true, false);
    }
}
