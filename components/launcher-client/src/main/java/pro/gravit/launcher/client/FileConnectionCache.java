package pro.gravit.launcher.client;

import com.google.gson.Gson;
import pro.gravit.launcher.base.Launcher;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class FileConnectionCache implements ConnectionResolver.Cache {
    private static final String CACHE_FILE_PROPERTY = "launcher.connection.cacheFile";
    private final Path path;

    public FileConnectionCache(Path path) {
        this.path = path;
    }

    public static FileConnectionCache defaultCache() {
        String configuredPath = System.getProperty(CACHE_FILE_PROPERTY);
        Path cachePath = configuredPath == null || configuredPath.isBlank()
                ? Path.of(System.getProperty("user.home"), ".gravitlauncher", "connection-cache.json")
                : Path.of(configuredPath);
        return new FileConnectionCache(cachePath);
    }

    @Override
    public Optional<ConnectionBootstrap> loadBootstrap() {
        return Optional.ofNullable(read().bootstrap);
    }

    @Override
    public void saveBootstrap(ConnectionBootstrap bootstrap) {
        CacheData data = read();
        data.bootstrap = bootstrap;
        write(data);
    }

    @Override
    public String connectionMode() {
        return read().connectionMode;
    }

    @Override
    public String selectedRegionId() {
        return read().selectedRegionId;
    }

    @Override
    public String lastSuccessfulRegionId() {
        return read().lastSuccessfulRegionId;
    }

    @Override
    public void saveLastSuccessfulRegionId(String regionId) {
        CacheData data = read();
        data.lastSuccessfulRegionId = regionId;
        write(data);
    }

    public void saveConnectionMode(String mode, String selectedRegionId) {
        CacheData data = read();
        data.connectionMode = mode == null || mode.isBlank() ? "auto" : mode;
        data.selectedRegionId = selectedRegionId;
        write(data);
    }

    private CacheData read() {
        if (!Files.exists(path)) {
            return new CacheData();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            CacheData data = gson().fromJson(reader, CacheData.class);
            return data == null ? new CacheData() : data.normalize();
        } catch (Exception ignored) {
            return new CacheData();
        }
    }

    private void write(CacheData data) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                gson().toJson(data.normalize(), writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    private Gson gson() {
        if (Launcher.gsonManager != null && Launcher.gsonManager.gson != null) {
            return Launcher.gsonManager.gson;
        }
        return new Gson();
    }

    private static class CacheData {
        int schemaVersion = 1;
        ConnectionBootstrap bootstrap;
        String connectionMode = "auto";
        String selectedRegionId;
        String lastSuccessfulRegionId;

        CacheData normalize() {
            schemaVersion = 1;
            if (connectionMode == null || connectionMode.isBlank()) {
                connectionMode = "auto";
            }
            return this;
        }
    }
}
