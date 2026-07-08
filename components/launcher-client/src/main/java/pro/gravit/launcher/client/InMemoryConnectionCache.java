package pro.gravit.launcher.client;

import java.util.Optional;

public class InMemoryConnectionCache implements ConnectionResolver.Cache {
    private ConnectionBootstrap bootstrap;
    private String connectionMode = "auto";
    private String selectedRegionId;
    private String lastSuccessfulRegionId;

    @Override
    public Optional<ConnectionBootstrap> loadBootstrap() {
        return Optional.ofNullable(bootstrap);
    }

    @Override
    public void saveBootstrap(ConnectionBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public String connectionMode() {
        return connectionMode;
    }

    public void setConnectionMode(String connectionMode) {
        this.connectionMode = connectionMode;
    }

    @Override
    public String selectedRegionId() {
        return selectedRegionId;
    }

    public void setSelectedRegionId(String selectedRegionId) {
        this.selectedRegionId = selectedRegionId;
    }

    @Override
    public String lastSuccessfulRegionId() {
        return lastSuccessfulRegionId;
    }

    @Override
    public void saveLastSuccessfulRegionId(String regionId) {
        this.lastSuccessfulRegionId = regionId;
    }
}
