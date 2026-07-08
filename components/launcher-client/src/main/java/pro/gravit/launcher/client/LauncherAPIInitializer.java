package pro.gravit.launcher.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.base.LauncherConfig;
import pro.gravit.launcher.base.modules.LauncherModulesManager;
import pro.gravit.launcher.base.request.*;
import pro.gravit.launcher.base.request.websockets.StdWebSocketService;
import pro.gravit.launcher.core.api.LauncherAPI;
import pro.gravit.launcher.core.api.LauncherAPIHolder;
import pro.gravit.launcher.core.api.features.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class LauncherAPIInitializer {
    private static final Logger logger = LoggerFactory.getLogger(LauncherAPIInitializer.class);
    private static ConnectionResolver.Cache connectionCache = FileConnectionCache.defaultCache();

    public static boolean isHttpAddress(String address) {
        return address.startsWith("http://") || address.startsWith("https://");
    }

    public enum Flag {
        OFFLINE_MODE,
        ENABLE_AUTO_REFRESH
    }

    public static void setConnectionCache(ConnectionResolver.Cache cache) {
        connectionCache = cache == null ? FileConnectionCache.defaultCache() : cache;
    }

    public static void initialize(LauncherModulesManager modulesManager, String address, List<Flag> flags) throws Exception {
        if(flags.contains(Flag.OFFLINE_MODE)) {
            initOfflineMode();
        } else if(isHttpAddress(address)) {
            initHttpMode(resolveHttpAddress(address));
        } else {
            initWebSocketMode(address, flags);
        }
    }

    private static String resolveHttpAddress(String address) throws Exception {
        String bootstrapUrl = bootstrapUrl();
        if (bootstrapUrl == null || bootstrapUrl.isBlank()) {
            return address;
        }
        var resolver = new ConnectionResolver(
                new HttpConnectionBootstrapClient(),
                new HttpConnectionHealthChecker(),
                connectionCache);
        var selection = resolver.resolve(bootstrapUrl);
        logger.info("Selected launcher connection region {} -> {}", selection.region().id(), selection.apiUrl());
        return selection.apiUrl();
    }

    private static String bootstrapUrl() {
        try {
            LauncherConfig config = Launcher.getConfig();
            return config == null ? "" : config.bootstrapUrl;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void initWebSocketMode(String address, List<Flag> flags) throws Exception {
        StdWebSocketService service;
        try {
            service = StdWebSocketService.initWebSockets(address).get();
        } catch (Throwable e) {
            logger.error("Connection failed", e);
            initOfflineMode();
            return;
        }
        Request.setRequestService(service);
        logger.debug("Restore sessions");
        Request.restore(false, false, true);
        service.registerEventHandler(new BasicLauncherEventHandler());
        service.reconnectCallback = () ->
        {
            logger.debug("WebSocket connect closed. Try reconnect");
            try {
                Request.reconnect();
            } catch (Exception e) {
                logger.error("", e);
                throw new RequestException("Connection failed", e);
            }
        };
        if(flags.contains(Flag.ENABLE_AUTO_REFRESH)) {
            Request.startAutoRefresh();
        }
        LauncherAPIHolder.setCoreAPI(new RequestCoreFeatureAPIImpl(Request.getRequestService()));
        Function<String, LauncherAPI> apiFactory = (authId) -> {
            var impl = new RequestFeatureAPIImpl(Request.getRequestService(), authId);
            return new LauncherAPI(Map.of(
                    AuthFeatureAPI.class, impl,
                    UserFeatureAPI.class, impl,
                    ProfileFeatureAPI.class, impl,
                    TextureUploadFeatureAPI.class, impl,
                    HardwareVerificationFeatureAPI.class, impl));
        };
        LauncherAPIHolder.setCreateApiFactory(apiFactory);
        LauncherAPIHolder.setApi(apiFactory.apply(null));
    }

    private static void initHttpMode(String address) {
        RequestFeatureHttpAPIImpl impl = new RequestFeatureHttpAPIImpl(address);
        LauncherAPIHolder.setCoreAPI(impl);
        Function<String, LauncherAPI> apiFactory = (authId) -> new LauncherAPI(Map.of(
                    AuthFeatureAPI.class, impl,
                    UserFeatureAPI.class, impl,
                    ProfileFeatureAPI.class, impl,
                    TextureUploadFeatureAPI.class, impl,
                    HardwareVerificationFeatureAPI.class, impl));
        LauncherAPIHolder.setCreateApiFactory(apiFactory);
        LauncherAPIHolder.setApi(apiFactory.apply(null));
    }

    private static void initOfflineMode() {
        RequestOfflineModeAPIImpl impl = new RequestOfflineModeAPIImpl();
        LauncherAPIHolder.setCoreAPI(impl);
        Function<String, LauncherAPI> apiFactory = (authId) -> new LauncherAPI(Map.of(
                    AuthFeatureAPI.class, impl,
                    UserFeatureAPI.class, impl,
                    ProfileFeatureAPI.class, impl));
        LauncherAPIHolder.setCreateApiFactory(apiFactory);
        LauncherAPIHolder.setApi(apiFactory.apply(null));
    }
}
