package pro.gravit.launchserver.auth.profiles;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launchserver.manangers.LaunchServerGsonManager;
import pro.gravit.launchserver.modules.impl.LaunchServerModulesManager;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

class RemoteProfilesProviderContractTest {
    @TempDir
    static Path modulesDir;
    @TempDir
    static Path configDir;

    @BeforeAll
    static void prepareGson() {
        var modulesManager = new LaunchServerModulesManager(modulesDir, configDir, null);
        Launcher.gsonManager = new LaunchServerGsonManager(modulesManager);
        Launcher.gsonManager.initGson();
    }

    @Test
    void fetchesZombieSiteProfileListResponse() throws Exception {
        String profileJson = """
                {
                  "title": "Zombie Survival",
                  "uuid": "00000000-0000-0000-0000-000000000001",
                  "version": "1.20.1",
                  "info": "Main project server",
                  "dir": "ZombieSurvival",
                  "sortIndex": 0,
                  "assetIndex": "1.20.1",
                  "assetDir": "assets",
                  "update": [],
                  "updateExclusions": [],
                  "updateVerify": [],
                  "updateOptional": [],
                  "jvmArgs": [],
                  "classPath": [],
                  "altClassPath": [],
                  "clientArgs": [],
                  "compatClasses": [],
                  "loadNatives": [],
                  "properties": {},
                  "servers": [
                    {
                      "name": "Zombie Survival",
                      "serverAddress": "127.0.0.1",
                      "serverPort": 25565,
                      "isDefault": true,
                      "protocol": -1,
                      "socketPing": true
                    }
                  ],
                  "classLoaderConfig": "LAUNCHER",
                  "flags": [],
                  "recommendJavaVersion": 21,
                  "minJavaVersion": 17,
                  "maxJavaVersion": 999,
                  "settings": {
                    "ram": 2048,
                    "autoEnter": false,
                    "fullScreen": false
                  },
                  "limited": false,
                  "mainClass": null,
                  "mainModule": null,
                  "moduleConf": null
                }
                """;
        String response = """
                {
                  "profiles": [
                    %s
                  ]
                }
                """.formatted(profileJson);

        var authorizationHeader = new AtomicReference<String>();
        var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/profile/list", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.createContext("/profile/by/uuid/00000000-0000-0000-0000-000000000001", exchange -> {
            byte[] body = """
                    {
                      "profile": %s,
                      "clientDir": {"map": {}},
                      "assetDir": {"map": {}}
                    }
                    """.formatted(profileJson).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.createContext("/profile/unconnected/assets", exchange -> {
            byte[] body = "{\"map\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.start();
        try {
            var provider = new RemoteProfilesProvider();
            provider.baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
            provider.accessToken = "launcher-test-token";

            var profiles = provider.getProfiles(null);
            var profile = profiles.iterator().next();

            Assertions.assertEquals("Bearer launcher-test-token", authorizationHeader.get());
            Assertions.assertEquals(1, profiles.size());
            Assertions.assertEquals("Zombie Survival", profile.getName());
            Assertions.assertInstanceOf(RemoteProfilesProvider.HttpUncompletedProfile.class, profile);
            var remoteProfile = (RemoteProfilesProvider.HttpUncompletedProfile) profile;
            Assertions.assertEquals("1.20.1", remoteProfile.profile().getVersion().toString());
            Assertions.assertEquals("ZombieSurvival", remoteProfile.profile().getDir());
            Assertions.assertEquals("127.0.0.1", remoteProfile.profile().getDefaultServerProfile().serverAddress);
            Assertions.assertEquals(25565, remoteProfile.profile().getDefaultServerProfile().serverPort);
            Assertions.assertEquals("Zombie Survival", provider.get(remoteProfile.getUuid(), null).getProfile().getTitle());
            Assertions.assertTrue(provider.getUnconnectedDirectory("assets").isEmpty());
        } finally {
            httpServer.stop(0);
        }
    }
}
