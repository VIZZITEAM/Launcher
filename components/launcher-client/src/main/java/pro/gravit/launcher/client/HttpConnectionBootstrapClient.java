package pro.gravit.launcher.client;

import pro.gravit.launcher.base.Launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpConnectionBootstrapClient implements ConnectionResolver.BootstrapClient {
    private final HttpClient client;
    private final int timeoutMillis;

    public HttpConnectionBootstrapClient() {
        this(2500);
    }

    public HttpConnectionBootstrapClient(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .build();
    }

    @Override
    public ConnectionBootstrap fetch(String bootstrapUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(bootstrapUrl))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Launcher bootstrap returned HTTP " + response.statusCode());
        }
        return Launcher.gsonManager.gson.fromJson(response.body(), ConnectionBootstrap.class);
    }
}
